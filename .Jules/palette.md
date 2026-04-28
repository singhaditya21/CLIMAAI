## 2024-05-24 - Accessibility improvements for emojis and interactive elements

**Learning:** Emojis used purely for decoration alongside text labels can create redundant screen reader announcements. Interactive components also need distinct focus states (e.g., `:focus-visible`) that don't rely purely on default behavior for better keyboard navigation without violating custom CSS rules.

**Action:** Added `aria-hidden="true"` to structural emojis (e.g., navigation icons and location pins) where text already exists, and grouped `:focus-visible` to reuse existing hover patterns for keyboard accessibility.
