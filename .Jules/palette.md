## 2024-05-02 - Form submission target tracking
**Learning:** When adding async button loading states on form submission in vanilla JS, `e.target.querySelector('button[type="submit"]')` can fail or return the wrong element if buttons lack explicit `type="submit"` attributes.
**Action:** Use `e.submitter` to reliably capture the specific button that triggered the submission event for applying `aria-busy` and `disabled=true` states.
