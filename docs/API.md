# ClimaAI API Documentation

## Base URL

- **Production**: `https://api.climaai.com`
- **Development**: `http://localhost:8000`

## Authentication

Most endpoints require JWT authentication via Bearer token.

```
Authorization: Bearer <access_token>
```

Obtain access token via `/users/register` or `/users/login` endpoints.

## Response Format

All responses are JSON. Errors follow this format:

```json
{
  "detail": "Error message description"
}
```

---

## Endpoints

### Health Check

#### `GET /health`

Check API health status.

**Authentication**: None

**Response**:
```json
{
  "status": "healthy",
  "app": "ClimaAI API",
  "version": "1.0.0"
}
```

---

## User Management

### Register User

#### `POST /users/register`

Create a new user account.

**Authentication**: None

**Request Body**:
```json
{
  "email": "user@example.com",
  "password": "SecurePass123",
  "full_name": "John Doe",
  "platform": "ios",
  "device_token": "optional-push-token"
}
```

**Response**: `201 Created`
```json
{
  "access_token": "eyJ0eXAiOiJKV1QiLCJhbGc...",
  "token_type": "bearer",
  "user": {
    "id": "uuid",
    "email": "user@example.com",
    "full_name": "John Doe",
    "is_active": true,
    "is_verified": true,
    "platform": "ios",
    "preferences": {...},
    "created_at": "2024-01-01T00:00:00Z"
  }
}
```

### Login

#### `POST /users/login`

Authenticate user and get access token.

**Authentication**: None

**Request Body**:
```json
{
  "email": "user@example.com",
  "password": "SecurePass123"
}
```

**Response**: `200 OK` - Same as register response

### Get Current User

#### `GET /users/me`

Get authenticated user's profile.

**Authentication**: Required

**Response**: `200 OK`
```json
{
  "id": "uuid",
  "email": "user@example.com",
  "full_name": "John Doe ",
  "is_active": true,
  "is_verified": true,
  "platform": "ios",
  "preferences": {
    "temperature_unit": "celsius",
    "wind_speed_unit": "kmh",
    "precipitation_unit": "mm",
    "time_format": "24h",
    "notifications_enabled": true,
    "theme": "auto"
  },
  "default_location_name": "New York, USA",
  "created_at": "2024-01-01T00:00:00Z"
}
```

### Update User

#### `PUT /users/me`

Update user profile and preferences.

**Authentication**: Required

**Request Body**:
```json
{
  "full_name": "Jane Doe",
  "preferences": {
    "temperature_unit": "fahrenheit",
    "theme": "dark"
  },
  "default_latitude": "40.7128",
  "default_longitude": "-74.0060",
  "default_location_name": "New York, USA"
}
```

**Response**: `200 OK` - Updated user object

---

## Weather

### Get Current Weather

#### `GET /weather/current`

Get current weather, hourly forecast (24h), and daily forecast (7 or 16 days).

**Authentication**: Optional (unlimited with premium)

**Query Parameters**:
- `latitude` (required): Latitude (-90 to 90)
- `longitude` (required): Longitude (-180 to 180)

**Response**: `200 OK`
```json
{
  "current": {
    "temperature": 22.5,
    "feels_like": 21.0,
    "humidity": 65,
    "wind_speed": 15.2,
    "wind_direction": 180,
    "precipitation": 0.0,
    "weather_code": 2,
    "weather_description": "Partly cloudy",
    "cloud_cover": 40,
    "pressure": 1013.25,
    "visibility": 10000,
    "uv_index": 5.2,
    "is_day": true,
    "timestamp": "2024-01-01T12:00:00Z"
  },
  "hourly": [...], // Array of 24 hourly forecasts
  "daily": [...],  // Array of 7 (free) or 16 (premium) daily forecasts
  "air_quality": {
    "aqi": 45,
    "pm2_5": 10.5,
    "pm10": 20.3,
    "carbon_monoxide": 0.3,
    "nitrogen_dioxide": 15.2,
    "ozone": 60.5,
    "sulphur_dioxide": 5.1,
    "category": "Good",
    "health_recommendation": "Air quality is satisfactory..."
  },
  "location": {
    "latitude": 40.7128,
    "longitude": -74.0060,
    "elevation": 10
  },
  "timezone": "America/New_York",
  "cached": false
}
```

### Get Hourly Forecast

#### `GET /weather/hourly`

Get detailed hourly forecast.

**Authentication**: Optional

**Query Parameters**:
- `latitude` (required)
- `longitude` (required)
- `hours` (optional, default: 24, max: 24 free, 168 premium)

**Response**: `200 OK`
```json
{
  "hourly": [
    {
      "time": "2024-01-01T13:00:00Z",
      "temperature": 23.0,
      "feels_like": 21.5,
      "precipitation_probability": 10,
      "precipitation": 0.0,
      "weather_code": 1,
      "weather_description": "Mainly clear",
      "wind_speed": 12.0,
      "wind_direction": 170,
      "humidity": 60,
      "cloud_cover": 20,
      "uv_index": 6.0
    }
    // ... more hours
  ],
  "timezone": "America/New_York",
  "location": {...}
}
```

### Get Daily Forecast

#### `GET /weather/daily`

Get daily forecast.

**Authentication**: Optional

**Query Parameters**:
- `latitude` (required)
- `longitude` (required)
- `days` (optional, default: 7, max: 7 free, 16 premium)

**Response**: `200 OK`
```json
{
  "daily": [
    {
      "date": "2024-01-01",
      "temperature_max": 25.0,
      "temperature_min": 18.0,
      "sunrise": "2024-01-01T06:30:00Z",
      "sunset": "2024-01-01T18:45:00Z",
      "precipitation_sum": 2.5,
      "precipitation_probability": 30,
      "weather_code": 61,
      "weather_description": "Slight rain",
      "wind_speed_max": 20.0,
      "wind_direction": 200,
      "uv_index_max": 7.5
    }
    // ... more days
  ],
  "timezone": "America/New_York",
  "location": {...}
}
```

### Get Air Quality

#### `GET /weather/air-quality`

Get air quality data.

**Authentication**: None

**Query Parameters**:
- `latitude` (required)
- `longitude` (required)

**Response**: `200 OK`
```json
{
  "air_quality": {
    "aqi": 45,
    "pm2_5": 10.5,
    "pm10": 20.3,
    "category": "Good",
    "health_recommendation": "Air quality is satisfactory..."
  },
  "location": {...}
}
```

---

## AI Insights (Premium Only)

### Get Complete AI Insights

#### `GET /ai/insights`

Get comprehensive AI-powered weather insights.

**Authentication**: Required (Premium)

**Query Parameters**:
- `latitude` (required)
- `longitude` (required)
- `location_name` (optional, default: "your location")

**Response**: `200 OK`
```json
{
  "daily_summary": {
    "title": "Partly Cloudy with Afternoon Warmth",
    "summary": "Expect a pleasant day with temperatures reaching 25°C...",
    "highlights": [
      "Perfect weather for outdoor activities",
      "Morning conditions ideal for exercise",
      "UV index moderate - sun protection recommended"
    ],
    "warnings": [],
    "best_times": {
      "outdoor_activities": "8am-11am, 4pm-6pm",
      "exercise": "7am-9am"
    }
  },
  "outfit": {
    "summary": "Light layers with sun protection",
    "details": "Wear breathable clothing with light layers...",
    "accessories": ["sunglasses", "sunscreen", "light jacket"],
    "layer_recommendation": "Start with a light jacket for the morning..."
  },
  "activities": [
    {
      "activity": "Running",
      "suitability_score": 90,
      "best_time": "7am-9am",
      "reasoning": "Cool temperatures and low UV make mornings ideal",
      "precautions": ["Stay hydrated", "Wear sunscreen"]
    }
    // ... more activities
  ],
  "health": {
    "uv_risk": "moderate",
    "uv_advice": "Moderate UV risk. Wear sunscreen...",
    "air_quality_risk": "low",
    "air_quality_advice": "Air quality is good...",
    "heat_stress_risk": "low",
    "heat_stress_advice": "Comfortable temperature...",
    "general_health_tips": [
      "Stay hydrated during outdoor activities",
      "Apply sunscreen if outdoors for extended periods"
    ]
  },
  "cached": false
}
```

### Get Daily Summary

#### `GET /ai/summary`

Get AI-generated daily weather summary.

**Authentication**: Required (Premium)

**Query Parameters**: Same as /ai/insights

**Response**: `200 OK`
```json
{
  "summary": {
    "title": "Partly Cloudy with Afternoon Warmth",
    "summary": "Expect a pleasant day...",
    "highlights": [...],
    "warnings": [],
    "best_times": {...}
  }
}
```

### Get Outfit Recommendation

#### `GET /ai/outfit`

Get AI outfit recommendation.

**Authentication**: Required (Premium)

**Response**: `200 OK` - Returns `outfit` object from complete insights

### Get Activity Recommendations

#### `GET /ai/activities`

Get AI activity recommendations.

**Authentication**: Required (Premium)

**Response**: `200 OK` - Returns `activities` array from complete insights

### Get Health Insights

#### `GET /ai/health`

Get health-related weather insights.

**Authentication**: Required (Premium)

**Response**: `200 OK` - Returns `health` object from complete insights

---

## Subscriptions

### Get Subscription Status

#### `GET /subscriptions/status`

Check current user's subscription status and features.

**Authentication**: Required

**Response**: `200 OK`
```json
{
  "has_active_subscription": true,
  "is_premium": true,
  "subscription": {
    "id": "uuid",
    "user_id": "uuid",
    "platform": "apple",
    "plan": "annual",
    "status": "active",
    "is_trial_used": true,
    "trial_start_date": "2024-01-01T00:00:00Z",
    "trial_end_date": "2024-01-08T00:00:00Z",
    "subscription_start_date": "2024-01-08T00:00:00Z",
    "subscription_end_date": "2025-01-08T00:00:00Z",
    "auto_renew": true,
    "is_active": true
  },
  "features": {
    "extended_forecast": true,
    "ai_insights": true,
    "minute_rain": true,
    "severe_alerts": true,
    "air_quality_detailed": true,
    "health_insights": true,
    "travel_analysis": true
  }
}
```

### Start Free Trial

#### `POST /subscriptions/trial`

Start 7-day free trial (one-time per user).

**Authentication**: Required

**Request Body**:
```json
{
  "platform": "apple",
  "plan": "monthly",
  "receipt_data": "base64-encoded-receipt"
}
```

**Response**: `201 Created` - Subscription object

### Activate Subscription

#### `POST /subscriptions/activate`

Activate paid subscription with receipt validation.

**Authentication**: Required

**Request Body**:
```json
{
  "platform": "apple",
  "plan": "annual",
  "receipt_data": "base64-encoded-receipt-or-purchase-token"
}
```

**Response**: `200 OK` - Subscription object

### Cancel Subscription

#### `DELETE /subscriptions/cancel`

Cancel subscription (disables auto-renewal).

**Authentication**: Required

**Response**: `200 OK` - Updated subscription object

### Get Subscription Plans

#### `GET /subscriptions/plans`

Get available subscription plans and pricing.

**Authentication**: None

**Response**: `200 OK`
```json
{
  "plans": [
    {
      "id": "monthly",
      "name": "Monthly Premium",
      "price": 4.99,
      "currency": "USD",
      "billing_period": "month",
      "trial_days": 7,
      "features": [
        "16-day weather forecast",
        "AI-powered insights",
        "Minute-level rain prediction",
        "Severe weather alerts",
        "Detailed air quality breakdown",
        "Health & activity recommendations",
        "Travel weather analysis"
      ]
    },
    {
      "id": "annual",
      "name": "Annual Premium",
      "price": 39.99,
      "currency": "USD",
      "billing_period": "year",
      "trial_days": 7,
      "savings": "33%",
      "features": [
        "All Monthly features",
        "Save $20/year",
        "Priority support"
      ]
    }
  ],
  "trial": {
    "duration_days": 7,
    "features": "Full premium access"
  }
}
```

---

## Error Codes

- `400` - Bad Request (validation error)
- `401` - Unauthorized (missing or invalid token)
- `403` - Forbidden (premium feature requires subscription)
- `404` - Not Found
- `500` - Internal Server Error

---

## Rate Limiting

- **Free tier**: 100 requests/hour
- **Premium**: 1000 requests/hour

Rate limit headers:
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1640000000
```

---

## Webhooks

### Apple App Store Server Notifications

**Endpoint**: `POST /webhooks/apple`

Receives Apple subscription notifications (handled by payment service).

### Google Play Developer Notifications

**Endpoint**: `POST /webhooks/google`

Receives Google Play subscription notifications (handled by payment service).

---

## Data Caching

- **Weather data**: Cached for 30 minutes
- **AI insights**: Cached for 1 hour (per location, per day)
- Cache status indicated by `cached: true` in responses

---

## SDK Examples

### cURL

```bash
# Register
curl -X POST https://api.climaai.com/users/register \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com", "password": "Pass123", "platform": "ios"}'

# Get weather
curl "https://api.climaai.com/weather/current?latitude=40.7128&longitude=-74.0060"

# Get AI insights (with auth)
curl "https://api.climaai.com/ai/insights?latitude=40.7128&longitude=-74.0060" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### JavaScript (Fetch)

```javascript
// Login
const response = await fetch('https://api.climaai.com/users/login', {
  method: 'POST',
  headers: {'Content-Type': 'application/json'},
  body: JSON.stringify({email: 'user@example.com', password: 'Pass123'})
});
const {access_token} = await response.json();

// Get weather with auth
const weather = await fetch(
  'https://api.climaai.com/weather/current?latitude=40.7128&longitude=-74.0060',
  {headers: {'Authorization': `Bearer ${access_token}`}}
).then(r => r.json());
```

### Swift

```swift
// Already implemented in APIClient.swift
let weather = try await APIClient.shared.getWeather(
    latitude: 40.7128,
    longitude: -74.0060
)
```

### Kotlin

```kotlin
// Using Retrofit (see ApiClient.kt)
val weather = apiService.getCurrentWeather(40.7128, -74.0060)
```
