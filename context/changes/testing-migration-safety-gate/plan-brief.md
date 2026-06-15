# Flyway Migration Safety Gate — Plan Brief

> Full plan: `context/changes/testing-migration-safety-gate/plan.md`
> Research: `context/changes/testing-migration-safety-gate/research.md`

## What & Why

Add a Testcontainers-backed `@SpringBootTest` that runs all four Flyway migrations against a real PostgreSQL 16 container and asserts the schema is correct and constraint-enforcing. H2 `MODE=PostgreSQL` is not a full PostgreSQL emulator — this gate exists to catch migration incompatibilities that H2 silently accepts before they reach production.

## Starting Point

All `@SpringBootTest` tests currently run against H2 in-memory. Four Flyway migrations (V1–V4) ship in production against PostgreSQL 16 (Railway), but no test has ever verified they apply cleanly against real PostgreSQL. `pom.xml` has no Testcontainers dependency; `src/test/resources/application.properties` hard-codes the H2 datasource URL.

## Desired End State

`FlywayMigrationPostgresTests` starts a `postgres:16` container, overrides the H2 datasource at runtime via `@DynamicPropertySource`, and asserts: (1) exactly 4 migrations applied successfully, (2) `UserAccount` rows can be persisted and reloaded with email uniqueness enforced, (3) `JournalEntry` rows can be persisted and reloaded. `.\mvnw.cmd test` is green with all pre-existing H2 tests unchanged.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
|---|---|---|---|
| Datasource override pattern | `@DynamicPropertySource` | Simpler than `@ServiceConnection`; overrides at highest Spring precedence without extra config classes | Research |
| Container scope | `static` (shared per class) | Avoids starting a new PostgreSQL container per test method; `@BeforeEach deleteAll()` handles data isolation | Research |
| Migration count assertion | `assertEquals(4L, applied)` | Exact count catches a missing SQL file (Flyway silently skips on checksum match); must be updated per new migration | Plan |
| Test class structure | `@SpringBootTest` + `@Testcontainers`, not `@DataJpaTest` | `@DataJpaTest` replaces the datasource with H2 by default, defeating the purpose | Research |
| PostgreSQL version | `postgres:16` | Matches Railway production target | Research |
| Scope of plan | Code + doc corrections | Research flagged §2 and §6.5 of test-plan.md as stale; correcting alongside the test prevents confusion | Plan |

## Scope

**In scope:**
- Two pom.xml test-scoped dependencies (`spring-boot-testcontainers`, `testcontainers:postgresql`)
- One new test class: `FlywayMigrationPostgresTests` (3 test methods)
- test-plan.md §2 Risk #6 wording correction (V1 and V2 → V1–V4)
- test-plan.md §6.5 cookbook entry (TBD → complete pattern)

**Out of scope:**
- No production code changes
- No changes to `src/test/resources/application.properties`
- No shared Testcontainers configuration (standalone gate only)
- No PostgreSQL-backed variants of existing H2 tests

## Architecture / Approach

`FlywayMigrationPostgresTests` is a standalone `@SpringBootTest` class. JUnit 5's `@Testcontainers` extension manages the `static PostgreSQLContainer<?>` lifecycle. `@DynamicPropertySource` redirects the three datasource properties to the container at context-refresh time, before Flyway runs. Hibernate `validate` (inherited from `src/test/resources/application.properties`) verifies the schema Flyway produced matches entity mappings. Three `@Test` methods then exercise the live schema via existing repositories.

## Phases at a Glance

| Phase | What it delivers | Key risk |
|---|---|---|
| 1. Add Testcontainers + write test class | Green `.\mvnw.cmd test` with PostgreSQL-backed migration assertions | BOM may not resolve Testcontainers version on first run (explicit BOM import fallback if needed) |
| 2. Update test-plan.md docs | §2 accurate (V1–V4), §6.5 filled with actionable cookbook | Minor — doc-only change |

**Prerequisites:** None — this plan is self-contained. `flyway-database-postgresql` and `org.postgresql:postgresql` runtime deps are already in `pom.xml`.

**Estimated effort:** ~1 session across 2 phases.

## Open Risks & Assumptions

- Spring Boot 4.x BOM is assumed to manage Testcontainers version. If `.\mvnw.cmd test` fails with "artifact version not found," an explicit Testcontainers BOM `<dependencyManagement>` import may be needed.
- Railway's PostgreSQL major version is assumed to still be 16 (per `infrastructure.md` dated 2026-05-28). `postgres:16` in the container matches this assumption.
- `assertEquals(4L, applied)` must be updated to `5L`, `6L`, etc. as new migrations are added. This is a known maintenance requirement.

## Success Criteria (Summary)

- `.\mvnw.cmd test` passes with 3 new green methods in `FlywayMigrationPostgresTests`
- Test output confirms `postgres:16` container started (not H2)
- All pre-existing H2-backed tests unaffected
