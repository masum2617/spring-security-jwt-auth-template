# Security decisions and customization

## Tokens and signing

The template issues first-party API access tokens; it is not an OAuth authorization server or
OIDC login implementation. Spring Resource Server support is used as the JWT verifier.

RS256 keeps verification keys public while signing keys remain private. The decoder accepts only
RS256 and the public key from the application's active key pair. It does not follow token-supplied
key URLs or contact an external issuer.

With the explicit `demo` profile, a fresh 2048-bit RSA pair is generated once in memory per
application context. No private/public file is required or read. Restarting invalidates earlier
tokens, and separate demo instances cannot validate each other's tokens. This is local-demo
behavior, not a production fallback.

Outside demo, key loading permits only local `file:` locations, with external provisioning.
The file-only check is our storage policy, not a universal JWT requirement; another trusted
key provider can replace it. Missing/bad keys fail startup. The startup check requires matching
RSA moduli and at least 2048 bits. Parser errors are replaced with a generic configuration error
rather than attaching potentially sensitive parser output.

Claims issued:

| Claim | Meaning |
| --- | --- |
| sub | Stable user ID, a nonblank string |
| iss | Expected issuer from configuration |
| aud | Intended API audience |
| iat / exp | Issuance and expiry NumericDate values |
| jti | Unique token ID; it is not a revocation database |
| roles | Up to 100 unprefixed uppercase role names, 1–64 characters each |

The decoder requires these claims and validates their types, issuer, audience, expiry, issuance
time, and a maximum one-hour lifetime. A string audience is accepted per JWT conventions;
issued tokens use an array. `nbf` is optional, but validated if present. Future issuance or
not-before values get an explicit 30-second skew allowance, as does expiry. NumericDate values
in this template must be integer seconds, not strings. Default token lifetime is 15 minutes;
configuration permits one minute through one hour. Do not put personal profiles or secrets in JWTs:
signing provides integrity, not encryption.

Application authorities must be `ROLE_<NAME>` to be issued as roles. Non-role authorities are
not included. JWT roles omit `ROLE_`; conversion adds it exactly once. Add granular permissions
only with a documented claim contract and corresponding authorization tests.

## Key rotation

Keep private keys out of Git, container images, logs, and public file servers. Use secret-mounted
files with restricted access. Back up signing material according to your deployment policy.
The development script is not a production key-management service.

Outside demo there is one configured persistent key pair. Replacing it and restarting changes the accepted signature key and
invalidates older tokens. Graceful overlap, multiple verification keys, `kid` routing, and automated
rotation require a separate design; this template does not claim to provide them.

## Passwords

The delegating encoder writes `{bcrypt}<hash>` and can identify supported existing formats.
New passwords are encoded once when an account is created/changed; login compares the raw input
against the stored hash. Never encode the stored hash again. The demo encodes configured passwords
only when it initializes its temporary users.

Existing unprefixed bcrypt values need an intentional migration: after verifying their format,
prefix those values with `{bcrypt}`, or configure a dedicated bcrypt encoder while migrating.
Do not fall back to NoOp or plaintext. Tune the bcrypt work factor for your deployment; the
factory's default is a starting point, not a performance guarantee. Bcrypt accepts at most 72
UTF-8 bytes; the API rejects longer input instead of allowing silent truncation. If your existing
password policy requires longer values, select another adaptive encoder and update validation.

## HTTP and browser assumptions

The API accepts JSON login credentials and header-supplied Bearer tokens. Form login, Basic auth,
authentication cookies, request caching, and persistent HTTP authentication sessions are disabled.
Therefore CSRF is disabled specifically for this contract. Stateless configuration alone is not
a reason to disable CSRF: revisit it if browsers automatically attach authentication credentials.

CORS integrates with Spring Security before authentication, allows explicit configured origins,
and does not permit credentials. Change allowed methods/headers alongside application routes.
Do not replace allowed origins with a wildcard. CORS is enforced by browsers, not by all clients.

Use HTTPS for real credentials/tokens. Keep browser access tokens in memory for the sample flow.
TLS termination, login throttling, resource ownership, tenant isolation, and XSS protection in your
frontend remain application/deployment responsibilities. Limit login attempts at your gateway or
reverse proxy and avoid logging login bodies or Authorization headers.

## Reference material

- [Spring Security 6.5 JWT support](https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/resource-server/jwt.html)
- [Password storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)
- [CSRF protection](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [CORS integration](https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html)
- [JWT Best Current Practices, RFC 8725](https://www.rfc-editor.org/info/rfc8725/)

These sources guide implementation; this template is not a security certification for applications
built from it. Run the tests and review your own authentication and authorization requirements.
