
## 2026-06-28 - Async Loading States via `e.submitter`
**Learning:** Native form events expose `e.submitter`, enabling direct access to the initiating element (e.g. submit button) for visual loading states, but it must be safely null-checked since programmatic or non-button submissions might leave it undefined. Using `.innerHTML` preserves any icon elements inside the button text.
**Action:** Always capture button state (`innerHTML`) and safely null-check `e.submitter` before modifying properties (`disabled`, `style.opacity`) during async calls, restoring original state in a `finally` block to guarantee recovery.
