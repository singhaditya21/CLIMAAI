"""Unavailable pollen data must not be presented as a measured forecast."""

import pytest
from fastapi import HTTPException

from app.services.pollen_service import PollenService


@pytest.mark.asyncio
async def test_no_api_key_does_not_generate_pollen_forecast():
    service = PollenService()
    service.api_key = ""
    try:
        with pytest.raises(HTTPException) as error:
            await service.get_pollen_forecast(51.5, -0.12)
        assert error.value.status_code == 503
    finally:
        await service.close()


def test_google_pollen_list_contract_and_missing_index():
    service = object.__new__(PollenService)
    types = [{
        "code": "TREE",
        "indexInfo": {"value": 4},
        "healthRecommendations": ["Limit outdoor exposure"],
    }]
    plants = [{
        "displayName": "Birch",
        "plantDescription": {"type": "TREE"},
    }]

    tree = service._parse_pollen_type(types, plants, "tree")
    assert tree.index == 4
    assert tree.species == ["Birch"]
    assert tree.health_advice == "Limit outdoor exposure"

    with pytest.raises(ValueError, match="No measured grass"):
        service._parse_pollen_type(types, plants, "grass")
