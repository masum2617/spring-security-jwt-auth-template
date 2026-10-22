package com.example.securityfoundation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.securityfoundation.demo.DemoController;
import com.example.securityfoundation.demo.DemoUserDetailsService;
import com.example.securityfoundation.security.config.CorsConfig;
import com.example.securityfoundation.security.config.JwtConfig;
import com.example.securityfoundation.security.user.ApplicationUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.core.userdetails.UserDetailsService;

class ConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class))
            .withUserConfiguration(JwtConfig.class)
            .withPropertyValues(
                    "app.jwt.issuer=test-issuer",
                    "app.jwt.audience=test-api",
                    "app.jwt.lifetime=15m",
                    "app.jwt.private-key="
                            + TestKeys.DIRECTORY.resolve("private.pem").toUri(),
                    "app.jwt.public-key="
                            + TestKeys.DIRECTORY.resolve("public.pem").toUri());

    @Test
    void demoStartsWithoutFileLocationsAndRejectsTokensFromAnEarlierContext() {
        ApplicationContextRunner demo = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class))
                .withUserConfiguration(JwtConfig.class)
                .withPropertyValues(
                        "spring.profiles.active=demo",
                        "app.jwt.issuer=test-issuer",
                        "app.jwt.audience=test-api",
                        "app.jwt.lifetime=15m");

        demo.run(first -> {
            assertThat(first).hasNotFailed();
            assertThat(first).hasBean("demoSigningKey").doesNotHaveBean("signingKey");
            var now = java.time.Instant.now();
            var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                    .subject("test-user")
                    .issuer("test-issuer")
                    .audience(java.util.List.of("test-api"))
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(900))
                    .id("test-id")
                    .claim("roles", java.util.List.of("USER"))
                    .build();
            var header = org.springframework.security.oauth2.jwt.JwsHeader.with(
                            org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256)
                    .build();
            String token = first.getBean(org.springframework.security.oauth2.jwt.JwtEncoder.class)
                    .encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(header, claims))
                    .getTokenValue();
            assertThat(first.getBean(org.springframework.security.oauth2.jwt.JwtDecoder.class)
                            .decode(token)
                            .getSubject())
                    .isEqualTo("test-user");
            demo.run(second -> {
                assertThat(second).hasNotFailed();
                org.assertj.core.api.Assertions.assertThatThrownBy(
                                () -> second.getBean(org.springframework.security.oauth2.jwt.JwtDecoder.class)
                                        .decode(token))
                        .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
            });
        });
    }

    @Test
    void productionDoesNotGenerateKeysWhenFileLocationsAreMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class))
                .withUserConfiguration(JwtConfig.class)
                .withPropertyValues("app.jwt.issuer=test", "app.jwt.audience=test", "app.jwt.lifetime=15m")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("local file:");
                });
    }

    @Test
    void validConfigurationStarts() {
        runner.run(context -> assertThat(context).hasNotFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0s", "30s", "61m", "-1m"})
    void rejectsLifetimeOutsideBounds(String lifetime) {
        runner.withPropertyValues("app.jwt.lifetime=" + lifetime)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsMissingAndMismatchedKeys() {
        runner.withPropertyValues("app.jwt.private-key=file:/nonexistent/private.pem")
                .run(context -> assertThat(context).hasFailed());
        Path other = TestKeys.createFiles(TestKeys.createPair(2048));
        runner.withPropertyValues(
                        "app.jwt.public-key=" + other.resolve("public.pem").toUri())
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsMalformedAndUndersizedKeys() throws Exception {
        Path invalid = java.nio.file.Files.createTempFile("invalid-key-", ".pem");
        java.nio.file.Files.writeString(invalid, "not a key");
        invalid.toFile().deleteOnExit();
        runner.withPropertyValues("app.jwt.public-key=" + invalid.toUri())
                .run(context -> assertThat(context).hasFailed());
        Path weak = TestKeys.createFiles(TestKeys.createPair(1024));
        runner.withPropertyValues(
                        "app.jwt.public-key=" + weak.resolve("public.pem").toUri(),
                        "app.jwt.private-key=" + weak.resolve("private.pem").toUri())
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void nonDemoModeDoesNotCreateDemoAccountsOrControllers() {
        new ApplicationContextRunner()
                .withUserConfiguration(DemoUserDetailsService.class, DemoController.class)
                .run(context -> assertThat(context)
                        .doesNotHaveBean(UserDetailsService.class)
                        .doesNotHaveBean(DemoController.class));
    }

    @Test
    void missingUserLookupFailsInsteadOfCreatingADefaultUser() {
        new org.springframework.boot.test.context.runner.WebApplicationContextRunner()
                .withUserConfiguration(SecurityFoundationApplication.class)
                .withPropertyValues(
                        "app.jwt.issuer=test",
                        "app.jwt.audience=test",
                        "app.jwt.lifetime=15m",
                        "app.jwt.private-key="
                                + TestKeys.DIRECTORY.resolve("private.pem").toUri(),
                        "app.jwt.public-key="
                                + TestKeys.DIRECTORY.resolve("public.pem").toUri(),
                        "app.cors.allowed-origins=http://localhost:5173")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining(
                                    "No qualifying bean of type 'org.springframework.security.core.userdetails.UserDetailsService'");
                });
    }

    @Test
    void rejectsMissingDemoCredentials() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class))
                .withPropertyValues("spring.profiles.active=demo")
                .withUserConfiguration(DemoUserDetailsService.class)
                .withBean(
                        org.springframework.security.crypto.password.PasswordEncoder.class,
                        () -> org.springframework.security.crypto.factory.PasswordEncoderFactories
                                .createDelegatingPasswordEncoder())
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void refusesRemoteKeyResources() {
        runner.withPropertyValues("app.jwt.private-key=https://example.invalid/private.pem")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("local file:");
                });
    }

    @Test
    void userRepresentationDoesNotSerializeOrPrintPassword() throws Exception {
        ApplicationUser user = new ApplicationUser("id", "user@example.com", "secret-hash", true, java.util.List.of());
        assertThat(new ObjectMapper().writeValueAsString(user)).doesNotContain("secret-hash", "password");
        assertThat(user.toString()).doesNotContain("secret-hash");
    }

    @Test
    void rejectsWildcardCorsOrigins() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class))
                .withUserConfiguration(CorsConfig.class)
                .withPropertyValues("app.cors.allowed-origins=*")
                .run(context -> assertThat(context).hasFailed());
    }
}
