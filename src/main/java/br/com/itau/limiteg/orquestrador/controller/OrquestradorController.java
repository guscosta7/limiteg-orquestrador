package br.com.itau.limiteg.orquestrador.controller;

import br.com.itau.limiteg.orquestrador.exception.ExecucaoDuplicadaException;
import br.com.itau.limiteg.orquestrador.service.OrquestradorRequest;
import br.com.itau.limiteg.orquestrador.service.OrquestradorService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orquestrador")
public class OrquestradorController {

    private final OrquestradorService service;

    public OrquestradorController(OrquestradorService service) {
        this.service = service;
    }

    @PostMapping("/contratar")
    public ResponseEntity<OrquestradorResponse> contratar(
        @RequestHeader("Correlation-Id") String correlationId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody OrquestradorHttpRequest request
    ) {
        MDC.put("correlation_id", correlationId);
        MDC.put("idempotency_key", idempotencyKey);

        try {
            var execucao = service.contratar(new OrquestradorRequest(
                request.clienteId(),
                request.cartaoId(),
                request.ativoId(),
                request.valorReservado(),
                request.novoLimite(),
                correlationId
            ));

            return ResponseEntity.ok(OrquestradorResponse.from(execucao));

        } catch (ExecucaoDuplicadaException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(OrquestradorResponse.duplicata(e.getIdempotencyId()));
        } finally {
            MDC.clear();
        }
    }

    @GetMapping("/{contratacaoId}")
    public ResponseEntity<OrquestradorResponse> consultar(
        @RequestHeader("Correlation-Id") String correlationId,
        @PathVariable String contratacaoId
    ) {
        return service.consultarPorContratacaoId(contratacaoId)
            .map(e -> ResponseEntity.ok(OrquestradorResponse.from(e)))
            .orElse(ResponseEntity.notFound().build());
    }
}
