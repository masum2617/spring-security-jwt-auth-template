package com.example.securityfoundation.demo;

import com.example.securityfoundation.security.user.ApplicationUser;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Profile("demo")
@EnableConfigurationProperties(DemoProperties.class)
public class DemoUserDetailsService implements UserDetailsService {
    private final Map<String, ApplicationUser> users;

    public DemoUserDetailsService(DemoProperties properties, PasswordEncoder encoder) {
        users = Map.of(
                "user@example.com",
                        new ApplicationUser(
                                "demo-user",
                                "user@example.com",
                                encoder.encode(properties.userPassword()),
                                true,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))),
                "admin@example.com",
                        new ApplicationUser(
                                "demo-admin",
                                "admin@example.com",
                                encoder.encode(properties.adminPassword()),
                                true,
                                List.of(
                                        new SimpleGrantedAuthority("ROLE_USER"),
                                        new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        // Deliberately exact lookup: applications define their own email uniqueness/case policy.
        ApplicationUser user = users.get(email);
        if (user == null) {
            throw new UsernameNotFoundException("User not found");
        }
        return user;
    }
}
