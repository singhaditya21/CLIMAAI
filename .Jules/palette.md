## 2026-08-18 - Async Action Feedback
**Learning:** Found that long-running authentication forms (like Google Sign-In and local Email login) lacked immediate visual feedback, causing user uncertainty and potential duplicate submissions when clicked multiple times.
**Action:** When making async calls in vanilla JavaScript forms, always apply a disabled state, visual opacity cues, and a "loading..." label to the triggering button. Ensure it is wrapped in a `finally` block to guarantee state restoration.
