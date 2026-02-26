
import pytest
from unittest.mock import AsyncMock, MagicMock, patch
from fastapi import HTTPException
from backend.api.app.routers.subscriptions import activate_subscription
from backend.api.app.schemas.subscription import SubscriptionCreate
from backend.api.app.models import User, SubscriptionPlan, SubscriptionPlatform, SubscriptionStatus
from backend.api.app.services.receipt_validator import Platform
from uuid import uuid4
from datetime import datetime

# Helper to create a mock subscription with required fields
def create_mock_subscription():
    mock_sub = MagicMock()
    mock_sub.id = uuid4()
    mock_sub.user_id = uuid4()
    mock_sub.platform = SubscriptionPlatform.GOOGLE
    mock_sub.plan = SubscriptionPlan.MONTHLY
    mock_sub.status = SubscriptionStatus.ACTIVE
    mock_sub.is_trial_used = True
    mock_sub.trial_start_date = None
    mock_sub.trial_end_date = None
    mock_sub.subscription_start_date = datetime.now()
    mock_sub.subscription_end_date = datetime.now()
    mock_sub.auto_renew = True
    mock_sub.created_at = datetime.now()
    mock_sub.updated_at = datetime.now()
    # Pydantic v2 model_validate looks for attributes
    return mock_sub

@pytest.mark.asyncio
async def test_activate_subscription_google_success():
    # Setup mocks
    mock_db = AsyncMock()
    mock_user = User(id=uuid4())

    subscription_data = SubscriptionCreate(
        platform=SubscriptionPlatform.GOOGLE,
        plan=SubscriptionPlan.MONTHLY,
        receipt_data="valid_token"
    )

    mock_settings = MagicMock()
    mock_settings.GOOGLE_PRODUCT_ID_MONTHLY = "com.climaai.premium.monthly"

    mock_validator = AsyncMock()
    # Return valid receipt info
    mock_validator.validate_receipt.return_value = (True, {"order_id": "GPA.1234"})

    mock_subscription = create_mock_subscription()

    mock_subscription_service_instance = AsyncMock()
    mock_subscription_service_instance.activate_subscription.return_value = mock_subscription

    # Patch dependencies
    with patch("backend.api.app.routers.subscriptions.get_settings", return_value=mock_settings), \
         patch("backend.api.app.routers.subscriptions.get_receipt_validator", return_value=mock_validator), \
         patch("backend.api.app.routers.subscriptions.SubscriptionService", return_value=mock_subscription_service_instance):

        # Call the function
        response = await activate_subscription(subscription_data, mock_user, mock_db)

        # Verify validate_receipt was called with correct args
        mock_validator.validate_receipt.assert_called_once_with(
            platform=Platform.ANDROID,
            receipt_data="valid_token",
            product_id="com.climaai.premium.monthly"
        )

        # Verify activate_subscription was called with correct args
        mock_subscription_service_instance.activate_subscription.assert_called_once_with(
            mock_user,
            SubscriptionPlatform.GOOGLE,
            SubscriptionPlan.MONTHLY,
            "valid_token", # transaction_id should be the token
            mock_db,
            order_id="GPA.1234"
        )

        assert response.id == mock_subscription.id

@pytest.mark.asyncio
async def test_activate_subscription_google_invalid():
    # Setup mocks
    mock_db = AsyncMock()
    mock_user = User(id=uuid4())

    subscription_data = SubscriptionCreate(
        platform=SubscriptionPlatform.GOOGLE,
        plan=SubscriptionPlan.ANNUAL,
        receipt_data="invalid_token"
    )

    mock_settings = MagicMock()
    mock_settings.GOOGLE_PRODUCT_ID_ANNUAL = "com.climaai.premium.annual"

    mock_validator = AsyncMock()
    # Return invalid receipt info
    mock_validator.validate_receipt.return_value = (False, {"error": "Invalid token"})

    # Patch dependencies
    with patch("backend.api.app.routers.subscriptions.get_settings", return_value=mock_settings), \
         patch("backend.api.app.routers.subscriptions.get_receipt_validator", return_value=mock_validator), \
         patch("backend.api.app.routers.subscriptions.SubscriptionService"):

        # Call the function and expect HTTPException
        with pytest.raises(HTTPException) as excinfo:
            await activate_subscription(subscription_data, mock_user, mock_db)

        assert excinfo.value.status_code == 400
        assert "Invalid Google receipt" in excinfo.value.detail

        # Verify validate_receipt was called
        mock_validator.validate_receipt.assert_called_once_with(
            platform=Platform.ANDROID,
            receipt_data="invalid_token",
            product_id="com.climaai.premium.annual"
        )

@pytest.mark.asyncio
async def test_activate_subscription_google_missing_order_id():
    # Setup mocks
    mock_db = AsyncMock()
    mock_user = User(id=uuid4())

    subscription_data = SubscriptionCreate(
        platform=SubscriptionPlatform.GOOGLE,
        plan=SubscriptionPlan.MONTHLY,
        receipt_data="valid_token_no_order_id"
    )

    mock_settings = MagicMock()
    mock_settings.GOOGLE_PRODUCT_ID_MONTHLY = "com.climaai.premium.monthly"

    mock_validator = AsyncMock()
    # Return valid receipt but without order_id
    mock_validator.validate_receipt.return_value = (True, {})

    mock_subscription = create_mock_subscription()

    mock_subscription_service_instance = AsyncMock()
    mock_subscription_service_instance.activate_subscription.return_value = mock_subscription

    # Patch dependencies
    with patch("backend.api.app.routers.subscriptions.get_settings", return_value=mock_settings), \
         patch("backend.api.app.routers.subscriptions.get_receipt_validator", return_value=mock_validator), \
         patch("backend.api.app.routers.subscriptions.SubscriptionService", return_value=mock_subscription_service_instance):

        await activate_subscription(subscription_data, mock_user, mock_db)

        mock_subscription_service_instance.activate_subscription.assert_called_once()
        call_args = mock_subscription_service_instance.activate_subscription.call_args
        assert call_args.kwargs['order_id'] is None
