
## 2024-06-27 - Communicating Async State in Vanilla JS
**Learning:** You can effectively communicate async loading states for forms (like "Signing in...") by safely caching `e.submitter.innerHTML` and restoring it in a `finally` block, avoiding the need for dedicated custom CSS classes or complex state management.
**Action:** Use this lightweight pattern (`opacity: 0.7`, `cursor: not-allowed`, dynamic `innerHTML`) for legacy or vanilla JS form handling where introducing a new spinner component or CSS class is restricted.
