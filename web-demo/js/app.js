// ClimaAI Web Demo - Main App Logic

class ClimaAI {
    constructor() {
        this.currentScreen = 'homeScreen';
        this.currentLocation = null;
        this.weatherData = null;
        this.aiInsights = null;
        // Demo mode - simulate logged in premium user
        this.user = {
            email: 'demo@climaai.com',
            full_name: 'Demo User',
            is_premium: true
        };
        this.isPremium = true;

        // Theme: 'dark', 'light', 'system'
        this.theme = localStorage.getItem('climaai-theme') || 'dark';

        this.init();
    }

    initTheme() {
        this.applyTheme(this.theme);
        this.updateThemeIcon();
    }

    applyTheme(theme) {
        const appContainer = document.getElementById('appContainer');
        if (theme === 'system') {
            const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
            appContainer.setAttribute('data-theme', prefersDark ? 'dark' : 'light');
        } else {
            appContainer.setAttribute('data-theme', theme);
        }
    }

    cycleTheme() {
        const themes = ['dark', 'light', 'system'];
        const currentIndex = themes.indexOf(this.theme);
        this.theme = themes[(currentIndex + 1) % themes.length];
        localStorage.setItem('climaai-theme', this.theme);
        this.applyTheme(this.theme);
        this.updateThemeIcon();
        this.showToast(`Theme: ${this.theme.charAt(0).toUpperCase() + this.theme.slice(1)}`);
    }

    updateThemeIcon() {
        const icon = document.getElementById('themeIcon');
        if (icon) {
            if (this.theme === 'dark') icon.textContent = '🌙';
            else if (this.theme === 'light') icon.textContent = '☀️';
            else icon.textContent = '🔄';
        }
    }

    init() {
        this.setupEventListeners();
        this.initTheme();

        // Skip login - go directly to home screen with demo data
        this.showScreen('homeScreen');
        this.loadWeatherData();
    }

    setupEventListeners() {
        // Auth
        document.getElementById('googleSignInBtn').addEventListener('click', (e) => this.handleGoogleSignIn(e));
        document.getElementById('loginForm').addEventListener('submit', (e) => this.handleLogin(e));
        document.getElementById('registerForm').addEventListener('submit', (e) => this.handleRegister(e));
        document.getElementById('showRegister').addEventListener('click', (e) => {
            e.preventDefault();
            this.showScreen('registerScreen');
        });
        document.getElementById('backToLogin').addEventListener('click', () => {
            this.showScreen('loginScreen');
        });
        document.getElementById('logoutBtn').addEventListener('click', () => this.handleLogout());

        // Navigation
        document.querySelectorAll('.nav-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const screen = btn.dataset.screen;
                this.navigateTo(screen);
            });
        });

        // Refresh
        document.getElementById('refreshBtn').addEventListener('click', () => this.loadWeatherData());

        // Theme toggle
        document.getElementById('themeToggle').addEventListener('click', () => this.cycleTheme());

        // Upgrade button
        document.getElementById('upgradeBtn').addEventListener('click', () => this.showUpgradePrompt());
    }

    showScreen(screenId) {
        document.querySelectorAll('.screen').forEach(screen => {
            screen.classList.add('hidden');
            screen.setAttribute('aria-hidden', 'true');
            // 'inert' ensures hidden screens are not focusable or interactive
            screen.setAttribute('inert', '');
        });
        const activeScreen = document.getElementById(screenId);
        activeScreen.classList.remove('hidden');
        activeScreen.removeAttribute('aria-hidden');
        activeScreen.removeAttribute('inert');
        this.currentScreen = screenId;
    }

    navigateTo(screen) {
        const screenMap = {
            'home': 'homeScreen',
            'insights': 'insightsScreen',
            'airquality': 'airqualityScreen',
            'settings': 'settingsScreen'
        };

        const screenId = screenMap[screen];
        if (screenId) {
            this.showScreen(screenId);

            // Update nav buttons
            document.querySelectorAll('.nav-btn').forEach(btn => {
                btn.classList.remove('active');
                if (btn.dataset.screen === screen) {
                    btn.classList.add('active');
                }
            });

            // Load data for specific screens
            if (screen === 'insights') {
                this.loadAIInsights();
            } else if (screen === 'airquality') {
                this.loadAirQuality();
            } else if (screen === 'settings') {
                this.loadSettings();
            }
        }
    }

    async handleLogin(e) {
        e.preventDefault();
        const email = document.getElementById('loginEmail').value;
        const password = document.getElementById('loginPassword').value;

        const submitBtn = e.submitter;
        let originalContent = '';
        if (submitBtn) {
            originalContent = submitBtn.innerHTML;
            submitBtn.innerHTML = 'Logging in...';
            submitBtn.disabled = true;
            submitBtn.setAttribute('aria-busy', 'true');
        }

        try {
            this.showToast('Logging in...', 'info');
            const response = await api.login(email, password);
            this.user = response.user;
            this.showToast('Welcome back! 🌤️', 'success');
            this.showScreen('homeScreen');
            this.loadWeatherData();
            this.checkSubscription();
        } catch (error) {
            this.showToast(error.message || 'Login failed', 'error');
        } finally {
            if (submitBtn) {
                submitBtn.innerHTML = originalContent;
                submitBtn.disabled = false;
                submitBtn.removeAttribute('aria-busy');
            }
        }
    }

    async handleRegister(e) {
        e.preventDefault();
        const name = document.getElementById('registerName').value;
        const email = document.getElementById('registerEmail').value;
        const password = document.getElementById('registerPassword').value;

        const submitBtn = e.submitter;
        let originalContent = '';
        if (submitBtn) {
            originalContent = submitBtn.innerHTML;
            submitBtn.innerHTML = 'Signing Up...';
            submitBtn.disabled = true;
            submitBtn.setAttribute('aria-busy', 'true');
        }

        try {
            this.showToast('Creating account...', 'info');
            const response = await api.register(email, password, name);
            this.user = response.user;
            this.showToast('Account created! Welcome! 🎉', 'success');
            this.showScreen('homeScreen');
            this.loadWeatherData();
            this.checkSubscription();
        } catch (error) {
            this.showToast(error.message || 'Registration failed', 'error');
        } finally {
            if (submitBtn) {
                submitBtn.innerHTML = originalContent;
                submitBtn.disabled = false;
                submitBtn.removeAttribute('aria-busy');
            }
        }
    }

    handleLogout() {
        api.setToken(null);
        this.user = null;
        this.isPremium = false;
        this.showScreen('loginScreen');
        this.showToast('Logged out successfully', 'info');
    }

    async handleGoogleSignIn(e) {
        let btn = null;
        let originalContent = '';
        if (e && e.currentTarget) {
            btn = e.currentTarget;
            originalContent = btn.innerHTML;
            btn.innerHTML = 'Signing in...';
            btn.disabled = true;
            btn.setAttribute('aria-busy', 'true');
        }

        try {
            this.showToast('🔐 Signing in with Google...', 'info');

            // In production, this would trigger Google OAuth flow:
            // 1. Redirect to Google OAuth consent screen
            // 2. User grants permissions
            // 3. Google redirects back with authorization code
            // 4. Backend exchanges code for tokens
            // 5. Backend creates/updates user and returns JWT

            // For demo purposes, we'll simulate successful OAuth with demo account
            await new Promise(resolve => setTimeout(resolve, 1500)); // Simulate OAuth redirect delay

            try {
                // Auto-login with demo account
                const response = await api.login('demo@climaai.com', 'Test1234');
                this.user = response.user;
                this.showToast('✅ Welcome! Signed in with Google', 'success');
                this.showScreen('homeScreen');
                this.loadWeatherData();
                this.checkSubscription();
            } catch (error) {
                this.showToast('Google Sign-In succeeded! Welcome!', 'success');
                // Create a demo user object
                this.user = {
                    email: 'google-user@gmail.com',
                    full_name: 'Google User',
                    is_premium: true
                };
                this.isPremium = true;
                this.showScreen('homeScreen');
                this.loadWeatherData();
            }

        } catch (error) {
            this.showToast(error.message || 'Google Sign-In failed', 'error');
        } finally {
            if (btn) {
                btn.innerHTML = originalContent;
                btn.disabled = false;
                btn.removeAttribute('aria-busy');
            }
        }
    }

    async loadUser() {
        try {
            this.user = await api.getCurrentUser();
            this.showScreen('homeScreen');
            this.loadWeatherData();
            this.checkSubscription();
        } catch (error) {
            console.error('Failed to load user:', error);
            this.showScreen('loginScreen');
        }
    }

    async checkSubscription() {
        try {
            const status = await api.getSubscriptionStatus();
            this.isPremium = status.is_premium;

            // Update UI
            if (this.isPremium) {
                document.getElementById('subscriptionStatus').textContent = 'Premium 💎';
                document.getElementById('upgradeBtn').textContent = '✅ Premium Active';
                document.getElementById('upgradeBtn').disabled = true;
            }
        } catch (error) {
            console.error('Failed to check subscription:', error);
            this.isPremium = false;
        }
    }

    async loadWeatherData() {
        try {
            // Show loading state
            document.getElementById('locationName').textContent = '📍 Getting location...';

            // Try to get user's actual location
            if (navigator.geolocation) {
                navigator.geolocation.getCurrentPosition(
                    async (position) => {
                        // Got user's real location
                        this.currentLocation = {
                            latitude: position.coords.latitude,
                            longitude: position.coords.longitude,
                            name: 'Your Location'
                        };

                        // Try to get city name from coordinates using reverse geocoding
                        try {
                            const response = await fetch(
                                `https://nominatim.openstreetmap.org/reverse?format=json&lat=${position.coords.latitude}&lon=${position.coords.longitude}&zoom=10`
                            );
                            const data = await response.json();

                            if (data && data.address) {
                                const city = data.address.city || data.address.town || data.address.village || data.address.county;
                                const country = data.address.country;
                                this.currentLocation.name = city ? `${city}, ${country}` : country || 'Your Location';
                            }
                        } catch (geoError) {
                            console.log('Reverse geocoding failed, using coordinates');
                            this.currentLocation.name = `📍 ${position.coords.latitude.toFixed(2)}, ${position.coords.longitude.toFixed(2)}`;
                        }

                        await this.fetchWeatherData();
                    },
                    (error) => {
                        // Location denied or error - use default
                        console.log('Geolocation error:', error.message);
                        this.currentLocation = {
                            latitude: 40.7128,
                            longitude: -74.0060,
                            name: 'New York, USA (Demo)'
                        };
                        this.showToast('📍 Using demo location. Allow location access for local weather.', 'info');
                        this.fetchWeatherData();
                    },
                    {
                        enableHighAccuracy: true,
                        timeout: 10000,
                        maximumAge: 300000 // Cache for 5 minutes
                    }
                );
            } else {
                // Browser doesn't support geolocation
                this.currentLocation = {
                    latitude: 40.7128,
                    longitude: -74.0060,
                    name: 'New York, USA (Demo)'
                };
                await this.fetchWeatherData();
            }
        } catch (error) {
            this.showToast('Failed to load weather', 'error');
            console.error(error);
        }
    }

    async fetchWeatherData() {
        const { latitude, longitude, name } = this.currentLocation;

        try {
            // Update location name
            document.getElementById('locationName').textContent = name;

            // Fetch weather data
            this.weatherData = await api.getWeather(latitude, longitude);

            this.renderCurrentWeather();
            this.renderQuickStats();
            this.renderHourlyForecast();
            this.renderDailyForecast();

            // Load AI summary if premium
            if (this.isPremium) {
                this.loadAISummary();
            } else {
                this.showPremiumAICard();
            }
        } catch (error) {
            this.showToast('Failed to load weather', 'error');
            console.error(error);
        }
    }

    getWindDirection(degrees) {
        const directions = ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW'];
        const index = Math.round(degrees / 45) % 8;
        return directions[index];
    }

    formatSunTime(isoString) {
        if (!isoString) return '--:--';
        const date = new Date(isoString);
        if (isNaN(date.getTime())) return '--:--';
        return date.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false });
    }

    renderCurrentWeather() {
        const { current, daily } = this.weatherData;
        const today = daily?.[0] || {};
        const windDir = this.getWindDirection(current.wind_direction ?? 0);

        const html = `
            <div class="weather-main">
                <div class="weather-icon">${weatherUtils.getWeatherIcon(current.weather_code)}</div>
                <div class="weather-temp-group">
                    <div class="temperature">${weatherUtils.formatTemperature(current.temperature)}</div>
                    <div class="weather-meta">
                        <span class="weather-description">${current.weather_description}</span>
                        <span class="temp-range">H:${Math.round(today.temperature_max ?? current.temperature)}° L:${Math.round(today.temperature_min ?? current.temperature - 5)}°</span>
                        <span class="feels-like">Feels ${Math.round(current.feels_like)}°</span>
                    </div>
                </div>
            </div>
            <div class="weather-details-grid">
                <div class="detail-item">
                    <span class="detail-icon">💧</span>
                    <span class="detail-value">${current.humidity}%</span>
                    <span class="detail-label">Humidity</span>
                </div>
                <div class="detail-item">
                    <span class="detail-icon">💨</span>
                    <span class="detail-value">${Math.round(current.wind_speed)}</span>
                    <span class="detail-label">${windDir} km/h</span>
                </div>
                <div class="detail-item">
                    <span class="detail-icon">☀️</span>
                    <span class="detail-value">${Math.round(current.uv_index)}</span>
                    <span class="detail-label">UV</span>
                </div>
                <div class="detail-item">
                    <span class="detail-icon">👁️</span>
                    <span class="detail-value">${Math.round(current.visibility / 1000)}</span>
                    <span class="detail-label">Vis km</span>
                </div>
                <div class="detail-item">
                    <span class="detail-icon">🌡️</span>
                    <span class="detail-value">${Math.round(current.pressure)}</span>
                    <span class="detail-label">hPa</span>
                </div>
                <div class="detail-item">
                    <span class="detail-icon">☁️</span>
                    <span class="detail-value">${current.cloud_cover ?? 0}%</span>
                    <span class="detail-label">Cloud</span>
                </div>
            </div>
        `;
        document.getElementById('currentWeather').innerHTML = html;

        // Apply weather-based animation
        this.applyWeatherAnimation(current.weather_code, current.is_day);
    }

    applyWeatherAnimation(weatherCode, isDay) {
        const container = document.getElementById('currentWeather');

        // Remove existing weather classes
        container.classList.remove('weather-clear', 'weather-cloudy', 'weather-rain',
            'weather-snow', 'weather-thunder', 'weather-night');

        // Determine weather type
        let weatherClass = 'weather-clear';
        if (weatherCode >= 95) {
            weatherClass = 'weather-thunder';
        } else if (weatherCode >= 71 && weatherCode <= 86) {
            weatherClass = 'weather-snow';
        } else if (weatherCode >= 51 && weatherCode <= 82) {
            weatherClass = 'weather-rain';
        } else if (weatherCode >= 1 && weatherCode <= 48) {
            weatherClass = 'weather-cloudy';
        }

        if (!isDay) {
            weatherClass += ' weather-night';
        }

        container.classList.add(weatherClass);
    }

    renderQuickStats() {
        // Stats now rendered inline in currentWeather, hide this section
        document.getElementById('quickStats').style.display = 'none';
    }

    renderHourlyForecast() {
        const { hourly } = this.weatherData;
        const next12 = hourly.slice(0, 12); // Show 12 hours for density

        const html = next12.map((hour, i) => `
            <div class="hourly-item ${i === 0 ? 'now' : ''}">
                <div class="hourly-time">${i === 0 ? 'Now' : weatherUtils.formatTime(hour.time)}</div>
                <div class="hourly-icon">${weatherUtils.getWeatherIcon(hour.weather_code)}</div>
                <div class="hourly-temp">${Math.round(hour.temperature)}°</div>
                <div class="hourly-rain">${hour.precipitation_probability > 0 ? '💧' + hour.precipitation_probability + '%' : ''}</div>
            </div>
        `).join('');

        document.getElementById('hourlyForecast').innerHTML = html;
    }

    renderDailyForecast() {
        const { daily } = this.weatherData;

        const html = daily.map((day, i) => `
            <div class="daily-item">
                <div class="daily-date">${i === 0 ? 'Today' : weatherUtils.formatDate(day.date)}</div>
                <div class="daily-precip">${day.precipitation_sum > 0 ? '💧' + Math.round(day.precipitation_sum) + 'mm' : ''}</div>
                <div class="daily-icon">${weatherUtils.getWeatherIcon(day.weather_code)}</div>
                <div class="daily-temps">
                    <span class="temp-max">${Math.round(day.temperature_max)}°</span>
                    <div class="temp-bar">
                        <div class="temp-fill" style="width: ${((day.temperature_max - day.temperature_min) / 20) * 100}%"></div>
                    </div>
                    <span class="temp-min">${Math.round(day.temperature_min)}°</span>
                </div>
            </div>
        `).join('');

        document.getElementById('dailyForecast').innerHTML = html;
    }

    async loadAISummary() {
        try {
            const { latitude, longitude, name } = this.currentLocation;
            const data = await api.getDailySummary(latitude, longitude, name);

            const html = `
                <h4>${data.summary.title}</h4>
                <p>${data.summary.summary}</p>
            `;
            document.getElementById('aiSummaryContent').innerHTML = html;
        } catch (error) {
            this.showPremiumAICard();
        }
    }

    showPremiumAICard() {
        const html = `
            <p>🔒 Upgrade to Premium to unlock AI-powered weather insights:</p>
            <ul class="insight-list">
                <li>📰 Daily weather summaries</li>
                <li>👔 "What to wear" recommendations</li>
                <li>🏃 Activity suggestions with best times</li>
                <li>💪 Health & wellness insights</li>
            </ul>
            <button class="btn btn-primary" onclick="app.showUpgradePrompt()" style="margin-top: 12px;">
                Upgrade to Premium
            </button>
        `;
        document.getElementById('aiSummaryContent').innerHTML = html;
    }

    async loadAIInsights() {
        if (!this.isPremium) {
            this.showPremiumPrompt('insightsContent');
            return;
        }

        try {
            document.getElementById('insightsContent').innerHTML = '<div class="loading">Loading AI insights...</div>';

            const { latitude, longitude, name } = this.currentLocation;
            this.aiInsights = await api.getAIInsights(latitude, longitude, name);

            this.renderAIInsights();
        } catch (error) {
            this.showToast('Failed to load AI insights. You need a Premium subscription.', 'error');
            this.showPremiumPrompt('insightsContent');
        }
    }

    renderAIInsights() {
        const { daily_summary, outfit, activities, health } = this.aiInsights;

        const html = `
            <div class="insight-card">
                <h4>📰 ${daily_summary.title}</h4>
                <p>${daily_summary.summary}</p>
                ${daily_summary.highlights.length > 0 ? `
                    <ul class="insight-list">
                        ${daily_summary.highlights.map(h => `<li>✨ ${h}</li>`).join('')}
                    </ul>
                ` : ''}
            </div>

            <div class="insight-card">
                <h4>👔 What to Wear</h4>
                <p><strong>${outfit.summary}</strong></p>
                <p>${outfit.details}</p>
                ${outfit.accessories.length > 0 ? `
                    <p><strong>Don't forget:</strong> ${outfit.accessories.join(', ')}</p>
                ` : ''}
            </div>

            <div class="insight-card">
                <h4>🏃 Activities</h4>
                ${activities.map(activity => `
                    <div class="activity-item">
                        <div>
                            <strong>${activity.activity}</strong>
                            <p style="font-size: 13px; color: #718096; margin-top: 4px;">${activity.reasoning}</p>
                        </div>
                        <div class="activity-score">${activity.suitability_score}%</div>
                    </div>
                `).join('')}
            </div>

            <div class="insight-card">
                <h4>💪 Health Insights</h4>
                <p><strong>UV Risk:</strong> ${health.uv_risk.toUpperCase()}</p>
                <p>${health.uv_advice}</p>
                <p><strong>Air Quality:</strong> ${health.air_quality_risk.toUpperCase()}</p>
                <p>${health.air_quality_advice}</p>
                ${health.general_health_tips.length > 0 ? `
                    <ul class="insight-list">
                        ${health.general_health_tips.map(tip => `<li>💡 ${tip}</li>`).join('')}
                    </ul>
                ` : ''}
            </div>
`;

        document.getElementById('insightsContent').innerHTML = html;
    }

    async loadAirQuality() {
        try {
            document.getElementById('airqualityContent').innerHTML = '<div class="loading">Loading air quality...</div>';

            const { latitude, longitude } = this.currentLocation;
            const data = await api.getAirQuality(latitude, longitude);

            this.renderAirQuality(data.air_quality);
        } catch (error) {
            this.showToast('Failed to load air quality', 'error');
            console.error(error);
        }
    }

    renderAirQuality(aqi) {
        const getAQIColor = (value) => {
            if (value <= 50) return '#50C878';
            if (value <= 100) return '#FFD700';
            if (value <= 150) return '#FF9800';
            if (value <= 200) return '#F44336';
            return '#8B0000';
        };

        const html = `
            <div class="aqi-gauge" style="background: linear-gradient(135deg, ${getAQIColor(aqi.aqi)} 0%, ${getAQIColor(aqi.aqi)}dd 100%);">
                <div class="aqi-value">${aqi.aqi}</div>
                <div class="aqi-category">${aqi.category}</div>
                <div class="aqi-description">${aqi.health_recommendation}</div>
            </div>

    <div class="pollutant-grid">
        <div class="pollutant-card">
            <div class="pollutant-name">PM2.5</div>
            <div class="pollutant-value">${aqi.pm2_5.toFixed(1)}</div>
        </div>
        <div class="pollutant-card">
            <div class="pollutant-name">PM10</div>
            <div class="pollutant-value">${aqi.pm10.toFixed(1)}</div>
        </div>
        <div class="pollutant-card">
            <div class="pollutant-name">NO₂</div>
            <div class="pollutant-value">${aqi.nitrogen_dioxide.toFixed(1)}</div>
        </div>
        <div class="pollutant-card">
            <div class="pollutant-name">O₃</div>
            <div class="pollutant-value">${aqi.ozone.toFixed(1)}</div>
        </div>
        <div class="pollutant-card">
            <div class="pollutant-name">SO₂</div>
            <div class="pollutant-value">${aqi.sulphur_dioxide.toFixed(1)}</div>
        </div>
        <div class="pollutant-card">
            <div class="pollutant-name">CO</div>
            <div class="pollutant-value">${aqi.carbon_monoxide.toFixed(2)}</div>
        </div>
    </div>
`;

        document.getElementById('airqualityContent').innerHTML = html;
    }

    loadSettings() {
        if (this.user) {
            document.getElementById('userEmail').textContent = this.user.email;
        }
    }

    showPremiumPrompt(containerId) {
        const html = `
            <div class="insight-card" style="text-align: center; padding: 40px 20px;">
                <h2 style="font-size: 48px; margin-bottom: 16px;">💎</h2>
                <h3 style="margin-bottom: 12px;">Premium Feature</h3>
                <p style="margin-bottom: 20px;">Upgrade to Premium to unlock AI-powered insights and advanced features.</p>
                <button class="btn btn-primary" onclick="app.showUpgradePrompt()">
                    Upgrade to Premium
                </button>
            </div>
        `;
        document.getElementById(containerId).innerHTML = html;
    }

    showUpgradePrompt() {
        this.showToast('💎 Premium subscription available for $4.99/month or $39.99/year with 7-day free trial!', 'info');
    }

    showToast(message, type = 'info') {
        const toast = document.createElement('div');
        toast.className = `toast ${type}`;

        const icons = {
            success: '✅',
            error: '❌',
            info: 'ℹ️'
        };

        toast.innerHTML = `
            <span class="toast-icon">${icons[type] || icons.info}</span>
            <span class="toast-message">${message}</span>
        `;

        document.getElementById('toastContainer').appendChild(toast);

        setTimeout(() => {
            toast.remove();
        }, 3000);
    }
}

// Initialize app
let app;
document.addEventListener('DOMContentLoaded', () => {
    app = new ClimaAI();
});
