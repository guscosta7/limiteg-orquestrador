package br.com.itau.limiteg.orquestrador.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "api-cartoes", url = "${clients.cartoes.url}")
public interface CartaoClient {

    // Ordem correta: Retry por fora (executa primeiro), CircuitBreaker por dentro (executa dentro do retry)
    @Retry(name = "cartoes", fallbackMethod = "provisionarLimiteFallback")
    @CircuitBreaker(name = "cartoes", fallbackMethod = "provisionarLimiteFallback")
    @PutMapping("/limite-cartao")
    ResponseEntity<Void> provisionarLimite(
        @RequestHeader("Correlation-Id") String correlationId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody ProvisionarLimiteRequest request
    );

    // Fallback para provisionarLimite
    @SuppressWarnings({"unused", "all"})
    default ResponseEntity<Void> provisionarLimiteFallback(
            String correlationId,
            String idempotencyKey,
            ProvisionarLimiteRequest request,
            Exception ex) {
        throw new RuntimeException(
                "Serviço de Cartão indisponível após retries e circuit breaker. " +
                "O sistema tentará retomar essa operação automaticamente.",
                ex);
    }

    @Retry(name = "cartoes", fallbackMethod = "listarCartoesElegivesFallback")
    @CircuitBreaker(name = "cartoes", fallbackMethod = "listarCartoesElegivesFallback")
    @GetMapping("/cartoes/elegiveis")
    ResponseEntity<CartoesElegiveisResponse> listarCartoesElegiveis(
        @RequestHeader("Correlation-Id") String correlationId,
        @RequestParam("clienteId") String clienteId
    );

    // Fallback para listarCartoesElegiveis
    @SuppressWarnings({"unused", "all"})
    default ResponseEntity<CartoesElegiveisResponse> listarCartoesElegivesFallback(
            String correlationId,
            String clienteId,
            Exception ex) {
        throw new RuntimeException(
                "Serviço de Cartão indisponível após retries e circuit breaker. " +
                "O sistema tentará retomar essa operação automaticamente.",
                ex);
    }
}
