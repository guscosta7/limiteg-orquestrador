package br.com.itau.limiteg.orquestrador.service;

import br.com.itau.limiteg.orquestrador.domain.ExecucaoOrquestrador;
import br.com.itau.limiteg.orquestrador.domain.StatusExecucao;
import br.com.itau.limiteg.orquestrador.metrics.OrquestradorMetrics;
import br.com.itau.limiteg.orquestrador.repository.OrquestradorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliacaoWorkerTest {

    @Mock OrquestradorRepository repository;
    @Mock OrquestradorService service;
    @Mock OrquestradorMetrics metrics;

    @InjectMocks
    ReconciliacaoWorker worker;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(worker, "timeoutMinutos", 10);
        ReflectionTestUtils.setField(worker, "maxTentativas", 5);
    }

    @Test
    void reconciliar_semExecucoesPresas_naoDeveInvocarServiceNemMetrics() {
        when(repository.findByStatusPendente(any())).thenReturn(List.of());

        worker.reconciliar();

        verifyNoInteractions(service, metrics);
    }

    @Test
    void reconciliar_comExecucaoPresa_deveRetomarExecucaoERegistrarSucesso() {
        when(repository.findByStatusPendente(any())).thenReturn(List.of(buildExecucao(1)));

        worker.reconciliar();

        verify(service).retomarExecucao(eq("cli01-cart01-ativ01"), contains("reconciliacao-"));
        verify(metrics).registrarPassoSucesso(eq("reconciliacao"), any());
    }

    @Test
    void reconciliar_falhaAbaixoMaxTentativas_naoDeveMarcaFalhaNemRegistrarMetrica() {
        when(repository.findByStatusPendente(any())).thenReturn(List.of(buildExecucao(2)));
        when(service.retomarExecucao(any(), any()))
                .thenThrow(new RuntimeException("timeout"));

        worker.reconciliar();

        // abaixo do limite: apenas loga warning, sem alterar status e sem métrica de falha
        verify(repository, never()).atualizarStatusComErro(any(), eq(StatusExecucao.FALHA), any());
        verify(metrics, never()).registrarPassoFalha(any(), any(), any());
    }

    @Test
    void reconciliar_falhaAposEsgotar5Tentativas_deveMarcaStatusFalha() {
        when(repository.findByStatusPendente(any())).thenReturn(List.of(buildExecucao(5)));
        when(service.retomarExecucao(any(), any()))
                .thenThrow(new RuntimeException("circuit breaker open"));

        worker.reconciliar();

        verify(repository).atualizarStatusComErro(
                eq("cli01-cart01-ativ01"),
                eq(StatusExecucao.FALHA),
                contains("5")
        );
    }

    @Test
    void reconciliar_multipasExecucoesPresas_deveRetomarTodas() {
        var exec1 = buildExecucao(1);
        var exec2 = buildExecucaoComId("cli02-cart02-ativ02", 1);
        when(repository.findByStatusPendente(any())).thenReturn(List.of(exec1, exec2));

        worker.reconciliar();

        verify(service, times(2)).retomarExecucao(any(), any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ExecucaoOrquestrador buildExecucao(int tentativas) {
        return buildExecucaoComId("cli01-cart01-ativ01", tentativas);
    }

    private ExecucaoOrquestrador buildExecucaoComId(String idempotencyId, int tentativas) {
        var exec = new ExecucaoOrquestrador();
        exec.setIdempotencyId(idempotencyId);
        exec.setContratacaoId("contratacao-abc");
        exec.setStatus(StatusExecucao.VALIDANDO);
        exec.setClienteId("cli01");
        exec.setCartaoId("cart01");
        exec.setAtivoId("ativ01");
        exec.setTentativas(tentativas);
        exec.setCorrelationId("corr-001");
        exec.setCreatedAt(Instant.now());
        exec.setUpdatedAt(Instant.now().minusSeconds(900));
        return exec;
    }
}
