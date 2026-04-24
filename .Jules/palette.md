## 2024-04-24 - Screen Reader Compatibility with Decorative Emojis
**Learning:** Screen readers will redundantly announce decorative emojis inside interactive components (like `.nav-btn` navigation buttons) when a descriptive text label is already present, leading to confusing or verbose output.
**Action:** Apply `aria-hidden="true"` to span elements wrapping decorative emojis inside interactive components that already possess adjacent descriptive text labels.
