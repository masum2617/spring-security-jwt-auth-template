package com.example.securityfoundation.security.jwt;

import com.nimbusds.jose.JWSObject;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** Checks this template's claim contract in addition to Spring's signature/time checks. */
public final class JwtClaimsValidator implements OAuth2TokenValidator<Jwt> {
    public static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    private final String audience;
    private final Clock clock;

    public JwtClaimsValidator(String audience, Clock clock) {
        this.audience = audience;
        this.clock = clock;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Map<String, Object> claims = token.getClaims();
        Instant issuedAt = token.getIssuedAt();
        Instant expiresAt = token.getExpiresAt();
        Instant latestAllowedIssuance = clock.instant().plus(CLOCK_SKEW);

        boolean valid = validWireTypes(token)
                && nonblank(claims.get("sub"))
                && nonblank(claims.get("jti"))
                && issuedAt != null
                && expiresAt != null
                && !issuedAt.isAfter(latestAllowedIssuance)
                && expiresAt.isAfter(issuedAt)
                && Duration.between(issuedAt, expiresAt).compareTo(Duration.ofHours(1)) <= 0
                && validAudience(claims.get("aud"))
                && validRoles(claims.get("roles"));

        if (!valid) {
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Required JWT claims are invalid", null));
        }
        return OAuth2TokenValidatorResult.success();
    }

    private boolean validWireTypes(Jwt token) {
        try {
            // Nimbus/Spring coerce some values (for example numeric subjects) to strings.
            // Check the signed JSON's original types after Spring verifies its signature.
            Map<String, Object> claims =
                    JWSObject.parse(token.getTokenValue()).getPayload().toJSONObject();
            if (claims == null) {
                return false;
            }
            Object audience = claims.get("aud");
            boolean audienceValid = nonblank(audience)
                    || (audience instanceof Collection<?> values
                            && !values.isEmpty()
                            && values.stream().allMatch(JwtClaimsValidator::nonblank));
            return nonblank(claims.get("sub"))
                    && nonblank(claims.get("iss"))
                    && nonblank(claims.get("jti"))
                    && audienceValid
                    && numericDate(claims.get("iat"))
                    && numericDate(claims.get("exp"))
                    && (!claims.containsKey("nbf") || numericDate(claims.get("nbf")))
                    && validRoles(claims.get("roles"));
        } catch (ParseException exception) {
            return false;
        }
    }

    private static boolean numericDate(Object value) {
        if (!(value instanceof Long || value instanceof Integer)) {
            return false;
        }
        long seconds = ((Number) value).longValue();
        // Nimbus converts seconds to Date milliseconds; reject values that would overflow.
        return seconds >= Long.MIN_VALUE / 1000 && seconds <= Long.MAX_VALUE / 1000;
    }

    private boolean validAudience(Object value) {
        return value instanceof Collection<?> audiences
                && audiences.stream().allMatch(JwtClaimsValidator::nonblank)
                && audiences.contains(audience);
    }

    private static boolean validRoles(Object value) {
        return value instanceof Collection<?> roles
                && roles.size() <= 100
                && roles.stream()
                        .allMatch(role -> role instanceof String name
                                && name.matches("[A-Z][A-Z0-9_]{0,63}")
                                && !name.startsWith("ROLE_"));
    }

    private static boolean nonblank(Object value) {
        return value instanceof String text && !text.isBlank();
    }
}
