
## 2026-08-09 - Add visual loading states to auth buttons
**Learning:** Adding clear loading states (e.g. `⏳ Loading...`) to async actions like authentication prevents double submissions and gives users immediate feedback, avoiding confusion when operations take time. It's crucial to wrap restoration logic in a `finally` block to ensure buttons don't stay disabled if an error occurs.
**Action:** Consistently disable submitters on form events and interactive buttons during async work, and always use try/finally to handle cleanup, even on failure.
