import asyncio
import time
import json
import sys
import os
import uvicorn
from fastapi import FastAPI
from multiprocessing import Process
from unittest.mock import MagicMock, AsyncMock

# Add backend/api to sys.path to allow imports
current_dir = os.path.dirname(os.path.abspath(__file__))
sys.path.append(current_dir)

# Mock redis before importing service to avoid connection errors if redis is not running
import app.services.radar_service as radar_service_module
radar_service_module.redis = MagicMock()
radar_service_module.redis.from_url = AsyncMock(return_value=AsyncMock())
# Also mock the redis instance methods
mock_redis_client = AsyncMock()
mock_redis_client.get.return_value = None # Cache miss
radar_service_module.redis.from_url.return_value = mock_redis_client

from app.services.radar_service import RadarService

# Mock HTTP Server
MOCK_PORT = 8001
MOCK_HOST = f"http://localhost:{MOCK_PORT}"

mock_app = FastAPI()

@mock_app.get("/public/weather-maps.json")
async def get_radar_maps():
    return {
        "radar": {
            "past": [{"time": 1706695200, "path": "/archive/1706695200"}],
            "nowcast": [{"time": 1706698800, "path": "/nowcast/1706698800"}]
        },
        "host": MOCK_HOST,
        "generated": 1706698800
    }

def run_mock_server():
    uvicorn.run(mock_app, host="localhost", port=MOCK_PORT, log_level="error")

async def benchmark():
    # Start mock server
    print(f"Starting mock server on {MOCK_HOST}...")
    server_process = Process(target=run_mock_server)
    server_process.start()

    # Wait for server to start
    await asyncio.sleep(2)

    try:
        # Override API URL for benchmark
        original_api = RadarService.RAINVIEWER_API
        RadarService.RAINVIEWER_API = f"{MOCK_HOST}/public/weather-maps.json"

        iterations = 100

        print(f"Running benchmark with {iterations} iterations...")

        # Scenario A: Inefficient (New instance per request)
        print("Running Scenario A: New Instance per request...")
        start_time = time.time()
        for i in range(iterations):
            service = RadarService()
            try:
                await service.get_radar_frames()
            finally:
                await service.close()
            if (i + 1) % 10 == 0:
                print(f"  Processed {i + 1}/{iterations} requests", end="\r")
        print()
        end_time = time.time()
        scenario_a_time = end_time - start_time
        print(f"Scenario A Time: {scenario_a_time:.4f} seconds")
        print(f"Avg time per request: {scenario_a_time/iterations*1000:.2f} ms")

        # Scenario B: Efficient (Reused instance)
        print("Running Scenario B: Reused Instance...")
        start_time = time.time()
        service = RadarService()
        try:
            for i in range(iterations):
                await service.get_radar_frames()
                if (i + 1) % 10 == 0:
                    print(f"  Processed {i + 1}/{iterations} requests", end="\r")
            print()
        finally:
            await service.close()
        end_time = time.time()
        scenario_b_time = end_time - start_time
        print(f"Scenario B Time: {scenario_b_time:.4f} seconds")
        print(f"Avg time per request: {scenario_b_time/iterations*1000:.2f} ms")

        # Calculate improvement
        if scenario_a_time > 0:
            improvement = (scenario_a_time - scenario_b_time) / scenario_a_time * 100
            print(f"Improvement: {improvement:.2f}%")
            print(f"Speedup: {scenario_a_time/scenario_b_time:.2f}x")

    finally:
        print("Stopping mock server...")
        server_process.terminate()
        server_process.join()

if __name__ == "__main__":
    asyncio.run(benchmark())
