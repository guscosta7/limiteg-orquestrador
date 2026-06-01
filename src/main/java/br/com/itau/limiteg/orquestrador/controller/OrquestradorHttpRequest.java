package br.com.itau.limiteg.orquestrador.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record OrquestradorHttpRequest(
    @NotBlank String clienteId,
    @NotBlank String cartaoId,
    @NotBlank String ativoId,
    @Positive Double valorReservado,
    @Positive Double novoLimite
) {}
