package com.wobbuffe.api.operation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface OperationRepository extends JpaRepository<OperationEntity, String> {
    boolean existsByIdempotencyKey(String idempotencyKey);
    long countByStatusIn(Collection<OperationStatus> statuses);
}
