# Spring Boot JWT Security Foundation

An editable REST authentication foundation: run the demo, understand the Spring Security flow,
then replace its user lookup with your own database. This is source you own and customize,
not a library that requires your tables to follow a particular schema.

## What you get

- Email/password authentication through Spring's authentication provider.
- RS256 access JWT generation and Spring's built-in Bearer-token validation.
- Explicit security rules, configurable CORS, bcrypt password encoding, and Problem Details errors.
- An opt-in, database-free demo with USER and ADMIN access examples.
- A complete [relational database guide](docs/database-integration.md) and runnable JPA example.
- Real-token HTTP tests, startup checks, and PostgreSQL integration tests.

There are **no refresh tokens, stored sessions, registration, password reset, or immediate token
revocation** in v1. A token remains usable until expiry, including after a password/role change
or account disablement. Discarding a client token does not invalidate another copy.

## Compatibility

| Component | Project target |
| --- | --- |
| Java | 17 minimum; verification on 17 and 21 |
| Spring Boot | 3.5.16 (3.5.x baseline) |
| Spring Security | Version managed by Boot; do not override independently |
| Build | Maven 3.9.10 through the included wrapper |

Older Boot 3.x versions and Boot 4 are not verified. Compiling for Java 17 does not establish
compatibility with every framework release. Framework/vendor support lifecycle and this project's
compatibility tests are separate: check [Spring's support policy](https://spring.io/projects/spring-boot#support)
and update the pinned patch when maintaining a deployment.

## Quick start

Prerequisites: JDK 17 or 21, internet access for the first Maven download, and curl/jq for
the request examples. **The local demo needs no RSA files, OpenSSL, or JWT signing secret.**
Windows users can use `mvnw.cmd` for Maven.

Run these commands from the project root:

```bash
# Explicit local-demo credentials. Choose your own values.
export DEMO_USER_PASSWORD='local-user-password-change-me'
export DEMO_ADMIN_PASSWORD='local-admin-password-change-me'

./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

The application listens on `http://localhost:8080`. With `demo` active, it generates a fresh
2048-bit RSA key pair in memory once at startup. No key files are read, even if file locations
are configured. Login and JWT verification still use RS256 normally.

**Restarting the demo invalidates its old tokens** because the next startup creates a new key
pair. Log in again or rerun the Postman collection. These temporary keys are for local learning,
not production or multiple application instances.

Without `demo`, provide your own `UserDetailsService` and persistent RSA files as described below.
Missing/invalid key configuration fails startup; there is no automatic production fallback.
Demo accounts and endpoints do not activate outside the profile.

### Log in and use the access token

In another terminal, set the same demo password variables. Capture the response without printing
credentials or tokens into shared logs:

```bash
set -euo pipefail

ACCESS_TOKEN=$(curl --fail-with-body -sS http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  --data "$(jq -n --arg email 'user@example.com' --arg password "$DEMO_USER_PASSWORD" \
    '{email: $email, password: $password}')" | jq -er '.accessToken')

curl -i http://localhost:8080/api/demo/profile \
  -H "Authorization: Bearer $ACCESS_TOKEN"

# USER receives 403 here.
curl -i http://localhost:8080/api/demo/admin \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

Successful login returns:

```json
{
  "accessToken": "<signed access JWT>",
  "tokenType": "Bearer",
  "expiresInSeconds": 900
}
```

To verify ADMIN access:

```bash
ADMIN_TOKEN=$(curl --fail-with-body -sS http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  --data "$(jq -n --arg email 'admin@example.com' --arg password "$DEMO_ADMIN_PASSWORD" \
    '{email: $email, password: $password}')" | jq -er '.accessToken')

curl -i http://localhost:8080/api/demo/admin \
  -H "Authorization: Bearer $ADMIN_TOKEN"

unset ACCESS_TOKEN ADMIN_TOKEN
```

Discarding the client token is the only local logout action in this version. There is no logout
endpoint promising server-side invalidation. Tokens are accepted only from the Authorization
header, not query strings, cookies, or form parameters.

## How the code fits together

```text
Login JSON
  -> AuthController
  -> AuthenticationServiceImpl
  -> AuthenticationManager / DaoAuthenticationProvider
  -> UserDetailsService + PasswordEncoder
  -> ApplicationUser
  -> JwtTokenService / JwtEncoder
  -> access JWT

Authorization: Bearer <JWT>
  -> Spring's BearerTokenAuthenticationFilter
  -> JwtDecoder + claim validators
  -> role converter
  -> URL / method access checks
  -> controller
```

| Package | Responsibility |
| --- | --- |
| `auth` | Login request/response, controller, service interface and orchestration |
| `security.config` | Filter chain, CORS, key loading, encoder/decoder, validated settings |
| `security.jwt` | Token issuance and the application's claim contract |
| `security.user` | User lookup result, independent of storage |
| `security.error` / `error` | Filter failures and controller validation errors |
| `demo` | Opt-in in-memory users and example endpoints |

The login principal is `ApplicationUser`; a protected request's principal is Spring's `Jwt`.
See the [flow walkthrough](docs/authentication-flow.md) for the class-by-class explanation.

## Persistent keys outside demo

For your own database-backed application, or any non-demo profile, generate/provision stable
keys before startup. The optional local script requires Bash/OpenSSL and refuses to replace an
existing directory:

```bash
./scripts/generate-dev-keys.sh
```

It creates `.keys/private.pem` (PKCS#8) and `.keys/public.pem` (X.509), ignored by Git. If valid
files already exist, skip generation. A manual alternative using a new directory is:

```bash
(umask 077 && mkdir .keys && \
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out .keys/private.pem && \
  openssl pkey -in .keys/private.pem -pubout -out .keys/public.pem)
```

Use externally provisioned, restricted-access files in deployments. Keeping file loading local
is this template's RSA storage policy, not a requirement of JWT itself. A trusted key-management
provider can replace the loader without changing the authentication flow.

## Configuration

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `JWT_PRIVATE_KEY` | `file:.keys/private.pem` | Non-demo: local PKCS#8 RSA private key |
| `JWT_PUBLIC_KEY` | `file:.keys/public.pem` | Non-demo: matching local X.509 RSA public key |
| `JWT_ISSUER` | `security-foundation` | Required issuer claim |
| `JWT_AUDIENCE` | `security-foundation-api` | Required API audience |
| `JWT_LIFETIME` | `15m` | Access-token lifetime; 1–60 minutes |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Comma-separated explicit origins, without trailing slash/path |
| `DEMO_USER_PASSWORD` | None | Set explicitly for demo; obey DemoProperties password limits |
| `DEMO_ADMIN_PASSWORD` | None | Set explicitly for demo; obey DemoProperties password limits |

Use `file:/absolute/path/private.pem` and `file:/absolute/path/public.pem` in deployments.
Outside demo, key resources must be local files, at least 2048 bits, and match. No classpath/HTTP key locations
or fallback secret. A 30-second skew allowance applies to token time checks. See
[security decisions](docs/security-decisions.md) for claims, password formats, and rotation.

## Connect an existing application

- [Real relational database integration](docs/database-integration.md): entities, repository,
  password hashes, roles, disabled accounts, transactions, and verification.
- [Copy into an existing project](docs/copy-into-existing-project.md): exact dependencies, code,
  configuration, component scanning, and merging existing security rules.

## Errors and troubleshooting

Authentication errors use `application/problem+json`, for example:

```json
{
  "type": "about:blank",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Incorrect email or password",
  "instance": "/api/auth/login"
}
```

| Symptom | Check |
| --- | --- |
| 400 at login | Valid JSON/email, nonblank password, maximum 72 UTF-8 bytes; unknown fields such as roles are rejected |
| 401 at login | Email lookup, encoded password format, account status; error intentionally does not identify which failed |
| 401 at a protected endpoint | Bearer header, expiry, key pair, issuer, audience, and required claims |
| 403 at admin endpoint | ADMIN role, stored as `ROLE_ADMIN` in authorities and `ADMIN` in JWT roles |
| Browser CORS failure | Exact frontend origin, allowed methods/headers, and preflight; curl is not a browser CORS test |
| Startup says UserDetailsService missing | Enable demo or provide your database-backed implementation |
| Non-demo startup says JWT keys invalid | Generate matching RSA PEM files; verify file location, format, and permissions |
| Encoder says no id mapping | Use `{bcrypt}<hash>` with the delegating encoder; follow the existing-hash guide |
| 404 for demo routes | They exist only with the demo profile |

Avoid enabling request-body logging, authentication DEBUG/TRACE logging, or proxy logging of
Authorization headers. Such logs can expose secrets regardless of this application's defaults.

## Test with Postman

Import [the API collection](postman/Security-Foundation.postman_collection.json) and
[the Local environment](postman/Local.postman_environment.json). Set the local passwords to match
`DEMO_USER_PASSWORD` / `DEMO_ADMIN_PASSWORD`, select the environment, and run the collection.

It includes 14 requests and 35 assertions for login, USER/ADMIN authorization, authentication
failures, role-injection rejection, and CORS. Tokens are captured only for the collection run.
See [Postman setup and troubleshooting](postman/README.md) for IntelliJ and CLI instructions.

## Build and verify

```bash
./mvnw clean verify
./mvnw spotless:apply   # Deliberate source formatting; verify only checks.

# Separate example: requires a running Docker daemon; creates disposable PostgreSQL.
./mvnw -f examples/relational-database/pom.xml clean verify
```

CI runs both verification commands on Java 17 and 21. See [verification results](VERIFICATION.md)
for the observed local build, database, and HTTP checks. Database tests do not use your real
application database. The default build needs no database. To build an executable JAR:

```bash
./mvnw clean package
java -jar target/security-foundation-0.1.0-SNAPSHOT.jar --spring.profiles.active=demo
```

The demo JAR requires the same demo environment variables and generates its keys in memory.

## Deployment boundaries and maintenance

Use HTTPS, keep private keys outside the repository/image, restrict filesystem access, replace
demo users/endpoints, and configure throttling at your reverse proxy/API gateway for
`POST /api/auth/login` before accepting public traffic. This project does not implement a
shared rate limiter. CORS does not prevent direct API calls or password attacks.

For a browser example, keep access tokens in memory; do not make localStorage persistence the
default. XSS can still act as the user or steal accessible tokens. Reassess CSRF if you introduce
authentication cookies or other browser-managed credentials. No frontend is bundled.

For authorization beyond roles, implement resource ownership and tenant checks in your own
services. A valid JWT does not establish access to every user's data.

See [CONTRIBUTING](CONTRIBUTING.md) and [SECURITY](SECURITY.md). Users who copy the code must
review and apply later fixes themselves. This repository does not automatically update copies.

Publication is pending: the maintainer must choose branding, a license, and a private security
reporting contact. No open-source license is asserted until that choice is recorded.
