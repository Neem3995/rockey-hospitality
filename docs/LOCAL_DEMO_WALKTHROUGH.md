# Local housekeeping demo

Verified against the real React UI, Spring Boot, and `rockey_hospitality_demo` on 2026-10-11. Use this isolated local environment only. Obtain demo passwords privately; never add credentials to this guide or frontend configuration.

## Startup

From the repository root, load the ignored private configuration into this PowerShell process, select your installed Java 17 directory, and bind to loopback. Do not start a second instance if port 8080 is occupied. Never print environment values.

```powershell
Get-Content -LiteralPath .env | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
        [Environment]::SetEnvironmentVariable($matches[1], $matches[2].Trim().Trim('"').Trim("'"), 'Process')
    }
}
$env:JAVA_HOME = 'YOUR_INSTALLED_JDK17_DIRECTORY'
$env:SERVER_ADDRESS = '127.0.0.1'
.\mvnw.cmd spring-boot:run
```

The simple loader supports one assignment per line, optionally enclosed in matching quotes; preserve special characters and do not add inline comments to values. Configure the private demo URL and JWT as described in `LOCAL_SETUP.md`.

From `frontend`, run `npm run dev -- --host 127.0.0.1`. Open `http://localhost:5173`; retain the existing API default `http://localhost:8080/api` and trusted localhost Origin. Keep browser/frontend/backend hostnames consistent for cookies. Spring Boot does not load `.env` automatically.

For provisioning, public registration creates USER only. Follow `LOCAL_SETUP.md` and `database/bootstrap-admin.sql.example` for the private first ADMIN, stopping the backend before promotion. ADMIN creates MANAGER through `/api/users`; MANAGER or ADMIN creates USER. Existing demo accounts are active and must not be recreated. Keep the private bootstrap ADMIN.

## Main workflow

1. Sign in as `HKmanager@email.com`. Dashboard identifies Manager; Tasks, Rooms, and Team are available.
2. In Rooms, create active Room 204, floor 2, initial status DIRTY only if absent. Do not overwrite existing completed history.
3. In Tasks, choose Create task: title `Clean Room 204`, priority HIGH, Room 204, housekeeper `Demo USER` (confirm `housekeeper@email.com` in Team). Save creates and assigns together. Task ASSIGNED; room DIRTY.
4. Sign out and sign in as `housekeeper@email.com`. Dashboard identifies Housekeeper; My Tasks shows only assigned work.
5. Start the task: task IN_PROGRESS, room CLEANING. Complete: task COMPLETED, room INSPECTION. Completed work remains visible.
6. Sign out and return as Manager. Rooms → Inspect room 204 selects the latest completed task automatically. Record PASS: room READY, task still COMPLETED. History records PASS.

The executed Room 204 example is already complete. For another demonstration, use a fresh disposable room/task or supported dirty-room operation; do not erase history.

## Separate fixtures and evidence

Room 205 was completed and inspected FAIL using direct real API calls: completed task retained, room DIRTY, FAIL history persisted. A separate `Rework Room 205` task is now ASSIGNED. Room 206 has an ASSIGNED task for a separate disposable isolation USER; the demo housekeeper list excludes it, and direct GET of that task returns 403. USER Rooms/Team API requests also return 403. These fixtures were added only to the demo database.

Final counts: 4 users (private ADMIN, MANAGER, demo USER, isolation USER), 3 rooms, 4 tasks, 2 inspections. No protected database was modified.

## Verification and troubleshooting

Real browser: both logins, dashboards, manager navigation, housekeeper route denial, refresh, account switching/logout, assignment, start/complete, PASS/history, login validation and safe invalid-password errors, keyboard Enter submission and Escape modal dismissal. Light/dark and 390×844 / 1280×900 viewports were inspected; tables use horizontal scrolling on small screens. Browser storage enumeration was unavailable through this browser tool; memory-only token handling and theme-only storage were checked in source and automated tests.

MySQL SELECT queries confirmed assignment, start, completion, PASS, FAIL, and fixture counts. Frontend: 217/217 tests passed; lint, typecheck, production build passed. No backend code changed or backend regression suite rerun. Loading state was observed during login; exhaustive keyboard/focus and delayed-network testing were not performed.

For connection errors, check loopback listeners, privately loaded process variables, demo database URL, and ignored startup logs under `target/`. For refresh problems, use localhost consistently and retain credentialed requests and existing CORS policy. Invalid credentials should produce a safe generic message; USER supervisor routes should be denied. Never run destructive hardening tests against this database or execute `schema.sql` over existing tables.
