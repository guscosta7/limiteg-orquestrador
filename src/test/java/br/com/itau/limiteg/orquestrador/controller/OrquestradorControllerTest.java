package br.com.itau.limiteg.orquestrador.controller;

import br.com.itau.limiteg.orquestrador.domain.ExecucaoOrquestrador;
import br.com.itau.limiteg.orquestrador.domain.StatusExecucao;
import br.com.itau.limiteg.orquestrador.exception.ExecucaoDuplicadaException;
import br.com.itau.limiteg.orquestrador.service.OrquestradorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class OrquestradorControllerTest {

    @Mock
    OrquestradorService service;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OrquestradorController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void contratar_dadosValidos_deveRetornar200ComContratacaoId() throws Exception {
        when(service.contratar(any())).thenReturn(buildExecucao(StatusExecucao.CONCLUIDA));

        mockMvc.perform(post("/orquestrador/contratar")
                        .header("Correlation-Id", "corr-001")
                        .header("Idempotency-Key", "idem-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contratacaoId").value("contratacao-abc"))
                .andExpect(jsonPath("$.status").value("CONCLUIDA"))
                .andExpect(jsonPath("$.idempotencyId").value("cli01-cart01-ativ01"));
    }

    @Test
    void contratar_execucaoDuplicada_deveRetornar409ComMensagemDuplicata() throws Exception {
        when(service.contratar(any()))
                .thenThrow(new ExecucaoDuplicadaException("cli01-cart01-ativ01"));

        mockMvc.perform(post("/orquestrador/contratar")
                        .header("Correlation-Id", "corr-001")
                        .header("Idempotency-Key", "idem-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("DUPLICATA"))
                .andExpect(jsonPath("$.idempotencyId").value("cli01-cart01-ativ01"));
    }

    @Test
    void contratar_falhaNoServico_deveRetornar500() throws Exception {
        when(service.contratar(any()))
                .thenThrow(new RuntimeException("erro inesperado"));

        mockMvc.perform(post("/orquestrador/contratar")
                        .header("Correlation-Id", "corr-001")
                        .header("Idempotency-Key", "idem-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson()))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void contratar_servicoExternoIndisponivel_deveRetornar503() throws Exception {
        when(service.contratar(any()))
                .thenThrow(new RuntimeException("Serviço de Contratação indisponível após retries"));

        mockMvc.perform(post("/orquestrador/contratar")
                        .header("Correlation-Id", "corr-001")
                        .header("Idempotency-Key", "idem-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson()))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void consultar_contratacaoExistente_deveRetornar200() throws Exception {
        when(service.consultarPorContratacaoId(eq("contratacao-abc")))
                .thenReturn(Optional.of(buildExecucao(StatusExecucao.CONCLUIDA)));

        mockMvc.perform(get("/orquestrador/contratacao-abc")
                        .header("Correlation-Id", "corr-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contratacaoId").value("contratacao-abc"))
                .andExpect(jsonPath("$.status").value("CONCLUIDA"));
    }

    @Test
    void consultar_contratacaoInexistente_deveRetornar404() throws Exception {
        when(service.consultarPorContratacaoId(eq("id-invalido")))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/orquestrador/id-invalido")
                        .header("Correlation-Id", "corr-001"))
                .andExpect(status().isNotFound());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
        exec.setTentativas(0);
        exec.setCorrelationId("corr-001");
        exec.setCreatedAt(Instant.now());
        exec.setUpdatedAt(Instant.now());
        return exec;
    }

    private String requestJson() {
        return """
                {
                    "clienteId": "cli01",
                    "cartaoId": "cart01",
                    "ativoId": "ativ01",
                    "valorReservado": 1000.0,
                    "novoLimite": 5000.0
                }
                """;
    }
}
