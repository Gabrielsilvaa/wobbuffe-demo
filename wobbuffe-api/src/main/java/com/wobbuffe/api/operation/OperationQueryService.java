package com.wobbuffe.api.operation;

import com.wobbuffe.api.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationQueryService {
    private final OperationRepository repository;
    public OperationQueryService(OperationRepository repository) { this.repository = repository; }

    @Transactional(readOnly = true)
    public OperationStatusResponse get(String operationId) {
        return repository.findById(operationId).map(OperationStatusResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Operation not found: " + operationId));
    }
}
