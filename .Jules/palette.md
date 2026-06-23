## 2026-06-23 - Form Submitter Loading States
**Learning:** Native `e.submitter` on `submit` events reliably provides the button used to submit a form, allowing for dynamic visual loading states without hardcoding button IDs or changing core app logic.
**Action:** Use `e.submitter` to gracefully handle visual loading styles (disabled, opacity, cursor) during async form submissions.
