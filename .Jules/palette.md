## 2025-01-21 - Native Outline Curving and Focus Visible
**Learning:** Modern browsers natively curve `outline` when an element has a border-radius. Avoid using `border-radius: inherit` for focus outlines, as it incorrectly forces inheritance from the parent container instead of the element itself.
**Action:** When adding global `:focus-visible` styles, simply use `outline` without custom border-radius properties to let the browser automatically match the element's shape.
