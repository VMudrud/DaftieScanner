
Living document. Edit freely as decisions change. Keep it tight.

---

## 1. Goal

Spring Boot service that polls daft.ie every ~1 minute for new rental listings matching per-user filters and notifies the user when matches appear. **Personal use, multi-tenant (no hard limit on tenant count).**

---

## 2. Locked decisions

| Topic | Decision |
|---|---|
| Stack | Java 25 + Spring Boot 4.0.5 |
| Cloud | AWS, single-AZ, EC2 `t4g.nano` |
| Container | Docker — same image runs locally and on EC2 |
| State store | DynamoDB |
| Config source | `.env` / environment variables (no init scripts, no secrets in git) |
| Anthropic / Spring AI | Removed |
| HA | None — 97% SLA accepts ~22h downtime/month |
| Polling cadence | `fixedDelay = 60s` + 40–80s random jitter, page 1 only |
| Sort | `publishDateDesc` (cursor logic depends on this) |
| Dedup | by `listing.id`, with `publishDate` cursor as fallback |
| Re-notify on re-post | **No** |
| Proxies | None on day 1; auto EC2 IP recycle on block; only buy proxies if blocked persistently |
| Email/SMS notifier | Last step; pluggable SPI; channels TBD (SES, Daft contact form, SMS) |
| Alert rate limit | CloudWatch alarm OK→ALARM transitions + app-side `AlertThrottle` (max 1/h/type) |
| Scraping legality | Personal use only; ToS risk acknowledged |

---

## 3. Architecture

```
┌────────────────── EC2 t4g.nano (single-AZ, Docker) ──────────────────┐
│                                                                       │
│ ┌──────────────┐  per-tenant @Scheduled(fixedDelay=60s, jitter)      │
│ │ TenantConfig │─►┌──────────┐    ┌────────────┐    ┌──────────────┐ │
│ │  (from env)  │  │ Scanner  │───►│ DaftClient │───►│ ProxyRouter  │─►daft.ie
│ └──────────────┘  │ Job      │    │ (RestClient)│    │ (direct/v2) │ │
│                   └────┬─────┘    └────────────┘    └──────────────┘ │
│                        ▼                                              │
│                  ┌─────────────┐       ┌────────────┐                │
│                  │ DedupStore  │◄─────►│ DynamoDB   │ 3 tables       │
│                  │ + Cursor    │       │            │                │
│                  └──────┬──────┘       └────────────┘                │
│                         ▼ (new match only)                           │
│                  ┌─────────────┐                                     │
│                  │ Notifier    │ SPI: Logging | Email | DaftForm | SMS
│                  │ Router      │                                     │
│                  └─────────────┘                                     │
│                                                                       │
│ ┌──────────────┐  ┌────────────────┐                                 │
│ │ Actuator     │─►│ CloudWatch     │ alarm:                          │
│ │ /health      │  │ Logs + Metrics │ block_detected ≥ 1 for 5m       │
│ └──────────────┘  └────────────────┘   → SNS → email + Lambda IP recycle
└──────────────────────────────────────────────────────────────────────┘
```

### Components

- **TenantConfig** — `@ConfigurationProperties` bound from `TENANT_N_*` env vars. Validated on startup. Disabled tenants skipped.
- **DaftClient** — `RestClient` POSTing to `gateway.daft.ie/api/v2/ads/listings`. Browser-like headers. Typed DTOs.
- **ProxyRouter** — interface so we can swap transport. Day 1 impl is `DirectProxyRouter` (no proxy).
- **DedupStore** — DynamoDB-backed; `seen(tenantId, listingId)`, `markSeen(...)`.
- **CursorStore** — `lastPostedAt` per tenant; cold-start handling.
- **ScannerJob** — orchestrator: fetch → dedup → persist → notify. One per tenant, separate thread pool.
- **NotifierRouter** — picks impl based on `seller.showContactForm` and tenant config.
- **BlockDetector** — classifies HTTP 403/429/captcha; emits CloudWatch metric.
- **AlertThrottle** — DynamoDB-backed rate limiter for app-side alerts.

---

## 4. Tenant config model

Active tenants declared via `TENANTS_ACTIVE` (comma-separated IDs). Each gets an env-var prefix `TENANT_N_`. No hard limit. All in `.env.example`.

```
TENANTS_ACTIVE=1,2
TENANT_1_ENABLED=true
TENANT_1_EMAIL=you@example.com
TENANT_1_SECTION=residential-to-rent
TENANT_1_RENTAL_PRICE_MIN=1200
TENANT_1_RENTAL_PRICE_MAX=2300
TENANT_1_NUM_BEDS_MIN=1
TENANT_1_NUM_BEDS_MAX=2
TENANT_1_STORED_SHAPE_IDS=42,43      # comma-separated daft.ie area shape IDs
```

Maps to:

```java
record FilterSpec(
    String section,
    Range rentalPrice,        // {from, to}
    Range numBeds,            // {from, to}
    List<String> storedShapeIds
) {}

record Tenant(
    String id,                // "1", "2", "3"
    boolean enabled,
    String email,
    FilterSpec filter
) {}
```

Hardcoded request fields (not configurable): `filters=[{adState:published}]`, `andFilters=[]`, `paging={from:0,pageSize:20}`, `sort=publishDateDesc`, `terms=""`, `geoSearchType=STORED_SHAPES`.

---

## 5. Data model (DynamoDB)

Three tables, on-demand billing (free-tier-friendly at our volume).

### `daftiescanner_seen`
- PK: `tenantId` (S)
- SK: `listingId` (N)
- Attrs: `postedAt` (N, epoch ms), `firstSeenAt` (N, epoch ms), `ttl` (N, +30 days)
- TTL attribute: `ttl` — auto-cleanup of old listings.

### `daftiescanner_cursor`
- PK: `tenantId` (S)
- Attrs: `lastPostedAt` (N), `lastListingId` (N), `updatedAt` (N)
- One item per tenant.

### `daftiescanner_alerts`
- PK: `alertKey` (S, e.g. `tenant1:block_detected`)
- Attrs: `lastFiredAt` (N), `ttl` (N, +24h)
- Used by `AlertThrottle` to dedupe alerts.

---

## 6. Daft.ie API contract

### Request
`POST https://gateway.daft.ie/api/v2/ads/listings?mediaSizes=size720x480&mediaSizes=size72x52`

```json
{
  "section": "residential-to-rent",
  "filters": [{"name": "adState", "values": ["published"]}],
  "andFilters": [],
  "ranges": [
    {"from": "1200", "to": "2300", "name": "rentalPrice"},
    {"from": "1", "to": "2", "name": "numBeds"}
  ],
  "paging": {"from": "0", "pageSize": "20"},
  "geoFilter": {"storedShapeIds": ["42","43"], "geoSearchType": "STORED_SHAPES"},
  "terms": "",
  "sort": "publishDateDesc"
}
```

### Response (only fields we model)

```
listings[].listing:
  id              long          dedup key
  title           string
  publishDate     long          epoch ms — cursor fallback
  price           string        "€2,000 per month"
  numBedrooms     string
  numBathrooms    string
  propertyType    string
  seoFriendlyPath string        prepend "https://www.daft.ie"
  state           string        "PUBLISHED"
  seller:
    name, branch, sellerType
    showContactForm   bool      — gates the Daft-form notifier channel
  media.images[]:
    size720x480, size72x52
  ber.rating        string
  point.coordinates [lng, lat]
  facilities[]:     {key, name}
  primaryAreaId     int
paging:             {totalPages, currentPage, totalResults, ...}
```

---

## 7. Ban-avoidance strategy

Layered, cheapest-first. Only escalate on observed failure.

1. **Polite client** — realistic `User-Agent`, `Accept`, `Accept-Language`, `Referer`. Randomized 40–80s jitter between polls. (~3 req/min total across 3 tenants.)
2. **BlockDetector** — classify responses:
   - HTTP 403 / 429
   - Cloudflare challenge HTML in body
   - Persistent zero-result responses for filters previously yielding hits
3. **Per-tenant exponential backoff** on block: 1h → 4h → 24h. Doesn't punish the other tenants.
4. **EC2 IP recycle on persistent block** — CloudWatch alarm → SNS → Lambda calls `StopInstances` + `StartInstances`. New auto-public-IP from AWS pool. ~30s downtime. Free.
5. **Paid rotating proxy** — only if 1–4 prove insufficient. ProxyRouter interface already in place from M4.

---

## 8. Alerting strategy

### Channels
- **CloudWatch alarm** → SNS topic → email subscription. Native; only fires on state transitions, so it self-dedupes.
- **App-side AlertThrottle** for non-CW alerts (config errors, parse failures): DynamoDB `daftiescanner_alerts`, max 1 alert/hour/type.

### Alarms
| Metric | Trigger | Action |
|---|---|---|
| `daftiescanner.block_detected` | ≥ 1 for 5m | email + Lambda IP recycle |
| `daftiescanner.poll_errors` | ≥ 5 for 10m | email |
| Poll silence | no `daftiescanner.listings_found` for 30m | email |

---

## 9. Deployment

### Local
```
docker compose up        # app + dynamodb-local, loads .env
```

### Production (EC2)
- `t4g.nano` Amazon Linux 2023 ARM, single instance, no Elastic IP (so we can recycle).
- Docker engine + `docker compose up -d` via systemd unit.
- IAM instance role with: DynamoDB (3 tables), CloudWatch Logs+Metrics, SNS publish.
- Secrets in SSM Parameter Store; pulled into env at container start.
- No load balancer, no Auto Scaling Group, no VPC endpoints — all unnecessary at this scale.

### Cost estimate (eu-west-1, monthly)

| Item | $ |
|---|---|
| EC2 t4g.nano on-demand | 3.00 |
| 8 GB gp3 EBS | 0.70 |
| DynamoDB on-demand | ~0 (free tier) |
| CloudWatch logs + 3 alarms | 0.50 |
| SSM Parameter Store standard | 0 |
| Data transfer out | 0.10 |
| **AWS subtotal** | **~4–5** |
| SES (later, per 1k emails) | 0.10 |
| **Total** | **~5/month** without paid proxies |

---

## 10. Milestones

| # | Title | Status |
|---|---|---|
| M1 | Pom cleanup + skeleton | done |
| M2 | Tenant config from env | done |
| M3 | Daft client | done |
| M4 | HTTP transport (timeouts, retry, BlockDetector skeleton, ProxyRouter interface) | done |
| M5 | DynamoDB dedup store | done |
| M6 | Cursor state + cold-start handling | done |
| M7 | Scanner orchestrator + per-tenant scheduling | done |
| M8 | Notifier SPI + LoggingNotifier (no-op default) | done |
| M9 | Block detection + CloudWatch alerting | pending |
| M10 | Docker + EC2 deployment (incl. Maven wrapper) | pending |
| M11 | Real notifier impls (SES / Daft form / SNS-SMS, one at a time) | pending |

### M1 — Pom cleanup + skeleton ✅
- Remove invalid starters (mongo-data, mongo, restclient, actuator-test, ai-anthropic).
- Replace with `spring-boot-starter-web`. Keep actuator, lombok, devtools, test.
- `application.yaml` — actuator exposure, structured log pattern.
- `.gitignore` — `.env*`.
- `.env.example` — full upcoming env-var template.
- Maven wrapper added (pulled forward from M10) so `./mvnw verify` works without a system Maven. Verified: Java 25 + SB 4.0.5, `BUILD SUCCESS`, 1/1 test passing.

### M2 — Tenant config
- `tenants.active` list in YAML (empty default); populated via `TENANTS_ACTIVE` env var.
- `TenantConfiguration` binds `TENANT_N_*` per active ID. No hard limit on tenant count.
- `TenantSlotValidator` — separate class for startup validation; fail fast on missing/invalid fields.
- `FilterSpec`, `Tenant`, `Range` records.
- Skip disabled tenants. Log enabled tenant count on startup.
- Test config in `src/test/resources/application.yaml` (not `.properties`).

### M3 — Daft client ✅
- `DaftClient.search(FilterSpec): SearchResult` via `RestClient`.
- `DaftClientConfiguration` `@Bean` builds the `RestClient` with browser headers (Spring Boot 4 does not auto-configure `RestClient.Builder`).
- All response DTOs are `@JsonIgnoreProperties(ignoreUnknown = true)` records in `client` package.
- `SearchRequest` is package-private with nested records for request sub-objects.
- Unit tests: pure JUnit 5, no Spring context. `MockRestServiceServer.bindTo(builder)` before `builder.build()` gives mock-backed `RestClient`.
- Fixture: `src/test/resources/daft/sample-response.json`.

### M4 — HTTP transport
- Per-request timeouts (connect 5s, read 15s).
- Retry policy: 1 retry on network error, none on HTTP errors (block detector handles those).
- `ProxyRouter` interface + `DirectProxyRouter`. No paid integration yet.
- `BlockDetector` skeleton classifying response into `OK | RATE_LIMITED | BLOCKED | UNKNOWN`.

### M5 — DynamoDB dedup store
- Add `software.amazon.awssdk:dynamodb-enhanced` (with AWS SDK BOM).
- `DedupStore` interface + `DynamoDedupStore` impl using `DynamoDbEnhancedClient`.
- `docker-compose.yml` adds `amazon/dynamodb-local`. App `DAFT_DYNAMO_ENDPOINT` switches to it locally.
- Table auto-create on startup if missing (idempotent).
- Testcontainers integration test.

### M6 — Cursor state + cold start
- `CursorStore` (Dynamo).
- Cold-start rule: first poll for a tenant with no cursor → mark all 20 results as seen WITHOUT notifying. Persist cursor.
- Subsequent polls: notify only if `id` not in seen AND `publishDate > lastPostedAt`.

### M7 — Scanner orchestrator
- `ScannerJob` per tenant.
- `SchedulingConfigurer` with `ThreadPoolTaskScheduler` sized to enabled tenant count.
- Jitter: each job's next-run delay = `60s + random(40..80s)`.
- Per-tenant exponential backoff on `BLOCKED` from BlockDetector.
- Structured INFO log per poll: `tenant=1 found=20 new=2 skipped=18 elapsed=412ms`.

### M8 — Notifier SPI
- `Notifier.notify(Tenant, Listing)` interface.
- `LoggingNotifier` default — logs URL+title+price.
- `NotifierRouter` selects impl(s) based on `seller.showContactForm` + per-tenant channel preference.

### M9 — Block detection + alerting
- Add `micrometer-registry-cloudwatch2`.
- Publish: `daftiescanner.block_detected`, `daftiescanner.poll_errors`, `daftiescanner.listings_found`, `daftiescanner.poll_duration`.
- CloudWatch alarms (Terraform/CDK snippet committed to `infra/`).
- `AlertThrottle` — Dynamo `daftiescanner_alerts` table, 1/h/type.
- Lambda function (TF) for IP recycle on `block_detected` alarm.

### M10 — Docker + EC2 deployment
- ~~Add Maven wrapper.~~ (done in M1)
- `Dockerfile`: multi-stage `maven:3.9-eclipse-temurin-25` → `eclipse-temurin:25-jre`.
- `docker-compose.yml` (local: app + dynamodb-local, loads `.env`).
- `docker-compose.aws.yml` override (production).
- Terraform for: EC2 + IAM role + DynamoDB tables + SNS topic + alarms + Lambda.
- systemd unit on EC2 running `docker compose up -d`.
- SSM Parameter Store + entrypoint script that exports vars before launching JVM.

### M11 — Real notifiers
- Implemented one at a time per user choice. Each = new `Notifier` impl + `NotifierRouter` wiring.
- Likely order: SES email (with attachments from S3) → Daft contact-form replay → SNS-SMS.

---

## 11. Open items

| Topic | Needed by | Question |
|---|---|---|
| Stored shape IDs | M2 testing | User to confirm exact IDs for desired Dublin areas. Sample used 42, 43. |
| Terraform vs manual AWS | M10 | Prefer IaC (Terraform) or manual console + documented? |
| Notifier preferred channel | M11 | Which to build first: SES email, Daft form replay, or SMS? |

---

## 12. Notes for future-Claude

- Always treat the daft.ie endpoint as undocumented. Schema can change. Keep DTOs forgiving (`@JsonIgnoreProperties(ignoreUnknown=true)`).
- Never re-notify on re-post: dedup key is listing `id`, full stop. If user later wants price-drop alerts, that's a new feature, not a tweak.
- Paging logic is intentionally simple (page 1 only). At 60s polling and ~20 results/page, listings flow through fast enough that a missed page is recovered next cycle via cursor.
- Cold-start "swallow first poll" behavior is critical — without it, every fresh deploy floods the user's inbox.
- AlertThrottle and CloudWatch alarm dedup are belt-and-braces. Keep both.

### Code style rules (enforced by user)
- Use YAML for all resource files (main and test). Never create `.properties` files.
- No hard-coded limits on tenant count or similar collections — use config lists.
- Keep `@Bean` methods thin: delegate to private helpers, one concern each.
- Use `forEach` / streams instead of indexed `for` loops.
- Validation logic belongs in a dedicated class, not inline in configuration beans.
- Use named constants for string literals used in property paths and env-var names.
