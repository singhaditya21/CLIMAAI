"""Receipt checks on POST /api/subscriptions/activate.

Every test here describes a way of getting premium without paying for it. The
endpoint previously validated Apple receipts against the *sandbox* endpoint in
all environments, which accepts receipts anyone can mint from a free sandbox
tester account, and never checked which app or which account a receipt belonged
to.

The App Store is stubbed out: what is under test is the endpoint's reaction to a
validation result, not httpx.
"""
from datetime import datetime, timedelta, timezone
from unittest.mock import patch

import pytest

from app.config import get_settings
from app.models import User
from app.models.subscription import (
    Subscription,
    SubscriptionPlan,
    SubscriptionPlatform,
    SubscriptionStatus,
)

ACTIVATE = "/api/subscriptions/activate"
BUNDLE_ID = get_settings().APPLE_BUNDLE_ID


def apple_receipt(**overrides):
    """A validated receipt as ReceiptValidator reports it."""
    expires = datetime.now(timezone.utc) + timedelta(days=30)
    receipt = {
        "platform": "ios",
        "is_active": True,
        "product_id": "climaai_pro_monthly",
        "original_transaction_id": "1000000000000001",
        "transaction_id": "1000000000000009",
        "expires_date_ms": int(expires.timestamp() * 1000),
        "bundle_id": BUNDLE_ID,
    }
    receipt.update(overrides)
    return receipt


class _StubValidator:
    """Stands in for the singleton ReceiptValidator."""

    def __init__(self, is_valid, receipt):
        self._result = (is_valid, receipt)
        self.calls = []

    async def validate_receipt(self, platform, receipt_data, product_id=None):
        self.calls.append((platform, receipt_data))
        return self._result


def stub_validator(is_valid, receipt):
    validator = _StubValidator(is_valid, receipt)
    return patch("app.routers.subscriptions.get_receipt_validator", return_value=validator), validator


def activation_body(**overrides):
    body = {"platform": "apple", "plan": "monthly", "receipt_data": "a-receipt"}
    body.update(overrides)
    return body


async def test_valid_receipt_activates_the_subscription(auth_client):
    receipt = apple_receipt()
    patcher, _ = stub_validator(True, receipt)

    with patcher:
        response = await auth_client.post(ACTIVATE, json=activation_body())

    assert response.status_code == 200, response.text
    assert response.json()["status"] == SubscriptionStatus.ACTIVE.value


async def test_activation_expires_when_the_store_says_it_does(auth_client):
    """Not 30 days from now: a receipt can be presented mid-period."""
    expires = datetime.now(timezone.utc) + timedelta(days=3)
    receipt = apple_receipt(expires_date_ms=int(expires.timestamp() * 1000))
    patcher, _ = stub_validator(True, receipt)

    with patcher:
        response = await auth_client.post(ACTIVATE, json=activation_body())

    assert response.status_code == 200, response.text
    stored = datetime.fromisoformat(response.json()["subscription_end_date"])
    assert abs((stored - expires).total_seconds()) < 1


async def test_a_rejected_receipt_grants_nothing(auth_client):
    patcher, _ = stub_validator(False, {"error": "Receipt could not be authenticated"})

    with patcher:
        response = await auth_client.post(ACTIVATE, json=activation_body())

    assert response.status_code == 400
    assert "authenticated" in response.json()["detail"]


async def test_a_receipt_from_another_app_grants_nothing(auth_client):
    """Apple validates receipts for whichever app issued them."""
    patcher, _ = stub_validator(True, apple_receipt(bundle_id="com.someone.else"))

    with patcher:
        response = await auth_client.post(ACTIVATE, json=activation_body())

    assert response.status_code == 400
    assert "different app" in response.json()["detail"]


async def test_an_expired_receipt_grants_nothing(auth_client):
    patcher, _ = stub_validator(True, apple_receipt(is_active=False))

    with patcher:
        response = await auth_client.post(ACTIVATE, json=activation_body())

    assert response.status_code == 400
    assert response.json()["detail"] == "Subscription expired"


async def test_a_receipt_with_no_transaction_grants_nothing(auth_client):
    patcher, _ = stub_validator(True, apple_receipt(original_transaction_id=None))

    with patcher:
        response = await auth_client.post(ACTIVATE, json=activation_body())

    assert response.status_code == 400


async def test_the_stored_id_is_the_one_apple_webhooks_carry(auth_client, db_session):
    """Renewal notifications identify a subscription by its *original*
    transaction id, so storing the per-renewal id orphans the row."""
    from sqlalchemy import select

    patcher, _ = stub_validator(True, apple_receipt())

    with patcher:
        response = await auth_client.post(ACTIVATE, json=activation_body())
    assert response.status_code == 200, response.text

    rows = await db_session.execute(select(Subscription))
    stored = rows.scalars().one()

    assert stored.apple_transaction_id == "1000000000000001"


async def test_a_receipt_already_redeemed_elsewhere_grants_nothing(auth_client, db_session):
    """One purchase, one account. Otherwise a single $4.99 receipt is a
    site-wide premium key the moment anyone posts it."""
    other = User(
        email="first-owner@example.com",
        password_hash="not-a-real-hash",
        full_name="First Owner",
        platform="ios",
    )
    db_session.add(other)
    await db_session.commit()
    await db_session.refresh(other)

    db_session.add(
        Subscription(
            user_id=other.id,
            platform=SubscriptionPlatform.APPLE,
            plan=SubscriptionPlan.MONTHLY,
            status=SubscriptionStatus.ACTIVE,
            subscription_start_date=datetime.now(timezone.utc),
            subscription_end_date=datetime.now(timezone.utc) + timedelta(days=30),
            is_trial_used=True,
            apple_transaction_id="1000000000000001",
        )
    )
    await db_session.commit()

    patcher, _ = stub_validator(True, apple_receipt())

    with patcher:
        response = await auth_client.post(ACTIVATE, json=activation_body())

    assert response.status_code == 400
    assert "another account" in response.json()["detail"]


@pytest.mark.parametrize("field", ["platform", "plan", "receipt_data"])
async def test_activation_requires_a_receipt(auth_client, field):
    body = activation_body()
    body.pop(field)

    response = await auth_client.post(ACTIVATE, json=body)

    assert response.status_code == 422


async def test_activation_requires_authentication(client):
    patcher, _ = stub_validator(True, apple_receipt())

    with patcher:
        response = await client.post(ACTIVATE, json=activation_body())

    assert response.status_code in (401, 403)
