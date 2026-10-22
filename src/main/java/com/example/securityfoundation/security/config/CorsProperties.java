package com.example.securityfoundation.security.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.cors")
public record CorsProperties(@NotEmpty List<String> allowedOrigins) {

    @AssertTrue(message = "CORS origins must be explicit http(s) origins without a path")
    public boolean isOriginsValid() {
        if (allowedOrigins == null) {
            return false;
        }
        return allowedOrigins.stream().allMatch(CorsProperties::isOrigin);
    }

    private static boolean isOrigin(String origin) {
        try {
            URI uri = URI.create(origin);
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && (uri.getPath() == null || uri.getPath().isEmpty())
                    && uri.getQuery() == null
                    && uri.getFragment() == null;
        } catch (IllegalArgumentException | NullPointerException exception) {
            return false;
        }
    }
}
