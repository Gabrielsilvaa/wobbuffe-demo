package com.wobbuffe.api.config;

import com.wobbuffe.api.pokemon.PokeApiProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(PokeApiProperties.class)
public class HttpClientConfiguration {
    // Cliente upstream executado dentro do ResilienceExecutor; os limites da política também ficam em wobbuffe.integrations.pokeapi.
    @Bean
    RestClient pokeApiRestClient(PokeApiProperties properties) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder().baseUrl(properties.getBaseUrl()).requestFactory(requestFactory).build();
    }
}
