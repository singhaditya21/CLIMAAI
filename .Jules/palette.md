## 2026-04-12 - Prevent Redundant Screen Reader Announcements for Decorative Icons
**Learning:** Decorative emojis inside interactive components (like `.nav-btn`) that have accompanying descriptive text labels can cause redundant or confusing announcements for screen reader users.
**Action:** Add `aria-hidden="true"` to spans wrapping these decorative icons to silence them while preserving the accessible text label.

## 2026-04-12 - Native Focus Visible Fallbacks for Keyboard Accessibility
**Learning:** Applying global `*:focus-visible` styles in this app causes visual regressions on scrollable layout containers which modern browsers natively make focusable.
**Action:** Group `:focus-visible` exclusively with existing `:hover` CSS blocks for specific interactive component classes (e.g., `.btn:hover, .btn:focus-visible`) to safely reuse existing design patterns and colors without violating the 'no custom CSS' constraint.
