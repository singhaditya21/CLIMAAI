
## 2026-06-14 - [Async Loading States in Forms]
**Learning:** When adding async loading states to vanilla JS form submit events, you must explicitly use `e.submitter` rather than generic selectors to reliably target the button that triggered the submission (crucial when multiple submit buttons exist or they lack explicit types). Also, placing the restoration logic in a `finally` block ensures the button recovers correctly even if the API call throws an error. Using `aria-busy="true"` on the active submitter enhances accessibility during processing.
**Action:** Always intercept form submissions using the `e.submitter` property and wrap the async API call in a `try...finally` block to handle UI state restoration robustly.
