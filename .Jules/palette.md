## 2024-05-14 - Missing Loading States on Async Actions
**Learning:** In the ClimaAI app, form buttons (Login, Register, Google Auth) lack visual feedback and disable states during async API requests.
**Action:** Always add loading spinners/text changes and disable the button natively (via `disabled=true` and `aria-busy=true`) during async submissions to prevent double-clicks and provide immediate feedback. Use `try...finally` to ensure state is cleaned up robustly.
