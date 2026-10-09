package com.wobbuffe.api.pokemon;

import com.wobbuffe.api.web.ResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class PokemonService {
    private final PokemonRepository repository;

    public PokemonService(PokemonRepository repository) { this.repository = repository; }

    public PokemonResponse create(CreatePokemonRequest request) {
        if (repository.existsByNameIgnoreCase(request.name())) throw new DataIntegrityViolationException("Pokemon name already exists");
        return PokemonResponse.from(repository.save(new Pokemon(request.name(), request.pokedexId(), request.height(), request.weight())));
    }

    @Transactional(readOnly = true)
    public List<PokemonResponse> findAll() { return repository.findAll().stream().map(PokemonResponse::from).toList(); }

    @Transactional(readOnly = true)
    public PokemonResponse findById(Long id) { return PokemonResponse.from(require(id)); }

    public PokemonResponse update(Long id, UpdatePokemonRequest request) {
        Pokemon pokemon = require(id);
        if (repository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
            throw new DataIntegrityViolationException("Pokemon name already exists");
        }
        pokemon.update(request.name(), request.pokedexId(), request.height(), request.weight());
        return PokemonResponse.from(repository.save(pokemon));
    }

    public void delete(Long id) { repository.delete(require(id)); }

    private Pokemon require(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Pokémon not found: " + id));
    }
}
