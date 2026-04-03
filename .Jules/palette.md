## 2025-04-03 - Global Focus Visibility Fix
**Learning:** Native `:focus` outlines can conflict with custom designs or cause duplicate outlines when interacting via mouse. Resetting `:focus` to `outline: none;` while strictly using `:focus-visible` with a high-contrast style (e.g. `var(--primary-light)`) significantly improves keyboard navigation accessibility without breaking the aesthetic experience for pointer device users. Certain native inputs (like `.form-group input` and `.setting-select`) handle focus beautifully out-of-the-box and need explicit exclusion.
**Action:** When adding focus styles, prioritize `:focus-visible` over `:focus`. Always review and exclude specific elements that already incorporate tailored, custom focus treatments to prevent overriding or double-outline artifacts.

## 2025-04-03 - Form Submit Button Accessibility States
**Learning:** Auth forms often lack immediate feedback during async submission, causing confusion or duplicate submissions. Merely changing text isn't fully accessible. Setting the native `disabled=true` attribute combined with `aria-busy="true"` explicitly communicates the loading state to screen readers while preventing duplicate clicks.
**Action:** When adding loading states to submit buttons, always use `aria-busy="true"` and `disabled=true` together, instead of relying purely on visual text changes or CSS modifications.
