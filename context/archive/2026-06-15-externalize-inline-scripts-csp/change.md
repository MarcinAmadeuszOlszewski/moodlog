---
change_id: externalize-inline-scripts-csp
title: Externalize inline JavaScript and add Content-Security-Policy header
status: archived
created: 2026-06-15
updated: 2026-06-16
archived_at: 2026-06-16T07:26:36Z
---

## Notes

Follow-up from impl-review of do-more-beautiful (F2). Three templates contain inline JavaScript that prevents adding a strict CSP header:
- register.html timezone detection script
- journal-history.html delete confirmation onsubmit handler
- journal-trends.html th:inline="javascript" chart data block

Move all three to external files, then add `default-src 'self'` in SecurityConfiguration.
