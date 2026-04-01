## 2024-04-01 - Global Keyboard Focus Indicators
**Learning:** The demo app has buttons, links, and navigation items, but lacks global focus indicators (`:focus-visible`), making it difficult to navigate via keyboard. Modern browsers correctly inherit border radius for focus rings automatically, so avoiding `border-radius: inherit` for outline is important.
**Action:** Add a clean global `*:focus-visible` outline using the existing `--primary-light` variable and explicitly remove default `*:focus` outlines to prevent double focus rings while excluding elements that handle their own focus.
