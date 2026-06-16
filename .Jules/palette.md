## 2026-06-16 - Reliable Loading States in Vanilla JS Forms
**Learning:** When implementing async form submissions in vanilla JavaScript, it's safer to use `e.submitter` (wrapped in a null-check) to target the specific button pressed, and to place the UI restoration logic inside a `finally` block. Also, simulated network delays should use awaitable Promises rather than `setTimeout` callbacks so the `finally` block executes deterministically.
**Action:** Use `try/finally` blocks for UI state restoration and robustly check `e.submitter` when adding loading states to buttons.
