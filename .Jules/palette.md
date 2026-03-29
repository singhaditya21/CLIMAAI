## 2024-03-29 - Global Focus Visible Strategy
**Learning:** Adding a global `*:focus-visible` ring can conflict with components that handle their own focus states (like `.form-group input` or `.setting-select` styled with custom outlines/box-shadows), leading to duplicate or misaligned focus rings.
**Action:** When applying global `*:focus-visible` styles for accessibility, explicitly reset or exclude components that have their own custom focus implementations using specific CSS selectors (e.g., `input:focus-visible { outline: none; }`).
