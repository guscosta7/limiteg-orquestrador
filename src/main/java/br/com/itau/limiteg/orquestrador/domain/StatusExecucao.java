package br.com.itau.limiteg.orquestrador.domain;

public enum StatusExecucao {
    RECEBIDA,
    VALIDANDO,
    CONTRATO_CRIADO,
    ATIVO_RESERVADO,
    LIMITE_PROVISIONADO,
    CONCLUIDA,
    FALHA;

    public boolean isPendente() {
        return this != CONCLUIDA && this != FALHA;
    }

    public boolean isFinal() {
        return this == CONCLUIDA || this == FALHA;
    }
}
