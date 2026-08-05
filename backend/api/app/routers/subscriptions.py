"""
Subscription management router.
"""
from datetime import datetime, timezone
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from ..database import get_db
from ..models import User, SubscriptionPlan
from ..schemas.subscription import (
    SubscriptionCreate,
    SubscriptionValidate,
    SubscriptionResponse,
    SubscriptionStatusResponse,
)
from ..services.auth import get_current_user
from ..services.receipt_validator import Platform, get_receipt_validator
from ..services.subscription_service import SubscriptionService
from ..config import get_settings

router = APIRouter(prefix="/api/subscriptions", tags=["subscriptions"])


def _expiry_from_millis(expires_ms) -> Optional[datetime]:
    """Store expiry timestamps are epoch milliseconds, as strings or ints."""
    if not expires_ms:
        return None
    return datetime.fromtimestamp(int(expires_ms) / 1000, tz=timezone.utc)


@router.get("/status", response_model=SubscriptionStatusResponse)
async def get_subscription_status(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Get current subscription status and available features.
    """
    subscription_service = SubscriptionService()
    return await subscription_service.check_subscription_status(current_user, db)


@router.post("/trial", response_model=SubscriptionResponse, status_code=status.HTTP_201_CREATED)
async def start_trial(
    subscription_data: SubscriptionCreate,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Start a 7-day free trial.
    
    - Available once per user
    - Grants full premium features
    - Automatically expires after 7 days
    """
    subscription_service = SubscriptionService()
    
    try:
        subscription = await subscription_service.start_trial(
            current_user,
            subscription_data.platform,
            db
        )
        return SubscriptionResponse.model_validate(subscription)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/activate", response_model=SubscriptionResponse)
async def activate_subscription(
    subscription_data: SubscriptionCreate,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Activate a paid subscription.
    
    Requires valid receipt/purchase token from Apple or Google.
    """
    subscription_service = SubscriptionService()
    expires_at = None

    # Validate receipt based on platform
    try:
        if subscription_data.platform.value == "apple":
            settings = get_settings()

            # ReceiptValidator asks production first and only falls back to
            # sandbox on Apple's 21007. The previous call pinned sandbox=True,
            # which in production rejects every real receipt and accepts any
            # receipt minted from a free sandbox tester account.
            is_valid, receipt = await get_receipt_validator().validate_receipt(
                platform=Platform.IOS,
                receipt_data=subscription_data.receipt_data,
            )

            if not is_valid:
                raise HTTPException(
                    status_code=400,
                    detail=receipt.get("error", "Invalid Apple receipt")
                )

            # Apple validates a receipt for whichever app issued it, so without
            # this the receipt from any other App Store purchase would pass.
            if receipt.get("bundle_id") != settings.APPLE_BUNDLE_ID:
                raise HTTPException(status_code=400, detail="Receipt belongs to a different app")

            if not receipt.get("is_active"):
                raise HTTPException(status_code=400, detail="Subscription expired")

            # The original transaction id, not the per-renewal one: it is what
            # the App Store sends in server notifications, so it is the only
            # value a later RENEW/EXPIRED webhook can match this row on.
            transaction_id = receipt.get("original_transaction_id")
            if not transaction_id:
                raise HTTPException(status_code=400, detail="No transaction found in receipt")

            expires_at = _expiry_from_millis(receipt.get("expires_date_ms"))

        else:  # Google
            # Validate Google purchase
            settings = get_settings()

            # Determine product ID based on plan
            if subscription_data.plan == SubscriptionPlan.MONTHLY:
                product_id = settings.GOOGLE_PRODUCT_ID_MONTHLY
            else:
                product_id = settings.GOOGLE_PRODUCT_ID_ANNUAL

            purchase_token = subscription_data.receipt_data

            # Call validation
            try:
                validation_response = await subscription_service.validate_google_purchase(
                    package_name=settings.GOOGLE_PACKAGE_NAME,
                    product_id=product_id,
                    purchase_token=purchase_token
                )
            except ValueError as e:
                # Configuration error
                raise HTTPException(status_code=500, detail=str(e))
            except Exception as e:
                # API error
                raise HTTPException(status_code=400, detail=f"Google validation failed: {str(e)}")

            # Validate payment state (1=Received, 2=Free Trial)
            payment_state = validation_response.get("paymentState")
            if payment_state is not None and payment_state not in [1, 2]:
                raise HTTPException(
                    status_code=400,
                    detail=f"Subscription not active (Payment State: {payment_state})"
                )

            # Validate expiry
            expires_at = _expiry_from_millis(validation_response.get("expiryTimeMillis"))
            if expires_at and expires_at < datetime.now(timezone.utc):
                raise HTTPException(status_code=400, detail="Subscription expired")

            transaction_id = purchase_token

        # Activate subscription
        subscription = await subscription_service.activate_subscription(
            current_user,
            subscription_data.platform,
            subscription_data.plan,
            transaction_id,
            db,
            end_date=expires_at,
        )

        return SubscriptionResponse.model_validate(subscription)

    except HTTPException:
        raise
    except ValueError as e:
        # Raised when the purchase is already linked to another account.
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Subscription activation failed: {str(e)}")


@router.post("/validate", response_model=dict)
async def validate_subscription(
    validation_data: SubscriptionValidate,
    current_user: User = Depends(get_current_user),
):
    """
    Validate a subscription receipt using server-to-server verification.
    
    - Apple: Verifies with App Store Connect (production/sandbox)
    - Google: Verifies with Google Play Developer API
    
    Returns detailed subscription status including expiration and auto-renewal.
    """
    validator = get_receipt_validator()
    
    try:
        platform = Platform.IOS if validation_data.platform.value == "apple" else Platform.ANDROID
        
        is_valid, receipt_info = await validator.validate_receipt(
            platform=platform,
            receipt_data=validation_data.receipt_data,
            product_id=getattr(validation_data, 'product_id', None)
        )
        
        if not is_valid:
            return {
                "valid": False,
                "platform": platform.value,
                "error": receipt_info.get("error", "Validation failed")
            }
        
        return {
            "valid": True,
            "platform": platform.value,
            "is_active": receipt_info.get("is_active", False),
            "product_id": receipt_info.get("product_id"),
            "expires_at": receipt_info.get("expires_date") or receipt_info.get("expiry_time"),
            "is_trial": receipt_info.get("is_trial_period", False),
            "auto_renew": receipt_info.get("auto_renew") or receipt_info.get("auto_renewing", False),
            "details": receipt_info
        }
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Receipt validation error: {str(e)}")


@router.delete("/cancel", response_model=SubscriptionResponse)
async def cancel_subscription(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Cancel subscription (disables auto-renewal).
    
    Access continues until the end of the current billing period.
    """
    subscription_service = SubscriptionService()
    sub_status = await subscription_service.check_subscription_status(current_user, db)
    
    if not sub_status.subscription:
        raise HTTPException(status_code=404, detail="No active subscription found")
    
    subscription = await subscription_service.cancel_subscription(
        sub_status.subscription,
        db
    )
    
    return SubscriptionResponse.model_validate(subscription)


@router.get("/plans", response_model=dict)
async def get_subscription_plans():
    """
    Get available subscription plans and pricing.
    """
    return {
        "plans": [
            {
                "id": "monthly",
                "name": "Monthly Premium",
                "price": 4.99,
                "currency": "USD",
                "billing_period": "month",
                "trial_days": 7,
                "features": [
                    "16-day weather forecast",
                    "AI-powered insights",
                    "Minute-level rain prediction",
                    "Severe weather alerts",
                    "Detailed air quality breakdown",
                    "Health & activity recommendations",
                    "Travel weather analysis"
                ]
            },
            {
                "id": "annual",
                "name": "Annual Premium",
                "price": 39.99,
                "currency": "USD",
                "billing_period": "year",
                "trial_days": 7,
                "savings": "33%",
                "features": [
                    "All Monthly features",
                    "Save $20/year",
                    "Priority support"
                ]
            }
        ],
        "trial": {
            "duration_days": 7,
            "features": "Full premium access"
        }
    }
