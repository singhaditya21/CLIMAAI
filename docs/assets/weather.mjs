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
  if (code === 0) return { label: 'Clear sky', icon: isDay ? '☀️' : '🌙', theme: isDay ? 'clear-day' : 'clear-night' };
  if ([1, 2].includes(code)) return { label: 'Partly cloudy', icon: isDay ? '🌤️' : '☁️', theme: isDay ? 'partly-cloudy-day' : 'cloudy-night' };
  if (code === 3) return { label: 'Overcast', icon: '☁️', theme: isDay ? 'overcast' : 'cloudy-night' };
  if ([45, 48].includes(code)) return { label: 'Fog', icon: '🌫️', theme: 'fog' };
  if ([51, 53, 55, 56, 57].includes(code)) return { label: 'Drizzle', icon: '🌦️', theme: 'drizzle' };
  if ([61, 63, 65, 66, 67, 80, 81, 82].includes(code)) return { label: 'Rain', icon: '🌧️', theme: 'rain' };
  if ([71, 73, 75, 77, 85, 86].includes(code)) return { label: 'Snow', icon: '❄️', theme: 'snow' };
  if ([95, 96, 99].includes(code)) return { label: 'Thunderstorm', icon: '⛈️', theme: 'thunderstorm' };
  return { label: 'Weather conditions', icon: '🌡️', theme: 'default' };
}

export function weatherSvg(code, isDay = true) {
  const { theme } = weatherCondition(code, isDay);
  const cloud = '<path d="M20 44h25a10 10 0 0 0 1.2-19.9A14 14 0 0 0 19 21a11 11 0 0 0 1 23Z" fill="#dce9f2" stroke="#9ab5c7" stroke-width="2" stroke-linejoin="round"/>';
  if (theme === 'clear-day') {
    return '<svg viewBox="0 0 64 64" aria-hidden="true"><circle cx="32" cy="32" r="14" fill="#f7b733" stroke="#e38d13" stroke-width="2"/><g stroke="#f7b733" stroke-width="3" stroke-linecap="round"><line x1="32" y1="7" x2="32" y2="13"/><line x1="32" y1="51" x2="32" y2="57"/><line x1="7" y1="32" x2="13" y2="32"/><line x1="51" y1="32" x2="57" y2="32"/><line x1="14.3" y1="14.3" x2="18.6" y2="18.6"/><line x1="45.4" y1="45.4" x2="49.7" y2="49.7"/><line x1="49.7" y1="14.3" x2="45.4" y2="18.6"/><line x1="18.6" y1="45.4" x2="14.3" y2="49.7"/></g></svg>';
  }
  if (theme === 'clear-night') {
    return '<svg viewBox="0 0 64 64" aria-hidden="true"><path d="M41 14a18 18 0 1 0 9 33.4A16 16 0 0 1 41 14Z" fill="#fcd34d" stroke="#f59e0b" stroke-width="2" stroke-linejoin="round"/><circle cx="19" cy="18" r="1.8" fill="#fde68a"/><circle cx="48" cy="20" r="1.4" fill="#fde68a"/></svg>';
  }
  if (theme === 'partly-cloudy-day') {
    return `<svg viewBox="0 0 64 64" aria-hidden="true"><circle cx="24" cy="22" r="10" fill="#f7b733" stroke="#e38d13" stroke-width="1.8"/>${cloud}</svg>`;
  }
  if (theme === 'drizzle' || theme === 'rain') {
    return `<svg viewBox="0 0 64 64" aria-hidden="true">${cloud}<g stroke="#2980b9" stroke-width="2.6" stroke-linecap="round"><line x1="23" y1="49" x2="20" y2="57"/><line x1="33" y1="49" x2="30" y2="57"/><line x1="43" y1="49" x2="40" y2="57"/></g></svg>`;
  }
  if (theme === 'snow') {
    return `<svg viewBox="0 0 64 64" aria-hidden="true">${cloud}<g fill="#5fa8d3"><circle cx="23" cy="53" r="2.5"/><circle cx="33" cy="55" r="2.5"/><circle cx="43" cy="53" r="2.5"/></g></svg>`;
  }
  if (theme === 'thunderstorm') {
    return `<svg viewBox="0 0 64 64" aria-hidden="true">${cloud}<polygon points="33,42 25,53 32,53 29,61 41,49 34,49" fill="#f59e0b" stroke="#d97706" stroke-width="1.2" stroke-linejoin="round"/></svg>`;
  }
  if (theme === 'fog') {
    return `<svg viewBox="0 0 64 64" aria-hidden="true">${cloud}<g stroke="#8da6b8" stroke-width="2.6" stroke-linecap="round"><line x1="16" y1="50" x2="48" y2="50"/><line x1="20" y1="56" x2="44" y2="56"/></g></svg>`;
  }
  return `<svg viewBox="0 0 64 64" aria-hidden="true">${cloud}</svg>`;
}

export function temperature(value, unit) {
  if (!Number.isFinite(value)) return '—';
  return `${Math.round(unit === 'F' ? value * 9 / 5 + 32 : value)}°`;
}

export function temperatureRange(min, max, weekMin, weekMax) {
  if (![min, max, weekMin, weekMax].every(Number.isFinite) || weekMax <= weekMin) {
    return { startPercent: 0, widthPercent: 100 };
  }
  const span = weekMax - weekMin;
  const startPercent = Math.max(0, Math.min(100, ((min - weekMin) / span) * 100));
  const endPercent = Math.max(startPercent, Math.min(100, ((max - weekMin) / span) * 100));
  return {
    startPercent: Math.round(startPercent * 10) / 10,
    widthPercent: Math.max(8, Math.round((endPercent - startPercent) * 10) / 10)
  };
}

export function buildHourlyChartSvg(temps, unit = 'C') {
  const values = temps.slice(0, 24).map(v => (unit === 'F' ? v * 9 / 5 + 32 : v));
  if (values.length < 2 || !values.every(Number.isFinite)) return '';
  const width = 720;
  const height = 86;
  const padX = 22;
  const padTop = 22;
  const padBottom = 14;
  const minVal = Math.min(...values);
  const maxVal = Math.max(...values);
  const range = Math.max(1, maxVal - minVal);
  const stepX = (width - padX * 2) / (values.length - 1);

  const pts = values.map((v, i) => ({
    x: Math.round((padX + i * stepX) * 10) / 10,
    y: Math.round((padTop + (1 - (v - minVal) / range) * (height - padTop - padBottom)) * 10) / 10,
    v: Math.round(v)
  }));

  const linePath = pts.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x},${p.y}`).join(' ');
  const areaPath = `${linePath} L${pts[pts.length - 1].x},${height} L${pts[0].x},${height} Z`;
  const maxIdx = values.indexOf(maxVal);
  const minIdx = values.indexOf(minVal);
  const maxPt = pts[maxIdx];
  const minPt = pts[minIdx];

  return `<svg viewBox="0 0 ${width} ${height}" role="presentation">
    <defs>
      <linearGradient id="tempArea" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#146ca4" stop-opacity="0.22"/>
        <stop offset="100%" stop-color="#146ca4" stop-opacity="0.0"/>
      </linearGradient>
    </defs>
    <path d="${areaPath}" fill="url(#tempArea)"/>
    <path d="${linePath}" fill="none" stroke="#105f93" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
    <circle cx="${maxPt.x}" cy="${maxPt.y}" r="3.5" fill="#d07628"/>
    <text x="${maxPt.x}" y="${Math.max(12, maxPt.y - 7)}" text-anchor="middle" font-size="11" font-weight="700" fill="#0f253e">${maxPt.v}°</text>
    <circle cx="${minPt.x}" cy="${minPt.y}" r="3.5" fill="#105f93"/>
  </svg>`;
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
    get('weather-content').removeAttribute('data-loading');
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
    const currentCard = get('current-card');
    if (currentCard) currentCard.setAttribute('data-condition', currentCondition.theme);
    document.body.dataset.condition = currentCondition.theme;

    get('current-heading').textContent = state.location.label;
    get('observation-time').textContent = `Provider observation: ${current.time.replace('T', ' ')} (local time)`;
    get('current-icon').innerHTML = weatherSvg(current.weather_code, current.is_day === 1);
    get('current-temp').textContent = temperature(current.temperature_2m, state.unit);
    get('current-description').textContent = currentCondition.label;
    get('feels-like').textContent = `Feels like ${temperature(current.apparent_temperature, state.unit)}`;
    get('current-details').replaceChildren(
      detail('High / low', `${temperature(daily.temperature_2m_max[0], state.unit)} / ${temperature(daily.temperature_2m_min[0], state.unit)}`),
      detail('Humidity', Number.isFinite(current.relative_humidity_2m) ? `${Math.round(current.relative_humidity_2m)}%` : '—'),
      detail('Wind speed', Number.isFinite(current.wind_speed_10m) ? `${Math.round(current.wind_speed_10m)} km/h` : '—'),
      detail('Rain chance next hour', Number.isFinite(hourly.precipitation_probability[firstHour]) ? `${Math.round(hourly.precipitation_probability[firstHour])}%` : '—')
    );

    const chartBox = get('hourly-chart');
    if (chartBox) {
      chartBox.innerHTML = buildHourlyChartSvg(hourly.temperature_2m.slice(firstHour, firstHour + 24), state.unit);
    }

    const hours = [];
    for (let offset = 0; offset < 24; offset++) {
      const index = firstHour + offset;
      const card = node('div', 'hour-card');
      const isHourDay = hourly.is_day[index] === 1;
      const condition = weatherCondition(hourly.weather_code[index], isHourDay);
      const iconBox = node('span', 'icon');
      iconBox.innerHTML = weatherSvg(hourly.weather_code[index], isHourDay);
      card.append(
        node('span', 'time', hourLabel(hourly.time[index], offset)),
        iconBox,
        node('span', 'temp', temperature(hourly.temperature_2m[index], state.unit)),
        node('span', 'rain', Number.isFinite(hourly.precipitation_probability[index]) ? `${Math.round(hourly.precipitation_probability[index])}% rain` : 'Rain —')
      );
      card.setAttribute('aria-label', `${hourLabel(hourly.time[index], offset)}, ${condition.label}, ${temperature(hourly.temperature_2m[index], state.unit)}`);
      hours.push(card);
    }
    get('hourly-list').replaceChildren(...hours);

    const weekMin = Math.min(...daily.temperature_2m_min.slice(0, 7));
    const weekMax = Math.max(...daily.temperature_2m_max.slice(0, 7));
    const days = [];
    for (let index = 0; index < 7; index++) {
      const row = node('div', 'daily-row');
      const condition = weatherCondition(daily.weather_code[index], true);
      const dayIcon = node('span', 'day-icon');
      dayIcon.innerHTML = weatherSvg(daily.weather_code[index], true);
      row.append(
        node('span', 'day-name', dayLabel(daily.time[index], index)),
        dayIcon,
        node('span', 'day-description', condition.label)
      );
      const temps = node('span', 'day-temps');
      const minSpan = node('span', 'temp-min', temperature(daily.temperature_2m_min[index], state.unit));
      const track = node('span', 'temp-range-track');
      const fill = node('span', 'temp-range-fill');
      const { startPercent, widthPercent } = temperatureRange(
        daily.temperature_2m_min[index],
        daily.temperature_2m_max[index],
        weekMin,
        weekMax
      );
      fill.style.left = `${startPercent}%`;
      fill.style.width = `${widthPercent}%`;
      track.append(fill);
      const maxSpan = node('span', 'temp-max', temperature(daily.temperature_2m_max[index], state.unit));
      temps.append(minSpan, track, maxSpan);
      row.append(temps);
      days.push(row);
    }
    get('daily-list').replaceChildren(...days);
    get('weather-content').hidden = false;
    get('weather-content').removeAttribute('data-loading');
    get('error-panel').hidden = true;
    setStatus(`Live forecast for ${state.location.label} · Source: Open-Meteo · Times shown in the forecast location's local time.`);
  }

  async function showForecast(location) {
    state.forecastController?.abort();
    const controller = new AbortController();
    state.forecastController = controller;
    state.location = location;
    if (state.forecast) {
      get('weather-content').setAttribute('data-loading', 'true');
    } else {
      get('weather-content').hidden = true;
    }
    get('error-panel').hidden = true;
    setStatus(`Loading the latest forecast for ${location.label}…`);
    const timeout = setTimeout(() => controller.abort(), 12000);
    try {
      state.forecast = await loadForecast(location, controller.signal);
      if (controller === state.forecastController) render();
    } catch (error) {
      if (controller !== state.forecastController) return;
      state.forecast = null;
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
