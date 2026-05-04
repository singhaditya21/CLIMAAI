## 2023-10-24 - Async Loading States for Form Submissions
**Learning:** Pure visual feedback (like a toast notification) isn't sufficient for async form submissions. Screen readers require explicit state changes like `aria-busy` and disabling the button prevents confusing double-submissions.
**Action:** Always capture `e.submitter` in `submit` event listeners to apply `disabled=true` and `aria-busy=true` explicitly to the submitting button, then safely restore the original state in a `finally` block to ensure UI recovery.
