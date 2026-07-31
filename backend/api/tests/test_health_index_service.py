"""Flu and migraine risk scoring. Pure functions — no database, no network.

The migraine tests are the interesting ones: the algorithm keys off the pressure
*trend* over 24 hours, so a flat history must register no pressure movement. An
earlier version of this codebase fed it a synthetic flat list, which made the
index constant regardless of the actual weather.
"""
from datetime import date

import pytest

from app.services.health_index_service import HealthIndexService, RiskLevel


@pytest.fixture
def service():
    return HealthIndexService()


# --------------------------------------------------------------------------
# Flu risk
# --------------------------------------------------------------------------

def test_cold_dry_winter_scores_higher_than_warm_humid_summer(service):
    winter = service.calculate_flu_risk(
        temperature=2.0, humidity=30, current_date=date(2026, 1, 15), latitude=40.0
    )
    summer = service.calculate_flu_risk(
        temperature=28.0, humidity=70, current_date=date(2026, 7, 15), latitude=40.0
    )

    assert winter.risk_score > summer.risk_score


@pytest.mark.parametrize(
    "temperature,humidity,month",
    [(-20.0, 10, 1), (45.0, 100, 7), (15.0, 50, 4), (0.0, 0, 12)],
)
def test_flu_score_stays_within_bounds(service, temperature, humidity, month):
    risk = service.calculate_flu_risk(
        temperature=temperature,
        humidity=humidity,
        current_date=date(2026, month, 15),
        latitude=40.0,
    )

    assert 0 <= risk.risk_score <= 100


def test_flu_risk_reports_contributing_factors(service):
    risk = service.calculate_flu_risk(
        temperature=-5.0, humidity=20, current_date=date(2026, 1, 15), latitude=40.0
    )

    assert risk.factors
    assert risk.recommendations
    assert risk.seasonal_context


def test_flu_risk_level_is_a_known_value(service):
    risk = service.calculate_flu_risk(
        temperature=5.0, humidity=35, current_date=date(2026, 2, 1), latitude=40.0
    )

    assert risk.risk_level in set(RiskLevel)


def test_southern_hemisphere_winter_is_mid_year(service):
    """July is winter below the equator, so it must not score like summer."""
    southern_july = service.calculate_flu_risk(
        temperature=5.0, humidity=35, current_date=date(2026, 7, 15), latitude=-33.0
    )
    southern_january = service.calculate_flu_risk(
        temperature=5.0, humidity=35, current_date=date(2026, 1, 15), latitude=-33.0
    )

    assert southern_july.risk_score >= southern_january.risk_score


# --------------------------------------------------------------------------
# Migraine risk
# --------------------------------------------------------------------------

def test_falling_pressure_scores_higher_than_stable(service):
    stable = service.calculate_migraine_risk(
        current_pressure=1013.0,
        pressure_history=[1013.0] * 24,
        humidity=50,
        temperature=20.0,
    )
    falling = service.calculate_migraine_risk(
        current_pressure=1000.0,
        pressure_history=[1015.0] * 24,
        humidity=50,
        temperature=20.0,
    )

    assert falling.risk_score > stable.risk_score


def test_flat_history_reports_no_pressure_movement(service):
    """Guards the bug where a synthetic flat history hid all pressure change."""
    risk = service.calculate_migraine_risk(
        current_pressure=1013.0,
        pressure_history=[1013.0] * 24,
        humidity=50,
        temperature=20.0,
    )

    assert risk.pressure_change == pytest.approx(0.0)
    assert risk.pressure_trend == "stable"


def test_sharp_drop_is_reported_as_falling(service):
    risk = service.calculate_migraine_risk(
        current_pressure=995.0,
        pressure_history=[1020.0] * 24,
        humidity=50,
        temperature=20.0,
    )

    # A 25mb drop is the "rapidly" variant of falling.
    assert risk.pressure_trend.startswith("falling")
    assert risk.pressure_change < 0
    assert risk.triggers


def test_rising_pressure_is_reported_as_rising(service):
    risk = service.calculate_migraine_risk(
        current_pressure=1025.0,
        pressure_history=[1005.0] * 24,
        humidity=50,
        temperature=20.0,
    )

    assert risk.pressure_trend.startswith("rising")
    assert risk.pressure_change > 0


def test_pressure_change_is_measured_against_the_oldest_reading(service):
    """History is oldest-first; the delta is current minus the first entry."""
    history = [1000.0] + [1010.0] * 23

    risk = service.calculate_migraine_risk(
        current_pressure=1012.0, pressure_history=history, humidity=50, temperature=20.0
    )

    assert risk.pressure_change == pytest.approx(12.0)


def test_empty_pressure_history_is_handled(service):
    """Callers may have no stored history yet; this must not raise."""
    risk = service.calculate_migraine_risk(
        current_pressure=1013.0, pressure_history=[], humidity=50, temperature=20.0
    )

    assert 0 <= risk.risk_score <= 100


@pytest.mark.parametrize("humidity", [10, 95])
def test_humidity_extremes_raise_migraine_risk(service, humidity):
    extreme = service.calculate_migraine_risk(
        current_pressure=1013.0,
        pressure_history=[1013.0] * 24,
        humidity=humidity,
        temperature=20.0,
    )
    comfortable = service.calculate_migraine_risk(
        current_pressure=1013.0,
        pressure_history=[1013.0] * 24,
        humidity=50,
        temperature=20.0,
    )

    assert extreme.risk_score > comfortable.risk_score


def test_migraine_score_stays_within_bounds(service):
    risk = service.calculate_migraine_risk(
        current_pressure=960.0,
        pressure_history=[1040.0] * 24,
        humidity=99,
        temperature=42.0,
    )

    assert 0 <= risk.risk_score <= 100
    assert risk.risk_level in set(RiskLevel)
    assert risk.recommendations
