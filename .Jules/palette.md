## 2024-04-26 - Add aria-hidden to decorative navigation icons
**Learning:** Emojis in `.nav-btn` interactive components are announced redundantly by screen readers when next to a descriptive text label.
**Action:** Apply `aria-hidden="true"` to span elements wrapping decorative emojis inside interactive components to prevent redundant or confusing screen reader announcements.
