package com.wobbuffe.api.pokemon;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePokemonRequest(
        @NotBlank @Size(max = 80) String name,
        @Min(1) Integer pokedexId,
        @NotNull @Min(0) Integer height,
        @NotNull @Min(0) Integer weight) {}
