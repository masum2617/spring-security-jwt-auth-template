package com.example.securityfoundation.security.error;

import com.example.securityfoundation.error.ProblemResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class SecurityAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final ProblemResponseWriter problems;

    public SecurityAuthenticationEntryPoint(ProblemResponseWriter problems) {
        this.problems = problems;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        // Preserve Spring's Bearer challenge and status, but keep the JSON detail generic.
        bearerEntryPoint.commence(request, response, exception);
        problems.write(request, response, response.getStatus(), "Authentication required or access token invalid");
    }
}
