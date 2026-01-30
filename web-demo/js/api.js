// ClimaAI API Client
class APIClient {
    constructor() {
        this.baseURL = 'http://localhost:8000';
        this.token = localStorage.getItem('access_token');
    }

    setToken(token) {
        this.token = token;
        if (token) {
            localStorage.setItem('access_token', token);
        } else {
            localStorage.removeItem('access_token');
        }
    }

    async request(endpoint, options = {}) {
        const url = `${this.baseURL}${endpoint}`;
        const headers = {
            'Content-Type': 'application/json',
            ...options.headers
        };

        if (this.token && options.auth !== false) {
            headers['Authorization'] = `Bearer ${this.token}`;
        }

        try {
            const response = await fetch(url, {
                ...options,
                headers
            });

            if (!response.ok) {
                if (response.status === 401) {
                    this.setToken(null);
                    throw new Error('Authentication required');
                }
                const error = await response.json();
                throw new Error(error.detail || 'Request failed');
            }

            return await response.json();
        } catch (error) {
            console.error('API Error:', error);
            throw error;
        }
    }

    // Auth
    async login(email, password) {
        const response = await this.request('/users/login', {
            method: 'POST',
            auth: false,
            body: JSON.stringify({ email, password })
        });
        this.setToken(response.access_token);
        return response;
    }

    async register(email, password, fullName) {
        const response = await this.request('/users/register', {
            method: 'POST',
            auth: false,
            body: JSON.stringify({
                email,
                password,
                full_name: fullName,
                platform: 'web'
            })
        });
        this.setToken(response.access_token);
        return response;
    }

    async getCurrentUser() {
        return await this.request('/users/me');
    }

    // Weather
    async getWeather(latitude, longitude) {
        return await this.request(
            `/weather/current?latitude=${latitude}&longitude=${longitude}`,
            { auth: false }
        );
    }

    async getHourlyForecast(latitude, longitude, hours = 24) {
        return await this.request(
            `/weather/hourly?latitude=${latitude}&longitude=${longitude}&hours=${hours}`,
            { auth: false }
        );
    }

    async getDailyForecast(latitude, longitude, days = 7) {
        return await this.request(
            `/weather/daily?latitude=${latitude}&longitude=${longitude}&days=${days}`,
            { auth: false }
        );
    }

    async getAirQuality(latitude, longitude) {
        return await this.request(
            `/weather/air-quality?latitude=${latitude}&longitude=${longitude}`,
            { auth: false }
        );
    }

    // AI (Premium)
    async getAIInsights(latitude, longitude, locationName = 'your location') {
        return await this.request(
            `/ai/insights?latitude=${latitude}&longitude=${longitude}&location_name=${encodeURIComponent(locationName)}`
        );
    }

    async getDailySummary(latitude, longitude, locationName = 'your location') {
        return await this.request(
            `/ai/summary?latitude=${latitude}&longitude=${longitude}&location_name=${encodeURIComponent(locationName)}`
        );
    }

    async getOutfitRecommendation(latitude, longitude) {
        return await this.request(
            `/ai/outfit?latitude=${latitude}&longitude=${longitude}`
        );
    }

    async getActivityRecommendations(latitude, longitude) {
        return await this.request(
            `/ai/activities?latitude=${latitude}&longitude=${longitude}`
        );
    }

    async getHealthInsights(latitude, longitude) {
        return await this.request(
            `/ai/health?latitude=${latitude}&longitude=${longitude}`
        );
    }

    // Subscriptions
    async getSubscriptionStatus() {
        return await this.request('/subscriptions/status');
    }

    async getPlans() {
        return await this.request('/subscriptions/plans', { auth: false });
    }
}

// Weather code to emoji mapping
const weatherIcons = {
    0: '☀️',  // Clear sky
    1: '🌤️', // Mainly clear
    2: '⛅', // Partly cloudy
    3: '☁️',  // Overcast
    45: '🌫️', // Foggy
    48: '🌫️', // Depositing rime fog
    51: '🌦️', // Light drizzle
    53: '🌦️', // Moderate drizzle
    55: '🌧️', // Dense drizzle
    61: '🌧️', // Slight rain
    63: '🌧️', // Moderate rain
    65: '🌧️', // Heavy rain
    71: '🌨️', // Slight snow
    73: '🌨️', // Moderate snow
    75: '🌨️', // Heavy snow
    77: '🌨️', // Snow grains
    80: '🌦️', // Slight rain showers
    81: '🌧️', // Moderate rain showers
    82: '⛈️', // Violent rain showers
    85: '🌨️', // Slight snow showers
    86: '🌨️', // Heavy snow showers
    95: '⛈️', // Thunderstorm
    96: '⛈️', // Thunderstorm with hail
    99: '⛈️'  // Thunderstorm with heavy hail
};

function getWeatherIcon(code) {
    return weatherIcons[code] || '🌤️';
}

function formatTemperature(temp, unit = 'celsius') {
    if (unit === 'fahrenheit') {
        temp = (temp * 9 / 5) + 32;
        return `${Math.round(temp)}°F`;
    }
    return `${Math.round(temp)}°C`;
}

function formatTime(dateString) {
    const date = new Date(dateString);
    return date.toLocaleTimeString('en-US', { hour: 'numeric', hour12: true });
}

function formatDate(dateString) {
    const date = new Date(dateString);
    const today = new Date();
    const tomorrow = new Date(today);
    tomorrow.setDate(tomorrow.getDate() + 1);

    if (date.toDateString() === today.toDateString()) {
        return 'Today';
    } else if (date.toDateString() === tomorrow.toDateString()) {
        return 'Tomorrow';
    } else {
        return date.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' });
    }
}

// Make API client global
window.api = new APIClient();
window.weatherUtils = { getWeatherIcon, formatTemperature, formatTime, formatDate };
