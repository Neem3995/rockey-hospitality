# Local setup — Windows PowerShell

This guide runs Rockey locally with Java 17, the Maven Wrapper, MySQL and React/Vite. AWS, Docker, Flyway and Liquibase are not needed. No passwords, signing keys or demo account credentials are supplied by the repository.

## 1. Prerequisites

- A JDK **17**, not just a JRE. Any suitable Java 17 distribution is acceptable.
- MySQL **8.0.16 or newer in the 8.0 family**, with its service running and the `mysql.exe` client available. The schema uses enforced CHECK constraints. This reproduction used MySQL **8.0.46**.
- Git; Node **22.12+ within Node 22**, or Node 24+, matching the frontend package engines. The verified local frontend runtime is Node 22.18.0 / npm 10.9.3.
- A database setup account allowed to create the development database/tables, plus a least-privilege local application account. The backend does not require root. Do not disable MySQL authentication.

No global Maven installation is required. The Wrapper downloads Maven **3.9.16** and project dependencies on first use.

## 2. Clone / checkout

For a new checkout:

```powershell
git clone --branch feat/react-frontend --single-branch https://github.com/Neem3995/rockey-hospitality.git
Set-Location .\rockey-hospitality
git status
git branch --all
```

Use `feat/react-frontend` for the complete backend and current frontend shown in this guide. `main` retains the earlier backend baseline; no merge to main is required for this checkout. Private configuration, generated files and temporary reports are not part of the published project. In an existing checkout, switch branches only when doing so will not overwrite local work.

Select your actual JDK installation; replace the example path:

```powershell
$env:JAVA_HOME = 'C:\path\to\your\jdk-17'
& "$env:JAVA_HOME\bin\java.exe" -version
& "$env:JAVA_HOME\bin\javac.exe" -version
.\mvnw.cmd --version
```

Both Java and Maven must report Java 17. No machine-specific JDK path is required by production configuration.

## 3. Private configuration and MySQL database

Spring Boot does **not** automatically read the repository `.env`. Set variables in the PowerShell process that will launch the backend. Prompt for credentials rather than placing actual values in command history:

```powershell
$localDbCredential = Get-Credential -Message 'Enter your local MySQL application account'
$env:ROCKEY_DB_USERNAME = $localDbCredential.UserName
$env:ROCKEY_DB_PASSWORD = $localDbCredential.GetNetworkCredential().Password
$env:ROCKEY_DB_URL = 'jdbc:mysql://127.0.0.1:3306/rockey_hospitality?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'

$jwtBytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($jwtBytes)
$rng.Dispose()
$env:ROCKEY_JWT_SECRET_BASE64 = [Convert]::ToBase64String($jwtBytes)

$env:ROCKEY_CORS_ALLOWED_ORIGINS = 'http://localhost:5173'
$env:ROCKEY_REFRESH_COOKIE_SECURE = 'false'
$env:ROCKEY_REFRESH_COOKIE_SAME_SITE = 'Lax'
Remove-Item Env:ROCKEY_HARDENING_LIVE -ErrorAction SilentlyContinue
```

The four required variables are `ROCKEY_DB_URL`, `ROCKEY_DB_USERNAME`, `ROCKEY_DB_PASSWORD`, and `ROCKEY_JWT_SECRET_BASE64`. CORS/cookie settings above are local settings, not a recommendation to disable Secure cookies under HTTPS. `ROCKEY_ALERT_SCAN_DELAY_MS` is optional and defaults to 300000 (five minutes).

If credentials are already in an approved, Git-ignored root `.env`, this optional loader reads only the two named database values without displaying them:

```powershell
git check-ignore -v .env
foreach ($line in [IO.File]::ReadAllLines((Join-Path (Get-Location) '.env'))) {
    if ($line -match '^\s*(ROCKEY_DB_USERNAME|ROCKEY_DB_PASSWORD)\s*=(.*)$') {
        [Environment]::SetEnvironmentVariable(
            $matches[1], $matches[2].Trim().Trim('"').Trim("'"), 'Process'
        )
    }
}
```

Do not print `.env`, dump environment variables, or copy backend secrets into frontend `VITE_*` variables. A new checkout has no `.env`; the prompt workflow above is sufficient. Variables are inherited by child processes, not by another independently opened terminal.

Use the MySQL client as the separate database setup account, entering its password privately at each `-p` prompt. If `mysql` is not on PATH, set `$mysqlClient` to your actual client executable; the installation path below is an example, not a project requirement:

```powershell
$mysqlClient = (Get-Command mysql -ErrorAction SilentlyContinue).Source
if (!$mysqlClient) { $mysqlClient = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' }
Get-Service MySQL* -ErrorAction SilentlyContinue
$schemaLogin = Read-Host 'MySQL schema setup username (not the runtime account)'
& $mysqlClient --host=127.0.0.1 --port=3306 --user=$schemaLogin -p --execute="CREATE DATABASE IF NOT EXISTS rockey_hospitality CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
& $mysqlClient --host=127.0.0.1 --port=3306 --user=$schemaLogin -p --database=rockey_hospitality --execute="source database/schema.sql"
& $mysqlClient --host=127.0.0.1 --port=3306 --user=$schemaLogin -p --database=rockey_hospitality --execute="SHOW TABLES;"
```

Run from the repository root so `source database/schema.sql` resolves correctly. Check `$LASTEXITCODE` after each command; do not continue after an SQL error. Never drop/reset a populated database to make startup succeed.

### Least-privilege application user

Have the database administrator create `rockey_app` restricted to `127.0.0.1`, using MySQL Workbench's local Users and Privileges interface and a privately chosen password. Do not put a `CREATE USER ... IDENTIFIED BY` password into this repository or shell history. The application needs only `SELECT, INSERT, UPDATE, DELETE` on `rockey_hospitality.*`; schema creation and manual bootstrap use the separate setup account. For an account already created privately, that administrator can grant:

```sql
GRANT SELECT, INSERT, UPDATE, DELETE ON rockey_hospitality.*
TO 'rockey_app'@'127.0.0.1';
```

No global privileges, GRANT OPTION, or runtime DROP/CREATE/ALTER privilege is needed. Configure `ROCKEY_DB_USERNAME` and `ROCKEY_DB_PASSWORD` with this application account, not the setup account. If the permitted host differs, align it with the actual local JDBC host rather than granting access from `%`.

### Who creates the tables?

`spring.jpa.hibernate.ddl-auto=validate` means **Hibernate validates existing tables; it does not create or migrate them**. `database/schema.sql` creates the nine tables using `CREATE TABLE IF NOT EXISTS`. It creates neither the database itself nor accounts. Spring SQL automatic initialization is not configured; there is no runtime `data.sql` or migration framework requirement. Running the table script against existing tables is not an upgrade/migration mechanism: it does not repair differing columns or constraints.

Expected tables: `users`, `employees`, `departments`, `rooms`, `tasks`, `events`, `event_registrations`, `inventory_items`, `alerts`.

Optional fictional Department seed, not needed for startup:

```powershell
("SET time_zone='+00:00';`n" + (Get-Content -Raw .\database\seed-departments.sql)) |
    & $mysqlClient --host=127.0.0.1 --port=3306 --user=$schemaLogin -p --database=rockey_hospitality --batch
```

It creates six Department names but **no login accounts**. Rerunning it preserves existing active/inactive states while updating descriptions.

## 4. Build and run the backend

```powershell
.\mvnw.cmd package
if ($LASTEXITCODE -ne 0) { throw 'Backend build failed; do not start an old JAR.' }
.\mvnw.cmd spring-boot:run
```

Keep this terminal open. Alternative after packaging: `& "$env:JAVA_HOME\bin\java.exe" -jar .\target\rockey-hospitality-0.0.1-SNAPSHOT.jar`. Do not run both at once. Default port is 8080. Offline tests do not need a live database; opt-in destructive fixture tests are restricted to the separate disposable `rockey_hospitality_hardening` database. Do not enable them against normal development data.

## 5. Verify the backend and persist data

In another terminal:

```powershell
curl.exe -s -o NUL -w '%{http_code}' http://localhost:8080/api/auth/me
```

**401** is expected without a bearer token; this is a protected API, not a public homepage. A 404 at `/` is not a startup failure. With the frontend below, register a USER, log out, log in and verify the account panel (`GET /api/auth/me`). Authentication responses/cookies must not be pasted into logs or saved Postman environments.

Public registration always creates **USER**. A fresh database has no ADMIN. Follow the one-time manual bootstrap below to create its first valid linked ADMIN. Afterward, use the existing Employee form/API to provision STAFF/ADMIN; `createLogin=false` creates an Employee with no login. Department/Employee CRUD must use ADMIN, not a changed USER selector in React.

To verify persistence manually: create a Department and Employee through an authorized ADMIN session; note their non-secret IDs, stop the backend, start it again in the same configured terminal, then log in and read the same IDs. Do not rerun seeds or reset tables for a normal restart.

### First ADMIN — LOCAL DEVELOPMENT / CAPSTONE DEMO ONLY

1. Start the initialized backend and frontend. Register a normal USER through `/register` (the real `POST /api/auth/register`), using your own private password. Record only its email. Do not add a role to the registration request.
2. Stop the backend with Ctrl+C so no application writes race the bootstrap. Keep MySQL running.
3. Make a private working copy of the template:

```powershell
Copy-Item .\database\bootstrap-admin.sql.example .\database\bootstrap-admin.sql
git check-ignore -v database/bootstrap-admin.sql
```

4. Edit that ignored copy locally: replace `REGISTERED_USER_EMAIL_HERE` with the exact registered email. Do not insert a password or hash. If the email contains an apostrophe, double it for the SQL string literal. Leave the guards and transaction intact.
5. From the repository root, execute the copy as the database setup account. Use batch input, **not SOURCE or `--force`**, so an SQL error prevents COMMIT:

```powershell
Get-Content -Raw .\database\bootstrap-admin.sql |
    & $mysqlClient --host=127.0.0.1 --port=3306 --user=$schemaLogin -p --database=rockey_hospitality --batch
if ($LASTEXITCODE -ne 0) { throw 'Bootstrap failed. Inspect privately; do not log in as ADMIN yet.' }
```

Success says `BOOTSTRAP_APPLIED` and displays matching User/Employee Department IDs. `NOT_APPLIED` means no bootstrap changes: check for a missing/inactive/non-USER account, an already-existing ADMIN, an existing Employee identity/email, an inactive Management Department, or the wrong selected database. Do not remove guards to get a success message. Review unexpected pre-existing records manually.

The transaction reuses or creates active `Management`, creates one uniquely linked ACTIVE Employee using the registered profile, promotes the same User to ADMIN, synchronizes both Department IDs and clears its old refresh session. It never rewrites the BCrypt password. FK/unique/NOT NULL constraints remain enabled. This exception exists only for the **first** local ADMIN, not a general User-linking feature.

6. Restart the backend in its configured terminal. Log in normally with the same email/password. An old USER access token is no longer valid after promotion; a new login is required. Verify `/api/auth/me`, then create/read a Department or Employee through the ADMIN UI. Never add a public ADMIN registration endpoint or a startup bootstrap service.

The filled SQL copy is ignored; only the placeholder `.sql.example` belongs in source control. Do not run this procedure on production or use it to create a second ADMIN.

## 6. Run the frontend

From a separate terminal in the development checkout containing the frontend:

```powershell
Set-Location .\frontend
npm ci
npm run dev
```

Open **http://localhost:5173** (not the numeric host or port 4173 unless explicitly allowed by backend CORS). Vite's strict port prevents silent origin changes. Backend API defaults to `http://localhost:8080/api`; optional `frontend/.env.local` can set the public `VITE_API_BASE_URL` only. Access tokens remain memory-only and refresh tokens remain HttpOnly cookies. `America/New_York` is the approved frontend display zone; existing backend timestamp/lifecycle semantics are unchanged.

Frontend checks: `npm test -- --maxWorkers=2`, `npm run lint`, `npm run typecheck`, `npm run build`. Production scaffolds beyond Employee/Department/dashboard are not complete yet.

## 7. Common errors

| Symptom | Check / safe fix |
|---|---|
| `JAVA_HOME` invalid / Java 25 selected | Select an installed JDK 17 and check Wrapper `--version` in the launching terminal. |
| `Could not resolve placeholder ROCKEY_...` | Configure all four required variables in that terminal; `.env` is not loaded automatically. |
| MySQL communications failure / connection refused | Verify the MySQL service, 127.0.0.1, port 3306, and the server's availability. Do not open a public firewall port. |
| `Access denied for user` | Re-enter the private MySQL credentials and verify the account's allowed host/privileges with the database administrator. Never log the password or disable auth. |
| `Unknown database rockey_hospitality` | Create the database before launching Spring; a JDBC URL alone does not create it. |
| `Schema-validation: missing table` | Apply `database/schema.sql` to the database named in the JDBC URL. Do not change `validate` to `create`/`update` as a shortcut. |
| Column/type validation mismatch | Check the applied schema against the reviewed script. `IF NOT EXISTS` does not migrate old tables. Back up existing data and request a reviewed migration, not a reset. |
| `mysql` not recognized / SOURCE file missing | Use the installed client path and run SOURCE from the repo root. |
| JWT key decode / weak key failure | Supply a Base64-encoded random key of at least 32 bytes; do not use a readable demo password as a key. |
| Port 8080 / 5173 occupied | Stop only your own existing backend/Vite terminal with Ctrl+C; do not kill an unidentified process. |
| Browser CORS / refresh rejected | Use localhost:5173, exact configured allowlist, credentials-enabled fetch and local HTTP cookie settings. Do not broaden to `*`. |
| STAFF/ADMIN 403 | Confirm active User, linked active Employee, active Department and permitted ownership; frontend hiding is not authorization. |
| 429 auth rate limit | Respect the configured window; repeated reloads consume refresh attempts. Do not disable limiting. |

## 8. Stop / restart

Ctrl+C stops the foreground backend or Vite terminal. Restart in the same configured terminal to retain the private environment; MySQL remains running and data persists. A different terminal needs configuration again. Keep the signing key stable across a normal backend restart; regenerating it invalidates previous access tokens (log in/refresh again). No key needs to be committed.

When finished, close the private terminal or remove its credential variables without displaying their values:

```powershell
Remove-Item Env:ROCKEY_DB_PASSWORD, Env:ROCKEY_JWT_SECRET_BASE64 -ErrorAction SilentlyContinue
$localDbCredential = $null
$jwtBytes = $null
```

## 9. UTC storage and local display

The JDBC URL sets the MySQL connection/session to UTC, and Hibernate uses `hibernate.jdbc.time_zone=UTC`. Database DATETIME columns do not carry a timezone label: treat application-written values as UTC storage. Set `time_zone='+00:00'` when inserting seed/bootstrap rows manually so `CURRENT_TIMESTAMP` agrees with that convention. Raw SQL can therefore show a time four or five hours ahead of New York wall-clock time, depending on daylight saving.

The hotel frontend presentation zone is **America/New_York**. Offset-bearing instants such as dashboard `asOf` are formatted in that zone. Existing API `LocalDateTime` fields are timezone-less server-local values; their current meaning and lifecycle comparisons are preserved. Do not append `Z`, reinterpret them as already-UTC instants, or subtract an offset twice. Keep server/laptop timezone consistent when presenting these fields; changing timestamp semantics would require an API decision.

## 10. Work-laptop checklist

Clone the available project revision; confirm that it includes `frontend/` before expecting the UI. Install/select JDK 17, MySQL 8 and the required Node runtime on that laptop. Create its own local database/application account and private environment; do not copy `.env`, credential files, build output or IDE settings from another machine. Apply `schema.sql`, start the backend, and check protected `/auth/me` returns 401 before authenticating. Run `npm ci` in `frontend`, then open localhost:5173. Register and manually bootstrap its first ADMIN only if none exists. Test a write/read, stop/start, and read the same record again. No private runtime-cache path, AWS or Docker is required.

## 11. Local validation evidence — 2026-10-07

- Java **17.0.20.1**, Maven Wrapper **3.9.16**, MySQL **8.0.46**. The Java 17 package build discovered **561 tests: 550 passed, 0 failures/errors, 11 opt-in live tests skipped**. Skipped tests are not counted as passed.
- The schema and first-ADMIN template were tested in a fresh, separate `rockey_hospitality_bootstrap_test` database with FK/unique constraints enabled. Startup used a temporary application account with only SELECT/INSERT/UPDATE/DELETE on that database.
- Real USER registration → stopped backend → manual bootstrap → restarted backend → ordinary ADMIN login, `/auth/me`, dashboard and Department/Employee create/read all passed. Both creating missing Management and reusing active Management were exercised. Existing/missing/inactive identities, inactive Management, duplicate linkage and an existing ADMIN were guarded; a batch SQL failure rolled back the promotion/linkage. Old USER JWT claims were rejected after promotion, and stored refresh state was cleared.
- Optional Department seeding and UTC manual-insert timestamps passed. Only the temporary database/account were removed afterward; normal development and existing hardening databases were not reset or changed. This is bootstrap smoke evidence, not a rerun of the separate 11 live hardening tests or the complete API collection.
- Frontend regression: **278/278**, with lint, checked-JavaScript/typecheck and production build passing. Frontend application/test code was not changed.
- The 51-operation OpenAPI snapshot, production Java behavior, schema definitions, Maven configuration and tests remained unchanged. Source/config/database edits are educational comments only; documentation, ignore rules, the manual template and Postman presentation labels were updated. Secret review found no high-confidence credential markers or saved Postman secret variables in source-control-eligible files; `.env` and filled bootstrap copies remain ignored.
