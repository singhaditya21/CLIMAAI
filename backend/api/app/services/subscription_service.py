"""
Subscription service for validation and management.
"""
from datetime import datetime, timedelta
from typing import Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from ..models import User, Subscription, SubscriptionStatus, SubscriptionPlatform, SubscriptionPlan
from ..schemas.subscription import SubscriptionStatusResponse
from ..config import get_settings
import httpx


class SubscriptionService:
    """Service for managing subscriptions."""
    
    TRIAL_DAYS = 7
    MONTHLY_PRICE = 4.99
    ANNUAL_PRICE = 39.99
    
    async def check_subscription_status(
        self,
        user: User,
        db: AsyncSession
    ) -> SubscriptionStatusResponse:
        """Check user's subscription status."""
        # Get active subscription
        result = await db.execute(
            select(Subscription)
            .where(Subscription.user_id == user.id)
            .where(Subscription.status.in_([
                SubscriptionStatus.ACTIVE,
                SubscriptionStatus.TRIAL,
                SubscriptionStatus.GRACE_PERIOD
            ]))
            .order_by(Subscription.created_at.desc())
        )
        subscription = result.scalar_one_or_none()
        
        if not subscription:
            return SubscriptionStatusResponse(
                has_active_subscription=False,
                is_premium=False,
                subscription=None,
                features={
                    "extended_forecast": False,
                    "ai_insights": False,
                    "minute_rain": False,
                    "severe_alerts": False,
                    "air_quality_detailed": False,
                    "health_insights": False,
                    "travel_analysis": False,
                }
            )
        
        # Check if subscription has expired
        now = datetime.utcnow()
        is_active = subscription.is_active
        
        if subscription.status == SubscriptionStatus.TRIAL:
            if subscription.trial_end_date and subscription.trial_end_date < now:
                subscription.status = SubscriptionStatus.EXPIRED
                is_active = False
                await db.commit()
        elif subscription.status == SubscriptionStatus.ACTIVE:
            if subscription.subscription_end_date and subscription.subscription_end_date < now:
                if subscription.auto_renew:
                    subscription.status = SubscriptionStatus.GRACE_PERIOD
                else:
                    subscription.status = SubscriptionStatus.EXPIRED
                    is_active = False
                await db.commit()
        
        return SubscriptionStatusResponse(
            has_active_subscription=is_active,
            is_premium=is_active,
            subscription=subscription,
            features={
                "extended_forecast": is_active,
                "ai_insights": is_active,
                "minute_rain": is_active,
                "severe_alerts": is_active,
                "air_quality_detailed": is_active,
                "health_insights": is_active,
                "travel_analysis": is_active,
            }
        )
    
    async def start_trial(
        self,
        user: User,
        platform: SubscriptionPlatform,
        db: AsyncSession
    ) -> Subscription:
        """Start a free trial for the user."""
        # Check if user has already used trial
        result = await db.execute(
            select(Subscription)
            .where(Subscription.user_id == user.id)
            .where(Subscription.is_trial_used == True)
        )
        existing_trial = result.scalar_one_or_none()
        
        if existing_trial:
            raise ValueError("Trial already used")
        
        now = datetime.utcnow()
        trial_end = now + timedelta(days=self.TRIAL_DAYS)
        
        subscription = Subscription(
            user_id=user.id,
            platform=platform,
            plan=SubscriptionPlan.MONTHLY,  # Default to monthly after trial
            status=SubscriptionStatus.TRIAL,
            trial_start_date=now,
            trial_end_date=trial_end,
            is_trial_used=True,
            auto_renew=True,
        )
        
        db.add(subscription)
        await db.commit()
        await db.refresh(subscription)
        
        return subscription
    
    async def activate_subscription(
        self,
        user: User,
        platform: SubscriptionPlatform,
        plan: SubscriptionPlan,
        transaction_id: str,
        db: AsyncSession
    ) -> Subscription:
        """Activate a paid subscription."""
        now = datetime.utcnow()
        
        # Calculate end date
        if plan == SubscriptionPlan.MONTHLY:
            end_date = now + timedelta(days=30)
        else:  # ANNUAL
            end_date = now + timedelta(days=365)
        
        # Check for existing subscription
        result = await db.execute(
            select(Subscription)
            .where(Subscription.user_id == user.id)
            .where(Subscription.status == SubscriptionStatus.TRIAL)
        )
        trial_subscription = result.scalar_one_or_none()
        
        if trial_subscription:
            # Upgrade trial to paid
            trial_subscription.status = SubscriptionStatus.ACTIVE
            trial_subscription.plan = plan
            trial_subscription.subscription_start_date = now
            trial_subscription.subscription_end_date = end_date
            
            if platform == SubscriptionPlatform.APPLE:
                trial_subscription.apple_transaction_id = transaction_id
            else:
                trial_subscription.google_purchase_token = transaction_id
            
            await db.commit()
            await db.refresh(trial_subscription)
            return trial_subscription
        else:
            # Create new subscription
            subscription = Subscription(
                user_id=user.id,
                platform=platform,
                plan=plan,
                status=SubscriptionStatus.ACTIVE,
                subscription_start_date=now,
                subscription_end_date=end_date,
                is_trial_used=True,
                auto_renew=True,
            )
            
            if platform == SubscriptionPlatform.APPLE:
                subscription.apple_transaction_id = transaction_id
            else:
                subscription.google_purchase_token = transaction_id
            
            db.add(subscription)
            await db.commit()
            await db.refresh(subscription)
            return subscription
    
    async def validate_apple_receipt(self, receipt_data: str, sandbox: bool = True) -> dict:
        """
        Validate Apple App Store receipt.
        Note: This is a simplified version. Production should use App Store Server API.
        """
        settings = get_settings()
        url = "https://sandbox.itunes.apple.com/verifyReceipt" if sandbox else "https://buy.itunes.apple.com/verifyReceipt"
        
        payload = {
            "receipt-data": receipt_data,
            "password": settings.APPLE_SHARED_SECRET,
            "exclude-old-transactions": True
        }
        
        async with httpx.AsyncClient() as client:
            response = await client.post(url, json=payload, timeout=10.0)
            return response.json()
    
    async def validate_google_purchase(self, package_name: str, product_id: str, purchase_token: str) -> dict:
        """
        Validate Google Play purchase.
        Note: This requires Google Play Developer API setup with OAuth2.
        """
        # This is a placeholder. In production, use google-api-python-client
        # with proper OAuth2 credentials
        return {
            "kind": "androidpublisher#productPurchase",
            "purchaseState": 0,  # 0 = Purchased
            "consumptionState": 0,
            "autoRenewing": True
        }
    
    async def cancel_subscription(
        self,
        subscription: Subscription,
        db: AsyncSession
    ) -> Subscription:
        """Cancel a subscription (disables auto-renew)."""
        subscription.auto_renew = False
        subscription.status = SubscriptionStatus.CANCELLED
        await db.commit()
        await db.refresh(subscription)
        return subscription
    
    async def renew_subscription(
        self,
        subscription: Subscription,
        db: AsyncSession
    ) -> Subscription:
        """Renew a subscription."""
        if subscription.plan == SubscriptionPlan.MONTHLY:
            subscription.subscription_end_date = datetime.utcnow() + timedelta(days=30)
        else:
            subscription.subscription_end_date = datetime.utcnow() + timedelta(days=365)
        
        subscription.status = SubscriptionStatus.ACTIVE
        subscription.last_validation_date = datetime.utcnow()
        
        await db.commit()
        await db.refresh(subscription)
        return subscription
