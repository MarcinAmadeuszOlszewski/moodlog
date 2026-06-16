<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: do-more-beautiful

- **Plan**: context/changes/do-more-beautiful/plan.md
- **Scope**: All phases (1–3 of 3)
- **Date**: 2026-06-15
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical  3 warnings  3 observations

## Verdicts

| Dimension | Verdict |
|---|---|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Automated Verification

- `.\mvnw.cmd test` → PASS (105 tests, 0 failures, 0 errors)
- All 8 templates contain `th:replace="~{fragments/head :: common-head}"` ✅
- `src/main/resources/static/css/styles.css` exists, non-empty ✅
- `src/main/resources/templates/fragments/head.html` exists ✅

## Findings

### F1 — Dead Thymeleaf attribute selector in styles.css

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: src/main/resources/static/css/styles.css:299
- **Detail**: `div[th\:if] article` never matches any browser element — Thymeleaf strips `th:if` server-side. Sibling selector `section > div > article` does the actual work.
- **Fix**: Remove `div[th\:if] article,` from line 299; keep only `section > div > article`.
- **Decision**: FIXED — removed dead selector from styles.css:299

### F2 — Inline JS data block on journal-trends.html has no CSP header

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: src/main/resources/templates/journal-trends.html:80–108
- **Detail**: `th:inline="javascript"` block pushes model attributes into a global `window` object. No CSP header configured. Strict `script-src 'self'` cannot be applied without first externalizing: `register.html` timezone script, `journal-history.html` onsubmit handler, and `journal-trends.html` inline data block. Applying `'unsafe-inline'` would provide only partial protection.
- **Fix**: Externalize all 3 inline scripts to `/js/*.js` files first, then add `script-src 'self'` CSP in SecurityConfiguration.
- **Decision**: SKIPPED — needs inline script refactor as prerequisite; actual risk low (model data is server-controlled, not raw user input)

### F3 — Import order in ApplicationTests violates AGENTS.md alphabetical rule

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: src/test/java/com/amadeuszx/moodlog/ApplicationTests.java:7–8
- **Detail**: `DisplayName` import appeared before `BeforeEach` — B < D alphabetically. Violates AGENTS.md rule. Sibling `AuthenticationFlowTests` has correct order.
- **Fix**: Swap the two import lines.
- **Decision**: FIXED — BeforeEach now before DisplayName

### F4 — ApplicationTests missing negative assertions for removed content

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: src/test/java/com/amadeuszx/moodlog/ApplicationTests.java:59–67
- **Detail**: Plan required asserting absence of "Witaj! Jesteś dziś" and "/v1/random". Test uses only positive matchers.
- **Fix**: Add `not(containsString("/v1/random"))` and `not(containsString("Witaj! Jesteś dziś"))` matchers.
- **Decision**: SKIPPED — positive assertions accepted as sufficient

### F5 — SecurityConfiguration permits /images/** with no images directory

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: src/main/java/com/amadeuszx/moodlog/user/SecurityConfiguration.java:48
- **Detail**: `/images/**` in `permitAll()` but no `static/images/` directory exists. Pre-existing dead config.
- **Fix**: Remove `"/images/**"` from the requestMatchers block.
- **Decision**: FIXED — removed dead permitAll entry

### F6 — README documents env vars beyond plan scope

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: README.md:45–47
- **Detail**: Plan listed 4 env vars; README documents 7 plus a "Running Tests" section. All extras accurate and match application.properties.
- **Decision**: SKIPPED — extra documentation is net positive
