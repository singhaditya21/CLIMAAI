
## 2024-05-18 - Component-Specific Focus Rings
**Learning:** Applying a global `*:focus-visible` rule in the web frontend causes visual regressions on scrollable layout containers, which modern browsers natively make focusable.
**Action:** Always explicitly apply `:focus-visible` solely to specific interactive component classes (e.g., `.btn`, `.nav-btn`) and group with existing `:hover` rules to reuse existing design patterns and colors safely without breaking container aesthetics.
