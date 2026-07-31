"""Minute-level precipitation nowcast. Pure functions — no network.

Covers intensity banding, the 15-minute to per-minute interpolation, and the
precipitation window search. The 120-minute cap matters: the service promises a
two-hour horizon regardless of how much upstream data it is handed.
"""
from datetime import datetime, timedelta

import pytest

from app.services.nowcast_service import NowcastMinute, NowcastService

START = datetime(2026, 7, 31, 12, 0)


@pytest.fixture
def service():
    return NowcastService()


def _series(values, start=START):
    """Build (time, precipitation_mm, probability) tuples at 15-minute spacing."""
    return [
        (start + timedelta(minutes=15 * i), precip, prob)
        for i, (precip, prob) in enumerate(values)
    ]


# --------------------------------------------------------------------------
# Intensity classification
# --------------------------------------------------------------------------

@pytest.mark.parametrize(
    "precip_15min,expected",
    [
        (0.0, "none"),
        (0.01, "none"),      # 0.04 mm/h
        (0.3, "light"),      # 1.2 mm/h
        (1.0, "moderate"),   # 4.0 mm/h
        (5.0, "heavy"),      # 20 mm/h
    ],
)
def test_intensity_bands(service, precip_15min, expected):
    assert service._classify_intensity(precip_15min) == expected


def test_intensity_is_monotonic(service):
    """More rain must never classify as a lighter band."""
    order = ["none", "light", "moderate", "heavy"]
    seen = [service._classify_intensity(v) for v in [0.0, 0.2, 1.0, 3.0, 10.0]]
    indices = [order.index(band) for band in seen]

    assert indices == sorted(indices)


# --------------------------------------------------------------------------
# Interpolation
# --------------------------------------------------------------------------

def test_interpolation_expands_to_one_entry_per_minute(service):
    minutes = service._interpolate_minutes(_series([(1.0, 80), (1.0, 80)]))

    assert len(minutes) == 30
    assert minutes[0].time == START
    assert minutes[1].time == START + timedelta(minutes=1)


def test_interpolation_is_capped_at_two_hours(service):
    """Twenty 15-minute buckets is five hours of input; output stays at 120."""
    minutes = service._interpolate_minutes(_series([(0.5, 50)] * 20))

    assert len(minutes) == 120


def test_interpolation_moves_between_endpoints(service):
    """Values should ramp from the first bucket toward the second."""
    minutes = service._interpolate_minutes(_series([(0.0, 0), (1.5, 90)]))

    first_quarter = minutes[:15]
    assert first_quarter[0].precipitation < first_quarter[-1].precipitation
    assert first_quarter[0].precipitation_probability <= first_quarter[-1].precipitation_probability


def test_probabilities_stay_in_range(service):
    """Interpolating between extremes must not overshoot 0-100."""
    minutes = service._interpolate_minutes(_series([(0.0, 0), (9.0, 100), (0.0, 0)]))

    assert all(0 <= m.precipitation_probability <= 100 for m in minutes)


def test_dry_series_is_marked_as_no_precipitation(service):
    minutes = service._interpolate_minutes(_series([(0.0, 0), (0.0, 0)]))

    assert all(not m.is_precipitation for m in minutes)
    assert all(m.intensity == "none" for m in minutes)


def test_empty_input_yields_no_minutes(service):
    assert service._interpolate_minutes([]) == []


# --------------------------------------------------------------------------
# Precipitation window
# --------------------------------------------------------------------------

def _minute(offset, wet):
    return NowcastMinute(
        time=START + timedelta(minutes=offset),
        precipitation=0.5 if wet else 0.0,
        precipitation_probability=90 if wet else 0,
        intensity="light" if wet else "none",
        is_precipitation=wet,
    )


def test_window_spans_first_to_last_wet_minute(service):
    minutes = [_minute(i, wet=10 <= i <= 40) for i in range(60)]

    start, end = service._find_precipitation_window(minutes)

    assert start == START + timedelta(minutes=10)
    assert end == START + timedelta(minutes=40)


def test_window_is_none_when_dry(service):
    minutes = [_minute(i, wet=False) for i in range(60)]

    assert service._find_precipitation_window(minutes) == (None, None)


def test_window_covers_intermittent_rain(service):
    """Two separate showers report one span from first to last."""
    minutes = [_minute(i, wet=i in {5, 6, 50, 51}) for i in range(60)]

    start, end = service._find_precipitation_window(minutes)

    assert start == START + timedelta(minutes=5)
    assert end == START + timedelta(minutes=51)


def test_summary_is_produced_for_wet_and_dry(service):
    wet = [_minute(i, wet=i < 30) for i in range(60)]
    dry = [_minute(i, wet=False) for i in range(60)]

    wet_start, wet_end = service._find_precipitation_window(wet)

    assert service._generate_summary(wet, wet_start, wet_end, 3.0)
    assert service._generate_summary(dry, None, None, 0.0)
