# Security and maintenance

The initial tested compatibility target is Java 17/21 and Spring Boot 3.5.x, pinned to 3.5.16.
Other framework lines are unverified. Test compatibility does not extend upstream security support:
track Spring's lifecycle and update supported patches/dependencies deliberately.

This foundation is not a complete identity platform. It does not provide refresh/session storage,
immediate revocation, MFA, password reset, signup, or login throttling. Read the README and
security-decisions document before deploying a copy. Existing JWTs keep their identity/roles
until expiry. Resource/tenant authorization belongs to the consuming application.

## Reporting

This project uses **GitHub private vulnerability reporting** for sensitive reports after the
maintainer enables it on the published repository.

For reporters, open the repository's **Security → Advisories → Report a vulnerability**, then
submit the affected version, reproduction, impact, and a proposed mitigation privately. Omit
real passwords, private keys, and production tokens. Do not open a public issue with exploit details.

If the private-report button is unavailable, reporting has not yet been enabled or is unavailable
for that repository. This template does not configure or activate the GitHub feature itself.
Use an existing private maintainer contact if one is provided; do not assume an unpublished email.

For the maintainer, after creating the public GitHub repository:

1. Open **Settings → Security → Advanced Security** (the security-settings label may vary).
2. Enable **Private vulnerability reporting**.
3. Verify that **Report a vulnerability** appears under Security → Advisories.

Follow [GitHub's configuration instructions](https://docs.github.com/en/code-security/how-tos/report-and-fix-vulnerabilities/configure-vulnerability-reporting/configure-for-a-repository).
This repository has not been connected to a GitHub remote during this task, so activation remains
a GitHub setup step rather than a claim of a working reporting channel.

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
