package com.wobbuffe.api.operation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wobbuffe.api.pokemon.PokeApiClient;
import com.wobbuffe.core.OperationEnvelope;
import com.wobbuffe.consumer.OperationHandler;
import com.wobbuffe.consumer.PermanentOperationException;
import org.springframework.stereotype.Component;

@Component
public class PokemonImportOperationHandler implements OperationHandler {
    private final PokeApiClient pokeApi;
    private final ObjectMapper mapper;

    public PokemonImportOperationHandler(PokeApiClient pokeApi, ObjectMapper mapper) {
        this.pokeApi = pokeApi;
        this.mapper = mapper;
    }

    @Override public String operationType() { return "pokemon.import"; }
    @Override public String integrationId() { return "pokeapi"; }

    @Override
    public JsonNode handle(OperationEnvelope operation) {
        JsonNode name = operation.payload().get("name");
        if (name == null || !name.isTextual() || name.asText().isBlank()) {
            throw new PermanentOperationException("INVALID_POKEMON_NAME", "Payload must include a Pokémon name");
        }
        try {
            return mapper.valueToTree(pokeApi.findPokemon(name.asText()));
        } catch (com.wobbuffe.core.HttpStatusException failure) {
            int status = failure.statusCode();
            if (status >= 400 && status < 500 && status != 408 && status != 429) {
                throw new PermanentOperationException("UPSTREAM_HTTP_" + status,
                        "PokéAPI rejected the operation", failure);
            }
            throw failure;
        }
    }
}
