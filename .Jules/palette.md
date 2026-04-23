## 2024-05-18 - Async Loading States
**Learning:** During async form submissions, failing to disable the button or update the aria-busy state can lead to duplicate submissions and provide no feedback to screen-reader users. The `e.submitter` object gives us the original button to modify its properties securely.
**Action:** When working on async operations triggered by forms, I will ensure the submit button is explicitly disabled and set to `aria-busy="true"` using `e.submitter`.
