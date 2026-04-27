# Lessons Learned

Patterns and rules derived from past corrections. Review at session start.

---

## Format

Each entry:
- **Rule**: What to do (or not do)
- **Why**: The mistake or context that caused this rule
- **Applies to**: When this rule is in effect

---

## Spring Boot / Java

- **Rule**: `@PostConstruct private void` is valid — Spring uses reflection and does NOT require package-private visibility.
  **Why**: Confusion with JUnit 5 rule below; the two are different frameworks.
  **Applies to**: Any `@PostConstruct` method in a Spring `@Component`.

- **Rule**: Use `@ConditionalOnExpression("!'${daft.dynamo.seen-table:}'.isBlank()")` to gate ALL DynamoDB beans (`DynamoDbClient`, `DynamoDbEnhancedClient`, and both stores). The cursor-table condition alone is NOT sufficient.
  **Why**: `DynamoDbConfiguration` is gated only on `seen-table`; if `seen-table` is blank the whole DynamoDB context is disabled — cursor tests must also blank `seen-table` in `src/test/resources/application.yaml`.
  **Applies to**: Any new DynamoDB-backed component or IT test.

- **Rule**: Do not use `RestClient.Builder` auto-injection in Spring Boot 4 — there is no auto-configured `RestClient.Builder` bean. Declare an explicit `@Bean RestClient` instead.
  **Why**: Caused `NoSuchBeanDefinitionException` in integration wiring. Fixed by explicit `@Bean` in `DaftClientConfiguration`.
  **Applies to**: All HTTP client setup in this project.

## Testing

- **Rule**: `@BeforeEach` methods must NOT be `private` — JUnit 5 warns now and will disallow it in a future release. Use package-private (no modifier).
  **Why**: After adding `private` to all test methods for consistency, JUnit 5 logged a warning for `@BeforeEach` specifically.
  **Applies to**: All `@BeforeEach` (and `@AfterEach`) methods in test classes.

- **Rule**: `@DynamicPropertySource` in an IT test must inject BOTH `daft.dynamo.seen-table` AND `daft.dynamo.cursor-table`, even when the test only uses one store.
  **Why**: `DynamoDbConfiguration` is conditionally loaded on `seen-table` being non-blank; without it the `DynamoDbClient` bean is absent and the context fails to start.
  **Applies to**: All `*IT` tests that use any DynamoDB store.

- **Rule**: For `MockRestServiceServer` in Spring Boot 4, bind via `MockRestServiceServer.bindTo(restClientBuilder)` — NOT via `MockRestServiceServer.createServer(restTemplate)`.
  **Why**: `RestClient` does not use `RestTemplate` internally; the old binding pattern silently does nothing.
  **Applies to**: All unit tests for `DaftClient` or any `RestClient`-based component.

## Architecture / Design

- **Rule**: Check table existence with `describeTable` before calling `createTable`. Do NOT rely solely on catching `ResourceInUseException` for flow control.
  **Why**: Using exceptions as control flow is an anti-pattern; `describeTable` → `createTable` is idiomatic. `ResourceInUseException` catch is kept only as a last-resort concurrent-startup guard.
  **Applies to**: `DynamoCursorStore.ensureTable()`, `DynamoDedupStore.ensureTable()`, and any future DynamoDB table init.

## Claude Behavior

- **Rule**: Never mark a milestone done without verifying tests pass (`./mvnw verify`). Report the actual test output, not an assumption.
  **Why**: Agents marked M5/M6 tasks complete without running the failsafe plugin; compile errors were caught later.
  **Applies to**: Every milestone completion step in PLAN.md.
