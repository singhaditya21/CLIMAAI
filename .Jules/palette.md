## 2024-04-30 - Vanilla JS Form Loading States Accessibility
**Learning:** In vanilla JavaScript forms without frameworks, `e.target.querySelector('button[type="submit"]')` can be unreliable if buttons lack explicit types. Using `e.submitter` accurately captures the exact button that triggered the form submission.
**Action:** Use `e.submitter` to apply `disabled=true` and `aria-busy="true"` on the specific triggering button, and always place the state reset logic (restoring text, disabled, aria-busy) in a `finally` block to ensure UI recovery regardless of async success or failure.
