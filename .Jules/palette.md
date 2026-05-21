## 2026-05-21 - Vanilla JS async state cleanup
**Learning:** When adding loading states and disabling buttons, vanilla JS `setTimeout` callbacks break `try/finally` state cleanup, because the `finally` block executes immediately rather than after the timeout.
**Action:** Convert `setTimeout` to awaitable Promises (e.g., `await new Promise(resolve => setTimeout(resolve, delay))`) so the `finally` block can properly restore the button state after the delay completes.
