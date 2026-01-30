# ClimaAI Web Demo

A web-based mobile interface to test and demonstrate the ClimaAI weather application.

## Features

- 📱 Mobile-like interface in browser
- 🔐 User authentication (login/register)
- 🌤️ Real-time weather data
- 🤖 AI-powered insights (Premium)
- 🌬️ Air quality monitoring
- 💎 Premium subscription simulation

## Quick Start

### 1. Start the Backend

```bash
cd /Users/adityasingh/clima-ai
./start.sh
```

Make sure the backend is running on `http://localhost:8000`

### 2. Open the Demo

Simply open `index.html` in your browser:

```bash
open web-demo/index.html
```

Or use a local server (recommended):

```bash
cd web-demo
python3 -m http.server 8080
```

Then visit: `http://localhost:8080`

## Demo Account

**Email:** demo@climaai.com
**Password:** Test1234

This account has Premium access to test all AI features.

## Testing Flow

1. **Login** with demo account or create new account
2. **View Weather** - See current conditions and forecasts
3. **AI Insights** (Premium) - Test AI-powered features
4. **Air Quality** - Check pollution levels
5. **Settings** - View account and preferences

## Features Demonstrated

### Free Tier
- ✅ Current weather
- ✅ 24-hour hourly forecast  
- ✅ 7-day daily forecast
- ✅ Basic air quality
- ✅ Location detection

### Premium Tier (Demo Account)
- 💎 AI daily summaries
- 💎 Outfit recommendations
- 💎 Activity suggestions
- 💎 Health insights
- 💎 Travel risk analysis

## Architecture

```
web-demo/
├── index.html          # Main HTML structure
├── css/
│   └── style.css      # Mobile-like styling
└── js/
    ├── api.js         # API client
    └── app.js         # Application logic
```

## API Endpoints Used

- `POST /users/login` - Authentication
- `POST /users/register` - User registration
- `GET /weather/current` - Weather data
- `GET /ai/insights` - AI insights (Premium)
- `GET /weather/air-quality` - Air quality
- `GET /subscriptions/status` - Check premium status

## Browser Support

- Chrome 90+
- Firefox 88+
- Safari 14+
- Edge 90+

## Troubleshooting

**Can't connect to API:**
- Make sure backend is running: `./start.sh`
- Check that port 8000 is accessible
- Verify no CORS issues in browser console

**AI Insights not working:**
- Requires OpenAI API key in backend/.env
- Or set `ENABLE_AI_INSIGHTS=false` for testing

**Location not detected:**
- Browser must support Geolocation API
- Grant location permissions when prompted
- Falls back to New York, USA if denied

---

**Enjoy testing ClimaAI!** 🌤️
