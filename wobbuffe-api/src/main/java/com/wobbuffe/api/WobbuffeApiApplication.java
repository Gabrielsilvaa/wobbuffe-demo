package com.wobbuffe.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// O starter é carregado por autoconfiguração Spring Boot e registra ResilienceExecutor, métricas e beans SQS configurados.
@SpringBootApplication
public class WobbuffeApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(WobbuffeApiApplication.class, args);
    }
}
