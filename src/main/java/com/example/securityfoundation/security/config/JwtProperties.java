package com.example.securityfoundation.security.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

/** RSA file locations are required outside demo; the demo profile supplies in-memory keys. */
@Validated
@ConfigurationProperties("app.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotNull Duration lifetime,
        Resource privateKey,
        Resource publicKey) {

    @AssertTrue(message = "JWT lifetime must be between one minute and one hour")
    public boolean isLifetimeValid() {
        return lifetime != null
                && lifetime.compareTo(Duration.ofMinutes(1)) >= 0
                && lifetime.compareTo(Duration.ofHours(1)) <= 0;
    }
}
