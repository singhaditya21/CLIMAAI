## 2026-03-23 - Add Global Focus Visible Styles
**Learning:** Found that custom UI components (like theme toggles and custom buttons) lacked proper keyboard focus indicators, making the site difficult to navigate for keyboard-only users.
**Action:** Implemented global `:focus-visible` styles but specifically excluded `.form-group input` and `.setting-select` to prevent duplicate or conflicting focus rings on elements that handle their own focus states, utilizing `var(--primary-light)` to maintain consistent design token usage.
