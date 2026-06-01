#!/bin/bash
set -e

echo ">>> Criando tabela DynamoDB limiteg-orquestrador-execucoes..."

awslocal dynamodb create-table \
  --table-name limiteg-orquestrador-execucoes \
  --attribute-definitions \
    AttributeName=idempotency_id,AttributeType=S \
    AttributeName=contratacao_id,AttributeType=S \
  --key-schema \
    AttributeName=idempotency_id,KeyType=HASH \
  --global-secondary-indexes '[
    {
      "IndexName": "contratacao_id-index",
      "KeySchema": [{"AttributeName":"contratacao_id","KeyType":"HASH"}],
      "Projection": {"ProjectionType":"ALL"},
      "ProvisionedThroughput": {"ReadCapacityUnits":5,"WriteCapacityUnits":5}
    }
  ]' \
  --provisioned-throughput ReadCapacityUnits=5,WriteCapacityUnits=5 \
  && echo ">>> Tabela limiteg-orquestrador-execucoes criada com sucesso." \
  || echo ">>> Tabela ja existe, ignorando."

echo ">>> Init LocalStack concluido."
