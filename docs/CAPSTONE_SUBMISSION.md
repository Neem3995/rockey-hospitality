# Rockey housekeeping capstone submission draft

Status: current product/implementation documentation, **not a claim that final submission is complete**. Prepared 2026-10-08 for the `refactor/final-capstone-polish` quality checkpoint based on `2ccc706b2ee7f93efd395d60b00a009057a30dbd`, committed/pushed as `51efcd06768070a51913fb9874eefdcd966d0533`. Pending submission gates remain unchanged.

The supplied official capstone PDF and `00_CAPSTONE_REQUIREMENTS.md` remain grading sources; they live outside this repository. Instructor-confirmed AWS waiver is recorded below. No other waiver is inferred. Earlier CAPSTONE documents 10–20 describe the superseded hotel-wide product, not a requirement to restore removed domains.

## Problem, solution and users

Housekeeping staff need to know which room to clean, who owns that work and whether a supervisor has checked the result. A room should not appear ready just because someone changed a label. Rockey connects assigned cleaning work, controlled room transitions and retained inspection history in one role-aware interface.

- **USER / Housekeeper:** views and executes only their assigned cleaning tasks.
- **MANAGER:** manages rooms/assignments and records inspections, but does not execute housekeeper tasks.
- **ADMIN:** has supervisor capabilities plus non-ADMIN account administration.

This is an internal housekeeping application, not a guest booking/payment system. Public registration creates USER only; supervisors still decide assignments. The first ADMIN is created through the private local setup procedure, not a public privileged endpoint.

## Approved scope and boundaries

Exactly four entities/tables: User/users, Room/rooms, Task/tasks, Inspection/inspections. Backend roles are USER/MANAGER/ADMIN; Housekeeper is a UI label for USER.

Workflow: DIRTY room -> assigned Task -> USER starts -> CLEANING -> USER completes -> INSPECTION -> supervisor records PASS (READY) or FAIL (DIRTY/rework). Cancellation/deactivation preserves rows/history.

No Department, Employee, Event/Registration, Inventory, Alert or separate Analytics domain. No reservation, revenue, forecasting, AI/voice, generic workflow engine or inspection analytics. No new table, dependency or API operation is introduced by the quality polish.

## User stories and acceptance criteria

These HK identifiers document existing housekeeping behavior; they do not silently reuse the old hotel-wide US/FR identifiers. Fifteen stories exceed the official eight-story minimum and ten-story Excellent target without adding business features.

| ID | Story | Acceptance criteria |
|---|---|---|
| HK-US-01 | As a housekeeper, I want to register an account. | Valid unique email/name/password creates USER only; invalid input is rejected and privileged role escalation is unavailable. |
| HK-US-02 | As an active user, I want to sign in securely. | Valid credentials establish authenticated identity; invalid/inactive credentials fail safely; auth rate limits remain enforced. |
| HK-US-03 | As a signed-in user, I want session recovery and sign-out. | Expired access can refresh once through HttpOnly cookie; logout revokes refresh and expires cookie; no browser token persistence. |
| HK-US-04 | As a housekeeper, I want to see only my work. | Task lists/details are ownership-scoped; another user's task is rejected by backend, not merely hidden in UI. |
| HK-US-05 | As the assigned housekeeper, I want to start cleaning. | Only own ASSIGNED work can become IN_PROGRESS; room becomes CLEANING in the same transaction; supervisors cannot execute it. |
| HK-US-06 | As the assigned housekeeper, I want to complete cleaning. | Only own IN_PROGRESS work can complete; server sets completion time and room becomes INSPECTION atomically. |
| HK-US-07 | As a supervisor, I want to manage room availability. | Valid unique rooms can be created/edited; number remains immutable; readiness cannot bypass inspection; active work/pending inspection blocks deactivation. |
| HK-US-08 | As a supervisor, I want to assign cleaning work. | Creation requires an active USER and active DIRTY room; competing non-terminal work for the room is rejected. |
| HK-US-09 | As a supervisor, I want to change or cancel unfinished work. | Non-terminal work can be edited/reassigned/cancelled; room cannot change; completed history cannot be edited/cancelled; cancellation retains history. |
| HK-US-10 | As a supervisor, I want to record a room inspection. | Latest completed task must belong to the active INSPECTION room; authenticated supervisor is recorded; PASS makes READY and FAIL makes DIRTY. |
| HK-US-11 | As a supervisor, I want retained inspection history. | Authorized history lists show result/task/inspector/notes/time; no inspection edit/delete operation exists; earlier cycles remain readable. |
| HK-US-12 | As a manager, I want to provision and view housekeepers. | Manager list/detail scope is USER only; creation cannot grant MANAGER/ADMIN; manager cannot update/deactivate accounts. |
| HK-US-13 | As an admin, I want to oversee non-ADMIN accounts. | ADMIN can provision USER/MANAGER and update/deactivate non-ADMIN accounts; active work blocks eligibility changes; ADMIN accounts stay bootstrap-managed. |
| HK-US-14 | As a user, I want a relevant housekeeping overview. | USER counts only own tasks; supervisors also see authorized active room counts; current complete lists are used, never a single page extrapolated into totals. |
| HK-US-15 | As a keyboard user, I want clear, recoverable forms and navigation. | Fields associate safe errors and retain drafts; pending/uncertain writes are guarded; route navigation focuses main; modal/skip/theme behavior stays accessible. |

## Functional requirements and implementation evidence

Sixteen current functional requirements exceed the official minimum of ten. Test references are source evidence; actual execution/coverage is recorded only in [SECURITY_AND_QUALITY.md](SECURITY_AND_QUALITY.md#current-verification--2026-10-08).

| ID | Requirement | Stories | Implementation / test evidence |
|---|---|---|---|
| HK-FR-01 | Register validated unique USER accounts only. | 01 | AuthController/AuthService; AuthServiceTest, authUi/apiClient tests |
| HK-FR-02 | Authenticate active accounts with BCrypt/JWT and bounded attempts. | 02 | AuthService, JwtService, AuthenticationRateLimiter; auth/security tests |
| HK-FR-03 | Rotate/revoke one hash-only refresh session and expose safe own profile. | 03 | AuthService/AuthController; API client/session regression tests |
| HK-FR-04 | Enforce current role/active status and task ownership server-side. | 04–13 | JWT filter, UserService, TaskService; MVC/security/service tests |
| HK-FR-05 | Create/read/edit/soft-deactivate rooms with validated lifecycle. | 07 | RoomController/RoomService; RoomServiceTest; rooms UI tests |
| HK-FR-06 | Create assigned cleaning work with valid active references. | 08 | TaskService/create, repository row locks; TaskServiceTest |
| HK-FR-07 | Start own ASSIGNED task and transition its room atomically. | 05 | TaskService/status; service/MVC and housekeeper UI tests |
| HK-FR-08 | Complete own IN_PROGRESS task and stamp completion atomically. | 06 | TaskService/status; service/MVC and housekeeper UI tests |
| HK-FR-09 | Allow supervisor non-terminal editing/reassignment/cancellation only. | 09 | TaskService/update/cancel; task management/security tests |
| HK-FR-10 | Record minimal authenticated PASS/FAIL inspections transactionally. | 10 | RoomService/inspect, RoomDtos; RoomServiceTest, inspection UI tests |
| HK-FR-11 | Retain soft-lifecycle and append-only inspection history. | 09,11,13 | Entity/FK schema and services; history/guard tests |
| HK-FR-12 | Provide scoped team provisioning and non-ADMIN administration. | 12,13 | UserService/UserController; team/service/security tests |
| HK-FR-13 | Derive only approved housekeeping dashboard counts from authorized data. | 14 | DashboardPage, scoped task/room APIs; dashboard isolation/zero-count tests |
| HK-FR-14 | Return safe DTO/errors and associate allowlisted form validation errors. | 01,07–13,15 | GlobalExceptionHandler, DTOs, apiClient, Input/Select, business pages; form/error tests |
| HK-FR-15 | Provide six protected/public routes, reusable controls and role-aware UI. | All | App/AuthGuard/AppLayout, shared UI; route/auth/theme/component tests |
| HK-FR-16 | Reject uncertain duplicate submissions until explicit read reconciliation and malformed essential responses before rendering. | 15 | Local page refs/state, housekeepingService; write/reconciliation/response tests |

Names above are grouping references. The actual production/test files are under [src/main/java](../src/main/java), [src/test/java](../src/test/java) and [frontend/src/tests](../frontend/src/tests). No claim is made that every named requirement has fresh live-MySQL/browser verification in this pass.

## Non-functional requirements

| Area | Current requirement / evidence boundary |
|---|---|
| Security | Backend RBAC/ownership, BCrypt, JWT validation, single hash-only refresh, Origin/CORS controls and safe DTOs; memory-only access JWT/HttpOnly refresh. No default credentials in repo. |
| Integrity | Required FKs, unique email/room number, floor/completion constraints, explicit indexes; service checks enforce active references and task/room consistency. |
| Reliability | Task/room and inspection/room mutations share transactions; conditional lock order User -> Room -> Task; active-work checks use locking current reads. Mock tests are not proof of database races; fresh live races remain NOT RUN. |
| Accessibility | Labeled controls, safe field associations, skip/main focus, keyboard modal behavior, non-color status text and reduced motion. Native-browser/responsive validation of this polish is PENDING. |
| Maintainability | Controller -> Service -> Repository -> Entity, DTO boundary, constructor injection, checked JS and small local state. No generic mutation/workflow framework or new dependency. |
| Time | Authentication uses UTC Instant; operational offset-free LocalDateTime uses JVM America/New_York per local setup. UI must not relabel those values as UTC. |
| Quality | Maven production line gate >=70%, Excellent target >=80%; success/failure/security cases. Use canonical measured results, not historical totals as fresh evidence. |
| Scalability | REQUIRED, locally implemented bounded pagination with scoped totals; live MySQL/deployed verification remains pending. |
| Performance | No current benchmark/SLA evidence: PENDING measurement/accepted target. Do not invent response-time claims. |
| Reproducibility | Fresh four-table schema, private first-ADMIN procedure and pinned project runtimes documented. Old nine-table data is incompatible, not automatically migrated/reset. |

## Logical system architecture

This shows the actual logical local stack, not an assertion that hosting/deployment exists.

```mermaid
flowchart LR
    Browser[Housekeeper / Manager / Admin browser] --> UI[React + Vite / React Router]
    UI --> Client[API adapter / memory-only access JWT]
    Client -->|JSON + Bearer / credentialed refresh cookie| Security[Spring Security / JWT / Origin and CORS checks]
    Security --> Controllers[Auth / User / Room / Task controllers]
    Controllers --> Services[Auth / User / Room / Task services]
    Services -->|transactions and ordered locks| Repositories[Spring Data JPA repositories]
    Repositories --> Entities[User / Room / Task / Inspection]
    Entities --> DB[(MySQL: four tables)]
    Controllers -->|safe DTO or error| Client
```

Authorization belongs to the backend, not hidden UI buttons. Inspector identity comes from authenticated principal, not a request body. Minimal inspection behavior belongs to RoomController/RoomService; no extra domain layer is invented.

## Four-table ERD

Derived from [database/schema.sql](../database/schema.sql), not the old wireframe. Each child FK is required; each parent can have zero or more related rows. Attribute constraints and nullable fields are stated below.

```mermaid
erDiagram
    users ||--o{ tasks : assigned_user_id
    rooms ||--o{ tasks : room_id
    rooms ||--o{ inspections : room_id
    tasks ||--o{ inspections : task_id
    users ||--o{ inspections : inspected_by_user_id
    users {
        bigint id PK
        varchar name
        varchar email UK
        varchar password_hash
        enum role
        boolean active
        varchar refresh_token_hash UK
        datetime refresh_token_expires_at
        datetime created_at
        datetime updated_at
    }
    rooms {
        bigint id PK
        varchar room_number UK
        smallint floor
        enum status
        boolean active
        datetime created_at
        datetime updated_at
    }
    tasks {
        bigint id PK
        varchar title
        varchar description
        enum status
        enum priority
        bigint assigned_user_id FK
        bigint room_id FK
        datetime due_at
        datetime completed_at
        datetime created_at
        datetime updated_at
    }
    inspections {
        bigint id PK
        bigint room_id FK
        bigint task_id FK
        bigint inspected_by_user_id FK
        enum result
        varchar notes
        datetime inspected_at
    }
```

- All IDs: auto-increment BIGINT primary keys. All five FKs are NOT NULL; there are no destructive cascade declarations.
- Users: name(100), unique email(120), password_hash(60), role USER/MANAGER/ADMIN, active; nullable unique refresh_token_hash(64) and nullable refresh_token_expires_at. No credential values are documented.
- Rooms: unique immutable room_number(10), floor CHECK 1–99, active; statuses READY/DIRTY/CLEANING/INSPECTION/OUT_OF_SERVICE.
- Tasks: title(150), nullable description(1000), statuses ASSIGNED/IN_PROGRESS/COMPLETED/CANCELLED, priorities LOW/MEDIUM/HIGH/URGENT; nullable due_at/completed_at. Completion CHECK requires completed_at exactly when status is COMPLETED. Indexes `(assigned_user_id,status)` and `(room_id,status)`.
- Inspections: result PASS/FAIL, nullable notes(1000), inspected_at; index `(room_id,inspected_at)`. No UNIQUE task_id is claimed: room locking/workflow eligibility prevents repeat recording for that completed cycle in application behavior.
- Timestamps use DATETIME(6); all other shown columns are NOT NULL. Task.room and Inspection.room consistency is checked by service. Inspector references a supervising User; task assignee eligibility is active USER.

## React component diagram

```mermaid
flowchart TD
    Root[main / BrowserRouter / AuthProvider] --> App[App routes]
    App --> Layout[AppLayout: navigation / theme / skip link / main]
    Layout --> Guard[AuthGuard: public or permitted role]
    Guard --> Auth[AuthPage: login / register]
    Guard --> Dashboard[DashboardPage]
    Guard --> Tasks[TasksPage]
    Guard --> Rooms[RoomsPage + inspection/history dialogs]
    Guard --> Team[TeamPage]
    Auth --> UI[Button / Input / Select / Card / Modal / Table / loading and errors]
    Dashboard --> UI
    Tasks --> UI
    Rooms --> UI
    Team --> UI
    Dashboard --> Reads[useRead / useAuth]
    Tasks --> Reads
    Rooms --> Reads
    Team --> Reads
    Reads --> Adapter[housekeepingService / apiClient]
    Auth --> Session[AuthProvider / apiClient]
```

Hooks have specific uses: useState for local UI/errors; useEffect for reads/route effects; useContext through useAuth; useReducer for task draft; useMemo for dashboard counts; useCallback for cancellable loaders; useRef for focus/submission guards. New resilience uses local page logic, not a general mutation engine.

## API pointers and freeze

[OpenAPI snapshot](rockey-openapi.json), [24-operation table and DTO/error guide](BACKEND_GUIDE.md#api--24-operations), [Postman collection](../postman/Rockey-Housekeeping.postman_collection.json).

Auth: register/login/refresh/me/logout. Users: list/create/detail/update/deactivate. Rooms: list/create/detail/update/deactivate/status, history/list and inspection/create. Tasks: list/create/detail/update/cancel/status. Lists now return bounded page envelopes with scoped totals; task status filtering is server-side. No count/Analytics endpoint was added.

Six UI routes: /login, /register, /dashboard, /tasks, /rooms, /team; unknown routes show Page not found. Rooms/Team are supervisor-only; task ownership is enforced server-side. All four core tables and twenty-four operations remain unchanged.

## Architecture decisions

| Decision | Rationale / boundary |
|---|---|
| Four housekeeping domains only | Matches approved scope and four-table ERD target; do not restore deleted hotel-wide features to satisfy old planning documents. |
| USER means Housekeeper; MANAGER/ADMIN oversee | Preserves rubric USER/ADMIN names; only assigned USER executes tasks, supervisors inspect/manage. |
| Unidirectional `@ManyToOne`, no reverse collections/cascades | Queries load the required child/parent relationships; unused `@OneToMany` collections and unrelated `@ManyToMany` add no current use case. No REMOVE cascade/orphan removal protects retained history. This is a deliberate mapping choice to justify, not an assertion that every annotation example is implemented. |
| Transactional service workflows and ordered row locks | Prevent partial task/room/inspection changes and conflicting active-work decisions; preserve current implementation rather than replace it. |
| Inspection stays minimal inside Room service/controller | It is append-only history with PASS/FAIL, not a separate workflow engine. |
| Memory JWT / HttpOnly refresh / one server refresh session | Limits browser token persistence; single-session behavior means tabs/devices can invalidate earlier refresh state. No client contract is changed to hide this limitation. |
| Checked JavaScript and ordinary React hooks | Course-aligned readable components; no TypeScript rewrite, schema library or generic mutation framework. |
| Fixed safe field messages / dependency-free response checks | Prevent arbitrary server internals entering UI and reject malformed consumed shapes without duplicating business validation. |
| Read reconciliation after uncertain write | A lost response may follow a committed mutation. No network/5xx replay; blocked writes require a successful explicit read, while existing legitimate 401 recovery remains. |
| Bounded page contract | Required-completion instruction authorizes replacing bare arrays; defaults, scope and compatibility are documented in REQUIRED_COMPLETION.md. |
| AWS waiver only | Instructor waived AWS due insufficient training; CI/CD, deployment, accessible URL and branch protection are not automatically waived. |

## Submission closeout checklist - 2026-10-12

One current inventory, based on the supplied official 13-page rubric and current project records. AWS is waived as explicitly supplied by the user; no other waiver is inferred. Local/main source revision: `aa8d2b9c7e2ee43b20b88fc895a613f12d18c8e7`, independently matched through GitHub. Fresh changed-source measurements are in [the canonical quality record](SECURITY_AND_QUALITY.md#required-completion-verification---2026-10-12); [current contract and external plan](REQUIRED_COMPLETION.md) supersede the earlier closeout. External status is NOT VERIFIED when evidence is unavailable; local existence does not establish instructor acceptance or Canvas delivery.

| Requirement | Status | Evidence location | Remaining action |
|---|---|---|---|
| Custom proposal / instructor approval | LOCAL CONTENT PRESENT; approval NOT VERIFIED | Problem, users and boundaries above; README | Supply written current-scope approval/acceptance. |
| 8+ stories / 10+ functional requirements / NFRs | LOCAL CONTENT PRESENT | 15 stories, 16 FRs and NFRs above | Instructor review; performance/scalability limitations remain stated. |
| Architecture, four-table ERD, React component diagram | LOCAL CONTENT PRESENT | Diagrams above; database/schema.sql | Submit current diagrams; deployment view depends on accepted delivery plan. |
| API docs / exported Postman JSON | STATIC PASS, live Postman NOT RUN | rockey-openapi.json, BACKEND_GUIDE.md, postman/Rockey-Housekeeping.postman_collection.json | 24 operations and 58 refs checked; approve disposable live suite separately. |
| Setup / README / decisions | LOCAL CONTENT PRESENT | README, LOCAL_SETUP.md, decisions above | Validate reviewer access; deployment/rollback guide depends on hosting decision. |
| Backend / frontend source and safe verification | VERIFIED for stated offline scope (150 passed/21 skipped; 226 passed) | SECURITY_AND_QUALITY.md; ignored target/closeout-* logs | Submit measured counts/coverage and limitations; no readiness claim. |
| Eight persisted account/session races | REQUIRED / PENDING EXECUTION (NOT RUN; user declined reset) | HousekeepingMySqlTest.java; quality record | If later approved, establish exact hardening URL, schema and account metadata permissions first. |
| Sonar / quality gate / major-critical findings | REQUIRED / PENDING EXECUTION (NOT RUN; no authorized project) | Quality record, no current report-task.txt | Provide authorized existing host/project; scan corrected revision and retain task/gate/findings. Instructor must decide any alternative. |
| Pagination | REQUIRED / IMPLEMENTED; unit, MockMvc and jsdom VERIFIED; MySQL query and browser PENDING EXECUTION | PageResponse, repositories/services/controllers, frontend navigation and regressions | MySQL/deployed verification pending authorized environment. |
| Standalone test seed | REQUIRED / IMPLEMENTED; input safety VERIFIED; seeding PENDING EXECUTION (NOT RUN) | database/seed-test.sql; scripts/seed-test.cjs; safety regressions | Explicit write approval, private hashes and empty disposable schema. |
| AWS infrastructure / monitoring | WAIVED per supplied instruction | Current request and recorded exception | Retain instructor waiver with submission. |
| Jenkins / non-AWS deployment / URLs / rollback | REQUIRED / CONFIGURATION PREPARED; PENDING EXECUTION (NOT RUN) | Jenkinsfile, deployment templates, REQUIRED_COMPLETION.md | Approve access/resources/installations/deployment; only a platform replacement needs instructor acceptance. |
| Git / original main protection | REQUIRED / PENDING EXECUTION: SHA MATCH; protection ABSENT | Read-only GitHub metadata: protected=false, protection 404, no rules | Approve policy/settings separately; code already on main at verified SHA. |
| Slides and required presentation PDF | PREPARED / LOCAL QA PASS; delivery NOT VERIFIED | docs/submission final PPTX/PDF and presenter notes | Rehearse; submit matching pair and retain Canvas receipt. |
| 5-10 minute delivery / rehearsal | OUTLINE PREPARED, rehearsal NOT RUN | Nine-minute plan below | Rehearse with approved fixtures; time delivery and fallback. |
| Two peer reviews / feedback given, received, incorporated | NOT VERIFIED | No supplied current peer-review evidence | Provide two genuine reviews and incorporation references. |
| Jira shared board / final stories / closed sprint | NOT VERIFIED | External system unavailable | Supply instructor-accessible link, final status and closed-sprint evidence. |
| Self-assessment | NOT VERIFIED | External submission unavailable | Complete and retain actual artifact/receipt. |
| Canvas repository URL / documentation / slides PDF / Postman | NOT VERIFIED | No current Canvas receipts | Upload accepted artifacts; retain receipts. |
| Current deadline | NOT VERIFIED | Rubric: end of final class; actual class date unavailable | Confirm instructor's date/time. |
| Private demo access | LOCAL SESSION WORKS; delivery NOT VERIFIED | LOCAL_DEMO_WALKTHROUGH.md and fresh USER browser check | Provide credentials privately and verify intended reviewer access. Never include secrets in submission docs. |

## Nine-minute presentation and rehearsal

| Time | Segment | Prepared route / evidence |
|---|---|---|
| 0:00-1:00 | Problem, users, bounded housekeeping scope | Problem/stories above; USER executes, MANAGER/ADMIN supervise. |
| 1:00-2:30 | Architecture and four entities | Existing logical architecture, ERD and component diagram; local stack honestly labeled. |
| 2:30-5:30 | Prepared cleaning/inspection walkthrough | Existing Room 204 completed/PASS and Room 205 completed/FAIL plus assigned rework from LOCAL_DEMO_WALKTHROUGH.md. Show existing views. Start/complete/inspect rework only after explicit disposable-fixture mutation approval; otherwise narrate verified sequence. |
| 5:30-6:30 | Retained history and one rejected invalid action | Manager history requires private login; demonstrate USER navigation to /rooms yielding Access denied (freshly observed, read-only). |
| 6:30-8:00 | Transactions, ownership, locks and testing | User -> Room -> Task ordering; separate login/logout account locks; current-source 150 backend/226 frontend passes; disclose 21 skipped live entries and no current Sonar. |
| 8:00-9:00 | Lessons and limitations | Scope discipline, retained history, uncertain-write read reconciliation; required external scan/pipeline/deployment/protection and genuine artifact completion. |

Rehearsal checklist:

- [ ] Confirm final deck/PDF pair and instructor timing; outline totals exactly 9 minutes.
- [ ] Start only the existing loopback demo, Java 17, ddl-auto=validate; never run schema/reset scripts over demo data.
- [ ] Privately verify manager and housekeeper sign-in; keep passwords, tokens, hashes, .env and network responses off-screen.
- [ ] Confirm Room 204/205 history before rehearsal; do not recreate or erase existing fixtures.
- [ ] Obtain explicit disposable-fixture approval before demonstrating start/complete/inspect or forced uncertain writes; otherwise use the read-only walkthrough.
- [ ] Check remaining supervisor modal Tab/Shift+Tab/Escape, pending-close and opener/fallback restoration in the native browser; do not substitute jsdom proof.
- [ ] Use the existing safe historical screenshot at target/frontend-demo-completed.png for fallback: it visibly shows completed Room 204 READY, completed FAIL Room 205 DIRTY, and assigned rework. It was visually inspected this closeout and contains no passwords/tokens/hashes. Copy it to the approved presentation package before deleting generated outputs. No recording exists in this inspected evidence.
- [ ] Rehearse failure fallback and questions; explain that screenshots are historical and fresh verification has its own stated scope.

## Remaining actions in priority order

See [the required completion plan](REQUIRED_COMPLETION.md#final-materials-and-shortest-path) for exact service access, approval-gated commands, deployment security/rollback, protection policy and user-owned artifacts. AWS is the only waiver; no other requirement is a waiver candidate. These changes remain uncommitted and unpushed.
