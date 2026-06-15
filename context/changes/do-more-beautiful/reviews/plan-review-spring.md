<!-- PLAN-REVIEW-SPRING-REPORT -->
# Spring Plan Review: do-more-beautiful Implementation Plan

- **Plan**: context/changes/do-more-beautiful/plan.md
- **Mode**: Deep
- **Date**: 2026-06-15
- **Verdict**: SOUND (REVISE before triage)
- **Findings**: 2 critical (all fixed), 0 warnings, 0 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | PASS (FAIL before triage) |
| Blind Spots | PASS (FAIL before triage) |
| Plan Completeness | PASS |

## Grounding
Grounding: 8/8 paths ✓, 4/4 symbols ✓, brief↔plan ✓, spring surfaces ✓, build/test commands ✓

## Findings

### F1 — ApplicationTests.java will fail after Phase 2

- **Severity**: ❌ CRITICAL
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Phase 2 — Success Criteria & Automated Verification
- **Detail**: The plan deletes `RandomNumberController`, `RandomNumberService`, and removes the guest counter text/JS from `index.html`. However, the integration test suite `ApplicationTests.java` has two test cases (`randomEndpointReturnsRandomNumber` and `indexPageContainsWelcomeMessageAndUsesRandomEndpoint`) that assert `/v1/random` returns a number and `/` contains the guest text. Without updating these tests, `./mvnw test` will fail during Phase 2.
- **Fix**: Update Phase 2 to explicitly modify `src/test/java/com/amadeuszx/moodlog/ApplicationTests.java` to remove or rewrite those two tests to verify that the landing page `/` contains the new application description (in Polish) instead of the old welcome text, and assert `/v1/random` is no longer mapped.
- **Decision**: FIXED (Added ApplicationTests.java update to Phase 2)

### F2 — Head fragment wiring deletes Chart.js scripts on journal-trends.html

- **Severity**: ❌ CRITICAL
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architectural Fitness
- **Location**: Phase 3 — Changes Required (3. Wire fragment into all 8 templates)
- **Detail**: The plan says: "Result: each template's `<head>` has exactly `th:block` + `<title>`." However, `journal-trends.html` contains `<script th:src="@{/js/chart.umd.min.js}" defer></script>` and `<script th:src="@{/js/journal-trends.js}" defer></script>` inside its `<head>`. Replacing its `<head>` entirely with only the head fragment and `<title>` will delete these scripts, breaking Chart.js trends rendering.
- **Fix**: Revise the Phase 3 template wiring section to specify that `journal-trends.html` must retain its `<script>` tags for Chart.js and custom JS in addition to the `th:block` and `<title>` elements inside its `<head>`.
- **Decision**: FIXED (Retained script tags in journal-trends.html head)
