## 2026-08-03 - Loading States for Async Operations
**Learning:** Missing loading states for async operations (like login, registration, and Google sign-in) can lead to user confusion and duplicate submissions. Modifying `innerHTML` safely within a `try/finally` block ensures the button is correctly reset to its original state, even if the operation throws an error.
**Action:** Always add visual loading indicators (like `⏳ Loading...`) and disable submit buttons during async calls to provide immediate feedback and prevent multiple clicks. Use `try/finally` to guarantee state restoration.
