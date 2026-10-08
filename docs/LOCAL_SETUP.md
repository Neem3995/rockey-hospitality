# Fresh local setup — housekeeping-only

## Runtimes

Java **17**, MySQL **8**, Maven Wrapper **3.9.16**; Node compatible with frontend/package.json (verified 22.18.0).
Use process-local JAVA_HOME; no system PATH change is needed.
Actual executed results, skips and live-check limitations are recorded in [SECURITY_AND_QUALITY.md](SECURITY_AND_QUALITY.md#current-verification--2026-10-08), not inferred from the setup instructions below.

```powershell
$env:JAVA_HOME = 'YOUR_JDK17_DIRECTORY'
& "$env:JAVA_HOME\bin\java.exe" -version
.\mvnw.cmd -version
```

## Fresh database, not a migration

This four-table branch is incompatible with the earlier nine-table schema. Do not import over old/user data.
In MySQL Workbench or your approved client, create/select a fresh housekeeping database and run database/schema.sql.
The script creates exactly users/rooms/tasks/inspections; it never drops/creates databases.

Configure privately in process environment or an ignored local launcher:

| Variable | Meaning |
|---|---|
| ROCKEY_DB_URL | jdbc:mysql://127.0.0.1:3306/YOUR_FRESH_DATABASE?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC (local demo only) |
| ROCKEY_DB_USERNAME | Private local username |
| ROCKEY_DB_PASSWORD | Private local password |
| ROCKEY_JWT_SECRET_BASE64 | Private standard-Base64 encoding of ≥32 securely random bytes |
| ROCKEY_CORS_ALLOWED_ORIGINS | Exact origins; default http://localhost:5173 |
| ROCKEY_REFRESH_COOKIE_SECURE | false local HTTP; true production HTTPS |
| ROCKEY_REFRESH_COOKIE_SAME_SITE | Lax default |

Repository-root .env is ignored but **Spring Boot does not automatically load it**. Set environment privately; never paste credentials or use password command-line arguments. VITE_* is public and must never contain backend secrets.

## Backend and first ADMIN

```powershell
$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=America/New_York'
.\mvnw.cmd spring-boot:run
```

Default port 8080. Hibernate validate, open-in-view false; no schema generation/automatic migration.

1. Register your account via UI or POST /api/auth/register; it is USER.
2. Stop backend.
3. Copy database/bootstrap-admin.sql.example to ignored database/bootstrap-admin.sql.
4. Replace only REGISTERED_USER_EMAIL_HERE with the exact registered email.
5. In the fresh selected database, run the reviewed script. It promotes one active USER only when no ADMIN exists and clears refresh state.
6. Confirm one changed row, restart and login again. Old USER-role JWTs cannot authorize ADMIN.
7. Keep the filled local script private/ignored.

No default credentials or privileged bootstrap endpoint. ADMIN can create USER/MANAGER through Team; MANAGER creates USER only.

## Frontend

```powershell
Set-Location frontend
npm ci
npm run dev
```

Open http://localhost:5173. Default API http://localhost:8080/api.
Only public VITE_API_BASE_URL may be overridden in ignored frontend/.env.local. Preview 4173 is not an approved CORS origin unless explicitly configured.
Demo: supervisor creates DIRTY room + assigned task; assigned USER starts/completes; supervisor records FAIL/rework or PASS.

## Verification

Normal mvnw.cmd verify runs unit/security/controller tests; opt-in live tests are skipped unless ROCKEY_HARDENING_LIVE=true.
Live tests require **disposable rockey_hospitality_hardening**, initialized with the four-table schema, and verify the database name before resetting its four tables. Never enable against project/user data.

With its URL/credentials configured privately:
```powershell
$env:ROCKEY_HARDENING_LIVE = 'true'
.\mvnw.cmd verify
```

Import postman/Rockey-Housekeeping.postman_collection.json; configure baseUrl/adminEmail/adminPassword privately for a bootstrapped disposable ADMIN.
It generates synthetic fixtures/history and verifies all 24 operations plus denial cases.
**Do not export raw reports/environments with tokens/cookies/passwords.**

Optional scripts/verify-hardening.cjs uses an existing Newman tool (ROCKEY_NEWMAN_PATH if not locally resolvable), never installs packages.
Inputs: ROCKEY_HARDENING_ADMIN_EMAIL/PASSWORD privately; ROCKEY_HARDENING_API_URL default http://127.0.0.1:18080/api; disposable ROCKEY_DB_URL.
It refuses non-disposable/non-loopback configuration, prints safe counts and exports only generated credential-free OpenAPI into ignored target/.
This guide does not authorize resetting another database, installing services or deploying.
