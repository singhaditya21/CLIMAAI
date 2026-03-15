## 2024-03-15 - Native Outline Rounding on Focus
**Learning:** Modern browsers automatically curve the `outline` CSS property to match an element's `border-radius`. Attempting to explicitly force it with `border-radius: inherit` on `:focus-visible` can cause incorrect inheritance from parent containers rather than applying to the element itself.
**Action:** When adding generic keyboard focus indicators (`:focus-visible`) across a UI component library or app, omit `border-radius` overrides and allow the browser to natively curve the outline around elements like buttons and inputs.
