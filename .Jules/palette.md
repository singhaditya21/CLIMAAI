## 2026-06-19 - Added loading states to auth forms
**Learning:** Missing loading states for async operations like login/register can lead to user confusion and potentially multiple form submissions.
**Action:** Always add visual feedback by disabling the submit button and updating its text to indicate a loading state during async API calls, and reliably restore state using a finally block.
