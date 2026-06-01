package br.com.itau.limiteg.orquestrador.config;

import org.springframework.context.annotation.Configuration;

/**
 * Configuração para Feign + Resilience4j.
 * Com resilience4j-spring-boot3, as anotações são interceptadas automaticamente.
 */
@Configuration
public class FeignConfig {
    // Spring Boot 3.2.4 + resilience4j-spring-boot3 autoconfiguram as anotações @CircuitBreaker e @Retry
}



