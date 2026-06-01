# Infra Local — Orquestrador Limite Garantido

## Serviços

| Serviço          | Porta | Descrição                                        |
|------------------|-------|--------------------------------------------------|
| LocalStack       | 4566  | DynamoDB + SQS locais                            |
| DynamoDB Admin   | 8001  | UI para visualizar e editar registros            |
| WireMock         | 9090  | Mock das APIs externas (Cartões, Ativos, Contratação) |

## Subindo a infra

```bash
docker-compose up -d

# acompanhar criação da tabela e filas
docker-compose logs -f localstack
```

O LocalStack executa automaticamente o script `infra/localstack/init/01-create-dynamo.sh`
ao iniciar, criando:
- Tabela `contratacoes-orquestrador` com GSI `contratacao_id-index`
- Fila `limiteg-contratacao-falha`
- Fila `limiteg-contratacao-falha-dlq`

## Rodando a aplicação

```bash
cp .env.example .env
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Acessando o DynamoDB Admin

Abra http://localhost:8001 — selecione o endpoint `http://localstack:4566`
para ver a tabela `contratacoes-orquestrador`.

## Testando o fluxo

```bash
curl -X POST http://localhost:8080/contratacao \
  -H "Content-Type: application/json" \
  -H "Correlation-Id: corr-001" \
  -H "Idempotency-Key: cliente-001+cartao-001+ativo-001" \
  -d '{
    "clienteId": "cliente-001",
    "cartaoId":  "cartao-001",
    "ativoId":   "ativo-001",
    "valorReservado": 4191.76,
    "novoLimite":    12381.00
  }'
```

**Testando idempotência** — envie a mesma requisição duas vezes.
A segunda deve retornar 409 sem criar nova contratação.

**Verificando a fila SQS**

```bash
awslocal sqs receive-message \
  --queue-url http://localhost:4566/000000000000/limiteg-contratacao-falha \
  --region us-east-1
```

## Parando

```bash
docker-compose down       # mantém volumes
docker-compose down -v    # remove volumes também
```
