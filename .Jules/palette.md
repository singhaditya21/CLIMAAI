
## 2024-05-01 - Adding aria-hidden to Nav Icons
**Learning:** Decorative emojis in interactive components (like nav buttons) are redundantly announced by screen readers if there's already an adjacent descriptive text label.
**Action:** Always add `aria-hidden="true"` to spans wrapping decorative emojis when a text label is present to keep the screen reader experience clean and uncluttered.
