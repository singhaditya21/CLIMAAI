## 2024-03-20 - Focus Styles & Border Radius Inheritance
**Learning:** Modern browsers automatically curve native `outline` properties to match an element's `border-radius`. Using `border-radius: inherit` on focus rings is not only unnecessary but can incorrectly force inheritance from a parent container rather than the focused element itself.
**Action:** When adding global `:focus-visible` styles, keep the CSS simple (`outline: 2px solid var(--color); outline-offset: 2px;`) and trust the browser to handle the curvature natively.
