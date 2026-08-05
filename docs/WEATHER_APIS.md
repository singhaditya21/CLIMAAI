# Weather data sources

Survey of free and freemium weather APIs, and what ClimaAI actually uses.
Last reviewed 2026-08-05.

## Licensing: resolved while the app ships free — a hard flag-flip condition

**Open-Meteo — the primary source — prohibits commercial use on its free
tier.** Its terms state plainly: *"You may only use the free API services for
non-commercial purposes."*

As shipped, ClimaAI is now non-commercial: `MONETIZATION_ENABLED=false` is
compiled into both Android build types, there are no ads, and nothing can be
purchased. That is the same footing on which Breezy Weather uses Open-Meteo
compliantly. The conflict returns the moment the flag flips, so **any of the
following must happen *before* `MONETIZATION_ENABLED` is set to `true`**, in
rough order of cost:

1. **Buy an Open-Meteo commercial plan.** Standard is 1M calls/month, with
   Professional at 5M and Enterprise above 50M. Pricing is only shown at Stripe
   checkout, so budget it before committing. The switch is already wired on
   both sides: paste the bought key into `OPEN_METEO_API_KEY` (backend env) and
   `openMeteoApiKey` (gradle.properties) and every call moves to the licensed
   `customer-` host with the key attached — full procedure in
   [LICENSING.md](LICENSING.md). `scripts/ci/check-release-config.sh` fails any
   build where `MONETIZATION_ENABLED=true` while the key is empty, so the flip
   cannot happen out of order.
2. **Shift the primary source to one whose free tier permits commercial use.**
   WeatherAPI.com allows commercial use on its free tier at 1M calls/month —
   which is both more generous and more permissive than Open-Meteo's free tier.
3. **Use national services directly.** NWS (US) and MET Norway are public-sector
   and free to use commercially with attribution, but each covers one region.
4. **Apple WeatherKit on iOS.** 500,000 calls/month are included with the Apple
   Developer Program membership the iOS release needs anyway.

## Attribution obligations, and where each is met

Free does not mean attribution-free. What each licence requires and where the
app satisfies it:

- **Open-Meteo (CC BY 4.0)** — credit required wherever the data is used.
  **Met:** the Android Settings screen shows a tappable "Open-Meteo
  (CC BY 4.0)" data-source row linking to open-meteo.com
  (`SettingsScreen.kt`).
- **MET Norway** — requires identifying the application in the `User-Agent`,
  which the client already does.
- **Nominatim / OpenStreetMap (ODbL)** — the [Nominatim usage
  policy](https://operations.osmfoundation.org/policies/nominatim/) and OSM's
  [attribution guidelines](https://osmfoundation.org/wiki/Licence/Attribution_Guidelines)
  require "© OpenStreetMap contributors" wherever OSM-derived data surfaces.
  In this app that surface is location search: **the LocationSwitcher search
  results screen shows "© OpenStreetMap contributors"**
  (`LocationSwitcherScreen.kt` — the screen code is owned by the Android
  side; this file records the obligation so it is not silently dropped in a
  redesign).
- **RainViewer + basemap** — the radar screen credits "Radar from RainViewer"
  in its UI and carries the basemap's own "© OpenStreetMap, © CARTO"
  attribution string (`RadarMapScreen.kt`), which covers the ODbL obligation
  for the map tiles as well.

## What is integrated

| Source | Key | Free tier | Coverage | Notes |
| :--- | :--- | :--- | :--- | :--- |
| Open-Meteo | optional | 10k/day, non-commercial only | Global | Primary. Commercial key switch wired ([LICENSING.md](LICENSING.md)) |
| MET Norway | none | Fair use | Global, best in Nordics | Requires a `User-Agent` |
| NWS | none | "Reasonable use" | US only | Alerts; 400s outside the US |
| 7Timer! | none | Unstated | Global | Astronomy-oriented, coarse |
| DWD (Bright Sky) | none | Unstated | Germany | Third-party DWD wrapper |
| wttr.in | none | Community-run | Global | Cross-check only; small budget |
| OpenWeatherMap | yes | 1M/month, 60/min | Global | |
| WeatherAPI.com | yes | **1M/month, commercial OK** | Global | Also returns air quality |
| Pirate Weather | yes | 20k/month | Global (NOAA) | Minutely precipitation + alerts |
| Weatherbit | yes | 50/day | Global | |
| Storm Glass | yes | 10/day | Marine | Tiny budget; marine only |
| OpenUV | yes | 50/day | Global | UV only |

Every key-gated source returns `None` when its key is blank, so the app degrades
to the no-key sources rather than failing.

## Evaluated and not integrated

- **Tomorrow.io, Visual Crossing, Meteosource, AerisWeather, Meteomatics** —
  all viable, all key-gated, none offering anything the above lack. Adding them
  would increase surface area without adding capability.
- **MSC GeoMet (Environment Canada)** — implemented, then removed. Its
  `climate-stations` collection returns station *metadata*, not observations, so
  it would have shipped as a "weather source" that reports no weather. Canada is
  already covered by Open-Meteo via the GEM model.
- **AccuWeather** — 50 calls/day free, which is too small to be useful, and its
  terms are restrictive for a competing product.
- **Apple WeatherKit** — genuinely worth adding for the iOS client specifically
  (500k/month with the developer programme). Not a backend fit, since it is
  keyed to an Apple team.

## Rate budgets

`SOURCE_DAILY_LIMITS` in `multi_weather_service.py` holds a conservative daily
budget per source, deliberately under each published limit. When a budget is
spent the source is skipped and reported in `metadata.rate_limited_sources`
rather than failing the request. A test asserts every registered provider has a
budget, so a new one cannot silently inherit the generic default.

## Adding a provider

1. Add `async def _fetch_<name>` returning `{"source": ..., "current": {...}}`.
   Return `None` for "not applicable here" or "no key" — never raise.
2. Register it in `all_sources` inside `get_multi_source_weather`.
3. Add a `SOURCE_DAILY_LIMITS` entry, or the budget test fails.
4. Add the key to `config.py` and `.env.example` if it needs one.
5. Add tests to `tests/test_multi_weather_service.py` with a stubbed client.

## Sources

- [Open-Meteo terms](https://open-meteo.com/en/terms)
- [Open-Meteo pricing](https://open-meteo.com/en/pricing)
- [Pirate Weather](https://pirateweather.net/)
- [WeatherAPI.com free tier](https://freeapihub.com/apis/weatherapi)
- [Apple WeatherKit subscriptions](https://developer.apple.com/news/?id=wsx8rd26)
- [wttr.in](https://wttr.in/api)
