package br.com.itau.limiteg.orquestrador.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "api-contratacao", url = "${clients.contratacao.url}")
public interface ContratoClient {

    // Ordem correta: Retry por fora (executa primeiro), CircuitBreaker por dentro (executa dentro do retry)
    @Retry(name = "contratacao", fallbackMethod = "criarContratacaoFallback")
    @CircuitBreaker(name = "contratacao", fallbackMethod = "criarContratacaoFallback")
    @PostMapping("/contratacao")
    ResponseEntity<CriarContratacaoResponse> criarContratacao(
        @RequestHeader("Correlation-Id") String correlationId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody CriarContratacaoRequest request
    );

    // Fallback para criarContratacao
    @SuppressWarnings({"unused", "all"})
    default ResponseEntity<CriarContratacaoResponse> criarContratacaoFallback(
            String correlationId,
            String idempotencyKey,
            CriarContratacaoRequest request,
            Exception ex) {
        throw new RuntimeException(
                "Serviço de Contratação indisponível após retries e circuit breaker. " +
                "O sistema tentará retomar essa operação automaticamente.",
                ex);
    }
}
