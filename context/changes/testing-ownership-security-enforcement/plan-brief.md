# Ownership and Security Enforcement Tests — Plan Brief

> Full plan: `context/changes/testing-ownership-security-enforcement/plan.md`
> Research: `context/changes/testing-ownership-security-enforcement/research.md`

## What & Why

Add two new test classes that make Risk #4 (IDOR on write endpoints) and Risk #5 (security config regression) observable in the test suite. Risk #5 needs a clean, standalone security fence — the existing coverage is real but buried inside login-flow tests and missing anonymous POST and novel-path assertions. Risk #4 cannot be tested yet (no write endpoints exist), but a contract-anchor stub class prevents a naive `findById`-based S-04 implementation from shipping without ownership verification.

## Starting Point

`AuthenticationFlowTests` already asserts anonymous redirects for all three journal GET routes, but those assertions are embedded in multi-step saved-request tests. No dedicated security regression class exists. `JournalController` has no PUT/PATCH/DELETE methods; S-04 is `proposed` on the roadmap with no change folder opened.

## Desired End State

`SecurityRouteProtectionTests` (5 tests, new `security` package) is the readable security fence: any reader opening the test suite immediately sees the boundary. `JournalEntryOwnershipTests` (4 `@Disabled` stubs, `journal` package) is the contract anchor: the S-04 implementer removes `@Disabled` and the tests immediately verify 404-via-`findByIdAndUserAccountId` for cross-user mutations. Cookbook §6.2, §6.4, §6.6 are filled in.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
|---|---|---|---|
| Cross-user mutation response code | 404 Not Found | `findByIdAndUserAccountId` returns `Optional.empty()` — no entry existence leaked to caller | Plan |
| `SecurityRouteProtectionTests` package | Dedicated `security` package | Unambiguous home for security-boundary tests across all domains | Plan |
| `@Disabled` style | Per-test with message | Message `"Activate when S-04 edit/delete endpoints ship"` tells the next developer exactly what to do | Plan |
| Cookbook sections to fill | §6.2, §6.4, §6.6 | MockMvc setup + ownership pattern + phase notes | Plan |
| Phase 1 intentionally redundant | Yes — new class, not modifying existing tests | Readability: standalone fence vs. buried assertions inside login flows | Research |
| IDOR trap | `JpaRepository.findById` is unscoped | Unlike all current read queries (which use `ByUserAccountId`), the inherited method fetches any entry | Research |

## Scope

**In scope:**
- `SecurityRouteProtectionTests` — 5 tests: anonymous GET×3, anonymous POST /journal (with csrf), novel-path deny-all
- `JournalEntryOwnershipTests` — 4 `@Disabled` stubs for cross-user DELETE/PATCH and same-user DELETE/PATCH
- `test-plan.md` §6.2, §6.4, §6.6 cookbook fill-in

**Out of scope:**
- Activating ownership stubs (blocked on S-04 shipping write endpoints)
- Adding `findByIdAndUserAccountId` to `JournalEntryRepository` (belongs to S-04 change)
- Replacing existing `AuthenticationFlowTests` anonymous-redirect assertions
- Method-level security annotations (`@PreAuthorize`) — codebase uses structural query scoping

## Architecture / Approach

Both new test classes use `@SpringBootTest` + `MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build()` — the full Spring Security filter chain, not mocked security. Phase 1 tests perform no DB writes (no `@BeforeEach` cleanup needed). Phase 2 stubs seed two users and one entry per test via `@BeforeEach` with `deleteAll()` + register + save pattern, matching the established isolation pattern from `JournalFlowTests` and `JournalTrendsFlowTests`.

## Phases at a Glance

| Phase | What it delivers | Key risk |
|---|---|---|
| 1. Security Route Protection Fence | 5 standalone anonymous-request tests in `security` package | Anonymous POST test must use `.with(csrf())` or CSRF filter returns 403 before auth check |
| 2. Ownership Contract Stubs | 4 `@Disabled` ownership stubs in `journal` package | Stubs must compile today against non-existent endpoints — MockMvc builder calls compile regardless |
| 3. Cookbook Documentation | §6.2 + §6.4 + §6.6 filled in `test-plan.md` | None — pure documentation |

**Prerequisites:** None. All infrastructure (Spring Boot, MockMvc, spring-security-test) is already in place.
**Estimated effort:** ~1 session across 3 phases. Phases 1 and 2 are mechanical; Phase 3 is brief documentation.

## Open Risks & Assumptions

- S-04 must eventually add `findByIdAndUserAccountId(UUID, UUID)` to `JournalEntryRepository` — the stubs document this expectation but don't enforce the method exists today
- If S-04 changes the URL shape (e.g., `/journal/{id}` becomes `/entries/{id}`), the stubs need URL updates on activation

## Success Criteria (Summary)

- `.\mvnw.cmd test` passes: 5 new tests green, 4 tests disabled (not failed)
- A reader opening `src/test/java/.../security/` immediately understands it as the security regression gate
- A reader opening `JournalEntryOwnershipTests` understands what S-04 must satisfy before shipping
