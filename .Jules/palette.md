## 2024-05-24 - Added Global Focus Visible and Disabled States
**Learning:** The `web-demo` project lacks explicit focus indicators for keyboard users and clear disabled states for buttons in its base CSS (`style.css`).
**Action:** Always verify if global interactive elements (`button`, `a`, `input`, `select`) have a defined `:focus-visible` state. Reusing existing CSS variables (like `var(--primary-light)`) ensures consistent design system integration when adding accessibility styles. Use `!important` on disabled hover states to override existing transform/box-shadow effects efficiently.
