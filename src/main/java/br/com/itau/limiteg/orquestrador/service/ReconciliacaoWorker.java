package br.com.itau.limiteg.orquestrador.service;

import br.com.itau.limiteg.orquestrador.domain.StatusExecucao;
import br.com.itau.limiteg.orquestrador.metrics.OrquestradorMetrics;
import br.com.itau.limiteg.orquestrador.repository.OrquestradorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Varre o DynamoDB periodicamente em busca de execuções presas
 * (status pendente sem atualização por mais de N minutos) e reprocessa.
 *
 * Safety net — cobre falhas que escaparam do retry síncrono.
 * Usa o idempotencyId existente para retomar do passo que parou.
 */
@Component
public class ReconciliacaoWorker {

    private static final Logger log = LoggerFactory.getLogger(ReconciliacaoWorker.class);

    private final OrquestradorRepository repository;
    private final OrquestradorService service;
    private final OrquestradorMetrics metrics;

    @Value("${reconciliacao.timeout-minutos:10}")
    private int timeoutMinutos;

    @Value("${reconciliacao.max-tentativas:5}")
    private int maxTentativas;

    public ReconciliacaoWorker(
        OrquestradorRepository repository,
        OrquestradorService service,
        OrquestradorMetrics metrics
    ) {
        this.repository = repository;
        this.service = service;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${reconciliacao.intervalo-ms:60000}")
    public void reconciliar() {
        var limite = Instant.now().minus(timeoutMinutos, ChronoUnit.MINUTES);
        var presas = repository.findByStatusPendente(limite);

        if (presas.isEmpty()) {
            log.debug("Reconciliacao: nenhuma execução presa encontrada (timeout: {} min)", timeoutMinutos);
            return;
        }

        log.warn("🔄 Reconciliacao encontrou {} execucoes presas (atualizadas antes de {} minutos)",
                 presas.size(), timeoutMinutos);

        presas.forEach(execucao -> {
            var idempotencyId = execucao.getIdempotencyId();
            var correlationId = "reconciliacao-" + execucao.getCorrelationId();

            try {
                log.warn("⟳ Reconciliacao: Reprocessando execução presa " +
                         "idempotency_id={} status={} tentativas={} atualizado_em={}",
                        idempotencyId, execucao.getStatus(), execucao.getTentativas(),
                        execucao.getUpdatedAt());

                // retoma pelo idempotencyId — OrquestradorService encontra no DynamoDB
                // e continua do passo que parou sem precisar dos dados de negócio
                service.retomarExecucao(idempotencyId, correlationId);

                log.info("✓ Reconciliacao concluida com sucesso idempotency_id={}", idempotencyId);
                metrics.registrarPassoSucesso("reconciliacao", correlationId);

            } catch (Exception e) {
                log.error("✗ Falha na reconciliacao idempotency_id={} erro={}",
                    idempotencyId, e.getMessage());

                if (execucao.getTentativas() >= maxTentativas) {
                    log.error("⛔ Execucao esgotou {} tentativas — marcando FALHA idempotency_id={}",
                              maxTentativas, idempotencyId);
                    repository.atualizarStatusComErro(
                        idempotencyId,
                        StatusExecucao.FALHA,
                        "Esgotou " + maxTentativas + " tentativas na reconciliacao: " + e.getMessage()
                    );
                    metrics.registrarPassoFalha("reconciliacao", correlationId, e.getMessage());
                } else {
                    log.warn("⚠ Reconciliacao falhará novamente (tentativa {}/{})",
                             execucao.getTentativas() + 1, maxTentativas);
                }
            }
        });
    }
}
