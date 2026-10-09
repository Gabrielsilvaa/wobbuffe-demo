package com.wobbuffe.api.pokemon;

public record PokemonResponse(Long id, String name, Integer pokedexId, Integer height, Integer weight) {
    static PokemonResponse from(Pokemon pokemon) {
        return new PokemonResponse(pokemon.getId(), pokemon.getName(), pokemon.getPokedexId(), pokemon.getHeight(), pokemon.getWeight());
    }
}
