## 2026-06-04 - Missing Loading State on Authentication Forms
**Learning:** Found that auth forms (login and register) in the vanilla JS app don't show a loading state on the submit button, keeping it clickable and confusing users during the API call. Also `.btn:disabled` styles were missing.
**Action:** Implemented async loading states with try/finally to ensure the UI handles API delays gracefully and buttons visually reflect the loading state.
