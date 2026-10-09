package com.wobbuffe.api.operation;

import java.time.Instant;

public record OperationStatusResponse(String operationId, String operationType, String integrationId,
                                      OperationStatus status, String correlationId, String failureCode,
                                      Instant createdAt, Instant updatedAt) {
    static OperationStatusResponse from(OperationEntity entity) {
        return new OperationStatusResponse(entity.getOperationId(), entity.getOperationType(), entity.getIntegrationId(),
                entity.getStatus(), entity.getCorrelationId(), entity.getFailureCode(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
