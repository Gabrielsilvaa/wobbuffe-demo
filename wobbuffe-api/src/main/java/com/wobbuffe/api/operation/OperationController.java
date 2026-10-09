package com.wobbuffe.api.operation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operations")
public class OperationController {
    private final OperationQueryService service;
    public OperationController(OperationQueryService service) { this.service = service; }
    @GetMapping("/{operationId}")
    public OperationStatusResponse get(@PathVariable String operationId) { return service.get(operationId); }
}
