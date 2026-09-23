import test from 'node:test';
import assert from 'node:assert/strict';
import {
  forecastUrl, searchUrl, searchPlaces, validateForecast, loadForecast,
  weatherCondition, temperature
} from '../../docs/assets/weather.mjs';

function sampleForecast() {
  const start = Date.parse('2026-09-23T00:00:00Z');
  const hours = Array.from({ length: 168 }, (_, i) => new Date(start + i * 3600000).toISOString().slice(0, 16));
  return {
    timezone: 'Asia/Kolkata',
    current: { time: '2026-09-23T12:15', temperature_2m: 29, weather_code: 1 },
    hourly: {
      time: hours,
      temperature_2m: hours.map(() => 28),
      precipitation_probability: hours.map(() => null),
      weather_code: hours.map(() => 1),
      is_day: hours.map(() => 1)
    },
    daily: {
      time: Array.from({ length: 7 }, (_, i) => new Date(start + i * 86400000).toISOString().slice(0, 10)),
      temperature_2m_max: Array(7).fill(31),
      temperature_2m_min: Array(7).fill(22),
      weather_code: Array(7).fill(1)
    }
  };
}

test('forecast request uses the public, keyless API and local forecast time', () => {
  const url = forecastUrl({ latitude: 28.61, longitude: 77.2 });
  assert.equal(url.origin, 'https://api.open-meteo.com');
  assert.equal(url.searchParams.get('timezone'), 'auto');
  assert.equal(url.searchParams.get('forecast_days'), '7');
  assert.match(url.searchParams.get('hourly'), /is_day/);
  assert.equal(url.searchParams.has('apikey'), false);
  assert.throws(() => forecastUrl({ latitude: 100, longitude: 77.2 }), /Invalid/);
});

test('place search sends typed text to Open-Meteo and preserves the chosen coordinates', async () => {
  assert.equal(searchUrl(' Delhi ').searchParams.get('name'), 'Delhi');
  const places = await searchPlaces('Delhi', undefined, async () => ({
    ok: true,
    json: async () => ({ results: [
      { name: 'Delhi', admin1: 'Delhi', country: 'India', latitude: 28.6, longitude: 77.2 },
      { name: 'Broken', latitude: null, longitude: 77.2 }
    ] })
  }));
  assert.deepEqual(places, [{ label: 'Delhi, Delhi, India', latitude: 28.6, longitude: 77.2 }]);
});

test('incomplete and failed provider responses never become a forecast', async () => {
  const data = sampleForecast();
  assert.equal(validateForecast(data).firstHour, 12);
  data.daily.temperature_2m_max = [];
  assert.throws(() => validateForecast(data), /Incomplete forecast/);
  await assert.rejects(
    loadForecast({ latitude: 28.6, longitude: 77.2 }, undefined, async () => ({ ok: false, status: 503 })),
    /HTTP 503/
  );
});

test('weather labels and unit conversion are honest about missing values', () => {
  assert.equal(weatherCondition(95).label, 'Thunderstorm');
  assert.equal(weatherCondition(0, false).icon, '🌙');
  assert.equal(weatherCondition(999).label, 'Weather conditions');
  assert.equal(temperature(0, 'F'), '32°');
  assert.equal(temperature(null, 'C'), '—');
});
