package br.com.itau.limiteg.orquestrador.config;

import io.github.resilience4j.retry.event.RetryOnRetryEvent;
import io.github.resilience4j.retry.event.RetryOnSuccessEvent;
import io.github.resilience4j.retry.event.RetryOnErrorEvent;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Listener para eventos de Retry — loga cada tentativa.
 */
@Component
public class RetryEventListener {

    private static final Logger log = LoggerFactory.getLogger(RetryEventListener.class);

    public RetryEventListener(RetryRegistry retryRegistry) {
        // Registra listeners para cada retry
        retryRegistry.getEventPublisher()
                .onEntryAdded(event -> {
                    var retry = event.getAddedEntry();
                    log.info("✓ Retry registrado: {}", retry.getName());

                    // Listener para cada tentativa
                    retry.getEventPublisher()
                            .onRetry(retryEvent ->
                                log.warn("⟲ [Retry: {}] tentativa {} após: {} ({})",
                                        retry.getName(),
                                        retryEvent.getNumberOfRetryAttempts(),
                                        retryEvent.getLastThrowable().getClass().getSimpleName(),
                                        retryEvent.getLastThrowable().getMessage()))
                            .onSuccess(successEvent ->
                                log.info("✓ [Retry: {}] sucesso após {} tentativas",
                                        retry.getName(),
                                        successEvent.getNumberOfRetryAttempts()))
                            .onError(errorEvent ->
                                log.error("✗ [Retry: {}] esgotado após {} tentativas: {}",
                                        retry.getName(),
                                        errorEvent.getNumberOfRetryAttempts(),
                                        errorEvent.getLastThrowable().getMessage()));
                });
    }
}

