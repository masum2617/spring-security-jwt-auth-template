# Postman API testing

These are importable Postman Collection v2.1 JSON files for the **demo profile**.
The environment template contains no passwords or tokens.

## Import and run

1. Start the application with the `demo` profile. In IntelliJ, set Active profiles to `demo`,
   set `DEMO_USER_PASSWORD` and `DEMO_ADMIN_PASSWORD`, and use the repository root as the
   working directory. Demo keys are generated in memory; no key setup is required.
   After restarting the app, run the login requests again to obtain tokens signed by its new key.
2. In Postman, choose **Import** and select both files from this directory:
   - `Security-Foundation.postman_collection.json`
   - `Local.postman_environment.json`
3. Select **Security Foundation - Local** as the active environment.
4. Set the environment's local password values:
   - `userPassword`: the same value as IntelliJ's `DEMO_USER_PASSWORD`.
   - `adminPassword`: the same value as IntelliJ's `DEMO_ADMIN_PASSWORD`.
   Postman does not automatically inherit IntelliJ/Terminal environment variables.
5. Confirm `baseUrl` is `http://localhost:8080` or change it to the port you configured.
6. Select the collection and choose **Run collection**. Keep all four folders in their listed
   order and use one iteration. You can also run the USER or ADMIN folder by itself, including
   its login request.

The login requests capture separate USER and ADMIN JWTs in `pm.variables`, which last for the
current collection run only. They are not saved to shared collection/environment values.
There is no need to copy tokens manually when using the runner. Passwords are serialized with
JSON.stringify in pre-request scripts so quotes/backslashes are handled correctly.

For individual **Send** requests outside the runner, login first and manually add the returned
token as a local environment value named `userAccessToken` or `adminAccessToken`. Clear those
values afterward. Automatic run-local capture intentionally does not persist across separate Sends.

## Requests and expected results

| Folder | Requests | Expected behavior |
| --- | --- | --- |
| USER flow | Login, profile, admin access | 200, 200, 403 |
| ADMIN flow | Login, profile, admin access | 200, 200, 200 |
| Authentication failures | Missing/invalid token, wrong password, unknown user, invalid input, supplied roles | Expected 401/400 failures |
| CORS preflight | Configured origin, untrusted origin | 200, 403 |

There are **14 requests and 35 assertions**. Negative status codes are intentional passing tests.
Assertions check the response contract, identity/roles, generic Problem Details failures, Bearer
challenges, no-store token responses, absence of login cookies, and explicit-origin CORS behavior.
HTTP CORS tests do not prove a real browser's enforcement.

`user@example.com` and `admin@example.com`, their `demo-user`/`demo-admin` IDs, and the expected
roles match this template's demo accounts. This collection is not a generic real-database test:
database mode has application-specific protected routes. Adapt identities/routes/assertions if you
copy it into your own application. The separate JPA integration tests cover database login.

## Environment values

| Variable | Default | Meaning |
| --- | --- | --- |
| baseUrl | http://localhost:8080 | Application origin, no trailing slash |
| userEmail | user@example.com | Demo USER login |
| userPassword | Blank | Set locally to the configured demo USER password |
| adminEmail | admin@example.com | Demo ADMIN login |
| adminPassword | Blank | Set locally to the configured demo ADMIN password |
| allowedOrigin | http://localhost:5173 | Must match the app's CORS_ALLOWED_ORIGINS |
| forbiddenOrigin | https://untrusted.example | Must not appear in the app's configured allowed origins |

Keep passwords local and do not share/export populated credentials or tokens. Marking a variable
secret masks it in the UI; it does not make an exported plaintext value safe to commit. If you
need a local export for the CLI, save it as `Local.private.postman_environment.json` (ignored by Git),
keeping the committed environment template blank.

## Optional command-line run

With the Postman CLI installed and the app running, export your populated environment to the
ignored private file and run from the repository root:

```bash
postman collection run postman/Security-Foundation.postman_collection.json \
  -e postman/Local.private.postman_environment.json \
  --no-report-events \
  --timeout-request 5000
```

No workspace binding or collection upload is required for this local-file workflow. Do not use
verbose logging or export detailed reports containing credentials/tokens into source control.
The default `postman-cli-reports/` directory is ignored. Delete populated local exports when no
longer needed.

## Troubleshooting

- **Missing-password pre-request error:** select the environment and fill both password values.
- **Connection refused:** start the demo app and verify its port/baseUrl.
- **USER/ADMIN login 401:** match the password to the respective IntelliJ environment variable.
- **Profile/admin returns 401 after individual Send:** tokens are run-local; use the collection
  runner or deliberately provide a local token variable for manual testing.
- **CORS assertion fails:** align allowedOrigin with the backend setting; keep forbiddenOrigin untrusted.
- **Demo routes return 404:** enable the demo profile; these routes are intentionally absent otherwise.

## Verification

Executed with Postman CLI 1.63.0 against a real local demo JAR on Java 17 using temporary credentials:
14 requests, 35 assertions, zero failures, exit code 0. Run details were not uploaded to a workspace.
This establishes collection scripts and actual HTTP behavior, not a Postman desktop UI import test.
