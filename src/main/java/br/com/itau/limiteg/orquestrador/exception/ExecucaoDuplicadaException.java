package br.com.itau.limiteg.orquestrador.exception;

public class ExecucaoDuplicadaException extends RuntimeException {

    private final String idempotencyId;

    public ExecucaoDuplicadaException(String idempotencyId) {
        super("Execucao ja existe para idempotencyId: " + idempotencyId);
        this.idempotencyId = idempotencyId;
    }

    public String getIdempotencyId() { return idempotencyId; }
}
