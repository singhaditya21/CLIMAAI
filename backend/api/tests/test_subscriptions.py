import pytest
from httpx import AsyncClient
from unittest.mock import AsyncMock, patch
import sys
import os

# Ensure app is importable
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

@pytest.mark.asyncio
async def test_subscription_flow(client: AsyncClient):
    # 1. Register User
    reg = await client.post("/api/auth/register", json={
        "email": "subtest@example.com",
        "password": "password123",
        "full_name": "Sub User",
        "platform": "ios"
    })
    assert reg.status_code == 201
    token = reg.json()["access_token"]
    headers = {"Authorization": f"Bearer {token}"}

    # 2. Check initial status
    res = await client.get("/api/subscriptions/status", headers=headers)
    assert res.status_code == 200
    assert res.json()["is_premium"] == False

    # 3. Start Trial
    res = await client.post("/api/subscriptions/trial", json={
        "platform": "apple",
        "plan": "monthly",
        "receipt_data": "dummy_receipt"
    }, headers=headers)
    assert res.status_code == 201
    # This response is SubscriptionResponse, so it has status
    assert res.json()["status"] == "trial"

    # 4. Check status again
    res = await client.get("/api/subscriptions/status", headers=headers)
    # This response is SubscriptionStatusResponse
    data = res.json()
    assert data["is_premium"] == True
    assert data["subscription"]["status"] == "trial"

@pytest.mark.asyncio
async def test_activate_subscription(client: AsyncClient):
    # Register
    reg = await client.post("/api/auth/register", json={
        "email": "activetest@example.com",
        "password": "password123",
        "full_name": "Active User",
        "platform": "ios"
    })
    assert reg.status_code == 201
    token = reg.json()["access_token"]
    headers = {"Authorization": f"Bearer {token}"}

    # Mock validate_apple_receipt
    with patch("app.services.subscription_service.SubscriptionService.validate_apple_receipt", new_callable=AsyncMock) as mock_verify:
        mock_verify.return_value = {
            "status": 0,
            "latest_receipt_info": [{"transaction_id": "10000001"}]
        }

        # Activate
        res = await client.post("/api/subscriptions/activate", json={
            "platform": "apple",
            "plan": "monthly",
            "receipt_data": "fake_receipt"
        }, headers=headers)

        assert res.status_code == 200
        assert res.json()["status"] == "active"
