## 2024-04-22 - Explicit Async States in Vanilla JS
**Learning:** Changing button text visually during async form submission is insufficient for screen readers. In a vanilla JS environment like `web-demo`, using `e.submitter` to apply `disabled=true` and `aria-busy="true"` reliably communicates processing states and prevents duplicate submissions without requiring custom CSS.
**Action:** Always pair visual loading text updates with `aria-busy="true"` and `disabled=true` on the specific submission button when handling async requests.
