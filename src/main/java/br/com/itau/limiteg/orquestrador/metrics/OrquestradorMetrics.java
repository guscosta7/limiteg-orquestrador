package br.com.itau.limiteg.orquestrador.metrics;

import br.com.itau.limiteg.orquestrador.domain.StatusExecucao;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class OrquestradorMetrics {

    private static final String PASSO_COUNTER    = "limiteg.orquestrador.passo";
    private static final String STATUS_COUNTER   = "limiteg.orquestrador.status";
    private static final String LATENCIA_TIMER   = "limiteg.orquestrador.latencia";
    private static final String DUPLICATA_COUNTER = "limiteg.orquestrador.duplicata";
    private static final String RETRY_COUNTER    = "limiteg.orquestrador.retry";

    private final MeterRegistry meterRegistry;

    public OrquestradorMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void registrarPassoSucesso(String passo, String correlationId) {
        meterRegistry.counter(PASSO_COUNTER,
            "passo", passo,
            "resultado", "sucesso",
            "correlation_id", correlationId
        ).increment();
    }

    public void registrarPassoFalha(String passo, String correlationId, String erro) {
        meterRegistry.counter(PASSO_COUNTER,
            "passo", passo,
            "resultado", "falha",
            "erro", erro,
            "correlation_id", correlationId
        ).increment();
    }

    public void registrarTransicaoStatus(StatusExecucao de, StatusExecucao para, String correlationId) {
        meterRegistry.counter(STATUS_COUNTER,
            "de", de.name(),
            "para", para.name(),
            "correlation_id", correlationId
        ).increment();
    }

    public void registrarLatencia(String passo, Duration duracao, String correlationId) {
        Timer.builder(LATENCIA_TIMER)
            .tag("passo", passo)
            .tag("correlation_id", correlationId)
            .register(meterRegistry)
            .record(duracao);
    }

    public void registrarDuplicata(String idempotencyId) {
        meterRegistry.counter(DUPLICATA_COUNTER,
            "idempotency_id", idempotencyId
        ).increment();
    }

    public void registrarTentativa(String idempotencyId, int tentativa) {
        meterRegistry.counter(RETRY_COUNTER,
            "idempotency_id", idempotencyId,
            "tentativa", String.valueOf(tentativa)
        ).increment();
    }
}
