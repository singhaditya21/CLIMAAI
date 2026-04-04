
## 2025-05-15 - Improve Focus Styles and Screen Reader Redundancy
**Learning:** Decorative emojis inside interactive components (like nav buttons) are often read redundantly by screen readers if the element already has descriptive text (e.g. "House Home"). Also, applying `*:focus-visible` globally can break layout containers.
**Action:** Apply `aria-hidden="true"` exclusively to the emoji/icon spans inside buttons that already have a label. Pair `:focus-visible` states explicitly with existing `:hover` CSS blocks to reuse design patterns without violating custom CSS boundaries or causing layout regressions.
