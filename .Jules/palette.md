## 2024-05-18 - Visual Loading States for Vanilla JS Forms
**Learning:** Added visual loading states to form submissions (`handleLogin`, `handleRegister`) and standalone buttons (`handleGoogleSignIn`) using standard text indicators (`⏳ Loading...`). Wrapped the logic in `try/finally` blocks to ensure button states (disabling and text) are correctly restored regardless of success or failure.
**Action:** Always check `e.submitter` for forms and `e.currentTarget` for standard click events in vanilla JS. Use `try/finally` for state restoration.
