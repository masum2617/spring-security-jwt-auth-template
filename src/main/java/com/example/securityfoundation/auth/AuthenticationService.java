package com.example.securityfoundation.auth;

public interface AuthenticationService {
    LoginResponse login(LoginRequest request);
}
