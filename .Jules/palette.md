## 2024-03-30 - Focus Outline Curve Behavior
**Learning:** When fixing missing keyboard focus indicators (`:focus-visible`) in custom UI components, avoid using `border-radius: inherit` for focus outlines, as it incorrectly forces inheritance from the parent container. Modern browsers natively curve outlines to match the element's actual border-radius.
**Action:** Let the browser handle the curve of the `outline` property organically, and ensure color choices reuse existing CSS variables (e.g., `var(--primary-light)`).
