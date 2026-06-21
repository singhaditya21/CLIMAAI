## 2024-11-09 - Async Button Feedback
**Learning:** For vanilla JS async form submissions, directly disabling `e.submitter` and updating innerHTML with a loading indicator during the await phase effectively prevents double-submissions and provides crucial feedback on latency, significantly improving user confidence.
**Action:** Always wrap async authentication/form submission logic in a `try/finally` block and reset the button's disabled state/original text in the `finally` block to ensure recovery on errors.
