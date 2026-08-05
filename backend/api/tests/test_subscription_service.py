"""Subscription lifecycle: trials, activation, cancellation and feature gating.

Runs against the test database, since the service reads and writes subscription
rows directly.
"""
from datetime import datetime, timedelta, timezone

import pytest
from sqlalchemy import select

from app.models import User
from app.models.subscription import (
    Subscription,
    SubscriptionPlan,
    SubscriptionPlatform,
    SubscriptionStatus,
)
from app.services.subscription_service import SubscriptionService

PREMIUM_FEATURES = {
    "extended_forecast",
    "ai_insights",
    "minute_rain",
    "severe_alerts",
    "air_quality_detailed",
    "health_insights",
    "travel_analysis",
}


@pytest.fixture
def service():
    return SubscriptionService()


@pytest.fixture
async def user(db_session):
    record = User(
        email="subscriber@example.com",
        password_hash="not-a-real-hash",
        full_name="Subscriber",
        platform="ios",
    )
    db_session.add(record)
    await db_session.commit()
    await db_session.refresh(record)
    return record


async def test_new_user_has_no_subscription(service, user, db_session):
    status = await service.check_subscription_status(user, db_session)

    assert status.has_active_subscription is False
    assert status.is_premium is False
    assert not any(status.features.values())


async def test_start_trial_grants_premium(service, user, db_session):
    await service.start_trial(user, SubscriptionPlatform.APPLE, db_session)

    status = await service.check_subscription_status(user, db_session)

    assert status.has_active_subscription is True
    assert status.is_premium is True
    assert PREMIUM_FEATURES <= set(status.features)
    assert all(status.features[name] for name in PREMIUM_FEATURES)


async def test_trial_lasts_the_advertised_number_of_days(service, user, db_session):
    subscription = await service.start_trial(user, SubscriptionPlatform.APPLE, db_session)

    length = subscription.trial_end_date - subscription.trial_start_date

    assert length.days == service.TRIAL_DAYS


async def test_trial_cannot_be_used_twice(service, user, db_session):
    await service.start_trial(user, SubscriptionPlatform.APPLE, db_session)

    with pytest.raises(ValueError, match="already used"):
        await service.start_trial(user, SubscriptionPlatform.APPLE, db_session)


async def test_activation_marks_the_subscription_active(service, user, db_session):
    await service.activate_subscription(
        user,
        SubscriptionPlatform.APPLE,
        SubscriptionPlan.ANNUAL,
        "txn-12345",
        db_session,
    )

    status = await service.check_subscription_status(user, db_session)

    assert status.is_premium is True
    assert status.subscription.plan == SubscriptionPlan.ANNUAL


async def test_activation_honours_the_store_expiry(service, user, db_session):
    """The receipt's expiry wins over the nominal plan length."""
    expires = datetime.now(timezone.utc) + timedelta(days=2)

    subscription = await service.activate_subscription(
        user,
        SubscriptionPlatform.APPLE,
        SubscriptionPlan.ANNUAL,
        "txn-expiring-soon",
        db_session,
        end_date=expires,
    )

    assert abs((subscription.subscription_end_date - expires).total_seconds()) < 1


async def test_activation_falls_back_to_the_plan_length(service, user, db_session):
    subscription = await service.activate_subscription(
        user, SubscriptionPlatform.GOOGLE, SubscriptionPlan.ANNUAL, "txn-no-expiry", db_session
    )

    length = subscription.subscription_end_date - subscription.subscription_start_date

    assert length.days == 365


async def test_a_purchase_cannot_be_redeemed_by_a_second_account(service, user, db_session):
    """A receipt proves a purchase happened, not who is entitled to it."""
    await service.activate_subscription(
        user, SubscriptionPlatform.APPLE, SubscriptionPlan.MONTHLY, "txn-shared", db_session
    )

    freeloader = User(
        email="freeloader@example.com",
        password_hash="not-a-real-hash",
        full_name="Freeloader",
        platform="ios",
    )
    db_session.add(freeloader)
    await db_session.commit()
    await db_session.refresh(freeloader)

    with pytest.raises(ValueError, match="another account"):
        await service.activate_subscription(
            freeloader, SubscriptionPlatform.APPLE, SubscriptionPlan.MONTHLY, "txn-shared", db_session
        )

    assert (await service.check_subscription_status(freeloader, db_session)).is_premium is False


async def test_the_buyer_may_re_present_their_own_purchase(service, user, db_session):
    """Restoring a purchase on a new device must not look like a replay."""
    await service.activate_subscription(
        user, SubscriptionPlatform.APPLE, SubscriptionPlan.MONTHLY, "txn-mine", db_session
    )

    await service.activate_subscription(
        user, SubscriptionPlatform.APPLE, SubscriptionPlan.MONTHLY, "txn-mine", db_session
    )

    assert (await service.check_subscription_status(user, db_session)).is_premium is True


async def test_expired_subscription_is_not_premium(service, user, db_session):
    """A row left in EXPIRED must not grant access."""
    db_session.add(
        Subscription(
            user_id=user.id,
            platform=SubscriptionPlatform.APPLE,
            plan=SubscriptionPlan.MONTHLY,
            status=SubscriptionStatus.EXPIRED,
            subscription_start_date=datetime.now(timezone.utc) - timedelta(days=60),
            subscription_end_date=datetime.now(timezone.utc) - timedelta(days=30),
            is_trial_used=True,
        )
    )
    await db_session.commit()

    status = await service.check_subscription_status(user, db_session)

    assert status.has_active_subscription is False
    assert status.is_premium is False


async def test_cancellation_revokes_premium(service, user, db_session):
    """Note: cancelling revokes access immediately rather than at period end.

    That is what the service does today — status moves to CANCELLED, which
    check_subscription_status does not count as active. Flagged as a product
    question, not changed here.
    """
    subscription = await service.activate_subscription(
        user, SubscriptionPlatform.APPLE, SubscriptionPlan.MONTHLY, "txn-1", db_session
    )

    await service.cancel_subscription(subscription, db_session)
    status = await service.check_subscription_status(user, db_session)

    assert status.is_premium is False
    assert subscription.auto_renew is False


async def test_subscriptions_are_scoped_to_their_user(service, user, db_session):
    """One user's trial must not make another user premium."""
    other = User(
        email="other-subscriber@example.com",
        password_hash="not-a-real-hash",
        full_name="Other",
        platform="android",
    )
    db_session.add(other)
    await db_session.commit()
    await db_session.refresh(other)

    await service.start_trial(user, SubscriptionPlatform.APPLE, db_session)

    assert (await service.check_subscription_status(other, db_session)).is_premium is False


async def test_trial_row_is_persisted(service, user, db_session):
    await service.start_trial(user, SubscriptionPlatform.GOOGLE, db_session)

    rows = await db_session.execute(
        select(Subscription).where(Subscription.user_id == user.id)
    )
    stored = rows.scalars().all()

    assert len(stored) == 1
    assert stored[0].is_trial_used is True
    assert stored[0].status == SubscriptionStatus.TRIAL
