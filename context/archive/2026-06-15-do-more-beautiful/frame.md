# Frame Brief: Add README, fix home page, apply whole-app grayscale CSS

> Framing step before /10x-plan-spring. Captures what is *actually* at issue,
> separated from what was initially assumed.

## Reported Observation

Three gaps in the current app state:
1. No `README.md` at project root — only Spring-generated `HELP.md` boilerplate.
2. `index.html` landing page shows a random-number "guest counter" with no relation to the app's purpose; the page JS fetches `/v1/random` (a live backend endpoint) on every load.
3. The entire app has zero CSS styling; all 8 Thymeleaf templates are unstyled bare HTML.

## Initial Framing (preserved)

- **User's stated cause or approach**: These are three separate cosmetic/doc gaps to close independently.
- **User's proposed direction**: (1) Write README, (2) replace home page content, (3) add grayscale CSS.
- **Pre-dispatch narrowing**: User confirmed CSS should cover the whole app, not just the landing page.

## Dimension Map

The observation could originate at any of these dimensions:

1. **Documentation surface** — No README; only `HELP.md` which is boilerplate, not user-facing.
2. **Home page content surface** — `index.html` has dead scaffolding (random-number guest counter). Backend (`RandomNumberController`, `RandomNumberService`) still live at `/v1/random` and will continue serving requests even if frontend reference is removed.
3. **CSS/template structure surface** — 8 standalone Thymeleaf templates (`index`, `login`, `register`, `journal`, `journal-edit`, `journal-history`, `journal-trends`, `error`). No shared layout fragment. No `<link>` to any stylesheet. No `static/css/` directory. Adding whole-app CSS requires either: (a) duplicate `<link>` tag in every template's `<head>`, or (b) a shared Thymeleaf `th:replace` head fragment — the structural decision the user's framing did not address. ← **key gap in framing**
4. **Dependency surface** — `pom.xml` has no Thymeleaf Layout Dialect. Any shared-layout approach must use native `th:replace` fragments (already available in standard Thymeleaf) or add the dialect dependency.

## Hypothesis Investigation

| Hypothesis | Evidence | Verdict |
|---|---|---|
| README gap is simple — no root README exists | `HELP.md` is Spring-generated boilerplate, not user README. No `README.md` found. | STRONG |
| Random-number feature is purely frontend-removable | `RandomNumberController` + `RandomNumberService` serve only `index.html`'s JS. No other reference found. Backend can be deleted entirely. | STRONG |
| Whole-app CSS needs structural mechanism | 8 templates confirmed, zero shared layout, zero `static/css/`. Whole-app CSS cannot be done by editing one file. | STRONG |
| Thymeleaf Layout Dialect available | Not in `pom.xml`. Native `th:replace` fragments are available without new dependency. | NONE (not available) |

## Narrowing Signals

- User confirmed "whole app (all pages)" scope for CSS — not just landing page.
- No shared head/layout fragment exists today in any template.
- `RandomNumberController` + `RandomNumberService` are dead code once frontend is removed — plan should delete them, not leave them.

## Cross-System Convention

Spring/Thymeleaf apps without a layout dialect typically centralize shared `<head>` markup via a `_head.html` or `fragments.html` file in `templates/` and include it via `th:replace`. This avoids adding a Maven dependency while still centralizing CSS/meta updates to one file. The existing `static/js/` folder establishes the pattern for a `static/css/` directory.

## Reframed (or Confirmed) Problem Statement

> **The actual problem to plan around is**: Three independent gaps, where the CSS task carries a hidden structural prerequisite — a shared Thymeleaf head fragment must be created before the grayscale stylesheet can be applied consistently across all 8 pages.

The original framing was directionally correct but treated "add CSS" as a simple file addition. In practice, whole-app CSS without a shared layout requires touching all 8 templates to wire the `<link>`, or first creating a head fragment and then wiring `th:replace` in all 8 templates. The plan must decide this mechanism explicitly and sequence it before styling.

The random-number removal should include full backend deletion (`RandomNumberController`, `RandomNumberService`), not just frontend JS removal.

## Confidence

**HIGH** — all three gaps confirmed by direct file inspection; structural gap in CSS approach is unambiguous; no conflicting evidence found.

## What Changes for /10x-plan-spring

The plan should treat the CSS task as two steps: (1) create `static/css/styles.css` and a shared `templates/fragments/head.html` fragment, (2) wire `th:replace` into all 8 templates. It should also scope `RandomNumberController` + `RandomNumberService` deletion as part of the home-page cleanup step — not just frontend JS removal.

## References

- Source files:
  - `src/main/resources/templates/index.html`
  - `src/main/resources/templates/login.html` (representative — 7 more similar)
  - `src/main/java/com/amadeuszx/moodlog/landing/RandomNumberController.java`
  - `src/main/java/com/amadeuszx/moodlog/landing/RandomNumberService.java`
  - `pom.xml` — no Layout Dialect dependency
  - `src/main/resources/static/` — `js/` exists, no `css/`
- Related research: none
