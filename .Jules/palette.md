## 2024-06-25 - Async Button Loading States

**Learning:** Forms submitting async requests without visual feedback cause users to wonder if the click registered, leading to multiple clicks or confusion. A visual loading state (text and disabled attribute) prevents this.
**Action:** Always add visual loading indicators to form submit buttons for async actions. Use `e.submitter` on the form submit event to capture the button, applying changes before the async operation and resetting in a `finally` block to guarantee state restoration regardless of success/failure.
