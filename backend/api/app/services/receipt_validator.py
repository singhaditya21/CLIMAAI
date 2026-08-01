"""
Receipt validation service for Apple App Store and Google Play.
Validates subscription receipts server-to-server.
"""
import httpx
import jwt
import json
from typing import Dict, Optional, Tuple
from datetime import datetime, timedelta, timezone
from enum import Enum


class Platform(str, Enum):
    IOS = "ios"
    ANDROID = "android"


class ReceiptValidator:
    """
    Server-side receipt validation for iOS and Android subscriptions.
    """
    
    # Apple App Store endpoints
    APPLE_PRODUCTION_URL = "https://buy.itunes.apple.com/verifyReceipt"
    APPLE_SANDBOX_URL = "https://sandbox.itunes.apple.com/verifyReceipt"
    
    # Google Play API endpoint
    GOOGLE_PLAY_API = "https://androidpublisher.googleapis.com/androidpublisher/v3"
    
    def __init__(
        self,
        apple_shared_secret: str,
        google_service_account_key: Optional[Dict] = None,
        bundle_id: str = "com.climaai.app",
        google_package_name: str = "com.climaai.app"
    ):
        self.apple_shared_secret = apple_shared_secret
        self.google_service_account_key = google_service_account_key
        self.bundle_id = bundle_id
        self.google_package_name = google_package_name
        self.http_client = httpx.AsyncClient(timeout=30.0)
    
    async def validate_receipt(
        self,
        platform: Platform,
        receipt_data: str,
        product_id: Optional[str] = None
    ) -> Tuple[bool, Dict]:
        """
        Validate a receipt from either platform.
        
        Returns:
            Tuple of (is_valid, receipt_info)
        """
        if platform == Platform.IOS:
            return await self._validate_apple_receipt(receipt_data)
        elif platform == Platform.ANDROID:
            return await self._validate_google_receipt(receipt_data, product_id)
        else:
            return False, {"error": "Unknown platform"}
    
    # =========================================================================
    # Apple App Store Validation
    # =========================================================================
    
    async def _validate_apple_receipt(self, receipt_data: str) -> Tuple[bool, Dict]:
        """
        Validate Apple App Store receipt using server-to-server verification.
        Tries production first, falls back to sandbox if needed.
        """
        payload = {
            "receipt-data": receipt_data,
            "password": self.apple_shared_secret,
            "exclude-old-transactions": True
        }
        
        # Try production first
        try:
            response = await self.http_client.post(
                self.APPLE_PRODUCTION_URL,
                json=payload
            )
            result = response.json()
            
            # Status 21007 means sandbox receipt sent to production
            if result.get("status") == 21007:
                response = await self.http_client.post(
                    self.APPLE_SANDBOX_URL,
                    json=payload
                )
                result = response.json()
            
            return self._parse_apple_response(result)
            
        except Exception as e:
            return False, {"error": str(e)}
    
    def _parse_apple_response(self, result: Dict) -> Tuple[bool, Dict]:
        """Parse Apple receipt validation response."""
        status = result.get("status", -1)
        
        # Status 0 means valid
        if status != 0:
            error_messages = {
                21000: "App Store could not read the receipt",
                21002: "Receipt data was malformed",
                21003: "Receipt could not be authenticated",
                21004: "Shared secret does not match",
                21005: "Receipt server is not available",
                21006: "Receipt is valid but subscription expired",
                21007: "Sandbox receipt sent to production",
                21008: "Production receipt sent to sandbox",
                21010: "Account could not be found",
            }
            return False, {
                "error": error_messages.get(status, f"Unknown error: {status}"),
                "status": status
            }
        
        # Extract latest receipt info
        receipt_info = result.get("receipt", {})
        latest_receipt_info = result.get("latest_receipt_info", [])
        pending_renewal_info = result.get("pending_renewal_info", [])
        
        # Get the most recent subscription
        if latest_receipt_info:
            latest = max(latest_receipt_info, key=lambda x: int(x.get("expires_date_ms", 0)))
            expires_ms = int(latest.get("expires_date_ms", 0))
            is_active = expires_ms > (datetime.now(timezone.utc).timestamp() * 1000)
            
            # Check for auto-renewal status
            auto_renew = False
            if pending_renewal_info:
                renewal = next(
                    (r for r in pending_renewal_info 
                     if r.get("product_id") == latest.get("product_id")),
                    {}
                )
                auto_renew = renewal.get("auto_renew_status") == "1"
            
            return True, {
                "platform": "ios",
                "is_active": is_active,
                "product_id": latest.get("product_id"),
                "original_transaction_id": latest.get("original_transaction_id"),
                "transaction_id": latest.get("transaction_id"),
                "purchase_date": latest.get("purchase_date"),
                "expires_date": latest.get("expires_date"),
                "expires_date_ms": expires_ms,
                "is_trial_period": latest.get("is_trial_period") == "true",
                "is_in_intro_offer_period": latest.get("is_in_intro_offer_period") == "true",
                "auto_renew": auto_renew,
                "bundle_id": receipt_info.get("bundle_id"),
            }
        
        return False, {"error": "No subscription found in receipt"}
    
    # =========================================================================
    # Google Play Validation
    # =========================================================================
    
    async def _validate_google_receipt(
        self,
        purchase_token: str,
        product_id: str
    ) -> Tuple[bool, Dict]:
        """
        Validate Google Play subscription using Google Play Developer API.
        """
        if not self.google_service_account_key:
            return False, {"error": "Google service account not configured"}
        
        if not product_id:
            return False, {"error": "Product ID required for Google validation"}
        
        try:
            # Get access token
            access_token = await self._get_google_access_token()
            
            # Call Google Play API
            url = (
                f"{self.GOOGLE_PLAY_API}/applications/{self.google_package_name}"
                f"/purchases/subscriptions/{product_id}/tokens/{purchase_token}"
            )
            
            response = await self.http_client.get(
                url,
                headers={"Authorization": f"Bearer {access_token}"}
            )
            
            if response.status_code != 200:
                return False, {"error": f"Google API error: {response.text}"}
            
            return self._parse_google_response(response.json(), product_id)
            
        except Exception as e:
            return False, {"error": str(e)}
    
    async def _get_google_access_token(self) -> str:
        """Generate OAuth2 access token from service account credentials."""
        now = datetime.now(timezone.utc)
        
        # Create JWT claim
        claim = {
            "iss": self.google_service_account_key["client_email"],
            "scope": "https://www.googleapis.com/auth/androidpublisher",
            "aud": "https://oauth2.googleapis.com/token",
            "iat": int(now.timestamp()),
            "exp": int((now + timedelta(hours=1)).timestamp())
        }
        
        # Sign with private key
        token = jwt.encode(
            claim,
            self.google_service_account_key["private_key"],
            algorithm="RS256"
        )
        
        # Exchange for access token
        response = await self.http_client.post(
            "https://oauth2.googleapis.com/token",
            data={
                "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
                "assertion": token
            }
        )
        
        return response.json()["access_token"]
    
    def _parse_google_response(self, result: Dict, product_id: str) -> Tuple[bool, Dict]:
        """Parse Google Play subscription response."""
        expiry_time_ms = int(result.get("expiryTimeMillis", 0))
        is_active = expiry_time_ms > (datetime.now(timezone.utc).timestamp() * 1000)
        
        # Map payment state
        payment_state = result.get("paymentState")
        payment_status = {
            0: "pending",
            1: "received",
            2: "free_trial",
            3: "pending_deferred"
        }.get(payment_state, "unknown")
        
        # Check cancellation
        cancel_reason = result.get("cancelReason")
        is_cancelled = cancel_reason is not None
        
        return True, {
            "platform": "android",
            "is_active": is_active,
            "product_id": product_id,
            "order_id": result.get("orderId"),
            "start_time": result.get("startTimeMillis"),
            "expiry_time": result.get("expiryTimeMillis"),
            "expiry_time_ms": expiry_time_ms,
            "auto_renewing": result.get("autoRenewing", False),
            "payment_state": payment_status,
            "price_currency": result.get("priceCurrencyCode"),
            "price_amount_micros": result.get("priceAmountMicros"),
            "country_code": result.get("countryCode"),
            "is_cancelled": is_cancelled,
            "cancel_reason": cancel_reason,
            "user_cancellation_time": result.get("userCancellationTimeMillis"),
        }
    
    async def close(self):
        """Close HTTP client."""
        await self.http_client.aclose()


# Singleton instance
_validator: Optional[ReceiptValidator] = None


def get_receipt_validator() -> ReceiptValidator:
    """Get or create receipt validator singleton."""
    global _validator
    if _validator is None:
        from ..config import get_settings
        settings = get_settings()
        
        # Load Google service account if available
        google_key = None
        if settings.GOOGLE_SERVICE_ACCOUNT_JSON:
            google_key = json.loads(settings.GOOGLE_SERVICE_ACCOUNT_JSON)
        
        _validator = ReceiptValidator(
            apple_shared_secret=settings.APPLE_SHARED_SECRET,
            google_service_account_key=google_key,
            bundle_id=settings.APPLE_BUNDLE_ID,
            google_package_name=settings.GOOGLE_PACKAGE_NAME
        )
    
    return _validator
