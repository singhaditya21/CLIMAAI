## 2026-07-02 - Adding loading states to async auth buttons
**Learning:** Auth forms in vanilla JS apps often lack automatic button disabling and visual loading states during API requests, leading to potential duplicate submissions and poor feedback.
**Action:** Consistently use e.submitter and try/finally blocks to manage button disabled states, opacity, and loading text during async network requests.
