
from playwright.sync_api import sync_playwright

def verify_sun_times():
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page()
        page.goto('http://localhost:8081')

        # Wait for weather container
        page.wait_for_selector('#currentWeather')

        # Wait for sun times to be rendered
        page.wait_for_selector('.sun-times')

        # Take a screenshot
        page.screenshot(path='weather_card_with_sun_times.png')

        browser.close()

if __name__ == "__main__":
    verify_sun_times()
