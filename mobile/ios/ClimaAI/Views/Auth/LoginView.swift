//
//  LoginView.swift
//  ClimaAI
//
//  User login screen
//

import SwiftUI

struct LoginView: View {
    @EnvironmentObject var authViewModel: AuthViewModel
    @State private var email = ""
    @State private var password = ""
    @State private var showingPassword = false
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 24) {
                    // Logo and title
                    VStack(spacing: 12) {
                        Image(systemName: "cloud.sun.fill")
                            .font(.system(size: 64))
                            .foregroundStyle(
                                .linearGradient(
                                    colors: [.blue, .cyan],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )
                            .padding(.top, 40)
                        
                        Text("Welcome Back")
                            .font(.largeTitle)
                            .fontWeight(.bold)
                        
                        Text("Sign in to continue")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }
                    .padding(.bottom, 20)
                    
                    // Email field
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
                    }
                    
                    // Password field
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
                    }
                    
                    // Error message
                    if let error = authViewModel.errorMessage {
                        Text(error)
                            .font(.caption)
                            .foregroundColor(.red)
                            .padding(.horizontal)
                    }
                    
                    // Login button
                    Button {
                        Task {
                            await authViewModel.login(email: email, password: password)
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
                                Text("Sign In")
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
                    .disabled(authViewModel.isLoading || email.isEmpty || password.isEmpty)
                    .opacity(email.isEmpty || password.isEmpty ? 0.6 : 1)
                    .padding(.top, 8)
                    
                    // Register link
                    HStack {
                        Text("Don't have an account?")
                            .foregroundColor(.secondary)
                        
                        NavigationLink {
                            RegisterView()
                        } label: {
                            Text("Sign Up")
                                .fontWeight(.semibold)
                                .foregroundColor(.blue)
                        }
                    }
                    .font(.subheadline)
                    
                    Spacer()
                }
                .padding(.horizontal, 24)
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
            }
        }
    }
}

#Preview {
    LoginView()
        .environmentObject(AuthViewModel())
}
