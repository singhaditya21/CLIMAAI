import pytest
import respx
import json
from httpx import Response
import sys
import os

# Adjust path to import from app
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

from app.services.subscription_service import SubscriptionService
from app.config import get_settings

@pytest.mark.asyncio
async def test_validate_apple_receipt_uses_configured_secret():
    settings = get_settings()
    # Force a specific secret for testing
    original_secret = settings.APPLE_SHARED_SECRET
    settings.APPLE_SHARED_SECRET = "test-secret-123"

    try:
        service = SubscriptionService()
        receipt_data = "fake-receipt-data"

        # Mock the Apple verify receipt endpoint
        async with respx.mock(base_url="https://sandbox.itunes.apple.com") as respx_mock:
            route = respx_mock.post("/verifyReceipt").mock(return_value=Response(200, json={"status": 0}))

            await service.validate_apple_receipt(receipt_data, sandbox=True)

            assert route.called
            request = route.calls.last.request
            payload = json.loads(request.content)

            # Verify that the password field matches the configured secret
            # This assertion is expected to fail initially because the code is hardcoded
            assert payload["password"] == "test-secret-123"
            assert payload["receipt-data"] == receipt_data
    finally:
        # Restore original secret (though settings is re-instantiated usually, but good practice)
        settings.APPLE_SHARED_SECRET = original_secret
