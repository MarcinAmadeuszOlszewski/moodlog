# LMStudio Classifier Compatibility — Implementation Plan

## Overview

`OpenAiMoodClassifier` unconditionally sends `response_format: {"type": "json_object"}` to the
configured AI endpoint. LMStudio rejects this parameter with an HTTP error before generating any
output, causing every classification attempt to fail with `PROVIDER_ERROR` and silently fall back
to `MoodTag.UNKNOWN`. The fix introduces a configurable `moodlog.ai.response-format-type` property
so the field can be suppressed for local model servers while remaining available for hosted providers.

## Current State Analysis

- `OpenAiMoodClassifier.java:40-45` — `ResponseFormat.Type.JSON_OBJECT` hardcoded in every call to
  `classify()`, no branching on provider type
- `application.properties:19` — base URL defaults to `http://localhost:1234/v1` (LMStudio); the
  classifier was written against the full OpenAI API surface without a local-model compatibility layer
- `application.properties:24` — default model name already matches the model currently running (`qwen2.5-7b-instruct-1m`)
- `MoodClassifierConfiguration.java:40` — `OpenAiMoodClassifier` constructed with only model name;
  no response-format parameter passed
- `OpenAiMoodClassifierTests.java` — all tests create `new OpenAiMoodClassifier(openAiChatModel, "gpt-4o-mini")`;
  none verify what `OpenAiChatOptions` was built with

## Desired End State

After this plan:
- Saving a journal entry with LMStudio running triggers actual inference and logs the response at
  `OpenAiMoodClassifier.java:51`
- Response-format behavior is controlled by `MOODLOG_AI_RESPONSE_FORMAT_TYPE` env var (default: `none`)
- A unit test proves that `none` mode omits the `response_format` field from the built options

### Key Discoveries

- `OpenAiMoodClassifier.java:39` — `BeanOutputConverter<OpenAiMoodResponse>` already instantiated;
  its schema is available for `json_schema` mode if needed later
- `application.properties:20` — `spring.ai.openai.timeout=${moodlog.ai.timeout}` — timeout property
  already wired to Spring AI HTTP client; no timeout changes needed in this plan

## What We're NOT Doing

- Adding automatic provider detection (detecting LMStudio vs hosted OpenAI) — explicit configuration
  is simpler and more transparent
- Changing the timeout value — `5s` is a separate concern; unblock classification first
- Adding integration tests against a real LMStudio endpoint
- Backfilling entries that failed classification before this fix

## Implementation Approach

Add one new constructor parameter to `OpenAiMoodClassifier` and one new `@Value` binding in
`MoodClassifierConfiguration`. The `classify()` method branches on the mode string before building
`OpenAiChatOptions`: `none` → no `.responseFormat()` call, `json_object` → current behavior,
`json_schema` → `ResponseFormat.Type.JSON_SCHEMA` with schema from `BeanOutputConverter.getJsonSchema()`.
A new property in `application.properties` exposes the mode as an env-var override.

## Phase 1: Configurable Response-Format Mode

### Overview

Wire a `moodlog.ai.response-format-type` property end-to-end from `application.properties` through
`MoodClassifierConfiguration` into `OpenAiMoodClassifier`, update the default model name, and add
a unit test for the `none` mode.

### Changes Required

#### 1. Application properties

**File**: `src/main/resources/application.properties`

**Intent**: Expose the response-format mode as a configurable env-var override, defaulting to `none`
so LMStudio works out of the box.

**Contract**: Add the following property (ordering by convention near the other `moodlog.ai.*`
properties):

```
moodlog.ai.response-format-type=${MOODLOG_AI_RESPONSE_FORMAT_TYPE:none}
```

Note: The default model `moodlog.ai.default-model` is already set to `qwen2.5-7b-instruct-1m` in `application.properties`, so no modifications are needed for that property.

#### 2. MoodClassifierConfiguration — wire new property

**File**: `src/main/java/com/amadeuszx/moodlog/classification/MoodClassifierConfiguration.java`

**Intent**: Read the new `moodlog.ai.response-format-type` property and pass it to the
`OpenAiMoodClassifier` constructor so the classifier can branch at call time.

**Contract**: Add a new `@Value("${moodlog.ai.response-format-type:none}") String responseFormatType`
parameter to the `moodClassifier` bean method (line 15). Pass it as the third argument to
`new OpenAiMoodClassifier(openAiChatModel, defaultModel, responseFormatType)`.

#### 3. OpenAiMoodClassifier — accept mode, branch on it

**File**: `src/main/java/com/amadeuszx/moodlog/classification/OpenAiMoodClassifier.java`

**Intent**: Accept the `responseFormatType` string via constructor and apply the appropriate
`ResponseFormat` setting (or none) when building `OpenAiChatOptions` in `classify()`.

**Contract**: Add a `private final String responseFormatType` field. Update the constructor
signature to `OpenAiMoodClassifier(OpenAiChatModel openAiChatModel, String defaultModel, String responseFormatType)`.

Replace the current `.responseFormat(...)` block in `classify()` (lines 42-45) with a branch:

- `"none"` → do not call `.responseFormat(...)` at all
- `"json_object"` → `.responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build())`
- `"json_schema"` → `.responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_SCHEMA).jsonSchema(outputConverter.getJsonSchema()).build())` (calls direct `.jsonSchema(String)` method on the `ResponseFormat.Builder`)
- Any unrecognised value → throw `IllegalArgumentException` at construction time so misconfiguration
  fails fast on startup rather than silently

#### 4. OpenAiMoodClassifierTests — update instantiations and add mode test

**File**: `src/test/java/com/amadeuszx/moodlog/classification/OpenAiMoodClassifierTests.java`

**Intent**: Update all existing test instantiations to pass `"none"` as the third constructor argument
(they currently pass two args), then add a new test that uses `ArgumentCaptor<Prompt>` to verify that
when `responseFormatType` is `"none"`, the built `OpenAiChatOptions` has no `response_format` field.

**Contract**: Every existing `new OpenAiMoodClassifier(openAiChatModel, "gpt-4o-mini")` becomes
`new OpenAiMoodClassifier(openAiChatModel, "gpt-4o-mini", "none")`.

New test outline (not a snippet — the implementer writes it):
- Name: `noneResponseFormatModeOmitsResponseFormatFromOptions`
- `openAiChatModel.call()` mock returns a valid `ChatResponse` (reuse the happy-path setup pattern)
- Capture the `Prompt` argument with `ArgumentCaptor<Prompt>`
- After `classify()`, cast `capturedPrompt.getOptions()` to `OpenAiChatOptions` and assert
  `getResponseFormat()` is `null`

No new test for `json_object` or `json_schema` mode is required in this plan; those modes are
used exclusively with hosted providers and are covered by the property documentation.

### Success Criteria

#### Automated Verification:

- `./mvnw test` passes with all existing + new tests green
- No compilation errors (constructor arity mismatch will fail fast if any instantiation is missed)

#### Manual Verification:

- With `MOODLOG_AI_RESPONSE_FORMAT_TYPE` unset (defaults to `none`): save a journal entry →
  `OpenAiMoodClassifier.java:51` fires and logs the response text → no WARN fallback log appears
- With LMStudio loaded model `qwen2.5-7b-instruct-1m` and no env override for model name:
  `moodlog.ai.default-model` resolves to `qwen2.5-7b-instruct-1m` in the startup logs

**Implementation note**: After automated verification passes, pause for manual confirmation that
classification is working end-to-end in LMStudio before marking this plan complete.

---

## Testing Strategy

### Unit Tests

- All existing exception-path and response-parsing tests still exercise the full `classify()` path
  with `none` mode (after constructor update)
- New `noneResponseFormatModeOmitsResponseFormatFromOptions` test pins the request-shape behavior

### Manual Testing Steps

1. Start the app with no `MOODLOG_AI_RESPONSE_FORMAT_TYPE` env var set
2. Log in, fill the journal form, click Save
3. Verify `OpenAiMoodClassifier` logs both the prompt (line 47) AND the response text (line 51)
4. Verify no WARN `journal.classification.failure` log appears
5. Verify the saved entry shows a real mood tag (not UNKNOWN) in the UI

## References

- Frame brief: `context/changes/lmstudio-classifier-no-response/frame.md`
- Source files: `OpenAiMoodClassifier.java:40-45`, `application.properties:24-25`,
  `MoodClassifierConfiguration.java:15-40`, `OpenAiMoodClassifierTests.java`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Configurable Response-Format Mode

#### Automated Verification:

- [x] 1.1 `./mvnw test` passes with all existing + new tests green
- [x] 1.2 No compilation errors (constructor arity mismatch will fail fast if any instantiation is missed)

#### Manual Verification:

- [x] 1.3 Line 51 fires and logs response text; no WARN fallback log with default `none` mode
- [x] 1.4 Default model name resolves to `qwen2.5-7b-instruct-1m` in startup logs
