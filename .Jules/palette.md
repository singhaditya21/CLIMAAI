## 2024-11-20 - Explicit Form Loading States

**Learning:** When forms process async actions, visually changing the button isn't enough. We must explicitly set `disabled=true` to prevent double-submissions, and `aria-busy="true"` so screen readers inform users the action is actively processing.

**Action:** Always capture `e.submitter` on form submit events, disable it, set `aria-busy`, update text, and restore everything in a `finally` block to ensure UI recovery on success or failure.
