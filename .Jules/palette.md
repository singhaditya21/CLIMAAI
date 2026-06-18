## 2026-06-18 - Adding loading states to vanilla JS async forms
**Learning:** When using `e.submitter` to modify button state (like adding loading spinners or text) in vanilla JS, you must check for null values as the submitter can be undefined if triggered programmatically. Additionally, `try/finally` blocks are essential to guarantee the state is restored regardless of success or failure.
**Action:** Always null-check `e.submitter` and use `try/finally` to reliably restore interactive elements to their original state in pure JS applications.
