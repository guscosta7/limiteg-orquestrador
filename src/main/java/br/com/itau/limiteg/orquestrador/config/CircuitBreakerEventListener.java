package br.com.itau.limiteg.orquestrador.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Listener para eventos de Circuit Breaker — loga mudanças de estado.
 */
@Component
public class CircuitBreakerEventListener {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerEventListener.class);

    public CircuitBreakerEventListener(CircuitBreakerRegistry circuitBreakerRegistry) {
        // Registra listeners para cada circuit breaker
        circuitBreakerRegistry.getEventPublisher()
                .onEntryAdded(event -> {
                    var circuitBreaker = event.getAddedEntry();
                    log.info("✓ Circuit Breaker registrado: {}", circuitBreaker.getName());

                    // Listener para mudanças de estado
                    circuitBreaker.getEventPublisher()
                            .onStateTransition(stateEvent ->
                                log.warn("⚠ [CB: {}] estado: {} → {}",
                                        circuitBreaker.getName(),
                                        stateEvent.getStateTransition().getFromState(),
                                        stateEvent.getStateTransition().getToState()))
                            .onError(errorEvent ->
                                log.debug("✗ [CB: {}] erro: {} (tempo: {} ms)",
                                        circuitBreaker.getName(),
                                        errorEvent.getThrowable().getClass().getSimpleName(),
                                        errorEvent.getElapsedDuration().toMillis()))
                            .onSuccess(successEvent ->
                                log.debug("✓ [CB: {}] sucesso (tempo: {} ms)",
                                        circuitBreaker.getName(),
                                        successEvent.getElapsedDuration().toMillis()));
                });
    }
}

