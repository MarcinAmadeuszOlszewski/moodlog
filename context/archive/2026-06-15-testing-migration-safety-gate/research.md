---
date: 2026-06-15T12:00:00+02:00
researcher: Claude Sonnet 4.6
git_commit: e53748ee7da51db609169958c358df6a3b72f029
branch: master
repository: MarcinAmadeuszOlszewski/moodlog
topic: "Flyway migration safety gate — Phase 4 test rollout: PostgreSQL compatibility and Testcontainers setup"
tags: [research, flyway, testcontainers, postgresql, h2, migration, phase-4]
status: complete
last_updated: 2026-06-15
last_updated_by: Claude Sonnet 4.6
---

# Research: Flyway Migration Safety Gate — Phase 4 Test Rollout

**Date**: 2026-06-15T12:00:00+02:00
**Researcher**: Claude Sonnet 4.6
**Git Commit**: e53748ee7da51db609169958c358df6a3b72f029
**Branch**: master
**Repository**: MarcinAmadeuszOlszewski/moodlog

---

## Research Question

Ground rollout Phase 4 of `context/foundation/test-plan.md`:

- **Risk #6**: Do all Flyway migrations (V1–V4) apply cleanly against a real PostgreSQL instance? Does the schema produced match what Hibernate expects? Are any H2-specific SQL constructs present that PostgreSQL would reject?
- **Setup requirement**: No Testcontainers dependency exists in `pom.xml`. What must be added, and what is the correct Spring Boot 4.x / `@DynamicPropertySource` setup pattern to override the H2 test datasource with a PostgreSQL container?
- **Scope correction**: The test plan mentions "V1 and V2 migrations" — V3 and V4 have since shipped and must be included in the gate.

---

## Summary

**Four migrations exist, not two.** V3 and V4 were added after the test plan was authored (2026-06-12). All four must be validated against PostgreSQL. The test plan's §2 Risk #6 source note is factually stale on migration count; the risk itself is unchanged.

**No obviously H2-incompatible SQL detected** in the four migration files. All four use standard DDL (UUID PKs assigned in Java, `TIMESTAMP WITH TIME ZONE`, `VARCHAR`, `BOOLEAN`, `INTEGER`, named constraints, `ALTER TABLE … DROP CONSTRAINT`, `ALTER COLUMN … DROP NOT NULL`, `ADD COLUMN … NOT NULL DEFAULT`). No `SERIAL`, `AUTO_INCREMENT`, `IDENTITY`, H2-specific functions, or H2-only syntax found.

**The risk remains real despite this.** H2's `MODE=PostgreSQL` is not a full PostgreSQL emulation. Subtle behavioral differences exist around `TIMESTAMP WITH TIME ZONE` precision and storage, CHECK constraint enforcement in edge cases, and constraint-drop semantics. More critically: the absence of obvious issues in the current SQL does not protect against a future migration author introducing PostgreSQL-incompatible SQL — the gate's primary value is as a regression fence, not a one-time check.

**No Testcontainers dependency in pom.xml.** Two artifacts must be added: `spring-boot-testcontainers` (scope test) and `org.testcontainers:postgresql` (scope test). Spring Boot 4.x manages Testcontainers versions via its BOM, so no explicit version is needed.

**`@DynamicPropertySource` is the correct override pattern.** `src/test/resources/application.properties` hard-codes an H2 URL. `@DynamicPropertySource` properties take the highest precedence and override file-based properties, so the migration test can use a `PostgreSQLContainer` without touching the shared test properties file.

**What the test must assert:** Context loads against PostgreSQL (Flyway ran, Hibernate `validate` passed), `Flyway` bean reports 4 applied migrations, at least one `UserAccount` and one `JournalEntry` can be persisted and reloaded (schema is actually queryable, FK and constraints are live), and a uniqueness violation on `user_accounts.email` is enforced by PostgreSQL (constraint is real, not silently ignored by H2).

---

## Detailed Findings

### Migration inventory (all four)

**`src/main/resources/db/migration/V1__create_user_accounts.sql`**

```sql
create table user_accounts (
    id uuid primary key,
    email varchar(320) not null,
    password_hash varchar(255) not null,
    active boolean not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_user_accounts_email unique (email)
);
```

Compatibility: Standard DDL. `uuid primary key` — UUID type is a native PostgreSQL type; H2 PostgreSQL MODE supports it. `timestamp with time zone` — native PostgreSQL type; H2 PostgreSQL MODE maps to `TIMESTAMP WITH TIME ZONE`. `varchar(320)` — standard. Named unique constraint — standard in both.

**`src/main/resources/db/migration/V2__create_journal_entries.sql`**

```sql
create table journal_entries (
    id uuid primary key,
    user_account_id uuid not null,
    content varchar(2000) not null,
    system_mood_tag varchar(32) not null,
    system_mood_score integer not null,
    override_mood_tag varchar(32),
    override_mood_score integer,
    classifier_provider varchar(100) not null,
    classifier_model varchar(100) not null,
    classified_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_journal_entries_user_account foreign key (user_account_id) references user_accounts(id),
    constraint ck_journal_entries_system_mood_score_range check (system_mood_score between 0 and 100),
    constraint ck_journal_entries_override_mood_score_range check (
        override_mood_score is null or override_mood_score between 0 and 100
    )
);
create index idx_journal_entries_user_account_created_at on journal_entries (user_account_id, created_at);
```

Compatibility: Standard. Named FK constraint, named CHECK constraints with `between` and `is null or`, composite index — all standard. PostgreSQL enforces CHECK constraints strictly; H2 2.x also enforces them in PostgreSQL MODE. The `is null or` pattern in the override check is standard nullable-column guard — both handle it identically.

**`src/main/resources/db/migration/V3__nullable_system_mood_score.sql`**

```sql
alter table journal_entries
    drop constraint ck_journal_entries_system_mood_score_range;

alter table journal_entries
    alter column system_mood_score drop not null;

alter table journal_entries
    add constraint ck_journal_entries_system_mood_score_range
    check (system_mood_score is null or system_mood_score between 0 and 100);
```

Compatibility: Standard ALTER TABLE pattern. `DROP CONSTRAINT` by name is standard. `ALTER COLUMN … DROP NOT NULL` is standard PostgreSQL syntax; H2 PostgreSQL MODE supports the `DROP NOT NULL` form. The re-added check now uses `is null or` to allow nullable values — same pattern as the override check in V2.

**`src/main/resources/db/migration/V4__add_timezone_to_user_accounts.sql`**

```sql
alter table user_accounts
    add column timezone varchar(50) not null default 'Europe/Warsaw';
```

Compatibility: `ADD COLUMN … NOT NULL DEFAULT '…'` is safe in PostgreSQL 11+ (constant default is an instant metadata operation; existing rows are backfilled with the default). Railway deploys PostgreSQL 16 (infrastructure.md). H2 also handles this form. No compatibility risk here.

---

### H2 vs PostgreSQL behavioral differences — what to watch

No H2-specific SQL was found. However, three subtle behavioral gaps exist that the test helps catch in future migrations:

| Area | H2 (PostgreSQL MODE) | PostgreSQL 16 |
|---|---|---|
| `TIMESTAMP WITH TIME ZONE` storage | Stores with JVM timezone offset; may not round-trip identically at sub-millisecond precision | Stores in UTC, returns in session timezone; precision is exact |
| `TIMESTAMP WITH TIME ZONE` zone offset display | Renders with JVM offset | Renders as UTC (unless `SET TIME ZONE` is active in session) |
| CHECK constraint with NULL | H2 2.x enforces; older H2 did not | Always enforces; `NULL` satisfies `NOT` operand of `NOT NULL` constraints |
| `ALTER COLUMN … DROP NOT NULL` | H2 accepts with `ALTER COLUMN name SET NULL` in some modes; `DROP NOT NULL` accepted in PostgreSQL MODE | Standard DDL |

The `spring.jpa.properties.hibernate.jdbc.time_zone=UTC` property (set in `application.properties`) neutralizes most `TIMESTAMP WITH TIME ZONE` divergence by pinning the JDBC time zone on both databases. No immediate incompatibility, but the gate catches drift.

---

### Current test datasource configuration

**`src/test/resources/application.properties`** — shared by all `@SpringBootTest` classes:

```properties
spring.datasource.url=jdbc:h2:mem:moodlog-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=validate
```

H2 in-memory, PostgreSQL compatibility MODE, Hibernate validates (not creates). Flyway runs on H2 at startup for every test context.

**`src/main/resources/application-local.properties`** — local dev overrides (not test):

```properties
spring.datasource.url=jdbc:h2:file:./data/moodlog;MODE=PostgreSQL;DB_CLOSE_ON_EXIT=FALSE
```

Also H2, file-backed for local dev. Not used in tests.

---

### Testcontainers — what must be added to pom.xml

No Testcontainers dependency currently exists in `pom.xml`. Two artifacts are needed:

```xml
<!-- Spring Boot integration: @ServiceConnection, @ImportTestcontainers, etc. -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>

<!-- PostgreSQL container image driver -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

Spring Boot 4.x's `spring-boot-starter-parent` BOM manages Testcontainers versions — no `<version>` tag needed on either artifact.

---

### Correct override pattern: `@DynamicPropertySource`

`src/test/resources/application.properties` hard-codes H2. `@DynamicPropertySource` properties override file-based properties (highest Spring Environment precedence), so the migration test can redirect the datasource to the PostgreSQL container without modifying the shared file.

```java
@SpringBootTest
class FlywayMigrationPostgresTests {

    @Container
    static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void configurePostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    
    // test methods ...
}
```

The container is `static` so it is shared across all test methods in the class (started once). `@DynamicPropertySource` is also `static` (required by Spring). `spring.jpa.hibernate.ddl-auto=validate` carries through from `src/test/resources/application.properties` and Hibernate runs validation against the PostgreSQL schema.

**Alternatively**, `@ServiceConnection` from `spring-boot-testcontainers` can be used via `@Bean @ServiceConnection` in a `@TestConfiguration` or via `@ImportTestcontainers`. The `@DynamicPropertySource` form is simpler, doesn't require extra configuration classes, and is more explicit — preferred here because the existing project has no Testcontainers infrastructure to reuse.

---

### What the test must assert (and what not to skip)

The test plan's anti-patterns: "Skipping because H2 tests pass" and "not asserting post-migration schema shape."

**Must assert:**

1. **Context loads** (implicit — Spring context startup means Flyway ran all migrations without error and Hibernate `validate` passed against PostgreSQL schema). This is the migration correctness gate.

2. **4 migrations applied** — Query `Flyway` bean's `info()`:
   ```java
   @Autowired Flyway flyway;
   
   @Test
   void allMigrationsApplyCleanlyToPostgres() {
       val applied = Arrays.stream(flyway.info().all())
           .filter(info -> info.getState() == MigrationState.SUCCESS)
           .count();
       assertEquals(4L, applied);
   }
   ```
   This catches a silent partial migration or a checksum mismatch that prevents a migration from running.

3. **Schema is queryable — `UserAccount`** — Persist and reload one user account. Proves columns exist with correct types and the unique constraint is enforced:
   ```java
   userAccountRepository.saveAndFlush(testAccount);
   assertTrue(userAccountRepository.findByEmail(testEmail).isPresent());
   assertThrows(DataIntegrityViolationException.class,
       () -> userAccountRepository.saveAndFlush(duplicateEmailAccount));
   ```

4. **Schema is queryable — `JournalEntry`** — Persist and reload one entry owned by the test user. Proves the FK constraint and the CHECK constraints are live in PostgreSQL:
   ```java
   journalEntryRepository.saveAndFlush(testEntry);
   assertEquals(1, journalEntryRepository.findTop10ByUserAccountIdOrderByCreatedAtDesc(owner.getId()).size());
   ```

**Not required (explicitly out of scope):**

- Full round-trip of all test scenarios — those already exist in `JournalEntryRepositoryTests` and `UserAccountRepositoryTests` against H2. This test's job is migration correctness, not business-logic coverage.
- H2-specific tests — those existing tests must continue to run unmodified against H2 (no changes to `src/test/resources/application.properties`).
- Testcontainers reuse across other test classes — the migration test is a standalone gate. If the team later wants to add more PostgreSQL-backed tests, a shared `@TestcontainersConfiguration` class can be introduced then.

---

### AI configuration — must be disabled in migration test

`src/test/resources/application.properties` already sets `moodlog.ai.enabled=false` and `moodlog.ai.provider=stub`, which prevents the Spring AI OpenAI autoconfiguration from requiring an API key. These properties carry through to the Testcontainers test (only the datasource is overridden by `@DynamicPropertySource`). No additional AI disablement is needed.

---

### Hibernate validate vs ddl-auto

Both `src/test/resources/application.properties` and `src/main/resources/application.properties` use `spring.jpa.hibernate.ddl-auto=validate`. This means:

- Flyway runs first (Flyway is `@Order(DatabaseInitializerDetector.HIGHPRECEDENCE)` in Spring Boot)
- Hibernate then validates the schema produced by Flyway against the entity mappings

When running against PostgreSQL, this two-stage boot is the strongest possible sanity check: Flyway must produce a schema that Hibernate's ORM model recognizes. Any missing column, type mismatch, or constraint configuration error surfaces here without writing a separate schema-inspection query.

---

### Entity field coverage of V4

`src/test/java/com/amadeuszx/moodlog/user/UserAccountRepositoryTests.java` already constructs `UserAccount` with the `timezone` parameter:

```java
new UserAccount(UUID.randomUUID(), "ela@example.com", "$2a$10$storedHash", true, createdAt, createdAt, "Europe/Warsaw")
```

The `timezone` field (added in V4) is already mapped in the entity and used in existing tests. The migration test reuses the same constructor pattern.

---

## Code References

- `src/main/resources/db/migration/V1__create_user_accounts.sql` — user_accounts table DDL
- `src/main/resources/db/migration/V2__create_journal_entries.sql` — journal_entries table DDL with CHECK constraints and index
- `src/main/resources/db/migration/V3__nullable_system_mood_score.sql` — ALTER TABLE: drop constraint, drop NOT NULL, re-add constraint
- `src/main/resources/db/migration/V4__add_timezone_to_user_accounts.sql` — ALTER TABLE: add timezone column NOT NULL with default
- `src/test/resources/application.properties` — H2 in-memory datasource; `spring.jpa.hibernate.ddl-auto=validate`; AI disabled
- `src/main/resources/application.properties` — production datasource from env vars; `spring.jpa.hibernate.ddl-auto=validate`; `spring.jpa.properties.hibernate.jdbc.time_zone=UTC`
- `src/main/resources/application-local.properties` — H2 file datasource for local dev (not used in tests)
- `pom.xml` — no Testcontainers dependency; Spring Boot 4.0.6; `com.h2database:h2` runtime scope (available in tests); `org.postgresql:postgresql` runtime scope
- `src/test/java/com/amadeuszx/moodlog/user/UserAccountRepositoryTests.java` — pattern for seed + reload + constraint violation against H2; reuse structure for PostgreSQL variant
- `src/test/java/com/amadeuszx/moodlog/journal/JournalEntryRepositoryTests.java` — pattern for `JournalEntry` seed with FK; reuse for PostgreSQL variant
- `context/foundation/infrastructure.md` — Railway recommended; PostgreSQL 16 targeted for production
- `context/foundation/test-plan.md:§2 Risk #6` — risk wording needs updating: "V1 and V2" → "V1–V4"

---

## Architecture Insights

**Flyway → Hibernate validate boot order is the main assertion.** Writing explicit schema-inspection SQL (column exists, type name matches) is fragile and couples the test to PostgreSQL system catalog naming. Relying on `spring.jpa.hibernate.ddl-auto=validate` gives a higher-signal check: Hibernate's entity-schema reconciliation runs against real PostgreSQL and fails loudly on any type or nullability mismatch.

**`@DynamicPropertySource` vs `@ServiceConnection`.** Both work in Spring Boot 4.x. `@DynamicPropertySource` is explicit, requires fewer classes, and is already familiar from the Spring documentation. `@ServiceConnection` requires `spring-boot-testcontainers` starter and a `@TestConfiguration` or `@ImportTestcontainers` wrapper. Both starters are needed for the dependency management (BOM), but the `@ServiceConnection` path adds indirection with no benefit for a single test class. The plan should default to `@DynamicPropertySource`.

**Container scope: `static`.** The `PostgreSQLContainer` must be `static` so JUnit 5 + Testcontainers start it once per class, not once per test. The `@BeforeEach` cleanup should use `deleteAll()` on repositories (same as all other `@SpringBootTest` tests in this codebase), not restart the container.

**`moodlog.ai.*` properties carry through.** The migration test inherits all properties from `src/test/resources/application.properties` that aren't overridden by `@DynamicPropertySource`. AI is already disabled; `moodlog.journal.*` limits also carry through. No test-scoped Spring profile or additional `@TestPropertySource` is needed.

**No `@DataJpaTest` — use `@SpringBootTest`.** `@DataJpaTest` would be cheaper but it replaces the datasource with H2 by default (even with Testcontainers) unless `@AutoConfigureTestDatabase(replace = NONE)` is added. `@SpringBootTest` with `@DynamicPropertySource` is simpler and consistent with the rest of the test suite.

**Migration count must be asserted.** Flyway silently skips migrations whose checksums are in `flyway_schema_history` but whose SQL files are missing from the classpath. A context-load-only test would pass in that case. Asserting `flyway.info().all()` length == 4 makes a new migration that wasn't applied (or a deleted migration file) fail the test explicitly.

---

## Risk Response Guidance — Research Corrections

**Risk #6 — "V1 and V2 migrations" wording in test plan §2 is stale.** V3 and V4 have shipped since the plan was written. The Source column already cites the correct evidence (`H2 vs PostgreSQL`, `infrastructure.md`, `pom.xml`). Only the risk wording needs updating: replace "V1 and V2 migrations" with "V1–V4 migrations" in the risk scenario text.

**Response guidance accuracy**: The test plan response guidance ("prove Flyway V1 and V2 migrations apply cleanly against a real PostgreSQL instance; schema is queryable; no H2-specific SQL present") is correct in intent and should be updated to reference V1–V4. The anti-pattern ("Skipping because H2 tests pass; not asserting post-migration schema shape") is confirmed appropriate — the Hibernate `validate` + `flyway.info()` assertions directly address it.

**Must challenge ("H2 tests pass means PostgreSQL migrations work")** — confirmed as the core assumption the test disproves. H2 `MODE=PostgreSQL` is not a full emulation. No obvious incompatibilities were found in the current SQL, but this is not a reason to skip the gate — the gate's value compounds over time as new migrations are added.

---

## Historical Context

- `context/archive/2026-06-12-testing-ai-boundary-hardening/research.md` — noted H2 in-memory setup for tests (test profile switch); no migration concerns raised, as the focus was on AI boundary
- `context/archive/2026-06-12-trends-time-zone-accuracy/research.md` — time-zone tests relied on H2 in-memory; `FixedClockTestConfiguration` used for time control; no migration concerns raised
- `context/foundation/infrastructure.md` — Railway + PostgreSQL 16 as the production target; code-rollback-does-not-undo-data-changes risk is listed; migration safety is explicitly called out as a concern
- `context/foundation/test-plan.md:§2 Risk #6` — original risk written when V1 and V2 were the only migrations; V3 and V4 added subsequently

---

## Open Questions

1. **PostgreSQL version in container**: Use `postgres:16` to match Railway's target. Confirm Railway's actual PostgreSQL major version if it has changed since `infrastructure.md` was written (2026-05-28).

2. **Risk #6 wording in `test-plan.md` §2**: Should the plan update "V1 and V2" to "V1–V4" now, or defer to `--refresh`? Recommend updating §2 source evidence inline (this is an evidence correction, not a strategy change) — the post-research backport check permits this.

3. **Future migrations and the gate**: As V5, V6, etc. are added, the `assertEquals(4L, applied)` assertion must be updated. Consider whether to assert `>= 4` (floor guard) or the exact count (exact gate). Exact count is stricter and catches a missing file earlier; recommend exact count, updated with each new migration.

4. **`spring-boot-testcontainers` BOM version**: Spring Boot 4.x should manage this. If the build fails with "version not found," explicit Testcontainers BOM import may be needed. The plan should note this as a likely first-run issue to resolve.
