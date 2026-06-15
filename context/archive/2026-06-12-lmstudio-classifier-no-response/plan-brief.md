# LMStudio Classifier Compatibility — Plan Brief

> Full plan: `context/changes/lmstudio-classifier-no-response/plan.md`
> Frame brief: `context/changes/lmstudio-classifier-no-response/frame.md`

## What & Why

`OpenAiMoodClassifier` unconditionally sends `response_format: {"type": "json_object"}` to the AI
endpoint. LMStudio rejects this parameter with an immediate HTTP error before generating any output,
so every classification fails silently with `PROVIDER_ERROR`. We're introducing a configurable
`moodlog.ai.response-format-type` property (default: `none`) so local model servers work out of the
box while hosted providers can still use `json_object` or `json_schema` mode via env var.

## Starting Point

`OpenAiMoodClassifier.java:40-45` hardcodes `ResponseFormat.Type.JSON_OBJECT` in every call; no
property or branching exists. Confirmed root cause via `PROVIDER_ERROR` log and LMStudio showing no
generated output.

## Desired End State

Saving a journal entry with LMStudio triggers actual inference: prompt logs at line 47, response
logs at line 51, and the entry is saved with a real mood tag. Operators can switch response-format
mode per environment via `MOODLOG_AI_RESPONSE_FORMAT_TYPE`.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Root cause | `response_format: json_object` incompatibility | `PROVIDER_ERROR` + LMStudio never generates output | Frame |
| Fix strategy | Configurable mode property | Unblocks LMStudio while keeping flexibility for hosted OpenAI | Plan |
| Default mode | `none` | Universally compatible; prompt already instructs JSON output | Plan |
| Test coverage | Add `ArgumentCaptor<Prompt>` test for `none` mode | Existing tests don't verify request shape; prevents silent regression | Plan |
| Default model name | `qwen2.5-7b-instruct-1m` | Matches currently loaded model; old default was `bielik-minitron-fit-6b` | Plan |

## Scope

**In scope:**
- Add `moodlog.ai.response-format-type` property (values: `none`, `json_object`, `json_schema`)
- Update `MoodClassifierConfiguration` to read and pass the property
- Update `OpenAiMoodClassifier` to accept the mode and branch in `classify()`
- Correct default model name in `application.properties`
- Add unit test verifying `none` mode omits `response_format` from built options

**Out of scope:**
- Automatic provider detection (LMStudio vs hosted OpenAI)
- Timeout changes
- Integration tests against a real LMStudio endpoint
- Backfilling `UNKNOWN` entries created before the fix

## Architecture / Approach

Thin configuration pass-through: `application.properties` → `@Value` in `MoodClassifierConfiguration`
→ new constructor parameter in `OpenAiMoodClassifier` → branch in `classify()` before
`OpenAiChatOptions` is built. No new beans, no new classes.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Configurable response-format mode | Properties, constructor wiring, branching logic, default model name fix, unit test | `json_schema` mode wiring requires Spring AI API verification against the version in `pom.xml` |

**Prerequisites:** LMStudio running with `qwen2.5-7b-instruct-1m` loaded for manual verification  
**Estimated effort:** ~1 session (4 files, ~20-30 lines of net change)

## Open Risks & Assumptions

- `json_schema` mode implementation requires confirming the exact `ResponseFormat.JsonSchema` builder
  API in the Spring AI version in use — the plan describes intent, not the exact API call
- If LMStudio does not support `json_schema` mode, that mode will hit the same `PROVIDER_ERROR`;
  `none` is the safe default

## Success Criteria (Summary)

- `./mvnw test` passes with all existing tests green plus the new request-shape test
- With no env overrides, `OpenAiMoodClassifier` line 51 fires and logs real response text from LMStudio
- Saved journal entry shows a real mood tag (not `UNKNOWN`) after classification
