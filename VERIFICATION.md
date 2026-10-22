# Verification results

Verified locally on 2026-10-22 (Asia/Dhaka). These are observed results, not a security certification.

## Build matrix

Spring Boot 3.5.16; Maven Wrapper 3.9.10; Java compiler release 17.

| Runtime | Command | Result |
| --- | --- | --- |
| Oracle JDK 17.0.15 | `./mvnw -B -ntp clean verify` | PASS: 55 tests, 0 failures/errors/skips; format check passes |
| OpenJDK 21.0.9 | `./mvnw -B -ntp clean verify` | PASS: 55 tests, 0 failures/errors/skips; format check passes |
| Oracle JDK 17.0.15 | `./mvnw -B -ntp -f examples/relational-database/pom.xml clean verify` | PASS: 3 PostgreSQL tests, 0 failures/errors/skips |
| OpenJDK 21.0.9 | `./mvnw -B -ntp -f examples/relational-database/pom.xml clean verify` | PASS: 3 PostgreSQL tests, 0 failures/errors/skips |

Each runtime executed 58 tests (116 test executions across the matrix). PostgreSQL tests used
Docker/Testcontainers with `postgres:17-alpine`, applied the sample schema.sql, and ran Hibernate
schema validation. They exercised real user/role queries and password checking, login token issuance,
protected requests, USER/ADMIN restrictions, and disabled/unknown/wrong-password accounts.

## Runtime smoke checks

The packaged executable JAR and the README's Maven `spring-boot:run` demo command were each
started locally on Java 21 and exercised through actual localhost HTTP requests:

- Successful USER and ADMIN login.
- USER profile access, USER admin rejection, and ADMIN admin access.
- Missing/invalid Bearer token (401), invalid credentials (401), malformed login data (400).
- Configured CORS preflight (200), untrusted-origin preflight (403).
- Login uses no authentication cookie and returns Cache-Control: no-store.

Temporary demo credentials were generated for the checks and not recorded. Both application
processes were stopped after verification. The packaged application's class-file major version is
61 (Java 17 bytecode). The development key script created ignored local keys and refused a second
invocation without altering the existing private key.

## Review and regression fixes

Independent read-only review identified and verified fixes for Nimbus timestamp conversion and
NumericDate overflow. Regression tests exercise original signed JSON claim types, crafted time
wraparound values, remote key rejection, and actual issued-token access. The reviewer reported no
remaining important findings within the reviewed scope.

Markdown relative links resolve and Java sources contain no wildcard imports. Spotless covers
core and optional-example Java sources. Expected negative startup tests emit warning messages;
those warnings are assertions of rejection, not application startup failures in the smoke tests.

## Boundaries

No browser automation, external deployment, public CI run, publication, push, or release was performed.
The CI configuration is present; its commands were verified locally on both JDKs. CORS HTTP checks
are not a live browser test. No existing ERP database or production service was contacted.

Boot releases other than 3.5.16 and JDKs other than the two listed above are unverified.
Private vulnerability reporting, project branding, and license remain publication prerequisites.

## Postman collection verification

The importable collection was run with Postman CLI 1.63.0 against an isolated local Java 17
demo application using temporary credentials: **14 requests, 35 assertions, zero failures, exit
code 0**. It checks real login, identity/role access, generic authentication/validation errors,
Bearer challenges, cache/cookie policy, and CORS preflight. JWTs remain in run-local variables;
the committed environment contains blank passwords. No collection was uploaded to a workspace.
Postman desktop import/click behavior was not automated.

## Keyless demo update

The demo profile now generates ephemeral 2048-bit RSA keys in memory. Both Java 17 and Java 21
core builds passed with 55 tests, including startup without file locations, actual JWT access
with nonexistent file paths, production failure without keys, and rejection of tokens from a
previous demo application context. The independent reviewer found no actionable issues in the
profile separation or tests.

The packaged demo was also run with both key-file settings pointing to nonexistent files. All
14 Postman requests and 35 assertions passed (exit code 0). The optional PostgreSQL example
continues to use persistent file keys outside demo.
