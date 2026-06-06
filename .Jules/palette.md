
## 2024-05-18 - Safe Async Form Submissions
**Learning:** When adding loading states to vanilla JS form buttons, `e.submitter` must be null-checked to avoid TypeErrors. Additionally, state restoration (restoring text, disabling `aria-busy`, and re-enabling the button) must be placed in a `finally` block to ensure the UI remains usable regardless of network success or failure.
**Action:** Always wrap `e.submitter` state changes in a conditional and use `finally` for UI recovery in async DOM operations.
