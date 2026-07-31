"""Pollen fallback data.

The month-end case is a regression test: the generator built each forecast day
with date(year, month, day + i), which raised "day is out of range for month"
from the 28th onward — the endpoint was broken for the last few days of every
month.
"""
from datetime import date

import pytest

from app.services.pollen_service import PollenService


@pytest.fixture
def service():
    return PollenService()


@pytest.mark.parametrize(
    "today",
    [
        date(2026, 7, 31),   # month end, the case that raised
        date(2026, 1, 30),
        date(2026, 2, 28),   # short month
        date(2028, 2, 28),   # leap year, day before the 29th
        date(2026, 12, 31),  # year boundary
        date(2026, 6, 15),   # mid-month control
    ],
)
def test_forecast_spans_five_consecutive_days(service, monkeypatch, today):
    class FrozenDate(date):
        @classmethod
        def today(cls):
            return today

    monkeypatch.setattr("app.services.pollen_service.date", FrozenDate)

    result = service._get_mock_pollen_data(51.5, -0.12)

    dates = [day.date for day in result.forecast]
    assert len(dates) == 5
    # Consecutive, and crossing month and year boundaries correctly.
    for earlier, later in zip(dates, dates[1:]):
        assert (later - earlier).days == 1
    assert dates[0] == today


def test_mock_data_is_returned_without_an_api_key(service):
    result = service._get_mock_pollen_data(51.5, -0.12)

    assert result.forecast
    first = result.forecast[0]
    assert first.tree.index >= 0
    assert first.grass.index >= 0
    assert first.weed.index >= 0
