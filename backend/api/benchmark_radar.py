import asyncio
import time
import httpx
import uvicorn
from fastapi import FastAPI
from threading import Thread
from unittest.mock import MagicMock, AsyncMock

# Patching settings before importing RadarService to avoid environment issues
import os
os.environ["REDIS_URL"] = "redis://localhost:6379/0"
os.environ["OPEN_METEO_BASE_URL"] = "https://api.open-meteo.com/v1"
os.environ["OPEN_METEO_AIR_QUALITY_URL"] = "https://air-quality-api.open-meteo.com/v1"
os.environ["OPEN_METEO_API_KEY"] = ""

# Import RadarService
# We need to make sure we are in the right directory or pythonpath
import sys
import os
sys.path.append(os.path.join(os.path.dirname(__file__), "app"))

from app.services.radar_service import RadarService

# --- Mock Server ---
mock_app = FastAPI()

MOCK_RESPONSE = {
    "host": "http://localhost:8001",
    "generated": 1706695200,
    "past": [
        {"time": 1706691600, "path": "/archive/2024-01-31/1000"},
        {"time": 1706692200, "path": "/archive/2024-01-31/1010"}
    ],
    "nowcast": [
        {"time": 1706695800, "path": "/nowcast/2024-01-31/1110"}
    ]
}

@mock_app.get("/weather-maps.json")
async def get_radar_frames():
    return MOCK_RESPONSE

def run_server():
    uvicorn.run(mock_app, host="127.0.0.1", port=8001, log_level="error")

# --- Benchmark ---

async def run_benchmark():
    print("Starting mock server...")
    server_thread = Thread(target=run_server, daemon=True)
    server_thread.start()
    # Give server time to start
    await asyncio.sleep(2)

    ITERATIONS = 50

    print(f"\nRunning benchmark with {ITERATIONS} iterations...")

    # --- Scenario A: New Instance Per Request ---
    print("\nScenario A: New Service Instance Per Request")
    start_time = time.time()

    for i in range(ITERATIONS):
        # We subclass to override the API URL and mock Redis
        class TestRadarService(RadarService):
            RAINVIEWER_API = "http://127.0.0.1:8001/weather-maps.json"
            async def _get_redis(self):
                # Return a mock that fails get/set so we always hit HTTP
                mock = MagicMock()
                mock.get = AsyncMock(return_value=None)
                mock.setex = AsyncMock()
                return mock

        service = TestRadarService()
        try:
            await service.get_radar_frames()
        finally:
            await service.close()

    end_time = time.time()
    scenario_a_time = end_time - start_time
    print(f"Total time: {scenario_a_time:.4f}s")
    print(f"Avg time per req: {scenario_a_time/ITERATIONS*1000:.2f}ms")

    # --- Scenario B: Reused Instance ---
    print("\nScenario B: Reused Service Instance")

    class TestRadarService(RadarService):
        RAINVIEWER_API = "http://127.0.0.1:8001/weather-maps.json"
        async def _get_redis(self):
            mock = MagicMock()
            mock.get = AsyncMock(return_value=None)
            mock.setex = AsyncMock()
            return mock

    service = TestRadarService()
    start_time = time.time()

    try:
        for i in range(ITERATIONS):
            await service.get_radar_frames()
    finally:
        await service.close()

    end_time = time.time()
    scenario_b_time = end_time - start_time
    print(f"Total time: {scenario_b_time:.4f}s")
    print(f"Avg time per req: {scenario_b_time/ITERATIONS*1000:.2f}ms")

    # --- Results ---
    print("\n" + "="*40)
    print("BENCHMARK RESULTS")
    print("="*40)
    print(f"Scenario A (New Instance): {scenario_a_time:.4f}s")
    print(f"Scenario B (Reused):       {scenario_b_time:.4f}s")

    improvement = scenario_a_time - scenario_b_time
    percent = (improvement / scenario_a_time) * 100

    print(f"Improvement: {improvement:.4f}s ({percent:.2f}%)")

    if scenario_b_time < scenario_a_time:
        print("✅ Optimization validated!")
    else:
        print("❌ No improvement detected.")

if __name__ == "__main__":
    asyncio.run(run_benchmark())
