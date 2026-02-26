import XCTest

final class WeatherUITests: XCTestCase {

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    override func tearDownWithError() throws {
    }

    func testExample() throws {
        // UI tests must launch the application that they test.
        let app = XCUIApplication()
        app.launchArguments = ["UITesting"] // Use mock data
        app.launch()

        // Use XCTAssert and related functions to verify your tests produce the correct results.

        // Check for Home Screen elements
        let locationLabel = app.staticTexts["locationLabel"]
        XCTAssertTrue(locationLabel.exists)

        let temperatureLabel = app.staticTexts["temperatureLabel"]
        XCTAssertTrue(temperatureLabel.exists)

        // Interact
        let refreshButton = app.buttons["refreshButton"]
        XCTAssertTrue(refreshButton.exists)
        refreshButton.tap()

        // Verify state change (e.g. loading indicator)
        let loadingIndicator = app.activityIndicators["loadingIndicator"]
        XCTAssertTrue(loadingIndicator.exists)
    }

    func testLaunchPerformance() throws {
        if #available(macOS 10.15, iOS 13.0, tvOS 13.0, watchOS 7.0, *) {
            // This measures how long it takes to launch your application.
            measure(metrics: [XCTApplicationLaunchMetric()]) {
                XCUIApplication().launch()
            }
        }
    }
}
