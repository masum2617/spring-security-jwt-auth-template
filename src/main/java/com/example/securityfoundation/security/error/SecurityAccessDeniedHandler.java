package com.example.securityfoundation.security.error;

import com.example.securityfoundation.error.ProblemResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class SecurityAccessDeniedHandler implements AccessDeniedHandler {
    private final BearerTokenAccessDeniedHandler bearerDeniedHandler = new BearerTokenAccessDeniedHandler();
    private final ProblemResponseWriter problems;

    public SecurityAccessDeniedHandler(ProblemResponseWriter problems) {
        this.problems = problems;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        bearerDeniedHandler.handle(request, response, exception);
        problems.write(request, response, response.getStatus(), "Access denied");
    }
}
