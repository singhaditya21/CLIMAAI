//
//  AuthViewModel.swift
//  ClimaAI
//
//  Authentication ViewModel - MVVM Pattern
//

import Foundation
import SwiftUI
import Combine

@MainActor
class AuthViewModel: ObservableObject {
    // MARK: - Published Properties
    @Published var isAuthenticated = false
    @Published var currentUser: User?
    @Published var isLoading = false
    @Published var errorMessage: String?
    
    // MARK: - Private Properties
    private let apiClient: APIClient
    private var cancellables = Set<AnyCancellable>()
    
    // MARK: - Initialization
    init(apiClient: APIClient = .shared) {
        self.apiClient = apiClient
        checkAuthState()
    }
    
    // MARK: - Public Methods
    
    /// Check if user is currently authenticated
    func checkAuthState() {
        isAuthenticated = apiClient.isAuthenticated
        if isAuthenticated {
            Task {
                await fetchCurrentUser()
            }
        }
    }
    
    /// Register a new user
    func register(fullName: String, email: String, password: String) async {
        isLoading = true
        errorMessage = nil
        
        do {
            let response: TokenResponse = try await apiClient.post(
                "/api/v1/users/register",
                body: [
                    "full_name": fullName,
                    "email": email,
                    "password": password
                ]
            )
            
            // Save token and update state
            apiClient.saveToken(response.accessToken)
            
            // Fetch user profile
            await fetchCurrentUser()
            
            isAuthenticated = true
            isLoading = false
        } catch {
            errorMessage = error.localizedDescription
            isLoading = false
        }
    }
    
    /// Login with email and password
    func login(email: String, password: String) async {
        isLoading = true
        errorMessage = nil
        
        do {
            let response: TokenResponse = try await apiClient.post(
                "/api/v1/users/login",
                body: [
                    "email": email,
                    "password": password
                ]
            )
            
            // Save token and update state
            apiClient.saveToken(response.accessToken)
            
            // Fetch user profile
            await fetchCurrentUser()
            
            isAuthenticated = true
            isLoading = false
        } catch {
            errorMessage = error.localizedDescription
            isLoading = false
        }
    }
    
    /// Logout the current user
    func logout() {
        apiClient.clearToken()
        currentUser = nil
        isAuthenticated = false
    }
    
    /// Fetch current user profile
    func fetchCurrentUser() async {
        do {
            let user: User = try await apiClient.get("/api/v1/users/me")
            currentUser = user
        } catch {
            print("Error fetching user: \\(error)")
            // If token is invalid, logout
            if (error as NSError).code == 401 {
                logout()
            }
        }
    }
    
    /// Update user profile
    func updateProfile(fullName: String?, preferences: UserPreferences?) async {
        isLoading = true
        errorMessage = nil
        
        do {
            var body: [String: Any] = [:]
            if let fullName = fullName {
                body["full_name"] = fullName
            }
            if let preferences = preferences {
                body["preferences"] = try? JSONEncoder().encode(preferences)
            }
            
            let updatedUser: User = try await apiClient.put("/api/v1/users/me", body: body)
            currentUser = updatedUser
            isLoading = false
        } catch {
            errorMessage = error.localizedDescription
            isLoading = false
        }
    }
    
    /// Delete user account
    func deleteAccount() async -> Bool {
        isLoading = true
        errorMessage = nil
        
        do {
            let _: [String: String] = try await apiClient.delete("/api/v1/users/me")
            logout()
            isLoading = false
            return true
        } catch {
            errorMessage = error.localizedDescription
            isLoading = false
            return false
        }
    }
    
    // MARK: - Validation Helpers
    
    func validateEmail(_ email: String) -> Bool {
        let emailRegex = "[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,64}"
        let emailPredicate = NSPredicate(format:"SELF MATCHES %@", emailRegex)
        return emailPredicate.evaluate(with: email)
    }
    
    func validatePassword(_ password: String) -> (isValid: Bool, message: String) {
        if password.count < 8 {
            return (false, "Password must be at least 8 characters")
        }
        
        let hasUppercase = password.range(of: "[A-Z]", options: .regularExpression) != nil
        let hasLowercase = password.range(of: "[a-z]", options: .regularExpression) != nil
        let hasNumber = password.range(of: "[0-9]", options: .regularExpression) != nil
        
        if !hasUppercase || !hasLowercase || !hasNumber {
            return (false, "Password must contain uppercase, lowercase, and numbers")
        }
        
        return (true, "Password is strong")
    }
}
