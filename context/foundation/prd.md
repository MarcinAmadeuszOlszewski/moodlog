---
project: "MoodLog"
version: 1
status: draft
created: 2026-05-27
context_type: greenfield
product_type: web-app
target_scale:
  users: small
  qps: "# TODO: target_scale.qps — see Open Questions"
  data_volume: "# TODO: target_scale.data_volume — see Open Questions"
timeline_budget:
  mvp_weeks: 4
  hard_deadline: null
  after_hours_only: true
---

## Vision & Problem Statement

Adults who keep a journal or care about mental hygiene struggle to assess their emotional state objectively over time. Manual review of free-text entries is slow and subjective, so emotional patterns are easy to miss.

The product accepts free-text journal entries, classifies mood automatically, and visualizes trends over time so the user can notice patterns that are difficult to see alone. Even at 100x the expected usage, the core product rule stays the same.

## User & Persona

### Primary persona

An adult who keeps a journal or actively cares about mental hygiene, and wants a low-friction way to write in free text instead of filling out forms or checkboxes.

### Product moment

They reach for the product when they want to record a daily entry and later review how their mood changes across days or weeks.

## Success Criteria

### Primary

- A logged-in user can write a journal entry and see an automatically assigned mood tag for that entry.

### Secondary

- The user can review a visible mood trend over the last 7 or 30 days.

### Guardrails

- Entries remain private and isolated per user.
- If AI classification fails, the entry is still saved with an unknown mood rather than being lost.

## User Stories

### US-01: User records an entry and gets a mood result

- **Given** a signed-in user with access to their private journal
- **When** they save a new free-text entry
- **Then** the entry is stored, receives a mood tag, and appears in their history with updated trend data

#### Acceptance Criteria

- The saved entry appears only in the signed-in user's private journal history.
- The saved entry shows an assigned mood tag after processing completes.
- The user's trend view reflects the new entry once the mood result is available.

## Functional Requirements

### Authentication

- FR-001: User can register, sign in, and sign out to access a private journal. Priority: must-have
  > Socrates: Counter-argument considered: "A seeded single-user account or
  > sign-in-only flow would ship faster." Resolution: kept; private journal
  > access is core to the MVP because privacy boundaries matter from day one.

### Journaling

- FR-002: Authenticated user can create a free-text journal entry. Priority: must-have
  > Socrates: Counter-argument considered: "Strip the capture flow to the
  > smallest possible input and postpone richer entry rules." Resolution:
  > kept; this FR already stays at the minimum useful shape — free-text entry
  > creation, without locking extra form fields into the requirement.
- FR-003: Authenticated user can view their own entry history. Priority: must-have
  > Socrates: Counter-argument considered: "A simple history list may be
  > enough at first; a separate detail view can wait." Resolution: revised;
  > the MVP keeps private history viewing as must-have and drops a dedicated
  > detail view from the core FR set.
- FR-004: Authenticated user can edit or delete their own entries. Priority: must-have
  > Socrates: Counter-argument considered: "Creation plus history might be
  > enough, and edit/delete could wait." Resolution: kept; in a personal
  > journal, ownership includes being able to correct or remove private
  > entries.

### Mood analysis

- FR-005: The product can automatically classify a saved entry into a mood tag and score. Priority: must-have
  > Socrates: Counter-argument considered: "Manual tagging would be cheaper
  > for v1, or the score could wait." Resolution: kept; automatic mood
  > classification is the core differentiator, and the score belongs with the
  > same value proposition.
- FR-006: Authenticated user can manually correct the assigned mood tag. Priority: must-have
  > Socrates: Counter-argument considered: "Delay override until AI trust is
  > tested, or capture disagreement without changing the stored tag."
  > Resolution: kept; mood labeling can be wrong, so user correction is part
  > of the MVP trust model.

### Trends

- FR-007: Authenticated user can view 7-day and 30-day mood trends plus a weekly dashboard summary. Priority: must-have
  > Socrates: Counter-argument considered: "A private history view might be
  > enough, or trends could shrink to a 7-day-only slice." Resolution: kept;
  > trend visualization is necessary to deliver the core insight of the
  > product.

## Non-Functional Requirements

- A user gets immediate visible feedback when entry analysis starts, and continuous visible progress while mood classification is still running.
- A failed mood-classification request does not prevent the entry from being saved; the user still sees the saved entry with an unknown mood.
- Journal entries and mood history remain visible only to the owning signed-in user.
- Typical mood classification cost stays below $0.01 per saved entry.

## Business Logic

The product infers a mood tag and score from a user's free-text journal entry, then turns repeated entries into visible emotional trends over time.

The rule consumes the text the user writes in each journal entry and the sequence of prior entries they have already saved.

Its outputs are a mood label and score for each saved entry, plus a trend view that helps the user notice patterns across days and weeks.

The user encounters this rule immediately after saving an entry and later when reviewing their private mood history and trend views.

## Access Control

- Authentication: email + password login.
- Authorization model: flat single-role user model for the MVP.
- Data boundary: each logged-in user can access only their own journal entries.
- Unauthenticated access: journaling, entry history, and mood trends are gated until the user signs in.

## Non-Goals

- No import or export in v1; the MVP focuses on writing entries and reviewing mood trends inside the product.
- No email or push notifications; the first version does not extend beyond the journaling flow itself.
- No mobile app; the first release stays on the web.
- No external-factor correlation such as sleep or activity; that belongs to a later phase.
- No sharing of entries; the MVP remains private and single-user in behavior.
- No payments or subscriptions; monetization is out of scope for the first version.
- No multi-language UI; the MVP keeps a single-language interface.

## Open Questions

1. **What is the target QPS ballpark?** — TBD by user. Block: no.
2. **What is the target data volume ballpark?** — TBD by user. Block: no.
