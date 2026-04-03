## 2025-02-12 - Added Global Focus Visible Outline
**Learning:** When applying a global `*:focus-visible` outline for keyboard accessibility, it can conflict with interactive components (e.g. `.form-group input`, `.setting-select`) that already define their own focus styles, leading to double outlines or poor visuals.
**Action:** Use `:not()` pseudo-class in the global focus rule to explicitly exclude components that handle their own focus styling.
