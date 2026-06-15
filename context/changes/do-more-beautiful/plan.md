# do-more-beautiful Implementation Plan

## Overview

Remove dead scaffolding from the landing page, write a project README, and apply a full grayscale UI overhaul across all 8 Thymeleaf templates via a shared head fragment and stylesheet. The app currently has zero CSS and a random-number guest counter left over from scaffolding.

## Current State Analysis

- No `README.md` at project root — only Spring-generated `HELP.md` boilerplate.
- `index.html` renders a "guest counter" that fetches `/v1/random` via JS — unrelated to the app's purpose.
- `RandomNumberController` and `RandomNumberService` serve only that JS call; no other callers exist.
- `SecurityConfiguration.java:47` — `/v1/random` is explicitly `permitAll()`; must be removed when the controller is deleted.
- `SecurityConfiguration.java:47` — `/css/**` already `permitAll()` — no security change needed for the stylesheet.
- 8 standalone Thymeleaf templates, all in Polish, no shared layout or fragment. No `static/css/` directory.
- `static/js/` exists and establishes the pattern for `static/css/`.

## Desired End State

- `README.md` at project root describes what MoodLog is, the tech stack, and how to run locally and in production.
- `index.html` shows a clear Polish-language description of what MoodLog does and how to use it, with links to login/register.
- All 8 pages share a consistent grayscale visual identity via a single `static/css/styles.css`. Buttons, forms, inputs, nav, lists, typography, and layout are all styled.
- No backend code serves `/v1/random`.

### Key Discoveries

- `SecurityConfiguration.java:39-53` — both `/v1/random` (remove) and `/css/**` (already present) are in the `permitAll` list. One needs removing, one is already handled.
- `src/main/resources/templates/` — all 8 templates have identical `<head>` structure: charset, viewport, title, favicon link. Safe to extract to a shared fragment.
- `src/main/resources/static/js/` — convention for static assets is `static/<type>/`. Follow with `static/css/`.
- All templates use `lang="pl"` and Polish text — content on `index.html` should remain Polish.

## What We're NOT Doing

- No Thymeleaf Layout Dialect dependency — use native `th:replace` fragments.
- No per-page `<title>` centralization — each template keeps its own `<title>` tag.
- No layout restructuring (no header/footer/sidebar partial) — only the `<head>` element is shared.
- No Chart.js or JavaScript changes — `journal-trends.html` JS behavior is untouched.
- No dark-mode toggle or theming system — single static grayscale stylesheet.
- No new routes, controllers, or services.

## Implementation Approach

Three sequential phases: documentation first (standalone, no dependencies), then backend cleanup and home page rewrite, then CSS infrastructure and template wiring. Phases 2 and 3 can technically be done in either order but sequencing cleanup before CSS means the head fragment work in Phase 3 only needs to happen once per template (including the already-modified `index.html`).

## Phase 1: Write README.md

### Overview

Create an English-language `README.md` at the project root covering what MoodLog is, the tech stack, and how to run it.

### Changes Required

#### 1. README.md

**File**: `README.md`

**Intent**: Give any developer landing on the repo an accurate description of the project and actionable run instructions — replacing the Spring-generated `HELP.md` boilerplate which serves neither purpose.

**Contract**: The README must cover these sections in order:
- Project title and 2–3 sentence description (what it is, who it's for, core capability: free-text journaling + automatic mood classification + trend visualization)
- Tech stack list (Spring Boot 4, Thymeleaf, Spring Security, Spring AI / OpenAI, PostgreSQL + Flyway, H2 for local dev)
- Prerequisites (Java 21, Maven wrapper included)
- Local run instructions using the `local` Spring profile (H2, AI disabled by default): `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`
- Environment variable reference for production: `MOODLOG_DATABASE_URL`, `MOODLOG_DATABASE_USERNAME`, `MOODLOG_DATABASE_PASSWORD`, `OPENAI_API_KEY`, and the optional overrides in `application.properties`
- Brief note that AI classification defaults to disabled in `local` profile (`moodlog.ai.enabled=false`)

### Success Criteria

#### Automated Verification

- `README.md` exists at project root
- `./mvnw test` still passes (no code changed)

#### Manual Verification

- README renders correctly on GitHub (headings, code blocks, list formatting)
- Run command (`./mvnw spring-boot:run -Dspring-boot.run.profiles=local`) actually starts the app successfully
- All environment variable names match those in `src/main/resources/application.properties`

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 2: Home Page Cleanup

### Overview

Remove the random-number guest counter feature entirely (frontend and backend), update `index.html` to describe the app, and remove `/v1/random` from the Spring Security permit list.

### Changes Required

#### 1. Delete RandomNumberController

**File**: `src/main/java/com/amadeuszx/moodlog/landing/RandomNumberController.java`

**Intent**: Delete the file. The `/v1/random` endpoint exists only to serve the guest counter JS in `index.html`, which is being removed.

**Contract**: File deleted. No replacement.

#### 2. Delete RandomNumberService

**File**: `src/main/java/com/amadeuszx/moodlog/landing/RandomNumberService.java`

**Intent**: Delete the file. Only `RandomNumberController` called this service.

**Contract**: File deleted. No replacement.

#### 3. Remove /v1/random from SecurityConfiguration

**File**: `src/main/java/com/amadeuszx/moodlog/user/SecurityConfiguration.java`

**Intent**: Remove the `/v1/random` entry from the `permitAll()` list now that the route no longer exists. Leaving a dead permit rule is misleading.

**Contract**: Remove the `"/v1/random",` line from the `requestMatchers(...)` block at line 47. All other entries remain unchanged.

#### 4. Rewrite index.html content

**File**: `src/main/resources/templates/index.html`

**Intent**: Replace the guest-counter paragraph and its `<script>` block with a Polish-language description of what MoodLog is and how to use it, keeping the existing login/register links.

**Contract**: The new content (inside `<main>`) must include:
- An `<h1>` with the app name
- 2–3 `<p>` elements in Polish describing: what MoodLog is (private free-text journal with automatic mood classification), how it works (write an entry → AI assigns a mood tag → view trends over time), and who it's for
- The existing `<a th:href="@{/login}">` and `<a th:href="@{/register}">` links retained
- The `<script>` block and `<span id="guest-number">` removed entirely
- The `<head>` block left intact at this phase — it will be replaced in Phase 3

#### 5. Update ApplicationTests

**File**: `src/test/java/com/amadeuszx/moodlog/ApplicationTests.java`

**Intent**: Remove or update tests that assert the old `/v1/random` endpoint and landing page welcome message to prevent compilation/runtime test failures.

**Contract**:
- Delete the `randomEndpointReturnsRandomNumber` test.
- Rewrite the `indexPageContainsWelcomeMessageAndUsesRandomEndpoint` test to `indexPageContainsAppDescriptionAndNoRandomEndpoint`. Assert the landing page `/` returns HTTP 200, uses view name `"index"`, contains Polish descriptions of MoodLog (e.g. "prywatnego dziennika" / "Zaloguj się"), and contains no reference to "Witaj! Jesteś dziś" or `/v1/random`.

### Success Criteria

#### Automated Verification

- `./mvnw test` passes (no compile errors from deleted classes)
- No remaining references to `RandomNumberController`, `RandomNumberService`, or `/v1/random` in the codebase

#### Manual Verification

- `http://localhost:8080/` shows the app description, no "guest" text, no network request to `/v1/random` in browser devtools
- `/v1/random` returns 404 (route is gone)
- Login and register links on the home page still work

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 3: Shared Head Fragment + Full Grayscale CSS

### Overview

Create a shared Thymeleaf head fragment and a full-polish grayscale stylesheet, then wire the fragment into all 8 templates. The CSS should make the app look intentionally designed — not just colored.

### Changes Required

#### 1. Create grayscale stylesheet

**File**: `src/main/resources/static/css/styles.css`

**Intent**: Define a complete grayscale visual identity for the app. Mood is the subject — the palette should feel calm, neutral, and slightly introspective. Full UI polish means every visible element (typography, forms, buttons, nav, lists, layout) is styled.

**Contract**: The stylesheet must cover at minimum:
- **Color palette**: use CSS custom properties (`--color-*`) for background, surface, border, text-primary, text-secondary, text-muted, and accent. All values must be grayscale (no hue). Recommended: near-black text on near-white background, mid-gray borders, light gray card surfaces.
- **Typography**: system font stack, readable base size (16px+), comfortable line-height, appropriate heading hierarchy
- **Layout**: centered content column with `max-width` (~640–720px), consistent vertical padding
- **Buttons**: styled `<button>` elements — solid dark fill, appropriate padding, hover state
- **Form inputs and textareas**: styled borders, focus ring, padding — consistent across all form pages
- **Labels**: styled for readability, appropriate spacing from inputs
- **Nav** (`<nav>` element): horizontal link row, subtle separator or spacing
- **Lists** (`<ul>/<li>`): styled for journal entry display (no default bullets, readable row layout)
- **Error/validation messages** (`<p>` inside form groups after inputs): distinguishable from normal text (slightly muted or indented)
- **Links** (`<a>`): styled with grayscale, underline or hover state
- **Page-level**: no horizontal scroll, consistent body background

#### 2. Create shared head fragment

**File**: `src/main/resources/templates/fragments/head.html`

**Intent**: Centralize the common `<head>` elements — charset, viewport, favicon, CSS link — so all 8 templates share them via a single `th:replace` inclusion.

**Contract**: Fragment named `common-head` using `th:fragment="common-head"` on a `<th:block>`. Contents:
```html
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<link rel="icon" type="image/svg+xml" th:href="@{/favicon.svg}">
<link rel="stylesheet" th:href="@{/css/styles.css}">
```
The fragment file must be valid standalone HTML (wrap in `<!DOCTYPE html><html xmlns:th="..."><head>...</head><body></body></html>`). Per-page `<title>` is NOT included in the fragment — each template keeps its own.

#### 3. Wire fragment into all 8 templates

**Files**:
- `src/main/resources/templates/index.html`
- `src/main/resources/templates/login.html`
- `src/main/resources/templates/register.html`
- `src/main/resources/templates/journal.html`
- `src/main/resources/templates/journal-edit.html`
- `src/main/resources/templates/journal-history.html`
- `src/main/resources/templates/journal-trends.html`
- `src/main/resources/templates/error.html`

**Intent**: Replace the repeated `<meta charset>`, `<meta viewport>`, and `<link rel="icon">` lines in every template's `<head>` with a single `th:replace` inclusion of the shared fragment, then add the CSS link through the fragment.

**Contract**: In each template, inside `<head>`, replace the existing charset/viewport/favicon lines with:
```html
<th:block th:replace="~{fragments/head :: common-head}"></th:block>
```
The per-page `<title>` tag remains in each template immediately after the `th:block`. The `xmlns:th` namespace declaration stays on the `<html>` element. 

Result: each template's `<head>` has the `th:block` + `<title>`. For templates with custom page-specific scripts (such as the Chart.js and logic scripts on `journal-trends.html`), those `<script>` tags must be retained within the `<head>` after the `th:block`.

### Success Criteria

#### Automated Verification

- `./mvnw test` passes
- All 8 template files contain `th:replace="~{fragments/head :: common-head}"`
- `src/main/resources/static/css/styles.css` exists and is non-empty
- `src/main/resources/templates/fragments/head.html` exists

#### Manual Verification

- Every page loads with CSS applied: `/`, `/login`, `/register`, `/journal`, `/journal/history`, `/journal/trends`, `/journal/edit/{id}`, `/error` (visit with invalid URL to trigger it)
- Grayscale palette is consistent across all pages — no page looks unstyled
- Forms on login, register, journal entry, and journal edit pages are visually polished (styled inputs, styled submit buttons)
- Journal entry list on `/journal` and `/journal/history` is readable and styled
- Chart on `/journal/trends` still renders correctly (Chart.js JS unchanged)
- No horizontal scroll on any page at standard desktop width
- Favicon loads on all pages

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Testing Strategy

### Automated Tests

- Compile: `./mvnw test` — catches deleted-class references and Thymeleaf template parse errors
- Existing tests (`SecurityRouteProtectionTests`, `JournalFlowTests`, `AuthenticationFlowTests`) cover route behavior — they must continue passing after `RandomNumberController` deletion and `SecurityConfiguration` update

### Manual Testing Steps

1. Start app: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`
2. Visit `/` — verify description content, no guest counter, login/register links visible and styled
3. Visit `/login` — verify form is styled
4. Register a new account at `/register` — verify styled form, timezone field hidden, success redirects to journal
5. Write a journal entry at `/journal` — verify textarea and submit button styled
6. View `/journal/history` — verify entries styled in list
7. View `/journal/trends` — verify chart renders, page styled around it
8. Open browser devtools Network tab on `/` — confirm no request to `/v1/random`
9. Directly visit `/v1/random` — confirm 404
10. Confirm README renders correctly (preview locally or push to GitHub)

## References

- Frame brief: `context/changes/do-more-beautiful/frame.md`
- `src/main/java/com/amadeuszx/moodlog/user/SecurityConfiguration.java:39-53`
- `src/main/java/com/amadeuszx/moodlog/landing/RandomNumberController.java`
- `src/main/java/com/amadeuszx/moodlog/landing/RandomNumberService.java`
- `src/main/resources/templates/index.html`
- `src/main/resources/application-local.properties` (local profile — H2, AI disabled)
- `src/main/resources/application.properties` (env var reference for README)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Write README.md

#### Automated

- [x] 1.1 README.md exists at project root — eead80a
- [x] 1.2 `./mvnw test` passes — eead80a

#### Manual

- [x] 1.3 README renders correctly on GitHub — eead80a
- [x] 1.4 Run command starts the app successfully — eead80a
- [x] 1.5 All env var names match application.properties — eead80a

### Phase 2: Home Page Cleanup

#### Automated

- [x] 2.1 `./mvnw test` passes (no compile errors from deleted classes) — 011c614
- [x] 2.2 No remaining references to RandomNumberController, RandomNumberService, or /v1/random — 011c614

#### Manual

- [x] 2.3 Home page shows app description, no guest text — 011c614
- [x] 2.4 No network request to /v1/random in browser devtools — 011c614
- [x] 2.5 /v1/random returns 404 — 011c614
- [x] 2.6 Login and register links still work — 011c614

### Phase 3: Shared Head Fragment + Full Grayscale CSS

#### Automated

- [x] 3.1 `./mvnw test` passes — 66e35e9
- [x] 3.2 All 8 templates contain th:replace for common-head fragment — 66e35e9
- [x] 3.3 static/css/styles.css exists and is non-empty — 66e35e9
- [x] 3.4 templates/fragments/head.html exists — 66e35e9

#### Manual

- [x] 3.5 Every page loads with CSS applied — 66e35e9
- [x] 3.6 Grayscale palette consistent across all pages — 66e35e9
- [x] 3.7 Forms styled on login, register, journal, journal-edit — 66e35e9
- [x] 3.8 Journal entry list styled on /journal and /journal/history — 66e35e9
- [x] 3.9 Chart on /journal/trends still renders — 66e35e9
- [x] 3.10 No horizontal scroll at standard desktop width — 66e35e9
- [x] 3.11 Favicon loads on all pages — 66e35e9
