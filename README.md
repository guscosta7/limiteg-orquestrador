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
| Falta de visibilidade operacional | Logs com `correlation_id` + métricas via Micrometer |

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

O `idempotency_id` é montado internamente pelo serviço a partir de
`clienteId + cartaoId + ativoId` enviados no corpo da requisição — não é
recebido como header.

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
| Micrometer | Métricas (exportador configurável por ambiente) |
| AWS DynamoDB | Estado da execução |

---

## Estrutura do projeto

```
src/main/java/br/com/itau/limiteg/orquestrador/
├── config/
│   ├── DynamoDbConfig.java              — client AWS DynamoDB
│   ├── FeignConfig.java                 — configuração dos clientes HTTP
│   ├── CircuitBreakerEventListener.java — logging de transições do circuit breaker
│   └── RetryEventListener.java          — logging de tentativas de retry
├── domain/
│   ├── StatusExecucao.java              — enum da máquina de estados
│   ├── StatusExecucaoConverter.java     — converter DynamoDB para o enum
│   ├── InstantAsStringConverter.java    — converter DynamoDB para timestamps
│   └── ExecucaoOrquestrador.java        — entidade DynamoDB (estado + parâmetros de execução)
├── repository/
│   └── OrquestradorRepository.java      — acesso à tabela DynamoDB
├── service/
│   ├── OrquestradorService.java         — orquestração principal e máquina de estados
│   ├── OrquestradorRequest.java         — record de entrada do service
│   └── ReconciliacaoWorker.java         — @Scheduled, safety net para execuções presas
├── client/
│   ├── ContratoClient.java, AtivoClient.java, CartaoClient.java — Feign clients
│   ├── ClientFallback.java              — fallback comum dos 3 clients
│   └── *Request.java / *Response.java / *DTO.java — records de payload, um por arquivo
├── controller/
│   ├── OrquestradorController.java      — endpoints HTTP
│   ├── OrquestradorHttpRequest.java     — record de entrada do controller
│   ├── OrquestradorResponse.java        — record de saída
│   └── GlobalExceptionHandler.java      — tratamento centralizado de exceções
├── metrics/
│   └── OrquestradorMetrics.java         — MeterRegistry por passo/status/latência
└── exception/
    └── ExecucaoDuplicadaException.java
```

---

## Configuração

### Variáveis de ambiente

| Variável | Descrição | Obrigatória |
|---|---|---|
| `API_CONTRATACAO_URL` | URL da API de Contratação | Sim |
| `API_ATIVOS_URL` | URL da API de Ativos | Sim |
| `API_CARTOES_URL` | URL da API de Cartões | Sim |
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
          - br.com.itau.limiteg.orquestrador.exception.ExecucaoDuplicadaException
```

Tentativas: `1s → 2s → 4s` antes de marcar como `FALHA`.
`ExecucaoDuplicadaException` é ignorada no retry — duplicata é decisão de
negócio, não falha de infraestrutura, então não faz sentido tentar de novo.

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

Isso sobe DynamoDB Local e WireMock. O container `dynamodb-init` cria a
tabela `limiteg-orquestrador-execucoes` automaticamente após o DynamoDB
Local ficar saudável.

Acompanhe a criação da tabela:
```bash
docker-compose logs -f dynamodb-init
```

### 2. Rodar a aplicação

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

A aplicação aponta para o WireMock (porta 9090) e DynamoDB Local (porta 8000).

### 3. Testar o fluxo completo

```bash
curl -X POST http://localhost:8080/orquestrador/contratar \
  -H "Content-Type: application/json" \
  -H "Correlation-Id: corr-teste-001" \
  -d '{
    "clienteId": "cliente-001",
    "cartaoId": "cartao-001",
    "ativoId": "ativo-001",
    "valorReservado": 4191.76,
    "novoLimite": 12381.00
  }'
```

**Testando idempotência** — envie a mesma requisição duas vezes.
A segunda deve retornar `409 Conflict` sem criar nova execução.

**Testando retry** — no WireMock Admin (http://localhost:9090/__admin),
configure a API de Cartões para retornar 500 e observe o circuit breaker abrir.

**Consultando uma execução** — `GET /orquestrador/{contratacaoId}` usando o
`contratacaoId` retornado na resposta do passo anterior.

### 4. Visualizar registros no DynamoDB

Acesse http://localhost:8001 para ver a tabela `limiteg-orquestrador-execucoes`.

---

## Métricas

As métricas são exportadas via Micrometer com as tags: `servico`, `ambiente`, `correlation_id`.
O `MeterRegistry` é fornecido pelo Spring Boot Actuator; o exportador (Datadog,
Prometheus, etc.) é configuração de ambiente, não está fixado no código.

| Métrica | Descrição |
|---|---|
| `limiteg.orquestrador.passo` | Contagem de sucesso/falha por passo |
| `limiteg.orquestrador.status` | Transições de estado |
| `limiteg.orquestrador.latencia` | Latência por passo |
| `limiteg.orquestrador.duplicata` | Tentativas de duplicata bloqueadas |
| `limiteg.orquestrador.retry` | Retries por tentativa |
| `resilience4j.circuitbreaker.*` | Estado do circuit breaker por cliente |

---

## Tabela DynamoDB

**Nome:** `limiteg-orquestrador-execucoes`

**Chave primária:** `idempotency_id` (String) — composta por `clienteId-cartaoId-ativoId`

**GSI:** `contratacao_id-index` — para busca pelo ID de negócio (usado pela tela de recibo)

**Estrutura do item:**

```json
{
  "idempotency_id":  "cliente-001-cartao-001-ativo-001",
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

> **Nota de design:** esta tabela guarda tanto o estado de execução
> (`status`, `tentativas`, `ultimo_erro`) quanto os parâmetros de negócio
> (`cliente_id`, `cartao_id`, `ativo_id`, `valor_reservado`, `novo_limite`)
> necessários para o `ReconciliacaoWorker` reprocessar uma execução presa
> sem depender de outro sistema estar disponível. Uma evolução natural
> seria separar isso em duas tabelas — uma para estado, outra para os
> parâmetros — já que mudam por razões diferentes (Single Responsibility).
> Optamos por manter em uma tabela só nesta versão para reduzir a
> superfície de mudança e manter o escopo dentro do tempo do case.

---

## Worker de Reconciliação

`ReconciliacaoWorker` roda via `@Scheduled` e busca execuções com status
pendente (não `CONCLUIDA` nem `FALHA`) sem atualização há mais de N minutos.
Para cada uma, chama `OrquestradorService.retomarExecucao`, que consulta o
DynamoDB e continua exatamente do passo que parou.

Após esgotar o número máximo de tentativas, a execução é marcada como
`FALHA` definitiva.

**Por que `@Scheduled` e não uma fila (SQS)?** O fluxo principal da tela 6
precisa ser síncrono — o cliente espera a confirmação na hora. Para a
reconciliação, que é assíncrona por natureza, `@Scheduled` é suficiente
para o volume atual e evita infraestrutura adicional. Em escala maior, o
`scan()` da tabela não performa bem e múltiplas instâncias do worker podem
disputar a mesma execução — uma fila com um consumer por mensagem resolveria
os dois problemas como evolução futura.
