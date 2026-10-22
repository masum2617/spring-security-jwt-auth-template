package com.example.securityfoundation.auth;

public record LoginResponse(String accessToken, String tokenType, long expiresInSeconds) {
    @Override
    public String toString() {
        return "LoginResponse[access token redacted]";
    }
}
