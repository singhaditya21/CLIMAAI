## 2026-06-13 - Add async loading states
**Learning:** In vanilla JS forms, 'submit' events don't visually block interactions; disabling the submit button with a try/finally block improves perceived performance and prevents duplicate submissions.
**Action:** Always wrap async form submissions in try/finally to restore button state reliably.
