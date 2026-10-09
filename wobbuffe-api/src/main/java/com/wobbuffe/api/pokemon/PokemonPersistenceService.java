package com.wobbuffe.api.pokemon;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PokemonPersistenceService {
    private final PokemonRepository repository;

    public PokemonPersistenceService(PokemonRepository repository) { this.repository = repository; }

    @Transactional
    public ImportOutcome upsertImported(String name, Integer pokedexId, Integer height, Integer weight) {
        return repository.findByNameIgnoreCase(name)
                .map(existing -> {
                    existing.update(name, pokedexId, height, weight);
                    return new ImportOutcome(PokemonResponse.from(repository.save(existing)), false);
                })
                .orElseGet(() -> new ImportOutcome(
                        PokemonResponse.from(repository.save(new Pokemon(name, pokedexId, height, weight))), true));
    }
}
