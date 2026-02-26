import pytest
from app.models import User
from sqlalchemy import select

@pytest.mark.asyncio
async def test_registration_creates_unverified_user(client, db_session):
    # Register
    response = await client.post("/api/auth/register", json={
        "email": "test@example.com",
        "password": "Password123!",
        "full_name": "Test User",
        "platform": "ios"
    })
    assert response.status_code == 201
    data = response.json()
    assert "access_token" in data
    assert data["user"]["is_verified"] is False

    # Check DB
    result = await db_session.execute(select(User).where(User.email == "test@example.com"))
    user = result.scalar_one()
    assert user.is_verified is False
    assert user.verification_token is not None
    assert user.verification_token_expires is not None

@pytest.mark.asyncio
async def test_verify_email(client, db_session):
    # Register first
    await client.post("/api/auth/register", json={
        "email": "verify@example.com",
        "password": "Password123!",
        "full_name": "Verify User",
        "platform": "android"
    })

    # Get token from DB
    result = await db_session.execute(select(User).where(User.email == "verify@example.com"))
    user = result.scalar_one()
    token = user.verification_token

    # Verify
    response = await client.get(f"/api/auth/verify-email?token={token}")
    assert response.status_code == 200
    assert response.json()["message"] == "Email verified successfully"

    # Check DB - Need to expire session or refresh user to see changes committed by API
    await db_session.refresh(user)
    assert user.is_verified is True
    assert user.verification_token is None

@pytest.mark.asyncio
async def test_verify_email_invalid_token(client):
    response = await client.get("/api/auth/verify-email?token=invalid-token")
    assert response.status_code == 400
    assert response.json()["detail"] == "Invalid verification token"

@pytest.mark.asyncio
async def test_resend_verification(client, db_session):
    # Register
    await client.post("/api/auth/register", json={
        "email": "resend@example.com",
        "password": "Password123!",
        "full_name": "Resend User",
        "platform": "ios"
    })

    # Get old token
    result = await db_session.execute(select(User).where(User.email == "resend@example.com"))
    user = result.scalar_one()
    old_token = user.verification_token

    # Resend
    response = await client.post("/api/auth/resend-verification", json={
        "email": "resend@example.com"
    })
    assert response.status_code == 200

    # Check DB for new token
    await db_session.refresh(user)
    assert user.verification_token != old_token
