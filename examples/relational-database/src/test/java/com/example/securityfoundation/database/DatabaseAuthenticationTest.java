package com.example.securityfoundation.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.securityfoundation.TestKeys;
import com.example.securityfoundation.security.user.ApplicationUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("database")
@Testcontainers
class DatabaseAuthenticationTest {
    @Container
    static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwords;

    @Autowired
    private DatabaseUserDetailsService users;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.sql.init.schema-locations", () -> "classpath:schema.sql");
        registry.add(
                "app.jwt.private-key",
                () -> TestKeys.DIRECTORY.resolve("private.pem").toUri().toString());
        registry.add(
                "app.jwt.public-key",
                () -> TestKeys.DIRECTORY.resolve("public.pem").toUri().toString());
    }

    @BeforeEach
    void seedUsers() {
        jdbc.update("delete from app_user_role");
        jdbc.update("delete from app_user");
        for (long id = 1; id <= 3; id++) {
            jdbc.update(
                    "insert into app_user (id, email, password_hash, enabled) values (?, ?, ?, ?)",
                    id,
                    "user" + id + "@example.com",
                    passwords.encode("database-password"),
                    id != 3);
            jdbc.update("insert into app_user_role (user_id, role) values (?, ?)", id, id == 2 ? "ADMIN" : "USER");
        }
    }

    @Test
    void databaseLookupReturnsStableIdentityAndMaterializedRoles() {
        ApplicationUser user = (ApplicationUser) users.loadUserByUsername("user1@example.com");
        assertThat(user.getUserId()).isEqualTo("1");
        assertThat(passwords.matches("database-password", user.getPassword())).isTrue();
        assertThat(user.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_USER");
    }

    @Test
    void databaseLoginTokenAuthenticatesAndEnforcesRoles() throws Exception {
        String user = token("user1@example.com", "database-password", 200);
        mvc.perform(get("/api/database-test/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + user))
                .andExpect(status().isOk());
        mvc.perform(get("/api/database-test/admin").header(HttpHeaders.AUTHORIZATION, "Bearer " + user))
                .andExpect(status().isForbidden());
        String admin = token("user2@example.com", "database-password", 200);
        mvc.perform(get("/api/database-test/admin").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk());
    }

    @Test
    void disabledUnknownAndWrongPasswordAccountsCannotLogin() throws Exception {
        token("user3@example.com", "database-password", 401);
        token("unknown@example.com", "database-password", 401);
        token("user1@example.com", "wrong-password", 401);
    }

    private String token(String email, String password, int expected) throws Exception {
        var response = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().is(expected))
                .andReturn()
                .getResponse();
        return expected == 200
                ? mapper.readTree(response.getContentAsString())
                        .get("accessToken")
                        .asText()
                : "";
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Routes {
        @Bean
        DatabaseTestController databaseTestController() {
            return new DatabaseTestController();
        }
    }

    @RestController
    static class DatabaseTestController {
        @GetMapping("/api/database-test/profile")
        Map<String, String> profile(@AuthenticationPrincipal Jwt jwt) {
            return Map.of("userId", jwt.getSubject());
        }

        @GetMapping("/api/database-test/admin")
        @PreAuthorize("hasRole('ADMIN')")
        Map<String, String> admin() {
            return Map.of("message", "Admin access granted");
        }
    }
}
