# DaftieScanner

Polls daft.ie for new rental listings matching per-tenant filters. Runs on AWS EC2 with DynamoDB for deduplication and CloudWatch for alerting.

## Stack

- Java 25, Spring Boot 4.0.5, Maven
- AWS SDK 2.x (DynamoDB Enhanced Client)
- Micrometer + CloudWatch registry for metrics
- Docker Compose for local development
- Docker multi-stage build (ARM64, eclipse-temurin:25-jre runtime)
- Terraform in `infra/` for EC2, DynamoDB, SSM, CloudWatch alarms, Lambda IP recycle

## Milestones

| M# | Feature | Status |
|----|---------|--------|
| M1–M4 | Daft.ie HTTP client, tenant config, proxy routing, block detection | done |
| M5 | DynamoDB dedup store (`daftiescanner_seen` table), Testcontainers ITs | done |
| M6 | Cursor state + cold-start handling | done |
| M7 | Scanner orchestrator + per-tenant scheduling | done |
| M8 | Notifier SPI + LoggingNotifier | done |
| M9 | Block detection metrics + CloudWatch alerting + AlertThrottle + Lambda IP recycle | done |
| M10 | Docker + EC2 deployment | done |
| M11 | Real notifier impls (SES / Daft form / SNS-SMS) | pending |

## Configuration

| Env var | Default | Description |
|---------|---------|-------------|
| `DAFT_DYNAMO_ENDPOINT` | _(blank — uses IAM role)_ | Local DynamoDB override, e.g. `http://localhost:8000` |
| `DAFT_DYNAMO_SEEN_TABLE` | `daftiescanner_seen` | DynamoDB dedup table name |
| `DAFT_DYNAMO_CURSOR_TABLE` | `daftiescanner_cursor` | DynamoDB cursor table name |
| `DAFT_DYNAMO_ALERTS_TABLE` | `daftiescanner_alerts` | DynamoDB alert-throttle table name |
| `AWS_REGION` | `eu-west-1` | AWS region |
| `MANAGEMENT_METRICS_EXPORT_CLOUDWATCH_ENABLED` | `false` | Set `true` in production to push metrics to CloudWatch |

When `DAFT_DYNAMO_SEEN_TABLE` is blank, all DynamoDB beans are skipped (used in unit tests).
CloudWatch metrics export is disabled by default; enable it in production via the env var above.

## Metrics published (Micrometer → CloudWatch namespace `DaftieScanner`)

| Metric | Type | Tags | Description |
|--------|------|------|-------------|
| `daftiescanner.block_detected` | Counter | `tenantId` | Fires each time a poll is classified as BLOCKED |
| `daftiescanner.poll_errors` | Counter | `tenantId`, `errorType` | Any poll-level error (BLOCKED, RATE_LIMITED, UNKNOWN) |
| `daftiescanner.listings_found` | Counter | `tenantId` | Number of listings returned per poll |
| `daftiescanner.poll_duration` | Timer | `tenantId` | Wall-clock time for each poll cycle |

## CloudWatch alarms (`infra/cloudwatch.tf`)

| Alarm | Trigger | Action |
|-------|---------|--------|
| `daftiescanner_block_detected` | `block_detected` ≥ 1 for 5 min | SNS email + Lambda IP recycle |
| `daftiescanner_poll_errors` | `poll_errors` ≥ 5 for 10 min | SNS email |
| `daftiescanner_poll_silence` | `listings_found` < 1 for 30 min | SNS email |

## Alert throttle (`daftiescanner_alerts` DynamoDB table)

`AlertThrottle` rate-limits app-side alerts to max 1 per hour per alert type using DynamoDB.
PK: `alertKey` (e.g. `tenant1:block_detected`). Attrs: `lastFiredAt` (epoch ms), `ttl` (+24h).

## DynamoDB table schemas

### `daftiescanner_seen`

| Attribute | Type | Role |
|-----------|------|------|
| `tenantId` | String | Partition key |
| `listingId` | Number | Sort key |
| `postedAt` | Number (ms) | Listing post time |
| `firstSeenAt` | Number (ms) | When first polled |
| `ttl` | Number (s) | DynamoDB TTL — 30 days |

### `daftiescanner_cursor`

| Attribute | Type | Role |
|-----------|------|------|
| `tenantId` | String | Partition key |
| `lastPostedAt` | Number (ms) | Cursor timestamp |
| `lastListingId` | Number | Cursor listing ID |
| `updatedAt` | Number (ms) | Last update time |

### `daftiescanner_alerts`

| Attribute | Type | Role |
|-----------|------|------|
| `alertKey` | String | Partition key (e.g. `tenant1:block_detected`) |
| `lastFiredAt` | Number (ms) | Last time this alert fired |
| `ttl` | Number (s) | DynamoDB TTL — 24 hours |

## Local development

```bash
# Full stack (app + DynamoDB local)
docker compose up

# Or run the app from Maven with an already-running dynamodb-local
docker compose up -d dynamodb-local
DAFT_DYNAMO_ENDPOINT=http://localhost:8000 ./mvnw spring-boot:run
```

### DynamoDB Local persistence

By default `dynamodb-local` writes its data to the `dynamodb-data` Docker volume
(`-dbPath ./data`), so tables and items survive `docker compose down` /
container restarts. To switch to the previous in-memory behaviour, set
`DYNAMODB_LOCAL_FLAGS=-inMemory` in `.env`. To wipe persistent data, run
`docker compose down -v`.

## Deployment

### Local

```bash
docker compose up          # starts app + dynamodb-local, loads .env
```

### EC2 (production)

```bash
# 1. Provision infrastructure
cd infra
terraform init
terraform apply -var="alert_email=you@example.com"

# 2. Build and load the image onto the instance (ARM64)
PUBLIC_IP=$(terraform output -raw public_ip)
docker buildx build --platform linux/arm64 -t daftiescanner:latest --load .
docker save daftiescanner:latest | gzip | ssh ec2-user@$PUBLIC_IP "docker load"

# 3. Fill in SSM parameters (repeat for each)
aws ssm put-parameter --name /daftiescanner/prod/TENANT_1_EMAIL \
  --value "your@email.com" --type String --overwrite

# 4. Start the service
ssh ec2-user@$PUBLIC_IP "sudo systemctl start daftiescanner"
```

See `infra/README.md` for full details.

## Running tests

```bash
# Unit tests only (no Docker required)
./mvnw test

# Unit tests + integration tests (Docker required)
./mvnw verify
```

Integration tests use Testcontainers with `amazon/dynamodb-local:2.5.2`.

## Terraform (infra/)

Provisions: EC2 t4g.nano (ARM64, Amazon Linux 2023), IAM role, 3 DynamoDB tables,
SSM Parameter Store entries, SNS topic, CloudWatch alarms, Lambda IP-recycle function.

```bash
cd infra
terraform init
terraform apply -var="alert_email=you@example.com"
# After first apply, re-apply to wire the Lambda:
terraform apply \
  -var="alert_email=you@example.com" \
  -var="instance_id=$(terraform output -raw instance_id)"
```
