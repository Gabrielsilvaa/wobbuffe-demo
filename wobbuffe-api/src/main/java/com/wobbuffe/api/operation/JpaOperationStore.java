package com.wobbuffe.api.operation;

import com.wobbuffe.api.web.ResourceNotFoundException;
import com.wobbuffe.core.OperationEnvelope;
import com.wobbuffe.store.OperationStore;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;

@Repository
// Adaptador da API para o SPI OperationStore do starter, persistindo idempotência e estado da publicação em JPA.
public class JpaOperationStore implements OperationStore {
    private static final EnumSet<OperationStatus> PENDING_STATUSES = EnumSet.of(
            OperationStatus.PENDING, OperationStatus.PUBLISH_PENDING, OperationStatus.QUEUED, OperationStatus.PROCESSING);
    private final OperationRepository repository;

    public JpaOperationStore(OperationRepository repository) { this.repository = repository; }

    @Override
    @Transactional
    public synchronized boolean reserve(String idempotencyKey, String operationId) {
        if (repository.existsByIdempotencyKey(idempotencyKey)) return false;
        repository.saveAndFlush(new OperationEntity(operationId, idempotencyKey, "pokemon.import", "pokeapi", operationId));
        return true;
    }

    @Override
    @Transactional
    public synchronized boolean reserve(OperationEnvelope envelope) {
        if (repository.existsByIdempotencyKey(envelope.idempotencyKey())) return false;
        repository.saveAndFlush(new OperationEntity(envelope.operationId(), envelope.idempotencyKey(), envelope.operationType(),
                envelope.integrationId(), envelope.correlationId()));
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public long pendingCount() { return repository.countByStatusIn(PENDING_STATUSES); }

    @Override
    @Transactional
    public void markPublished(String operationId) { require(operationId).markPublished(); }

    @Override
    @Transactional
    public void markPublishFailed(String operationId, String reason) { require(operationId).markPublishFailed(reason); }

    private OperationEntity require(String operationId) {
        return repository.findById(operationId).orElseThrow(() -> new ResourceNotFoundException("Operation not found: " + operationId));
    }
}
