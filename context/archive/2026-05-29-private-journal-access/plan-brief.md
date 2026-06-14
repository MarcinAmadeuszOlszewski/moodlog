# Private journal access - Plan Brief

> Full plan: `context/changes/private-journal-access/plan.md`

## What & Why

We are building the first private access slice for MoodLog: public landing page, registration, login, logout, and a protected empty journal shell. The goal is to prove that the app can safely separate anonymous and authenticated users before entry creation, history, and trends arrive in later slices.

## Starting Point

Today the app is a public Spring MVC + Thymeleaf site with `GET /` and `GET /v1/random`, no auth, no persistence, and one `MockMvc`-based integration test class. This change is the first one that introduces security rules, user storage, and a protected HTML route.

## Desired End State

Anonymous users can still open the landing page and move into dedicated login and registration screens. A new user can register with email + password, become active immediately, land in a protected empty journal shell, and later return through login. Logged-out access to `/journal` redirects to login and then back to the protected route after success.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
| --- | --- | --- |
| Public entrypoint | Keep `/` public | Preserves the existing landing flow and keeps anonymous routing simple. |
| Registration data | Email + password only | Matches the smallest possible account model for S-01. |
| Account activation | Immediate activation + auto-login | Avoids email infrastructure and lands the user in the private shell right away. |
| Session model | Standard server session, no remember-me | Fits the current MVC app and keeps security scope narrow. |
| Error messaging | Generic login failure, explicit duplicate signup | Balances safe auth behavior with usable registration feedback. |
| Recovery scope | No password reset in S-01 | Prevents email/token flows from bloating the first slice. |
| Verification bar | Broad negative auth matrix + repository tests | Auth is the first protected flow, so confidence needs to be higher than a happy path only. |

## Scope

**In scope:**
- public landing page with login/register links
- registration, login, logout
- minimal persisted user accounts
- protected `/journal` shell with empty state
- redirect-to-login and return-to-target flow
- basic auth success/failure logging

**Out of scope:**
- password reset and email verification
- remember-me sessions
- profile management and account deletion
- entry creation, history, trends, and dashboards
- advanced audit trail or anti-abuse features

## Architecture / Approach

Use the existing Spring MVC monolith shape and add conventional server-side auth rather than a custom client-side flow. The plan introduces a minimal user-account persistence layer, route protection for `/journal`, dedicated auth templates, and test coverage that protects both the new private flow and the existing public routes.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Security and user-account foundation | Dependencies, config, user model, route protection | First persistence/auth setup can break startup or public routes if miswired. |
| 2. Public login and registration flow | Working signup/login/logout UX | Error handling and copy can drift from the agreed safe behavior. |
| 3. Protected journal shell and redirect flow | First authenticated page and saved-target return | Easy to create broken redirects or a misleading CTA for not-yet-built entry creation. |
| 4. Auth hardening and verification depth | Logging, negative-path coverage, regression safety | Under-testing this slice would make later journal work fragile. |

**Prerequisites:** current change folder exists; implementation can add the first database-backed account storage and related environment config.
**Estimated effort:** ~4-6 focused sessions across 4 phases.

## Open Risks & Assumptions

- This slice introduces the app's first persistent data, so local/test/deploy environments must all support the chosen database configuration.
- The empty journal CTA is intentionally a shell affordance until S-02; if stakeholders expect real entry creation here, scope must expand.
- Existing public behavior, especially `/` and `/v1/random`, must stay accessible after auth lands.

## Success Criteria (Summary)

- A user can register, sign in, sign out, and reach a protected journal shell using only email + password.
- Anonymous access to `/journal` redirects through login and returns to the protected page after success.
- Automated tests cover both happy path and key negative auth cases without regressing existing public routes.
