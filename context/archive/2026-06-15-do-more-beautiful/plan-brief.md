# do-more-beautiful — Plan Brief

> Full plan: `context/changes/do-more-beautiful/plan.md`
> Frame brief: `context/changes/do-more-beautiful/frame.md`

## What & Why

The app has no README, a landing page showing leftover scaffolding (random-number guest counter), and zero CSS across all 8 pages. This change removes the dead code, writes project documentation, and applies a full grayscale UI overhaul — making the app look like a finished product rather than a scaffold.

## Starting Point

8 standalone Thymeleaf templates (all Polish, no shared layout), no `static/css/` directory, a live `/v1/random` backend endpoint that serves only the guest counter JS, and no project README.

## Desired End State

Every page in the app is visually styled in a consistent grayscale palette — buttons, forms, inputs, nav, lists, layout. The landing page describes what MoodLog does in Polish and links to login/register. An English README at project root documents the app and run instructions. No dead backend code remains.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
|---|---|---|---|
| CSS scope | Whole app (all 8 pages) | User confirmed during framing | Frame |
| Shared head mechanism | Thymeleaf `th:replace` fragment (no new dependency) | Layout Dialect not in pom.xml; native fragments achieve the same result | Frame |
| Backend deletion | Delete RandomNumberController + RandomNumberService | No other callers; dead code once frontend JS is removed | Frame |
| CSS depth | Full UI polish (colors + layout + forms + buttons + nav) | User chose full overhaul, not just palette | Plan |
| README language | English | Standard for repos regardless of app UI language | Plan |
| Per-page `<title>` | Kept in each template | Fragment centralizes common elements only; titles vary per page | Plan |

## Scope

**In scope:**
- `README.md` at project root (English)
- Rewrite `index.html` content — Polish description of MoodLog
- Delete `RandomNumberController.java` + `RandomNumberService.java`
- Remove `/v1/random` from `SecurityConfiguration` permitAll list
- Create `static/css/styles.css` — full grayscale UI polish
- Create `templates/fragments/head.html` — shared head fragment
- Wire `th:replace` into all 8 templates

**Out of scope:**
- Thymeleaf Layout Dialect
- Shared header/footer/navigation partial (only `<head>` is shared)
- Dark mode / theming system
- Chart.js or JS changes
- Polish-language README

## Architecture / Approach

A shared Thymeleaf fragment (`templates/fragments/head.html`) holds charset, viewport, favicon, and CSS link. Each template's `<head>` replaces those repeated lines with a single `th:replace`, keeping its own `<title>`. The stylesheet lives at `static/css/styles.css` (publicly accessible via the existing `/css/**` permitAll rule in SecurityConfiguration). CSS uses custom properties for a grayscale color palette applied consistently across typography, forms, buttons, nav, and layout.

## Phases at a Glance

| Phase | What it delivers | Key risk |
|---|---|---|
| 1. Write README | Project documentation at root | Env var names must match application.properties exactly |
| 2. Home page cleanup | Dead backend deleted, index.html describes the app | Missing `/v1/random` removal from SecurityConfiguration leaves dead permit rule |
| 3. CSS + head fragment | All 8 pages styled, shared head wired | Chart.js rendering on journal-trends must not be broken by CSS |

**Prerequisites:** Java 21 installed, Maven wrapper in repo (already present)
**Estimated effort:** ~1-2 sessions across 3 phases

## Open Risks & Assumptions

- `journal-trends.html` embeds Chart.js — global CSS resets (e.g. `box-sizing`, `*` selectors) could affect canvas rendering; keep chart container styles minimal
- `register.html` has an inline `<script>` for timezone detection — the head fragment must not interfere with inline scripts in `<body>`

## Success Criteria (Summary)

- Every page loads with consistent grayscale styling visible in browser
- `/v1/random` returns 404; no browser network call to it from the home page
- `README.md` exists at root and run instructions actually work locally
