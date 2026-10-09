package com.wobbuffe.api.integration;

import jakarta.validation.constraints.NotNull;

public record IntegrationToggleRequest(@NotNull Boolean enabled) {
}
