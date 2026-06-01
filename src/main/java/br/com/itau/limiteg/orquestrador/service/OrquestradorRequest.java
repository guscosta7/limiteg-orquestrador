package br.com.itau.limiteg.orquestrador.service;

public record OrquestradorRequest(
    String clienteId,
    String cartaoId,
    String ativoId,
    Double valorReservado,
    Double novoLimite,
    String correlationId
) {}
