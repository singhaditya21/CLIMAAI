"""
Subscription service for validation and management.
"""
from datetime import datetime, timedelta, timezone
from typing import Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from ..models import User, Subscription, SubscriptionStatus, SubscriptionPlatform, SubscriptionPlan
from ..schemas.subscription import SubscriptionStatusResponse
from ..config import get_settings
import json
import logging
import asyncio
from google.oauth2 import service_account
from googleapiclient.discovery import build

logger = logging.getLogger(__name__)


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
            # order_by + scalar_one_or_none() is a contradiction without this:
            # a user holding two live rows would raise MultipleResultsFound
            # rather than getting the most recent one the ordering asks for.
            .limit(1)
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
        now = datetime.now(timezone.utc)
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
        
        now = datetime.now(timezone.utc)
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
        db: AsyncSession,
        end_date: Optional[datetime] = None,
    ) -> Subscription:
        """Activate a paid subscription.

        `end_date` is the expiry the store reported for this purchase. Pass it
        whenever the receipt carries one: assuming a full period from *now*
        grants 30 or 365 fresh days to a receipt that expires tomorrow.
        """
        now = datetime.now(timezone.utc)

        await self._reject_reused_transaction(user, platform, transaction_id, db)

        if end_date is None:
            # No store expiry available — fall back to the nominal period.
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

    async def _reject_reused_transaction(
        self,
        user: User,
        platform: SubscriptionPlatform,
        transaction_id: str,
        db: AsyncSession,
    ) -> None:
        """Refuse a purchase that is already linked to a different account.

        A validated receipt proves that a purchase happened, not who is holding
        it. Anyone can replay a receipt they obtained once — their own, or one
        posted publicly — so without this check a single $4.99 purchase
        activates premium on an unlimited number of accounts.
        """
        token_column = (
            Subscription.apple_transaction_id
            if platform == SubscriptionPlatform.APPLE
            else Subscription.google_purchase_token
        )

        result = await db.execute(
            select(Subscription)
            .where(token_column == transaction_id)
            .where(Subscription.user_id != user.id)
            .limit(1)
        )

        if result.scalar_one_or_none() is not None:
            logger.warning(
                "Rejected activation: purchase %s is already linked to another account",
                transaction_id,
            )
            raise ValueError("This purchase is already linked to another account")

    async def validate_google_purchase(self, package_name: str, product_id: str, purchase_token: str) -> dict:
        """
        Validate Google Play purchase using Google Play Developer API.
        Runs in a separate thread to avoid blocking the async event loop.
        """
        settings = get_settings()

        if not settings.GOOGLE_SERVICE_ACCOUNT_JSON:
            logger.warning("Google service account JSON not configured. Skipping validation.")
            raise ValueError("Google service account credentials not configured")

        def _validate_sync():
            try:
                service_account_info = json.loads(settings.GOOGLE_SERVICE_ACCOUNT_JSON)
                credentials = service_account.Credentials.from_service_account_info(
                    service_account_info,
                    scopes=['https://www.googleapis.com/auth/androidpublisher']
                )

                # Build the service
                service = build('androidpublisher', 'v3', credentials=credentials, cache_discovery=True)

                # Call the API
                request = service.purchases().subscriptions().get(
                    packageName=package_name,
                    subscriptionId=product_id,
                    token=purchase_token
                )
                return request.execute()
            except Exception as e:
                # Capture exception in the thread to propagate correctly
                raise e

        try:
            # Run the synchronous Google API call in a separate thread
            response = await asyncio.to_thread(_validate_sync)

            logger.info(f"Google Play validation response: {response}")
            return response

        except Exception as e:
            logger.error(f"Google Play validation failed: {str(e)}")
            raise
    
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
            subscription.subscription_end_date = datetime.now(timezone.utc) + timedelta(days=30)
        else:
            subscription.subscription_end_date = datetime.now(timezone.utc) + timedelta(days=365)
        
        subscription.status = SubscriptionStatus.ACTIVE
        subscription.last_validation_date = datetime.now(timezone.utc)
        
        await db.commit()
        await db.refresh(subscription)
        return subscription
