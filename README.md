# Rockey Hospitality Backend

Rockey is an internal hotel operations and workflow-management application. The feature backend contains Department, User/JWT/refresh/logout, Employee, Room/turnover, Task, Event/registration, Inventory, Alert/automation, and role-scoped Dashboard/Analytics.

## Technology

- Java 17
- Spring Boot 3.5.16 and Maven Wrapper
- Spring Web, Spring Data JPA, Jakarta Validation, Spring Security
- JJWT 0.13.0 for signed access tokens
- BCrypt password hashing
- MySQL
- JUnit 5, Mockito, MockMvc, and Spring Security Test
- Springdoc 2.8.17 (generated OpenAPI; ADMIN-only) and JaCoCo 0.8.14

## Backend hardening gate

The previously verified backend baseline passed **561/561** Java17/live tests and **51/51 operations**, **116 Postman requests/291 assertions**, zero failures. Final Sonar analysis and Quality Gate passed with **0 open vulnerabilities/Critical/Major findings**, **13 Accepted + 6 False Positive** human dispositions, and **89 deferred Minor/Info** findings. These are historical verified results, not a claim that every gate was rerun for the latest documentation cleanup or that the application has zero risk.

See [Windows local setup and first ADMIN](docs/LOCAL_SETUP.md), [the backend study guide](docs/BACKEND_GUIDE.md), [security/quality safeguards](docs/SECURITY_AND_QUALITY.md), and [the retained model and frontend direction](docs/SIMPLIFICATION_OPTIONS.md). The canonical business API has 51 operations, including logout; `/v3/api-docs` and `/v3/api-docs.yaml` are ADMIN-only documentation tooling, not additional business operations. A portable frozen snapshot is at [docs/rockey-openapi.json](docs/rockey-openapi.json); the parent canonical API contract remains authoritative for business rules and lifecycle semantics.

Older parent review documentation's "50 endpoints" wording predates logout. Current README, frozen OpenAPI and `14_API_CONTRACT.md` govern the 51-operation contract.

`mvnw.cmd verify` targets Java 17 and enforces at least 70% overall production line coverage without exclusions (80% target). Live checks are opt-in and restricted to the disposable local `rockey_hospitality_hardening` schema; they reset its test data. They are never normal production seeds. SonarQube, repository publication/protection, frontend and deployment are separate gates, not implied by a passing backend suite.

## Frontend foundations, authentication and management

The React/Vite frontend is isolated in [frontend/](frontend/README.md). It provides checked JavaScript, shared Rockey light/dark themes, reusable UI, authentication, role-scoped Dashboard, ADMIN Employee/Department management and STAFF self-profile. Other business routes remain scaffolds. Management dialogs preserve opener/fallback focus during background refresh. See the frontend README for setup and boundaries.

## Authentication API

| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | `/api/auth/register` | Public | Create an active `USER` account |
| POST | `/api/auth/login` | Public | Authenticate and replace the account's refresh session |
| POST | `/api/auth/refresh` | Public with refresh cookie | Rotate the refresh token and issue a new access token |
| GET | `/api/auth/me` | Authenticated | Return the current user's safe profile |
| POST | `/api/auth/logout` | Authenticated | Revoke the current refresh session and expire its cookie |

Registration does not accept a role field and always creates `USER`. Login, registration, and refresh responses contain a 15-minute bearer access token plus safe user data. The 7-day opaque refresh token is set only in the `rockey_refresh` HttpOnly cookie; its raw value is never returned in JSON or stored in the database. Only its SHA-256 hash is persisted, and every login or refresh replaces the prior refresh session.

Logout returns `204 No Content`, clears the account's stored refresh hash and expiration, and expires the `rockey_refresh` cookie. Repeating logout while the account has no stored refresh state is safe.

## Employee API

| Method | Path | Required role | Purpose |
|---|---|---|---|
| GET | `/api/employees` | `ADMIN` | Paginated employee list with department/status filters |
| POST | `/api/employees` | `ADMIN` | Create an employee with an optional new STAFF/ADMIN login |
| GET | `/api/employees/{employeeId}` | `ADMIN` or linked self `STAFF` | Employee detail |
| PUT | `/api/employees/{employeeId}` | `ADMIN` | Update profile, department, job role, and status |
| DELETE | `/api/employees/{employeeId}` | `ADMIN` | Soft-deactivate employee and linked login |

`createLogin=false` persists an Employee with no User and no application access. `createLogin=true` creates one unique BCrypt-backed STAFF/ADMIN User in the same transaction. Linked User and Employee departments remain synchronized, and employee responses never contain credentials or tokens.

## Room API

| Method | Path | Required role | Purpose |
|---|---|---|---|
| GET | `/api/rooms` | STAFF, ADMIN | Paginated room list with status, floor, type, and active filters |
| POST | `/api/rooms` | ADMIN | Create a unique normalized room |
| GET | `/api/rooms/{roomId}` | STAFF, ADMIN | Retrieve room details |
| PUT | `/api/rooms/{roomId}` | ADMIN | Update room type, floor, arrival time, and active state |
| DELETE | `/api/rooms/{roomId}` | ADMIN | Soft-deactivate a room |
| PATCH | `/api/rooms/{roomId}/status` | eligible STAFF, ADMIN | Apply a canonical turnover or maintenance transition |

Room status follows the documented transition table, including `OCCUPIED → DIRTY → CLEANING → INSPECTION → READY`. STAFF status changes require an active linked Employee in an active Department. `nextArrivalAt` is operational readiness data only; no reservation, booking, guest identity, or payment model exists.

## Task API

| Method | Path | Required role | Purpose |
|---|---|---|---|
| GET | `/api/tasks` | ADMIN | Paginated Task search with operational filters and optional `overdue` Boolean |
| POST | `/api/tasks` | ADMIN | Create an OPEN or ASSIGNED Task |
| GET | `/api/tasks/{taskId}` | assigned STAFF, ADMIN | Retrieve one authorized Task |
| PUT | `/api/tasks/{taskId}` | ADMIN | Update details, references, assignment, priority, due time, and valid state |
| DELETE | `/api/tasks/{taskId}` | ADMIN | Cancel an eligible Task while preserving history |
| PATCH | `/api/tasks/{taskId}/complete` | assigned STAFF, ADMIN | Complete eligible assigned work |
| PATCH | `/api/tasks/{taskId}/assigned-employee` | ADMIN | Assign, reassign, or unassign an active Employee |
| GET | `/api/tasks/assigned/{employeeId}` | self STAFF, ADMIN | Paginated assigned work |

`overdue=true` selects non-terminal Tasks with a non-null due time before server time. `overdue=false` selects all Tasks that do not meet that predicate; omission applies no due filter. Room and Event references are independently optional; new references to cancelled Events are rejected, and Event filtering uses the optional `eventId` query parameter.

## Event and registration API

| Method | Path | Required role | Purpose |
|---|---|---|---|
| GET | `/api/events` | USER, STAFF, ADMIN | Paginated Event list with status/date filters |
| POST | `/api/events` | ADMIN | Create a future DRAFT or OPEN Event |
| GET | `/api/events/{eventId}` | USER, STAFF, ADMIN | Event detail, capacity, registration, and preparation counts |
| PUT | `/api/events/{eventId}` | ADMIN | Update Event details and apply a canonical transition |
| DELETE | `/api/events/{eventId}` | ADMIN | Soft-cancel an eligible Event and return its preserved response |
| POST | `/api/events/{eventId}/registrations` | USER | Register the current active USER for an eligible Event |
| DELETE | `/api/events/{eventId}/registrations/me` | USER | Withdraw the current USER before registration closes or the Event starts |
| GET | `/api/events/registrations/me` | USER | Paginated list of the current USER's registrations |

Event deletion always transitions to `CANCELLED`; it never removes the Event, registration rows, or linked Task history. Registration is transactional and limited to active USER accounts, future OPEN Events, remaining capacity, and one registration per User/Event pair. Preparation is reported as Task and completed-Task counts, with zero counts when an Event has no preparation work.

## Inventory API

| Method | Path | Required role | Purpose |
|---|---|---|---|
| GET | `/api/inventory` | STAFF scoped, ADMIN | Paginated inventory with optional `departmentId` and `active` filters |
| POST | `/api/inventory` | ADMIN | Create an item in an active Department |
| GET | `/api/inventory/{itemId}` | STAFF scoped, ADMIN | Safe item detail and shallow Department summary |
| PUT | `/api/inventory/{itemId}` | ADMIN | Update/restock, transfer, or change active state |
| DELETE | `/api/inventory/{itemId}` | ADMIN | Soft-deactivate; return 204 with no body |

STAFF requires an ACTIVE Employee in an active Department and can view only that Department's active items. ADMIN can view active and inactive history across Departments. List defaults are `page=0`, `size=20` (maximum 100), `sort=name,asc`; sorting is allowlisted to name, SKU, quantity, reorder threshold, and creation time.

SKU is trimmed, uppercased, globally unique (including inactive items), and cannot be changed through PUT. Quantities and reorder thresholds are non-negative; omitted creation counts default to zero. PUT `quantity` supplies the new absolute on-hand count, not a restock delta. New assignments, moves, and reactivation require an active Department; inactive history may retain its original inactive Department. Department deactivation now checks active inventory as well as active Employees and non-terminal Tasks. Writes are transactional; inventory item updates are locked and assignments share the Department lock with Department deactivation. Alert generation and Analytics use these persisted counts; no automated purchasing action exists.

The Inventory Postman collection creates isolated fixtures and checks all five endpoints, validation/errors, role boundaries, Department guarding, and preserved inactive history. Supply runtime tokens; do not save credentials. The previously verified full backend gate included live database/API checks; a fresh checkout still requires the documented local configuration.

## Alerts and automation

| Method | Path | Access | Purpose |
|---|---|---|---|
| GET | `/api/alerts` | STAFF own; ADMIN oversight | Paginated alerts with optional employee/type/status filters |
| GET | `/api/alerts/{alertId}` | Recipient STAFF; ADMIN | Detail including preserved resolved history |
| PUT | `/api/alerts/{alertId}/read` | Recipient only, including ADMIN for own alert | UNREAD to READ, with no request body |
| DELETE | `/api/alerts/{alertId}` | Recipient STAFF; ADMIN oversight | Resolve/dismiss; preserve row; 204 with no body |

STAFF requires an active Employee in an active Department. USER cannot access alerts. Lists default to unresolved UNREAD/READ alerts, `page=0`, `size=20` (maximum 100), `sort=createdAt,desc`; `status=RESOLVED` explicitly retrieves history. Sorting is allowlisted to type, severity, status, createdAt, readAt, resolvedAt. Repeated READ or resolving an already RESOLVED alert returns 409. Alert services derive a JVM-server-zone view of the injected Clock to match the existing Room/Task server-local LocalDateTime conventions without changing the authentication Clock. First-read and resolution timestamps use that same view; resolving UNREAD also sets readAt.

One ordinary Spring scheduled method runs all checks transactionally, with configurable fixed delay `rockey.alerts.scan-delay-ms` / environment variable `ROCKEY_ALERT_SCAN_DELAY_MS`, default 300000 ms (five minutes). The first scan also waits that delay. A scan completes before the next delay begins. No scheduling dependency or manual scan/create endpoint was added. Failed service scans roll back; the scheduler logs only the exception type and permits later attempts.

- ROOM: active non-READY Room with `now <= nextArrivalAt <= now + 2 hours`; every active Housekeeping Employee receives a notice. Past/missing arrivals do not qualify.
- TASK: assigned non-terminal Task; overdue is strictly `dueAt < now`, and HIGH/URGENT priority is an independent condition. COMPLETED/CANCELLED or unassigned Tasks produce no notices.
- INVENTORY: active item with `quantity <= reorderThreshold`; active Purchasing Employees receive notices, or active ADMIN Employees with active accounts when Purchasing has no active recipients. No recipient is invented when both sets are empty.

Server-generated source keys are `ROOM:<id>:ARRIVAL_NOT_READY`, `TASK:<id>:OVERDUE`, `TASK:<id>:HIGH_PRIORITY`, and `INVENTORY:<id>:AT_OR_BELOW_THRESHOLD`. At most one unresolved row per type/key/recipient is retained; recipient and Alert locks protect generation/lifecycle writes. Cleared conditions and former/ineligible recipients automatically resolve existing derived alerts, preserving rows and first-read timestamps. Resolved conditions can later recur as a new row. Manual dismissal also permits a new row on a later scan if the condition still holds. SYSTEM/non-derived alerts are not automatically cleared. Severity retains the canonical INFO default; no additional severity-mapping policy was invented. Room/Inventory alerts have no Task relationship; Task alerts retain a shallow Task summary.

Tests invoke services using a controllable Clock and scheduler configuration without sleeping. The Alert Postman collection needs runtime tokens, an existing own UNREAD alert, and another employee's fixture IDs; prepare source conditions through existing APIs and allow the scheduler to run before manual API verification. The previously verified full backend gate included live deduplication/concurrency checks; these were not rerun during documentation cleanup. Automation never purchases inventory, changes staffing, completes Tasks, changes Room states, or sends external notifications.

## Department API authorization

| Method | Path | Required role |
|---|---|---|
| GET | `/api/departments?active=true` | `STAFF` or `ADMIN` |
| GET | `/api/departments/{departmentId}` | `STAFF` or `ADMIN` |
| POST | `/api/departments` | `ADMIN` |
| PUT | `/api/departments/{departmentId}` | `ADMIN` |
| DELETE | `/api/departments/{departmentId}` | `ADMIN` |

Department deletion remains a soft deactivation. Publicly registered users cannot administer departments.

## Local setup

Follow [docs/LOCAL_SETUP.md](docs/LOCAL_SETUP.md) for PowerShell commands, private configuration, MySQL troubleshooting, running both applications and restart verification. Normal database: `rockey_hospitality`. Hibernate uses **validate**, so apply `database/schema.sql` before startup; Department seeds are optional. Spring does not load `.env` automatically. A fresh database has no ADMIN; use the documented manual first-ADMIN procedure for local development/demo only, never a public privileged-registration endpoint.

Under instructor guidance, AWS is not required for this local project. Local reproduction requires no AWS, Docker or migration framework; other capstone requirements are not automatically waived.

## Build and run

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
.\mvnw.cmd spring-boot:run
```

The default base URL is `http://localhost:8080`. Import the collections from `postman/`. The authentication collection captures a bearer token and lets Postman retain the refresh cookie. Department, Employee, Room, Task, Event, and Inventory management require an `ADMIN` access token; eligible STAFF can read active rooms, perform valid Room transitions, view/complete only their own assigned Tasks, and read active inventory in their own Department. USER accounts are limited to Event browsing and their own registrations. Registration always creates `USER`, so the initial ADMIN remains a controlled bootstrap concern; after that, the Employee create operation may provision linked STAFF/ADMIN accounts.

## Security and current boundaries

- Passwords are BCrypt-hashed and never included in API responses.
- JWT signing material is required through external configuration and is never hardcoded.
- Access JWT claims are limited to user id, email/subject, and role; clients must keep access tokens in memory.
- Authentication checks reject inactive users and reject JWT identity/role claims that no longer match the database.
- Login limiting is keyed by IP plus normalized email: 5 failures per 15 minutes.
- Registration limiting is keyed by IP: 5 attempts per hour.
- Refresh limiting is keyed by IP: 30 attempts per 15 minutes.
- The rate limiter is intentionally in-memory for one backend instance. A distributed deployment would require a shared limiter.
- Client IP currently comes from the servlet remote address. Any later proxy/deployment topology requires separate verification.
- CSRF is disabled for stateless bearer-token APIs. Cookie refresh validates browser Origin against the configured CORS allowlist; non-browser requests may omit Origin. HttpOnly, SameSite, rotation and revocation remain in force. HTTPS/cross-site-cookie assumptions require separate verification if deployed.
- One active refresh session is supported per account. `tokenVersion` remains in the canonical User model but is not used by this approved opaque-refresh design.
- No normal production seed user or plaintext password is supplied. Opt-in disposable fixtures generate random test-only passwords and keep temporary exports in ignored `target/hardening/` until the runner removes them.
- `FR-14` User/Employee department consistency is enforced transactionally.
- Department deactivation rejects active Employee, non-terminal Task, and active Inventory references.
- Employee and Room deactivation reject non-terminal assigned/referencing Tasks.
- New Tasks reject inactive Departments, Employees, and Rooms.
- Room-readiness, Task, and Inventory alert generation, deduplication, auto-resolution, and history-preserving lifecycle are implemented.
- Analytics is implemented as a read-only aggregate layer; USER sees only its own registration count, STAFF receives eligible identity/Department-scoped metrics, and ADMIN receives approved aggregates. React authentication/dashboard/Employee/Department UI exists; other business pages remain scaffolds. AWS and CI/CD are not implemented.
