package com.example.securityfoundation.demo;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.demo")
public record DemoProperties(@NotBlank String userPassword, @NotBlank String adminPassword) {

    @AssertTrue(message = "Each demo password must contain 5 to 72 UTF-8 bytes")
    public boolean isPasswordLengthValid() {
        return valid(userPassword) && valid(adminPassword);
    }

    private boolean valid(String password) {
        if (password == null) {
            return false;
        }
        int length = password.getBytes(StandardCharsets.UTF_8).length;
        return length >= 5 && length <= 72;
    }

    @Override
    public String toString() {
        return "DemoProperties[passwords redacted]";
    }
}
