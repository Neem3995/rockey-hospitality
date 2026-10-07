# Backend study guide

Rockey is one Spring Boot application with eight JPA entities, nine MySQL tables and 51 business API operations. This guide explains the existing code, not a different architecture. Read [LOCAL_SETUP.md](LOCAL_SETUP.md) before running it.

## 1. Request and response flow

```text
React form / API service
  ↓ HTTP JSON + memory-only Bearer access token
Spring Security (authenticate and gate the route)
  ↓
Controller (validate request DTO, pass authenticated identity)
  ↓
Service (authorize ownership, enforce rules, start transaction)
  ↓
Repository (scoped queries, saves and intentional locks)
  ↓
JPA / Hibernate (map Java objects to SQL)
  ↓
MySQL (persist rows and enforce constraints)

MySQL → Repository → Service → safe response DTO
  → Controller → JSON → React state / render
```

The controller does not decide employment rules or write SQL. Hiding a React button is not authorization. The response is a DTO, not the entire JPA object graph.

## 2. Layer responsibilities

| Layer | Responsibility | Example to explain aloud |
|---|---|---|
| Controller | HTTP method/path, request validation, authenticated principal, status/response | EmployeeController passes a valid request to EmployeeService. |
| Service | Business rules, ownership, normalization, lifecycle, coordinated transactions | EmployeeService creates an optional User and its Employee atomically. |
| Repository | Data lookup, save, scoped pagination, aggregate queries and locks | DepartmentRepository locks the Department before referencing work is created. |
| Entity | Table fields, identity and JPA relationships | Employee has a required Department and an optional unique User link. |
| DTO / mapping | Validated input or safe, shallow output | EmployeeResponse includes a Department summary, never a password/hash/token. |
| Security | Authentication and coarse route-role gates; services add fine restrictions | JwtAuthenticationFilter checks signed claims against current database state. |

Constructor injection makes dependencies explicit. Existing private mapping methods convert entities into DTOs; no mapping framework is needed. Lazy relationships and `open-in-view=false` keep response preparation inside service transactions rather than allowing hidden queries while JSON is serialized.

## 3. Nine tables and relationships

```text
departments (1) ─────< employees       (required Department)
              ├─────< tasks           (required Department)
              ├─────< inventory_items (required Department)
              └─────< users           (optional Department)

users (0..1) ──────── (0..1) employees
  employee.user_id is nullable + unique:
  each User can have 0..1 Employee; each Employee can have 0..1 User.
  A linked pair must have matching department_id values.

employees (1) ───────< tasks  (optional assigned_employee_id)
rooms     (1) ───────< tasks  (optional room_id)
events    (1) ───────< tasks  (optional event_id)

users (1) ──< event_registrations >── (1) events
              unique (user_id, event_id): many-to-many membership

employees (1) ───────< alerts (required recipient employee_id)
tasks     (1) ───────< alerts (optional task_id)
```

The nine tables are `users`, `employees`, `departments`, `rooms`, `tasks`, `events`, `event_registrations`, `inventory_items`, and `alerts`. Each parent may have zero or many child rows. Optional foreign keys permit a child with no referenced parent. `event_registrations` is a join mapping, not a ninth entity. Analytics has neither entity nor table. Alert `source_key` correlates Room/Inventory conditions without extra Room/Inventory foreign keys.

Keeping identity (`users`) separate from employment (`employees`) permits public USER accounts without employment and Employees without application logins. `Employee.jobRole` describes position; Department groups work. Neither grants permissions.

## 4. Authentication versus authorization

**Authentication: who are you?** BCrypt verifies the password; JWT proves a short-lived signed identity. The filter then verifies the account's current role, identity and active state in MySQL, so outdated claims are not authoritative.

**Authorization: what may you do?** Spring Security gates routes by USER/STAFF/ADMIN. Services enforce ownership and, where required, an active linked Employee in an active Department. React role checks improve navigation only; the backend remains authoritative.

The access JWT stays in frontend memory. The opaque refresh token stays in an HttpOnly cookie and only its hash/expiry is stored in User. Login/refresh replace the one active refresh session. Cookie refresh validates browser Origin against the configured allowlist. Logout clears that stored session and expires the cookie; it does not blacklist access tokens. New access tokens and fresh profile checks follow the existing authentication flow.

### Compact access table

| Capability | USER | STAFF | ADMIN |
|---|---|---|---|
| Account / dashboard | Own profile; retained registration count only | Own assigned work/alerts and own Department stock counts, with eligibility checks | Own profile; approved global dashboard counts |
| Departments / Employees | No access | Department reads; linked own Employee detail only | Manage; optional internal login provisioning |
| Rooms | No access | Read Rooms; valid status changes require Employee/Department eligibility | Manage Rooms and valid transitions |
| Tasks | No access | Read/list/complete own assigned work only | Search, create, update, assign, complete or cancel eligible work |
| Events | Browse; register/withdraw own membership while eligible | Browse, not attendee self-registration | Manage/cancel Events; preparation counts |
| Inventory | No access | Active items in own active Department only | Manage/restock; active and inactive history |
| Alerts | No access | Own alerts; read/resolve under eligibility rules | Oversight/resolve; mark READ only when the recipient |
| Other Analytics / OpenAPI tooling | No access | No access | Read-only global/scoped aggregates; API documentation |

Not every read requires the same eligibility rule: for example, STAFF Room browsing is not limited to a Department. Never replace backend scopes with filtering a global response in React.

## 5. Representative workflows

### A. STAFF task and Room context

The Task/Room backend is implemented; their operational React pages are not built yet. ADMIN creates/assigns a Task through `/api/tasks`. TaskService checks the active Department, assignee and optional Room/Event, using shared locks before saving. Room and Employee deactivation cannot bypass non-terminal work guards.

The eventual React Work view calls the existing `/api/tasks/assigned/{employeeId}` using the authenticated Employee identity, then `/api/tasks/{taskId}` or `/api/tasks/{taskId}/complete`. Controller → TaskService checks own assignment and lifecycle → Repository → MySQL → TaskResponse → React. A Room summary adds workflow context; Task completion does not invent an automatic Room transition. A separate `/api/rooms/{roomId}/status` request follows RoomService's canonical transition rules (`OCCUPIED → DIRTY → CLEANING → INSPECTION → READY`).

### B. ADMIN Employee creation

This frontend/backend workflow is available now: EmployeeForm → `POST /api/employees` → EmployeeController → EmployeeService → locked active Department and uniqueness checks → optional BCrypt User save → Employee save → EmployeeResponse → React.

`createLogin=false` creates no User. `true` creates one new STAFF/ADMIN login, never links an arbitrary existing User. A transaction prevents a half-created login/profile. Linked User/Employee Department IDs stay equal on transfers. Deactivation preserves history, disables linked access and rejects non-terminal assigned work. The separate manual first-ADMIN procedure is local/demo-only, not an application provisioning endpoint.

### C. USER Event membership

The Event/registration backend is implemented; its operational React pages are not built yet. The eventual form calls `POST /api/events/{eventId}/registrations`. RegistrationService checks the active USER, locks the future OPEN Event, checks capacity/duplicate membership and saves the User's join mapping. The response contains safe registration/Event information, not an entity graph.

`READ_COMMITTED` lets post-lock counts see the prior registration's commit rather than an earlier InnoDB snapshot. The join's unique pair prevents duplicate membership. Withdraw uses the current authenticated USER; Event cancellation preserves Event, registration and Task history.

## 6. MySQL, JPA and correctness protections

The setup account creates the database and runs `database/schema.sql` to create tables. Hibernate `ddl-auto=validate` checks their shape at startup and refuses mismatches; it does not silently rewrite an existing database. `CREATE TABLE IF NOT EXISTS` is not a migration strategy. The application account needs only SELECT/INSERT/UPDATE/DELETE on its own schema.

Foreign keys prevent dangling references; unique constraints protect email, Room number, SKU and optional Employee/User linkage. `@Transactional` groups related writes so failures roll them back. Intentional shared row locks serialize races such as Department deactivation versus new work, or the last Event seat. These protections are not replaceable by disabled buttons.

UTC JDBC handling stores/treats database timestamps as UTC. DATETIME itself has no zone label, so raw SQL can appear four/five hours ahead of New York display. Manual inserts set the SQL session to UTC. Dashboard `asOf` is an offset-bearing instant rendered in `America/New_York`; existing timezone-less API LocalDateTime values retain server-local semantics. Do not append `Z` or apply an offset twice. See the setup guide for details.

Ordinary Spring scheduling checks Room readiness, Tasks and low Inventory every five minutes by default. Source keys plus recipient locks suppress equivalent unresolved alerts; cleared conditions resolve rows, and later recurrence can create a new row. Analytics queries count persisted data with fixed query fragments and named parameters under role scope. Neither needs a workflow engine, message broker or new persistence model.

## 7. Five-minute instructor demo

Prepare privately beforehand: configure Java 17/MySQL/runtime variables, apply schema, complete the first local ADMIN bootstrap, build/start the backend and start the frontend. Never project `.env`, auth tokens, password hashes or a login response. Use synthetic demonstration names.

1. **0:00–0:45 — Model:** show `database/schema.sql` table names and the diagram above. Explain User versus Employee and the join table.
2. **0:45–1:30 — Layers:** open EmployeeController → EmployeeService → EmployeeRepository → Employee. Explain input validation, service rules, transaction and safe DTO.
3. **1:30–2:30 — UI write:** log in as the prepared ADMIN. Create a demonstration Department, then an Employee with `createLogin=false`. Show the Department summary and no-login profile; do not demonstrate real credentials.
4. **2:30–3:30 — Persistence:** use the setup account privately, then display only `SELECT id, name, department_id, status FROM employees WHERE id=<demo_id>;`. Explain the FK and linked-login option. Never use `SELECT * FROM users`.
5. **3:30–4:15 — Read/restart:** close/reopen the management view; read the same ID. If time permits, restart the backend in its configured terminal and log in/read it again; no reseeding is needed.
6. **4:15–5:00 — Security and remaining scope:** explain USER/STAFF/ADMIN using the table, memory JWT/HttpOnly refresh, and backend ownership. Show the existing role dashboard. Clearly identify Task/Room/Event/Inventory/Alert/extra-Analytics UI as remaining work rather than claiming it is available.

Future presentation may put Rooms and Tasks in one Work view with compact Alerts and USER Events. Inventory and Analytics are **not waived**; a shorter demo does not remove required delivery features. See [SIMPLIFICATION_OPTIONS.md](SIMPLIFICATION_OPTIONS.md).
