package com.example.securityfoundation.auth;

import com.example.securityfoundation.security.config.JwtProperties;
import com.example.securityfoundation.security.jwt.JwtTokenService;
import com.example.securityfoundation.security.user.ApplicationUser;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationServiceImpl implements AuthenticationService {
    private final AuthenticationManager authenticationManager;
    private final JwtTokenService tokens;
    private final JwtProperties properties;

    public AuthenticationServiceImpl(
            AuthenticationManager authenticationManager, JwtTokenService tokens, JwtProperties properties) {
        this.authenticationManager = authenticationManager;
        this.tokens = tokens;
        this.properties = properties;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));

        if (!(authentication.getPrincipal() instanceof ApplicationUser user)) {
            throw new BadCredentialsException("User lookup must return ApplicationUser");
        }

        return new LoginResponse(
                tokens.generateAccessToken(user),
                "Bearer",
                properties.lifetime().toSeconds());
    }
}
