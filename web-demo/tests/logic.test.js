/**
 * @jest-environment jsdom
 */

const { APIClient, formatTemperature, formatTime, formatDate } = require('../js/api');

// Mock localStorage
const localStorageMock = (function() {
  let store = {};
  return {
    getItem: function(key) {
      return store[key] || null;
    },
    setItem: function(key, value) {
      store[key] = value.toString();
    },
    removeItem: function(key) {
      delete store[key];
    },
    clear: function() {
      store = {};
    }
  };
})();
Object.defineProperty(window, 'localStorage', { value: localStorageMock });

describe('Weather Utils', () => {
    test('formatTemperature returns correctly formatted string', () => {
        expect(formatTemperature(25)).toBe('25°C');
        expect(formatTemperature(25, 'fahrenheit')).toBe('77°F');
    });

    test('formatTime returns correctly formatted time', () => {
        // Use a fixed date
        const dateStr = '2023-01-01T12:30:00Z';
        const formatted = formatTime(dateStr);
        // Implementation uses hour: 'numeric', so it might not show minutes
        expect(formatted).toMatch(/12/);
        expect(formatted).toMatch(/PM/);
    });
});

describe('APIClient', () => {
    let api;

    beforeEach(() => {
        api = new APIClient();
        api.useMockData = true; // Use built-in mock data for unit tests
        localStorage.clear();
    });

    test('getWeather returns current weather data structure', async () => {
        const data = await api.getWeather(51.5, -0.1);
        expect(data).toHaveProperty('current');
        expect(data.current).toHaveProperty('temperature');
        expect(data).toHaveProperty('hourly');
        expect(data).toHaveProperty('daily');
    });

    test('getAIInsights returns insights structure', async () => {
        const data = await api.getAIInsights(51.5, -0.1);
        expect(data).toHaveProperty('daily_summary');
        expect(data).toHaveProperty('outfit');
        expect(data).toHaveProperty('activities');
    });

    test('login sets token', async () => {
        api.setToken('fake-token');
        expect(api.token).toBe('fake-token');
        expect(localStorage.getItem('access_token')).toBe('fake-token');
    });
});
