const { test, expect } = require('@playwright/test');

test.describe('ClimaAI Web Demo', () => {
  test.beforeEach(async ({ page }) => {
    // Navigate to the app. Adjust URL for CI/local dev.
    // Assuming served at root or via file protocol for testing
    await page.goto('http://localhost:8000/web-demo/index.html');
  });

  test('should display login screen initially', async ({ page }) => {
    await expect(page.locator('#loginScreen')).toBeVisible();
    await expect(page.locator('#homeScreen')).toBeHidden();
  });

  test('should allow login with demo credentials', async ({ page }) => {
    await page.fill('#loginEmail', 'demo@climaai.com');
    await page.fill('#loginPassword', 'Test1234');
    await page.click('#loginForm button[type="submit"]');

    // Wait for home screen transition
    await expect(page.locator('#homeScreen')).toBeVisible({ timeout: 5000 });

    // Verify location loading or default text
    const locationText = await page.locator('#locationName').textContent();
    expect(locationText).not.toBe('Loading...');
  });

  test('should display weather data after login', async ({ page }) => {
    // Login
    await page.fill('#loginEmail', 'demo@climaai.com');
    await page.fill('#loginPassword', 'Test1234');
    await page.click('#loginForm button[type="submit"]');

    await expect(page.locator('#homeScreen')).toBeVisible();

    // Verify weather elements
    await expect(page.locator('.weather-temp-group .temperature')).toBeVisible();
    await expect(page.locator('.weather-description')).toBeVisible();

    // Check for hourly forecast items
    const hourlyItems = page.locator('.hourly-item');
    await expect(hourlyItems).toHaveCount(12); // We render 12 hours
  });

  test('should navigate between screens', async ({ page }) => {
    // Login
    await page.fill('#loginEmail', 'demo@climaai.com');
    await page.fill('#loginPassword', 'Test1234');
    await page.click('#loginForm button[type="submit"]');

    await expect(page.locator('#homeScreen')).toBeVisible();

    // Navigate to Air Quality
    await page.click('button[data-screen="airquality"]');
    await expect(page.locator('#airqualityScreen')).toBeVisible();
    await expect(page.locator('#homeScreen')).toBeHidden();

    // Navigate back to Home
    await page.click('button[data-screen="home"]');
    await expect(page.locator('#homeScreen')).toBeVisible();
  });
});
