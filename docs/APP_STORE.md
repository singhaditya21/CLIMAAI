# Store Listing for ClimaAI

Every claim in this file must be true of the build being submitted. The previous
version of this listing sold AI insights (off in the shipped config), minute-level
rain alerts (never implemented), and a premium tier (monetization is compiled out
— `MONETIZATION_ENABLED=false`). If a feature is behind a flag that is off, it
does not belong here.

## App Information

**Name**: ClimaAI

**Subtitle / tagline**: Forecasts that tell you how sure they are

**Category**: Weather

**Price**: Free. No ads, no in-app purchases, no subscriptions.

## Short Description (Google Play, 80 characters max)

Multi-source weather with an honest confidence readout. Free, no ads or account.

## Full Description

**Most weather apps show you one model's answer. ClimaAI shows you whether the
models agree.**

ClimaAI queries multiple independent forecast providers — Open-Meteo, MET Norway,
the US National Weather Service and others — and compares their answers. When
they agree, you get a confident forecast. When they don't, ClimaAI says so,
plainly: the median, the range, and a high / medium / low confidence rating for
temperature, precipitation and wind. Other apps show you one model, or let you
pick one — ClimaAI tells you how much they actually agree.

**Forecasts**
• Current conditions, hourly and daily forecasts
• Cross-source consensus card: median, min–max range, and a confidence rating
  computed from how much the sources disagree
• The consensus is plain arithmetic over real model output — never invented,
  and hidden entirely when fewer than two sources respond

**Radar**
• Live precipitation radar with animated recent frames (RainViewer)

**Air Quality**
• Air Quality Index with pollutant breakdown (PM2.5, PM10, NO₂, O₃, SO₂, CO)
• UV index

**Pollen — Europe only**
• Species-level pollen counts (alder, birch, grass, mugwort, olive, ragweed)
• Pollen data comes from CAMS, whose coverage is Europe. Outside Europe the
  pollen screen shows "no data for this location" — it will not guess

**Widgets & Wear OS**
• Home-screen widgets showing your location's latest synced reading
• Wear OS app, tile and complications mirroring the phone's most recent data
• If nothing has synced yet, widgets and the watch show an explicit no-data
  state rather than a plausible-looking number

**Notifications (optional, off by default)**
• Daily weather summary at a time you choose
• Rain and severe-weather notifications

**Free means free**
• Every feature included
• No ads, no trackers, no third-party analytics SDKs
• No account required — an optional account only syncs your saved locations,
  and can be deleted in-app at any time

## Keywords

weather, forecast, multi-source, forecast confidence, ensemble, radar,
air quality, pollen, widgets, wear os

## What's New (1.0.0)

First release: multi-source forecasts with a per-variable confidence readout,
precipitation radar, air quality, Europe-only pollen counts, home-screen
widgets, and a Wear OS app with tile and complications.

## URLs

- **Privacy Policy**: https://singhaditya21.github.io/CLIMAAI/privacy.html
- **Terms of Service**: https://singhaditya21.github.io/CLIMAAI/terms.html
- **Account Deletion**: https://singhaditya21.github.io/CLIMAAI/delete-account.html
- **Support contact**: singhaditya21@gmail.com

## Age Rating

Everyone / 4+ (no objectionable content).

## Screenshots (6–8, phone)

Screenshots must be real captures from the submitted build — no mockups, no
staged data.

1. **Home** — current conditions with the consensus confidence card visible
2. **Consensus detail** — a location where sources genuinely disagree, showing
   the range and a "low confidence" rating (this is the differentiator; lead
   with it)
3. **Hourly / daily forecast**
4. **Radar** — animated precipitation frames
5. **Air quality** — AQI gauge and pollutant breakdown
6. **Pollen** — a European location with real counts; optionally a second
   capture showing the honest no-data state elsewhere
7. **Widgets** — on a home screen, after a sync, showing real data
8. **Wear OS** — tile or complication mirroring the phone reading

## Review Information

**Contact**: singhaditya21@gmail.com

**Notes for reviewers:**

- The app works fully without an account. There is nothing to purchase.
- Location permission is requested to fetch forecasts for the device's
  position; a location can also be searched manually, in which case the
  permission can be denied.
- Pollen data is Europe-only (CAMS coverage); outside Europe the screen shows
  a no-data state by design.
- Account creation is optional (syncs saved locations). Account deletion is
  in-app: Settings → Account → Delete account.

## Data Sources & Attribution

- Weather data: Open-Meteo (CC BY 4.0 — attribution shown in-app under
  Settings), MET Norway, US National Weather Service, and other sources listed
  in [WEATHER_APIS.md](WEATHER_APIS.md)
- Radar tiles: RainViewer
- Location search: Nominatim / © OpenStreetMap contributors (ODbL)

## Copyright

© 2026 ClimaAI. All rights reserved.
