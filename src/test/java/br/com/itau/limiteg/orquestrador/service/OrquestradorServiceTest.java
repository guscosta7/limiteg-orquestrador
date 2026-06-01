package br.com.itau.limiteg.orquestrador.service;

import br.com.itau.limiteg.orquestrador.client.*;
import br.com.itau.limiteg.orquestrador.domain.ExecucaoOrquestrador;
import br.com.itau.limiteg.orquestrador.domain.StatusExecucao;
import br.com.itau.limiteg.orquestrador.exception.ExecucaoDuplicadaException;
import br.com.itau.limiteg.orquestrador.metrics.OrquestradorMetrics;
import br.com.itau.limiteg.orquestrador.repository.OrquestradorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrquestradorServiceTest {

    @Mock OrquestradorRepository repository;
    @Mock ContratoClient contratoClient;
    @Mock AtivoClient ativoClient;
    @Mock CartaoClient cartaoClient;
    @Mock OrquestradorMetrics metrics;

    @InjectMocks
    OrquestradorService service;

    // ── Happy path ─────────────────────────────────────────────────────────────

    @Test
    void contratar_novaExecucao_devePercorrerFluxoCompletoERetornarConcluida() {
        when(repository.findByIdempotencyId("cli01-cart01-ativ01")).thenReturn(Optional.empty());
        when(contratoClient.criarContratacao(any(), any(), any()))
                .thenReturn(ResponseEntity.ok(new CriarContratacaoResponse("contratacao-abc")));
        when(ativoClient.reservarAtivo(any(), any(), any()))
                .thenReturn(ResponseEntity.ok().build());
        when(cartaoClient.provisionarLimite(any(), any(), any()))
                .thenReturn(ResponseEntity.ok().build());

        var resultado = service.contratar(buildRequest());

        assertThat(resultado.getStatus()).isEqualTo(StatusExecucao.CONCLUIDA);
        assertThat(resultado.getContratacaoId()).isEqualTo("contratacao-abc");
        verify(repository).save(any());
        // VALIDANDO + CONTRATO_CRIADO + ATIVO_RESERVADO + LIMITE_PROVISIONADO + CONCLUIDA = 5 transições
        verify(repository, times(5)).atualizarStatus(any(), any());
    }

    @Test
    void contratar_idempotenciaComExecucaoConcluida_deveLancarExcecaoDuplicada() {
        when(repository.findByIdempotencyId("cli01-cart01-ativ01"))
                .thenReturn(Optional.of(buildExecucao(StatusExecucao.CONCLUIDA)));

        assertThatThrownBy(() -> service.contratar(buildRequest()))
                .isInstanceOf(ExecucaoDuplicadaException.class)
                .hasMessageContaining("cli01-cart01-ativ01");

        verifyNoInteractions(contratoClient, ativoClient, cartaoClient);
    }

    @Test
    void contratar_idempotenciaComExecucaoFalha_deveLancarExcecaoDuplicada() {
        when(repository.findByIdempotencyId("cli01-cart01-ativ01"))
                .thenReturn(Optional.of(buildExecucao(StatusExecucao.FALHA)));

        assertThatThrownBy(() -> service.contratar(buildRequest()))
                .isInstanceOf(ExecucaoDuplicadaException.class);
    }

    // ── Retomada de execução parcial ───────────────────────────────────────────

    @Test
    void contratar_execucaoPendenteEmRECEBIDA_deveExecutarTodosOsPassos() {
        when(repository.findByIdempotencyId("cli01-cart01-ativ01"))
                .thenReturn(Optional.of(buildExecucao(StatusExecucao.RECEBIDA)));
        when(contratoClient.criarContratacao(any(), any(), any()))
                .thenReturn(ResponseEntity.ok(new CriarContratacaoResponse("contratacao-xyz")));
        when(ativoClient.reservarAtivo(any(), any(), any()))
                .thenReturn(ResponseEntity.ok().build());
        when(cartaoClient.provisionarLimite(any(), any(), any()))
                .thenReturn(ResponseEntity.ok().build());

        var resultado = service.contratar(buildRequest());

        assertThat(resultado.getStatus()).isEqualTo(StatusExecucao.CONCLUIDA);
        verify(contratoClient).criarContratacao(any(), any(), any());
        verify(ativoClient).reservarAtivo(any(), any(), any());
        verify(cartaoClient).provisionarLimite(any(), any(), any());
    }

    @Test
    void contratar_execucaoPendenteEmCONTRATO_CRIADO_devePularCriacaoDeContrato() {
        when(repository.findByIdempotencyId("cli01-cart01-ativ01"))
                .thenReturn(Optional.of(buildExecucao(StatusExecucao.CONTRATO_CRIADO)));
        when(ativoClient.reservarAtivo(any(), any(), any()))
                .thenReturn(ResponseEntity.ok().build());
        when(cartaoClient.provisionarLimite(any(), any(), any()))
                .thenReturn(ResponseEntity.ok().build());

        var resultado = service.contratar(buildRequest());

        assertThat(resultado.getStatus()).isEqualTo(StatusExecucao.CONCLUIDA);
        verifyNoInteractions(contratoClient);
        verify(ativoClient).reservarAtivo(any(), any(), any());
        verify(cartaoClient).provisionarLimite(any(), any(), any());
    }

    @Test
    void contratar_execucaoPendenteEmATIVO_RESERVADO_devePularContratacaoEReserva() {
        when(repository.findByIdempotencyId("cli01-cart01-ativ01"))
                .thenReturn(Optional.of(buildExecucao(StatusExecucao.ATIVO_RESERVADO)));
        when(cartaoClient.provisionarLimite(any(), any(), any()))
                .thenReturn(ResponseEntity.ok().build());

        var resultado = service.contratar(buildRequest());

        assertThat(resultado.getStatus()).isEqualTo(StatusExecucao.CONCLUIDA);
        verifyNoInteractions(contratoClient, ativoClient);
    }

    // ── Tratamento de falhas ───────────────────────────────────────────────────

    @Test
    void contratar_falhaNoPassoContratacao_devePersistirErroERethrow() {
        when(repository.findByIdempotencyId("cli01-cart01-ativ01")).thenReturn(Optional.empty());
        when(contratoClient.criarContratacao(any(), any(), any()))
                .thenThrow(new RuntimeException("timeout no contrato"));

        assertThatThrownBy(() -> service.contratar(buildRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("timeout no contrato");

        verify(repository).atualizarStatusComErro(any(), any(), contains("timeout no contrato"));
    }

    @Test
    void contratar_respostaHTTPNao2xxDoContrato_deveLancarIllegalState() {
        when(repository.findByIdempotencyId("cli01-cart01-ativ01")).thenReturn(Optional.empty());
        when(contratoClient.criarContratacao(any(), any(), any()))
                .thenReturn(ResponseEntity.badRequest().build());

        assertThatThrownBy(() -> service.contratar(buildRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("POST /contratacao");
    }

    @Test
    void contratar_respostaHTTPNao2xxDoAtivo_deveLancarIllegalState() {
        when(repository.findByIdempotencyId("cli01-cart01-ativ01")).thenReturn(Optional.empty());
        when(contratoClient.criarContratacao(any(), any(), any()))
                .thenReturn(ResponseEntity.ok(new CriarContratacaoResponse("c-abc")));
        when(ativoClient.reservarAtivo(any(), any(), any()))
                .thenReturn(ResponseEntity.status(503).build());

        assertThatThrownBy(() -> service.contratar(buildRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("POST /reserva-ativo");
    }

    // ── retomarExecucao ────────────────────────────────────────────────────────

    @Test
    void retomarExecucao_execucaoNaoEncontrada_deveLancarIllegalState() {
        when(repository.findByIdempotencyId("id-inexistente")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.retomarExecucao("id-inexistente", "corr-001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("id-inexistente");
    }

    @Test
    void retomarExecucao_execucaoEncontrada_deveExecutarFluxoERetornarConcluida() {
        when(repository.findByIdempotencyId("cli01-cart01-ativ01"))
                .thenReturn(Optional.of(buildExecucao(StatusExecucao.ATIVO_RESERVADO)));
        when(cartaoClient.provisionarLimite(any(), any(), any()))
                .thenReturn(ResponseEntity.ok().build());

        var resultado = service.retomarExecucao("cli01-cart01-ativ01", "corr-001");

        assertThat(resultado.getStatus()).isEqualTo(StatusExecucao.CONCLUIDA);
    }

    // ── consulta ──────────────────────────────────────────────────────────────

    @Test
    void consultarPorContratacaoId_execucaoExistente_deveRetornarOptionalPreenchido() {
        var execucao = buildExecucao(StatusExecucao.CONCLUIDA);
        when(repository.findByContratacaoId("contratacao-abc")).thenReturn(Optional.of(execucao));

        var resultado = service.consultarPorContratacaoId("contratacao-abc");

        assertThat(resultado).isPresent().contains(execucao);
    }

    @Test
    void consultarPorContratacaoId_inexistente_deveRetornarOptionalVazio() {
        when(repository.findByContratacaoId("id-invalido")).thenReturn(Optional.empty());

        var resultado = service.consultarPorContratacaoId("id-invalido");

        assertThat(resultado).isEmpty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private OrquestradorRequest buildRequest() {
        return new OrquestradorRequest("cli01", "cart01", "ativ01", 1000.0, 5000.0, "corr-001");
    }

    private ExecucaoOrquestrador buildExecucao(StatusExecucao status) {
        var exec = new ExecucaoOrquestrador();
        exec.setIdempotencyId("cli01-cart01-ativ01");
        exec.setContratacaoId("contratacao-abc");
        exec.setStatus(status);
        exec.setClienteId("cli01");
        exec.setCartaoId("cart01");
        exec.setAtivoId("ativ01");
        exec.setValorReservado(1000.0);
        exec.setNovoLimite(5000.0);
        exec.setTentativas(1);
        exec.setCorrelationId("corr-001");
        exec.setCreatedAt(Instant.now());
        exec.setUpdatedAt(Instant.now());
        return exec;
    }
}
