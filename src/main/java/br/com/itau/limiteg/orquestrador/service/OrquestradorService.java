package br.com.itau.limiteg.orquestrador.service;

import br.com.itau.limiteg.orquestrador.client.*;
import br.com.itau.limiteg.orquestrador.domain.ExecucaoOrquestrador;
import br.com.itau.limiteg.orquestrador.domain.StatusExecucao;
import br.com.itau.limiteg.orquestrador.exception.ExecucaoDuplicadaException;
import br.com.itau.limiteg.orquestrador.metrics.OrquestradorMetrics;
import br.com.itau.limiteg.orquestrador.repository.OrquestradorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrquestradorService {

    private static final Logger log = LoggerFactory.getLogger(OrquestradorService.class);

    private final OrquestradorRepository repository;
    private final ContratoClient contratoClient;
    private final AtivoClient ativoClient;
    private final CartaoClient cartaoClient;
    private final OrquestradorMetrics metrics;

    public OrquestradorService(
            OrquestradorRepository repository,
            ContratoClient contratoClient,
            AtivoClient ativoClient,
            CartaoClient cartaoClient,
            OrquestradorMetrics metrics
    ) {
        this.repository = repository;
        this.contratoClient = contratoClient;
        this.ativoClient = ativoClient;
        this.cartaoClient = cartaoClient;
        this.metrics = metrics;
    }

    public ExecucaoOrquestrador contratar(OrquestradorRequest request) {
        var idempotencyId = buildIdempotencyId(
                request.clienteId(), request.cartaoId(), request.ativoId());

        MDC.put("correlation_id", request.correlationId());
        MDC.put("idempotency_id", idempotencyId);

        try {
            var existente = repository.findByIdempotencyId(idempotencyId);
            if (existente.isPresent()) {
                return tratarExistente(existente.get(), request.correlationId());
            }

            var execucao = iniciarExecucao(request, idempotencyId);
            MDC.put("contratacao_id", execucao.getContratacaoId());

            return executarFluxo(execucao, request.correlationId());

        } finally {
            MDC.clear();
        }
    }

    public Optional<ExecucaoOrquestrador> consultarPorContratacaoId(String contratacaoId) {
        return repository.findByContratacaoId(contratacaoId);
    }

    /**
     * Usado pelo ReconciliacaoWorker — retoma execução existente pelo idempotencyId.
     * Não precisa dos dados de negócio — o estado está no DynamoDB.
     */
    public ExecucaoOrquestrador retomarExecucao(String idempotencyId, String correlationId) {
        var execucao = repository.findByIdempotencyId(idempotencyId)
                .orElseThrow(() -> new IllegalStateException(
                        "Execucao nao encontrada para reconciliacao: " + idempotencyId));

        MDC.put("correlation_id", correlationId);
        MDC.put("idempotency_id", idempotencyId);
        MDC.put("contratacao_id", execucao.getContratacaoId());

        try {
            return executarFluxo(execucao, correlationId);
        } finally {
            MDC.clear();
        }
    }

    // ── Idempotência ──────────────────────────────────────────────────────────

    private ExecucaoOrquestrador tratarExistente(ExecucaoOrquestrador existente,
                                                 String correlationId) {
        if (existente.getStatus().isFinal()) {
            log.info("Execucao ja finalizada status={}", existente.getStatus());
            metrics.registrarDuplicata(existente.getIdempotencyId());
            throw new ExecucaoDuplicadaException(existente.getIdempotencyId());
        }

        log.info("Retomando execucao status={} tentativa={}",
                existente.getStatus(), existente.getTentativas());
        metrics.registrarTentativa(existente.getIdempotencyId(), existente.getTentativas());
        return executarFluxo(existente, correlationId);
    }

    private ExecucaoOrquestrador iniciarExecucao(OrquestradorRequest request,
                                                 String idempotencyId) {
        var execucao = new ExecucaoOrquestrador();
        execucao.setIdempotencyId(idempotencyId);
        execucao.setContratacaoId(UUID.randomUUID().toString());
        execucao.setStatus(StatusExecucao.RECEBIDA);
        // parâmetros de execução — persistidos para reprocessamento pelo worker
        execucao.setClienteId(request.clienteId());
        execucao.setCartaoId(request.cartaoId());
        execucao.setAtivoId(request.ativoId());
        execucao.setValorReservado(request.valorReservado());
        execucao.setNovoLimite(request.novoLimite());
        execucao.setTentativas(0);
        execucao.setCorrelationId(request.correlationId());
        execucao.setCreatedAt(Instant.now());
        execucao.setUpdatedAt(Instant.now());

        repository.save(execucao);
        log.info("Execucao iniciada contratacao_id={}", execucao.getContratacaoId());
        return execucao;
    }

    // ── Máquina de estados ────────────────────────────────────────────────────

    private ExecucaoOrquestrador executarFluxo(ExecucaoOrquestrador execucao,
                                               String correlationId) {
        try {
            if (execucao.getStatus().ordinal() < StatusExecucao.CONTRATO_CRIADO.ordinal()) {
                executarPassoContratacao(execucao, correlationId);
            }

            if (execucao.getStatus().ordinal() < StatusExecucao.ATIVO_RESERVADO.ordinal()) {
                executarPassoReservaAtivo(execucao, correlationId);
            }

            if (execucao.getStatus().ordinal() < StatusExecucao.LIMITE_PROVISIONADO.ordinal()) {
                executarPassoLimite(execucao, correlationId);
            }

            transicionarStatus(execucao, StatusExecucao.CONCLUIDA, correlationId);
            log.info("Execucao concluida contratacao_id={}", execucao.getContratacaoId());
            return execucao;

        } catch (Exception e) {
            log.error("Falha na execucao contratacao_id={} status={} erro={}",
                    execucao.getContratacaoId(), execucao.getStatus(), e.getMessage());
            // Preserva o status do passo onde parou para que o worker possa retomar
            repository.atualizarStatusComErro(
                    execucao.getIdempotencyId(), execucao.getStatus(), e.getMessage());
            throw e;
        }
    }

    // ── Passos ────────────────────────────────────────────────────────────────

    private void executarPassoContratacao(ExecucaoOrquestrador execucao, String correlationId) {
        transicionarStatus(execucao, StatusExecucao.VALIDANDO, correlationId);
        var inicio = Instant.now();

        try {
            ResponseEntity<CriarContratacaoResponse> response = contratoClient.criarContratacao(
                    correlationId,
                    execucao.getIdempotencyId(),
                    new CriarContratacaoRequest(
                            execucao.getClienteId(),
                            execucao.getCartaoId(),
                            execucao.getAtivoId(),
                            execucao.getValorReservado(),
                            execucao.getNovoLimite(),
                            execucao.getIdempotencyId()
                    )
            );

            validarResposta(response, "POST /contratacao");

            var body = response.getBody();
            if (body != null) {
                execucao.setContratacaoId(body.contratacaoId());
            }
            transicionarStatus(execucao, StatusExecucao.CONTRATO_CRIADO, correlationId);
            metrics.registrarPassoSucesso("contrato_criado", correlationId);

        } catch (Exception e) {
            log.error("Falha na criacao do contrato contratacao_id={} status={} erro={}",
                    execucao.getContratacaoId(), execucao.getStatus(), e.getMessage());
            metrics.registrarPassoFalha("contrato_criado", correlationId, e.getMessage());
            throw e;
        } finally {
            metrics.registrarLatencia("contrato_criado", Duration.between(inicio, Instant.now()), correlationId);
        }
    }

    private void executarPassoReservaAtivo(ExecucaoOrquestrador execucao, String correlationId) {
        var inicio = Instant.now();

        try {
            log.info("Iniciando chamada ao cliente de Ativo — idempotency_id={}",
                    execucao.getIdempotencyId());

            ResponseEntity<Void> response = ativoClient.reservarAtivo(
                    correlationId,
                    execucao.getIdempotencyId(),
                    new ReservarAtivoRequest(
                            execucao.getClienteId(),
                            execucao.getAtivoId(),
                            execucao.getContratacaoId()
                    )
            );

            log.info("Chamada ao cliente de Ativo concluída com sucesso");
            validarResposta(response, "POST /reserva-ativo");

            transicionarStatus(execucao, StatusExecucao.ATIVO_RESERVADO, correlationId);
            metrics.registrarPassoSucesso("ativo_reservado", correlationId);

        } catch (Exception e) {
            log.error("Falha ao chamar cliente de Ativo: {} — tentaremos retomar depois",
                    e.getClass().getSimpleName() + ": " + e.getMessage(), e);
            metrics.registrarPassoFalha("ativo_reservado", correlationId, e.getMessage());
            throw e;
        } finally {
            metrics.registrarLatencia("ativo_reservado", Duration.between(inicio, Instant.now()), correlationId);
        }
    }

    private void executarPassoLimite(ExecucaoOrquestrador execucao, String correlationId) {
        var inicio = Instant.now();

        try {
            ResponseEntity<Void> response = cartaoClient.provisionarLimite(
                    correlationId,
                    execucao.getIdempotencyId(),
                    new ProvisionarLimiteRequest(
                            execucao.getCartaoId(),
                            execucao.getNovoLimite(),
                            execucao.getContratacaoId()
                    )
            );

            validarResposta(response, "PUT /limite-cartao");

            transicionarStatus(execucao, StatusExecucao.LIMITE_PROVISIONADO, correlationId);
            metrics.registrarPassoSucesso("limite_provisionado", correlationId);

        } catch (Exception e) {
            log.error("Falha no provisionamento do limite contratacao_id={} status={} erro={}",
                    execucao.getContratacaoId(), execucao.getStatus(), e.getMessage());
            metrics.registrarPassoFalha("limite_provisionado", correlationId, e.getMessage());
            throw e;
        } finally {
            metrics.registrarLatencia("limite_provisionado", Duration.between(inicio, Instant.now()), correlationId);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void validarResposta(ResponseEntity<?> response, String endpoint) {
        if (response == null || !response.getStatusCode().is2xxSuccessful()) {
            var status = response != null ? response.getStatusCode().value() : -1;
            throw new IllegalStateException(
                    String.format("Resposta inesperada de %s — status=%d", endpoint, status));
        }
    }

    private void transicionarStatus(ExecucaoOrquestrador execucao,
                                    StatusExecucao novo,
                                    String correlationId) {
        var anterior = execucao.getStatus();
        execucao.setStatus(novo);
        repository.atualizarStatus(execucao.getIdempotencyId(), novo);
        metrics.registrarTransicaoStatus(anterior, novo, correlationId);
        log.info("Transicao de estado de={} para={} contratacao_id={}",
                anterior, novo, execucao.getContratacaoId());
    }

    private String buildIdempotencyId(String clienteId, String cartaoId, String ativoId) {
        return String.format("%s-%s-%s", clienteId, cartaoId, ativoId);
    }
}
