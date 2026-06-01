package br.com.itau.limiteg.orquestrador.repository;

import br.com.itau.limiteg.orquestrador.domain.ExecucaoOrquestrador;
import br.com.itau.limiteg.orquestrador.domain.StatusExecucao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class OrquestradorRepository {

    private static final Logger log = LoggerFactory.getLogger(OrquestradorRepository.class);

    private final DynamoDbTable<ExecucaoOrquestrador> table;

    public OrquestradorRepository(DynamoDbEnhancedClient dynamoDbEnhancedClient) {
        this.table = dynamoDbEnhancedClient.table(
            "limiteg-orquestrador-execucoes",
            TableSchema.fromBean(ExecucaoOrquestrador.class)
        );
    }

    public Optional<ExecucaoOrquestrador> findByIdempotencyId(String idempotencyId) {
        return Optional.ofNullable(
            table.getItem(Key.builder().partitionValue(idempotencyId).build())
        );
    }

    public Optional<ExecucaoOrquestrador> findByContratacaoId(String contratacaoId) {
        var index = table.index("contratacao_id-index");
        var results = index.query(r -> r.queryConditional(
            QueryConditional.keyEqualTo(k -> k.partitionValue(contratacaoId))
        ));
        return results.stream()
            .flatMap(page -> page.items().stream())
            .findFirst();
    }

    public void save(ExecucaoOrquestrador contratacao) {
        contratacao.setUpdatedAt(Instant.now());
        table.putItem(contratacao);
    }

    public void atualizarStatus(String idempotencyId, StatusExecucao novoStatus) {
        ExecucaoOrquestrador contratacao = findByIdempotencyId(idempotencyId)
            .orElseThrow(() -> new IllegalStateException(
                "Contratacao nao encontrada: " + idempotencyId));

        contratacao.setStatus(novoStatus);
        contratacao.setUpdatedAt(Instant.now());
        table.putItem(contratacao);
    }

    public void atualizarStatusComErro(String idempotencyId,
                                        StatusExecucao novoStatus,
                                        String erro) {
        ExecucaoOrquestrador contratacao = findByIdempotencyId(idempotencyId)
            .orElseThrow(() -> new IllegalStateException(
                "Contratacao nao encontrada: " + idempotencyId));

        contratacao.setStatus(novoStatus);
        contratacao.setUltimoErro(erro);
        contratacao.setTentativas(contratacao.getTentativas() + 1);
        contratacao.setUpdatedAt(Instant.now());
        table.putItem(contratacao);
    }

    // usado pelo worker de reconciliação — busca contratações presas
    public List<ExecucaoOrquestrador> findByStatusPendente(Instant antes) {
        log.debug("🔍 Buscando execuções pendentes com updated_at antes de: {}", antes);

        var result = table.scan().items().stream()
            .filter(c -> c.getStatus().isPendente())
            .filter(c -> c.getUpdatedAt().isBefore(antes))
            .collect(Collectors.toList());

        if (!result.isEmpty()) {
            log.info("✓ Encontradas {} execuções pendentes no DynamoDB", result.size());
        }

        return result;
    }
}


