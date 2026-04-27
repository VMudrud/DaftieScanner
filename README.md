# DaftieScanner

Polls daft.ie for new rental listings matching per-tenant filters. Runs on AWS EC2 with DynamoDB for deduplication.

## Stack

- Java 25, Spring Boot 4.0.5, Maven
- AWS SDK 2.x (DynamoDB Enhanced Client)
- Docker Compose for local development

## Milestones

| M# | Feature |
|----|---------|
| M1-M4 | Daft.ie HTTP client, tenant config, proxy routing, block detection |
| M5 | DynamoDB dedup store (`daftiescanner_seen` table), Testcontainers integration tests |

## Configuration

| Env var | Default | Description |
|---------|---------|-------------|
| `DAFT_DYNAMO_ENDPOINT` | _(blank — uses IAM role)_ | Local DynamoDB override, e.g. `http://localhost:8000` |
| `DAFT_DYNAMO_SEEN_TABLE` | `daftiescanner_seen` | DynamoDB table name |
| `AWS_REGION` | `eu-west-1` | AWS region |

When `DAFT_DYNAMO_SEEN_TABLE` is blank the DynamoDB beans are skipped entirely (used in unit tests).

## Local development

```bash
docker compose up -d dynamodb-local
DAFT_DYNAMO_ENDPOINT=http://localhost:8000 ./mvnw spring-boot:run
```

## Running tests

```bash
# Unit tests only (no Docker required)
./mvnw test

# Unit tests + integration tests (Docker required)
./mvnw verify
```

Integration tests use Testcontainers with `amazon/dynamodb-local:2.5.2`.

## DynamoDB table schema (`daftiescanner_seen`)

| Attribute | Type | Role |
|-----------|------|------|
| `tenantId` | String | Partition key |
| `listingId` | Number | Sort key |
| `postedAt` | Number (ms) | Listing post time |
| `firstSeenAt` | Number (ms) | When first polled |
| `ttl` | Number (s) | DynamoDB TTL — 30 days from first seen |
