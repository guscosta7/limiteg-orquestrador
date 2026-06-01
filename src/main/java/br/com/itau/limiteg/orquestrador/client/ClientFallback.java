package br.com.itau.limiteg.orquestrador.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Fallback methods para clientes HTTP quando circuit breaker abre ou retries esgotam.
 * Registra o erro e relança a exceção para mais alto handling, evitando swallowing de erros.
 */
@Component
public class ClientFallback {

    private static final Logger log = LoggerFactory.getLogger(ClientFallback.class);

    // ── Contrato ──────────────────────────────────────────────────────────

    public ResponseEntity<CriarContratacaoResponse> criarContratacaoFallback(
            String correlationId,
            String idempotencyKey,
            CriarContratacaoRequest request,
            Exception ex) {
        log.error("Circuit breaker ou timeout na API de Contratacao — correlation_id={} causa={}",
                correlationId, ex.getMessage());
        throw new RuntimeException("Falha ao criar contratacao (circuit breaker aberto ou timeout)", ex);
    }

    // ── Ativo ─────────────────────────────────────────────────────────────

    public ResponseEntity<Void> reservarAtivoFallback(
            String correlationId,
            String idempotencyKey,
            ReservarAtivoRequest request,
            Exception ex) {
        log.error("Circuit breaker ou timeout na API de Ativo — correlation_id={} causa={}",
                correlationId, ex.getMessage());
        throw new RuntimeException("Falha ao reservar ativo (circuit breaker aberto ou timeout)", ex);
    }

    public ResponseEntity<AtivosElegiveisResponse> listarAtivosElegivesFallback(
            String correlationId,
            String clienteId,
            Exception ex) {
        log.error("Circuit breaker ou timeout na API de Ativo (listar elegiveis) — correlation_id={} causa={}",
                correlationId, ex.getMessage());
        throw new RuntimeException("Falha ao listar ativos elegiveis (circuit breaker aberto ou timeout)", ex);
    }

    // ── Cartão ────────────────────────────────────────────────────────────

    public ResponseEntity<Void> provisionarLimiteFallback(
            String correlationId,
            String idempotencyKey,
            ProvisionarLimiteRequest request,
            Exception ex) {
        log.error("Circuit breaker ou timeout na API de Cartao — correlation_id={} causa={}",
                correlationId, ex.getMessage());
        throw new RuntimeException("Falha ao provisionar limite (circuit breaker aberto ou timeout)", ex);
    }

    public ResponseEntity<CartoesElegiveisResponse> listarCartoesElegivesFallback(
            String correlationId,
            String clienteId,
            Exception ex) {
        log.error("Circuit breaker ou timeout na API de Cartao (listar elegiveis) — correlation_id={} causa={}",
                correlationId, ex.getMessage());
        throw new RuntimeException("Falha ao listar cartoes elegiveis (circuit breaker aberto ou timeout)", ex);
    }
}

