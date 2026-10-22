# Optional relational database example

This separate Maven application adds JPA/PostgreSQL user lookup to the existing security
foundation sources. It is not a required database schema or a dependency of the default demo.

Follow [the database guide](../../docs/database-integration.md) for mapping and setup.

From the repository root:

```bash
# Running Docker required; isolated PostgreSQL, not your application database.
./mvnw -f examples/relational-database/pom.xml clean verify
```

For manual startup, set the guide's datasource/JWT variables and run with `database`, without
`demo`. Provide your own migrated tables and seeded accounts. Schema generation is disabled.
No production admin/profile controller is added by this example; test-only routes verify the flow.
