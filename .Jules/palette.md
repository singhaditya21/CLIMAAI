
## 2024-06-25 - Grouping :focus-visible with :hover states
**Learning:** Adding global `*:focus-visible` styles caused visual regressions on scrollable containers.
**Action:** Applied `:focus-visible` to specific interactive classes by grouping them directly with existing `:hover` CSS blocks (e.g., `.nav-btn:hover, .nav-btn:focus-visible`) to drastically improve keyboard accessibility across the app without violating the 'no custom CSS' boundary.
