package br.com.itau.limiteg.orquestrador.domain;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Instant;

@DynamoDbBean
public class ExecucaoOrquestrador {

    // construtor sem args obrigatório para DynamoDB Enhanced Client
    public ExecucaoOrquestrador() {}

    private String idempotencyId;
    private String contratacaoId;
    private StatusExecucao status;
    // parâmetros de execução — necessários para reprocessamento pelo worker
    private String clienteId;
    private String cartaoId;
    private String ativoId;
    private Double valorReservado;
    private Double novoLimite;
    // controle de execução
    private Integer tentativas;
    private String ultimoErro;
    private String correlationId;
    private Instant createdAt;
    private Instant updatedAt;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("idempotency_id")
    public String getIdempotencyId() { return idempotencyId; }
    public void setIdempotencyId(String idempotencyId) { this.idempotencyId = idempotencyId; }

    @DynamoDbSecondaryPartitionKey(indexNames = "contratacao_id-index")
    @DynamoDbAttribute("contratacao_id")
    public String getContratacaoId() { return contratacaoId; }
    public void setContratacaoId(String contratacaoId) { this.contratacaoId = contratacaoId; }

    @DynamoDbConvertedBy(StatusExecucaoConverter.class)
    @DynamoDbAttribute("status")
    public StatusExecucao getStatus() { return status; }
    public void setStatus(StatusExecucao status) { this.status = status; }

    @DynamoDbAttribute("cliente_id")
    public String getClienteId() { return clienteId; }
    public void setClienteId(String clienteId) { this.clienteId = clienteId; }

    @DynamoDbAttribute("cartao_id")
    public String getCartaoId() { return cartaoId; }
    public void setCartaoId(String cartaoId) { this.cartaoId = cartaoId; }

    @DynamoDbAttribute("ativo_id")
    public String getAtivoId() { return ativoId; }
    public void setAtivoId(String ativoId) { this.ativoId = ativoId; }

    @DynamoDbAttribute("valor_reservado")
    public Double getValorReservado() { return valorReservado; }
    public void setValorReservado(Double valorReservado) { this.valorReservado = valorReservado; }

    @DynamoDbAttribute("novo_limite")
    public Double getNovoLimite() { return novoLimite; }
    public void setNovoLimite(Double novoLimite) { this.novoLimite = novoLimite; }

    @DynamoDbAttribute("tentativas")
    public Integer getTentativas() { return tentativas; }
    public void setTentativas(Integer tentativas) { this.tentativas = tentativas; }

    @DynamoDbAttribute("ultimo_erro")
    public String getUltimoErro() { return ultimoErro; }
    public void setUltimoErro(String ultimoErro) { this.ultimoErro = ultimoErro; }

    @DynamoDbAttribute("correlation_id")
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    @DynamoDbConvertedBy(InstantAsStringConverter.class)
    @DynamoDbAttribute("created_at")
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @DynamoDbConvertedBy(InstantAsStringConverter.class)
    @DynamoDbAttribute("updated_at")
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
