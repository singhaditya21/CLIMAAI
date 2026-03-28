## 2024-05-24 - Global Focus State Management

**Learning:** When managing focus states globally using `*:focus-visible`, it's crucial to explicitly `not()` target elements that manage their own complex focus states (like `.form-group input` or `.setting-select`) to prevent double focus rings or conflicting outline offsets.

**Action:** Always test global `*:focus-visible` updates across all standard input types to ensure existing component-specific focus behaviors aren't overridden.
