# ClimaAI free-launch audit — 23 September 2026

The selected first release is a **browser-only weather website on GitHub Pages**, with no payment method or paid API. This document distinguishes code prepared on the local `audit/free-launch-cleanup` branch from the public site. [CODEBASE_CATALOG.csv](CODEBASE_CATALOG.csv) categorizes every other project file; regenerate it with `python3 scripts/generate-codebase-catalog.py` after structural edits.

## Public and local status

The public Pages URL still serves the previous legal/support landing page. The new `docs/index.html` on this branch fetches real current, hourly and seven-day forecasts directly from Open-Meteo. It uses Open-Meteo geocoding for place search and requests browser location only after a click. It has no sign-in, paid tier, AI output, ClimaAI backend, API key or generated forecast. The former `web-demo/` with fake authentication and random weather has been deleted. Privacy, terms and deletion URLs remain at their existing paths, with copy specific to the website. Nothing has been pushed or deployed from this branch.

The Android and iOS apps and FastAPI backend are not live. Mobile release builds still have unconfigured backend URLs. The website does not depend on those components.

## Cost and provider conditions

| Route | Fee/payment method | Status |
| --- | --- | --- |
| GitHub Pages website from this public repo | No fee or payment method for Pages hosting | Selected; local release candidate, pending browser review and publication |
| Direct Open-Meteo forecast and geocoding | Free for non-commercial use, with limits and CC BY 4.0 attribution | Implemented without a key; attribution is visible |
| Standard Cloud Run backend | Billing account required; overages and database can cost money | Excluded from this launch |
| Google Play / Apple App Store | Developer enrollment fees | Excluded from this launch |

The Open-Meteo free API is limited to fewer than 10,000 calls/day, 5,000/hour and 600/minute, may block misuse, and carries no uptime promise. The website should stay free, ad-free and non-commercial on this tier. A public static site cannot centrally enforce a quota across all visitors; traffic must be watched after publication and the provider plan reassessed before scale or commercialization. Official references: [Open-Meteo terms](https://open-meteo.com/en/terms), [forecast API](https://open-meteo.com/en/docs), [geocoding API](https://open-meteo.com/en/docs/geocoding-api), and [GitHub Pages publishing](https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site).

## Source findings and disposition

| Priority | Finding | Disposition |
| --- | --- | --- |
| P0 | The old web prototype made up weather, AI and account responses, including on failed network requests. | Removed it. New site validates a real Open-Meteo response and displays an unavailable state on failure; no synthetic fallback exists. |
| P0 | The old public legal pages claimed multi-source consensus and universal account-free mobile access, which did not match the code. | Rewritten for the browser release. Mobile claims require a separate review before mobile distribution. |
| P1 | Browser search previously called public Nominatim without a cross-user rate-limit plan. | Website now uses Open-Meteo geocoding, with no Nominatim request. Android's separate Nominatim use still needs attention before Android distribution. |
| P1 | The old website presented fake Google login, premium status and AI insights. | Removed with `web-demo/`; the new site contains only current, hourly and daily weather. |
| P1 | Backend pollen returned invented seasonal readings without a key, and the Google response parser used the wrong shape. | Mock fallback removed. Pollen returns 503 with no key, and missing measured indices are rejected. Backend pollen remains outside the website scope. |
| P1 | Backend password recovery logged a reset token and claimed an email was sent, though no delivery existed. | Token creation/logging removed; endpoint now returns 503. Real recovery is required before deploying accounts. |
| P1 | iOS paths, registration payload, 204 deletion handling and an air-quality coding key did not match backend contracts. | Corrected; route-existence check added to CI. Full iOS integration and signing remain unverified. |
| P1 | Production backend defaults allowed weak JWT/CORS settings and AI on by default. | Production validation rejects unsafe settings; AI defaults off. |
| P1 | Backend image installed unused `python-multipart==0.0.6`, `python-dateutil`, Alembic and developer tools. The old multipart pin has a [form-parser advisory](https://github.com/advisories/GHSA-2jv5-9r88-3w3p), though no route used forms. | Removed unused pins and split production from development requirements. Full dependency scanning is still needed before backend deployment. |
| P2 | iOS and Watch icons are absent; backend tests and signed mobile builds cannot be run with the current local toolchain. | Mobile launch remains a separate project. |
| P2 | GitHub has 28 open PRs, including 19 near-duplicate Palette changes. | Review separately; no PRs changed here. |

## Verification and release gate

The site contract tests cover keyless provider URLs, place selection, incomplete responses, HTTP failure and unit conversion. An actual Open-Meteo forecast response for New Delhi matched the client contract (current data, 168 hourly entries, seven daily entries). Chrome locally showed New Delhi, London and Paris forecasts; search selection by keyboard, Celsius/Fahrenheit and all three legal links worked. A phone-width visual check and 320 px overflow check also passed. Static syntax and file-inventory checks pass. Location denial and browser-offline behavior still need final checking. Then publish the reviewed `docs/` files to the existing Pages source and verify the public URL. See [WEBSITE.md](WEBSITE.md).

The full backend suite needs its Python dependencies and Postgres; this Mac has no running Docker daemon and no full Xcode. Prior GitHub CI success applies to the previous `main`, not these local edits. No deployment, push or store submission has occurred.
