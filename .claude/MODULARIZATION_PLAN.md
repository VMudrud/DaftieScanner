# DaftieScanner — Modularization + AI Gate + Agent Outreach

## Context

Today the app is one flat package tree (`client/`, `scanner/`, `notifier/`, `store/`, `config/`) with `ScannerJob` directly calling `EmailNotificationGuard` → `NotifierRouter`. As we add real notifier channels (M11) and a new image-quality AI gate, this monolithic shape will not scale: each new feature would tangle scheduler/poll code with notification + AI logic.

We will refactor the codebase into three feature areas plus a shared core, and decouple them with Spring `ApplicationEvent`s. The new flow is **search → ai-analyzer (gate) → notification (user + agent email)**. Bad-image listings never reach the notifier, so we don't spam ourselves or estate agents with junk.

Decisions confirmed with the user:
- **Layout**: top-level Java packages within a single Maven module / Spring Boot JAR (lightweight; no multi-module pom).
- **Wiring**: Spring `ApplicationEvent`s. Modules don't import each other's classes (only `common/`).
- **AI role**: gate. No notification fires until the analyzer passes.
- **Agent email**: auto-send a templated SMTP email to the listing's agent.

---

## Target package layout

```
com.vmudrud.daftiescanner/
├── DaftiescannerApplication.java
├── common/
│   ├── event/
│   │   ├── NewListingFoundEvent.java        (search → ai)
│   │   ├── ListingApprovedEvent.java        (ai → notification)
│   │   └── ListingRejectedEvent.java        (ai → metrics/log only)
│   ├── tenant/
│   │   ├── Tenant.java                      (moved from config/dto)
│   │   ├── FilterSpec.java                  (moved from config/dto)
│   │   ├── TenantConfiguration.java         (moved from config/)
│   │   └── TenantSlotValidator.java         (moved from config/)
│   ├── listing/
│   │   ├── ListingResult.java               (moved from client/dto — used by all 3 modules)
│   │   └── SearchResult.java                (moved from client/dto)
│   └── store/                               (DynamoDB infra is shared)
│       ├── CursorStore.java
│       ├── DedupStore.java                  (listing dedup AND email dedup methods)
│       ├── DynamoCursorStore.java
│       ├── DynamoDedupStore.java
│       ├── DynamoDbConfiguration.java
│       ├── AlertThrottle.java
│       └── entity/                          (CursorItem, SeenItem, AlertItem)
├── search/
│   ├── client/
│   │   ├── DaftClient.java                  (gateway/ads/listings — search endpoint)
│   │   ├── DaftClientConfiguration.java
│   │   ├── ProxyRouter.java + DirectProxyRouter.java
│   │   ├── BlockDetector.java + BlockStatus.java
│   │   └── dto/SearchRequest.java
│   ├── scheduler/
│   │   ├── ScannerScheduler.java
│   │   ├── ScannerJob.java                  (publishes NewListingFoundEvent — no notifier call)
│   │   ├── ScannerConfiguration.java
│   │   └── TenantBackoff.java
│   └── metrics/
│       └── MetricsPublisher.java            (search-side metrics; other modules add their own later)
├── aianalyzer/
│   ├── ImageAnalyzerListener.java           (@EventListener NewListingFoundEvent)
│   ├── ImageAnalyzer.java                   (interface)
│   ├── ClaudeImageAnalyzer.java             (Anthropic Vision via RestClient)
│   ├── AnalysisResult.java                  (record: passed, score, reason)
│   └── AnalyzerConfiguration.java           (RestClient bean, prompt template, threshold)
└── notification/
    ├── ListingApprovedListener.java         (@EventListener ListingApprovedEvent)
    ├── EmailNotificationGuard.java          (moved from scanner/ — still locks per-email)
    ├── router/
    │   ├── Notifier.java                    (interface)
    │   ├── NotifierRouter.java              (resolves channel(s) per tenant)
    │   └── LoggingNotifier.java             (kept — useful for dry-run)
    ├── agent/
    │   ├── AgentOutreachService.java        (orchestrates fetch + render + send)
    │   ├── ListingDetailsClient.java        (calls daft.ie listing-detail endpoint)
    │   ├── AgentEmailTemplate.java          (renders subject/body)
    │   └── dto/ListingDetails.java          (parsed agent contact)
    └── smtp/
        ├── SmtpEmailSender.java             (wraps Spring JavaMailSender)
        └── MailConfiguration.java
```

**Boundary rule**: `search`, `aianalyzer`, `notification` import only from `common.*` and their own subpackages — never from each other. Static-analyzed via ArchUnit test (added in Phase B).

---

## Flow

### Component view

```
                      ┌──────────────────────────┐
                      │    Spring EventBus       │
                      └────┬───────────────┬─────┘
                           │               │
   publishes               │               │      listens
   NewListingFoundEvent ───┘               └─── ListingApprovedEvent

        ┌──────────┐         ┌──────────────┐         ┌──────────────────┐
        │  search  │ ──→ ev──│  aianalyzer  │ ──→ ev──│  notification    │
        └──────────┘         └──────────────┘         └──────────────────┘
        ScannerJob           ImageAnalyzer-           ListingApprovedListener
        DaftClient           Listener                 ├── EmailGuard (dedup)
        DedupStore           ClaudeImageAnalyzer      ├── NotifierRouter
                             AnalysisResult           │   (Telegram/SMTP user)
                                                      └── AgentOutreachService
                                                           ├── ListingDetailsClient
                                                           ├── AgentEmailTemplate
                                                           └── SmtpEmailSender
```

### Per-listing sequence

```
ScannerJob.poll()
   │
   ├─ DaftClient.search(filter)
   ├─ DedupStore.seen(tenant, id)?      → skip if true
   ├─ DedupStore.markSeen(...)
   ├─ check listing.publishDate > cursor.lastPostedAt → skip if old
   └─ publishEvent(NewListingFoundEvent(tenant, listing))
                              │
                              ▼
ImageAnalyzerListener.onListingFound(event)        [@Async, AI-thread-pool]
   │
   ├─ analyzer.analyze(listing.media.images) → AnalysisResult
   ├─ if !passed → publish(ListingRejectedEvent) + log + metric → STOP
   └─ if  passed → publish(ListingApprovedEvent(tenant, listing, analysis))
                              │
                              ▼
ListingApprovedListener.onApproved(event)          [@Async, notify-thread-pool]
   │
   ├─ EmailGuard.tryNotify(tenant, listing, analysis):
   │     ├─ lock per email
   │     ├─ DedupStore.notifiedByEmail(email, id)?  → skip if true
   │     ├─ NotifierRouter.notify(tenant, listing)  ← user notification
   │     ├─ AgentOutreachService.send(tenant, listing, analysis):  ← agent email
   │     │     ├─ ListingDetailsClient.fetch(listing.id)
   │     │     ├─ AgentEmailTemplate.render(tenant, listing, details)
   │     │     └─ SmtpEmailSender.send(to=details.agentEmail, …)
   │     └─ DedupStore.markNotifiedByEmail(email, id)
```

`@Async` on listeners ensures slow AI/SMTP calls don't block the scheduler thread.

---

## Phases

Each phase is a separate, shippable PR. Existing tests must stay green at each phase boundary.

### Phase A — Pure refactor (no behavior change)

Goal: rearrange files into the new package layout. `git diff` should show only moves + import updates.

- Move files per the layout above. Notable moves:
  - `client/dto/ListingResult.java`, `SearchResult.java` → `common/listing/`
  - `client/dto/SearchRequest.java` → `search/client/dto/`
  - `config/{Tenant,FilterSpec,TenantConfiguration,TenantSlotValidator}.java` → `common/tenant/`
  - `store/*` → `common/store/`
  - `scanner/{ScannerScheduler,ScannerJob,ScannerConfiguration,TenantBackoff,MetricsPublisher}.java` → `search/scheduler/` (`MetricsPublisher` → `search/metrics/`)
  - `scanner/EmailNotificationGuard.java` → `notification/`
  - `notifier/*` → `notification/router/`
  - `client/*` (except DTOs already moved) → `search/client/`
- Update all imports.
- Update `application.yaml` `logging.level.com.vmudrud.daftiescanner.client.DaftClient` → `…search.client.DaftClient`.
- Run `mvn test` — every existing test must pass with no logic changes.

**Verification**: `mvn clean verify` green; app starts locally with `docker-compose up`; one poll cycle produces the same `NOTIFY tenant=…` log line as before.

### Phase B — Introduce events for listings

Goal: replace direct `ScannerJob → EmailGuard → Notifier` call with event publication.

- Add `common/event/NewListingFoundEvent.java` (record).
- `search/scheduler/ScannerJob.java`: replace `emailGuard.tryNotify(...)` with `applicationEventPublisher.publishEvent(new NewListingFoundEvent(tenant, listing))`. Inject `ApplicationEventPublisher`.
- `notification/ListingApprovedListener.java` — TEMPORARY: in this phase the listener subscribes to `NewListingFoundEvent` directly (until Phase C inserts the AI gate). It calls `EmailGuard.tryNotify(...)` exactly as `ScannerJob` did before.
- Annotate the listener with `@Async` and add a small `TaskExecutor` bean (`@EnableAsync` on the application class).
- Add ArchUnit test: `search.*` classes must not depend on `notification.*` or `aianalyzer.*` (and vice versa).
- Update `ScannerJobTest.java` to verify the publisher is called instead of the notifier. Move email-guard tests to a new `notification/ListingApprovedListenerTest.java`.

**Verification**: `mvn test` green; running app produces the same notification log line as Phase A; ArchUnit guards the boundaries.

### Phase C — AI image analyzer (gate)

Goal: wedge ai-analyzer between search and notification.

- Add `common/event/ListingApprovedEvent.java`, `ListingRejectedEvent.java`.
- Add `aianalyzer/`:
  - `AnalysisResult` record: `boolean passed`, `double score`, `String reason`, `Instant analyzedAt`.
  - `ImageAnalyzer` interface: `AnalysisResult analyze(Tenant tenant, ListingResult listing)`.
  - `ClaudeImageAnalyzer` impl using Anthropic Vision API (Claude Sonnet 4.6 via `RestClient`). Configured via `daft.ai.api-key` (env `ANTHROPIC_API_KEY`), `daft.ai.threshold` (default `0.7`), `daft.ai.model` (default `claude-sonnet-4-6`). Use a focused prompt: "Score this rental property's interior on cleanliness/condition/photo quality 0–1; return JSON `{score, reason}`". Cap to first 4 images to keep cost predictable.
  - `NoopImageAnalyzer` impl — always passes; `@ConditionalOnProperty(name = "daft.ai.enabled", havingValue = "false", matchIfMissing = true)` so existing dev setups don't need an API key.
  - `ImageAnalyzerListener` — `@EventListener` on `NewListingFoundEvent`, calls analyzer, publishes `ListingApproved` or `ListingRejected`. `@Async` on AI executor.
  - `AnalyzerConfiguration` — RestClient bean, ai-thread-pool, threshold/model props.
- Switch `ListingApprovedListener` from listening to `NewListingFoundEvent` → `ListingApprovedEvent`. Carry `AnalysisResult` through into the notification (so the user sees the score in the message).
- New tests:
  - `ClaudeImageAnalyzerTest` — `MockRestServiceServer`, fixture JSON response from Claude API (positive: score=0.9, negative: score=0.3).
  - `ImageAnalyzerListenerTest` — Mockito; verify `ListingApprovedEvent` published on pass, `ListingRejectedEvent` on fail.
  - `NoopImageAnalyzerTest` — always returns `passed=true`.

**Verification**: with `daft.ai.enabled=false` (default), behavior is identical to Phase B (Noop passes everything). With `daft.ai.enabled=true` and a fake key + MockRestServiceServer in an integration test, a low-score response prevents the notification listener from firing. Manual smoke: real API key, run against current daft.ie data, confirm reasonable scores.

### Phase D — Agent outreach (auto-send templated email)

Goal: in addition to user notification, send a templated email to the listing's agent.

- Add SMTP support: `spring-boot-starter-mail` dependency in `pom.xml`. SMTP config in `application.yaml` reading from env (`SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASS`).
- Add `notification/smtp/`:
  - `SmtpEmailSender` — thin wrapper around `JavaMailSender`. Method: `send(String to, String subject, String htmlBody, String textBody)`.
  - `MailConfiguration` — bean wiring; dev profile uses GreenMail/MailHog from docker-compose.
- Add `notification/agent/`:
  - `ListingDetailsClient` — calls daft.ie listing-detail endpoint (`GET https://gateway.daft.ie/old/v1/ads/{id}` or equivalent — confirm exact path during implementation by inspecting the network tab on a daft.ie listing page). Parses agent name + email + phone. Reuse existing `RestClient` patterns and `BlockDetector` for resilience.
  - `ListingDetails` record: `String agentName`, `String agentEmail`, `String agentPhone`, `String fullAddress`, `String description`.
  - `AgentEmailTemplate` — renders subject/body. For now, a single Java text-block template with `String.formatted(...)` placeholders (intro, mention of viewing interest, tenant-supplied bio fields). Defer Thymeleaf unless we need conditionals.
  - `AgentOutreachService` — `send(Tenant, ListingResult, AnalysisResult)`. Steps: fetch details → render → SMTP send. Skips silently if `agentEmail` is missing or per-tenant `agent-outreach-enabled=false`.
- Per-tenant config additions in `TenantSlot`: `agentOutreachEnabled` (default `false`), `senderName`, `senderBio`, `senderPhone`. Validate: when `agentOutreachEnabled=true`, all three sender fields must be non-blank.
- Wire `AgentOutreachService.send(...)` into `ListingApprovedListener` after `NotifierRouter.notify(...)` succeeds. Failures in agent-outreach must NOT roll back the user notification — log + metric and continue.
- New tests:
  - `ListingDetailsClientTest` — `MockRestServiceServer` + JSON fixture.
  - `AgentEmailTemplateTest` — golden-string assertion for subject + body.
  - `SmtpEmailSenderTest` — uses GreenMail or `JavaMailSender` mock to assert outgoing message.
  - `AgentOutreachServiceTest` — Mockito; covers happy path, missing agent email, per-tenant disabled, daft.ie 4xx.
  - `ListingApprovedListenerTest` extended: agent outreach failure does not break user notification.
- Add a GreenMail container to `docker-compose.yml` for local testing.

**Verification**: integration test with GreenMail asserts an email arrives with the templated subject/body. Manual: with `agent-outreach-enabled=false` per tenant, system behaves like Phase C. Flip to `true` for a single tenant against MailHog → see real templated email rendered.

---

## Critical files (sorted by phase)

**Phase A** (move + import-update only — no logic):
- All files under `src/main/java/com/vmudrud/daftiescanner/` (roughly 30 files).
- `src/main/resources/application.yaml` — log namespace.
- All test files — package + import updates.

**Phase B**:
- `search/scheduler/ScannerJob.java` — swap `emailGuard.tryNotify(...)` for `eventPublisher.publishEvent(...)`.
- `notification/ListingApprovedListener.java` (new).
- `common/event/NewListingFoundEvent.java` (new).
- `DaftiescannerApplication.java` — `@EnableAsync`.
- New `architecture/PackageBoundariesTest.java` (ArchUnit).

**Phase C**:
- `common/event/ListingApprovedEvent.java`, `ListingRejectedEvent.java` (new).
- `aianalyzer/` package (~5 files, new).
- `notification/ListingApprovedListener.java` — switch event type.
- `pom.xml` — confirm RestClient already present (it is); no Anthropic SDK dep, just `RestClient`.
- `application.yaml` — `daft.ai.*` block.

**Phase D**:
- `pom.xml` — add `spring-boot-starter-mail`.
- `notification/smtp/`, `notification/agent/` (~6 files, new).
- `common/tenant/TenantConfiguration.java` — extend `TenantSlot` with sender fields and `agentOutreachEnabled`.
- `notification/ListingApprovedListener.java` — call `AgentOutreachService.send(...)` after notify.
- `application.yaml` — `spring.mail.*` block.
- `docker-compose.yml` — GreenMail/MailHog service.
- `README.md` — update milestones M11/M12, env-var table.

---

## Reused existing components

- `RestClient` + `MockRestServiceServer` pattern from `DaftClientConfiguration.java:1-82` and `DaftClientTest` — reuse for `ClaudeImageAnalyzer` and `ListingDetailsClient`.
- `BlockDetector.java` — extend for daft.ie listing-detail endpoint (same gateway, same risks).
- `DedupStore` (already split into listing dedup + email dedup) — no changes needed; just lives in `common/store/` now.
- `MetricsPublisher.java:1-47` — pattern reused for `aianalyzer` and `notification` to add their own counters/timers in their own modules.
- `EmailNotificationGuard.java:23-36` — moved verbatim into `notification/`; the per-email lock + dedup-by-email logic still applies, just gated on `ListingApprovedEvent` instead of being called directly.
- `AlertThrottle.java:1-95` — reuse for "AI quota exhausted" or "SMTP send failure" alerts in Phase C/D.
- `@Retryable` annotation pattern from `DaftClient` — reuse on `ListingDetailsClient` and `SmtpEmailSender`.

---

## Verification checklist (end-to-end after Phase D)

- [ ] `mvn clean verify` — all unit + integration tests pass.
- [ ] ArchUnit boundary test passes (no cross-module imports).
- [ ] `docker-compose up` starts app + DynamoDB Local + MailHog. Health endpoint returns 200.
- [ ] Insert a fake listing into the search response via local fixture / live test tenant. Observe in logs:
  - `[search] poll → NewListingFoundEvent published`
  - `[aianalyzer] analyzed score=0.85 → ListingApprovedEvent published`
  - `[notification] NOTIFY tenant=… title=…`
  - `[notification.agent] SMTP sent to=agent@example.com`
- [ ] Check MailHog UI → see two emails (user notification if SMTP user-channel enabled, plus agent outreach).
- [ ] Set `daft.ai.threshold=0.99`, re-run → `ListingRejectedEvent` published, no notification fires.
- [ ] Set `agent-outreach-enabled=false` for a tenant → user notification fires, no agent email sent.
- [ ] Kill MailHog → agent-outreach failure logs error but user notification still succeeds.
- [ ] CloudWatch metrics: confirm new counters `ai_listings_passed`, `ai_listings_rejected`, `agent_emails_sent`, `agent_emails_failed`.

---

## Out of scope (explicitly deferred)

- Maven multi-module split — revisit only if/when one module starts to grow beyond ~30 classes or we want to extract ai-analyzer into a separate microservice.
- Persisting `AnalysisResult` to DynamoDB for re-analysis avoidance — only useful if re-poll loops cause repeated AI calls; current dedup already prevents this.
- Telegram / SNS-SMS notifiers (originally M11 idea) — still pending after M11→M14 reshuffle, but this plan focuses on architecture + AI + agent email; channel impls slot in cleanly behind `Notifier` interface.
- HTML email rendering with Thymeleaf — defer until template needs conditionals/loops.
- Rate limiting AI calls / cost cap — add only after observing real cost in production.
