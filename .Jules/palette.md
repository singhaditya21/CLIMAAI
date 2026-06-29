
## 2024-05-14 - Add loading states to authentication buttons
**Learning:** Users can double-submit forms or think the application has frozen if there is no visual feedback on async operations like authentication.
**Action:** Always add loading states (disable button, update text/icon, adjust opacity) to buttons triggering asynchronous actions, using a try/finally block to reliably restore the button state.
