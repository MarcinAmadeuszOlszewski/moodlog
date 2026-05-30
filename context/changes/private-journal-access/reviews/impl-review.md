<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Private journal access

- **Plan**: `context/changes/private-journal-access/plan.md`
- **Scope**: Phases 1-4 of 4
- **Date**: 2026-05-30
- **Verdict**: REJECTED
- **Findings**: 1 critical 2 warnings 0 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | FAIL |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | FAIL |

## Findings

### F1 — Registration keeps the pre-auth session alive

- **Severity**: ❌ CRITICAL
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: `src/main/java/com/amadeuszx/moodlog/AuthController.java:83`
- **Detail**: Signup bypasses Spring Security's normal login/session strategy and writes the authenticated `SecurityContext` straight into the current `HttpSession`. The session ID is not rotated first, so a pre-seeded anonymous session can survive into the authenticated state. That creates a session fixation risk and breaks the plan's requirement that registration end with the same authenticated session shape as login.
- **Fix A ⭐ Recommended**: Route post-signup auth through Spring Security's authentication/session strategy instead of hand-writing the session.
  - Strength: Closes fixation at the source and keeps signup aligned with the app's normal login path.
  - Tradeoff: Touches auth wiring and the controller flow.
  - Confidence: HIGH — the current risk exists precisely because the framework's session handling is bypassed.
  - Blind spot: Haven't verified whether saved-request behavior should also be applied after signup.
- **Fix B**: Keep the current controller flow but rotate the session ID before saving the `SecurityContext`.
  - Strength: Narrow patch with a small blast radius.
  - Tradeoff: Signup still stays on a custom auth path that can drift from future login/session behavior.
  - Confidence: MEDIUM — it closes the fixation window, but not the deeper divergence from framework auth handling.
  - Blind spot: It does not automatically pick up future session-auth features.
- **Decision**: FIXED via Fix A

### F2 — Duplicate signup can still escape as a 500 under concurrency

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: `src/main/java/com/amadeuszx/moodlog/UserAccountService.java:41`
- **Detail**: Registration does `existsByEmail(...)` and only then `save(...)`. The DB uniqueness constraint is tested, but the service/controller do not translate a save-time uniqueness collision. Two concurrent signup requests for the same email can therefore bypass the pre-check and one of them will fail as an unhandled persistence exception instead of the explicit duplicate-email validation promised by the plan.
- **Fix**: Catch the save-time uniqueness exception (for example `DataIntegrityViolationException`) and translate it into `DuplicateUserAccountException` so the user still gets the planned duplicate-email response.
- **Decision**: FIXED

### F3 — "Safe identifiers only" is not actually true in auth logging

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: `src/main/java/com/amadeuszx/moodlog/UserAccountService.java:60`
- **Detail**: Phase 4 requires auth logs to use safe identifiers only, but `safeEmailIdentifier()` currently returns the full normalized email, and both registration and login logs write that raw address (`UserAccountService.java:42,60,109` and `SecurityConfiguration.java:65,75-80`). The regression tests lock this in by asserting `email=ela@example.com` (`AuthenticationFlowTests.java:253,269`). That means manual progress item 4.4 is checked off even though the implementation still exposes personally identifying login identifiers in logs.
- **Fix**: Change auth logging to use a masked or hashed identifier, update the log assertions to reject raw email addresses, and re-run the phase 4 manual log check before treating 4.4 as done.
- **Decision**: FIXED — manual 4.4 re-check pending
