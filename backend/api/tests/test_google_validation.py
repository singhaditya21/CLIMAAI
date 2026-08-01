import pytest
from unittest.mock import Mock, patch, MagicMock
from app.services.subscription_service import SubscriptionService
from app.config import Settings
import json

# Mock settings
@pytest.fixture
def mock_settings():
    with patch("app.services.subscription_service.get_settings") as mock_get_settings:
        settings = Mock(spec=Settings)
        # Default mock settings
        settings.GOOGLE_SERVICE_ACCOUNT_JSON = json.dumps({
            "type": "service_account",
            "project_id": "test-project",
            "private_key_id": "test-key-id",
            "private_key": "-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC...\n-----END PRIVATE KEY-----\n",
            "client_email": "test@test-project.iam.gserviceaccount.com",
            "client_id": "123456789",
            "auth_uri": "https://accounts.google.com/o/oauth2/auth",
            "token_uri": "https://oauth2.googleapis.com/token",
            "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
            "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/test%40test-project.iam.gserviceaccount.com"
        })
        mock_get_settings.return_value = settings
        yield settings

@pytest.fixture
def service():
    return SubscriptionService()

@pytest.mark.asyncio
async def test_validate_google_purchase_success(mock_settings, service):
    # Mock Google credentials and build
    # Patched only to stop it reaching Google; the mock itself is not asserted on.
    with patch("google.oauth2.service_account.Credentials.from_service_account_info"), \
         patch("app.services.subscription_service.build") as mock_build:

        # Setup mock service
        mock_google_service = MagicMock()
        mock_build.return_value = mock_google_service

        # Setup API call chain
        mock_purchases = mock_google_service.purchases.return_value
        mock_subscriptions = mock_purchases.subscriptions.return_value
        mock_get = mock_subscriptions.get.return_value

        expected_response = {
            "kind": "androidpublisher#subscriptionPurchase",
            "paymentState": 1,
            "expiryTimeMillis": "1700000000000",
            "autoRenewing": True
        }
        mock_get.execute.return_value = expected_response

        # Call the method
        package_name = "com.climaai.app"
        product_id = "climaai_pro_monthly"
        token = "test_token"

        response = await service.validate_google_purchase(package_name, product_id, token)

        # Assertions
        assert response == expected_response

        # Verify API called with correct params
        mock_subscriptions.get.assert_called_once_with(
            packageName=package_name,
            subscriptionId=product_id,
            token=token
        )
        mock_get.execute.assert_called_once()

@pytest.mark.asyncio
async def test_validate_google_purchase_missing_credentials(mock_settings, service):
    # Set credentials to empty string
    mock_settings.GOOGLE_SERVICE_ACCOUNT_JSON = ""

    with pytest.raises(ValueError, match="Google service account credentials not configured"):
        await service.validate_google_purchase("pkg", "prod", "token")

@pytest.mark.asyncio
async def test_validate_google_purchase_api_error(mock_settings, service):
    with patch("google.oauth2.service_account.Credentials.from_service_account_info"), \
         patch("app.services.subscription_service.build") as mock_build:

        mock_google_service = MagicMock()
        mock_build.return_value = mock_google_service

        mock_purchases = mock_google_service.purchases.return_value
        mock_subscriptions = mock_purchases.subscriptions.return_value
        mock_get = mock_subscriptions.get.return_value

        # Simulate API error
        mock_get.execute.side_effect = Exception("API Error")

        with pytest.raises(Exception, match="API Error"):
            await service.validate_google_purchase("pkg", "prod", "token")
