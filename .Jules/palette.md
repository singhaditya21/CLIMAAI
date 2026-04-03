## 2026-04-03 - Decorative Emojis in Navigation
**Learning:** Decorative emojis used as icons within interactive components like navigation buttons are read aloud by screen readers, which can add unnecessary noise and confuse users when a text label is already present.
**Action:** Applied `aria-hidden="true"` to the `span` elements wrapping these decorative emojis, ensuring screen readers skip them and only announce the adjacent descriptive text labels.
