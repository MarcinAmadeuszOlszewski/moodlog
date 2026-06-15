---
change_id: testing-migration-safety-gate
title: Phase 4 test rollout — Flyway migration safety gate (Testcontainers + PostgreSQL)
status: implementing
created: 2026-06-15
updated: 2026-06-15
archived_at: null
---

## Notes

Open a change folder for rollout Phase 4 of context/foundation/test-plan.md: "Migration safety gate".
Risks covered: Risk #6 (Flyway V1 and V2 migrations applied against H2 in all tests but PostgreSQL in production — incompatible SQL or missing constraint causes app to fail to start in prod with no test catching it).
Test types planned: integration (Testcontainers + @SpringBootTest).
Risk response intent:
- Risk #6: prove Flyway V1 and V2 migrations apply cleanly against a real PostgreSQL instance, schema is queryable after all migrations run, and no H2-specific SQL is present; challenge "H2 tests pass means PostgreSQL migrations work" and "SERIAL vs IDENTITY and VARCHAR(n) are fully compatible"; avoid skipping because H2 tests pass and not asserting post-migration schema shape.
After creating the folder, follow the downstream continuation rule.
