import sys
import os
import timeit
from datetime import datetime, timedelta

# Add backend/api to sys.path to make imports work
sys.path.append(os.getcwd())

try:
    from app.services.nowcast_service import NowcastService
except ImportError:
    # Try alternate import if running from different location
    sys.path.append(os.path.join(os.getcwd(), 'backend', 'api'))
    from app.services.nowcast_service import NowcastService

def run_benchmark():
    service = NowcastService()

    # Create sample data
    # 2 hours of data (120 minutes) -> 8 15-minute intervals
    # Let's create 10 intervals to be safe
    base_time = datetime(2023, 1, 1, 12, 0, 0)
    data_15min = []

    # Generate 15-minute data points
    # (time, precipitation, probability)
    for i in range(10):
        t = base_time + timedelta(minutes=15 * i)
        precip = float(i % 5) * 2.0  # varying precipitation
        prob = int((i * 10) % 100)
        data_15min.append((t, precip, prob))

    # Benchmark function
    # We call _interpolate_minutes directly
    # Note: access to private method for benchmarking purposes
    def benchmark_func():
        return service._interpolate_minutes(data_15min)

    # Run benchmark
    number = 10000
    timer = timeit.Timer(benchmark_func)
    time_taken = timer.timeit(number=number)

    avg_time_ms = (time_taken / number) * 1000
    print(f"Total time for {number} runs: {time_taken:.4f} seconds")
    print(f"Average time per run: {avg_time_ms:.4f} ms")

    # Also verify correctness - basic check
    result = benchmark_func()
    print(f"Generated {len(result)} minute entries")
    if len(result) > 0:
        print(f"First entry: {result[0]}")
        print(f"Last entry: {result[-1]}")

if __name__ == "__main__":
    run_benchmark()
