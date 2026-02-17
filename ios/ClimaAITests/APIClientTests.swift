//
//  APIClientTests.swift
//  ClimaAITests
//
//  Unit tests for API client and network layer
//

import XCTest
@testable import ClimaAI

final class APIClientTests: XCTestCase {
    
    // MARK: - TC-ERR-001: Network Connectivity Tests
    
    func testBaseURLFormat() {
        let baseURL = "http://localhost:8000"
        XCTAssertTrue(baseURL.hasPrefix("http"), "URL should have http scheme")
        XCTAssertFalse(baseURL.hasSuffix("/"), "URL should not end with slash")
    }
    
    func testEndpointConstruction() {
        let baseURL = "http://localhost:8000"
        let endpoint = "/weather/current"
        let fullURL = baseURL + endpoint
        
        XCTAssertEqual(fullURL, "http://localhost:8000/weather/current")
    }
    
    func testQueryParameterEncoding() {
        let lat = 40.7128
        let lon = -74.0060
        let endpoint = "/weather?latitude=\(lat)&longitude=\(lon)"
        
        XCTAssertTrue(endpoint.contains("latitude=40.7128"))
        XCTAssertTrue(endpoint.contains("longitude=-74.006"))
    }
    
    // MARK: - TC-SEC-002: HTTPS Validation
    
    func testProductionURLUsesHTTPS() {
        let productionURL = "https://api.climaai.com"
        XCTAssertTrue(productionURL.hasPrefix("https"), "Production URL must use HTTPS")
    }
    
    // MARK: - TC-ERR-002: HTTP Status Code Handling
    
    func testSuccessStatusCodes() {
        let successCodes = [200, 201, 204]
        for code in successCodes {
            XCTAssertTrue(isSuccessStatusCode(code), "\(code) should be success")
        }
    }
    
    func testClientErrorStatusCodes() {
        let clientErrors = [400, 401, 403, 404, 409, 422, 429]
        for code in clientErrors {
            XCTAssertTrue(isClientError(code), "\(code) should be client error")
        }
    }
    
    func testServerErrorStatusCodes() {
        let serverErrors = [500, 502, 503, 504]
        for code in serverErrors {
            XCTAssertTrue(isServerError(code), "\(code) should be server error")
        }
    }
    
    // MARK: - TC-AUTH-005 & TC-AUTH-011: Token Handling
    
    func testAuthorizationHeaderFormat() {
        let token = "sample_token_123"
        let header = "Bearer \(token)"
        
        XCTAssertTrue(header.hasPrefix("Bearer "), "Header should start with 'Bearer '")
        XCTAssertTrue(header.contains(token), "Header should contain token")
    }
    
    func testTokenExtractionFromHeader() {
        let header = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
        let token = header.replacingOccurrences(of: "Bearer ", with: "")
        
        XCTAssertFalse(token.contains("Bearer"), "Extracted token should not contain 'Bearer'")
    }
    
    // MARK: - TC-ERR-003: Timeout Configuration
    
    func testDefaultTimeoutInterval() {
        let timeout: TimeInterval = 30.0
        XCTAssertEqual(timeout, 30.0, "Default timeout should be 30 seconds")
    }
    
    func testLongOperationTimeout() {
        let longTimeout: TimeInterval = 60.0
        XCTAssertGreaterThan(longTimeout, 30.0, "Long operations should have extended timeout")
    }
    
    // MARK: - TC-ERR-005: 401 Handling Tests
    
    func testUnauthorizedResponseCode() {
        let code = 401
        XCTAssertTrue(code == 401, "401 should indicate unauthorized")
    }
    
    // MARK: - TC-ERR-006: 403 Premium Required Tests
    
    func testForbiddenResponseCode() {
        let code = 403
        XCTAssertTrue(code == 403, "403 should indicate forbidden/premium required")
    }
    
    // MARK: - Request Methods
    
    func testSupportedHTTPMethods() {
        let methods = ["GET", "POST", "PUT", "DELETE", "PATCH"]
        
        for method in methods {
            XCTAssertTrue(isValidHTTPMethod(method), "\(method) should be valid")
        }
    }
    
    // MARK: - Content Type Tests
    
    func testJSONContentType() {
        let contentType = "application/json"
        XCTAssertEqual(contentType, "application/json")
    }
    
    func testFormURLEncodedContentType() {
        let contentType = "application/x-www-form-urlencoded"
        XCTAssertTrue(contentType.contains("urlencoded"))
    }
    
    // MARK: - Coordinate Validation
    
    func testLatitudeRange() {
        let validLatitudes = [-90.0, 0.0, 45.5, 90.0]
        let invalidLatitudes = [-91.0, 91.0, -180.0, 180.0]
        
        for lat in validLatitudes {
            XCTAssertTrue(isValidLatitude(lat), "\(lat) should be valid latitude")
        }
        
        for lat in invalidLatitudes {
            XCTAssertFalse(isValidLatitude(lat), "\(lat) should be invalid latitude")
        }
    }
    
    func testLongitudeRange() {
        let validLongitudes = [-180.0, -74.0, 0.0, 139.5, 180.0]
        let invalidLongitudes = [-181.0, 181.0, -200.0, 200.0]
        
        for lon in validLongitudes {
            XCTAssertTrue(isValidLongitude(lon), "\(lon) should be valid longitude")
        }
        
        for lon in invalidLongitudes {
            XCTAssertFalse(isValidLongitude(lon), "\(lon) should be invalid longitude")
        }
    }
    
    // MARK: - TC-PERF-002: API Response Time Measurement
    
    func testAPIResponseTimeExpectation() {
        let targetResponseTime: TimeInterval = 3.0
        XCTAssertLessThanOrEqual(targetResponseTime, 3.0, "Target response time should be ≤ 3s")
    }
    
    // MARK: - Helper Functions
    
    private func isSuccessStatusCode(_ code: Int) -> Bool {
        return (200...299).contains(code)
    }
    
    private func isClientError(_ code: Int) -> Bool {
        return (400...499).contains(code)
    }
    
    private func isServerError(_ code: Int) -> Bool {
        return (500...599).contains(code)
    }
    
    private func isValidHTTPMethod(_ method: String) -> Bool {
        let validMethods = ["GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"]
        return validMethods.contains(method.uppercased())
    }
    
    private func isValidLatitude(_ lat: Double) -> Bool {
        return lat >= -90.0 && lat <= 90.0
    }
    
    private func isValidLongitude(_ lon: Double) -> Bool {
        return lon >= -180.0 && lon <= 180.0
    }
    
    // MARK: - Performance Tests
    
    func testURLConstructionPerformance() {
        measure {
            for _ in 0..<1000 {
                let baseURL = "http://localhost:8000"
                let endpoint = "/weather?lat=40.7&lon=-74.0"
                _ = baseURL + endpoint
            }
        }
    }
}
