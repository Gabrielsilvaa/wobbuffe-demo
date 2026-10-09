package com.wobbuffe.api.pokemon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "pokemon")
public class Pokemon {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 80)
    private String name;
    @Column(name = "pokedex_id")
    private Integer pokedexId;
    @Column(nullable = false)
    private Integer height;
    @Column(nullable = false)
    private Integer weight;

    protected Pokemon() {}

    public Pokemon(String name, Integer pokedexId, Integer height, Integer weight) {
        this.name = canonical(name);
        this.pokedexId = pokedexId;
        this.height = height;
        this.weight = weight;
    }

    public void update(String name, Integer pokedexId, Integer height, Integer weight) {
        this.name = canonical(name);
        this.pokedexId = pokedexId;
        this.height = height;
        this.weight = weight;
    }

    private static String canonical(String value) { return value.trim().toLowerCase(java.util.Locale.ROOT); }
    public Long getId() { return id; }
    public String getName() { return name; }
    public Integer getPokedexId() { return pokedexId; }
    public Integer getHeight() { return height; }
    public Integer getWeight() { return weight; }
}
