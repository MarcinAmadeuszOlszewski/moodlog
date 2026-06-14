<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Private journal access

- **Plan**: `context/changes/private-journal-access/plan.md`
- **Scope**: Phases 1-4 of 4
- **Date**: 2026-06-14
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical · 3 warnings · 6 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | WARNING |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Findings

### F1 — JournalController and journal.html exceed Phase 3 empty-shell scope

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: `src/main/java/com/amadeuszx/moodlog/journal/JournalController.java`
- **Detail**: Phase 3 specified a non-submitting empty-state journal shell. The plan's "What We're NOT Doing" section explicitly excluded entry creation, history, and trend rendering. The current JournalController handles POST /journal, GET /journal/history, and GET /journal/trends. journal.html is a full journal UI with a submitting form. This is expected growth from subsequent slices (first-mood-classified-entry, history-and-mood-trends) building on Phase 3's route — the plan's scope guardrails were superseded by deliberate later work, not an error.
- **Fix**: No code change needed. Acknowledge that Phase 3's empty-shell description is obsolete. The later change plans cover the added functionality.
- **Decision**: FIXED — acknowledged; expected growth from later slices; no code change needed

### F2 — Logger boilerplate in UserAccountService and SecurityConfiguration violates AGENTS.md

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: `src/main/java/com/amadeuszx/moodlog/user/UserAccountService.java:31`, `src/main/java/com/amadeuszx/moodlog/user/SecurityConfiguration.java:27`
- **Detail**: Both classes declare a manual `private static final Logger logger = LoggerFactory.getLogger(...class)` field. AGENTS.md requires Lombok annotations for logger declarations (`@Slf4j`). The field is also named `logger` rather than `log` (the name Lombok's @Slf4j generates), creating a naming inconsistency.
- **Fix**: Add `@Slf4j` to each class (in the Lombok annotation group); remove the manual field; rename all `logger.*` calls to `log.*`.
- **Decision**: FIXED

### F3 — UserAccount entity uses hand-written constructor and getters instead of Lombok

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: `src/main/java/com/amadeuszx/moodlog/user/UserAccount.java`
- **Detail**: UserAccount has a 7-argument hand-written constructor and individual getters for all fields. AGENTS.md requires Lombok to minimize boilerplate, specifically naming constructors, getters, and builders. A protected no-arg constructor is also needed for JPA and should be generated via Lombok.
- **Fix**: Annotate with `@AllArgsConstructor @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)` (Lombok group, after `@Entity`/`@Table`); remove all hand-written accessor methods and the explicit constructor.
- **Decision**: SKIPPED — finding was stale; current UserAccount.java already carries these Lombok annotations from an earlier refactor

### F4 — RegistrationForm uses hand-written getters/setters instead of @Data

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: `src/main/java/com/amadeuszx/moodlog/user/register/RegistrationForm.java`
- **Detail**: Three hand-written getters and three hand-written setters for a simple form-backing POJO. AGENTS.md Lombok rule applies.
- **Fix**: Add `@Data` (Lombok group, first annotation since no Spring annotations on this POJO) and remove the six accessor methods.
- **Decision**: FIXED

### F5 — AuthController captures SecurityContextHolderStrategy via static call

- **Severity**: OBSERVATION
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Pattern Consistency
- **Location**: `src/main/java/com/amadeuszx/moodlog/user/AuthController.java:42`
- **Detail**: The constructor body calls `SecurityContextHolder.getContextHolderStrategy()` as a static capture. All other AuthController dependencies enter via constructor parameters. This breaks the DI contract sibling controllers follow and makes unit testing AuthController harder without static-mocking support.
- **Fix A ⭐ Recommended**: Expose `SecurityContextHolderStrategy` as a `@Bean` in `SecurityConfiguration` and inject it into `AuthController`'s constructor.
  - Strength: Consistent with every other dependency; mockable in unit tests without framework tricks.
  - Tradeoff: One extra bean definition + constructor parameter change.
  - Confidence: HIGH — the same pattern is used for all other SecurityConfiguration-provided beans.
  - Blind spot: Need to verify no Spring Security internals assume the default strategy singleton.
- **Fix B**: Keep static capture but add a comment explaining the intent.
  - Strength: Zero diff to source; documents the deliberate deviation.
  - Tradeoff: Leaves the testability gap in place.
  - Confidence: MEDIUM — acceptable if @SpringBootTest tests are sufficient (they are).
  - Blind spot: Future developers may "fix" the static call without understanding the context.
- **Decision**: FIXED via Fix A

### F6 — register.html: intentional th:field omission on password field is undocumented

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: `src/main/resources/templates/register.html` (password input)
- **Detail**: The password `<input>` uses plain `name="password"` instead of `th:field="*{password}"`. This is the correct safer choice — `th:field` would echo the raw password back into the HTML value attribute on validation errors. The omission is undocumented; a future developer could change it to `th:field` and inadvertently introduce password echoing in the rendered HTML.
- **Fix**: Add an HTML comment on the password input: "th:field intentionally omitted — prevents Thymeleaf from echoing the raw password into the HTML value attribute on validation errors."
- **Decision**: FIXED

### F7 — Unbounded email string passed to safeEmailIdentifier in failure handler

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: `src/main/java/com/amadeuszx/moodlog/user/SecurityConfiguration.java:93`
- **Detail**: `request.getParameter("email")` is passed directly to `safeEmailIdentifier()`, which calls `trim().toLowerCase(Locale.ROOT)` before SHA-256 hashing. An attacker submitting a very large email value (e.g. 10 MB) causes a large string allocation on the request thread before hashing.
- **Fix**: Truncate input before normalizing in `safeEmailIdentifier()` — e.g. if `email.length() > 500` use `email.substring(0, 500)` before further processing.
- **Decision**: FIXED

### F8 — Duplicate-email check-then-act race is correctly handled but undocumented

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: `src/main/java/com/amadeuszx/moodlog/user/UserAccountService.java:50`
- **Detail**: `existsByEmail()` pre-check followed by `save()` is a classic check-then-act race. The `DataIntegrityViolationException` catch block correctly handles the concurrent-registration case and propagates `DuplicateUserAccountException`. The two-tier pattern is sound but undocumented — the catch block looks like defensive noise to a future reader rather than the deliberate safety net it is.
- **Fix**: Add a short comment above the catch block: "Handles concurrent-registration race where both requests passed existsByEmail but the DB unique constraint fires on the second insert."
- **Decision**: FIXED

### F9 — H2 fallback URL silently hides PostgreSQL-specific schema behavior in dev

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: `src/main/resources/application.properties` (datasource URL)
- **Detail**: `spring.datasource.url` defaults to an H2 file database when `MOODLOG_DATABASE_URL` is unset. H2's PostgreSQL mode is not complete; schema constructs accepted in H2 may fail in real PostgreSQL. `ddl-auto=validate` catches some mismatches but not all.
- **Fix**: Move the H2 default to `application-local.properties` (a dev-only profile) so the main `application.properties` has no fallback URL. Developers explicitly opt in to the H2 shortcut; CI always supplies the env var.
- **Decision**: FIXED — created src/main/resources/application-local.properties; removed H2 fallback from main application.properties
