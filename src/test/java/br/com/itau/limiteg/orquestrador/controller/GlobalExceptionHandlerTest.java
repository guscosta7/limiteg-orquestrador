package br.com.itau.limiteg.orquestrador.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @ParameterizedTest
    @ValueSource(strings = {"Serviço indisponível", "service unavailable", "circuit breaker open", "timeout reached"})
    void handleRuntimeException_mensagemDeIndisponibilidade_deveRetornar503(String mensagem) {
        var response = handler.handleRuntimeException(new RuntimeException(mensagem));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsKey("message");
    }

    @Test
    void handleRuntimeException_mensagemGenerica_deveRetornar500() {
        var response = handler.handleRuntimeException(new RuntimeException("erro inesperado"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody())
                .containsEntry("status", 500)
                .containsKey("timestamp");
    }

    @Test
    void handleRuntimeException_semMensagem_deveRetornar500ComMensagemPadrao() {
        var response = handler.handleRuntimeException(new RuntimeException((String) null));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsKey("message");
    }

    @Test
    void handleIllegalStateException_deveRetornar400ComMensagemOriginal() {
        var response = handler.handleIllegalStateException(new IllegalStateException("estado inválido"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "estado inválido");
    }

    @Test
    void handleIllegalArgumentException_deveRetornar400() {
        var response = handler.handleIllegalArgumentException(new IllegalArgumentException("argumento ruim"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "argumento ruim");
    }

    @Test
    void handleException_deveRetornar500ComMensagemGenerica() {
        var response = handler.handleException(new Exception("qualquer erro"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("message", "Erro interno no servidor");
    }

    @Test
    void respostaPadrao_deveTerCamposTimestampStatusErrorMessage() {
        var response = handler.handleIllegalStateException(new IllegalStateException("x"));
        var body = response.getBody();
        assertThat(body).containsKeys("timestamp", "status", "error", "message");
    }
}
