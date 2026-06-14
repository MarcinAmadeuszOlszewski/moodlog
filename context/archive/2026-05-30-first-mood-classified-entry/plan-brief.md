# First mood-classified journal entry — Plan Brief

> Full plan: `context/changes/first-mood-classified-entry/plan.md`
> Research: `context/changes/first-mood-classified-entry/research.md`

## What & Why

Build the first real private-journal flow on `/journal`: a signed-in user can submit free-text entries, get an automatically classified mood tag plus score, and see a simple recent-entry list on the same page. This turns the current placeholder into the first end-to-end journaling capability and establishes the AI-backed mood contract that later history, trends, and manual correction features will build on.

## Starting Point

Today `/journal` is only a protected shell with placeholder copy and no persistence (`src/main/java/com/amadeuszx/moodlog/JournalController.java:11-19`, `src/main/resources/templates/journal.html:13-18`). The repo already has working auth boundaries, Flyway + JPA persistence patterns, and integration-heavy `MockMvc` tests, but no journal-entry model and no AI dependency/config surface yet.

## Desired End State

When this plan is done, an authenticated user can create multiple entries, see the latest 10 in a newest-first list, and view an app-owned mood tag plus `0-100` mood-intensity score for each saved entry. Production uses a hosted AI provider through a `MoodClassifier` seam, while local development and tests use a deterministic stub implementation.

The schema already separates system-assigned mood data from future user overrides, so FR-006 can layer manual correction without redefining the entry model. In this plan, classification is a hard prerequisite for persistence: on classifier failure, the draft stays in the form and no entry is written.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Visible scope | Save entries and show a simple recent list on `/journal` | Gives immediate value now while avoiding full history and trend work from S-03. | Plan |
| Save sequencing | Classification must succeed before persistence | You explicitly chose a no-temporary-state flow even though it deviates from the earlier save-first product assumption. | Plan |
| AI integration | Spring AI + OpenAI adapter behind `MoodClassifier`, with stub locally/tests | Satisfies real FR-005 behavior while preserving the roadmap's mocked path for deterministic verification. | Research |
| Classification contract | Fixed app-owned mood enum + integer score `0-100` | Keeps the domain stable across providers and future trend/override features. | Plan |
| Future-proofing | Separate system result from nullable override fields in the schema | Avoids overloading one field with two meanings when FR-006 arrives. | Plan |
| Testing strategy | Integration-first plus focused repository/service tests | Matches current repo conventions and protects the full MVC/auth wiring. | Plan |

## Scope

**In scope:**
- New `journal_entries` persistence slice with ownership and recent-entry query
- Real provider-backed classification plus a deterministic stub path
- `/journal` GET + POST flow, validation, failure UX, and recent-entry rendering
- Safe logging and regression coverage for stub and hosted-provider modes

**Out of scope:**
- Trends, analytics, and dedicated history browsing from S-03
- Manual correction UI, edit/delete, or export flows
- Background processing, queues, polling, or saved `PENDING` states

## Architecture / Approach

Use the existing Spring Boot layering: Flyway migration + JPA entity/repository for `JournalEntry`, then a `JournalEntryService` that resolves the current user, delegates classification to a provider-neutral `MoodClassifier`, and persists only successful results. `JournalController` stays responsible for HTTP/view flow, using PRG on success and same-view re-render on classification or validation failure; Thymeleaf renders the form plus a fixed-size recent-entry list with Polish labels.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Entry domain and persistence | `journal_entries` schema, entity, enum, repository, and ownership-safe recent query | Schema choices must fit later override/history work without another redesign |
| 2. Classification seam and application service | `MoodClassifier`, OpenAI + stub adapters, property config, and blocking-save orchestration | Provider config or payload validation could destabilize the first write flow |
| 3. Journal write flow and recent-entry UI | Real `/journal` form, create action, recent list, and preserved-draft failure UX | Blocking-save UX can feel rough if provider latency or errors are frequent |
| 4. Operational hardening and regression coverage | Safe logging, stub/hosted verification, and full regression protection | Privacy/logging mistakes or config drift can undercut trust in the feature |

**Prerequisites:** Hosted-provider credentials for manual prod-like verification, acceptance of the blocking-save deviation from earlier product notes, and the existing Maven test workflow.
**Estimated effort:** ~3-4 focused implementation sessions across 4 phases.

## Open Risks & Assumptions

- The blocking-save decision intentionally overrides an earlier save-first fallback assumption and may need revisiting after review or user feedback.
- Hosted-provider text transfer is acceptable for MVP production as long as local/test runs stay stubbed.
- The seven-tag mood taxonomy is sufficient for the first slice and can be refined later without changing the flow shape.

## Success Criteria (Summary)

- A signed-in user can create multiple journal entries and see them immediately in a private recent-entry list on `/journal`.
- The same feature works with a hosted AI provider in production and a deterministic stub in local/test environments.
- Failure handling preserves the user's draft, blocks the save cleanly, and avoids leaking raw journal text to logs.
