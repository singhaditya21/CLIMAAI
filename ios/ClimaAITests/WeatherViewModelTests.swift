import XCTest
import Combine
@testable import ClimaAI

final class WeatherViewModelTests: XCTestCase {
    
    var viewModel: WeatherViewModel!
    var mockRepository: MockWeatherRepository!
    var cancellables: Set<AnyCancellable>!
    
    override func setUpWithError() throws {
        mockRepository = MockWeatherRepository()
        viewModel = WeatherViewModel(repository: mockRepository)
        cancellables = []
    }

    override func tearDownWithError() throws {
        viewModel = nil
        mockRepository = nil
        cancellables = nil
    }

    func testFetchWeather_Success() async throws {
        // Given
        let expectedTemperature = 25.0
        let mockResponse = WeatherResponse(
            current: CurrentWeather(temperature: expectedTemperature, condition: "Sunny"),
            hourly: [],
            daily: []
        )
        mockRepository.mockResult = .success(mockResponse)
        
        // When
        await viewModel.fetchWeather(lat: 51.5, lon: -0.1)
        
        // Then
        switch viewModel.state {
        case .success(let weather):
            XCTAssertEqual(weather.current.temperature, expectedTemperature)
            XCTAssertEqual(weather.current.condition, "Sunny")
        default:
            XCTFail("Expected success state but got \(viewModel.state)")
        }
    }
    
    func testFetchWeather_Failure() async throws {
        // Given
        let expectedError = NetworkError.connectionFailed
        mockRepository.mockResult = .failure(expectedError)

        // When
        await viewModel.fetchWeather(lat: 51.5, lon: -0.1)

        // Then
        if case .error(let message) = viewModel.state {
            XCTAssertEqual(message, expectedError.localizedDescription)
        } else {
            XCTFail("Expected error state")
        }
    }
    
    func testLoadingState() {
        // Given
        let expectation = XCTestExpectation(description: "State changes to loading")

        viewModel.
            .sink { state in
                if case .loading = state {
                    expectation.fulfill()
                }
            }
            .store(in: &cancellables)

        // When
        viewModel.fetchWeather(lat: 0, lon: 0)

        // Then
        wait(for: [expectation], timeout: 1.0)
    }
}

// Mock Repository
class MockWeatherRepository: WeatherRepositoryProtocol {
    var mockResult: Result<WeatherResponse, Error>?
    
    func getWeather(lat: Double, lon: Double) async throws -> WeatherResponse {
        if let result = mockResult {
            switch result {
            case .success(let response): return response
            case .failure(let error): throw error
            }
        }
        fatalError("Mock result not set")
    }
}
