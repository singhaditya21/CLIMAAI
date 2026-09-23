# ClimaAI roadmap

Current status is documented in [the free-launch audit](docs/CODEBASE_AUDIT.md).
The [file catalog](docs/CODEBASE_CATALOG.csv) assigns every part of the repo to
the website release candidate, a mobile companion, a prototype, or a deferred feature.

## Phase 1: free GitHub Pages weather website

The selected first release is a real weather website on GitHub Pages. It uses
Open-Meteo directly, needs no payment method, and has no account, paid tier,
OpenAI, or ClimaAI backend. The current Pages root remains the old legal landing
page until this branch is published to the Pages source. See
[the website release guide](docs/WEBSITE.md).

- [x] Replace simulated browser weather with direct, live provider requests.
- [x] Remove fake sign-in, premium, AI, and subscription flows from the website.
- [x] Keep the existing privacy, terms and deletion URLs, with website-accurate copy.
- [x] Add provider attribution, explicit failure states and browser-only location choice.
- [x] Check live city selection, keyboard search, temperature conversion, legal
  links and narrow mobile layout in a local Chrome preview.
- [ ] Review denied geolocation and browser-offline behavior. Automated tests
  already cover failed provider responses.
- [ ] Publish the reviewed `docs/` changes to the Pages source branch and verify
  the public URL and legal links after deployment.

## What CI currently proves

The latest `main` Mobile workflow completed Android debug build/lint/tests,
Android emulator smoke, iOS simulator build/tests and the Android API contract
check. It did **not** produce a store-ready signed release: the strict Android
release-config gate failed because the backend URL is empty. The Deploy to
Production workflow built and tested but skipped deployment because cloud
credentials are not configured. GitHub Pages currently serves the prior
`docs/` legal landing page; the new website is local to this branch. The repo
has no GitHub release.

The iOS path checker added on this branch compares literal client paths with
FastAPI route declarations. It does not verify JSON response models or replace
an end-to-end test against a deployed API.

## Deferred work

Paid subscriptions and OpenAI-powered insights are outside the free release.
Android monetization is compiled off, while iOS still exposes paid UI and must
be corrected before its first release. The Node payment webhook service and
store billing code remain classified as deferred in the catalog. The older
[pre-launch audit](docs/PRE_LAUNCH_AUDIT.md) is historical; some of its findings
were fixed in August 2026 and should not be treated as current release status.
