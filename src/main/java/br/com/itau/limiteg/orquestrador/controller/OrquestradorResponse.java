package br.com.itau.limiteg.orquestrador.controller;

import br.com.itau.limiteg.orquestrador.domain.ExecucaoOrquestrador;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrquestradorResponse(
    String contratacaoId,
    String idempotencyId,
    String status,
    String mensagem
) {
    public static OrquestradorResponse from(ExecucaoOrquestrador e) {
        return new OrquestradorResponse(
            e.getContratacaoId(),
            e.getIdempotencyId(),
            e.getStatus().name(),
            null
        );
    }

    public static OrquestradorResponse duplicata(String idempotencyId) {
        return new OrquestradorResponse(
            null,
            idempotencyId,
            "DUPLICATA",
            "Contratacao ja existe para este ativo e cartao"
        );
    }
}
