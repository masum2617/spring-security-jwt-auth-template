package com.example.securityfoundation.security.jwt;

import com.example.securityfoundation.security.config.JwtProperties;
import com.example.securityfoundation.security.user.ApplicationUser;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtTokenService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public String generateAccessToken(ApplicationUser user) {
        Instant now = clock.instant();
        List<String> roles = user.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring(5))
                .distinct()
                .sorted()
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getUserId())
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .issuedAt(now)
                .expiresAt(now.plus(properties.lifetime()))
                .id(UUID.randomUUID().toString())
                .claim("roles", roles)
                .build();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
