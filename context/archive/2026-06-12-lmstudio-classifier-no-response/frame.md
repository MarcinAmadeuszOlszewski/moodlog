# Frame Brief: LMStudio classifier receives no response

> Framing step before /10x-plan-spring. This document captures what is *actually*
> at issue, separated from what was initially assumed.

## Reported Observation

Prompt IS logged (OpenAiMoodClassifier.java:47 fires). LMStudio receives the
request. But LMStudio **never generates any response** — no output appears in
LMStudio logs at all. App silently falls back to MoodTag.UNKNOWN stub result.
Line 51 (response log) never fires.

## Initial Framing (preserved)

- **User's stated cause or approach**: Not stated — pure observation-driven report.
- **User's proposed direction**: Not stated — diagnosing what's wrong.
- **Pre-dispatch narrowing**: App shows silent stub result (UNKNOWN mood); LMStudio
  never generates a response (not a delayed response — never).

## Dimension Map

The observation could originate at any of these dimensions:

1. **[response_format incompatibility]** — `response_format: {"type": "json_object"}`
   is sent unconditionally to LMStudio; many local model servers reject this parameter
   with an HTTP error before inference starts → no generation logged in LMStudio.
2. **[model name mismatch]** — `bielik-minitron-fit-6b` is sent without validation;
   if LMStudio has a different model loaded, it could fail before generation.
3. **[timeout fires first]** — `moodlog.ai.timeout=5s` is wired to the Spring AI HTTP
   client; if the model or LMStudio initialisation takes >5s, timeout throws before
   the response arrives.
4. **[exception silently swallowed]** — exception is thrown but not surfaced visibly,
   making the cause invisible to the operator.

## Hypothesis Investigation

| Hypothesis | Evidence | Verdict |
| --- | --- | --- |
| `response_format: JSON_OBJECT` incompatibility | Hardcoded at `OpenAiMoodClassifier.java:40-45`; no LMStudio suppression or override in any config or test; `application.properties:19` defaults to local LMStudio endpoint; no `response_format` override property exists | STRONG |
| Model name mismatch (`bielik-minitron-fit-6b`) | Model name passed without validation to `OpenAiChatOptions` (`OpenAiMoodClassifier.java:41`); no model-existence check in `MoodClassifierConfiguration.java:32-40`; LMStudio might silently reject unknown model before generation | WEAK |
| Timeout (5s) fires first | `application.properties:20,25` → `spring.ai.openai.timeout` wired to Spring AI HTTP client; `isTimeoutFailure()` detection exists at `OpenAiMoodClassifier.java:102-124`; BUT: user confirmed "no response ever in LMStudio" — a pure timeout would still result in LMStudio eventually generating | WEAK |
| Exception silently swallowed | `JournalEntryService.java:233-234` logs WARN (`journal.classification.failure`) and re-throws; `saveEntryWithUnknownMood()` logs second WARN (`journal.entry.saved.with.unknown.mood`) at line 139; both visible at configured INFO level | NONE |

## Narrowing Signals

- **"No response ever in LMStudio"**: Rules out pure timeout as root cause. A
  timeout fires on the *client* side after the request is sent; LMStudio would
  still generate and its logs would show output eventually. "Never generates"
  means LMStudio rejects the request before inference — consistent with an
  unsupported HTTP parameter causing an immediate error response.
- **"Silently falls back to stub"**: Exception IS thrown and caught by
  `JournalEntryService.java:82-83`. Two WARN logs fire every time. The user
  sees no error because the save succeeds (with UNKNOWN mood) — intentional
  design, not a bug in exception handling.
- **Confirmed from app logs** (`reason=PROVIDER_ERROR`, not `PROVIDER_TIMEOUT`):
  LMStudio returned an HTTP error response immediately — not a slow/hung call.
  This is the signature of an unsupported request parameter being rejected
  before inference starts. Timeout hypothesis definitively ruled out.
- **Model in logs is `bielik-11b-v3.0-instruct`**, not the default
  `bielik-minitron-fit-6b` — user is overriding `MOODLOG_AI_DEFAULT_MODEL`.
  Not the root cause, but model name and `application.properties` default
  are out of sync.

## Cross-System Convention

Spring AI's `ResponseFormat.Type.JSON_OBJECT` maps to the OpenAI API field
`response_format: {"type": "json_object"}`. The real OpenAI API and well-tested
OpenAI-compatible servers support this. Local inference servers (LMStudio,
Ollama, llama.cpp) have inconsistent support — many simply return a 400/422
error for unrecognised fields. LMStudio specifically: support for
`response_format` depends on the loaded model and the LMStudio version; models
without explicit JSON mode support will fail.

The local convention in this repo treats LMStudio as the default provider
(`application.properties:18-19`) but the classifier was written against the
full OpenAI API surface without a local-model compatibility layer.

## Reframed (or Confirmed) Problem Statement

> **The actual problem to plan around is**: `OpenAiMoodClassifier` unconditionally
> sends `response_format: {"type": "json_object"}` to LMStudio, which the local
> model server rejects before generating any output.

The prompt instruction already asks for JSON-only output — the API-level
`response_format` constraint is belt-and-suspenders for hosted OpenAI but is
the active blocker for LMStudio. Removing or conditionally suppressing it
would let LMStudio proceed to inference; the prompt instruction alone is
sufficient for a cooperative local model. The 5s timeout is a separate concern
worth revisiting once classification is unblocked.

## Confidence

**HIGH** — `reason=PROVIDER_ERROR` (not PROVIDER_TIMEOUT) from real logs confirms
immediate HTTP error from LMStudio + hardcoded `response_format` with no
suppression + no prior LMStudio compatibility work in any change doc.

## What Changes for /10x-plan-spring

The plan has a design choice to resolve before implementing:

**Option A — Remove `response_format` entirely.** Rely on the prompt instruction
("Return JSON only") alone. Works with all LMStudio versions and models; no
API-level schema enforcement.

**Option B — Switch from `json_object` to `json_schema` with the actual schema.**
Send `response_format: {"type": "json_schema", "json_schema": {...}}` with the
`OpenAiMoodResponse` schema inlined. LMStudio 0.3.x+ supports structured output
mode for compatible models; gives stronger output guarantees. `BeanOutputConverter`
at `OpenAiMoodClassifier.java:39` already generates the schema — Spring AI's
`ResponseFormat.Type.JSON_SCHEMA` can consume it directly. The research doc
`testing-ai-boundary-hardening/research.md:192` mentioned `JSON_SCHEMA` mode
as considered but not used.

**Risk for Option B**: only works if the running LMStudio version and model
support structured outputs — not guaranteed. If unsupported, same PROVIDER_ERROR.
The plan should address whether to verify LMStudio compatibility first or make
the approach configurable so both modes can be selected.

## References

- Source files: `OpenAiMoodClassifier.java:40-45`, `application.properties:19,20,25`,
  `MoodClassifierConfiguration.java:32-40`, `JournalEntryService.java:75-84,233-234`
- Investigation tasks: a50e781bb79f9dcc0 (response_format), ada8155b2d1e3b3bb (exception path),
  a25f685beba9310cd (timeout/model name)
