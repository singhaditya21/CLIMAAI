// Browser-only weather site. No backend, account, secret, or generated forecast.
const FORECAST_ENDPOINT = 'https://api.open-meteo.com/v1/forecast';
const SEARCH_ENDPOINT = 'https://geocoding-api.open-meteo.com/v1/search';
const START_LOCATION = { latitude: 28.6139, longitude: 77.2090, label: 'New Delhi, India' };

export function forecastUrl({ latitude, longitude }) {
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude) ||
      Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
    throw new Error('Invalid location coordinates');
  }
  const url = new URL(FORECAST_ENDPOINT);
  url.search = new URLSearchParams({
    latitude: String(latitude),
    longitude: String(longitude),
    current: 'temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,is_day',
    hourly: 'temperature_2m,precipitation_probability,weather_code,is_day',
    daily: 'temperature_2m_max,temperature_2m_min,weather_code',
    timezone: 'auto',
    forecast_days: '7'
  }).toString();
  return url;
}

export function searchUrl(query) {
  const name = query.trim();
  if (name.length < 2 || name.length > 100) throw new Error('Enter at least two characters');
  const url = new URL(SEARCH_ENDPOINT);
  url.search = new URLSearchParams({ name, count: '6', language: 'en', format: 'json' }).toString();
  return url;
}

async function requestJson(url, signal, fetcher = fetch) {
  const response = await fetcher(url, { signal, headers: { Accept: 'application/json' } });
  if (!response.ok) throw new Error(`Weather provider returned HTTP ${response.status}`);
  return response.json();
}

export async function searchPlaces(query, signal, fetcher = fetch) {
  const data = await requestJson(searchUrl(query), signal, fetcher);
  if (!Array.isArray(data.results)) return [];
  return data.results.filter(place =>
    Number.isFinite(place.latitude) && Number.isFinite(place.longitude) &&
    typeof place.name === 'string'
  ).map(place => ({
    latitude: place.latitude,
    longitude: place.longitude,
    label: [place.name, place.admin1, place.country].filter(Boolean).join(', ')
  }));
}

function validSeries(group, keys, minimum) {
  return group && keys.every(key => Array.isArray(group[key]) && group[key].length >= minimum);
}

export function validateForecast(data) {
  const current = data?.current;
  if (!current || typeof current.time !== 'string' ||
      !Number.isFinite(current.temperature_2m) ||
      !Number.isFinite(current.weather_code) ||
      !validSeries(data.hourly, ['time', 'temperature_2m', 'precipitation_probability', 'weather_code', 'is_day'], 24) ||
      !validSeries(data.daily, ['time', 'temperature_2m_max', 'temperature_2m_min', 'weather_code'], 7)) {
    throw new Error('Incomplete forecast from weather provider');
  }
  const firstHour = data.hourly.time.findIndex(time => time >= current.time.slice(0, 13));
  if (firstHour < 0 || data.hourly.time.length - firstHour < 24) {
    throw new Error('The next 24 hours are unavailable');
  }
  return { current, hourly: data.hourly, daily: data.daily, firstHour, timezone: data.timezone };
}

export async function loadForecast(location, signal, fetcher = fetch) {
  return validateForecast(await requestJson(forecastUrl(location), signal, fetcher));
}

export function weatherCondition(code, isDay = true) {
  if (code === 0) return { label: 'Clear sky', icon: isDay ? '☀️' : '🌙' };
  if ([1, 2].includes(code)) return { label: 'Partly cloudy', icon: isDay ? '🌤️' : '☁️' };
  if (code === 3) return { label: 'Overcast', icon: '☁️' };
  if ([45, 48].includes(code)) return { label: 'Fog', icon: '🌫️' };
  if ([51, 53, 55, 56, 57].includes(code)) return { label: 'Drizzle', icon: '🌦️' };
  if ([61, 63, 65, 66, 67, 80, 81, 82].includes(code)) return { label: 'Rain', icon: '🌧️' };
  if ([71, 73, 75, 77, 85, 86].includes(code)) return { label: 'Snow', icon: '❄️' };
  if ([95, 96, 99].includes(code)) return { label: 'Thunderstorm', icon: '⛈️' };
  return { label: 'Weather conditions', icon: '🌡️' };
}

export function temperature(value, unit) {
  if (!Number.isFinite(value)) return '—';
  return `${Math.round(unit === 'F' ? value * 9 / 5 + 32 : value)}°`;
}

function node(tag, className, content) {
  const element = document.createElement(tag);
  if (className) element.className = className;
  if (content !== undefined) element.textContent = content;
  return element;
}

function dayLabel(date, index) {
  if (index === 0) return 'Today';
  if (index === 1) return 'Tomorrow';
  return new Intl.DateTimeFormat('en', { weekday: 'long' }).format(new Date(`${date}T12:00:00`));
}

function hourLabel(time, index) {
  if (index === 0) return 'Now';
  const hour = Number(time.slice(11, 13));
  return `${hour % 12 || 12}${hour < 12 ? 'am' : 'pm'}`;
}

function createWeatherSite() {
  const get = id => document.getElementById(id);
  const state = { location: START_LOCATION, forecast: null, unit: 'C', forecastController: null, searchController: null };

  function setStatus(message) { get('status').textContent = message; }
  function setError(message) {
    get('weather-content').hidden = true;
    get('error-panel').hidden = false;
    get('error-message').textContent = message;
    setStatus('No current forecast is displayed.');
  }
  function detail(label, value) {
    const box = node('div', 'detail');
    box.append(node('span', 'detail-label', label), node('strong', 'detail-value', value));
    return box;
  }
  function render() {
    const { current, hourly, daily, firstHour } = state.forecast;
    const currentCondition = weatherCondition(current.weather_code, current.is_day === 1);
    get('current-heading').textContent = state.location.label;
    get('observation-time').textContent = `Provider observation: ${current.time.replace('T', ' ')} (local time)`;
    get('current-icon').textContent = currentCondition.icon;
    get('current-temp').textContent = temperature(current.temperature_2m, state.unit);
    get('current-description').textContent = currentCondition.label;
    get('feels-like').textContent = `Feels like ${temperature(current.apparent_temperature, state.unit)}`;
    get('current-details').replaceChildren(
      detail('High / low', `${temperature(daily.temperature_2m_max[0], state.unit)} / ${temperature(daily.temperature_2m_min[0], state.unit)}`),
      detail('Humidity', Number.isFinite(current.relative_humidity_2m) ? `${Math.round(current.relative_humidity_2m)}%` : '—'),
      detail('Wind speed', Number.isFinite(current.wind_speed_10m) ? `${Math.round(current.wind_speed_10m)} km/h` : '—'),
      detail('Rain chance next hour', Number.isFinite(hourly.precipitation_probability[firstHour]) ? `${Math.round(hourly.precipitation_probability[firstHour])}%` : '—')
    );

    const hours = [];
    for (let offset = 0; offset < 24; offset++) {
      const index = firstHour + offset;
      const card = node('div', 'hour-card');
      const condition = weatherCondition(hourly.weather_code[index], hourly.is_day[index] === 1);
      card.append(
        node('span', 'time', hourLabel(hourly.time[index], offset)),
        node('span', 'icon', condition.icon),
        node('span', 'temp', temperature(hourly.temperature_2m[index], state.unit)),
        node('span', 'rain', Number.isFinite(hourly.precipitation_probability[index]) ? `${Math.round(hourly.precipitation_probability[index])}% rain` : 'Rain —')
      );
      card.setAttribute('aria-label', `${hourLabel(hourly.time[index], offset)}, ${condition.label}, ${temperature(hourly.temperature_2m[index], state.unit)}`);
      hours.push(card);
    }
    get('hourly-list').replaceChildren(...hours);

    const days = [];
    for (let index = 0; index < 7; index++) {
      const row = node('div', 'daily-row');
      const condition = weatherCondition(daily.weather_code[index]);
      row.append(
        node('span', 'day-name', dayLabel(daily.time[index], index)),
        node('span', 'day-icon', condition.icon),
        node('span', 'day-description', condition.label)
      );
      const temps = node('span', 'day-temps', temperature(daily.temperature_2m_max[index], state.unit));
      temps.append(node('span', '', temperature(daily.temperature_2m_min[index], state.unit)));
      row.append(temps);
      days.push(row);
    }
    get('daily-list').replaceChildren(...days);
    get('weather-content').hidden = false;
    get('error-panel').hidden = true;
    setStatus(`Live forecast for ${state.location.label} · Source: Open-Meteo · Times shown in the forecast location's local time.`);
  }

  async function showForecast(location) {
    state.forecastController?.abort();
    const controller = new AbortController();
    state.forecastController = controller;
    state.location = location;
    state.forecast = null;
    get('weather-content').hidden = true;
    get('error-panel').hidden = true;
    setStatus(`Loading the latest forecast for ${location.label}…`);
    const timeout = setTimeout(() => controller.abort(), 12000);
    try {
      state.forecast = await loadForecast(location, controller.signal);
      if (controller === state.forecastController) render();
    } catch (error) {
      if (controller !== state.forecastController) return;
      setError(error.name === 'AbortError' ? 'The request timed out. Try again.' : 'The live forecast could not be loaded. Check your connection or try another location.');
    } finally {
      clearTimeout(timeout);
    }
  }

  get('place-form').addEventListener('submit', async event => {
    event.preventDefault();
    state.searchController?.abort();
    const controller = new AbortController();
    state.searchController = controller;
    const results = get('search-results');
    results.hidden = false;
    results.replaceChildren(node('p', '', 'Searching places…'));
    const timeout = setTimeout(() => controller.abort(), 10000);
    try {
      const places = await searchPlaces(get('place-query').value, controller.signal);
      if (controller !== state.searchController) return;
      if (!places.length) {
        results.replaceChildren(node('p', '', 'No matching places found. Try a more specific name.'));
        return;
      }
      results.replaceChildren(...places.map(place => {
        const button = node('button', 'result-button', place.label);
        button.type = 'button';
        button.addEventListener('click', () => {
          results.hidden = true;
          get('place-query').value = place.label;
          showForecast(place).then(() => {
            if (!get('weather-content').hidden) get('current-heading').focus();
          });
        });
        return button;
      }));
    } catch (error) {
      if (controller === state.searchController) results.replaceChildren(node('p', '', 'Place search is unavailable. Try again later.'));
    } finally {
      clearTimeout(timeout);
    }
  });

  get('place-query').addEventListener('keydown', event => {
    if (event.key === 'Escape') get('search-results').hidden = true;
  });

  get('use-location').addEventListener('click', () => {
    if (!navigator.geolocation) { setStatus('This browser does not support location sharing. Search for a place instead.'); return; }
    setStatus('Waiting for your location permission…');
    navigator.geolocation.getCurrentPosition(
      position => showForecast({
        latitude: position.coords.latitude,
        longitude: position.coords.longitude,
        label: `Your location (${position.coords.latitude.toFixed(2)}°, ${position.coords.longitude.toFixed(2)}°)`
      }),
      () => setStatus('Location was not shared. Search for a place instead.'),
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 300000 }
    );
  });
  get('refresh').addEventListener('click', () => showForecast(state.location));
  get('retry').addEventListener('click', () => showForecast(state.location));
  for (const unit of ['C', 'F']) {
    get(`unit-${unit.toLowerCase()}`).addEventListener('click', () => {
      state.unit = unit;
      for (const choice of ['C', 'F']) {
        const button = get(`unit-${choice.toLowerCase()}`);
        button.classList.toggle('active', unit === choice);
        button.setAttribute('aria-pressed', String(unit === choice));
      }
      if (state.forecast) render();
    });
  }
  showForecast(START_LOCATION);
}

if (typeof document !== 'undefined') createWeatherSite();
