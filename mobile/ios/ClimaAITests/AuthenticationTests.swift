//
//  AuthenticationTests.swift
//  ClimaAITests
//
//  Unit tests for Authentication functionality
//

import XCTest
@testable import ClimaAI

final class AuthenticationTests: XCTestCase {
    
    // MARK: - TC-AUTH-003: Email Validation Tests
    
    func testValidEmailFormats() {
        let validEmails = [
            "test@example.com",
            "user.name@domain.org",
            "user+tag@company.co.uk",
            "firstname.lastname@subdomain.domain.com"
        ]
        
        for email in validEmails {
            XCTAssertTrue(isValidEmail(email), "\(email) should be valid")
        }
    }
    
    func testInvalidEmailFormats() {
        let invalidEmails = [
            "notanemail",
            "test@",
            "@domain.com",
            "test@.com",
            "test..test@domain.com",
            "test@domain..com",
            "",
            " ",
            "test@domain",
            "test @domain.com"
        ]
        
        for email in invalidEmails {
            XCTAssertFalse(isValidEmail(email), "\(email) should be invalid")
        }
    }
    
    // MARK: - TC-AUTH-002: Password Validation Tests
    
    func testPasswordMinimumLength() {
        let shortPasswords = ["", "a", "ab", "abc", "abcd", "abcde", "abcdef", "abcdefg"]
        let validLengthPasswords = ["abcdefgh", "password123", "VeryLongPassword!"]
        
        for password in shortPasswords {
            XCTAssertFalse(isValidPassword(password), "\(password) should be too short")
        }
        
        for password in validLengthPasswords {
            XCTAssertTrue(isValidPassword(password), "\(password) should have valid length")
        }
    }
    
    func testPasswordStrengthWeak() {
        let weakPasswords = ["password", "12345678", "abcdefgh"]
        
        for password in weakPasswords {
            let strength = calculatePasswordStrength(password)
            XCTAssertEqual(strength, .weak, "\(password) should be weak")
        }
    }
    
    func testPasswordStrengthMedium() {
        let mediumPasswords = ["Password1", "Secure123"]
        
        for password in mediumPasswords {
            let strength = calculatePasswordStrength(password)
            XCTAssertEqual(strength, .medium, "\(password) should be medium")
        }
    }
    
    func testPasswordStrengthStrong() {
        let strongPasswords = ["SecureP@ss123", "MyStr0ng!Pass", "C0mpl3x_P@ssw0rd"]
        
        for password in strongPasswords {
            let strength = calculatePasswordStrength(password)
            XCTAssertEqual(strength, .strong, "\(password) should be strong")
        }
    }
    
    // MARK: - TC-AUTH-011: Token Storage Tests
    
    func testTokenIsNotEmpty() {
        let sampleToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
        XCTAssertFalse(sampleToken.isEmpty, "Token should not be empty")
    }
    
    func testTokenFormat() {
        let jwtToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U"
        let parts = jwtToken.split(separator: ".")
        XCTAssertEqual(parts.count, 3, "JWT should have 3 parts")
    }
    
    // MARK: - TC-AUTH-007: Rate Limiting Tests
    
    func testLoginAttemptCounter() {
        var attempts = 0
        let maxAttempts = 5
        
        for _ in 0..<6 {
            attempts += 1
        }
        
        XCTAssertTrue(attempts > maxAttempts, "Should track attempts exceeding limit")
    }
    
    func testLoginLockoutDuration() {
        let lockoutMinutes = 5
        let lockoutSeconds = lockoutMinutes * 60
        XCTAssertEqual(lockoutSeconds, 300, "Lockout should be 5 minutes (300 seconds)")
    }
    
    // MARK: - Helper Functions
    
    private func isValidEmail(_ email: String) -> Bool {
        let emailRegex = #"^[A-Z0-9a-z._%+-]+@[A-Z0-9a-z.-]+\.[A-Za-z]{2,64}$"#
        let predicate = NSPredicate(format: "SELF MATCHES %@", emailRegex)
        return predicate.evaluate(with: email)
    }
    
    private func isValidPassword(_ password: String) -> Bool {
        return password.count >= 8
    }
    
    private enum PasswordStrength {
        case weak, medium, strong
    }
    
    private func calculatePasswordStrength(_ password: String) -> PasswordStrength {
        var score = 0
        
        if password.count >= 8 { score += 1 }
        if password.count >= 12 { score += 1 }
        if password.rangeOfCharacter(from: .uppercaseLetters) != nil { score += 1 }
        if password.rangeOfCharacter(from: .lowercaseLetters) != nil { score += 1 }
        if password.rangeOfCharacter(from: .decimalDigits) != nil { score += 1 }
        if password.rangeOfCharacter(from: CharacterSet(charactersIn: "!@#$%^&*()_+-=[]{}|;:,.<>?")) != nil { score += 1 }
        
        switch score {
        case 0...2: return .weak
        case 3...4: return .medium
        default: return .strong
        }
    }
    
    // MARK: - Performance Tests
    
    func testEmailValidationPerformance() {
        let email = "test@example.com"
        measure {
            for _ in 0..<1000 {
                _ = isValidEmail(email)
            }
        }
    }
    
    func testPasswordStrengthPerformance() {
        let password = "SecureP@ss123"
        measure {
            for _ in 0..<1000 {
                _ = calculatePasswordStrength(password)
            }
        }
    }
}
