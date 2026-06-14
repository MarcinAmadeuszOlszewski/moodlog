# Private journal access Implementation Plan

## Overview

Implement the first user-facing auth slice for MoodLog: keep `/` public, let a user register with email and password, sign in, sign out, and reach a protected empty journal shell. This plan introduces the first persistent user account data, access control, and auth-specific verification without pulling entry creation, trends, or account recovery into the same change.

## Current State Analysis

MoodLog is currently a small Spring MVC + Thymeleaf app with one public HTML route and one public REST endpoint. The codebase has no auth, no persistence layer, no migrations, and no protected pages yet, so `private-journal-access` is the first slice that crosses security, storage, UI, and test boundaries together.

The current application shape matters:
- `pom.xml:32-57` includes Spring Web MVC, Thymeleaf, Lombok, and test support, but no security or persistence dependencies.
- `src/main/java/com/amadeuszx/moodlog/IndexController.java:6-12` serves the current public landing page at `/`.
- `src/test/java/com/amadeuszx/moodlog/ApplicationTests.java:18-50` already establishes the preferred verification pattern with `@SpringBootTest` and `MockMvc`.
- `src/main/resources/templates/index.html` already carries Polish UI copy, so new auth templates should match that language and UTF-8 handling.

## Desired End State

After this plan lands, anonymous users can still open the public landing page, move to dedicated login and registration screens, create an account with email and password, sign in, and sign out. Successful registration activates the account immediately, creates an authenticated session, and lands the user in a protected empty journal shell.

Unauthenticated requests to `/journal` redirect to login and then return to the protected target after successful authentication. The auth flow exposes only the minimal first-slice behavior: email + password credentials, standard browser session, basic password validation, clear but safe error handling, and basic success/failure auth logging.

### Key Discoveries:

- `pom.xml:32-57` confirms this slice must add the project's first security and persistence capabilities rather than plugging into an existing stack.
- `src/main/java/com/amadeuszx/moodlog/IndexController.java:6-12` and `src/main/resources/templates/index.html` show the app is currently server-rendered and route-light, so the least risky auth shape is server-side MVC rather than client-managed auth.
- `src/test/java/com/amadeuszx/moodlog/ApplicationTests.java:18-50` shows route and rendered-HTML changes should be verified through `MockMvc`; this change should extend that style instead of inventing a new test strategy.
- `context/foundation/roadmap.md` defines this change as S-01 and explicitly stops at access to a private journal shell, not entry creation.

## What We're NOT Doing

- Password reset or account recovery
- Email verification or delayed account activation
- Remember-me or long-lived persistent sessions
- Profile fields beyond email and password
- Entry creation, editing, deletion, history, or trend rendering
- Account deletion or admin moderation flows
- Full audit trail, CAPTCHA, or advanced anti-abuse controls

## Implementation Approach

Use the existing Spring MVC monolith shape and add conventional server-side auth instead of inventing a custom client-managed flow. The change should introduce a minimal user-account persistence model, server-session authentication, dedicated login and registration pages, a protected `/journal` route, and a broad integration-test matrix that covers both happy paths and auth denials.

The access rules should preserve current public behavior for `/` and `/v1/random`, while explicitly protecting `/journal` and future private routes. Registration should create an active account immediately, hash the password, establish the authenticated session, and redirect into the private shell. Login should show a generic credential failure, while registration may explicitly reject duplicate email addresses.

## Critical Implementation Details

### Timing & lifecycle

Registration must end with the same authenticated session shape as a successful login because the agreed end state lands the user in `/journal` immediately after signup. Saved-target redirects should apply only when auth began from a protected route; logout should always clear that state and go to the login page with confirmation.

### User experience spec

The empty journal shell needs a clear first-entry CTA, but S-01 must not fake entry creation before S-02 exists. The CTA should therefore be visibly present while remaining non-submitting and non-broken until the real create-entry flow lands in the next slice.

### Debug & observability

Auth logging should capture success/failure event type and safe identifiers only. Raw passwords, full credential payloads, and session identifiers must never appear in application logs.

## Phase 1: Security and user-account foundation

### Overview

Establish the dependencies, configuration, persistence contract, and route-protection rules that the rest of the auth flow depends on.

### Changes Required:

#### 1. Platform and runtime configuration

**File**: `pom.xml`

**Intent**: Add the missing build-time capabilities for security, validation, and the first persistent account data. This phase should keep the project in the existing Maven + Spring shape rather than creating a parallel auth mechanism.

**Contract**: The application build includes the Spring auth and persistence capabilities needed by `private-journal-access`, while keeping the current MVC and test stack intact.

#### 2. Application configuration and environment contract

**File**: `src/main/resources/application.properties`

**Intent**: Introduce the auth- and persistence-related application settings without breaking existing UTF-8 and MVC behavior. This is where the plan should define the environment-backed contract the deployed app will need once a real database is present.

**Contract**: The app has explicit configuration for the first persistent account store and auth runtime behavior, with properties wired through environment-friendly configuration rather than hard-coded secrets.

#### 3. User account persistence and auth domain

**File**: `src/main/java/com/amadeuszx/moodlog/UserAccount.java`, `src/main/java/com/amadeuszx/moodlog/UserAccountRepository.java`, `src/main/java/com/amadeuszx/moodlog/UserAccountService.java`, `src/main/resources/db/**`

**Intent**: Introduce the minimal account model required for this slice: unique email, hashed password, and active state that starts enabled immediately after registration. Keep the domain intentionally narrow so the first migration does not smuggle profile or recovery concerns into S-01.

**Contract**: Email is unique and case-normalized for lookup, passwords are stored hashed rather than raw, and account creation does not require profile data beyond email and password.

#### 4. Route protection and session rules

**File**: `src/main/java/com/amadeuszx/moodlog/SecurityConfiguration.java`

**Intent**: Define which routes stay public and which become protected, while using server-side session auth that fits the current MVC app. The rule set should preserve the existing public routes and add explicit protection for the private journal shell.

**Contract**: `/`, auth pages, static assets, and `/v1/random` stay public; `/journal` and future private routes require an authenticated session; logout clears the session and redirects to login with confirmation.

### Success Criteria:

#### Automated Verification:

- Security, persistence, and MVC dependencies compile and the application test suite passes via `.\mvnw.cmd test`
- The first user-account schema bootstrap succeeds under test configuration without breaking app startup

#### Manual Verification:

- Anonymous users can still open `/`, while `/journal` is no longer accessible without auth

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase. Phase blocks use plain bullets — the corresponding `- [ ]` checkboxes for these items live in the `## Progress` section at the bottom of the plan.

---

## Phase 2: Public login and registration flow

### Overview

Add the public auth pages and form handling that let a user create an account, sign in, and sign out while keeping the landing page public.

### Changes Required:

#### 1. Public routing and auth form handlers

**File**: `src/main/java/com/amadeuszx/moodlog/IndexController.java`, `src/main/java/com/amadeuszx/moodlog/AuthController.java`, `src/main/java/com/amadeuszx/moodlog/RegistrationForm.java`

**Intent**: Keep the landing page as the public entrypoint and add dedicated login and registration screens plus form handling. This phase is responsible for the agreed UX rules around generic login failures, explicit duplicate-email rejection on signup, and immediate authenticated access after successful registration.

**Contract**: The public landing page links into separate login and registration pages; registration accepts only email + password; successful registration activates the account immediately and lands in `/journal`; login failures do not reveal whether an account exists.

#### 2. Public auth templates

**File**: `src/main/resources/templates/index.html`, `src/main/resources/templates/login.html`, `src/main/resources/templates/register.html`

**Intent**: Render the auth flow using the existing server-rendered approach and Polish UI copy. The templates should make the first slice feel coherent without introducing dashboard or entry-management behavior too early.

**Contract**: Auth pages render in Polish, collect only email and password, surface validation and flash feedback safely, and preserve the public landing page as the anonymous starting point.

#### 3. Logout confirmation flow

**File**: `src/main/java/com/amadeuszx/moodlog/SecurityConfiguration.java`, `src/main/resources/templates/login.html`

**Intent**: Finish the public auth loop by making logout visible and understandable to the user. This phase should route a logged-out user back to login instead of silently dropping them onto an unclear page.

**Contract**: Manual logout invalidates the session and lands on the login screen with a short confirmation message.

### Success Criteria:

#### Automated Verification:

- MVC tests cover public landing, login page, registration page, successful signup, duplicate-email signup, failed login, and logout redirect behavior

#### Manual Verification:

- A new user can register and is taken straight to the private journal shell
- An existing user can log in, log out, and see clear Polish feedback on each auth screen

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase. Phase blocks use plain bullets — the corresponding `- [ ]` checkboxes for these items live in the `## Progress` section at the bottom of the plan.

---

## Phase 3: Protected journal shell and redirect flow

### Overview

Introduce the first authenticated-only page and make the redirect-back behavior from protected routes feel natural and stable.

### Changes Required:

#### 1. Private journal shell

**File**: `src/main/java/com/amadeuszx/moodlog/JournalController.java`, `src/main/resources/templates/journal.html`

**Intent**: Add the first page that exists only for authenticated users. This page is intentionally a shell: it proves private access works and makes the next action obvious without pulling entry persistence into the same change.

**Contract**: `GET /journal` renders only for authenticated users and shows an empty-state journal shell with a visible first-entry CTA placeholder that does not attempt to submit or persist an entry before S-02 lands.

#### 2. Saved-target redirect behavior

**File**: `src/main/java/com/amadeuszx/moodlog/SecurityConfiguration.java`, `src/main/java/com/amadeuszx/moodlog/AuthController.java`

**Intent**: Support the agreed flow where a user who hits a protected route while logged out returns there after successful authentication. This should work for `/journal` and future protected routes without changing the default anonymous experience from the public landing page.

**Contract**: Anonymous requests to `/journal` redirect to login; successful login returns to the originally requested protected target when one exists, otherwise to `/journal`.

### Success Criteria:

#### Automated Verification:

- MVC tests verify anonymous redirect to login, successful return to `/journal`, and authenticated rendering of the journal shell

#### Manual Verification:

- Opening `/journal` directly while logged out returns through login back to the private journal shell
- The empty-state journal presents a clear next step without exposing a broken create-entry flow

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase. Phase blocks use plain bullets — the corresponding `- [ ]` checkboxes for these items live in the `## Progress` section at the bottom of the plan.

---

## Phase 4: Auth hardening and verification depth

### Overview

Add the final guardrails that make the first auth slice safe to extend: logging discipline, negative-path coverage, and regressions around the now-mixed public/private routing model.

### Changes Required:

#### 1. Safe auth event logging and validation boundaries

**File**: `src/main/java/com/amadeuszx/moodlog/SecurityConfiguration.java`, `src/main/java/com/amadeuszx/moodlog/UserAccountService.java`, `src/main/resources/application.properties`

**Intent**: Add the basic success/failure auth logging requested for MVP supportability and centralize the agreed password and credential rules. The goal is diagnosability without turning S-01 into a full audit subsystem.

**Contract**: Auth success and failure events are logged with safe identifiers only, the minimum password rule is enforced consistently, and no sensitive credential data appears in logs.

#### 2. Negative-path and persistence test expansion

**File**: `src/test/java/com/amadeuszx/moodlog/ApplicationTests.java`, `src/test/java/com/amadeuszx/moodlog/AuthenticationFlowTests.java`, `src/test/java/com/amadeuszx/moodlog/UserAccountRepositoryTests.java`

**Intent**: Reflect the user's chosen confidence bar by covering not only happy paths but also duplicate signup, invalid credential handling, repository invariants, and public-route regressions. This is the phase that turns the auth slice into a stable base for S-02.

**Contract**: Tests cover duplicate email rejection, generic login failure, protected-route redirects, logout behavior, repository uniqueness/persistence rules, and preservation of the existing public `/` and `/v1/random` behavior.

### Success Criteria:

#### Automated Verification:

- Repository and auth-flow tests cover the negative matrix and persistence invariants agreed during planning
- The full regression suite passes via `.\mvnw.cmd test`

#### Manual Verification:

- End-to-end browser smoke testing confirms landing -> register -> journal -> logout -> login -> journal works as planned
- Auth logs are visible for success and failure paths without exposing sensitive credential data

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase. Phase blocks use plain bullets — the corresponding `- [ ]` checkboxes for these items live in the `## Progress` section at the bottom of the plan.

## Testing Strategy

### Unit Tests:

- Password policy and credential normalization at the service boundary
- Duplicate-email rejection and account creation invariants around the user-account domain

### Integration Tests:

- Public `/` remains anonymous and keeps linking into login/register
- Successful signup creates an authenticated session and lands in `/journal`
- Failed login stays generic while duplicate signup remains explicit
- Anonymous `/journal` requests redirect to login and then back to the protected target after success
- Logout clears access to `/journal` and returns the user to the login screen
- Existing `/v1/random` behavior remains public and unchanged

### Manual Testing Steps:

1. Open `/` anonymously and confirm the landing page remains public with links to login and registration.
2. Register a fresh account with email + password and confirm the app lands in the empty private journal shell.
3. Log out and confirm the login screen shows logout confirmation.
4. Log back in with the same account and confirm the journal shell opens again.
5. Open `/journal` while logged out, authenticate, and confirm the app returns to `/journal`.
6. Try duplicate signup and invalid login credentials and confirm the copy matches the agreed error behavior.

## Performance Considerations

This slice should stay operationally light: one protected shell page, one account lookup path, and standard server-session behavior. Avoid loading future journal history or trend data in `/journal`, keep logging concise, and keep validation synchronous and local to the request path.

## Migration Notes

This change introduces the first persistent schema in the app, so environment-backed database configuration becomes part of the deploy contract for the first time. Prefer an additive first migration for user accounts and keep rollback focused on route/config changes where possible; once real user accounts exist, rolling back UI code should not require destroying account data.

## References

- Roadmap item: `context/foundation/roadmap.md`
- Product requirements: `context/foundation/prd.md`
- Shape notes: `context/foundation/shape-notes.md`
- Existing public controller: `src/main/java/com/amadeuszx/moodlog/IndexController.java:6-12`
- Existing test pattern: `src/test/java/com/amadeuszx/moodlog/ApplicationTests.java:18-50`
- Current build baseline: `pom.xml:32-57`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Security and user-account foundation

#### Automated

- [x] 1.1 Security, persistence, and MVC dependencies compile and the application test suite passes via `.\mvnw.cmd test` — fdc3d9c
- [x] 1.2 The first user-account schema bootstrap succeeds under test configuration without breaking app startup — fdc3d9c

#### Manual

- [x] 1.3 Anonymous users can still open `/`, while `/journal` is no longer accessible without auth — fdc3d9c

### Phase 2: Public login and registration flow

#### Automated

- [x] 2.1 MVC tests cover public landing, login page, registration page, successful signup, duplicate-email signup, failed login, and logout redirect behavior — e1e04f1

#### Manual

- [x] 2.2 A new user can register and is taken straight to the private journal shell — e1e04f1
- [x] 2.3 An existing user can log in, log out, and see clear Polish feedback on each auth screen — e1e04f1

### Phase 3: Protected journal shell and redirect flow

#### Automated

- [x] 3.1 MVC tests verify anonymous redirect to login, successful return to `/journal`, and authenticated rendering of the journal shell — 07df9da

#### Manual

- [x] 3.2 Opening `/journal` directly while logged out returns through login back to the private journal shell — 07df9da
- [x] 3.3 The empty-state journal presents a clear next step without exposing a broken create-entry flow — 07df9da

### Phase 4: Auth hardening and verification depth

#### Automated

- [x] 4.1 Repository and auth-flow tests cover the negative matrix and persistence invariants agreed during planning — 88908d9
- [x] 4.2 The full regression suite passes via `.\mvnw.cmd test` — 88908d9

#### Manual

- [x] 4.3 End-to-end browser smoke testing confirms landing -> register -> journal -> logout -> login -> journal works as planned — 88908d9
- [x] 4.4 Auth logs are visible for success and failure paths without exposing sensitive credential data — 28680a9
