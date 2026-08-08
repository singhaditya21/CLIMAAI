## 2026-08-08 - Added Visual Feedback and Disabled State to Form Buttons
**Learning:** During asynchronous operations like login or registration, users may double-click submission buttons if they don't see immediate feedback, leading to duplicate requests.
**Action:** Always disable form submission buttons and provide a visual loading indicator (e.g., changing text to "⏳ Logging in...") during async requests, restoring the state in a `finally` block to ensure a smooth and robust user experience.
