
## 2026-05-29 - [Robust Async Loading States for Form Submits]
**Learning:** When adding loading states (disabled, aria-busy, text updates) to submit buttons in Vanilla JS forms where the submit event binds to the form itself, it's critical to access the specific button via `e.submitter`. Always implement state restoration (e.g. restoring original text, enabling the button, and removing the aria-busy attribute) inside a `finally` block to ensure UI recovery regardless of whether the async logic (e.g., login, register) resolves successfully or throws an error.
**Action:** Use `e.submitter` inside a null-check alongside `try/finally` for implementing loading states in form submit handlers where direct button clicks aren't individually targeted.
