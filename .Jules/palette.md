## 2024-03-24 - Loading states on form buttons
**Learning:** Using `e.submitter` to apply a loading state on form submission is effective, but relies on `finally` blocks in async methods safely restoring state. If methods do not return Promises (e.g. `setTimeout`), the button may stay disabled forever if an error occurs.
**Action:** Always convert standard delays (like `setTimeout`) into awaitable Promises when they block form resolution, enabling safe state restoration via `try/finally` blocks and preventing lockouts.
