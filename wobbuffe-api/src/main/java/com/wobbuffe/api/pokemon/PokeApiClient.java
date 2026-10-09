package com.wobbuffe.api.pokemon;

import com.wobbuffe.core.HttpStatusException;
import com.wobbuffe.core.ResilienceExecutionException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class PokeApiClient {
    private final RestClient restClient;

    public PokeApiClient(RestClient pokeApiRestClient) { this.restClient = pokeApiRestClient; }

    public PokeApiPokemon findPokemon(String name) {
        try {
            PokeApiPokemon pokemon = restClient.get().uri("/pokemon/{name}", name).retrieve().body(PokeApiPokemon.class);
            if (pokemon == null) throw new IllegalStateException("PokéAPI returned an empty response");
            return pokemon;
        } catch (RestClientResponseException e) {
            // Converte erros HTTP para o tipo reconhecido pelo classificador de retry/circuit breaker do starter.
            throw new HttpStatusException(e.getStatusCode().value(), "PokéAPI returned HTTP " + e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            Throwable cause = e.getMostSpecificCause();
            // A exceção tipada permite ao starter classificar falhas de transporte como transitórias.
            throw new ResilienceExecutionException("PokéAPI transport failed", cause);
        }
    }
}
