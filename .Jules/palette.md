## 2024-05-18 - Missing Loading State Feedback on Async Forms
**Learning:** In the ClimaAI login and registration forms, async network requests are triggered but the submit button lacks visual loading states and remains interactive. This allows multiple submissions and leaves the user wondering if the button click registered.
**Action:** Always provide immediate visual feedback and disable the submit button during async form submissions to improve perceived performance and prevent duplicate requests. Added disabled styles and original text restoration in `app.js`.
## 2026-05-30 - Added Missing Async Form Loading States
**Learning:** Async forms natively do not provide user feedback or prevent duplicate requests during high-latency network actions. This can lead to frustration and accidental duplicate submissions.
**Action:** Handled async submit buttons by storing their original innerHTML, substituting a loading text, and applying a `disabled=true` state wrapped within a `try...finally` block. Used CSS `.btn:disabled` styling to visually communicate the locked interaction state.
