## 2026-07-04 - Add async loading states to auth buttons
**Learning:** Adding visual feedback (like '⏳ Loading...' and disabled state) to auth buttons during async operations improves user confidence and prevents duplicate submissions. State restoration in a 'finally' block is crucial for ensuring the UI is usable even if an error occurs.
**Action:** Always add visual loading states and disabled attributes to buttons during async API calls, and remember to use 'try/finally' blocks to clean up state changes robustly.
