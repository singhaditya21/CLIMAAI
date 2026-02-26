# ClimaAI Testing Guide

This document describes the testing strategy and procedures for the ClimaAI project.

## 🛠️ Overview

The project is divided into several components, each with its own testing requirements:

1.  **Backend API (Python/FastAPI):** Unit and Integration tests using ============================= test session starts ==============================
platform linux -- Python 3.12.12, pytest-9.0.2, pluggy-1.6.0
rootdir: /app
plugins: cov-7.0.0, asyncio-1.3.0, anyio-4.12.1
asyncio: mode=Mode.STRICT, debug=False, asyncio_default_fixture_loop_scope=None, asyncio_default_test_loop_scope=function
collected 7 items

backend/api/tests/test_auth.py ...                                       [ 42%]
backend/api/tests/test_health.py .                                       [ 57%]
backend/api/tests/test_subscriptions.py ..                               [ 85%]
backend/api/tests/test_weather.py .                                      [100%]

=============================== warnings summary ===============================
../home/jules/.pyenv/versions/3.12.12/lib/python3.12/site-packages/pydantic/_internal/_config.py:271
../home/jules/.pyenv/versions/3.12.12/lib/python3.12/site-packages/pydantic/_internal/_config.py:271
../home/jules/.pyenv/versions/3.12.12/lib/python3.12/site-packages/pydantic/_internal/_config.py:271
../home/jules/.pyenv/versions/3.12.12/lib/python3.12/site-packages/pydantic/_internal/_config.py:271
../home/jules/.pyenv/versions/3.12.12/lib/python3.12/site-packages/pydantic/_internal/_config.py:271
../home/jules/.pyenv/versions/3.12.12/lib/python3.12/site-packages/pydantic/_internal/_config.py:271
../home/jules/.pyenv/versions/3.12.12/lib/python3.12/site-packages/pydantic/_internal/_config.py:271
../home/jules/.pyenv/versions/3.12.12/lib/python3.12/site-packages/pydantic/_internal/_config.py:271
  /home/jules/.pyenv/versions/3.12.12/lib/python3.12/site-packages/pydantic/_internal/_config.py:271: PydanticDeprecatedSince20: Support for class-based `config` is deprecated, use ConfigDict instead. Deprecated in Pydantic V2.0 to be removed in V3.0. See Pydantic V2 Migration Guide at https://errors.pydantic.dev/2.5/migration/
    warnings.warn(DEPRECATION_MESSAGE, DeprecationWarning)

../home/jules/.pyenv/versions/3.12.12/lib/python3.12/site-packages/passlib/utils/__init__.py:854
  /home/jules/.pyenv/versions/3.12.12/lib/python3.12/site-packages/passlib/utils/__init__.py:854: DeprecationWarning: 'crypt' is deprecated and slated for removal in Python 3.13
    from crypt import crypt as _crypt

backend/api/app/services/personalization_service.py:21
  /app/backend/api/app/services/personalization_service.py:21: DeprecationWarning: datetime.datetime.utcnow() is deprecated and scheduled for removal in a future version. Use timezone-aware objects to represent datetimes in UTC: datetime.datetime.now(datetime.UTC).
    timestamp: datetime = datetime.utcnow()

backend/api/app/services/personalization_service.py:54
  /app/backend/api/app/services/personalization_service.py:54: DeprecationWarning: datetime.datetime.utcnow() is deprecated and scheduled for removal in a future version. Use timezone-aware objects to represent datetimes in UTC: datetime.datetime.now(datetime.UTC).
    last_updated: datetime = datetime.utcnow()

backend/api/tests/test_auth.py::test_register_user
backend/api/tests/test_auth.py::test_login_user
backend/api/tests/test_auth.py::test_login_user
backend/api/tests/test_auth.py::test_get_me
backend/api/tests/test_subscriptions.py::test_subscription_flow
backend/api/tests/test_subscriptions.py::test_activate_subscription
  /app/backend/api/app/services/auth.py:38: DeprecationWarning: datetime.datetime.utcnow() is deprecated and scheduled for removal in a future version. Use timezone-aware objects to represent datetimes in UTC: datetime.datetime.now(datetime.UTC).
    expire = datetime.utcnow() + timedelta(hours=settings.JWT_EXPIRATION_HOURS)

backend/api/tests/test_auth.py::test_get_me
backend/api/tests/test_subscriptions.py::test_subscription_flow
backend/api/tests/test_subscriptions.py::test_subscription_flow
backend/api/tests/test_subscriptions.py::test_subscription_flow
backend/api/tests/test_subscriptions.py::test_activate_subscription
  /home/jules/.pyenv/versions/3.12.12/lib/python3.12/site-packages/jose/jwt.py:311: DeprecationWarning: datetime.datetime.utcnow() is deprecated and scheduled for removal in a future version. Use timezone-aware objects to represent datetimes in UTC: datetime.datetime.now(datetime.UTC).
    now = timegm(datetime.utcnow().utctimetuple())

backend/api/tests/test_subscriptions.py::test_subscription_flow
  /app/backend/api/app/services/subscription_service.py:106: DeprecationWarning: datetime.datetime.utcnow() is deprecated and scheduled for removal in a future version. Use timezone-aware objects to represent datetimes in UTC: datetime.datetime.now(datetime.UTC).
    now = datetime.utcnow()

backend/api/tests/test_subscriptions.py::test_subscription_flow
  /app/backend/api/app/services/subscription_service.py:56: DeprecationWarning: datetime.datetime.utcnow() is deprecated and scheduled for removal in a future version. Use timezone-aware objects to represent datetimes in UTC: datetime.datetime.now(datetime.UTC).
    now = datetime.utcnow()

backend/api/tests/test_subscriptions.py::test_activate_subscription
  /app/backend/api/app/services/subscription_service.py:135: DeprecationWarning: datetime.datetime.utcnow() is deprecated and scheduled for removal in a future version. Use timezone-aware objects to represent datetimes in UTC: datetime.datetime.now(datetime.UTC).
    now = datetime.utcnow()

backend/api/tests/test_weather.py::test_get_current_weather
  /app/backend/api/tests/test_weather.py:30: DeprecationWarning: datetime.datetime.utcnow() is deprecated and scheduled for removal in a future version. Use timezone-aware objects to represent datetimes in UTC: datetime.datetime.now(datetime.UTC).
    timestamp=datetime.utcnow()

backend/api/tests/test_weather.py: 24 warnings
  /app/backend/api/tests/test_weather.py:34: DeprecationWarning: datetime.datetime.utcnow() is deprecated and scheduled for removal in a future version. Use timezone-aware objects to represent datetimes in UTC: datetime.datetime.now(datetime.UTC).
    time=datetime.utcnow(),

-- Docs: https://docs.pytest.org/en/stable/how-to/capture-warnings.html
======================== 7 passed, 50 warnings in 4.47s ========================.
2.  **Payment Service (Node.js/Express):** Webhook handling tests using .
3.  **Web Demo (JavaScript):** Logic and E2E tests using  and .
4.  **Mobile Apps (Android/iOS):** Native unit and UI tests.

## 🐍 Backend API ()

### Prerequisites
- Python 3.11+
- Dependencies:

### Running Tests
Run all tests with coverage report:
```bash
cd backend/api
pytest --cov=app tests/
```

### Test Structure
- `tests/conftest.py`: Global fixtures (DB, Client, Auth).
- `tests/test_auth.py`: User registration, login, profile management.
- `tests/test_subscriptions.py`: Trial creation, activation, status checks.
- `tests/test_weather.py`: Weather data endpoints (mocked external services).
- `tests/test_health.py`: System health check.

## 💳 Payment Service ()

### Prerequisites
- Node.js 18+
- Dependencies:

### Running Tests
```bash
cd backend/payment-service
npm test
```

### Coverage
- Webhook validation (Apple/Google).
- Error handling (Invalid signatures, missing payloads).
- Health endpoints.

## 🌐 Web Demo ()

### Prerequisites
- Node.js 18+
- Dependencies:
- Playwright browsers: Downloading Chrome for Testing 145.0.7632.6 (playwright chromium v1208)[2m from https://cdn.playwright.dev/builds/cft/145.0.7632.6/linux64/chrome-linux64.zip[22m
|                                                                                |   0% of 167.3 MiB
|■■■■■■■■                                                                        |  10% of 167.3 MiB
|■■■■■■■■■■■■■■■■                                                                |  20% of 167.3 MiB
|■■■■■■■■■■■■■■■■■■■■■■■■                                                        |  30% of 167.3 MiB
|■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■                                                |  40% of 167.3 MiB
|■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■                                        |  50% of 167.3 MiB
|■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■                                |  60% of 167.3 MiB
|■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■                        |  70% of 167.3 MiB
|■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■                |  80% of 167.3 MiB
|■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■        |  90% of 167.3 MiB
|■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■| 100% of 167.3 MiB
Chrome for Testing 145.0.7632.6 (playwright chromium v1208) downloaded to /home/jules/.cache/ms-playwright/chromium-1208
Downloading Chrome Headless Shell 145.0.7632.6 (playwright chromium-headless-shell v1208)[2m from https://cdn.playwright.dev/builds/cft/145.0.7632.6/linux64/chrome-headless-shell-linux64.zip[22m
|                                                                                |   0% of 110.9 MiB
|■■■■■■■■                                                                        |  10% of 110.9 MiB
|■■■■■■■■■■■■■■■■                                                                |  20% of 110.9 MiB
|■■■■■■■■■■■■■■■■■■■■■■■■                                                        |  30% of 110.9 MiB
|■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■                                                |  40% of 110.9 MiB
|■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■                                        |  50% of 110.9 MiB
|■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■                                |  60% of 110.9 MiB
|■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■                        |  70% of 110.9 MiB
|■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■                |  80% of 110.9 MiB
|■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■        |  90% of 110.9 MiB
|■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■| 100% of 110.9 MiB
Chrome Headless Shell 145.0.7632.6 (playwright chromium-headless-shell v1208) downloaded to /home/jules/.cache/ms-playwright/chromium_headless_shell-1208
Downloading Firefox 146.0.1 (playwright firefox v1509)[2m from https://cdn.playwright.dev/dbazure/download/playwright/builds/firefox/1509/firefox-ubuntu-24.04.zip[22m
|                                                                                |   0% of 99.5 MiB
|■■■■■■■■                                                                        |  10% of 99.5 MiB
|■■■■■■■■■■■■■■■■                                                                |  20% of 99.5 MiB
|■■■■■■■■■■■■■■■■■■■■■■■■                                                        |  30% of 99.5 MiB
|■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■                                                |  40% of 99.5 MiB
|■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■                                        |  50% of 99.5 MiB
|■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■                                |  60% of 99.5 MiB
|■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■                        |  70% of 99.5 MiB
|■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■                |  80% of 99.5 MiB
|■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■        |  90% of 99.5 MiB
|■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■| 100% of 99.5 MiB
Firefox 146.0.1 (playwright firefox v1509) downloaded to /home/jules/.cache/ms-playwright/firefox-1509
Downloading WebKit 26.0 (playwright webkit v2248)[2m from https://cdn.playwright.dev/dbazure/download/playwright/builds/webkit/2248/webkit-ubuntu-24.04.zip[22m
|                                                                                |   0% of 99.2 MiB
|■■■■■■■■                                                                        |  10% of 99.2 MiB
|■■■■■■■■■■■■■■■■                                                                |  20% of 99.2 MiB
|■■■■■■■■■■■■■■■■■■■■■■■■                                                        |  30% of 99.2 MiB
|■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■                                                |  40% of 99.2 MiB
|■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■                                        |  50% of 99.2 MiB
|■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■                                |  60% of 99.2 MiB
|■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■                        |  70% of 99.2 MiB
|■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■                |  80% of 99.2 MiB
|■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■        |  90% of 99.2 MiB
|■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■| 100% of 99.2 MiB
WebKit 26.0 (playwright webkit v2248) downloaded to /home/jules/.cache/ms-playwright/webkit-2248

### Running Tests
Unit tests (Logic):
```bash
cd web-demo
npm test
```

E2E Tests (Browser):
```bash
# Requires local server running
npm run test:e2e
```

## 📱 Mobile Apps

### Android ()
Run unit tests via Gradle:
```bash
./gradlew testDebugUnitTest
```
Run UI tests (requires emulator):
```bash
./gradlew connectedAndroidTest
```

### iOS ()
Run tests via :
```bash
xcodebuild test -scheme ClimaAI -destination 'platform=iOS Simulator,name=iPhone 15'
```

## 🤖 CI/CD

Automated testing is configured via GitHub Actions in `.github/workflows/test.yml`.
Tests run on every push to `main` and `develop` branches, and on all Pull Requests.
