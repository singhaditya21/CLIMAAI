## 2026-08-17 - Button loading states with e.submitter
**Learning:** Using `e.submitter` in `submit` events reliably identifies the trigger element without hallucinating class names. Modifying `innerHTML` safely requires restoring the previous content using a `try/finally` block.
**Action:** Always use `e.submitter` inside a null-check with `try/finally` state restoration when implementing loading states on form submission.
