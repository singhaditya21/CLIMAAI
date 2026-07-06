## 2024-05-14 - Add loading feedback to form submit buttons
**Learning:** Users can easily click submit buttons multiple times on forms doing async operations (like authentication), leading to multiple requests and potential errors. A visible loading state is vital.
**Action:** Add disabled states with loading text (like `⏳ Loading...`) and restore button using `try/finally` block for consistent cleanup.
