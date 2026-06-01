package br.com.itau.limiteg.orquestrador.controller;

import br.com.itau.limiteg.orquestrador.domain.ExecucaoOrquestrador;
import br.com.itau.limiteg.orquestrador.domain.StatusExecucao;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrquestradorResponseTest {

    @Test
    void from_deveMapearTodosCamposDaExecucao() {
        var exec = new ExecucaoOrquestrador();
        exec.setContratacaoId("ctrt-001");
        exec.setIdempotencyId("cli01-cart01-ativ01");
        exec.setStatus(StatusExecucao.CONCLUIDA);

        var response = OrquestradorResponse.from(exec);

        assertThat(response.contratacaoId()).isEqualTo("ctrt-001");
        assertThat(response.idempotencyId()).isEqualTo("cli01-cart01-ativ01");
        assertThat(response.status()).isEqualTo("CONCLUIDA");
        assertThat(response.mensagem()).isNull();
    }

    @Test
    void from_statusDiferentes_deveRefletirNomeDoEnum() {
        for (StatusExecucao status : StatusExecucao.values()) {
            var exec = new ExecucaoOrquestrador();
            exec.setStatus(status);
            exec.setContratacaoId("c");
            exec.setIdempotencyId("id");

            assertThat(OrquestradorResponse.from(exec).status()).isEqualTo(status.name());
        }
    }

    @Test
    void duplicata_deveCriarRespostaComStatusDuplicataEMensagem() {
        var response = OrquestradorResponse.duplicata("cli01-cart01-ativ01");

        assertThat(response.contratacaoId()).isNull();
        assertThat(response.idempotencyId()).isEqualTo("cli01-cart01-ativ01");
        assertThat(response.status()).isEqualTo("DUPLICATA");
        assertThat(response.mensagem()).isNotBlank();
    }
}
