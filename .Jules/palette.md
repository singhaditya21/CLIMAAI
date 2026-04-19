
## 2024-05-16 - Decorative Emojis in Interactive Components
**Learning:** Screen readers announce decorative emojis (like 🏠 inside `.nav-btn` navigation buttons) in `web-demo/index.html` which creates redundant and confusing announcements because the buttons already have descriptive text labels adjacent to them.
**Action:** Apply `aria-hidden="true"` to `span` elements wrapping decorative emojis inside interactive components that already possess adjacent descriptive text labels.
