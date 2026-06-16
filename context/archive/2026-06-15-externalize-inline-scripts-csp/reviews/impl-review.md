<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Externalize Inline Scripts + CSP

- **Plan**: context/changes/externalize-inline-scripts-csp/plan.md
- **Scope**: All Phases (1–4 of 4)
- **Date**: 2026-06-15
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 5 warnings, 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | FAIL |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | WARNING |
| Pattern Consistency | WARNING |
| Success Criteria | WARNING |

## Findings

### F1 — JournalController ObjectMapper change MISSING; data island uses th:inline workaround

- **Severity**: ⚠️ WARNING
- **Impact**: 🔬 HIGH — architectural stakes; think carefully before deciding
- **Dimension**: Plan Adherence / Architecture
- **Location**: src/main/java/com/amadeuszx/moodlog/journal/JournalController.java, src/main/resources/templates/journal-trends.html:8
- **Detail**: Phase 3 required ObjectMapper injection in JournalController and trendViewJson model attribute; neither was implemented. Template workaround uses th:inline="javascript" on the <script type="application/json"> element instead of th:utext="${trendViewJson}". th:inline="javascript" remains present on the data island, which the plan explicitly called for removing. Workaround appears functional for current data types (Thymeleaf 3.x uses double-quoted strings) but is a semantic mismatch.
- **Fix A ⭐ Recommended**: Inject ObjectMapper in JournalController constructor, serialize trendView in trendsPage(), add trendViewJson to model; switch journal-trends.html:8 to th:utext="${trendViewJson}" and remove th:inline="javascript".
  - Strength: Matches plan intent; removes last th:inline="javascript" instance; makes data island independent of Thymeleaf JS-escaping nuances.
  - Tradeoff: Requires controller change + test run.
  - Confidence: HIGH — exactly what the plan specified.
  - Blind spot: Verify Jackson serializes LocalDate fields in JournalTrendView nested records correctly.
- **Fix B**: Accept the th:inline workaround; add a comment explaining why th:inline="javascript" is intentional on a JSON block.
  - Strength: Zero controller change; tests pass today.
  - Tradeoff: th:inline stays; plan's "no th:inline" goal not achieved; future string types could introduce escaping edge cases.
  - Confidence: MED — current data types are safe; risk is latent.
  - Blind spot: Behavior on unusual string values untested in JSON consumer context.
- **Decision**: FIXED via Fix A (ObjectMapper injection in JournalController; th:utext on data island; journal-trends.js updated to use completedSevenDayTrend/completedThirtyDayTrend/completedWeeklyTrend and chartLabels/chartValues; JournalTrendsFlowTests updated to match Jackson field names)

### F2 — CSS data: URI for dropdown SVG arrow blocked by CSP

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: src/main/resources/static/css/styles.css:168, src/main/java/com/amadeuszx/moodlog/user/SecurityConfiguration.java:71
- **Detail**: styles.css:168 uses background-image: url("data:image/svg+xml,...") for the <select> dropdown arrow. Under default-src 'self', img-src falls back to 'self' which does NOT include data: URIs. Browsers block this and the dropdown arrow disappears on all pages with a <select> element.
- **Fix**: Add `img-src 'self' data:` to the CSP policy: `.policyDirectives("default-src 'self'; img-src 'self' data:")`
- **Decision**: FIXED

### F3 — form[onsubmit] CSS selector orphaned; delete button loses secondary styling

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: src/main/resources/static/css/styles.css:222, 230
- **Detail**: Phase 2 replaced onsubmit attribute with data-confirm, but styles.css:222 and :230 still use form[onsubmit] button selectors. No form has onsubmit anymore — the delete button now renders with primary button weight, which is a visual regression.
- **Fix**: Replace `form[onsubmit]` with `form[data-confirm]` at lines 222 and 230 in styles.css.
- **Decision**: FIXED

### F4 — Inline style attribute on logout button blocked by CSP

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: src/main/resources/templates/journal-trends.html:41 (same pattern in journal.html:10, journal-history.html:10, journal-edit.html:10)
- **Detail**: `<button form="logout-form" type="submit" style="font-size:small">` — default-src 'self' blocks inline style= attributes; browsers emit a CSP violation error in the console. Same logout button pattern repeated across 4 templates.
- **Fix A ⭐ Recommended**: Added `.btn-small { font-size: small; }` to styles.css; replaced `style="font-size:small"` with `class="btn-small"` in all 4 templates.
- **Decision**: FIXED via Fix A (all 4 templates updated; .btn-small added to styles.css)

### F5 — CSP header not asserted on /journal/history and /journal/trends routes

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: src/test/java/com/amadeuszx/moodlog/ApplicationTests.java:108–119
- **Detail**: CSP assertion covers GET / (public) and /journal redirect. The redirect assertions for /journal/history and /journal/trends (lines 108–119) do not assert the CSP header — a future removal of headers config on those routes would go undetected.
- **Fix**: Add `.andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))` to `journalHistoryPageRequiresAuthentication` and `journalTrendsPageRequiresAuthentication`.
- **Decision**: FIXED

### F6 — script tag placed inside form without defer

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: src/main/resources/templates/register.html:29
- **Detail**: `<script th:src="@{/js/register.js}">` sits inside the <form> between the hidden timezone input and the submit button, without defer. Script uses DOMContentLoaded so it works correctly. But head.html convention uses defer for site.js; placing a non-deferred script inside a form is non-standard.
- **Fix**: Add `defer` to the script tag and move it outside the `<form>` (just before `</body>`).
- **Decision**: FIXED

### F7 — register.js uses DOMContentLoaded listener instead of planned IIFE

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: src/main/resources/static/js/register.js
- **Detail**: Plan specified an IIFE; implementation uses DOMContentLoaded with an 'Europe/Warsaw' fallback added. Functionally equivalent and arguably safer. Benign drift.
- **Fix**: No fix required — accept the DOMContentLoaded pattern.
- **Decision**: ACCEPTED-AS-RULE: Prefer DOMContentLoaded over IIFE for externalized browser scripts

### F8 — register.js uses var; inconsistent with const in journal-trends.js

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: src/main/resources/static/js/register.js:2
- **Detail**: register.js declares `var el = ...` while journal-trends.js uses const throughout. Minor JS style inconsistency; AGENTS.md var/val rules apply to Java only.
- **Fix**: Change `var` to `const` in register.js.
- **Decision**: ACCEPTED-AS-RULE: Use const/let over var in browser JS files
