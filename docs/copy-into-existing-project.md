# Copy the foundation into an existing Spring Boot application

Target verified versions: Java 17/21 with Boot 3.5.16. Do not assume every Boot 3 release or
Boot 4 uses identical APIs. Keep your application's Boot-managed dependency versions coherent.

## Copy and adapt

1. Add `spring-boot-starter-web`, `spring-boot-starter-validation`, and
   `spring-boot-starter-oauth2-resource-server` if missing. Keep the target application's Boot
   parent/BOM. The resource-server starter supplies Spring Security/JOSE; no JJWT dependency is needed.
2. Copy `auth`, `security`, and `error` from the foundation source package. Keep your existing
   application entrypoint. Rename packages/imports to sit under its component scan, or explicitly
   import the needed configuration. Do not copy `demo` into a production app.
3. Merge `app.jwt` and `app.cors` properties; provision stable local signing-key files outside demo.
   In-memory demo keys are only for local learning and invalidate tokens on restart. Keep property
   class registration through `@EnableConfigurationProperties`.
4. Supply a `UserDetailsService` returning `ApplicationUser`, following the database guide.
5. Merge the security rules as described below; keep one intended authentication manager,
   password encoder, JWT decoder/encoder, and authority conversion policy.
6. Adapt controller DTO/error integration to your existing public contract. The template rejects
   unknown login properties globally; either retain that application-wide Jackson setting or
   enforce the equivalent restriction specifically at your login boundary.
7. Add a protected endpoint and a role-restricted method, then port the behavior tests.

The default login contract is email/password JSON and a response containing accessToken,
tokenType, and expiresInSeconds. Avoid returning ApplicationUser or persistence entities.
A lookup returning Spring's generic User instead of ApplicationUser does not satisfy the stable-ID
contract and intentionally fails login.

## Merge existing security configuration

Do not blindly paste a second catch-all `SecurityFilterChain`. If your application already has
one, integrate the JWT resource-server configuration, handlers, CORS, and route rules into it.
With deliberately separate chains, give each an explicit securityMatcher and order, and test
which chain handles login and protected routes. A higher-priority catch-all can hide later chains.

Review existing public endpoints and authorization annotations. The foundation permits only
POST /api/auth/login and ERROR dispatches. Your health/docs/webhook routes need intentional rules;
copying the ERP's public endpoint list would be incorrect.

Enable method security once. Map role names to `ROLE_` authorities consistently. Replace existing
Bearer parsing filters if they duplicate Spring Resource Server behavior; do not run two competing
JWT authentication mechanisms.

## Preserve your application's HTTP contract

The template assumes no authentication cookies, form login, or Basic authentication. If your
existing application relies on them, keep its CSRF defenses and design the integration explicitly
instead of copying `.csrf(disable)` wholesale. CORS credentials=false is likewise intentional
for this header-only example.

Security filters need their own error handlers even when your application has controller advice.
Preserve Bearer WWW-Authenticate headers/statuses when adapting Problem Details to an existing
response format. Keep secrets out of binding errors, user serialization, and diagnostic logs.

## Verify after copying

Run successful/failed login with your database, send an actual issued token to a protected route,
and verify USER versus ADMIN behavior. Test expiry, issuer/audience mismatch, disabled accounts,
CORS preflight, and missing-token behavior. Check that conflicting beans, default users, or old
filters are not active. Confirm that existing protected business endpoints remain protected.

Role changes do not revoke existing tokens. Add ownership/tenant checks to your business services;
this generic identity foundation cannot supply your domain authorization rules.
