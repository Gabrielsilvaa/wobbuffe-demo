package com.wobbuffe.api.pokemon;

import org.springframework.stereotype.Service;

@Service
public class PokemonImportService {
    private final PokeApiClient client;
    private final PokemonPersistenceService persistence;

    public PokemonImportService(PokeApiClient client, PokemonPersistenceService persistence) {
        this.client = client; this.persistence = persistence;
    }

    public ImportOutcome importByName(String name) {
        PokeApiPokemon remote = client.findPokemon(name);
        return persistence.upsertImported(remote.name(), remote.id(), remote.height(), remote.weight());
    }
}
