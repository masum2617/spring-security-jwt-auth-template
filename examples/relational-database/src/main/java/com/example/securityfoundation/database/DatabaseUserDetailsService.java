package com.example.securityfoundation.database;

import com.example.securityfoundation.security.user.ApplicationUser;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("database & !demo")
public class DatabaseUserDetailsService implements UserDetailsService {
    private final UserRepository users;

    public DatabaseUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        UserEntity user = users.findByEmail(email).orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // Materialize roles while the transaction is open. Never put entities into JWTs.
        return new ApplicationUser(
                user.getId().toString(),
                user.getEmail(),
                user.getPasswordHash(),
                user.isEnabled(),
                user.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList());
    }
}
