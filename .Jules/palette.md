
## 2026-06-03 - Form Submitters in Vanilla JS
**Learning:** In vanilla JavaScript, accessing the button that triggered a form submission using `e.target.querySelector('button[type="submit"]')` can be unreliable if elements intercept pointer events or multiple buttons exist.
**Action:** Use the native `e.submitter` property on the `submit` event to precisely and reliably capture the button that triggered the action, ensuring accurate DOM manipulation (like adding loading states) even if the button lacks a `type="submit"` attribute. Remember to also use a `finally` block to guarantee the state reset happens even if the simulated async operation fails.
