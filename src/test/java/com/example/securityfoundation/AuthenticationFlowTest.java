package com.example.securityfoundation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.RSAKey;
import jakarta.servlet.DispatcherType;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@org.springframework.context.annotation.Import(testsupport.DisabledUserFixture.class)
class AuthenticationFlowTest {
    private static final String PASSWORD = "test-only-password";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private RSAKey signingKey;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // Missing paths prove the demo never tries to read configured key files.
        registry.add("app.jwt.private-key", () -> "file:/nonexistent/demo/private.pem");
        registry.add("app.jwt.public-key", () -> "file:/nonexistent/demo/public.pem");
        registry.add("app.demo.user-password", () -> PASSWORD);
        registry.add("app.demo.admin-password", () -> PASSWORD);
    }

    @Test
    void loginIssuesUsableJwtAndDoesNotCreateSessionOrCookies() throws Exception {
        MvcResult login = login("user@example.com", PASSWORD);
        JsonNode body = mapper.readTree(login.getResponse().getContentAsString());
        assertThat(body.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(body.get("expiresInSeconds").asLong()).isEqualTo(900);
        assertThat(login.getRequest().getSession(false)).isNull();
        assertThat(login.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(login.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        assertThat(body.toString()).doesNotContain(PASSWORD, "encodedPassword", "{bcrypt}");

        mvc.perform(get("/api/demo/profile")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + body.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("demo-user"))
                .andExpect(jsonPath("$.authorities[0]").value("ROLE_USER"));
    }

    @Test
    void adminAuthorizationUsesServerRoles() throws Exception {
        mvc.perform(get("/api/demo/admin").header(HttpHeaders.AUTHORIZATION, "Bearer " + token("user@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(header().string(
                                HttpHeaders.WWW_AUTHENTICATE,
                                org.hamcrest.Matchers.containsString("insufficient_scope")))
                .andExpect(jsonPath("$.status").value(403));
        mvc.perform(get("/api/demo/admin").header(HttpHeaders.AUTHORIZATION, "Bearer " + token("admin@example.com")))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown@example.com", "disabled@example.com", "user@example.com"})
    void failedLoginsHaveGenericErrorsWithoutCredentials(String email) throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "email",
                                email,
                                "password",
                                email.equals("user@example.com") ? "incorrect-password" : PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Incorrect email or password"))
                .andExpect(header().string(
                                HttpHeaders.CONTENT_TYPE,
                                org.hamcrest.Matchers.startsWith("application/problem+json")));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{",
                "{\"email\":\"invalid\",\"password\":\"p\"}",
                "{\"email\":\"user@example.com\",\"password\":\"test-only-password\",\"roles\":[\"ADMIN\"]}"
            })
    void invalidPayloadAndClientSuppliedRolesAreRejected(String json) throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rejectsPasswordsAboveBcryptByteLimit() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                Map.of("email", "user@example.com", "password", "é".repeat(37)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingTokenHasBearerChallenge() throws Exception {
        mvc.perform(get("/api/demo/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void rejectsMalformedTamperedUnsignedAndWrongAlgorithmTokens() throws Exception {
        String valid = sign(claims());
        String[] segments = valid.split("\\.");
        String changedPayload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(mapper.writeValueAsBytes(Map.of("sub", "attacker", "roles", List.of("ADMIN"))));
        String tampered = segments[0] + "." + changedPayload + "." + segments[2];
        String unsigned = Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8))
                + "." + segments[1] + ".";
        for (String token : List.of(
                "not-a-jwt",
                tampered,
                unsigned,
                TestKeys.sign(claims(), TestKeys.createPair(2048), JWSAlgorithm.RS256),
                TestKeys.sign(claims(), signingKey.toKeyPair(), JWSAlgorithm.RS512))) {
            reject(token);
        }
    }

    @ParameterizedTest(name = "rejects invalid {0}")
    @MethodSource("invalidClaims")
    void rejectsInvalidClaims(String name, Map<String, Object> claims) throws Exception {
        reject(sign(claims));
    }

    static Stream<Arguments> invalidClaims() {
        Stream.Builder<Arguments> cases = Stream.builder();
        for (String required : List.of("sub", "iss", "aud", "iat", "exp", "jti", "roles")) {
            Map<String, Object> missing = claims();
            missing.remove(required);
            cases.add(Arguments.of("missing " + required, missing));
        }
        Map<String, Object> changes = Map.ofEntries(
                Map.entry("iss", "other-issuer"),
                Map.entry("aud", List.of("other-api")),
                Map.entry("sub", 42),
                Map.entry("jti", 42),
                Map.entry("iat", "yesterday"),
                Map.entry("exp", "tomorrow"),
                Map.entry("roles", "ADMIN"));
        changes.forEach((key, value) -> {
            Map<String, Object> modified = claims();
            modified.put(key, value);
            cases.add(Arguments.of("invalid " + key, modified));
        });
        long now = Instant.now().getEpochSecond();
        for (Map<String, Object> changeset : List.of(
                Map.<String, Object>of("iat", now - 300, "exp", now - 60),
                Map.<String, Object>of("iat", now + 60),
                Map.<String, Object>of("nbf", now + 60),
                Map.<String, Object>of("exp", now + 7200),
                Map.<String, Object>of("roles", List.of(42)),
                Map.<String, Object>of("roles", List.of("ROLE_ADMIN")),
                Map.<String, Object>of("sub", " "),
                Map.<String, Object>of("iat", now + 18446744073709552L, "exp", now + 18446744073709552L + 900),
                Map.<String, Object>of("nbf", now + 18446744073709552L))) {
            Map<String, Object> modified = claims();
            modified.putAll(changeset);
            cases.add(Arguments.of(changeset.keySet().toString(), modified));
        }
        return cases.build();
    }

    @Test
    void corsAllowsConfiguredOriginAndRejectsOtherOrigins() throws Exception {
        mvc.perform(options("/api/demo/profile")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        mvc.perform(options("/api/demo/profile")
                        .header(HttpHeaders.ORIGIN, "https://untrusted.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void directErrorEndpointRequiresAuthenticationButErrorDispatchPreservesStatus() throws Exception {
        mvc.perform(get("/error")).andExpect(status().isUnauthorized());
        mvc.perform(get("/error")
                        .with(request -> {
                            request.setDispatcherType(DispatcherType.ERROR);
                            return request;
                        })
                        .requestAttr("jakarta.servlet.error.status_code", 409))
                .andExpect(status().isConflict());
    }

    @Test
    void ignoresTokensInCookiesAndQueryParameters() throws Exception {
        String token = token("user@example.com");
        mvc.perform(get("/api/demo/profile").param("access_token", token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/demo/profile").cookie(new jakarta.servlet.http.Cookie("access_token", token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsStringAudienceAndRejectsHs256() throws Exception {
        Map<String, Object> stringAudience = claims();
        stringAudience.put("aud", "security-foundation-api");
        mvc.perform(get("/api/demo/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + sign(stringAudience)))
                .andExpect(status().isOk());

        com.nimbusds.jose.JWSObject symmetric = new com.nimbusds.jose.JWSObject(
                new com.nimbusds.jose.JWSHeader(JWSAlgorithm.HS256),
                new com.nimbusds.jose.Payload(mapper.writeValueAsString(claims())));
        symmetric.sign(new com.nimbusds.jose.crypto.MACSigner(new byte[32]));
        reject(symmetric.serialize());
    }

    @Test
    void loginDoesNotLogCredentialsOrTokens(org.springframework.boot.test.system.CapturedOutput output)
            throws Exception {
        String token = token("user@example.com");
        assertThat(output.getAll()).doesNotContain(PASSWORD, token, "{bcrypt}");
    }

    private String sign(Map<String, Object> claims) throws Exception {
        return TestKeys.sign(claims, signingKey.toKeyPair(), JWSAlgorithm.RS256);
    }

    private void reject(String token) throws Exception {
        mvc.perform(get("/api/demo/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(
                        header().string(HttpHeaders.WWW_AUTHENTICATE, org.hamcrest.Matchers.containsString("Bearer")));
    }

    private String token(String email) throws Exception {
        return mapper.readTree(login(email, PASSWORD).getResponse().getContentAsString())
                .get("accessToken")
                .asText();
    }

    private MvcResult login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
    }

    private static Map<String, Object> claims() {
        long now = Instant.now().getEpochSecond();
        return new HashMap<>(Map.of(
                "sub",
                "demo-user",
                "iss",
                "security-foundation",
                "aud",
                List.of("security-foundation-api"),
                "iat",
                now,
                "exp",
                now + 900,
                "jti",
                "test-id",
                "roles",
                List.of("USER")));
    }
}
