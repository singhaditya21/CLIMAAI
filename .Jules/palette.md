## 2024-05-17 - Async Form Submission State

**Learning:** When dealing with native HTML form submissions triggered asynchronously, relying purely on the submit event isn't enough because the exact button clicked (`e.submitter`) is only available immediately. If there are loading state resets after `await`, using `e.submitter.innerHTML` ensures text and icons are restored correctly, and a `try/finally` block guarantees restoration even if the async call fails.
**Action:** Always capture `e.submitter` state (original text/icons) immediately at the start of the submit handler, show a clear loading indicator, and restore the original state in a `finally` block to ensure a robust user experience during async API calls.
