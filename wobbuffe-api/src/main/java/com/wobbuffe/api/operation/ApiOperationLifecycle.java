package com.wobbuffe.api.operation;

import com.fasterxml.jackson.databind.JsonNode;
import com.wobbuffe.core.OperationEnvelope;
import com.wobbuffe.consumer.OperationLifecycle;
import org.springframework.stereotype.Component;

@Component
public class ApiOperationLifecycle implements OperationLifecycle {
    private final OperationLifecycleService updates;

    public ApiOperationLifecycle(OperationLifecycleService updates) { this.updates = updates; }

    @Override
    public void processing(OperationEnvelope operation) {
        updates.update(operation.operationId(), new OperationLifecycleUpdate(OperationStatus.PROCESSING, null, null, null, null, null));
    }

    @Override
    public void succeeded(OperationEnvelope operation, JsonNode result) {
        if (!"pokemon.import".equals(operation.operationType()) || !"pokeapi".equals(operation.integrationId())) {
            throw new IllegalArgumentException("No API lifecycle mapping for this operation type");
        }
        if (result == null || !result.hasNonNull("id") || !result.hasNonNull("name")
                || !result.hasNonNull("height") || !result.hasNonNull("weight")) {
            throw new IllegalArgumentException("PokéAPI result is incomplete");
        }
        updates.update(operation.operationId(), new OperationLifecycleUpdate(OperationStatus.SUCCEEDED,
                result.path("name").asText(), result.path("id").asInt(),
                result.path("height").asInt(), result.path("weight").asInt(), null));
    }

    @Override
    public void failed(OperationEnvelope operation, String failureCode) {
        updates.update(operation.operationId(), new OperationLifecycleUpdate(OperationStatus.FAILED,
                null, null, null, null, normalizeCode(failureCode)));
    }

    private String normalizeCode(String code) {
        if (code == null || !code.matches("[A-Z0-9_]{1,100}")) return "OPERATION_FAILED";
        return code;
    }
}
