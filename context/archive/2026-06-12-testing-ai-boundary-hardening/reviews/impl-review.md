<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Phase 1 test rollout — AI classifier boundary and entry durability

- **Plan**: context/changes/testing-ai-boundary-hardening/plan.md
- **Scope**: Full Plan Review (Phases 1-3)
- **Date**: 2026-06-12
- **Verdict**: APPROVED WITH WARNINGS (NEEDS ATTENTION)
- **Findings**: 0 critical | 2 warnings | 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Findings

### F1 — Potential prompt injection vulnerability in classifier

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: src/main/java/com/amadeuszx/moodlog/classification/OpenAiMoodClassifier.java:132
- **Detail**: The raw user input text is formatted directly into the template string for prompt engineering. An attacker could craft a journal entry to inject prompt instructions and subvert the classification mechanism.
- **Fix**: Wrap the user input in XML tags `<entry>%s</entry>` inside the prompt.
- **Decision**: FIXED (Demarcated user input with `<entry>` tags in buildPrompt)

### F2 — Missing transactional annotation on save entry method

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: src/main/java/com/amadeuszx/moodlog/journal/JournalEntryService.java:73
- **Detail**: `JournalEntryService.saveEntry()` is not marked `@Transactional`, which can cause lazy-loading or synchronization errors.
- **Fix**: Add `@Transactional` annotation to `saveEntry()` to ensure SQL operations execute safely in a transaction context.
- **Decision**: FIXED (Added `@Transactional` annotation to saveEntry)

### F3 — Missing input length check/truncation in classifier

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: src/main/java/com/amadeuszx/moodlog/classification/OpenAiMoodClassifier.java:31
- **Detail**: Classifier sends entry text to OpenAI without validating or truncating length.
- **Fix**: Truncate the input to `MAX_CONTENT_LENGTH` (2000 chars) before making the LLM call.
- **Decision**: FIXED (Truncated input to JournalEntry.MAX_CONTENT_LENGTH before calling buildPrompt)

### F4 — Missing Lombok boilerplate reduction on JournalEntry

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: src/main/java/com/amadeuszx/moodlog/journal/JournalEntry.java:18
- **Detail**: `JournalEntry.java` has manually written getters and constructors instead of Lombok annotations.
- **Fix**: Replace manual constructor and getter declarations with Lombok annotations.
- **Decision**: SKIPPED (Hibernate/JPA entity constructor/getter safety)
