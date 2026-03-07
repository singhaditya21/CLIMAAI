## 2024-03-09 - Focus Rings on Rounded Elements
**Learning:** Modern browsers natively curve `outline` rings to match an element's `border-radius`. Setting `border-radius: inherit` on a focus outline is usually incorrect and counterproductive, as it attempts to inherit from the parent container rather than following the element's own border-radius.
**Action:** When adding global `:focus-visible` accessibility styles, rely on the native `outline` behavior for rounded shapes instead of applying explicit border-radius properties to the focus state.
