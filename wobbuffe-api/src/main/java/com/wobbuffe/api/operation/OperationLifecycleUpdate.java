package com.wobbuffe.api.operation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

public record OperationLifecycleUpdate(
        @NotNull OperationStatus status,
        @Pattern(regexp = "[a-z0-9-]{1,80}") String name,
        @PositiveOrZero Integer pokedexId,
        @PositiveOrZero Integer height,
        @PositiveOrZero Integer weight,
        @Pattern(regexp = "[A-Z0-9_]{1,100}") String failureCode) {
}
