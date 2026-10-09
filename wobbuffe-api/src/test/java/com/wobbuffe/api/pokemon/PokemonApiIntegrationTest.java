package com.wobbuffe.api.pokemon;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.wobbuffe.api.integration.IntegrationControlService;
import com.wobbuffe.api.operation.OperationRepository;
import com.wobbuffe.api.operation.OperationStatus;
import com.wobbuffe.sqs.OperationPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "wobbuffe.routing.enabled=true",
        "wobbuffe.routing.max-percent=100",
        "wobbuffe.routing.step-percent=100",
        "wobbuffe.routing.evaluation-interval=1ms",
        "wobbuffe.defaults.max-attempts=1",
        "wobbuffe.defaults.minimum-calls=10",
        "wobbuffe.defaults.latency-minimum-samples=2",
        "wobbuffe.defaults.degraded-error-rate-threshold=50",
        "wobbuffe.integrations.pokeapi.async-eligible=true"
})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PokemonApiIntegrationTest {
    private static final WireMockServer POKEAPI = new WireMockServer(
            com.github.tomakehurst.wiremock.core.WireMockConfiguration.options().dynamicPort());
    private static final AtomicInteger PUBLISHED = new AtomicInteger();
    static { POKEAPI.start(); }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("wobbuffe.pokeapi.base-url", () -> POKEAPI.baseUrl() + "/api/v2");
    }

    @Autowired MockMvc mvc;
    @Autowired PokemonRepository pokemonRepository;
    @Autowired OperationRepository operationRepository;
    @Autowired IntegrationControlService integrationControl;

    @BeforeEach
    void reset() {
        POKEAPI.resetAll();
        operationRepository.deleteAll();
        pokemonRepository.deleteAll();
        PUBLISHED.set(0);
        integrationControl.setEnabled(IntegrationControlService.POKEAPI, true);
    }

    @Test
    @Order(1)
    void supportsPokemonCrudAndValidation() throws Exception {
        mvc.perform(post("/api/pokemon").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Eevee","pokedexId":133,"height":3,"weight":65}
                                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("eevee"));
        mvc.perform(get("/api/pokemon")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        Long id = pokemonRepository.findByNameIgnoreCase("eevee").orElseThrow().getId();
        mvc.perform(get("/api/pokemon/{id}", id)).andExpect(status().isOk()).andExpect(jsonPath("$.pokedexId").value(133));
        mvc.perform(put("/api/pokemon/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"vaporeon","pokedexId":134,"height":10,"weight":290}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("vaporeon"));
        mvc.perform(post("/api/pokemon").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"height\":-1,\"weight\":10}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/pokemon/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/pokemon/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    @Order(2)
    void importsPokemonAndUsesControlledUpsert() throws Exception {
        POKEAPI.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/api/v2/pokemon/pikachu")).willReturn(okJson(
                "{\"id\":25,\"name\":\"pikachu\",\"height\":4,\"weight\":60}")));
        mvc.perform(post("/api/pokemon/import/pikachu")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("pikachu")).andExpect(jsonPath("$.pokedexId").value(25));
        POKEAPI.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/api/v2/pokemon/pikachu")).willReturn(okJson(
                "{\"id\":25,\"name\":\"pikachu\",\"height\":4,\"weight\":61}")));
        mvc.perform(post("/api/pokemon/import/pikachu")).andExpect(status().isOk())
                .andExpect(jsonPath("$.weight").value(61));
    }

    @Test
    @Order(3)
    void doesNotRetryPermanent404AndReturns202OnlyAfterQueueAcceptance() throws Exception {
        POKEAPI.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/api/v2/pokemon/missingno")).willReturn(notFound()));
        mvc.perform(post("/api/pokemon/import/missingno")).andExpect(status().isNotFound());

        POKEAPI.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/api/v2/pokemon/rate-limited")).willReturn(aResponse().withStatus(429)));
        mvc.perform(post("/api/pokemon/import/rate-limited").header("Idempotency-Key", "rate-limit-0"))
                .andExpect(status().isServiceUnavailable());
        Thread.sleep(10);

        mvc.perform(post("/api/pokemon/import/rate-limited")
                        .header("Idempotency-Key", "queue-this")
                        .header("X-Correlation-Id", "demo-correlation"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.statusUrl").exists());
        var operation = operationRepository.findAll().stream().findFirst().orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(OperationStatus.QUEUED, operation.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals("demo-correlation", operation.getCorrelationId());
        org.junit.jupiter.api.Assertions.assertEquals(1, PUBLISHED.get());
        mvc.perform(get("/api/operations/{id}", operation.getOperationId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("QUEUED"));
        mvc.perform(post("/api/pokemon/import/rate-limited")
                        .header("Idempotency-Key", "queue-this")
                        .header("X-Correlation-Id", "demo-correlation"))
                .andExpect(status().isConflict());
        org.junit.jupiter.api.Assertions.assertEquals(1, PUBLISHED.get());
    }

    @Test
    @Order(4)
    void canDisableAndReenableThePokeApiIntegration() throws Exception {
        mvc.perform(get("/api/integrations/pokeapi"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));

        mvc.perform(put("/api/integrations/pokeapi").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.integrationId").value("pokeapi"))
                .andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(get("/api/integrations/pokeapi"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(post("/api/pokemon/import/pikachu"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.statusUrl").exists());
        org.junit.jupiter.api.Assertions.assertEquals(0, POKEAPI.getAllServeEvents().size());
        org.junit.jupiter.api.Assertions.assertEquals(1, PUBLISHED.get());
        var operation = operationRepository.findAll().stream().findFirst().orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(OperationStatus.QUEUED, operation.getStatus());

        mvc.perform(put("/api/integrations/pokeapi").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
        mvc.perform(get("/api/integrations/pokeapi"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
    }

    @TestConfiguration
    static class TestPublisherConfiguration {
        @Bean @Primary
        OperationPublisher testPublisher() { return envelope -> PUBLISHED.incrementAndGet(); }
    }
}
