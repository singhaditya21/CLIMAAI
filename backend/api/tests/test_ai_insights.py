"""Honest attribution of AI insights.

Without OPENAI_API_KEY (or with ENABLE_AI_INSIGHTS off) every insight is
template text from the fallback rules. The response must say so: each insight
model carries generated_by = "rules" | "llm", and nothing here may label a
template as LLM output. These tests pin both directions, plus the cache round
trip and the travel fallback that used to fail response validation outright.

The OpenAI client is stubbed at the attribute level — AIService keeps it in
self.openai_client — so no test depends on environment keys. The Redis cache is
stubbed the same way to keep tests deterministic on machines that run one.
"""
import json
from datetime import datetime, timezone
from unittest.mock import patch

import pytest

from app.schemas.ai import InsightSource, TravelRiskAnalysis
from app.schemas.weather import CurrentWeather, DailyWeather, HourlyWeather, WeatherResponse
from app.services.ai_service import AIService


def weather_fixture(**current_overrides) -> WeatherResponse:
    now = datetime.now(timezone.utc)
    current = {
        "temperature": 21.0,
        "feels_like": 21.5,
        "humidity": 55,
        "wind_speed": 12.0,
        "wind_direction": 180,
        "precipitation": 0.0,
        "weather_code": 1,
        "weather_description": "Mainly clear",
        "cloud_cover": 20,
        "pressure": 1015.0,
        "visibility": 20000.0,
        "uv_index": 4.0,
        "is_day": True,
        "timestamp": now,
    }
    current.update(current_overrides)

    hourly = [
        HourlyWeather(
            time=now,
            temperature=20.0,
            feels_like=20.0,
            precipitation_probability=10,
            precipitation=0.0,
            weather_code=1,
            weather_description="Mainly clear",
            wind_speed=10.0,
            wind_direction=180,
            humidity=55,
            cloud_cover=20,
            uv_index=3.0,
        )
    ]

    daily = [
        DailyWeather(
            date="2026-08-05",
            temperature_max=25.0,
            temperature_min=15.0,
            sunrise="05:45",
            sunset="20:30",
            precipitation_sum=0.0,
            precipitation_probability=10,
            weather_code=1,
            weather_description="Mainly clear",
            wind_speed_max=18.0,
            wind_direction=180,
            uv_index_max=6.0,
        )
    ]

    return WeatherResponse(
        current=CurrentWeather(**current),
        hourly=hourly,
        daily=daily,
        location={"latitude": 52.52, "longitude": 13.405, "name": "Berlin"},
        timezone="Europe/Berlin",
    )


class _StubCompletions:
    """Stands in for openai_client.chat.completions, replaying canned JSON."""

    def __init__(self, payloads):
        self._payloads = list(payloads)
        self.calls = 0

    async def create(self, **kwargs):
        class Message:
            pass

        class Choice:
            pass

        class Response:
            pass

        payload = self._payloads[self.calls % len(self._payloads)]
        self.calls += 1

        message = Message()
        message.content = json.dumps(payload)
        choice = Choice()
        choice.message = message
        response = Response()
        response.choices = [choice]
        return response


class _StubOpenAI:
    def __init__(self, payloads):
        class Chat:
            pass

        self.chat = Chat()
        self.chat.completions = _StubCompletions(payloads)


def offline_service() -> AIService:
    """An AIService with no LLM and no cache, regardless of environment."""
    service = AIService()
    service.openai_client = None
    return service


@pytest.fixture(autouse=True)
def no_cache():
    """Cache misses always, stores nowhere: keeps tests independent of Redis
    state and of each other (the cache key is only lat/lon/date)."""

    async def miss(self, cache_key):
        return None

    async def drop(self, cache_key, data):
        return None

    with patch.object(AIService, "_get_cached_insight", miss), \
         patch.object(AIService, "_set_cached_insight", drop):
        yield


async def test_without_a_key_every_insight_says_rules():
    service = offline_service()

    insights = await service.generate_complete_insights(
        weather_fixture(), "Berlin", include_travel=True
    )

    assert insights.generated_by == InsightSource.RULES
    assert insights.daily_summary.generated_by == InsightSource.RULES
    assert insights.outfit.generated_by == InsightSource.RULES
    assert insights.health.generated_by == InsightSource.RULES
    assert insights.travel.generated_by == InsightSource.RULES
    assert all(a.generated_by == InsightSource.RULES for a in insights.activities)


async def test_llm_generated_insights_say_llm():
    summary = {
        "title": "Clear skies",
        "summary": "A calm day.",
        "highlights": ["Mild", "Dry"],
        "warnings": [],
        "best_times": {"outdoor_activities": "10:00-16:00"},
    }
    outfit = {
        "summary": "Light layers.",
        "details": "T-shirt with a light jacket.",
        "accessories": ["sunglasses"],
        "layer_recommendation": "Shed the jacket at midday.",
    }
    activities = {
        "activities": [
            {
                "activity": "Cycling",
                "suitability_score": 90,
                "best_time": "morning",
                "reasoning": "Dry and mild",
                "precautions": ["Wear a helmet"],
            }
        ]
    }
    travel = {
        "overall_risk": "LOW",  # uppercase on purpose: models do this
        "summary": "Safe to travel.",
        "severe_weather_alerts": [],
        "travel_tips": ["Standard precautions"],
        "best_travel_times": ["Morning"],
        "worst_travel_times": ["None expected"],
    }

    service = AIService()
    service.openai_client = _StubOpenAI([summary, outfit, activities, travel])

    insights = await service.generate_complete_insights(
        weather_fixture(), "Berlin", include_travel=True
    )

    assert insights.generated_by == InsightSource.LLM
    assert insights.daily_summary.generated_by == InsightSource.LLM
    assert insights.outfit.generated_by == InsightSource.LLM
    assert insights.travel.generated_by == InsightSource.LLM
    assert all(a.generated_by == InsightSource.LLM for a in insights.activities)
    # Health never goes through the LLM, and must never claim to.
    assert insights.health.generated_by == InsightSource.RULES


async def test_a_partial_fallback_keeps_the_aggregate_at_rules():
    """One template section anywhere means the bundle is not 'AI'."""

    class _FailingOpenAI(_StubOpenAI):
        def __init__(self, payloads, fail_on_call):
            super().__init__(payloads)
            original = self.chat.completions.create
            call_index = {"n": 0}

            async def create(**kwargs):
                call_index["n"] += 1
                if call_index["n"] == fail_on_call:
                    raise RuntimeError("model unavailable")
                return await original(**kwargs)

            self.chat.completions.create = create

    summary = {
        "title": "Clear",
        "summary": "Calm.",
        "highlights": ["Mild"],
    }
    outfit = {
        "summary": "Light layers.",
        "details": "T-shirt.",
        "layer_recommendation": "None needed.",
    }
    activities = {"activities": []}

    # Second call (the outfit) falls back to templates.
    service = AIService()
    service.openai_client = _FailingOpenAI([summary, outfit, activities], fail_on_call=2)

    insights = await service.generate_complete_insights(weather_fixture(), "Berlin")

    assert insights.daily_summary.generated_by == InsightSource.LLM
    assert insights.outfit.generated_by == InsightSource.RULES
    assert insights.generated_by == InsightSource.RULES


async def test_cache_entries_older_than_the_field_deserialize_as_rules():
    """Pre-field cache payloads carry no label; the safe reading is 'rules'."""
    cached = {
        "title": "Cached headline",
        "summary": "Cached summary.",
        "highlights": ["one"],
        "warnings": [],
        "best_times": {},
    }

    async def hit(self, cache_key):
        return dict(cached)

    service = offline_service()
    with patch.object(AIService, "_get_cached_insight", hit):
        summary = await service.generate_daily_summary(weather_fixture(), "Berlin")

    assert summary.generated_by == InsightSource.RULES


async def test_the_travel_fallback_produces_the_documented_shape():
    """Regression: the old fallback built fields the response model does not
    have, so /api/travel-risk answered 500 whenever the LLM was unavailable."""
    service = offline_service()
    stormy = weather_fixture(
        precipitation=12.0, wind_speed=60.0, cloud_cover=100, temperature=-8.0
    )

    travel = await service.generate_travel_risk_analysis(stormy, "Munich")

    assert isinstance(travel, TravelRiskAnalysis)
    assert travel.generated_by == InsightSource.RULES
    assert travel.overall_risk.value == "very_high"
    assert travel.severe_weather_alerts  # the dangerous conditions are named
    assert travel.travel_tips
    assert travel.best_travel_times and travel.worst_travel_times


async def test_the_insights_endpoint_reports_the_source(auth_client):
    """End to end through response_model: the JSON a client sees carries the
    label on the bundle and on every section."""
    trial = await auth_client.post(
        "/api/subscriptions/trial",
        json={"platform": "apple", "plan": "monthly", "receipt_data": "-"},
    )
    assert trial.status_code == 201, trial.text

    class StubWeatherService:
        async def get_current_weather(self, latitude, longitude):
            return weather_fixture()

        async def close(self):
            return None

    with patch("app.routers.ai.WeatherService", StubWeatherService), \
         patch("app.routers.ai.AIService", offline_service):
        response = await auth_client.get(
            "/api/insights", params={"latitude": 52.52, "longitude": 13.405}
        )

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["generated_by"] == "rules"
    assert body["daily_summary"]["generated_by"] == "rules"
    assert body["outfit"]["generated_by"] == "rules"
    assert body["health"]["generated_by"] == "rules"
    assert all(a["generated_by"] == "rules" for a in body["activities"])
