# Security and maintenance

The initial tested compatibility target is Java 17/21 and Spring Boot 3.5.x, pinned to 3.5.16.
Other framework lines are unverified. Test compatibility does not extend upstream security support:
track Spring's lifecycle and update supported patches/dependencies deliberately.

This foundation is not a complete identity platform. It does not provide refresh/session storage,
immediate revocation, MFA, password reset, signup, or login throttling. Read the README and
security-decisions document before deploying a copy. Existing JWTs keep their identity/roles
until expiry. Resource/tenant authorization belongs to the consuming application.

## Reporting

A private vulnerability-reporting contact has not yet been configured. **Publication is blocked
until the maintainer adds a working private contact or enables private GitHub vulnerability reporting.**
Do not publish exploit details or real credentials in a public issue while arranging disclosure.
No contact address or reporting service is implied by this template.

## Applying fixes to copies

The maintainer should review security reports, dependency advisories, supported Spring versions,
and regression tests when issuing fixes. Document behavior changes and relevant affected files.
Consumers who copied source must compare releases and apply/test fixes in their own applications;
there is no automatic update channel for copied code.

Keep private signing keys outside source control and images. Rotate compromised keys and follow
your incident response process. Graceful overlapping key rotation is not implemented in v1.

The explicit demo profile generates temporary RSA keys in memory and invalidates old tokens on
restart. Never use that profile for production or shared/multi-instance authentication. Non-demo
profiles still require stable externally configured files and fail startup if keys are missing.
