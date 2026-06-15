# Flyway Migration Safety Gate Implementation Plan

## Overview

Add a Testcontainers-backed integration test that proves all four Flyway migrations (V1–V4) apply cleanly against a real PostgreSQL 16 instance, and that the resulting schema is queryable and constraint-enforcing. Also corrects stale test-plan.md wording that still refers to "V1 and V2" and fills in the §6.5 cookbook entry.

## Current State Analysis

All `@SpringBootTest` tests run against H2 in-memory (`MODE=PostgreSQL`). H2 is not a full PostgreSQL emulator — `TIMESTAMP WITH TIME ZONE` semantics, CHECK constraint enforcement edge cases, and constraint-drop semantics differ. Four migrations exist (V1–V4); the test plan §2 Risk #6 was authored when only V2 existed and still says "V1 and V2." No Testcontainers dependency is in `pom.xml`.

### Key Discoveries

- `pom.xml`: `spring-boot-starter-parent` 4.0.6 manages Testcontainers BOM — no explicit version needed on either artifact. `flyway-database-postgresql` and `org.postgresql:postgresql` are already present (runtime scope), so no additional Flyway or JDBC driver is needed for the test. (`pom.xml:44–113`)
- `src/test/resources/application.properties`: Hard-codes H2 datasource. `@DynamicPropertySource` properties override file-based properties (highest Spring Environment precedence) — allows the migration test to redirect to PostgreSQL without touching the shared file. (`src/test/resources/application.properties:1–4`)
- AI is disabled via multiple mechanisms (`spring.ai.model.chat=none`, `moodlog.ai.enabled=false`, `moodlog.ai.provider=stub`) — these carry through to the Testcontainers test; no additional AI disablement is needed.
- `UserAccount` constructor pattern confirmed at `src/test/java/com/amadeuszx/moodlog/user/UserAccountRepositoryTests.java` — includes `timezone` field (added in V4). Reuse for seed data.
- `JournalEntry` seed + FK pattern at `src/test/java/com/amadeuszx/moodlog/journal/JournalEntryRepositoryTests.java` — reuse for schema queryability assertion.
- `@BeforeEach deleteAll()` is the established cleanup pattern across all `@SpringBootTest` tests in this codebase.

## Desired End State

A single test class (`FlywayMigrationPostgresTests`) that:
1. Starts a `PostgreSQLContainer<?>` (`postgres:16`) once per test run.
2. Overrides the H2 datasource via `@DynamicPropertySource` — Flyway applies V1–V4, Hibernate validates the result.
3. Asserts exactly 4 migrations applied successfully.
4. Asserts `UserAccount` rows can be persisted, reloaded, and that the email uniqueness constraint is enforced.
5. Asserts `JournalEntry` rows can be persisted and reloaded (FK + CHECK constraints live).

`.\mvnw.cmd test` passes with all existing H2 tests unmodified.

### Key Discoveries (Desired End State)

- No changes to `src/test/resources/application.properties` — H2 tests stay intact.
- No new Spring profile or `@TestPropertySource` needed — `@DynamicPropertySource` is sufficient.
- `@Testcontainers` (JUnit 5 extension annotation) is required on the class for JUnit to manage `@Container` field lifecycle.

## What We're NOT Doing

- Not adding a shared `@TestcontainersConfiguration` class — this is a standalone gate; cross-test container reuse is a future concern.
- Not changing any production code or `application.properties`.
- Not using `@DataJpaTest` — it replaces the datasource with H2 by default, defeating the purpose.
- Not using `@ServiceConnection` — `@DynamicPropertySource` is simpler and explicit; no extra configuration class needed.
- Not adding PostgreSQL-backed variants of existing H2 tests — this test's job is migration correctness, not business-logic coverage duplication.
- Not refreshing test-plan.md strategy sections beyond the two stale spots research identified.

## Implementation Approach

Two sequential changes: (1) wire Testcontainers into the build and write the test class; (2) correct the stale documentation. Phase 1 is independently verifiable — `.\mvnw.cmd test` must be green before Phase 2 is touched.

## Critical Implementation Details

**`@Testcontainers` is required.** Without this JUnit 5 extension annotation on the class, `@Container` fields are not processed and the container never starts. This is a common first-run mistake when adopting Testcontainers.

**Migration count must be asserted by exact count.** Flyway silently skips migrations whose SQL files are missing from the classpath if their checksum is already in `flyway_schema_history`. A context-load-only test would pass in that scenario. `assertEquals(4L, applied)` catches a missing file. When V5 is added, this assertion must be updated to `5L`.

---

## Phase 1: Add Testcontainers + write FlywayMigrationPostgresTests

### Overview

Add three test-scoped Maven dependencies and create the migration gate test class. The test boots a full Spring application context against a real PostgreSQL 16 container and asserts migration correctness and schema queryability.

### Changes Required

#### 1. pom.xml — add test-scoped Testcontainers dependencies

**File**: `pom.xml`

**Intent**: Make Testcontainers and the PostgreSQL container image driver available in the test classpath. Spring Boot 4.x BOM manages the version — no `<version>` tag is needed.

**Contract**: Add all three entries inside the existing `<dependencies>` block, after the `spring-security-test` dependency:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

#### 2. FlywayMigrationPostgresTests.java — new migration gate test class

**File**: `src/test/java/com/amadeuszx/moodlog/migration/FlywayMigrationPostgresTests.java`

**Intent**: Start a PostgreSQL 16 container once per class, redirect the Spring datasource to it via `@DynamicPropertySource`, and verify that all four Flyway migrations apply cleanly and the schema is queryable with live constraints. Inherits all other properties from `src/test/resources/application.properties` (AI disabled, Hibernate `validate`, journal limits).

**Contract**:
- Class annotations: `@SpringBootTest`, `@Testcontainers`
- Static `@Container` field: `PostgreSQLContainer<?>` constructed with `"postgres:16"`
- Static `@DynamicPropertySource` method overriding the three datasource properties — `spring.datasource.url` (via `postgres::getJdbcUrl`), `spring.datasource.username`, `spring.datasource.password`:

```java
@DynamicPropertySource
static void configurePostgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
}
```

- `@Autowired` fields: `Flyway flyway`, `UserAccountRepository userAccountRepository`, `JournalEntryRepository journalEntryRepository`
- `@BeforeEach void cleanUp()`: `journalEntryRepository.deleteAll()` then `userAccountRepository.deleteAll()` (FK order)
- Three `@Test` methods with descriptive `@DisplayName` annotations, declaring local variables with Lombok `val` per repo conventions:
  1. `allMigrationsApplyCleanlyToPostgres` — streams `flyway.info().all()`, filters to `MigrationState.SUCCESS`, asserts count equals `4L` exactly. Snippet:
     ```java
     val applied = Arrays.stream(flyway.info().all())
         .filter(info -> info.getState() == MigrationState.SUCCESS)
         .count();
     assertEquals(4L, applied);
     ```
  2. `userAccountSchemaIsQueryable` — persist one `UserAccount` (follow `UserAccountRepositoryTests` constructor pattern, declaring local variables with Lombok `val`), assert `findByEmail` returns present, then assert that saving a second `UserAccount` with the same email throws `DataIntegrityViolationException`.
  3. `journalEntrySchemaIsQueryable` — persist one `UserAccount` as owner, persist one `JournalEntry` owned by that account (follow `JournalEntryRepositoryTests` constructor pattern, declaring local variables with Lombok `val`), assert `findTop10ByUserAccountIdOrderByCreatedAtDesc` returns a list of size 1.

### Success Criteria

#### Automated Verification

- `.\mvnw.cmd test` passes with all three `FlywayMigrationPostgresTests` methods green
- All pre-existing H2-backed tests continue to pass — `src/test/resources/application.properties` is unchanged
- Hibernate `validate` succeeds against PostgreSQL (implicit — context loads without exception)

#### Manual Verification

- Test output (Maven Surefire log or IDE runner) confirms the PostgreSQL 16 container was started — look for `org.testcontainers` container startup lines, not H2 init lines
- No regression in other test classes when the test suite runs in full

**Implementation Note**: After completing this phase and all automated verification passes, confirm manually that test output shows the container starting before moving to Phase 2.

---

## Phase 2: test-plan.md documentation updates

### Overview

Correct two stale spots research identified: the §2 Risk #6 row still says "V1 and V2 migrations" (V3 and V4 shipped after the plan was authored), and §6.5 reads "TBD — see §3 Phase 4."

### Changes Required

#### 1. test-plan.md §2 Risk Map — Risk #6 scenario wording

**File**: `context/foundation/test-plan.md`

**Intent**: Update the risk scenario text to reflect that four migrations now exist, so the gate description is accurate when someone reads §2 in the future.

**Contract**: In the Risk Map table, Risk #6 row, first cell — replace "Flyway V1 and V2 migrations applied against H2" with "Flyway V1–V4 migrations applied against H2." Corresponding change in the Source column evidence note if it mentions specific migration files.

#### 2. test-plan.md §2 Risk Response Guidance — Risk #6 row

**File**: `context/foundation/test-plan.md`

**Intent**: Ensure the "What would prove protection" and "Context `/10x-research-spring` must ground" columns no longer reference "V1 and V2" specifically.

**Contract**: In the Risk Response Guidance table, Risk #6 row — replace "Flyway V1 and V2 migrations apply cleanly" with "Flyway V1–V4 migrations apply cleanly."

#### 3. test-plan.md §6.5 cookbook entry

**File**: `context/foundation/test-plan.md`

**Intent**: Replace the "TBD — see §3 Phase 4" placeholder with a complete, actionable cookbook entry so future developers know the exact pattern to follow when adding PostgreSQL-backed tests.

**Contract**: §6.5 content should cover:
- Class-level annotations: `@SpringBootTest`, `@Testcontainers`
- Static `@Container PostgreSQLContainer<?>` with `"postgres:16"`
- Static `@DynamicPropertySource` method overriding three datasource properties
- `@BeforeEach deleteAll()` cleanup in FK order (entries before accounts)
- When to use `assertEquals(N, applied)` and the rule that this assertion must be updated with each new migration
- Live example: `src/test/java/com/amadeuszx/moodlog/migration/FlywayMigrationPostgresTests.java`

#### 4. test-plan.md §3 Phased Rollout — Phase 4 status

**File**: `context/foundation/test-plan.md`

**Intent**: Update Phase 4 row status from "change opened" to "complete" once the test ships.

**Contract**: Phase 4 row in the Phased Rollout table, Status column — update when Phase 1 of this plan is verified. (Leave this for the implementation step; note it here so it isn't forgotten.)

### Success Criteria

#### Automated Verification

- (none — documentation changes only)

#### Manual Verification

- §2 Risk Map table Risk #6 row no longer contains "V1 and V2" — reads "V1–V4"
- §2 Risk Response Guidance Risk #6 row updated consistently
- §6.5 no longer reads "TBD — see §3 Phase 4" — contains a complete, runnable cookbook entry with class-level annotation list, container declaration, `@DynamicPropertySource` snippet, cleanup note, and link to live example

**Implementation Note**: Phase 2 is documentation only. No test run is needed to verify it — visual review of the edited sections is sufficient.

---

## Testing Strategy

### Integration Tests

- `FlywayMigrationPostgresTests` is the primary artifact: 3 methods covering migration count, `UserAccount` schema, `JournalEntry` schema.
- Existing `UserAccountRepositoryTests` and `JournalEntryRepositoryTests` continue to run against H2 — no changes.

### Manual Testing Steps

1. Run `.\mvnw.cmd test` and confirm `FlywayMigrationPostgresTests` appears in the Surefire report with 3 tests passed.
2. Inspect Maven/IDE output: look for `org.testcontainers` container start log lines confirming `postgres:16` was launched.
3. Confirm all pre-existing tests still pass (no H2 regression).
4. Review `context/foundation/test-plan.md` §2 and §6.5 to confirm stale content is replaced.

## Migration Notes

No schema changes. Testcontainers starts a fresh ephemeral PostgreSQL 16 instance per test class run; it is torn down after the class. No data persists between runs.

## References

- Research: `context/changes/testing-migration-safety-gate/research.md`
- UserAccount seed pattern: `src/test/java/com/amadeuszx/moodlog/user/UserAccountRepositoryTests.java`
- JournalEntry seed pattern: `src/test/java/com/amadeuszx/moodlog/journal/JournalEntryRepositoryTests.java`
- Test datasource config: `src/test/resources/application.properties`
- Migrations: `src/main/resources/db/migration/V1__create_user_accounts.sql` through `V4__add_timezone_to_user_accounts.sql`

---

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Add Testcontainers + write FlywayMigrationPostgresTests

#### Automated

- [x] 1.1 `.\mvnw.cmd test` passes — all three FlywayMigrationPostgresTests methods green
- [x] 1.2 Pre-existing H2-backed tests unchanged — no regressions in full suite
- [x] 1.3 Hibernate `validate` succeeds against PostgreSQL (context loads without exception)

#### Manual

- [x] 1.4 Test output confirms PostgreSQL 16 container started (not H2)
- [x] 1.5 No regression in other test classes when the test suite runs in full

### Phase 2: test-plan.md documentation updates

#### Manual

- [ ] 2.1 §2 Risk #6 Risk Map row references V1–V4, not V1 and V2
- [ ] 2.2 §2 Risk Response Guidance Risk #6 row updated consistently
- [ ] 2.3 §6.5 cookbook entry filled — complete and actionable, no longer reads TBD
