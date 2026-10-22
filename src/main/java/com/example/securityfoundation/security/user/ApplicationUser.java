package com.example.securityfoundation.security.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** User lookup result. Never return this credential-bearing object from a controller. */
public final class ApplicationUser implements UserDetails {
    private final String userId;
    private final String email;
    private final String encodedPassword;
    private final boolean enabled;
    private final List<? extends GrantedAuthority> authorities;

    public ApplicationUser(
            String userId,
            String email,
            String encodedPassword,
            boolean enabled,
            Collection<? extends GrantedAuthority> authorities) {
        this.userId = Objects.requireNonNull(userId);
        this.email = Objects.requireNonNull(email);
        this.encodedPassword = Objects.requireNonNull(encodedPassword);
        this.enabled = enabled;
        this.authorities = List.copyOf(authorities);
    }

    public String getUserId() {
        return userId;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    @JsonIgnore
    public String getPassword() {
        return encodedPassword;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }
}
