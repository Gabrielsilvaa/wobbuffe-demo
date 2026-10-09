package com.wobbuffe.api.pokemon;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("wobbuffe.pokeapi")
public class PokeApiProperties {
    @NotBlank private String baseUrl = "https://pokeapi.co/api/v2";
    @NotNull private Duration connectTimeout = Duration.ofSeconds(2);
    @NotNull private Duration readTimeout = Duration.ofSeconds(3);
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
}
