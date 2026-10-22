# Spring Boot JWT Security Foundation — Final Implementation Plan

This replaces the earlier Java 21 / Boot 4 proposal. The implementation lives in this directory;
the ERP reference application remains unchanged. See README.md for runnable instructions.

## Agreed scope and compatibility

- Editable source template, not a database schema mandate or installed security library.
- Java 17 release target; verify on Java 17 and 21 with Boot 3.5.16 and Boot-managed Security.
- Other Boot versions are unverified. Compatibility and upstream support lifecycle are separate.
- Database-free demo behind an explicit profile; real database mapping documented and tested separately.
- No refresh tokens, session tables, immediate revocation, signup/reset, OAuth login, or ERP rules.

## Architecture and contracts

- auth: controller, DTOs, service interface and implementation for password login.
- security: configuration, external RSA keys, Nimbus encoder/decoder, claims and role mapping,
  UserDetails representation, and filter-layer errors.
- demo: opt-in in-memory users and authenticated/admin examples.
- error: Problem Details and validation failure handling.
- Replace UserDetailsService to return ApplicationUser with a stable ID, email, encoded password,
  status, and server-derived authorities. Database entities never enter JWTs or responses.
- POST /api/auth/login returns accessToken, tokenType, and expiresInSeconds.
- GET /api/demo/profile requires authentication; GET /api/demo/admin requires ADMIN.
- Spring handles Bearer filtering. No custom request authentication filter or per-request DB lookup.

## Security defaults

- RS256 with demo-only ephemeral in-memory RSA keys; non-demo requires matching externally
  supplied local RSA PEM files >=2048 bits. No committed secret or production fallback.
- Default lifetime 15 minutes, configuration 1–60 minutes, explicit skew 30 seconds.
- Required sub/iss/aud/iat/exp/jti/roles with signed claim-type validation, issuer/audience/time
  checks, and fixed accepted signing algorithm. nbf checked when present.
- Role names in JWTs omit ROLE_; server authorities include it exactly once.
- Stateless header-only JSON API, no auth cookies/Basic/form login/session persistence.
- CSRF disabled only under those assumptions; explicit-origin noncredentialed CORS.
- Delegating bcrypt encoder, generic failures, no-store token responses, standard Bearer challenges.
- Never log credentials or serialize password-bearing user objects.
- Document HTTPS, deployment throttling, XSS/token handling, ownership checks, rotation, and revocation limits.

## Documentation and code standards

- README: setup, commands, compatibility, package flow, settings, errors, tests, limits.
- Detailed JPA guide: dependencies, schema adaptation, repository, transactions, passwords, roles,
  account status, stable ID, disabling demo, and actual PostgreSQL verification.
- Working optional database example compiles shared core sources without duplicating security code.
- Copy guide explains component scanning, security-chain merging, response contracts, and compatibility.
- Flow/security decisions, CONTRIBUTING.md, and SECURITY.md supply maintainable extension guidance.
- Four-space Java format, meaningful names, constructor injection, small methods, useful comments,
  explicit imports, separated logical steps. Automated Spotless formatting check.

## Verification and delivery

1. Scaffold Maven Wrapper, Java17 build, and configuration validation.
2. Implement RSA keys, JWT encoder/decoder/validators, and authority conversion.
3. Implement filter-chain rules, errors, CORS, and password authentication.
4. Add profile-isolated users and example endpoints.
5. Verify issued-token access plus negative JWT, login, role, CORS, startup, and leakage cases.
6. Verify optional JPA lookup and sample SQL against disposable PostgreSQL.
7. Write documentation and Java17/21 CI, run clean verify on both JDKs, and review changes.

Acceptance: run without tables, obtain a JWT, verify access and role restrictions, then follow
the guide to connect real relational users. Report exact verification evidence in VERIFICATION.md.
The selected repository name is spring-security-jwt-auth-template and the code uses the MIT
license, copyright 2026 Masum Hasan. Private security reporting uses GitHub after maintainer
activation. Publishing, pushing, and releasing remain outside this task. Stateless v1 retains
client-side token disposal rather than adding a revocation endpoint.
