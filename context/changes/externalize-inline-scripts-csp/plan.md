# Externalize Inline Scripts + CSP Implementation Plan

## Overview

Remove all inline JavaScript from Thymeleaf templates and add a `default-src 'self'` Content-Security-Policy response header. This closes the latent XSS surface identified in the do-more-beautiful implementation review (F2), where the absence of CSP meant the `th:inline="javascript"` block on the trends page relied solely on per-value escaping with no browser-level backstop.

## Current State Analysis

Three templates contain inline JavaScript that blocks a strict CSP:

- `src/main/resources/templates/register.html:29–38` — 7-line IIFE reads `Intl.DateTimeFormat().resolvedOptions().timeZone` and writes to a hidden `#timezone` field. Pure browser logic, no server data.
- `src/main/resources/templates/journal-history.html:37` — `onsubmit="return confirm('Na pewno usunąć ten wpis?')"` inline event handler on the delete form. No server data.
- `src/main/resources/templates/journal-trends.html:80–108` — `<script th:inline="javascript">` block injecting the full `JournalTrendView` chart data into `window.journalTrendsData`. `journal-trends.js` reads this global at `DOMContentLoaded`.

Additionally, `journal-trends.html:53,64,76` contains three `<div style="height: 280px;">` inline styles. These would be blocked by `style-src 'self'` (implied by `default-src 'self'`) and must be moved to a CSS class before CSP is activated.

`SecurityConfiguration.java` currently has no `.headers()` configuration — the CSP directive is entirely absent.

## Desired End State

- No `<script>` blocks in `<body>` and no inline event handlers (`on*` attributes) in any template.
- Chart data travels from controller to browser as a `<script type="application/json">` data island — parsed by Thymeleaf but never executed by the browser as JavaScript.
- A `default-src 'self'` CSP header is returned on every HTTP response, enforced by the Spring Security filter chain.
- All static assets (CSS, JS, images, fonts) are served from the same origin and remain accessible under the new policy.
- A MockMvc assertion verifies the CSP header is present on at least one protected and one public route.

### Key Discoveries

- `journal-trends.js:2` — already guards on `window.journalTrendsData` existence: `if (!window.Chart || !window.journalTrendsData) { return; }`. This check must be updated to read from the JSON data island instead.
- `JournalTrendView` is a Java `record` with nested records (`CurrentWeekSummary`, `DailyTrendSeries`, `WeeklyTrendSeries`). Spring Boot's Jackson auto-configuration serializes records correctly. The nested `DailyTrendPoint` and `WeeklyTrendPoint` records contain `LocalDate` fields — JS doesn't read the `points` list, so the serialization format doesn't affect chart rendering.
- `src/main/resources/static/js/` already contains `chart.umd.min.js` and `journal-trends.js` — follow this same directory for new files.
- `fragments/head.html` already centralizes common `<head>` content. Adding `site.js` here loads it on every page automatically.
- `<script type="application/json">` content is raw text in the HTML parser — the browser does NOT HTML-decode it before passing to `JSON.parse()`. Therefore `th:utext` (unescaped output) must be used on the element, not `th:text`. Jackson's `ObjectMapper` output for the `JournalTrendView` model contains no HTML-significant characters (numeric mood labels, ISO date strings, integers, booleans) — `th:utext` is safe here.

## What We're NOT Doing

- No nonce-based CSP — all inline scripts are removed instead.
- No TypeScript, module bundler, or build pipeline.
- No changes to Chart.js library or its chart configuration in `journal-trends.js`.
- No new API endpoint for chart data — JSON data island keeps server-push semantics.
- No `'unsafe-inline'` in the CSP policy.
- No separate chart data DTO — `JournalTrendView` is serialized directly; the JS only reads the fields it needs.
- No changes to any other templates beyond `register.html`, `journal-history.html`, `journal-trends.html`, and `fragments/head.html`.

## Implementation Approach

Four sequential phases: externalize each inline script independently (phases 1–3), then activate CSP once all inline content is gone (phase 4). Phases 1–2 are independent and could be done in either order; phase 3 is the most involved because it touches both the controller and the JS file; phase 4 is the payoff and depends on all prior phases being clean.

---

## Phase 1: Externalize Register Timezone Script

### Overview

Extract the timezone detection IIFE from `register.html` into `/js/register.js` and load it via a `<script>` tag in the template.

### Changes Required

#### 1. Create register.js

**File**: `src/main/resources/static/js/register.js`

**Intent**: Hold the timezone detection logic that was previously inlined in `register.html`. The script reads `Intl.DateTimeFormat().resolvedOptions().timeZone` and writes the result to the hidden `#timezone` field.

**Contract**: The file must contain exactly the logic from `register.html:30–37` (the IIFE body). It runs synchronously on page load — no `DOMContentLoaded` wrapper needed since it is loaded at the bottom of the form (after the input exists in DOM), or it may be wrapped in `DOMContentLoaded` for safety.

#### 2. Update register.html

**File**: `src/main/resources/templates/register.html`

**Intent**: Replace the inline `<script>` block at lines 29–38 with an external script reference.

**Contract**: Remove the inline `<script>...</script>` block. Add `<script th:src="@{/js/register.js}"></script>` immediately after the hidden timezone `<input>` element (same position, so the script runs after the element exists in DOM, or wrap the IIFE body in `DOMContentLoaded` if the script is loaded with `defer`).

### Success Criteria

#### Automated Verification

- `.\mvnw.cmd test` passes
- `src/main/resources/static/js/register.js` exists and is non-empty
- `register.html` contains no `<script>` block with inline timezone logic

#### Manual Verification

- Register a new account at `/register` — the hidden timezone field is populated with the browser's timezone before form submission
- User's subsequent journal entries display dates in the correct local timezone

---

## Phase 2: Externalize Delete Confirmation via site.js

### Overview

Create a global `site.js` loaded via the head fragment. It attaches submit event listeners to any form with a `data-confirm` attribute. Replace the `onsubmit` inline handler in `journal-history.html` with `data-confirm`.

### Changes Required

#### 1. Create site.js

**File**: `src/main/resources/static/js/site.js`

**Intent**: Provide reusable page-level behavior loaded on every page. For now, this covers the delete confirmation pattern — forms with a `data-confirm` attribute prompt the user before submitting.

**Contract**: On `DOMContentLoaded`, query all `form[data-confirm]` elements and attach a `submit` handler that calls `confirm(form.getAttribute('data-confirm'))` and calls `event.preventDefault()` if the user cancels. No other behavior should be in this file at this point.

#### 2. Update fragments/head.html

**File**: `src/main/resources/templates/fragments/head.html`

**Intent**: Load `site.js` on every page by including it in the shared head fragment, immediately after the stylesheet link.

**Contract**: Add `<script th:src="@{/js/site.js}" defer></script>` as the last element inside the `<th:block th:fragment="common-head">`. The `defer` attribute ensures it runs after DOM is parsed.

#### 3. Update journal-history.html

**File**: `src/main/resources/templates/journal-history.html`

**Intent**: Replace the `onsubmit` inline event handler with a `data-confirm` attribute that `site.js` will pick up.

**Contract**: On the delete form at line 37, replace `onsubmit="return confirm('Na pewno usunąć ten wpis?')"` with `data-confirm="Na pewno usunąć ten wpis?"`. The form element, action, method, and hidden inputs are otherwise unchanged.

#### 4. Update SecurityConfiguration — permit site.js

**File**: `src/main/java/com/amadeuszx/moodlog/user/SecurityConfiguration.java`

**Intent**: `site.js` is a static asset served under `/js/**`, which is already in the `permitAll()` list. No change is needed — verify this is the case before marking done.

**Contract**: Confirm `/js/**` remains in `requestMatchers(...).permitAll()`. No edit required.

### Success Criteria

#### Automated Verification

- `.\mvnw.cmd test` passes
- `src/main/resources/static/js/site.js` exists and is non-empty
- `journal-history.html` contains no `onsubmit` attribute
- `fragments/head.html` contains `site.js` script reference

#### Manual Verification

- Visit `/journal/history` (authenticated), click Usuń — a Polish confirmation dialog appears before the entry is deleted
- Cancelling the dialog does not submit the form
- All other pages load without JS errors (site.js loads but finds no `form[data-confirm]` and does nothing)

---

## Phase 3: Externalize Trends Inline Data (JSON Data Island)

### Overview

Replace the `th:inline="javascript"` block in `journal-trends.html` with a `<script type="application/json">` data island. Update `journal-trends.js` to read from the data island instead of `window.journalTrendsData`. Move the three inline `style="height: 280px"` attributes to a CSS class.

### Changes Required

#### 1. Update JournalController

**File**: `src/main/java/com/amadeuszx/moodlog/journal/JournalController.java`

**Intent**: Inject `ObjectMapper` and serialize the `JournalTrendView` to a JSON string, adding it as a `trendViewJson` model attribute alongside the existing `trendView`.

**Contract**: Add `com.fasterxml.jackson.databind.ObjectMapper` as a constructor-injected dependency. In `trendsPage()`, after building `trendView`, call `objectMapper.writeValueAsString(trendView)` and add the result as `model.addAttribute("trendViewJson", ...)`. Wrap in a try-catch for `JsonProcessingException` — on failure, add an empty JSON object `"{}"` and log a warning. The existing `trendView` model attribute must remain for the Thymeleaf template to continue rendering the summary section and `th:if` blocks that reference it.

#### 2. Update journal-trends.html

**File**: `src/main/resources/templates/journal-trends.html`

**Intent**: Remove the `th:inline="javascript"` block and replace it with a `<script type="application/json">` data island. Move the three inline `style="height: 280px"` canvas wrapper divs to a CSS class.

**Contract**:
- Remove lines 80–108 (the `<script th:inline="javascript">...</script>` block).
- Add `<script type="application/json" id="trends-data" th:utext="${trendViewJson}"></script>` in `<head>` after the two existing `<script th:src>` tags. Use `th:utext` (not `th:text`) — `<script>` content is raw text in the HTML parser and must not be HTML-entity-encoded.
- Replace all three `<div style="height: 280px;">` occurrences with `<div class="chart-canvas-wrapper">`.

#### 3. Update styles.css

**File**: `src/main/resources/static/css/styles.css`

**Intent**: Define the `.chart-canvas-wrapper` class to replace the inline `style="height: 280px"` on the three canvas container divs.

**Contract**: Add `.chart-canvas-wrapper { height: 280px; }` in the trends section of the stylesheet (near the existing `/* Trends */` rules).

#### 4. Update journal-trends.js

**File**: `src/main/resources/static/js/journal-trends.js`

**Intent**: Replace the `window.journalTrendsData` global dependency with a local read from the JSON data island element.

**Contract**: Replace the guard `if (!window.Chart || !window.journalTrendsData) { return; }` at line 2 with:
```javascript
const dataEl = document.getElementById('trends-data');
if (!window.Chart || !dataEl) { return; }
const journalTrendsData = JSON.parse(dataEl.textContent);
```
Replace all subsequent references to `window.journalTrendsData` with `journalTrendsData`. The rest of the chart logic is unchanged.

### Success Criteria

#### Automated Verification

- `.\mvnw.cmd test` passes
- `journal-trends.html` contains no `th:inline="javascript"` and no `style="height: 280px"`
- `journal-trends.html` contains `<script type="application/json" id="trends-data"`
- `styles.css` contains `.chart-canvas-wrapper`

#### Manual Verification

- Visit `/journal/trends` (authenticated, with at least a few journal entries) — all three charts render correctly (7-day, 30-day, weekly)
- The page has no JS console errors
- The `#trends-data` element is visible in browser DevTools with valid JSON content
- The chart canvas wrappers are 280px tall (verify via DevTools computed styles)

---

## Phase 4: Add Content-Security-Policy Header

### Overview

Configure Spring Security to return `Content-Security-Policy: default-src 'self'` on every HTTP response and add a MockMvc assertion verifying the header.

### Changes Required

#### 1. Update SecurityConfiguration

**File**: `src/main/java/com/amadeuszx/moodlog/user/SecurityConfiguration.java`

**Intent**: Add a CSP response header to the security filter chain now that all inline scripts and inline styles have been removed.

**Contract**: In `securityFilterChain()`, before `.build()`, add:
```java
.headers(headers -> headers
    .contentSecurityPolicy(csp -> csp
        .policyDirectives("default-src 'self'")
    )
)
```
The policy `default-src 'self'` covers `script-src`, `style-src`, `img-src`, `connect-src`, and `font-src` — all assets in this app are same-origin after the prior phases.

#### 2. Add CSP assertion to ApplicationTests

**File**: `src/test/java/com/amadeuszx/moodlog/ApplicationTests.java`

**Intent**: Verify the CSP header is present on public and protected routes, so a future accidental removal of the security headers configuration is caught automatically.

**Contract**: In `indexPageContainsAppDescriptionAndNoRandomEndpoint`, add:
```java
.andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
```
Import `org.springframework.test.web.servlet.result.MockMvcResultMatchers.header`. Add a second assertion in `journalPageRequiresAuthentication` (or a new dedicated test) verifying the CSP header is also present on a redirect response from a protected route.

### Success Criteria

#### Automated Verification

- `.\mvnw.cmd test` passes with CSP header assertions
- `SecurityConfiguration` contains `contentSecurityPolicy` configuration
- CSP header assertion is present in at least one test in `ApplicationTests`

#### Manual Verification

- Open browser DevTools → Network tab → any page response → Response Headers shows `Content-Security-Policy: default-src 'self'`
- All pages load without CSP violation errors in the browser console (`F12 → Console`)
- Specifically verify: `/`, `/login`, `/register`, `/journal`, `/journal/history`, `/journal/trends`, `/error`
- The chart on `/journal/trends` still renders — Chart.js loaded from `/js/chart.umd.min.js` is same-origin and allowed

---

## Testing Strategy

### Automated Tests

- `.\mvnw.cmd test` after each phase — catches compile errors from controller change (phase 3) and broken Thymeleaf expressions
- CSP header assertion added in phase 4 to `ApplicationTests`

### Manual Testing Steps

1. Start app: `.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local`
2. `/register` — verify timezone field is populated before submit (check via DevTools → Form Data on submission, or `document.getElementById('timezone').value` in console)
3. `/journal/history` — click Usuń on any entry, verify Polish confirm dialog appears; cancel → no deletion; confirm → entry deleted
4. `/journal/trends` — all 3 charts render; no console errors; `F12 → Application → #trends-data` shows valid JSON
5. All pages: `F12 → Console` — zero CSP violation errors after phase 4
6. All pages: `F12 → Network → Response Headers` — `Content-Security-Policy: default-src 'self'` present

## References

- Impl review finding: `context/changes/do-more-beautiful/reviews/impl-review.md` (F2)
- `src/main/java/com/amadeuszx/moodlog/journal/JournalController.java:140–148` (trendsPage method)
- `src/main/java/com/amadeuszx/moodlog/journal/trend/JournalTrendView.java` (record structure)
- `src/main/resources/static/js/journal-trends.js` (consumes window.journalTrendsData)
- `src/main/resources/templates/fragments/head.html` (shared head — receives site.js)
- `src/main/java/com/amadeuszx/moodlog/user/SecurityConfiguration.java` (receives CSP headers config)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Externalize Register Timezone Script

#### Automated

- [x] 1.1 `.\mvnw.cmd test` passes — f5a4091
- [x] 1.2 `src/main/resources/static/js/register.js` exists and is non-empty — f5a4091
- [x] 1.3 `register.html` contains no inline `<script>` block with timezone logic — f5a4091

#### Manual

- [x] 1.4 Timezone field populated correctly at `/register` — f5a4091
- [x] 1.5 Journal entry dates display in correct local timezone after registration — f5a4091

### Phase 2: Externalize Delete Confirmation via site.js

#### Automated

- [x] 2.1 `.\mvnw.cmd test` passes
- [x] 2.2 `src/main/resources/static/js/site.js` exists and is non-empty
- [x] 2.3 `journal-history.html` contains no `onsubmit` attribute
- [x] 2.4 `fragments/head.html` contains `site.js` script reference

#### Manual

- [x] 2.5 Delete confirmation dialog appears in Polish on `/journal/history`
- [x] 2.6 Cancelling dialog does not delete the entry
- [x] 2.7 Other pages load without JS errors

### Phase 3: Externalize Trends Inline Data (JSON Data Island)

#### Automated

- [ ] 3.1 `.\mvnw.cmd test` passes
- [ ] 3.2 `journal-trends.html` contains no `th:inline="javascript"` and no `style="height: 280px"`
- [ ] 3.3 `journal-trends.html` contains `<script type="application/json" id="trends-data"`
- [ ] 3.4 `styles.css` contains `.chart-canvas-wrapper`

#### Manual

- [ ] 3.5 All three charts render correctly on `/journal/trends`
- [ ] 3.6 No JS console errors on `/journal/trends`
- [ ] 3.7 `#trends-data` element contains valid JSON in DevTools
- [ ] 3.8 Canvas wrappers are 280px tall (DevTools computed styles)

### Phase 4: Add Content-Security-Policy Header

#### Automated

- [ ] 4.1 `.\mvnw.cmd test` passes with CSP header assertions
- [ ] 4.2 `SecurityConfiguration` contains `contentSecurityPolicy` configuration
- [ ] 4.3 CSP header assertion present in `ApplicationTests`

#### Manual

- [ ] 4.4 `Content-Security-Policy: default-src 'self'` visible in response headers on all pages
- [ ] 4.5 Zero CSP violation errors in browser console across all pages
- [ ] 4.6 Chart on `/journal/trends` still renders under CSP
