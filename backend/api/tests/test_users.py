import pytest

@pytest.mark.asyncio
async def test_register_user(client):
    payload = {
        "email": "test@example.com",
        "password": "securepassword123",
        "full_name": "Test User",
        "platform": "ios"
    }
    response = await client.post("/users/register", json=payload)
    assert response.status_code == 201
    data = response.json()
    assert "access_token" in data
    assert data["user"]["email"] == payload["email"]
    assert data["user"]["full_name"] == payload["full_name"]

@pytest.mark.asyncio
async def test_register_duplicate_user(client):
    payload = {
        "email": "duplicate@example.com",
        "password": "password123",
        "full_name": "Duplicate User",
        "platform": "android"
    }
    # Register first time
    response = await client.post("/users/register", json=payload)
    assert response.status_code == 201

    # Register second time
    response = await client.post("/users/register", json=payload)
    assert response.status_code == 400
    assert response.json()["detail"] == "Email already registered"

@pytest.mark.asyncio
async def test_login_user(client):
    # Register first
    register_payload = {
        "email": "login@example.com",
        "password": "password123",
        "full_name": "Login User",
        "platform": "ios"
    }
    await client.post("/users/register", json=register_payload)

    # Login
    login_payload = {
        "email": "login@example.com",
        "password": "password123"
    }
    response = await client.post("/users/login", json=login_payload)
    assert response.status_code == 200
    data = response.json()
    assert "access_token" in data
    assert data["user"]["email"] == register_payload["email"]

@pytest.mark.asyncio
async def test_login_invalid_credentials(client):
    login_payload = {
        "email": "nonexistent@example.com",
        "password": "wrongpassword"
    }
    response = await client.post("/users/login", json=login_payload)
    assert response.status_code == 401
    assert response.json()["detail"] == "Incorrect email or password"

@pytest.mark.asyncio
async def test_get_me(client):
    # Register and get token
    payload = {
        "email": "me@example.com",
        "password": "password123",
        "full_name": "Me User",
        "platform": "ios"
    }
    response = await client.post("/users/register", json=payload)
    token = response.json()["access_token"]

    # Get me
    headers = {"Authorization": f"Bearer {token}"}
    response = await client.get("/users/me", headers=headers)
    assert response.status_code == 200
    assert response.json()["email"] == payload["email"]
