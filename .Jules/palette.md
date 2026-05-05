## 2024-03-24 - Async Button Loading States

**Learning:** When dealing with asynchronous form submissions, it's critical to capture the specific button that triggered the event using `e.submitter`. Disabling the button and setting `aria-busy="true"` prevents duplicate submissions and informs screen readers. Using a `finally` block to reset the state is essential to ensure the UI recovers regardless of API success or failure, especially when utilizing mock APIs or testing networks.

**Action:** Always capture `e.submitter` on form submit events and implement state resets in a `finally` block for robust UI recovery.