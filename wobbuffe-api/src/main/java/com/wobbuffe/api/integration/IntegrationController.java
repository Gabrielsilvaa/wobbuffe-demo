package com.wobbuffe.api.integration;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations")
public class IntegrationController {
    private final IntegrationControlService service;

    public IntegrationController(IntegrationControlService service) {
        this.service = service;
    }

    @GetMapping("/{integrationId}")
    public IntegrationState getState(@PathVariable String integrationId) {
        return service.getState(integrationId);
    }

    @PutMapping("/{integrationId}")
    public IntegrationState setEnabled(@PathVariable String integrationId,
                                       @Valid @RequestBody IntegrationToggleRequest request) {
        return service.setEnabled(integrationId, request.enabled());
    }
}
