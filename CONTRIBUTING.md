# Contributing

Keep this project focused on an editable JWT login foundation. Discuss scope changes before adding
refresh/session storage, framework-specific frontend code, multi-tenancy, or infrastructure.

Use Java 17-compatible APIs and Boot-managed dependency versions. Before proposing a change:

```bash
./mvnw spotless:apply
./mvnw clean verify
./mvnw -f examples/relational-database/pom.xml clean verify
```

The database check requires Docker. Repeat on Java 17 and Java 21. Keep meaningful tests of the
actual authentication chain, not only mocked principals. Update README commands and database
examples when their contracts change. Do not submit signing keys, actual passwords, or tokens.

Use four-space Java indentation, meaningful names, constructor injection, explicit imports, and
blank lines between logical steps. The formatter wraps fluent chains/long declarations and
checks the Java sources in the optional example too. Comments explain why, security assumptions,
and where to customize; avoid comments for obvious assignments and unnecessary abstractions.

Report what was actually verified. Unit/MockMvc success is not browser, database, or deployment
proof. The project is licensed under MIT; preserve the license notice when redistributing copies.
Use the private reporting process in SECURITY.md for sensitive vulnerabilities.
