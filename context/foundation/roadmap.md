---
project: "MoodLog"
version: 1
status: draft
created: 2026-05-29
updated: 2026-06-16
prd_version: 1
main_goal: low-complexity
top_blocker: none
---

# Roadmap: MoodLog

> Derived from `context/foundation/prd.md` (v1) + auto-researched codebase baseline.
> Edit-in-place; archive when superseded.
> Slices below are listed in dependency order. The "At a glance" table is the index.

## Vision recap

MoodLog is for adults who journal and want a low-friction way to turn free-text entries into a clearer view of their emotional state over time. The product stores private entries, assigns mood automatically, and shows trends across days and weeks so patterns stop depending on slow, subjective manual review.

## North star

**S-02: User can save a free-text entry and see an automatic mood result in their private journal.** — This sits as early as `S-01` allows because it is the smallest end-to-end slice that proves the core product idea under a low-complexity strategy.

> Here, the north star — the smallest end-to-end slice that proves the core product idea works — is placed as early as its prerequisites allow, because later history and trend work only matter if this save-and-classify loop works first.

## At a glance

| ID | Change ID | Outcome (user can …) | Prerequisites | PRD refs | Status |
|---|---|---|---|---|---|
| S-01 | private-journal-access | register, sign in, sign out, and reach a private journal shell | — | FR-001 | done |
| S-02 | first-mood-classified-entry | save a free-text entry and see an automatic mood result in the private journal | S-01 | US-01, FR-002, FR-003, FR-005 | done |
| S-03 | history-and-mood-trends | review private entry history plus 7-day, 30-day, and weekly mood trends | S-02 | US-01, FR-003, FR-007 | done |
| S-04 | entry-ownership-and-mood-correction | edit or delete owned entries and correct an assigned mood tag | S-03 | FR-004, FR-006 | done |

## Baseline

What's already in place in the codebase as of `2026-05-29` (auto-researched + user-confirmed).
Foundations below assume these are present and do NOT re-scaffold them.

- **Frontend:** partial — server-rendered UI via Thymeleaf (`src\main\java\com\amadeuszx\moodlog\IndexController.java`, `src\main\resources\templates\index.html`), but no SPA framework or frontend build pipeline.
- **Backend / API:** present — Spring Boot app bootstrap and MVC/REST handlers already exist (`src\main\java\com\amadeuszx\moodlog\Application.java`, `IndexController.java`, `RandomNumberController.java`).
- **Data:** absent — no database driver, ORM, repositories, schema, migrations, or seed data are present.
- **Auth:** absent — no security library, login/register/logout flow, or route-level auth middleware is present.
- **Deploy / infra:** partial — Railway recommendation and live deploy docs exist (`context\foundation\infrastructure.md`, `context\deployment\deploy-plan.md`), but CI/CD, Docker, and IaC are still absent.
- **Observability:** partial — platform/runtime logs are documented, but the app has no explicit metrics, tracing, or error-tracking hooks.

## Foundations

No explicit foundations on first generation. With a present backend, a partial UI layer, and a low-complexity sequencing goal, the minimum auth, persistence, and AI wiring stay inside the first consuming slices instead of becoming horizontal work ahead of user value.

## Slices

### S-01: Private journal access

- **Outcome:** User can register, sign in, sign out, and reach a private journal shell.
- **Change ID:** private-journal-access
- **PRD refs:** FR-001
- **Prerequisites:** —
- **Parallel with:** —
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Sequenced first because every later capability assumes a private user boundary; the main risk is overbuilding auth beyond the single-role MVP.
- **Status:** done

### S-02: First mood-classified entry

- **Outcome:** User can save a free-text entry and see an automatic mood result in the private journal.
- **Change ID:** first-mood-classified-entry
- **PRD refs:** US-01, FR-002, FR-003, FR-005
- **Prerequisites:** S-01
- **Parallel with:** —
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Sequenced second because this is the first full proof of value; the main risk is spreading work across progress, fallback, and AI wiring before the save-and-result loop works end to end.
- **Status:** done

### S-03: History and mood trends

- **Outcome:** User can review private entry history plus 7-day, 30-day, and weekly mood trends.
- **Change ID:** history-and-mood-trends
- **PRD refs:** US-01, FR-003, FR-007
- **Prerequisites:** S-02
- **Parallel with:** —
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Sequenced after `S-02` because trends without trusted saved entries are mostly cosmetic; the main risk is building dashboard breadth before the entry model settles.
- **Status:** done

### S-04: Entry ownership and mood correction

- **Outcome:** User can edit or delete owned entries and correct an assigned mood tag.
- **Change ID:** entry-ownership-and-mood-correction
- **PRD refs:** FR-004, FR-006
- **Prerequisites:** S-03
- **Parallel with:** —
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Sequenced last because correction and deletion depend on existing entries, history context, and visible mood output; the main risk is mixing trust/ownership controls into the first-value flow too early.
- **Status:** done

## Backlog Handoff

| Roadmap ID | Change ID | Suggested issue title | Ready for `/10x-plan` | Notes |
|---|---|---|---|---|
| S-01 | private-journal-access | Private journal access shell | yes | Start here; this establishes the private boundary every later slice assumes. |
| S-02 | first-mood-classified-entry | First mood-classified journal entry | no | Plan after `S-01`; include one AI-mocked integration path from `shape-notes.md`. |
| S-03 | history-and-mood-trends | Private history and 7/30-day mood trends | no | Plan after `S-02`; depends on persisted classified entries. |
| S-04 | entry-ownership-and-mood-correction | Entry editing, deletion, and mood correction | no | Plan after `S-03`; depends on existing history and visible mood state. |

## Open Roadmap Questions

1. **What is the target QPS ballpark?** — Owner: user. Block: roadmap-wide (non-blocking).
2. **What is the target data volume ballpark?** — Owner: user. Block: roadmap-wide (non-blocking).

## Parked

- **Import or export in v1** — Why parked: PRD `## Non-Goals`; the MVP stays focused on writing entries and reviewing mood trends inside the product.
- **Email or push notifications** — Why parked: PRD `## Non-Goals`; the first release does not extend beyond the journaling flow itself.
- **Mobile app** — Why parked: PRD `## Non-Goals`; the first release stays on the web.
- **External-factor correlation such as sleep or activity** — Why parked: PRD `## Non-Goals`; this belongs to a later phase.
- **Sharing of entries** — Why parked: PRD `## Non-Goals`; the MVP remains private in behavior.
- **Payments or subscriptions** — Why parked: PRD `## Non-Goals`; monetization is out of scope for the first version.
- **Multi-language UI** — Why parked: PRD `## Non-Goals`; the MVP keeps a single-language interface.
- **Automated build/test checks in GitHub Actions** — Why parked: lifted from `shape-notes.md` technical-roadmap notes; useful, but not required before the first low-complexity user-facing flow.

## Done

- **S-01: User can register, sign in, sign out, and reach a private journal shell.** — Archived 2026-06-14 → `context/archive/2026-05-29-private-journal-access/`. Lesson: —.
- **S-02: User can save a free-text entry and see an automatic mood result in the private journal.** — Archived 2026-06-14 → `context/archive/2026-05-30-first-mood-classified-entry/`. Lesson: —.
- **S-03: User can review private entry history plus 7-day, 30-day, and weekly mood trends.** — Archived 2026-06-14 → `context/archive/2026-06-01-history-and-mood-trends/`. Lesson: —.
- **S-04: User can edit or delete owned entries and correct an assigned mood tag.** — Archived 2026-06-15 → `context/archive/2026-06-15-entry-ownership-and-mood-correction/`. Lesson: —.
