# Connect users from a real relational database

You keep your schema. The only integration contract is a `UserDetailsService` that loads a user
and returns `ApplicationUser`. The login service and JWT code do not depend on JPA entities.

## 1. Inspect the working example

[examples/relational-database](../examples/relational-database) is a separate Maven application.
It compiles the foundation's existing sources and adds only the database lookup. No security source
files are duplicated. The core project's POM and default startup remain database-free.

The example's added production dependencies are:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

Keep the same Boot parent/BOM as the target application. The example also has test-only
PostgreSQL Testcontainers dependencies; those are not required for your production application.

## 2. Map your tables

Read [UserEntity](../examples/relational-database/src/main/java/com/example/securityfoundation/database/UserEntity.java)
and the optional [sample SQL](../examples/relational-database/schema.sql). Their mapping is:

| Example field | Your equivalent |
| --- | --- |
| app_user.id | Stable immutable primary key; converted to JWT subject string |
| app_user.email | Unique login identity |
| app_user.password_hash | Existing adaptive password hash with its encoder prefix |
| app_user.enabled | Whether the account may log in |
| app_user_role.user_id / role | Assigned role names such as USER and ADMIN |

Change `@Table`, `@Column`, `@Id`, identifier type, and join annotations to match your actual tables.
Do not rename your real tables to satisfy the example. An existing role entity/join table can use
`@ManyToMany` or another suitable mapping instead of this example's `@ElementCollection`.
Load only active roles if your schema has role-status fields. Do not copy ERP-specific company,
branch, menu, or full-access rules into this generic foundation.

This example uses exact, case-sensitive email lookup and a matching unique constraint. If your
application uses case-insensitive identities, align normalization, lookup, registration, and a
case-insensitive uniqueness constraint. Do not change only the Java query and leave duplicate
identities possible in the database. Do not trim, lowercase, or otherwise normalize passwords.

For UUID identifiers, keep the UUID column and use `getId().toString()`; no numeric ID is required.
Never use an editable email as the stable JWT subject.

## 3. Fetch the user and roles together

[UserRepository](../examples/relational-database/src/main/java/com/example/securityfoundation/database/UserRepository.java)
uses `findByEmail` with an entity graph for roles. It is a derived, parameterized JPA query. Do not
assemble SQL with a submitted email string.

The service's read-only transaction covers the entity lookup and authority conversion. The entity
graph avoids deferred role loading, and the transaction prevents relying on open-session-in-view.
The example sets `spring.jpa.open-in-view=false`.

## 4. Return the authentication representation

[DatabaseUserDetailsService](../examples/relational-database/src/main/java/com/example/securityfoundation/database/DatabaseUserDetailsService.java)
implements Spring's `UserDetailsService`. It:

1. Looks up the exact email.
2. Throws `UsernameNotFoundException` if absent.
3. Converts database roles such as ADMIN into `ROLE_ADMIN` authorities.
4. Maps ID, email, encoded password, enabled status, and authorities into `ApplicationUser`.

It does **not** check the submitted password, sign a JWT, return an entity to a controller, or
read a client-supplied role. `DaoAuthenticationProvider` and `PasswordEncoder` own password checking.
Do not add duplicate manual hash comparisons.

The example uses `@Profile("database & !demo")`. Run with `database` and without `demo`.
When copying into your project, remove the demo lookup or retain an equally explicit exclusion.
Exactly one production `UserDetailsService` should be active; do not hide an accidental conflict
with `@Primary`. Keep entities/repositories under your application's scan package or explicitly
configure `@EntityScan` and `@EnableJpaRepositories` when they live elsewhere.

## 5. Handle password hashes intentionally

The default encoder stores `{bcrypt}` followed by the bcrypt hash. It is a one-way hash, not
encryption. Existing raw bcrypt values such as `$2a$...` need the correct prefix or a dedicated
bcrypt migration configuration. Never encode an existing hash again. New/changed passwords are
encoded once with your injected `PasswordEncoder` before persistence.

The sample does not create accounts or ship database passwords/users. Seed your own development
records through a migration or trusted local provisioning code, using:

```java
String passwordHash = passwordEncoder.encode(rawPassword);
```

Bind that result as a SQL parameter; do not paste raw passwords into shared SQL scripts. After
checking a record is actually bcrypt, adding `{bcrypt}` to an existing hash is a representation
migration, not a new password hash. Test the migration on a copy before modifying existing data.

Account disablement affects new logins. Existing access tokens still work until expiry in v1.

## 6. Configure and run against a development database

Use an existing **development** PostgreSQL database. Apply the sample SQL only if you are creating
its optional sample tables; otherwise supply your mappings and migrations. Runtime uses
`ddl-auto: validate` and SQL initialization is disabled, so it does not create or rewrite tables.

From the repository root, after generating local JWT keys:

```bash
export JWT_PRIVATE_KEY="file:$PWD/.keys/private.pem"
export JWT_PUBLIC_KEY="file:$PWD/.keys/public.pem"
export DATABASE_URL='jdbc:postgresql://localhost:5432/your_development_database'
export DATABASE_USERNAME='your_database_user'
# Enter your development database password without putting it in shell history.
printf 'Database password: '; read -r -s DATABASE_PASSWORD; printf '\n'
export DATABASE_PASSWORD

./mvnw -f examples/relational-database/pom.xml spring-boot:run \
  -Dspring-boot.run.profiles=database
```

Use your JDBC driver/dialect and column types for another relational database. The mapping and
UserDetailsService contract remain the same, but this project's actual SQL verification is PostgreSQL.
Do not enable `demo` together with `database`. The demo routes are intentionally absent in database
mode: add your own protected controllers or use the test example below.

Authenticate using your seeded email/password at `POST /api/auth/login`. The result remains
`accessToken`, `tokenType`, and `expiresInSeconds`, regardless of the storage implementation.

## 7. Verify the integration

With Docker running, this command starts a disposable PostgreSQL instance, applies the actual
sample schema, validates the JPA mapping, seeds test-only hashed users, and exercises login:

```bash
./mvnw -f examples/relational-database/pom.xml clean verify
```

The tests prove:

- Database lookup returns the stable ID, encoded password, and materialized roles.
- A correct password produces a usable JWT.
- USER cannot access an admin method, while ADMIN can.
- Disabled, unknown, and wrong-password accounts cannot log in.

Test-only protected controllers exist solely inside the test source. Containers are discarded;
your datasource environment variables are overridden by Testcontainers during these tests.
There is no H2 substitute and no silent skip when Docker is unavailable. A missing Docker daemon
is a verification failure, not evidence that PostgreSQL integration passed.

## Troubleshooting

- **No encoder ID mapping:** inspect the stored password format and follow the prefix guidance.
- **Lazy initialization:** fetch roles and map them within the read-only service transaction.
- **Two user-detail beans:** disable demo and activate only the production lookup.
- **Schema validation failure:** align entity columns/types with your migrations; do not switch a
  real database to `create` merely to make startup pass.
- **Token has no roles:** authorities need the `ROLE_` prefix at the user boundary; JWT roles omit it.
- **Account disabled but old JWT works:** expected v1 behavior; immediate revocation is not implemented.
