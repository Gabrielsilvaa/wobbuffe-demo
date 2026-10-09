package com.wobbuffe.api.integration;

import com.wobbuffe.api.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class IntegrationControlService {
    public static final String POKEAPI = "pokeapi";

    private final AtomicBoolean pokeApiEnabled = new AtomicBoolean(true);

    public IntegrationState getState(String integrationId) {
        requireSupported(integrationId);
        return new IntegrationState(POKEAPI, pokeApiEnabled.get());
    }

    public IntegrationState setEnabled(String integrationId, boolean enabled) {
        requireSupported(integrationId);
        pokeApiEnabled.set(enabled);
        return new IntegrationState(POKEAPI, enabled);
    }

    public boolean isEnabled(String integrationId) {
        requireSupported(integrationId);
        return pokeApiEnabled.get();
    }

    private void requireSupported(String integrationId) {
        if (!POKEAPI.equals(integrationId)) {
            throw new ResourceNotFoundException("Integration not found: " + integrationId);
        }
    }
}
