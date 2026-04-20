## 2024-04-20 - Safe Keyboard Focus Styling
**Learning:** Applying global `*:focus-visible` styles in this web app causes severe visual regressions on scrollable layout containers, which modern browsers natively make focusable.
**Action:** Group the `:focus-visible` pseudo-class exclusively with existing `:hover` CSS blocks for specific interactive component classes (e.g., `.btn`, `.nav-btn`) to safely apply focus states without custom CSS or breaking layouts.
