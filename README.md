# Orquestrador — Limite Garantido

Serviço responsável por orquestrar o fluxo de contratação do Limite Garantido,
garantindo **idempotência**, **rastreabilidade** e **resiliência** nas integrações
com as APIs de Contratação, Ativos e Cartões.

---

## Contexto

O Limite Garantido permite que o cliente utilize um ativo financeiro
como garantia para obter um limite adicional no cartão de crédito.

O fluxo de contratação envolve três sistemas distintos que precisam ser
coordenados de forma confiável:

```
POST /contratacao   →   POST /reserva-ativo   →   PUT /limite-cartao
  API Contratação          API Ativos               API Cartões
```

### Problemas que este serviço resolve

| Problema | Solução |
|---|---|
| Limite não atualizado após contratação | Máquina de estados com retry por passo |
| Duplicatas por retry indevido | `idempotency_id` como chave única no DynamoDB |
| Falha silenciosa nas integrações | Circuit breaker + backoff exponencial |
| Falta de visibilidade operacional | Traces com `correlation_id` + métricas no Datadog |

---

## Arquitetura

```
Cliente
  └─► Gateway (Correlation-ID)
        └─► BFF Limite Garantido
              └─► Orquestrador (este serviço)
                    ├─► API Contratação  →  (3) POST /contratacao
                    ├─► API Ativos       →  (4) POST /reserva-ativo
                    ├─► API Cartões      →  (5) PUT /limite-cartao
                    └─► DynamoDB         →  estado por passo
```

### Máquina de estados

```
RECEBIDA → VALIDANDO → CONTRATO_CRIADO → ATIVO_RESERVADO → LIMITE_PROVISIONADO → CONCLUIDA
                                                                                ↘ FALHA
```

Cada transição é persistida no DynamoDB antes de avançar. Em caso de falha,
o retry retoma **do passo que parou**, sem reprocessar o que já foi feito.

### Idempotência

O `idempotency_id` é composto por `client_id + cartao_id + ativo_id`.

- Um ativo só pode garantir uma contratação — regra de negócio
- Qualquer retry com o mesmo `idempotency_id` retoma o estado existente
- A API de Contratação também possui `unique constraint` no banco como segunda camada

---

## Stack

| Tecnologia | Uso |
|---|---|
| Java 21 | Linguagem |
| Spring Boot 3.2 | Framework |
| Spring Cloud OpenFeign | Clientes HTTP |
| Resilience4j | Circuit breaker · retry · timeout |
| Micrometer + Datadog | Métricas e traces |
| AWS DynamoDB | Estado da contratação |
| AWS SQS | Fila de reconciliação e DLQ |

---

## Estrutura do projeto

```
src/main/java/br/com/itau/limiteg/orquestrador/
├── config/
│   └── DynamoDbConfig.java          — client AWS DynamoDB
├── domain/
│   ├── StatusContratacao.java        — enum da máquina de estados
│   └── ContratacaoOrquestrador.java  — entidade DynamoDB
├── repository/
│   └── ContratacaoOrquestradorRepository.java
├── service/
│   └── ContratacaoOrquestradorService.java  — orquestração principal
├── client/
│   └── Clients.java                  — Feign clients com circuit breaker
├── metrics/
│   └── ContratacaoMetrics.java       — MeterRegistry por passo/status/latência
└── exception/
    └── ContratacaoDuplicadaException.java
```

---

## Configuração

### Variáveis de ambiente

| Variável | Descrição | Obrigatória |
|---|---|---|
| `API_CONTRATACAO_URL` | URL da API de Contratação | Sim |
| `API_ATIVOS_URL` | URL da API de Ativos | Sim |
| `API_CARTOES_URL` | URL da API de Cartões | Sim |
| `DATADOG_API_KEY` | Chave da API do Datadog | Sim (prod) |
| `DATADOG_APP_KEY` | Application key do Datadog | Sim (prod) |
| `AWS_REGION` | Região AWS | Sim |
| `ENV` | Ambiente (local/dev/prod) | Não (default: local) |

### Resilience4j — configuração de retry

```yaml
resilience4j:
  retry:
    instances:
      contratacao:
        max-attempts: 3
        wait-duration: 1s
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
        ignore-exceptions:
          - ContratacaoDuplicadaException   # não faz retry em duplicata
```

Tentativas: `1s → 2s → 4s` antes de marcar como `FALHA`.

---

## Rodando localmente

### Pré-requisitos

- Java 21
- Maven 3.9+
- Docker e Docker Compose

### 1. Subir a infra

```bash
docker-compose up -d
```

Isso sobe DynamoDB Local, LocalStack (SQS), WireMock e Datadog Agent.
O container `setup` cria a tabela e as filas automaticamente.

Acompanhe o setup:
```bash
docker-compose logs -f setup
```

### 2. Rodar a aplicação

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

A aplicação aponta para o WireMock (porta 9090) e DynamoDB Local (porta 8000).

### 3. Testar o fluxo completo

```bash
curl -X POST http://localhost:8080/contratacao \
  -H "Content-Type: application/json" \
  -H "Correlation-Id: corr-teste-001" \
  -H "Idempotency-Key: cliente-001+cartao-001+ativo-001" \
  -d '{
    "clienteId": "cliente-001",
    "cartaoId": "cartao-001",
    "ativoId": "ativo-001",
    "valorReservado": 4191.76,
    "novoLimite": 12381.00
  }'
```

**Testando idempotência** — envie a mesma requisição duas vezes.
A segunda deve retornar o estado existente sem criar nova contratação.

**Testando retry** — no WireMock Admin (http://localhost:9090/__admin),
configure a API de Cartões para retornar 500 e observe o circuit breaker abrir.

### 4. Visualizar registros no DynamoDB

Acesse http://localhost:8001 para ver a tabela `contratacoes-orquestrador`.

---

## Métricas

As métricas são exportadas via Micrometer para o Datadog com as tags:
`servico`, `ambiente`, `correlation_id`.

| Métrica | Descrição |
|---|---|
| `limiteg.contratacao.passo` | Contagem de sucesso/falha por passo |
| `limiteg.contratacao.status` | Transições de estado |
| `limiteg.contratacao.latencia` | Latência por passo |
| `limiteg.contratacao.duplicata` | Tentativas de duplicata bloqueadas |
| `limiteg.contratacao.retry` | Retries por tentativa |
| `resilience4j.circuitbreaker.*` | Estado do circuit breaker por cliente |

---

## Tabela DynamoDB

**Nome:** `contratacoes-orquestrador`

**Chave primária:** `idempotency_id` (String) — composta por `client_id-cartao_id-ativo_id`

**GSI:** `contratacao_id-index` — para busca pelo ID de negócio

**Estrutura do item:**

```json
{
  "idempotency_id":  "cliente-001+cartao-001+ativo-001",
  "contratacao_id":  "uuid-gerado",
  "status":          "ATIVO_RESERVADO",
  "cliente_id":      "cliente-001",
  "cartao_id":       "cartao-001",
  "ativo_id":        "ativo-001",
  "valor_reservado": 4191.76,
  "novo_limite":     12381.00,
  "tentativas":      1,
  "ultimo_erro":     null,
  "correlation_id":  "corr-teste-001",
  "created_at":      "2024-01-15T10:30:00Z",
  "updated_at":      "2024-01-15T10:30:05Z"
}
```

---

## Decisões técnicas

**Por que DynamoDB e não Redis?**
O DynamoDB mantém histórico permanente de tentativas e erros — essencial para
rastreabilidade operacional. Redis seria adequado apenas para dedup de curto prazo.

**Por que idempotency_id composto e não UUID?**
Reflete a regra de negócio: um ativo só pode garantir uma contratação. A composição
`client_id-cartao_id-ativo_id` é a chave natural do domínio.

**Por que o orquestrador retoma pelo `ordinal()` do status?**
A ordem dos status no enum reflete a sequência obrigatória do fluxo. Comparar
`ordinal()` é simples, explícito e impossível de executar fora de ordem.

**Por que não Step Functions?**
O fluxo precisa ser síncrono — o cliente espera o "Feito!" na tela.
Step Functions Express Workflow seria uma alternativa válida para evolução futura.
