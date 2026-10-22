# Authentication flow

## Login: framework checks credentials, application issues a token

`AuthController` validates `LoginRequest` and delegates to `AuthenticationService`. The
implementation asks `AuthenticationManager` to authenticate the submitted email/password.
It does not query tables or compare hashes itself.

`DaoAuthenticationProvider` calls `UserDetailsService.loadUserByUsername(email)`. With the
demo profile this is `DemoUserDetailsService`. A real application replaces it with
`DatabaseUserDetailsService`, as shown in the database guide.

The lookup returns `ApplicationUser`: stable ID, email, encoded password, enabled flag, and
server-derived authorities. It is not a response DTO. `PasswordEncoder.matches` checks the
password; Spring also checks the supported account flags. Unknown accounts, disabled accounts,
and wrong passwords return the same public 401 response. Existing expired/locked-account
policies can be added to your UserDetails implementation; this template does not store them.

Only after successful authentication does `JwtTokenService` create claims and call `JwtEncoder`.
`sub` is the stable ID, not an editable email address. The response has no password, entity,
refresh token, or authentication cookie. `Cache-Control: no-store` applies to login responses.

## Protected request: Spring handles Bearer authentication

The client explicitly sends `Authorization: Bearer <token>`. Spring's built-in filter invokes
the configured `JwtDecoder`. Nimbus verifies the RS256 signature with the public key from the active pair (generated in memory for demo, loaded from files otherwise),
then Spring and `JwtClaimsValidator` check the claim contract and time policy.

The custom validator also inspects the signed JSON's original types after signature validation:
Nimbus/Spring normalize some claims, so a numeric subject must not silently become a string ID.
This uses Nimbus's payload parser; no custom cryptography or request filter is implemented.

The JWT's `roles` array contains unprefixed names such as `USER` and `ADMIN`. The configured
converter produces `ROLE_USER` and `ROLE_ADMIN` authorities. Method security enforces
`@PreAuthorize("hasRole('ADMIN')")` on the admin example.

The request principal is now a Spring `Jwt`, not the password-login `ApplicationUser`:

```java
@GetMapping("/api/orders/current-user")
public Map<String, String> currentUser(@AuthenticationPrincipal Jwt jwt) {
    return Map.of("userId", jwt.getSubject());
}
```

Do not cast it to a database entity or assume a fresh database user lookup has occurred.

## Errors occur in two places

`ApiExceptionHandler` handles login authentication failures and malformed/invalid request bodies.
It avoids serializing binding errors because rejected values can contain passwords.

Filter-level failures do not normally reach controller advice. The authentication entry point
and denied handler preserve Spring's Bearer response status/challenge and use
`ProblemResponseWriter` for generic JSON details. Spring retains its appropriate security headers.
CORS rejection and unsupported HTTP routing use framework behavior; not every framework error
is rewritten into this authentication error format.

ERROR dispatches are permitted so an application error keeps its intended status. A normal direct
request to `/error` still requires authentication.

## What changes when you use a database

Only the password-login user lookup and its mapping change. JWT signing, decoding, role conversion,
and login orchestration stay the same. Protected requests still do not consult the database.
Role/account/password changes affect new logins; already-issued JWTs survive until expiry.

Your ERP had an additional database-session check for each token request. That check enabled
immediate revocation. This smaller foundation deliberately does not carry that session lifecycle.
