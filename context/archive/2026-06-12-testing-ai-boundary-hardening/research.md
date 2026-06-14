---
date: 2026-06-12T11:40:00+02:00
researcher: GitHub Copilot
git_commit: 2c733aa08fec2a8bb73302745079697199de516b
branch: master
repository: MarcinAmadeuszOlszewski/moodlog
topic: "AI boundary hardening — Phase 1 test rollout: entry durability and classifier response contract"
tags: [research, classification, journal, durability, mood-tag, spring-ai, prd-gap]
status: complete
last_updated: 2026-06-12
last_updated_by: GitHub Copilot
---

# Research: AI Boundary Hardening — Phase 1 Test Rollout

**Date**: 2026-06-12T11:40:00+02:00
**Researcher**: GitHub Copilot
**Git Commit**: 2c733aa08fec2a8bb73302745079697199de516b
**Branch**: master
**Repository**: MarcinAmadeuszOlszewski/moodlog

---

## Research Question

Ground rollout Phase 1 of `context/foundation/test-plan.md`:

- **Risk #1**: Does `JournalEntryService.saveEntry()` fall back to saving with an unknown mood when the classifier fails/times out, or does it block persistence? Verify the intended fallback path against the PRD NFR.
- **Risk #2**: How does `OpenAiMoodClassifier` parse the AI response? Which fields are mandatory? How are null/missing fields and out-of-range scores handled? What do existing tests miss?

---

## Summary

**Risk #1 is a confirmed implementation gap.** `saveEntry()` BLOCKS persistence when classification fails — the entry is NOT saved. The PRD NFR ("A failed mood-classification request does not prevent the entry from being saved; the user still sees the saved entry with an unknown mood") is NOT satisfied. `MoodTag` has no `UNKNOWN` value. No fallback path exists anywhere. The plan for Phase 1 must include adding this implementation before testing it.

**Risk #2 is a confirmed test gap.** `OpenAiMoodClassifier` already handles the most common failure modes correctly (null response, blank text, convert() returning null, RuntimeException mapping). However, three parsing paths are NOT covered by existing tests: the non-null-but-incomplete response branch in `extractResponseText()`, the out-of-range score path through `MoodClassification` validation, and the primitive `int moodScore` default-to-zero behavior when the field is absent from the AI JSON.

---

## Detailed Findings

### Risk #1 — Entry durability: save/classify flow

**`JournalEntryService.saveEntry()` (lines 73–103)**

The call sequence is:
1. `resolveUserAccount(currentUserEmail)` — loads the `UserAccount` or throws
2. `classifyContent(content, safeUserIdentifier)` — calls the classifier; rethrows on any failure
3. Build `JournalEntry` using fields from `MoodClassification` (lines 78–91)
4. `journalEntryRepository.save(journalEntry)` (line 93)
5. Log success and return

**The classifier is invoked at line 76, before the `JournalEntry` is built.** If it throws, execution never reaches line 78 or 93.

```
saveEntry()
  → classifyContent()      ← throws MoodClassificationFailedException
  → [never reached] new JournalEntry(...)
  → [never reached] journalEntryRepository.save(...)
```

**`classifyContent()` (lines 184–204)**

```java
private MoodClassification classifyContent(String content, String safeUserIdentifier) {
    try {
        return moodClassifier.classify(content);
    }
    catch (MoodClassificationFailedException exception) {
        logClassificationFailure(safeUserIdentifier, exception);
        throw exception;                          // ← always rethrows
    }
    catch (IllegalArgumentException exception) {  // ← from MoodClassification ctor
        ...
        throw moodClassificationFailedException;  // ← always rethrows
    }
}
```

There is **no catch block in `saveEntry()` itself** and **no fallback path** that calls `journalEntryRepository.save()` with an unknown mood. The method throws on all classification failures.

**`JournalController.saveJournalEntry()` (lines 56–79)**

The controller catches `MoodClassificationFailedException` at line 74 and re-renders the form with `classificationError`:

```java
catch (MoodClassificationFailedException exception) {
    model.addAttribute("classificationError", exception.getMessage());
    populateJournalModel(userEmail, model);
    return "journal";    // ← re-renders form, NOT a redirect
}
```

The user's text is preserved because `@ModelAttribute("journalEntryForm") JournalEntryForm` is already bound before this method runs, and the template uses `th:field="*{content}"` which reads back from the bound object. The user sees an error banner but their text stays in the textarea.

**Conclusion vs PRD NFR**: The PRD says the entry must be saved with an unknown mood on classification failure. The implementation instead discards the entry and asks the user to retry. The text is preserved in the browser, but the entry is NOT persisted.

**`MoodTag` enum (lines 3–12)**

```java
public enum MoodTag {
    JOY, CALM, NEUTRAL, SADNESS, ANXIETY, ANGER, OVERWHELMED
}
```

No `UNKNOWN` value exists. `polishMoodLabel()` in `JournalEntryService` (lines 404–414) is an exhaustive switch with no `default` case — adding `UNKNOWN` requires updating this switch.

**Timeout properties (application.properties)**

`moodlog.ai.timeout=${MOODLOG_AI_TIMEOUT:5s}` controls `spring.ai.openai.timeout`. This means a slow AI provider will eventually throw `OpenAIIoException` with an `InterruptedIOException` cause, which is detected by `isTimeoutFailure()` and mapped to `PROVIDER_TIMEOUT`. The entry is NOT saved in this case either.

**Test-profile switch**

`src/test/resources/application.properties` sets `moodlog.ai.enabled=false` and `moodlog.ai.provider=stub`. `MoodClassifierConfiguration` returns `StubMoodClassifier` as the `MoodClassifier` bean in tests. Flow tests use `@MockitoBean MoodClassifier` to override this further for per-test control.

---

### Risk #2 — Classifier response contract: parsing flow

**`OpenAiMoodClassifier.classify()` (lines 30–65)**

The full parsing pipeline:

```
1. callProvider(prompt)           → ChatResponse  (or throws)
2. extractResponseText(response)  → String        (or throws INVALID_RESPONSE)
3. outputConverter.convert(text)  → OpenAiMoodResponse (or null)
4. null check on result           → throws INVALID_RESPONSE if null
5. new MoodClassification(...)    → validates fields (or throws IllegalArgumentException)
6. RuntimeException catch         → wraps as INVALID_RESPONSE
```

**Step 1 — `callProvider()` (lines 67–80)**

| Input to `openAiChatModel.call()` | Mapped to |
|---|---|
| Returns normally | `ChatResponse` passed to next step |
| Throws `OpenAIIoException` + timeout detected | `PROVIDER_TIMEOUT` |
| Throws `OpenAIIoException` (non-timeout) | `PROVIDER_ERROR` |
| Throws any other `RuntimeException` | `PROVIDER_ERROR` |

Timeout detection (`isTimeoutFailure()`, lines 90–112) walks the full cause chain looking for `InterruptedIOException`, `HttpTimeoutException`, or a message containing "timeout" or "timed out".

**Step 2 — `extractResponseText()` (lines 114–126)**

```java
if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
    throw buildFailureException(MoodClassificationFailureReason.INVALID_RESPONSE);
}
final String responseText = response.getResult().getOutput().getText();
if (!StringUtils.hasText(responseText)) {
    throw buildFailureException(MoodClassificationFailureReason.INVALID_RESPONSE);
}
```

All null-chain cases and blank text are caught and mapped to `INVALID_RESPONSE`.

**Step 3–4 — `BeanOutputConverter` and null check (lines 44–49)**

```java
final OpenAiMoodResponse openAiMoodResponse = outputConverter.convert(responseText);
if (openAiMoodResponse == null) {
    throw buildFailureException(MoodClassificationFailureReason.INVALID_RESPONSE);
}
```

`BeanOutputConverter` uses Jackson under the hood. If the JSON is malformed or cannot be deserialized to `OpenAiMoodResponse`, Jackson throws `JsonProcessingException` (a `RuntimeException`), which is caught at line 62–64 → `INVALID_RESPONSE`.

**Step 5 — `MoodClassification` compact constructor validation (lines 15–31)**

| Field | Validation | On violation |
|---|---|---|
| `moodTag` | non-null | `IllegalArgumentException` |
| `moodScore` | `0 ≤ score ≤ 100` | `IllegalArgumentException` |
| `provider` | has text | `IllegalArgumentException` |
| `model` | has text | `IllegalArgumentException` |
| `classifiedAt` | non-null | `IllegalArgumentException` |

These `IllegalArgumentException` instances are caught as `RuntimeException` at line 62–64 → `INVALID_RESPONSE`. (Also caught at `JournalEntryService.classifyContent()` lines 192–203 as a belt-and-suspenders catch.)

**`OpenAiMoodResponse` private record (line 141)**

```java
private record OpenAiMoodResponse(MoodTag moodTag, int moodScore) {}
```

- `moodTag` is an object type → deserializes to `null` if field is missing from JSON → `MoodClassification` ctor throws → `INVALID_RESPONSE`. ✓
- `moodScore` is a **primitive `int`** → Jackson defaults to `0` if field is missing from JSON. Score `0` passes `MoodClassification` validation (`0 ≥ 0 && 0 ≤ 100`). **A missing `moodScore` field silently saves with score 0.**
- Unknown `moodTag` enum string (e.g., `"HAPPY"`) → Jackson throws `JsonProcessingException` → `INVALID_RESPONSE`. ✓

**JSON Schema mode note**: The classifier uses `ResponseFormat.Type.JSON_SCHEMA` with the BeanOutputConverter's schema (lines 35–39), which strongly constrains the AI to produce valid JSON. However, this only reduces the probability of malformed responses; it does not eliminate them (API errors, quota failures, prompt injection). The error-handling code should still be tested.

---

### What existing tests cover

**`OpenAiMoodClassifierTests.java` (3 tests)**

| Test | Path covered |
|---|---|
| `missingProviderResponseBecomesInvalidResponse` | `call()` returns `null` → `INVALID_RESPONSE` via `extractResponseText` line 115 |
| `providerCallFailureBecomesProviderError` | `call()` throws `IllegalStateException` → `PROVIDER_ERROR` via `callProvider` line 77 |
| `slowProviderCallBecomesProviderTimeout` | `call()` throws `OpenAIIoException(InterruptedIOException)` → `PROVIDER_TIMEOUT` |

**`JournalEntryServiceTests.java` (relevant tests)**

| Test | Path covered |
|---|---|
| `savesJournalEntryAfterSuccessfulClassification` | Happy path — classifier returns, entry saved |
| `blocksPersistenceWhenTheClassifierFails` | `classify()` throws `MoodClassificationFailedException` → 0L entries |
| `rejectsInvalidClassificationPayloadsBeforePersistence` | `classify()` throws `IllegalArgumentException` → wrapped → 0L entries |
| `logsClassificationSuccessWithoutExposingTheEntryText` | Log safety on success |

---

### Confirmed test gaps for Phase 1

**For Risk #1 (entry durability — implementation gap first)**

The fallback behavior does not exist yet. The Phase 1 plan must address implementation AND then testing:

1. Add `MoodTag.UNKNOWN` to the enum.
2. Wrap the classifier call in `saveEntry()` with a catch that saves the entry with `MoodTag.UNKNOWN`, a neutral score (e.g., 0 or 50), and provider/model from the exception.
3. Update `polishMoodLabel()` to handle `MoodTag.UNKNOWN` (e.g., "Nieznane").
4. Update the controller: when the entry IS saved (even with unknown mood), redirect to `/journal` instead of re-rendering the form with an error. Add a banner to the journal page for "entry saved with unknown mood" case.
5. Test: `@SpringBootTest` + MockMvc — POST /journal with `@MockitoBean MoodClassifier` throwing `MoodClassificationFailedException` → assert entry count = 1, entry has `MoodTag.UNKNOWN`, redirect to `/journal`.

**For Risk #2 (classifier contract — test-only gaps)**

Missing unit tests in `OpenAiMoodClassifierTests`:

| Gap | Test scenario | Expected result |
|---|---|---|
| Non-null response but null `getResult()` | Mock returns `ChatResponse` with null result | `INVALID_RESPONSE` |
| Non-null result but blank text | Mock returns non-null response with blank `getText()` | `INVALID_RESPONSE` |
| `convert()` returns null (malformed JSON) | Mock returns response where BeanOutputConverter cannot parse | `INVALID_RESPONSE` |
| Out-of-range moodScore (e.g., 150) | Mock returns response where JSON has `{"moodTag":"CALM","moodScore":150}` | `INVALID_RESPONSE` |
| Missing `moodScore` field → score defaults to 0 | Mock returns JSON without `moodScore` field | **Currently saves with score 0 — evaluate whether this should throw `INVALID_RESPONSE` or be acceptable** |

The last gap is a design decision: is a missing score field from the AI an error, or is score=0 acceptable? If the team wants to reject it, change `moodScore` in `OpenAiMoodResponse` from `int` to `Integer` and add a null check before constructing `MoodClassification`.

---

## Code References

- `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryService.java:73–103` — `saveEntry()`: classify-first, no fallback path
- `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryService.java:184–204` — `classifyContent()`: logs and rethrows, no save-with-unknown fallback
- `src/main/java/com/amadeuszx/moodlog/journal/JournalEntryService.java:404–414` — `polishMoodLabel()`: exhaustive switch, must be updated for `UNKNOWN`
- `src/main/java/com/amadeuszx/moodlog/journal/JournalController.java:56–79` — POST `/journal`: catches classification failure, re-renders form with error
- `src/main/java/com/amadeuszx/moodlog/classification/OpenAiMoodClassifier.java:30–65` — `classify()`: full parsing pipeline
- `src/main/java/com/amadeuszx/moodlog/classification/OpenAiMoodClassifier.java:67–80` — `callProvider()`: provider exception → PROVIDER_TIMEOUT / PROVIDER_ERROR
- `src/main/java/com/amadeuszx/moodlog/classification/OpenAiMoodClassifier.java:114–126` — `extractResponseText()`: null chain + blank text → INVALID_RESPONSE
- `src/main/java/com/amadeuszx/moodlog/classification/OpenAiMoodClassifier.java:141` — `OpenAiMoodResponse(MoodTag moodTag, int moodScore)`: primitive `int` defaults to 0 on missing field
- `src/main/java/com/amadeuszx/moodlog/classification/MoodClassification.java:15–31` — compact ctor: validates all fields; score 0..100; moodTag non-null
- `src/main/java/com/amadeuszx/moodlog/classification/MoodTag.java:3–12` — NO `UNKNOWN` value; `polishMoodLabel` switch is exhaustive
- `src/main/resources/application.properties:17–24` — timeout: 5s default; provider: stub default
- `src/test/resources/application.properties` — `moodlog.ai.enabled=false`, `moodlog.ai.provider=stub`
- `src/test/java/com/amadeuszx/moodlog/classification/OpenAiMoodClassifierTests.java` — 3 tests; missing: blank text, out-of-range score, convert-null paths
- `src/test/java/com/amadeuszx/moodlog/journal/JournalEntryServiceTests.java:100–112` — `blocksPersistenceWhenTheClassifierFails` asserts 0L entries — this test will need updating once the fallback is implemented

---

## Architecture Insights

**Classify-first design**: The current `saveEntry()` is intentionally atomic: either classification succeeds and the entry is saved with a valid mood, or the whole operation fails. The PRD NFR wants a softer contract (save with unknown mood), which requires changing this atomicity guarantee.

**Test-profile isolation**: `StubMoodClassifier` is wired in all `@SpringBootTest` tests via properties. Tests that need to control classifier behavior use `@MockitoBean MoodClassifier` to replace it. This pattern is correct and should be used for the fallback test.

**`BeanOutputConverter` + JSON Schema**: The classifier uses structured output mode (`ResponseFormat.Type.JSON_SCHEMA`), which constrains the AI response shape. This is good for reliability but the error-handling code should still be tested for the cases where the constraint is violated (API errors, edge cases).

**`int` vs `Integer` for moodScore in response record**: Using primitive `int` means a missing JSON field silently defaults to 0. This is a latent issue: a score of 0 is valid but might not reflect what the AI intended. The team should decide whether to treat a missing score field as an error.

**`polishMoodLabel()` is exhaustive**: Adding `MoodTag.UNKNOWN` without updating this method will cause a compile-time error in modern Java (exhaustive switch expression). This is a good safety net.

---

## Risk Response Guidance — Research Corrections

**Risk #1**: The test plan response guidance says "Likely cheapest layer: MockMvc integration test with mocked classifier throwing." This is correct for the TEST layer, but the plan must first include the IMPLEMENTATION work (add `UNKNOWN`, add fallback). The test plan's "Must challenge" — "classifier exception = entry not saved is the correct behavior" — is confirmed to be the current behavior and confirmed to be WRONG per PRD. No correction needed to the test plan source column or risk wording; the response guidance is accurate. However, the planning prompt should explicitly include the implementation phases (not just test phases) in Phase 1.

**Risk #2**: The test plan response guidance is accurate. The "implementation mirror" anti-pattern is specifically relevant here: test expected values must come from the PRD spec ("score 0–100 is required") and the Spring AI contract, not from running the production code and copying its output.

---

## Historical Context

No prior changes under `context/changes/` or `context/archive/` directly address the AI classification boundary. This is first research into this surface.

---

## Related Research

None yet. Phase 2 research will cover `JournalEntryService.getTrendView()` and the Europe/Warsaw time-zone boundary logic.

---

## Open Questions

1. **Fallback score for UNKNOWN mood**: When saving with `MoodTag.UNKNOWN`, what score value should be stored? Options: 0 (neutral/empty), 50 (mid-range), null (would require schema change to allow null). The `JournalEntry` entity schema and `MoodClassification` validation (score 0..100 required) constrain this. Recommend 0 or 50.

2. **Controller behavior on fallback save**: When the entry IS saved with unknown mood, should the controller redirect to `/journal` (success path) or stay on the form with an informational message? The PRD says "the user still sees the saved entry" — redirect to `/journal` is consistent with this, where the entry appears with an "unknown" mood label.

3. **`int` vs `Integer` for `moodScore` in `OpenAiMoodResponse`**: Should a missing `moodScore` field in the AI response be treated as `INVALID_RESPONSE` or is score=0 acceptable? This should be decided before writing the missing-score test.

4. **`JournalEntryServiceTests.blocksPersistenceWhenTheClassifierFails`**: This test currently asserts `0L` entries on classifier failure. Once the fallback is implemented, this test will need to be updated to assert `1L` entry with `MoodTag.UNKNOWN`. The test name should also be updated to reflect the new behavior.
