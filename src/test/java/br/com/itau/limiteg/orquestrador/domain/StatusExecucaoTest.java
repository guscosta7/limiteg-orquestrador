package br.com.itau.limiteg.orquestrador.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class StatusExecucaoTest {

    @Test
    void isFinal_concluida_deveRetornarTrue() {
        assertThat(StatusExecucao.CONCLUIDA.isFinal()).isTrue();
    }

    @Test
    void isFinal_falha_deveRetornarTrue() {
        assertThat(StatusExecucao.FALHA.isFinal()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = StatusExecucao.class,
            names = {"RECEBIDA", "VALIDANDO", "CONTRATO_CRIADO", "ATIVO_RESERVADO", "LIMITE_PROVISIONADO"})
    void isFinal_statusIntermediarios_deveRetornarFalse(StatusExecucao status) {
        assertThat(status.isFinal()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = StatusExecucao.class,
            names = {"RECEBIDA", "VALIDANDO", "CONTRATO_CRIADO", "ATIVO_RESERVADO", "LIMITE_PROVISIONADO"})
    void isPendente_statusIntermediarios_deveRetornarTrue(StatusExecucao status) {
        assertThat(status.isPendente()).isTrue();
    }

    @Test
    void isPendente_concluida_deveRetornarFalse() {
        assertThat(StatusExecucao.CONCLUIDA.isPendente()).isFalse();
    }

    @Test
    void isPendente_falha_deveRetornarFalse() {
        assertThat(StatusExecucao.FALHA.isPendente()).isFalse();
    }

    @Test
    void isFinal_eIsPendente_saoMutuamenteExclusivos() {
        for (StatusExecucao status : StatusExecucao.values()) {
            assertThat(status.isFinal()).isEqualTo(!status.isPendente());
        }
    }

    @Test
    void ordemDosStatusRefleteProgressoDoFluxo() {
        assertThat(StatusExecucao.RECEBIDA.ordinal())
                .isLessThan(StatusExecucao.CONTRATO_CRIADO.ordinal());
        assertThat(StatusExecucao.CONTRATO_CRIADO.ordinal())
                .isLessThan(StatusExecucao.ATIVO_RESERVADO.ordinal());
        assertThat(StatusExecucao.ATIVO_RESERVADO.ordinal())
                .isLessThan(StatusExecucao.LIMITE_PROVISIONADO.ordinal());
        assertThat(StatusExecucao.LIMITE_PROVISIONADO.ordinal())
                .isLessThan(StatusExecucao.CONCLUIDA.ordinal());
    }
}
