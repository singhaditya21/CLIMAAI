## 2026-04-18 - Loading States for Native Submit Buttons
**Learning:** In vanilla JavaScript apps where forms are intercepted, accessing the clicked submit button via `e.submitter` allows you to explicitly provide loading states. This pattern handles complex multi-button forms efficiently without querying elements.
**Action:** Always provide explicit disabled and `aria-busy` feedback directly on the `e.submitter` button during asynchronous requests, explicitly wrapping the API call in a `try...finally` block to restore the original state.
