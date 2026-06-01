package br.com.itau.limiteg.orquestrador.client;

public record ReservarAtivoRequest(
    String clienteId,
    String ativoId,
    String contratacaoId
) {}
