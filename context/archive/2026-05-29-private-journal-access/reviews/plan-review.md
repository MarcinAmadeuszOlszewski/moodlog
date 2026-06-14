<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Implementation Plan — Private journal access

- **Plan**: `context/changes/private-journal-access/plan.md`
- **Mode**: Deep
- **Date**: 2026-05-30
- **Verdict**: REVISE
- **Findings**: 0 critical, 3 warnings, 0 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | WARNING |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | WARNING |
| Plan Completeness | WARNING |

## Grounding
Grounding: 5/5 existing paths ✓, 4/4 symbols ✓, brief↔plan ✓

## Findings

### F1 — Persistence bootstrap is still a placeholder

- **Severity**: ⚠️ WARNING
- **Impact**: 🔬 HIGH — architectural stakes; think carefully before deciding
- **Dimension**: Plan Completeness
- **Location**: Phase 1 — User account persistence and auth domain
- **Detail**: Phase 1 points work at `src/main/resources/db/**` and expects the first schema bootstrap to succeed, but the plan never chooses the migration mechanism or the datasource/test-store strategy. The repo currently has no persistence stack in `pom.xml:32-57` or `src/main/resources/application.properties:1-5`, while the foundation notes already seed PostgreSQL 16 in `context/foundation/shape-notes.md:166`. The implementer still has to guess between Flyway, `schema.sql`, JPA DDL, or a split test-database setup.
- **Fix**: Lock Phase 1 to one concrete database/bootstrap path: name the runtime database, the test database strategy, and the exact schema mechanism (`db/migration`, `schema.sql`, etc.) instead of `src/main/resources/db/**`.
  - Strength: Removes the biggest implementation guess in the first persistence slice and aligns the plan with the seeded PostgreSQL choice.
  - Tradeoff: Forces the plan to commit to one migration/test path now.
  - Confidence: HIGH — the codebase has no existing persistence convention, so the plan must supply it.
  - Blind spot: The exact test-container vs embedded-test-db choice still needs a decision.
- **Decision**: PENDING

### F2 — Password validation is promised but never defined

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: End-State Alignment
- **Location**: Desired End State; Phase 2; Phase 4
- **Detail**: The Desired End State promises basic password validation, and Phase 4 says a minimum password rule will be enforced consistently, but the plan never states what that rule is. Phase 2 can therefore be marked complete while signup still accepts whatever the implementer happens to allow, and the tests cannot assert the negative path deterministically.
- **Fix**: Define the password rule in the plan now, then move at least one invalid-password registration test into Phase 2.
  - Strength: Keeps the first signup checkpoint aligned with the promised auth behavior instead of deferring a core rule to hardening.
  - Tradeoff: Commits the product to a concrete password rule earlier.
  - Confidence: HIGH — Phase 2 already owns signup validation UX, so it is the natural place to pin the rule.
  - Blind spot: Exact Polish error copy may still need a product wording decision.
- **Decision**: PENDING

### F3 — Redirect ownership is not pinned down

- **Severity**: ⚠️ WARNING
- **Impact**: 🔬 HIGH — architectural stakes; think carefully before deciding
- **Dimension**: Blind Spots
- **Location**: Critical Implementation Details; Phase 2; Phase 3
- **Detail**: The plan says registration should end with the same authenticated-session shape as login and that saved-target state should be cleared on logout, while Phase 3 also promises redirect reuse for future protected routes. But no phase names the actual redirect owner or verifies saved-target clearing on logout. With no existing security flow in the repo, the implementer still has to guess between Spring Security request-cache/success-handler logic and controller-managed redirects.
- **Fix A ⭐ Recommended**: Standardize on Spring Security redirect ownership.
  - Strength: One redirect policy for login, signup, logout cleanup, and future private routes.
  - Tradeoff: Slightly more auth plumbing in the first slice.
  - Confidence: MED — it matches Spring conventions, but the exact Spring Boot 4 API surface still needs implementation-time confirmation.
  - Blind spot: If signup never needs non-`/journal` redirects in S-01, this may be more general than strictly necessary.
- **Fix B**: Narrow the redirect scope to the current slice.
  - Strength: Smaller first auth slice with fewer moving parts.
  - Tradeoff: The Phase 3 contract becomes less future-ready and needs wording changes.
  - Confidence: HIGH — today `/journal` is the only protected route, so this matches the current product surface.
  - Blind spot: If the roadmap expects reusable redirect handling as a foundational auth rule, this defers that work.
- **Decision**: PENDING
