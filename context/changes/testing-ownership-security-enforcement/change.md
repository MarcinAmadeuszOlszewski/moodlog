---
change_id: testing-ownership-security-enforcement
title: Phase 3 test rollout — ownership and security enforcement
status: implemented
created: 2026-06-15
updated: 2026-06-15
archived_at: null
---

## Notes

Open a change folder for rollout Phase 3 of context/foundation/test-plan.md: "Ownership + security enforcement".
Risks covered: Risk #4 (S-04 edit/delete/override ships without per-resource ownership check → authenticated user A mutates user B's entry), Risk #5 (Security config change accidentally removes route protection → anonymous GET on /journal, /journal/history, or /journal/trends succeeds).
Test types planned: integration (MockMvc + spring-security-test).
Risk response intent:
- Risk #4: prove PUT/PATCH/DELETE on an entry ID belonging to user B returns 403 or 404 when user A is authenticated; challenge that isAuthenticated() implies authorized to mutate this specific resource; avoid testing only same-user happy path.
- Risk #5: prove anonymous GET /journal, /journal/history, and /journal/trends each redirect to /login and any new route does not bypass the deny-all fallback; challenge that testing /journal covers sibling routes; avoid testing only one representative protected path.
After creating the folder, follow the downstream continuation rule.
