// ClimaAI API Client
class APIClient {
    constructor() {
        this.baseURL = 'http://localhost:8000';
        this.token = localStorage.getItem('access_token');
        this.useMockData = true; // Enable mock mode for demo
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
        // Use mock data for demo mode
        if (this.useMockData) {
            return this.getMockData(endpoint);
        }

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
            console.log('API unavailable, using mock data');
            return this.getMockData(endpoint);
        }
    }

    getMockData(endpoint) {
        // Mock weather data - match any weather endpoint
        if (endpoint.includes('/weather')) {
            return {
                current: {
                    temperature: 28,
                    feels_like: 30,
                    humidity: 65,
                    wind_speed: 12,
                    weather_code: 1,
                    weather_description: 'Mostly Sunny',
                    uv_index: 6,
                    is_day: true,
                    pressure: 1013,
                    visibility: 10
                },
                hourly: Array.from({ length: 24 }, (_, i) => ({
                    time: new Date(Date.now() + i * 3600000).toISOString(),
                    temperature: 24 + Math.sin(i / 4) * 5,
                    weather_code: i < 12 ? 1 : 2,
                    precipitation_probability: Math.floor(Math.random() * 30)
                })),
                daily: Array.from({ length: 7 }, (_, i) => ({
                    date: new Date(Date.now() + i * 86400000).toISOString().split('T')[0],
                    temperature_max: 30 + Math.random() * 4,
                    temperature_min: 22 + Math.random() * 3,
                    weather_code: [0, 1, 2, 3, 1, 0, 2][i],
                    weather_description: ['Clear', 'Sunny', 'Partly Cloudy', 'Cloudy', 'Sunny', 'Clear', 'Partly Cloudy'][i],
                    precipitation_probability: [10, 5, 20, 40, 15, 5, 25][i],
                    sunrise: '06:30',
                    sunset: '18:45'
                })),
                air_quality: {
                    aqi: 42,
                    category: 'Good',
                    pm2_5: 8.5,
                    pm10: 15.2,
                    nitrogen_dioxide: 12.3,
                    ozone: 45.2,
                    sulphur_dioxide: 5.1,
                    carbon_monoxide: 0.3,
                    health_recommendation: 'Air quality is good. Enjoy outdoor activities!'
                }
            };
        }

        // Mock AI insights - match any ai endpoint
        if (endpoint.includes('/ai')) {
            return {
                summary: {
                    title: "Great Weather Today! ☀️",
                    summary: "Expect warm and sunny conditions with comfortable temperatures. Perfect for outdoor activities!"
                },
                daily_summary: {
                    title: "Perfect Day for Outdoor Activities! ☀️",
                    summary: "Today brings comfortable temperatures with light clouds. UV levels will be moderate in the afternoon. Great conditions for a morning jog or evening walk.",
                    highlights: [
                        "Pleasant temperatures throughout the day",
                        "Low chance of rain",
                        "Good air quality"
                    ],
                    warnings: []
                },
                outfit: {
                    summary: "Light layers recommended",
                    details: "A light t-shirt with optional cardigan for evening. Sunglasses recommended.",
                    accessories: ["Sunglasses", "Light jacket for evening"],
                    layer_recommendation: "Single layer with backup"
                },
                activities: [
                    { activity: "Morning Jog", suitability_score: 92, best_time: "7-9 AM", reasoning: "Cool temperatures, low UV" },
                    { activity: "Outdoor Dining", suitability_score: 88, best_time: "6-8 PM", reasoning: "Pleasant evening weather" },
                    { activity: "Cycling", suitability_score: 85, best_time: "4-6 PM", reasoning: "Good visibility, mild winds" }
                ],
                health: {
                    uv_risk: "moderate",
                    uv_advice: "Wear sunscreen if outdoors for extended periods",
                    air_quality_risk: "low",
                    air_quality_advice: "Air quality is good for all activities",
                    general_health_tips: ["Stay hydrated", "Take breaks in shade during peak UV hours"]
                }
            };
        }

        // Mock subscription status
        if (endpoint.includes('/subscriptions/status')) {
            return {
                has_active_subscription: true,
                is_premium: true,
                subscription: { plan: 'yearly' },
                features: {
                    extended_forecast: true,
                    ai_insights: true,
                    minute_rain: true
                }
            };
        }

        return {};
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
