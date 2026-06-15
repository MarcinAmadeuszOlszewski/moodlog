# Externalize Inline Scripts + CSP — Plan Brief

> Full plan: `context/changes/externalize-inline-scripts-csp/plan.md`
> Impl review source: `context/changes/do-more-beautiful/reviews/impl-review.md` (F2)

## What & Why

Three Thymeleaf templates contain inline JavaScript that prevents adding a Content-Security-Policy header. This change externalizes all three, then activates `default-src 'self'` CSP via Spring Security — closing the latent XSS surface where the trends page relied entirely on per-value Thymeleaf escaping with no browser-level backstop.

## Starting Point

The app has no CSP header. `register.html` has a timezone-detection IIFE, `journal-history.html` has an `onsubmit` delete handler, and `journal-trends.html` has a `th:inline="javascript"` block pushing chart data into `window.journalTrendsData`. Additionally, three `style="height: 280px"` inline styles on canvas wrappers would be blocked by the implied `style-src 'self'` of a full `default-src` policy.

## Desired End State

Every HTTP response carries `Content-Security-Policy: default-src 'self'`. No template contains executable inline `<script>` blocks or inline event handlers. Chart data flows via a `<script type="application/json">` data island (not executed by the browser). A MockMvc assertion catches any regression that would remove the header.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
|---|---|---|---|
| Trends data passing mechanism | JSON data island (`<script type="application/json">`) | Same server-push timing as today, no extra HTTP round-trip, CSP-compliant since browser never executes it | Plan |
| Delete confirmation approach | `data-confirm` attribute + `site.js` event delegation | Removes inline handler; pattern generalizes to any future confirm form | Plan |
| site.js scope | Global via head fragment | Loaded once everywhere; site.js is tiny and the confirm pattern should be available on any page | Plan |
| CSP policy breadth | `default-src 'self'` | User chose full policy; safe after inline styles are also removed from canvas wrappers | Plan |
| Test coverage | Assert header in existing `ApplicationTests` | Low effort, catches regression if CSP config is accidentally removed | Plan |

## Scope

**In scope:**
- `register.html` → `register.js` (timezone detection)
- `journal-history.html` → `data-confirm` + `site.js`
- `journal-trends.html` → JSON data island + remove inline styles
- `fragments/head.html` → add `site.js` include
- `JournalController` → inject `ObjectMapper`, add `trendViewJson` model attribute
- `journal-trends.js` → read from data island instead of `window` global
- `styles.css` → add `.chart-canvas-wrapper`
- `SecurityConfiguration` → `default-src 'self'` CSP
- `ApplicationTests` → CSP header assertion

**Out of scope:**
- Nonces, hashes, or `'unsafe-inline'`
- New API endpoint for chart data
- TypeScript, bundlers, or build pipeline
- Changes to any other template
- Chart.js library or chart configuration

## Architecture / Approach

Client-side data flow before: Thymeleaf writes JS directly into the DOM via `th:inline="javascript"` → Chart.js reads `window.journalTrendsData`. After: Thymeleaf writes JSON into a `<script type="application/json" id="trends-data">` element → `journal-trends.js` reads it via `JSON.parse(document.getElementById('trends-data').textContent)`. The JSON data island is parsed by the browser as HTML text content, not executed as JavaScript, so CSP `script-src 'self'` permits it.

## Phases at a Glance

| Phase | What it delivers | Key risk |
|---|---|---|
| 1. Register timezone script | `register.js` + template updated | None — pure JS extraction |
| 2. Delete confirmation via site.js | `site.js` + head fragment + journal-history updated | If site.js loads before DOM is ready, confirm handler misses elements — mitigated by `DOMContentLoaded` listener |
| 3. Trends JSON data island | Controller serializes `JournalTrendView`; chart JS reads data island; inline styles removed | `LocalDate` fields in nested records serialized as arrays — JS ignores those fields but keep in mind if expanding JS logic later |
| 4. CSP header | `default-src 'self'` active; MockMvc asserts header | Any overlooked inline script or style breaks a page under CSP — full manual verification sweep required |

**Prerequisites:** Phases 1–3 must all complete before phase 4 — adding CSP before any inline content is removed will break affected pages.  
**Estimated effort:** ~1 session across 4 phases

## Open Risks & Assumptions

- `JournalTrendView` serialized via `ObjectMapper.writeValueAsString()` — assumes Spring Boot's auto-configured Jackson handles Java records and nested records correctly (it does as of Spring Boot 3+).
- `th:utext` used on the JSON data island script element — safe because `JournalTrendView` contains only enum-derived strings, ISO date strings, integers, and booleans (no HTML characters). Would need HTML-safe Jackson configuration if user-supplied text ever enters this model.
- No external CDN or third-party domains referenced anywhere — assumption that `default-src 'self'` won't block any asset. Verified: Chart.js is served from `/js/`, all CSS from `/css/`.

## Success Criteria (Summary)

- All pages load with zero CSP violation errors in the browser console
- `Content-Security-Policy: default-src 'self'` present in every response header
- `.\mvnw.cmd test` passes with CSP header MockMvc assertions
