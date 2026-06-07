## 2026-06-07 - [Handling async button loading states in vanilla JS]
**Learning:** When interacting with form submit events in the vanilla JavaScript frontend, use `e.submitter` rather than `e.target.querySelector('button[type="submit"]')` to reliably capture the specific button that triggered the submission. Convert `setTimeout` callbacks into awaitable Promises to reliably execute cleanup in a `finally` block.
**Action:** Use `e.submitter` when interacting with form submit events and use awaitable promises for mock network requests to ensure reliable state cleanup.
