package br.com.itau.limiteg.orquestrador.client;

public record CriarContratacaoRequest(
    String clienteId,
    String cartaoId,
    String ativoId,
    Double valorReservado,
    Double novoLimite,
    String idempotencyId
) {}
