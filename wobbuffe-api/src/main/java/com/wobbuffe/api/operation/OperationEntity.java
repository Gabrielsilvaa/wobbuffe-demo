package com.wobbuffe.api.operation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "operations", indexes = @Index(name = "idx_operations_status", columnList = "status"))
public class OperationEntity {
    @Id
    @Column(name = "operation_id", length = 36)
    private String operationId;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 160)
    private String idempotencyKey;
    @Column(name = "operation_type", nullable = false, length = 100)
    private String operationType;
    @Column(name = "integration_id", nullable = false, length = 100)
    private String integrationId;
    @Column(name = "correlation_id", nullable = false, length = 160)
    private String correlationId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OperationStatus status;
    @Column(name = "failure_code", length = 100)
    private String failureCode;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OperationEntity() {}

    public OperationEntity(String operationId, String idempotencyKey, String operationType,
                           String integrationId, String correlationId) {
        this.operationId = operationId;
        this.idempotencyKey = idempotencyKey;
        this.operationType = operationType;
        this.integrationId = integrationId;
        this.correlationId = correlationId;
        this.status = OperationStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void markPublished() { status = OperationStatus.QUEUED; updatedAt = Instant.now(); failureCode = null; }
    public void markProcessing() {
        if (status == OperationStatus.SUCCEEDED || status == OperationStatus.FAILED) return;
        status = OperationStatus.PROCESSING;
        updatedAt = Instant.now();
    }
    public void markSucceeded() {
        if (status == OperationStatus.FAILED) return;
        status = OperationStatus.SUCCEEDED;
        failureCode = null;
        updatedAt = Instant.now();
    }
    public void markPublishFailed(String code) {
        status = OperationStatus.FAILED;
        failureCode = code == null ? "PUBLISH_FAILED" : code.substring(0, Math.min(code.length(), 100));
        updatedAt = Instant.now();
    }
    public void markFailed(String code) {
        if (status == OperationStatus.SUCCEEDED) return;
        status = OperationStatus.FAILED;
        failureCode = code == null ? "WORKER_FAILED" : code.substring(0, Math.min(code.length(), 100));
        updatedAt = Instant.now();
    }

    public String getOperationId() { return operationId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getOperationType() { return operationType; }
    public String getIntegrationId() { return integrationId; }
    public String getCorrelationId() { return correlationId; }
    public OperationStatus getStatus() { return status; }
    public String getFailureCode() { return failureCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
