from playwright.sync_api import Page, expect, sync_playwright
import time
import os

def verify_google_login_ui(page: Page):
    print("Navigating to http://localhost:3000")
    page.goto("http://localhost:3000")

    # Wait for the app to load
    time.sleep(2)

    # Click settings nav button inside homeScreen
    print("Clicking settings button...")
    # Use force=True to bypass overlapping elements if needed
    page.locator("button[data-screen='settings']").first.click(force=True)

    # Wait for settings screen to be visible
    print("Waiting for settings screen...")
    expect(page.locator("#settingsScreen")).to_be_visible()

    # Click logout button
    print("Clicking logout button...")
    logout_btn = page.locator("#logoutBtn")
    logout_btn.click(force=True)

    # Wait for login screen
    print("Waiting for login screen...")
    expect(page.locator("#loginScreen")).to_be_visible()

    # Check for Google Sign-In button
    print("Checking for Google Sign-In button...")
    google_btn = page.locator("#googleSignInBtn")
    expect(google_btn).to_be_visible()

    # Take screenshot
    screenshot_path = "/home/jules/verification/google_login_ui.png"
    page.screenshot(path=screenshot_path)
    print(f"Screenshot taken at {screenshot_path}")

if __name__ == "__main__":
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page()
        try:
            verify_google_login_ui(page)
        except Exception as e:
            print(f"Error: {e}")
            try:
                page.screenshot(path="/home/jules/verification/error.png")
            except:
                pass
        finally:
            browser.close()
