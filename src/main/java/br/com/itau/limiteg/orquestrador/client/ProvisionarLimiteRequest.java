package br.com.itau.limiteg.orquestrador.client;

public record ProvisionarLimiteRequest(
    String cartaoId,
    Double novoLimite,
    String contratacaoId
) {}
