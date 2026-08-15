## 2023-10-24 - Async Form Submissions
**Learning:** When implementing visual loading states for async vanilla JS form submissions, state restoration logic needs to be safely wrapped in a `finally` block utilizing `e.submitter` to ensure it triggers regardless of success or error.
**Action:** Always wrap state restoration logic for `disabled`, `innerHTML`, and `opacity` inside a `finally` block during async form operations, checking for `e.submitter`.
