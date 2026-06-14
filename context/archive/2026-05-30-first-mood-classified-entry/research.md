---
date: 2026-05-31T11:41:52.4154719+02:00
researcher: GitHub Copilot
git_commit: 3a8d6acc49c5645845204880116fd9281157bb82
branch: master
repository: MarcinAmadeuszOlszewski/moodlog
topic: "Requirement context for first-mood-classified-entry / FR-005"
tags: [research, requirements, mood-classification, journaling]
status: complete
last_updated: 2026-05-31
last_updated_by: GitHub Copilot
last_updated_note: "Added follow-up research for AI provider choice and MVP integration path"
---

# Research: Requirement context for first-mood-classified-entry / FR-005

**Date**: 2026-05-31T11:41:52.4154719+02:00
**Researcher**: GitHub Copilot
**Git Commit**: `3a8d6acc49c5645845204880116fd9281157bb82`
**Branch**: `master`
**Repository**: `MarcinAmadeuszOlszewski/moodlog`

## Research Question

Research requirement context for change-id `first-mood-classified-entry`. Determine what FR-005 requires, adjacent functional constraints, whether AI is explicitly required or whether rule-based classification could satisfy the requirement, and any historical decisions that matter.

## Summary

- FR-005 requires automatic classification of each saved entry into both a **mood tag and score**; the PRD calls this the product's core differentiator ([`context/foundation/prd.md:92-96`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/foundation/prd.md#L92-L96)).
- The first consuming slice is S-02: save a **free-text** entry and show an **automatic mood result** in the **private journal** after S-01 auth is in place ([`context/foundation/roadmap.md:67-77`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/foundation/roadmap.md#L67-L77)).
- Adjacent constraints include private ownership, authenticated access, visible progress during analysis, unknown-mood fallback on classification failure, and a cost cap below $0.01 per entry ([`context/foundation/prd.md:51-61`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/foundation/prd.md#L51-L61), [`context/foundation/prd.md:74-83`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/foundation/prd.md#L74-L83), [`context/foundation/prd.md:97-116`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/foundation/prd.md#L97-L116), [`context/foundation/prd.md:128-133`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/foundation/prd.md#L128-L133)).
- FR-005 text alone is algorithm-agnostic, but broader requirement context explicitly assumes **AI classification**, **AI wiring**, and an **AI-mocked integration path**. A deterministic rules engine would not be a clean fit without clarifying or revising that wording ([`context/foundation/prd.md:46-48`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/foundation/prd.md#L46-L48), [`context/foundation/roadmap.md:51-52`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/foundation/roadmap.md#L51-L52), [`context/foundation/roadmap.md:108-109`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/foundation/roadmap.md#L108-L109), [`context/foundation/shape-notes.md:166-171`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/foundation/shape-notes.md#L166-L171)).
- Historical context from S-01 matters: the protected `/journal` page was intentionally left as a shell with a **non-submitting first-entry CTA**, so S-02 is the first slice that should introduce real entry persistence/classification ([`context/changes/private-journal-access/plan.md:54-55`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/changes/private-journal-access/plan.md#L54-L55), [`context/changes/private-journal-access/plan.md:174-176`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/changes/private-journal-access/plan.md#L174-L176)).

## Detailed Findings

### FR-005 requirement

- FR-005 is explicit about two outputs, not one: the product must automatically classify a saved entry into a **mood tag and score** ([`context/foundation/prd.md:92-96`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/foundation/prd.md#L92-L96)).
- The PRD business-logic section repeats the same contract: the rule outputs a mood label and score per saved entry, then feeds trends over time ([`context/foundation/prd.md:120-126`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/foundation/prd.md#L120-L126)).
- Roadmap S-02 narrows the first implementation target to the smallest end-to-end proof: save a free-text entry and show the automatic mood result in the private journal ([`context/foundation/roadmap.md:24-25`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/foundation/roadmap.md#L24-L25), [`context/foundation/roadmap.md:67-77`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/foundation/roadmap.md#L67-L77)).

### Adjacent functional constraints

- US-01 ties create + classify + history/trends together conceptually: after save, the entry is stored, receives a mood tag, appears in private history, and contributes to trend data once the result is available ([`context/foundation/prd.md:51-61`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/foundation/prd.md#L51-L61)).
- FR-002 keeps input scope minimal: authenticated users create a **free-text** entry, without extra fields forced into the MVP contract ([`context/foundation/prd.md:74-78`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/foundation/prd.md#L74-L78)).
- FR-003 requires private entry history, but roadmap sequencing keeps broad history/trend work for S-03; S-02 should show the automatic result without expanding into full history/dashboard breadth ([`context/foundation/prd.md:79-83`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/foundation/prd.md#L79-L83), [`context/foundation/roadmap.md:79-100`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/foundation/roadmap.md#L79-L100)).
- FR-006 means the assigned mood tag is not final forever; later slices must allow manual correction, so S-02 should avoid baking in an unchangeable tag model ([`context/foundation/prd.md:97-101`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/foundation/prd.md#L97-L101)).
- Access-control rules constrain all entry work to authenticated users and owner-only visibility ([`context/foundation/prd.md:128-133`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/foundation/prd.md#L128-L133)).
- NFRs add three important operational constraints: show visible progress while analysis runs, never lose the entry if classification fails, and keep typical classification cost below $0.01 per saved entry ([`context/foundation/prd.md:111-116`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/foundation/prd.md#L111-L116)).
- Roadmap risk language reinforces that S-02 should not over-expand into all progress/fallback/AI concerns before the save-and-result loop works end to end ([`context/foundation/roadmap.md:76-76`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/foundation/roadmap.md#L76-L76)).

### Is AI explicitly required?

- FR-005 itself says **automatic classification**, not “LLM,” “model,” or “OpenAI,” so the requirement text is provider-agnostic in isolation ([`context/foundation/prd.md:92-96`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/foundation/prd.md#L92-L96)).
- But the surrounding PRD language explicitly assumes **AI classification** failure as a named case, not just generic classifier failure ([`context/foundation/prd.md:46-48`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/foundation/prd.md#L46-L48)).
- The roadmap says the minimum auth, persistence, and **AI wiring** belong inside the first consuming slices, and S-02 backlog notes require “one AI-mocked integration path” ([`context/foundation/roadmap.md:51-52`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/foundation/roadmap.md#L51-L52), [`context/foundation/roadmap.md:108-109`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/foundation/roadmap.md#L108-L109)).
- Shape notes preserve the same implementation expectation: seed notes mention **OpenAI API** and at least one main journaling integration test with **AI mocked** ([`context/foundation/shape-notes.md:166-171`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/foundation/shape-notes.md#L166-L171)).
- Bottom line: a rules engine could satisfy “automatic classification” literally, but the broader accepted product context currently assumes AI-backed classification with a mocked AI integration seam. Using rules only would need explicit product clarification or wording changes.

### Historical context that matters

- The current change folder is only a stub that points back to S-02 in the roadmap; no prior plan or research exists yet for this change ([`context/changes/first-mood-classified-entry/change.md:1-13`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/changes/first-mood-classified-entry/change.md#L1-L13)).
- S-01 deliberately stopped at private access and a journal shell; it explicitly refused to fake entry creation before S-02 ([`context/changes/private-journal-access/plan.md:54-55`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/changes/private-journal-access/plan.md#L54-L55), [`context/changes/private-journal-access/plan.md:174-176`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/changes/private-journal-access/plan.md#L174-L176)).
- The S-01 plan brief repeats that if stakeholders expect real entry creation from the existing CTA, scope must expand into S-02 ([`context/changes/private-journal-access/plan-brief.md:64-66`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/changes/private-journal-access/plan-brief.md#L64-L66)).
- No archived change artifacts exist yet beyond the archive README, so there is no older archived decision trail for journaling/classification work ([`context/archive/README.md:1-4`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/archive/README.md#L1-L4)).
- Deployment and starter docs matter as recent context, not requirement changes: the starter metadata still says `has_ai: false`, and the first deployment explicitly excluded auth/AI rollout, which means AI is expected later rather than already provisioned ([`context/foundation/tech-stack.md:15-19`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/foundation/tech-stack.md#L15-L19), [`context/deployment/first-deployment.md:5-7`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/deployment/first-deployment.md#L5-L7)).

## Code References

- `context/foundation/prd.md:51-61` - US-01 acceptance criteria for save, assigned mood tag, private history, and trend reflection.
- `context/foundation/prd.md:74-116` - FR-002 through FR-007 plus NFRs that constrain first entry classification.
- `context/foundation/prd.md:128-133` - Auth and ownership boundary for all journal entries.
- `context/foundation/roadmap.md:67-77` - S-02 scope and risk notes.
- `context/foundation/roadmap.md:108-109` - Explicit AI-mocked integration-path note for S-02 planning.
- `context/foundation/shape-notes.md:166-171` - Seed-note history for OpenAI API and AI-mocked testing.
- `context/changes/private-journal-access/plan.md:54-55` - S-01 defers real entry creation to S-02.
- `context/changes/private-journal-access/plan.md:174-176` - Journal shell contract: placeholder CTA only before S-02.

## Architecture Insights

- The product contract separates **entry durability** from **classification success**: the entry must persist even when mood classification fails, with `unknown mood` as the fallback state. That argues for a save-first pipeline with classification as a follow-on step rather than a single all-or-nothing request ([`context/foundation/prd.md:46-48`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/foundation/prd.md#L46-L48), [`context/foundation/prd.md:111-116`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/foundation/prd.md#L111-L116)).
- Roadmap sequencing intentionally keeps auth, persistence, and AI wiring inside the first value-delivering slices instead of introducing a large horizontal AI subsystem before user value appears ([`context/foundation/roadmap.md:51-52`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/foundation/roadmap.md#L51-L52)).
- Later manual tag correction (FR-006) implies the data model should distinguish system-assigned classification from user-overridden classification, even if S-02 only exposes the first state ([`context/foundation/prd.md:97-101`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/foundation/prd.md#L97-L101)).

## Historical Context (from prior changes)

- `context/changes/private-journal-access/plan.md` established `/journal` as a protected shell and explicitly deferred actual entry submission to this change.
- `context/changes/private-journal-access/plan-brief.md` documented that the visible first-entry CTA was intentionally a placeholder until S-02.
- `context/archive/README.md` is the only archive artifact; no archived journaling/classification decisions exist yet.

## Related Research

- None yet.

## Open Questions

- None specific to FR-005 beyond the existing PRD open questions on target QPS and data volume.

## Follow-up Research 2026-05-31T11:46:30.8867881+02:00

### Question

Should FR-005 use AI tools, how should the integration work, and what should we choose for the fastest MVP?

### Recommendation

- **Use AI, not rules-only.** The accepted product context already assumes AI-backed classification and an AI-mocked integration seam, so a pure rules engine would drift from the current roadmap/shape notes ([`context/foundation/roadmap.md:108-109`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/foundation/roadmap.md#L108-L109), [`context/foundation/shape-notes.md:149-152`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/context/foundation/shape-notes.md#L149-L152)).
- **Choose Spring AI + OpenAI as the default MVP path.** This repo is already a Spring Boot app with no AI dependency yet, so the smallest new surface is one Spring AI starter plus one provider adapter ([`pom.xml:33-56`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/pom.xml#L33-L56)). Spring AI can map model output directly into a typed Java object, and OpenAI Structured Outputs can enforce a JSON Schema instead of relying on loose prompt parsing ([Spring AI structured output reference](https://docs.spring.io/spring-ai/reference/api/chat/openai-sdk-chat.html), [OpenAI Structured Outputs guide](https://platform.openai.com/docs/guides/structured-outputs)).
- **Keep Gemini as the first fallback.** Google Gemini also supports JSON Schema-based structured output and has low-cost flash tiers, so it is a credible provider swap if billing, terms, or evaluation results come out better for your text mix ([Gemini structured output docs](https://ai.google.dev/gemini-api/docs/structured-output), [Gemini pricing](https://ai.google.dev/gemini-api/docs/pricing)).
- **Drop AWS Comprehend from the MVP shortlist.** Its sentiment API is cheap, but the official supported-language list for sentiment does **not** include Polish, which is a bad fit for a Polish-first journal product ([Amazon Comprehend supported languages](https://docs.aws.amazon.com/comprehend/latest/dg/supported-languages.html), [Amazon Comprehend sentiment docs](https://docs.aws.amazon.com/comprehend/latest/dg/how-sentiment.html)).
- **Do not start with self-hosted models for MVP speed.** Self-hosting adds runtime, prompt-eval, and ops work before the product has validated its mood taxonomy or latency envelope.

### Why this fits the current codebase

- The private journal is still only a shell: `/journal` renders placeholder copy and a disabled first-entry CTA, so S-02 can add real persistence and classification without undoing an existing entry workflow ([`src/main/java/com/amadeuszx/moodlog/JournalController.java:11-19`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/src/main/java/com/amadeuszx/moodlog/JournalController.java#L11-L19), [`src/main/resources/templates/journal.html:13-18`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/src/main/resources/templates/journal.html#L13-L18)).
- The only persisted domain object today is `UserAccount`; there is no `JournalEntry` entity or mood model yet ([`src/main/java/com/amadeuszx/moodlog/UserAccount.java:11-30`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/src/main/java/com/amadeuszx/moodlog/UserAccount.java#L11-L30)).
- Configuration already follows env-backed Spring properties, so AI provider/model/timeouts should follow the same pattern instead of being hardcoded ([`src/main/resources/application.properties:1-16`](https://github.com/MarcinAmadeuszOlszewski/moodlog/blob/3a8d6acc49c5645845204880116fd9281157bb82/src/main/resources/application.properties#L1-L16)).

### Suggested MVP integration path

1. Add a `JournalEntry` aggregate and table with at least: `content`, `classification_status`, `system_mood_tag`, `system_mood_score`, `classified_at`, `classification_provider`, `classification_model`, and `classification_error`.
2. Introduce a `MoodClassifier` port that returns a typed result like `{ moodTag, moodScore }`.
3. Implement two adapters from day one:
   - `StubMoodClassifier` for tests/dev and the roadmap's required AI-mocked path.
   - `OpenAiMoodClassifier` for production.
4. On entry save, **persist first** with `classification_status = PENDING` and unknown mood, then trigger classification immediately after save.
5. Update the entry to `COMPLETED` or `FAILED`; on failure keep the entry and show `unknown` mood to satisfy the PRD fallback rule.
6. In the UI, redirect back to `/journal` and render `Analizuję...` while status is pending, then the final mood result when ready. This matches the visible-progress requirement without introducing a queueing platform too early.
7. Keep provider configuration property-driven, e.g. `moodlog.ai.provider`, `moodlog.ai.model`, `moodlog.ai.timeout`, `moodlog.ai.enabled`, with secrets injected from environment.

### What to choose

| Option | Verdict | Why |
|---|---|---|
| Spring AI + OpenAI structured output | **Recommended** | Best fit for fastest MVP in this Spring Boot app: typed Java mapping, strict JSON schema support, easy provider swap later. |
| Spring AI + Gemini structured output | Good fallback | Similar architecture and strong pricing; good if you prefer Google billing or provider terms. |
| Amazon Comprehend sentiment | No | Cheap and simple, but sentiment-language support does not cover Polish and the output is coarser than mood-tag + score. |
| Self-hosted model (e.g. Ollama/vLLM) | Later | Better for privacy/control, worse for MVP speed because infra and evaluation work arrive too early. |

### Cost and output-shape notes

- OpenAI's current small model tier (`GPT-5.4 mini`) is listed at **$0.75 / 1M input tokens** and **$4.50 / 1M output tokens**, which leaves comfortable room under the PRD's `$0.01` per-entry target for short journal text ([OpenAI API pricing](https://openai.com/api/pricing/)).
- Gemini's low-cost flash pricing is also comfortably below the PRD cap, with paid flash tiers listed around **$0.125-$0.25 / 1M input tokens** and **$0.75-$1.50 / 1M output tokens** depending on serving mode ([Gemini pricing](https://ai.google.dev/gemini-api/docs/pricing)).
- For this use case, do **not** start with embeddings, vector search, or fine-tuning. A small fixed mood taxonomy plus structured output is the faster first implementation.

### Minimal technical shape

- Define a fixed mood enum first (for example: `JOY`, `CALM`, `SAD`, `ANXIOUS`, `ANGRY`, `OVERWHELMED`, `NEUTRAL`, `UNKNOWN`).
- Ask the model for exactly one enum value plus a numeric confidence/score.
- Validate the result server-side before saving it to the entry row.
- Keep the classification adapter isolated from controllers so the same interface can later support retries, provider swaps, or manual override workflows from FR-006.

### External references

- [Spring AI structured output reference](https://docs.spring.io/spring-ai/reference/api/chat/openai-sdk-chat.html)
- [OpenAI Structured Outputs guide](https://platform.openai.com/docs/guides/structured-outputs)
- [OpenAI API pricing](https://openai.com/api/pricing/)
- [Gemini structured output docs](https://ai.google.dev/gemini-api/docs/structured-output)
- [Gemini pricing](https://ai.google.dev/gemini-api/docs/pricing)
- [Amazon Comprehend supported languages](https://docs.aws.amazon.com/comprehend/latest/dg/supported-languages.html)
- [Amazon Comprehend sentiment docs](https://docs.aws.amazon.com/comprehend/latest/dg/how-sentiment.html)
