<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: First mood-classified journal entry

- **Plan**: context\changes\first-mood-classified-entry\plan.md
- **Scope**: Phases 1-4 of 4
- **Date**: 2026-05-31
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical 2 warnings 1 observation

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | WARNING |

## Findings

### F1 — Timeout wrapper can leave provider calls running

- **Severity**: ⚠️ WARNING
- **Impact**: 🔬 HIGH — architectural stakes; think carefully before deciding
- **Dimension**: Safety & Quality
- **Location**: src/main/java/com/amadeuszx/moodlog/OpenAiMoodClassifier.java:71
- **Detail**: `callWithinTimeout` wraps the blocking OpenAI call in `CompletableFuture.supplyAsync(...)` and then waits with `future.get(timeout)`. On timeout it cancels the future, but that does not guarantee the underlying HTTP call stops. Under slow provider periods, timed-out requests can continue consuming threads and network after the user already received a failure.
- **Fix A ⭐ Recommended**: Move timeout enforcement to the underlying OpenAI/Spring AI client and call the model synchronously here.
  - Strength: Enforces the timeout at the I/O boundary and avoids double-threading each classification request.
  - Tradeoff: Needs bean/config wiring beyond this class.
  - Confidence: MED — strong general pattern, but the exact Spring AI timeout hook in this repo was not verified during review.
  - Blind spot: We did not inspect the generated HTTP client path.
- **Fix B**: Keep the wrapper, but route provider calls through a bounded classification executor.
  - Strength: Caps blast radius when provider latency spikes.
  - Tradeoff: More moving parts and still may not stop the outbound HTTP call itself.
  - Confidence: HIGH — bounded executors reliably contain thread growth.
  - Blind spot: Does not solve in-flight provider work continuing after timeout.
- **Decision**: FIXED via Fix A

### F2 — Hosted-provider manual check is marked done without code evidence

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: context/changes/first-mood-classified-entry/plan.md:403
- **Detail**: Progress item 4.4 is checked complete, but the repo only shows unit coverage for adapter failure mapping plus stub-mode/full-suite test coverage. There is no checked-in evidence of a real hosted-provider smoke run or log review from a provider-backed classification.
- **Fix**: Set 4.4 back to pending until someone performs the hosted-provider smoke test, or add concrete evidence before keeping it checked.
- **Decision**: FIXED

### F3 — Form validation contract is split between form and controller

- **Severity**: 👀 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: src/main/java/com/amadeuszx/moodlog/JournalEntryForm.java:5
- **Detail**: Phase 3 put both non-blank and max-length validation on `JournalEntryForm`. Actual max-length enforcement lives in `JournalController#validateContentLength`, while the form itself only carries `@NotBlank`. Behavior is correct on `/journal`, but the input contract is split across two objects instead of being owned by the form.
- **Fix**: Move the max-length rule into the form/binding layer so `JournalEntryForm` fully represents the request contract.
- **Decision**: FIXED
