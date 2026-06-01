package br.com.itau.limiteg.orquestrador.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "api-ativos", url = "${clients.ativos.url}")
public interface AtivoClient {


    // Ordem correta: Retry por fora (executa primeiro), CircuitBreaker por dentro (executa dentro do retry)
    @Retry(name = "ativos", fallbackMethod = "reservarAtivoFallback")
    @CircuitBreaker(name = "ativos", fallbackMethod = "reservarAtivoFallback")
    @PostMapping("/reserva-ativo")
    ResponseEntity<Void> reservarAtivo(
        @RequestHeader("Correlation-Id") String correlationId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody ReservarAtivoRequest request
    );

    // Fallback para reservarAtivo
    @SuppressWarnings({"unused", "all"})
    default ResponseEntity<Void> reservarAtivoFallback(
            String correlationId,
            String idempotencyKey,
            ReservarAtivoRequest request,
            Exception ex) {
        throw new RuntimeException(
                "Serviço de Ativo indisponível após retries e circuit breaker. " +
                "O sistema tentará retomar essa operação automaticamente.",
                ex);
    }

    @Retry(name = "ativos", fallbackMethod = "listarAtivosElegivesFallback")
    @CircuitBreaker(name = "ativos", fallbackMethod = "listarAtivosElegivesFallback")
    @GetMapping("/ativos/elegiveis")
    ResponseEntity<AtivosElegiveisResponse> listarAtivosElegiveis(
        @RequestHeader("Correlation-Id") String correlationId,
        @RequestParam("clienteId") String clienteId
    );

    // Fallback para listarAtivosElegiveis
    @SuppressWarnings({"unused", "all"})
    default ResponseEntity<AtivosElegiveisResponse> listarAtivosElegivesFallback(
            String correlationId,
            String clienteId,
            Exception ex) {
        throw new RuntimeException(
                "Serviço de Ativo indisponível após retries e circuit breaker. " +
                "O sistema tentará retomar essa operação automaticamente.",
                ex);
    }
}


