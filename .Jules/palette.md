
## 2024-11-20 - Prevent Duplicate Form Submissions and Add Loading States
**Learning:** Using `e.submitter` in vanilla JavaScript submit events reliably captures the exact submit button that was clicked, even without a `type="submit"` attribute on all elements. Disabling this button (`submitBtn.disabled = true`) and setting `aria-busy="true"` explicitly communicates loading states to screen readers while simultaneously preventing duplicate API calls.
**Action:** Always prefer `e.submitter` over `querySelector` when accessing form buttons in `submit` event listeners to build accessible and resilient async loading states in vanilla JS applications.
