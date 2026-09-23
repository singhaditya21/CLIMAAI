# GitHub Pages weather website

The website source is `docs/index.html`, `docs/assets/weather.mjs`, and `docs/assets/weather.css`. `docs/.nojekyll` keeps Pages publication static. `privacy.html`, `terms.html`, and `delete-account.html` retain their existing public URLs. There is no build step, account, ClimaAI API, or secret. The page loads a real New Delhi forecast on arrival so the displayed place is explicit, then accepts a city search or optional browser location. Celsius/Fahrenheit conversion happens in the browser.

## Local preview

From the repository root, run `python3 -m http.server 8080 --directory docs` and open `http://localhost:8080/`. Run `node --test scripts/tests/weather-site.test.mjs` for provider-contract and failure-path checks. The browser needs internet access to fetch Open-Meteo; if the provider is unavailable, the page shows an error instead of fabricated data.

Before publishing, check a narrow mobile viewport and desktop layout, keyboard-only search selection, a city outside India, a place with multiple matching results, Celsius/Fahrenheit, refresh, denied geolocation, network failure, and the three legal links. Inspect the browser console for errors. A local HTTP server is needed for module scripts; opening the HTML as `file://` is not a supported preview.

## Data and cost

The forecast uses `https://api.open-meteo.com/v1/forecast` and place search uses `https://geocoding-api.open-meteo.com/v1/search`. Neither requires an API key for non-commercial use. The free service has [request limits and attribution requirements](https://open-meteo.com/en/terms); this site credits Open-Meteo on the forecast page and in the terms. Search text and selected coordinates go directly from the visitor's browser to Open-Meteo. Location permission is requested only when the visitor clicks “Use my location.” No data is saved in local storage. There are no Google Cloud services or payment-method requirements in this website path.

A public static site has no central request quota. Monitor actual usage and provider policy after publication, and choose another provider arrangement before ads, subscriptions or substantial growth.

## Publication

GitHub Pages is already configured to serve this repository's `docs/` directory. The public root currently hosts an older legal landing page. Once the branch has passed browser review, merge or push the reviewed `docs/` changes to the configured Pages source branch. GitHub Pages will publish `docs/index.html` at `https://singhaditya21.github.io/CLIMAAI/`; the legal pages stay at their existing `.html` URLs. Check the Pages build and public site after publication. No deployment action has been taken from the audit branch.
