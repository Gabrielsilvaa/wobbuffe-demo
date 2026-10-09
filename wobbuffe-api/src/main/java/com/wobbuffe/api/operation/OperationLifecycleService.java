package com.wobbuffe.api.operation;

import com.wobbuffe.api.pokemon.PokemonPersistenceService;
import com.wobbuffe.api.web.BadRequestException;
import com.wobbuffe.api.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationLifecycleService {
    private final OperationRepository operations;
    private final PokemonPersistenceService pokemonPersistence;

    public OperationLifecycleService(OperationRepository operations, PokemonPersistenceService pokemonPersistence) {
        this.operations = operations;
        this.pokemonPersistence = pokemonPersistence;
    }

    @Transactional
    public void update(String operationId, OperationLifecycleUpdate update) {
        OperationEntity operation = operations.findById(operationId)
                .orElseThrow(() -> new ResourceNotFoundException("Operation not found: " + operationId));
        switch (update.status()) {
            case PROCESSING -> operation.markProcessing();
            case SUCCEEDED -> {
                if (update.name() == null || update.pokedexId() == null || update.height() == null || update.weight() == null) {
                    throw new BadRequestException("A successful worker result must include Pokémon data");
                }
                if (!operation.getOperationType().equals("pokemon.import") || !operation.getIntegrationId().equals("pokeapi")) {
                    throw new BadRequestException("The operation does not accept a Pokémon import result");
                }
                if (operation.getStatus() != OperationStatus.FAILED) {
                    pokemonPersistence.upsertImported(update.name(), update.pokedexId(), update.height(), update.weight());
                    operation.markSucceeded();
                }
            }
            case FAILED -> operation.markFailed(update.failureCode());
            default -> throw new BadRequestException("The worker cannot set status " + update.status());
        }
    }
}
