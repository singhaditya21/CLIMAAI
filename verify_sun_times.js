
const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();

  // Navigate to the local server
  await page.goto('http://localhost:8081');

  // Wait for the weather container to be visible
  await page.waitForSelector('#currentWeather');

  // Wait for the sun times to be rendered
  await page.waitForSelector('.sun-times');

  // Take a screenshot of the weather card
  await page.screenshot({ path: 'weather_card_with_sun_times.png', fullPage: true });

  await browser.close();
})();
