package br.com.itau.limiteg.orquestrador.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * Marcador de configuração para Resilience4j — garante que as anotações sejam interceptadas.
 * Os logs são configurados via application.yml.
 */
@Configuration
public class Resilience4jLoggingConfig {

    private static final Logger log = LoggerFactory.getLogger(Resilience4jLoggingConfig.class);

    public Resilience4jLoggingConfig() {
        log.info("✓ Resilience4j habilitado com suporte a @CircuitBreaker e @Retry");
    }
}



