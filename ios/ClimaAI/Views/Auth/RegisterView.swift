//
//  RegisterView.swift
//  ClimaAI
//
//  User registration screen
//

import SwiftUI

struct RegisterView: View {
    @EnvironmentObject var authViewModel: AuthViewModel
    @State private var fullName = ""
    @State private var email = ""
    @State private var password = ""
    @State private var confirmPassword = ""
    @State private var showingPassword = false
    @State private var acceptedTerms = false
    @Environment(\.dismiss) var dismiss
    
    var passwordsMatch: Bool {
        !password.isEmpty && password == confirmPassword
    }
    
    var passwordValidation: (isValid: Bool, message: String) {
        authViewModel.validatePassword(password)
    }
    
    var isFormValid: Bool {
        !fullName.isEmpty &&
        authViewModel.validateEmail(email) &&
        passwordValidation.isValid &&
        passwordsMatch &&
        acceptedTerms
    }
    
    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                // Header
                VStack(spacing: 12) {
                    Image(systemName: "person.crop.circle.fill.badge.plus")
                        .font(.system(size: 64))
                        .foregroundStyle(
                            .linearGradient(
                                colors: [.blue, .cyan],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .padding(.top, 40)
                    
                    Text("Create Account")
                        .font(.largeTitle)
                        .fontWeight(.bold)
                    
                    Text("Get started with ClimaAI")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
                .padding(.bottom, 20)
                
                // Full Name
                VStack(alignment: .leading, spacing: 8) {
                    Text("Full Name")
                        .font(.subheadline)
                        .fontWeight(.medium)
                    
                    HStack {
                        Image(systemName: "person.fill")
                            .foregroundColor(.secondary)
                        TextField("John Doe", text: $fullName)
                            .textContentType(.name)
                    }
                    .padding()
                    .background(Color(.systemGray6))
                    .cornerRadius(12)
                }
                
                // Email
                VStack(alignment: .leading, spacing: 8) {
                    Text("Email")
                        .font(.subheadline)
                        .fontWeight(.medium)
                    
                    HStack {
                        Image(systemName: "envelope.fill")
                            .foregroundColor(.secondary)
                        TextField("your@email.com", text: $email)
                            .textContentType(.emailAddress)
                            .autocapitalization(.none)
                            .keyboardType(.emailAddress)
                    }
                    .padding()
                    .background(Color(.systemGray6))
                    .cornerRadius(12)
                    
                    if !email.isEmpty && !authViewModel.validateEmail(email) {
                        Label("Invalid email format", systemImage: "exclamationmark.circle.fill")
                            .font(.caption)
                            .foregroundColor(.red)
                    }
                }
                
                // Password
                VStack(alignment: .leading, spacing: 8) {
                    Text("Password")
                        .font(.subheadline)
                        .fontWeight(.medium)
                    
                    HStack {
                        Image(systemName: "lock.fill")
                            .foregroundColor(.secondary)
                        
                        if showingPassword {
                            TextField("Password", text: $password)
                        } else {
                            SecureField("Password", text: $password)
                        }
                        
                        Button {
                            showingPassword.toggle()
                        } label: {
                            Image(systemName: showingPassword ? "eye.slash.fill" : "eye.fill")
                                .foregroundColor(.secondary)
                        }
                    }
                    .padding()
                    .background(Color(.systemGray6))
                    .cornerRadius(12)
                    
                    // Password strength indicator
                    if !password.isEmpty {
                        HStack(spacing: 4) {
                            ForEach(0..<3) { index in
                                RoundedRectangle(cornerRadius: 2)
                                    .fill(passwordStrengthColor(for: index))
                                    .frame(height: 4)
                            }
                        }
                        
                        Text(passwordValidation.message)
                            .font(.caption)
                            .foregroundColor(passwordValidation.isValid ? .green : .orange)
                    }
                }
                
                // Confirm Password
                VStack(alignment: .leading, spacing: 8) {
                    Text("Confirm Password")
                        .font(.subheadline)
                        .fontWeight(.medium)
                    
                    HStack {
                        Image(systemName: "lock.fill")
                            .foregroundColor(.secondary)
                        SecureField("Confirm password", text: $confirmPassword)
                    }
                    .padding()
                    .background(Color(.systemGray6))
                    .cornerRadius(12)
                    
                    if !confirmPassword.isEmpty && !passwordsMatch {
                        Label("Passwords don't match", systemImage: "exclamationmark.circle.fill")
                            .font(.caption)
                            .foregroundColor(.red)
                    } else if passwordsMatch {
                        Label("Passwords match", systemImage: "checkmark.circle.fill")
                            .font(.caption)
                            .foregroundColor(.green)
                    }
                }
                
                // Terms and Privacy
                Toggle(isOn: $acceptedTerms) {
                    HStack(spacing: 4) {
                        Text("I agree to the")
                        Link("Terms", destination: URL(string: "https://singhaditya21.github.io/CLIMAAI/terms.html")!)
                        Text("and")
                        Link("Privacy Policy", destination: URL(string: "https://singhaditya21.github.io/CLIMAAI/privacy.html")!)
                    }
                    .font(.caption)
                }
                .toggleStyle(CheckboxToggleStyle())
                
                // Error message
                if let error = authViewModel.errorMessage {
                    Text(error)
                        .font(.caption)
                        .foregroundColor(.red)
                        .padding(.horizontal)
                }
                
                // Register button
                Button {
                    Task {
                        await authViewModel.register(
                            fullName: fullName,
                            email: email,
                            password: password
                        )
                        if authViewModel.isAuthenticated {
                            dismiss()
                        }
                    }
                } label: {
                    HStack {
                        if authViewModel.isLoading {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                        } else {
                            Text("Create Account")
                                .fontWeight(.semibold)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(
                        LinearGradient(
                            colors: [.blue, .cyan],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .foregroundColor(.white)
                    .cornerRadius(12)
                }
                .disabled(authViewModel.isLoading || !isFormValid)
                .opacity(isFormValid ? 1 : 0.6)
                .padding(.top, 8)
                
                Spacer()
            }
            .padding(.horizontal, 24)
        }
        .navigationTitle("Sign Up")
        .navigationBarTitleDisplayMode(.inline)
    }
    
    private func passwordStrengthColor(for index: Int) -> Color {
        let strength = passwordValidation.isValid ? 3 : (password.count >= 8 ? 2 : 1)
        return index < strength ? (passwordValidation.isValid ? .green : .orange) : Color(.systemGray5)
    }
}

// Custom checkbox toggle style
struct CheckboxToggleStyle: ToggleStyle {
    func makeBody(configuration: Configuration) -> some View {
        Button {
            configuration.isOn.toggle()
        } label: {
            HStack(alignment: .top, spacing: 8) {
                Image(systemName: configuration.isOn ? "checkmark.square.fill" : "square")
                    .foregroundColor(configuration.isOn ? .blue : .secondary)
                    .font(.title3)
                
                configuration.label
                    .foregroundColor(.primary)
            }
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    NavigationView {
        RegisterView()
            .environmentObject(AuthViewModel())
    }
}
