## 2026-06-10 - [Initial UX check]
**Learning:** Checking for standard UX loading states.
**Action:** Need to add loading state to buttons.
## 2026-06-10 - [Accessible Async Loading States]
**Learning:** Vanilla JS forms need explicit UI feedback during async operations, but standard DOM methods like `e.target.querySelector` can fail if the click intercepted by child elements.
**Action:** Used `e.submitter` to robustly capture the submitting button, allowing temporary text modification (`⏳ Logging in...`), `disabled` state, and `aria-busy` attribute implementation within a `try/finally` block to ensure robust cleanup without adding custom CSS.
