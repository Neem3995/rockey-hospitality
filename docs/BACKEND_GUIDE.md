# Rockey housekeeping study walkthrough

Start with the definitions, then follow one operation through the files. We are not trying to memorize every annotation or getter. The useful question is: where did this value come from, what happens to it next, and which layer is responsible for checking it?

This guide keeps its existing filename because it is the current linked study document. It now walks through the React connections as well as the backend. Setup commands belong in [LOCAL_SETUP.md](LOCAL_SETUP.md); dated verification evidence belongs in [SECURITY_AND_QUALITY.md](SECURITY_AND_QUALITY.md). Reading an example here is not evidence that a live check was rerun.

## Definitions first

- **Class and instance:** `Room` is the Java class describing the shape/operations. `new Room(...)` creates an instance with values for one room. A JavaScript object literal is an object, not automatically a class.
- **Parameter and argument:** `actorId` in a method declaration is a parameter. `actor.getId()` supplied in the call is an argument. The caller provides the value; the receiving method gives it a local name.
- **DTO and entity:** a DTO carries input/output data. An entity is mapped to a database row. A request DTO's positive id does not prove a corresponding entity exists.
- **Authentication and authorization:** first establish the identity, then check what that identity may read/change. A signed JWT is not permission to access every row.
- **Bean and request:** Spring usually creates these shared application beans at startup. A request calls their methods; it does not create another service/configuration object each time.
- **Transaction and save:** a transaction groups database work into one commit or rollback. `save` participates in it; it is not an independent commit. In-memory refs/counters are not database writes.
- **State and ref:** a React state setter schedules a render with a new value. A ref's `.current` changes immediately but does not itself cause a render.
- **Promise and await:** a promise represents a future result. `await` suspends the current async function until that result settles; it does not block the browser's whole event loop.
- **JSDoc and runtime checks:** JSDoc gives checked JavaScript type information. The actual `safeUser`, `checkedList` and shape-check functions reject invalid runtime data. A type cast does not do that work.
- **Operational time and instant:** `LocalDateTime`/MySQL `DATETIME` contain calendar/time fields without an offset. `Instant` identifies a point on the timeline. Rockey treats operational fields as hotel-local time and auth expiry as UTC-based time; neither convention adds an offset to a `DATETIME` column.

## Approved implementation plan

Keep USER/MANAGER/ADMIN and exactly four related core tables, retaining the four-table Excellent ERD target.
Order: entity/schema → repositories → auth/team/room/task services → DTOs/controllers/security → task/inspection workflow → six React routes → focused tests, fresh MySQL/API, coverage and docs.

For a React request we follow the API helper, security chain, controller, service and repository to Hibernate/MySQL, then return a DTO to the UI. Postman sends HTTP directly rather than running the React helpers. The [walkthrough below](#follow-one-start-task-request) names the actual symbols involved.

Controllers validate DTOs and accept the trusted principal. Services enforce permissions, transitions and transactions.
Repositories contain ordinary JPA lookups and row locks. Entities are stored rows, never controller responses.
There are four flat DTO grouping files and four controllers/services. Minimal Inspection behavior stays in RoomService/RoomController/RoomDtos; no separate inspection service, controller, engine, analytics or alerts.

The [submission draft](CAPSTONE_SUBMISSION.md) supplies the current architecture/component diagrams, complete ERD, requirements and decisions. Older CAPSTONE planning diagrams describe superseded branches. Use [SECURITY_AND_QUALITY.md](SECURITY_AND_QUALITY.md#current-verification--2026-10-08) for dated measured results.

## Four-table ERD

| Child | Required foreign key | Parent |
|---|---|---|
| tasks | assigned_user_id | users.id |
| tasks | room_id | rooms.id |
| inspections | room_id | rooms.id |
| inspections | task_id | tasks.id |
| inspections | inspected_by_user_id | users.id |

Four tables, five many-to-one/one-to-many relationships. Foreign keys preserve history without destructive cascades.

### User

id/name/unique email/BCrypt passwordHash/role/active/refreshTokenHash/refreshTokenExpiresAt/createdAt/updatedAt.
Role USER/MANAGER/ADMIN. User is both login identity and housekeeper; no second worker model.
Public registration always creates USER. Manager provisions USER; Admin provisions USER/MANAGER.
The first ADMIN is bootstrap-managed, not a privileged endpoint.

### Room

id/unique immutable roomNumber/floor/status/active/createdAt/updatedAt.
roomNumber: nonblank, ≤10 characters. floor: 1–99.
READY, DIRTY, CLEANING, INSPECTION, OUT_OF_SERVICE.
Initial status READY (default), DIRTY or OUT_OF_SERVICE; workflow creates CLEANING/INSPECTION.

### Task

id/title/description/status/priority/assignedUser/room/dueAt/completedAt/createdAt/updatedAt.
title 2–150 characters; optional description ≤1000. Required active USER and active DIRTY room for creation.
Status ASSIGNED/IN_PROGRESS/COMPLETED/CANCELLED; priority LOW/MEDIUM/HIGH/URGENT.
At most one non-terminal task per room. Room is immutable after creation.
Supervisor can edit/reassign non-terminal tasks; terminal history is immutable.
dueAt is optional hotel-local operational time. A past deadline is valid overdue work, not a reservation.

### Inspection

id/room/completed task/authenticated inspectedBy/PASS or FAIL/optional notes (≤1000)/inspectedAt.
Only a supervisor can inspect the latest completed task for an active room currently INSPECTION.
Append-only history; no inspection edit/delete endpoints. Reinspection follows the next cleaning cycle.

## Permissions

| Action | USER | MANAGER | ADMIN |
|---|---|---|---|
| Own profile/refresh/logout | Yes | Yes | Yes |
| Task list/detail | Own assigned only | All | All |
| Start/complete | Own assigned only | No | No |
| Create/edit/reassign/cancel tasks | No | Yes | Yes |
| Room management/inspection/history | No | Yes | Yes |
| Team list/detail | No | USER only | All |
| Provision USER | Public self-register | Yes | Yes |
| Provision MANAGER | No | No | Yes |
| Update/deactivate non-ADMIN | No | No | Yes |
| Provision/change ADMIN | No | No | Bootstrap only |
| Generated OpenAPI | No | No | Yes |

JWT authentication reloads active/current role from MySQL. Service ownership checks prevent cross-user reads/changes.

## Workflow

Only the assigned USER can execute work; MANAGER/ADMIN manage assignment/cancellation and inspection, never start/complete tasks.
USER start: ASSIGNED → IN_PROGRESS makes DIRTY → CLEANING atomically.
USER complete: IN_PROGRESS → COMPLETED stamps completedAt and makes CLEANING → INSPECTION.
Inspection PASS makes READY; FAIL makes DIRTY. Manager creates/reassigns new cleaning work for rework.
Direct room status: READY → DIRTY/OUT_OF_SERVICE, DIRTY → OUT_OF_SERVICE, OUT_OF_SERVICE → DIRTY. Same status is safe; READY cannot bypass inspection.
Task DELETE cancels non-terminal work; cancelling IN_PROGRESS makes DIRTY. Repeated cancel returns 204; completed task cancellation returns 409.
Room/User DELETE soft-deactivates. Active tasks block room/worker deactivation or worker eligibility changes. Pending inspection blocks room deactivation.
History remains readable under role/ownership boundaries.

When assignment needs an account lock, task mutations use User → Room → Task for mutable-row locking. Existing-task operations may first look up the room id without locking the task. Room and account guards also lock their active-work query results. In the configured MySQL setup these are **locking current reads**, not older repeatable-read snapshot checks. Prior live race checks are recorded in the quality document; locks are not a blanket guarantee against every possible future concurrency bug. Task/Room and Inspection/Room changes share transactions.

## API — 24 operations

No query parameters/pagination wrappers. Lists are ordinary complete arrays.

| Method | Path | Success | Access / input |
|---|---|---|---|
| POST | /api/auth/register | 201 AuthResponse | Public; name/email/password; USER only |
| POST | /api/auth/login | 200 AuthResponse | Public; email/password |
| POST | /api/auth/refresh | 200 AuthResponse | HttpOnly cookie; no body; approved Origin when present |
| GET | /api/auth/me | 200 CurrentUserResponse | Authenticated own identity |
| POST | /api/auth/logout | 204 | Authenticated; no body; revoke refresh, expire cookie |
| GET | /api/users | 200 UserResponse[] | Supervisor; Manager USER scope |
| POST | /api/users | 201 UserResponse | name/email/password/role; Manager USER, Admin USER/MANAGER |
| GET | /api/users/{id} | 200 UserResponse | Supervisor; Manager USER scope |
| PUT | /api/users/{id} | 200 UserResponse | ADMIN; non-ADMIN target; name/email/role/active |
| DELETE | /api/users/{id} | 204 | ADMIN; soft-deactivate non-ADMIN |
| GET | /api/rooms | 200 RoomResponse[] | Supervisor |
| POST | /api/rooms | 201 RoomResponse | roomNumber/floor; optional status/active |
| GET | /api/rooms/{id} | 200 RoomResponse | Supervisor |
| PUT | /api/rooms/{id} | 200 RoomResponse | Supervisor; immutable roomNumber, floor, optional same status/active |
| DELETE | /api/rooms/{id} | 204 | Supervisor; soft-deactivate |
| PUT | /api/rooms/{id}/status | 200 RoomResponse | Supervisor; status |
| GET | /api/rooms/{id}/inspections | 200 InspectionResponse[] | Supervisor; history |
| POST | /api/rooms/{id}/inspections | 201 InspectionResponse | Supervisor; taskId/result/notes; identity server-owned |
| GET | /api/tasks | 200 TaskResponse[] | USER own scope; supervisor all |
| POST | /api/tasks | 201 TaskResponse | Supervisor; title/description/priority/assignedUserId/roomId/dueAt |
| GET | /api/tasks/{id} | 200 TaskResponse | Own USER or supervisor |
| PUT | /api/tasks/{id} | 200 TaskResponse | Supervisor; same required fields; room immutable |
| DELETE | /api/tasks/{id} | 204 | Supervisor; soft-cancel |
| PUT | /api/tasks/{id}/status | 200 TaskResponse | Own USER start/complete; MANAGER/ADMIN cancel only |

AuthResponse: accessToken/tokenType/accessExpiresAt/refreshExpiresAt/safe user, never raw refresh.
Safe User: id/name/email/role/active. Worker summaries: id/name.
Room response: room fields/timestamps. Task response: task fields, assignedUser summary, room response.
Inspection response: id/roomId/taskId/inspectedBy/result/notes/inspectedAt.
Business/inspection responses never return JPA entities, passwords, raw refresh secrets or hashes. Auth responses deliberately include the access JWT and safe expiry/profile fields; the raw refresh value remains cookie-only.

Errors: 400 validation/JSON/id parsing; 401 invalid/missing/inactive/mismatched auth; 403 role/ownership/Origin; 404 missing; 409 duplicate/lifecycle/active-work; 429 auth rate limit; safe 500 unexpected.
USER cancellation through status is an invalid transition (409); DELETE cancellation is supervisor-only (403).
ApiError: timestamp/status/error/message/path/fieldErrors, no secrets/stack trace.

## Time and study notes

JWT/refresh expiry uses Instant/Clock. Operational LocalDateTime is offset-free hotel wall-clock time; run JVM in America/New_York. UI does not relabel it UTC.
“Completed today” uses that hotel date; overdue means dueAt < now and non-terminal status.
Every production Java file has STUDY NOTE guidance for its layer/annotations/collaborators. Start with Room/Task/Inspection and their services.

Do not add `Z` to an operational timestamp to make it look like UTC. The frontend's `displayTime` keeps the value labelled hotel local time; `hotelNow` uses America/New_York. Run the JVM in that zone as the setup guide specifies. Entity callbacks use the JVM clock directly, while task completion/inspection view the injected Clock in the JVM zone. Offset-free storage cannot distinguish the two occurrences of a repeated daylight-saving hour. This is a model limitation, not something a comment or display formatter can repair.

## Production source tree (32 Java files)

```text
com.rockey.hospitality/
  RockeyHospitalityApplication.java
  configuration/
    ApplicationConfiguration.java  SecurityConfiguration.java  SecurityProperties.java
  controller/
    AuthController.java  UserController.java  RoomController.java  TaskController.java
  dto/
    AuthDtos.java  UserDtos.java  RoomDtos.java  TaskDtos.java
  entity/
    User.java  Room.java  Task.java  Inspection.java
  exception/
    ApiException.java  GlobalExceptionHandler.java
  repository/
    UserRepository.java  RoomRepository.java  TaskRepository.java  InspectionRepository.java
  security/
    AuthenticationRateLimiter.java  JwtAuthenticationFilter.java  JwtService.java
    RefreshTokenService.java  RockeyUserPrincipal.java  SecurityHandlers.java
  service/
    AuthService.java  UserService.java  RoomService.java  TaskService.java
```

Enums and related input/output classes stay inside their domain files. This keeps the model small without replacing the taught layered architecture.

## Follow one start-task request

We already have an active DIRTY room and an ASSIGNED task belonging to an active USER. On `/tasks`, that housekeeper clicks Start. Follow the outgoing call and then the return path:

```text
frontend/src/pages/TasksPage.jsx: act(() => taskStatus(id, 'IN_PROGRESS'))
  → frontend/src/services/housekeepingService.js: taskStatus → write
  → frontend/src/services/apiClient.js: apiRequest → rawRequest/fetch
  → SecurityConfiguration filter chain + JwtAuthenticationFilter
  → controller/TaskController.java: status
  → service/TaskService.java: status
  → repository/TaskRepository.java: findRoomId
  → repository/RoomRepository.java: findForUpdate
  → repository/TaskRepository.java: findForUpdate
  → entity/Task.java: start + entity/Room.java: updateStatus
  → Hibernate/MySQL writes in the service transaction
  → TaskService.response → TaskDtos.TaskResponse → JSON
  → TasksPage.act closes its dialog if any and calls view.reload
  → hooks/useRead.js reads the list again and schedules updated UI state
```

Backend paths in this map and the explanations below are relative to `src/main/java/com/rockey/hospitality/`, the package shown in the tree above. Frontend paths start at the repository root. `TaskService.response` maps data before the transactional service call returns to the controller; Spring finishes the transaction before that return is delivered. A persistence/commit failure goes down the error path instead.

## Startup and configuration: give the app its shared tools

**Purpose and local words.** Configuration is setup code. A bean is a managed object, and constructor injection means Spring supplies a class's collaborators instead of that class building its own replacements.

Start at [RockeyHospitalityApplication.java](../src/main/java/com/rockey/hospitality/RockeyHospitalityApplication.java). `SpringApplication.run(RockeyHospitalityApplication.class, args)` starts the context/server using the launch arguments. Component scanning finds our annotated classes and repository interfaces below this package. This happens at startup, not when somebody clicks Start.

In [ApplicationConfiguration.java](../src/main/java/com/rockey/hospitality/configuration/ApplicationConfiguration.java), take `return Clock.systemUTC();`. The whole line provides a shared time source. `Clock` is the type; `systemUTC()` creates a clock that views system time in UTC. Spring supplies that bean to services needing time. It does not force every `LocalDateTime.now()` elsewhere to be UTC.

Next the BCrypt bean supplies password encode/match operations. The `userDetailsService(UserRepository users)` bean produces a loader: when the JWT filter invokes it, it looks up a normalized email and constructs `RockeyUserPrincipal`. Registering the loader at startup does not load every account then. OpenAPI beans describe existing operations; descriptions and security schemes do not enforce access.

`configuration/SecurityProperties.java` receives the external `rockey.security` settings and validates required/positive values at startup. Those values go to token helpers, the security chain and the cookie writer. They are configuration, not client form input.

**Housekeeping example.** TaskService receives its repositories, UserService and Clock once through its constructor. Now every start-task call uses those injected collaborators; each task still has its own stored entity values.

**Explain it aloud.**

- When is a service bean made? At context startup in this setup, not for each room request.
- Does an OpenAPI role description block a USER? No; the filter chain and service rules do.

## Security: identify the caller, then limit the action

**Purpose and local words.** A filter runs in HTTP processing before controller handling. The principal represents the verified caller. A claim is a value inside the JWT; an authority such as `ROLE_USER` is how Spring matches a current role.

Read [JwtAuthenticationFilter.java](../src/main/java/com/rockey/hospitality/security/JwtAuthenticationFilter.java) alongside [SecurityConfiguration.java](../src/main/java/com/rockey/hospitality/configuration/SecurityConfiguration.java). `SecurityContextHolder.getContext().setAuthentication(authentication)` places the verified principal/authorities in the request's security context. The `authentication` object came from the JWT claims plus a freshly loaded account, not JSON submitted by the page. Controllers can now receive that principal; the line does not itself authorize a task id.

First the filter reads Authorization. With no header it passes an unauthenticated request onward for route rules to decide. With a Bearer header it asks `JwtService.parseAccessToken` to verify the signature, expiry and required claims. Next the account loader rereads MySQL. Disabled accounts or changed id/role are rejected rather than trusting an old role claim. Then the principal enters the context and the security chain checks the HTTP method/path matchers in order. Finally the service checks row ownership and lifecycle.

For `/api/tasks/{id}/status`, all three roles may reach the service because the route also supports supervisor cancellation. `TaskService.status` still rejects MANAGER/ADMIN start/complete actions and USER access to somebody else's task. Matching the URL is only the first authorization step.

### Where the auth pieces actually connect

| Concern | Current location and responsibility |
|---|---|
| Password shape | `dto/AuthDtos.java` and `dto/UserDtos.java` annotations, invoked through controller `@Valid` |
| Password hash/match | `service/AuthService.java`: `hashPassword` checks the 72 UTF-8-byte BCrypt boundary, then encodes; login uses `PasswordEncoder.matches` |
| Signed access JWT | `security/JwtService.java`: `issueAccessToken`/`parseAccessToken`; Instant/Duration/Clock internally, Date at JJWT boundary |
| Current account | `configuration/ApplicationConfiguration.java`: account loader; `security/JwtAuthenticationFilter.java`: id/role/active comparisons |
| HttpOnly refresh cookie | `controller/AuthController.java`: `setRefreshCookie`, same name and `/api/auth` path for issue/expiry |
| Refresh secret/hash/time | `security/RefreshTokenService.java`: random generation, SHA-256, UTC comparison convention |
| Rotation/revocation | `service/AuthService.java`: `startSession`, `refresh`, `logout`; `repository/UserRepository.java`: locking refresh-hash query |
| Browser Origin/CORS | `AuthController.validateRefreshOrigin`; `SecurityConfiguration.corsConfigurationSource`; same configured allowlist |
| Attempt limits | `security/AuthenticationRateLimiter.java`, called by AuthService; local JVM counters, safe 429 |
| Work ownership | `service/TaskService.java`: `ownership`, list scope and execution rules; not a frontend role label |

### Login, refresh and logout in order

First login validates credentials, normalizes email, checks the limiter and active account, then BCrypt matches the supplied password against the stored hash. We do not decrypt the hash. `startSession` replaces the account's refresh hash/expiry and issues an access JWT. AuthController writes the raw refresh secret as Set-Cookie and returns only the access token/profile response as JSON.

The browser stores that HttpOnly cookie; `apiClient` cannot directly read it. A credentialed refresh request carries it automatically when cookie/browser policies permit. A present Origin must match the allowlist. A missing Origin retains approved non-browser compatibility. The service hashes the cookie value, locks the matching account, checks expiry/active state and rotates it. Each User has one active stored refresh session: another login or successful rotation invalidates the previous refresh value. The frontend coalesces refresh within this tab; it is not a cross-tab lock.

Logout clears the refresh hash/expiry and sends the same cookie with zero max-age. It does not blacklist an already-issued access JWT; account revalidation and expiry still apply to those tokens. The frontend confirms a 204 before announcing successful logout and clearing its local session.

CORS controls which browser origins may use the API; it is not authentication and cannot stop a non-browser client from sending a request. Origin validation, HttpOnly/SameSite, configured Secure, JWT checks and service rules each cover a different boundary. JWT signing detects changes to its claims; the payload is not encrypted. Do not log a token, password, cookie, signing key or hash while studying this flow.

**Housekeeping example.** A USER's valid token lets the controller identify them, but it cannot start another USER's task. Changing a browser label to Admin changes neither database role nor authorization.

**Explain it aloud.**

- Why reload an account after verifying a JWT? Stored active/role/id state may have changed since issue.
- Why are password and refresh hashes different? BCrypt slows password guessing; the refresh secret is high-entropy random data checked through SHA-256.
- Does an HttpOnly cookie mean every CSRF concern disappears? No; the refresh flow also checks Origin and retains its cookie/CORS policies.

## Controllers: collect the HTTP pieces, pass them on

**Purpose and local words.** A controller matches an operation to a Java method. A path variable comes from the URL. A request body comes from JSON. `@AuthenticationPrincipal` comes from Spring Security, not a caller-supplied account id.

In [TaskController.java](../src/main/java/com/rockey/hospitality/controller/TaskController.java), read `return service.status(actor.getId(), id, request.getStatus());`. The whole line delegates and returns a TaskResponse. `actor.getId()` identifies who is asking; `id` is the task selected in `/api/tasks/{id}/status`; `request.getStatus()` is the requested move from the validated DTO. These are arguments to TaskService's `actorId`, `id` and `next` parameters. They answer different questions and are not interchangeable.

First Spring matches `@RequestMapping` plus `@PutMapping`, parses the URL/body and runs `@Valid`. Next this method passes those values to the service. The service loads/checks work and returns a DTO, or throws a controlled failure. Spring serializes the returned DTO to JSON; the error handler handles failures. This controller does not duplicate transitions or save entities.

`controller/RoomController.java` passes the principal id when recording an inspection; `controller/UserController.java` delegates Team actions; `controller/AuthController.java` additionally handles cookie/Origin HTTP details. They use their corresponding service classes and DTO groupings. No separate Inspection controller is needed for this small room use case.

**Housekeeping example.** React sends `{ "status": "IN_PROGRESS" }`. The controller gets the task id from the URL and the caller from the verified header, not extra identity fields in that body.

**Explain it aloud.**

- Which id could the client choose here? The target task id, not the authenticated principal id.
- Does `@Operation` enforce an access note? No; it documents the operation.

## DTOs: keep input shape separate from stored state

**Purpose and local words.** DTO means data transfer object. Jackson maps JSON to/from these objects. Bean Validation checks annotated constraints when the controller uses `@Valid`; checking an id's shape is different from looking up a database row.

In [TaskDtos.java](../src/main/java/com/rockey/hospitality/dto/TaskDtos.java), take `@NotNull @Positive private Long roomId;`. These annotations require a supplied positive number. `roomId` is a DTO field filled from the request, not an already loaded Room. Jackson fills it from JSON, validation rejects missing/non-positive values, and TaskService then looks up the row and checks active/readiness rules. A positive but missing id still fails later.

First Jackson constructs a request using its empty constructor and fills properties. Next validation checks required fields/lengths. Then service rules resolve relationships and state. On return, `TaskService.response` builds TaskResponse using `UserService.summary` and `RoomService.response`. We expose `id/name` for the assignee, not the User's password/refresh fields or its JPA relationships.

The four grouping files `dto/AuthDtos.java`, `dto/UserDtos.java`, `dto/RoomDtos.java` and `dto/TaskDtos.java` keep related request/response classes together. `AuthDtos.ApiError` is the shared safe error shape. The grouping classes and response constructors do not create database tables.

**Housekeeping example.** An inspection DTO sends taskId/result/notes. RoomService supplies the authenticated inspector and recorded time. The form cannot impersonate a different inspector.

**Explain it aloud.**

- Why not return a User entity? It contains private fields and persistence relationships the API does not need.
- Why does DTO validation not prove a transition is legal? Legality depends on loaded current state and the caller.

## Services: check rules and keep related changes together

**Purpose and local words.** A service coordinates one use case. A terminal task is COMPLETED or CANCELLED. A managed entity is tracked by Hibernate in the persistence context. Atomic means related database changes commit together or neither does.

In [TaskService.java](../src/main/java/com/rockey/hospitality/service/TaskService.java), `task.start(); room.updateStatus(Room.Status.CLEANING);` changes two managed instances. The task/room came from repository lookups under the transaction, after active/role/ownership checks. The service executes these calls only when next=IN_PROGRESS, stored task=ASSIGNED and stored room=DIRTY. Repositories/Hibernate persist them in that transaction; neither entity method sends an HTTP response.

First `status` loads the active caller and rejects supervisor execution. `roomForTask` finds the room id, then locks the room; `lockedTask` locks the task next. Ownership and eligibility are checked against current objects. Now choose the valid transition, update both objects, save and map the DTO. Completion similarly stamps completedAt and moves CLEANING → INSPECTION, not READY. Supervisor cancellation of IN_PROGRESS work moves the room back to DIRTY; terminal history cannot be edited.

### What the transaction and locks do

Spring's transactional proxy wraps the public service call made through the managed bean. With the ordinary configuration here, uncaught RuntimeException/Error failures trigger rollback; not every checked exception automatically does. A private helper called from the same object is part of the enclosing transaction, not a new transaction simply because it is another method. `readOnly=true` is a persistence hint for reads, not a permission check or guaranteed database ban on writes.

When create/update assignment locks an assignee, the order is User → Room → Task (where a task row exists). Existing-task operations first read the room id, then lock Room before Task. Room guards lock Room and active task rows; account eligibility guards lock User and active task rows. Consistent ordering reduces competing-lock risks; do not turn this into a claim that every future path is deadlock-proof. Locks are held to transaction completion, not just the end of `findForUpdate`.

The room lock is the shared gate for task creation, task changes and inspection. Two concurrent creators must recheck active work while holding it. The guard query is a locking current read in this MySQL setup, so it is not relying on an older snapshot. The service check plus locking matters; there is no extra active-task table or placeholder workflow engine.

### Inspection and history

[RoomService.java](../src/main/java/com/rockey/hospitality/service/RoomService.java) calls `users.requireSupervisor(actorId)`, locks the room, checks active INSPECTION state and a completed task for that room, and checks that task is the latest completed one. It constructs `Inspection(room, task, supervisor, result, notes, time)`. That `supervisor` came from the authenticated caller. It appends the inspection and changes the room: PASS → READY; FAIL → DIRTY. Both writes belong to the same transaction.

After FAIL, the completed task and failed inspection stay history. Create a new cleaning task; do not rewrite the completed one. After another cleaning cycle a new inspection can record a PASS. Room/User deactivation retains rows and is blocked by active work; pending inspection also blocks room deactivation. There is no inspection edit/delete operation.

`service/UserService.java` handles Team eligibility/grants. It rejects ADMIN provisioning/changes through its API. `service/AuthService.java` owns account/session work and delegates password/token details. In-memory rate-limit counters remain outside database rollback, so an attempt does not disappear just because login's database transaction fails.

**Housekeeping example.** A valid completion cannot leave a COMPLETED task with a CLEANING room if the transaction fails halfway. The UI must still treat a lost HTTP response as uncertain: a database commit could have succeeded even though the client never received confirmation.

**Explain it aloud.**

- Why change the task and room in one service method? They represent one workflow step that must not partly commit.
- What is the inspector's source? The authenticated supervisor account, never a request-selected inspector.
- Why retain completed work after FAIL? It records what happened; rework is a new cycle.

## Repositories: ask for rows, not business permission

**Purpose and local words.** A repository is the database-access interface. A derived query uses its method name to describe the lookup. JPQL names entities/properties rather than raw table/column SQL. `Optional` makes a missing row explicit.

In [RoomRepository.java](../src/main/java/com/rockey/hospitality/repository/RoomRepository.java), `Optional<Room> findForUpdate(@Param("id") Long id)` binds the service's room id to `:id` in `select r from Room r where r.id = :id`. `r` is an alias and Room is the entity class. `@Lock(PESSIMISTIC_WRITE)` requests locking for that query inside the service transaction. The result is a locked Room or empty; the service decides whether to turn empty into 404. The caller's text is not pasted into JPQL.

First the service calls the Spring-generated repository implementation. Hibernate translates the fixed query/mapping to database work. MySQL provides the row and lock; Hibernate gives the service an entity. Later `save` participates in the transaction. An entity is not a response DTO just because it came from a repository.

`repository/TaskRepository.java` also has `findRoomId`, scoped/sorted task reads and locking active-work guards. `repository/UserRepository.java` has email lookup, uniqueness checks, Team scope queries and locked account/refresh queries. `repository/InspectionRepository.java` reads a room's append-only history newest id first; RoomService already holds the room workflow gate when appending a result. There is no business decision inside these method names.

**Housekeeping example.** `findByAssignedUserIdOrderByIdDesc(actorId)` fetches one worker's tasks when TaskService chooses USER scope. The service must choose the trusted actor id; a repository method name alone does not authenticate it.

**Explain it aloud.**

- Who writes the repository implementation? Spring Data supplies it from the interface/mappings.
- When does the lock end? At transaction completion, not when the repository returns.
- Is `save` always an INSERT? No; JPA persistence handles new and existing entities, with SQL timing managed by Hibernate.

## Entities and schema: map the stored facts

**Purpose and local words.** An entity class maps stored state. A foreign key references another table row. Many-to-one means many child rows can refer to the same parent; it does not require a Java collection on both sides.

In [Room.java](../src/main/java/com/rockey/hospitality/entity/Room.java), `@Enumerated(EnumType.STRING) ... private Status status = Status.READY;` says an instance starts with READY and Hibernate stores the enum name as text. The constructor may receive a permitted initial status from RoomService instead. The annotation maps storage; it does not define a legal transition or authenticate a caller.

First the service creates an instance or Hibernate reconstructs one using the protected empty constructor. `@Id/@GeneratedValue(IDENTITY)` connect the key to MySQL's generated id. `@Column` describes column mapping. The callbacks set createdAt/updatedAt when inserting/updating. A managed object changes in memory before its transaction completes; a getter does not query the full application or authorize a response.

`entity/Task.java` references one User and Room through `@ManyToOne`/`@JoinColumn`. `entity/Inspection.java` references Room, completed Task and inspecting User. LAZY means relationship loading can be deferred; services map required values inside the transaction because open-in-view is disabled. `entity/User.java` combines login identity and worker account; no separate Employee model exists.

The actual four tables come from [database/schema.sql](../database/schema.sql). Hibernate is configured to validate, not create/migrate the schema. Mapping annotations do not turn the older nine-table project into this four-table version. Use the setup guide's fresh-database procedure; do not import over existing/user data or pretend an incompatible schema will be silently upgraded. No database reset is part of studying these comments.

**Housekeeping example.** Deactivating a room changes active=false, not its id or historical task/inspection references. TaskResponse still maps its current room data; it is not a frozen snapshot of the room at task creation.

**Explain it aloud.**

- Is Room itself one room? It is the class; a Room instance carries one room's values.
- Do `@Table` and `@Column` run schema.sql? No; the reviewed setup imports it separately.
- Why not hard-delete a completed task? Inspection/history foreign keys and audit meaning must remain intact.

## Errors: show the failure without leaking internals

**Purpose and local words.** An exception interrupts normal execution. `exception/ApiException.java` groups specific runtime failure types; its outer namespace class is not itself the thrown failure. An error response is data explaining the failure safely, not a repair operation.

In [GlobalExceptionHandler.java](../src/main/java/com/rockey/hospitality/exception/GlobalExceptionHandler.java), `return response(HttpStatus.CONFLICT, exception.getMessage(), request, null);` maps a controlled ConflictException to a 409 ApiError. The status is chosen by the handler, the message came from our controlled rule, and the path comes from the servlet request. `null` means no field-error map for this branch. Do not use this explanation to expose arbitrary exception text; unexpected/persistence failures have separate generic mappings.

First a controller/service/validation failure reaches `@RestControllerAdvice`. `@ExceptionHandler` selects the matching method. Validation failures collect safe field messages; malformed JSON/types receive 400; duplicates/lifecycle conflicts receive 409. Unexpected failures get safe 500 text and controlled server logging. `security/SecurityHandlers.java` handles security-filter 401/403 with the same ApiError shape because those failures occur outside normal controller handling.

The frontend's `ApiError` in `frontend/src/services/apiClient.js` uses fixed presentation messages and allowlisted field keys rather than echoing arbitrary server/unknown-exception text. A 403 does not cause a refresh retry. A network/5xx write failure does not prove rollback.

**Housekeeping example.** Attempting to inspect a DIRTY room returns a lifecycle conflict, not a stack trace and not a partial inspection. The form can keep input while asking the user to reconcile current state.

**Explain it aloud.**

- Why have both security handlers and controller advice? They handle failures at different stages of the HTTP flow.
- Does a generic 500 mean the browser may safely repeat a write? No; the result may be unconfirmed.

## React entry, auth and routes: render the right workspace

**Purpose and local words.** A component returns UI from props/state. Context shares a provider value with descendants. A keyed subtree gets a fresh instance when its key changes. A route guard guides rendering; it is not backend authorization.

`frontend/src/main.jsx` creates the React root, then BrowserRouter → AuthProvider → App. [AuthProvider.jsx](../frontend/src/auth/AuthProvider.jsx) subscribes to `apiClient` through an effect, requests bootstrap and unsubscribes on cleanup. `auth/authContext.js` defines the safe snapshot/action contract; `auth/useAuth.js` reads it. The access token is not placed in Context.

Read `return <div key={auth.sessionKey}>{children}</div>;` in [AuthGuard.jsx](../frontend/src/routes/AuthGuard.jsx). The children came from App's route declaration. The session key came from apiClient's identity/session handling via the provider. When it changes, React remounts this private subtree instead of carrying the old user's drafts/data forward. The wrapper only renders after status/role checks; session checking/errors show SessionStatus and unauthenticated access redirects.

First the router matches a page. AppLayout supplies navigation and Outlet; AuthGuard selects checking/recovery/redirect/denial/content. Each protected page calls useAuth for the safe role/scope and makes its own permitted reads. `routes/routeDefinitions.js` supplies titles/menu metadata, while `App.jsx` declares actual routes. Neither a hidden link nor changing USER's display name to Housekeeper grants database permission.

**Housekeeping example.** A USER visits `/tasks` and gets their work; `/rooms` is supervisor-only. Even if someone manually sends a room API request, the backend rejects unauthorized access.

**Explain it aloud.**

- Where is the provider mounted? Above App in main.jsx, so layout, guards and pages can read it.
- Why use a session key rather than only a role check? A different identity or authorization/session scope must not inherit private page state.

## API helpers: send data, validate responses, reject old sessions

**Purpose and local words.** Fetch sends HTTP; JSON parsing is a separate awaited operation. An epoch labels a session generation. AbortSignal requests cancellation, but cancellation does not undo a server-side write. A runtime guard tests received values while JSDoc only informs the checker.

In [apiClient.js](../frontend/src/services/apiClient.js), `await fetch(\`${API_BASE_URL}${path}\`, { ...options, credentials: 'include', signal })` joins the public base from `services/config.js` with a service path. Options contain method/headers/body; credentials permits browser-managed cookies subject to browser policy. The signal combines session lifetime with an optional read cancellation signal. `await` suspends this async function until a response is available; the browser can process other work meanwhile. `readJson` parses separately, and epoch/scope checks reject responses from an old session.

First auth actions create a new epoch and explicitly allowlist login/register fields. The response's access token stays in the module-private variable. `establishSession` validates the response and requests `/auth/me` before publishing a safe account snapshot. `safeUser` tests current id/role/active shape and discards unknown fields. A protected `apiRequest` attaches Bearer, sends, and only on 401 may share a refresh and retry once with matching identity/scope. A 403 or uncertain network/server failure is not automatically replayed.

[housekeepingService.js](../frontend/src/services/housekeepingService.js) builds frozen domain paths/bodies and checks list response fields actually used by the UI. `checkedList(await read('/tasks', signal), taskShape)` first reads JSON, then requires an array of valid consumed shapes. The JSDoc cast to Task[] does not perform that check; the functions do. These checks are not a complete server schema/security validator. Save/action responses lead the page to reload authoritative lists rather than calculate new lifecycle state locally.

Auth changes abort lifetime-bound requests. Checks after awaits prevent late old-session data from becoming current data. Promise sharing coalesces refresh within this module/tab, not across tabs or devices. Refresh still rotates the backend's one stored session; storage is not used to coordinate auth or persist JWTs.

**Housekeeping example.** Clicking Complete uses `taskStatus` and the existing status operation. We do not invent a separate frontend-only READY change; the service returns INSPECTION and the next read renders it.

**Explain it aloud.**

- What does fetch resolution prove? A response arrived, not that JSON has already been parsed or the application operation was successful.
- Why check scope again after await? Identity/session may have changed while network work was pending.
- What belongs in a VITE variable? A public API address, never a backend signing key or database credential.

## Read hooks and pages: turn settled data into a render

**Purpose and local words.** An effect performs work after a render. Its dependency array identifies values that restart it. A callback can keep a function reference stable. A reducer returns the next state; it is not a database update.

In [useRead.js](../frontend/src/hooks/useRead.js), `const reload = useCallback(() => setRevision((value) => value + 1), []);` creates a stable reload function. A page calls it after a confirmed write or Retry. The functional setter uses the latest revision and schedules a render. Revision is an effect dependency, so the effect then runs the page's load function again. The line does not fetch synchronously or mutate returned rows.

First a page supplies its session/scope key and a stable loader. useRead starts an AbortController and marks that effect active. It calls load and schedules success/error state only if still active. Cleanup sets active=false and aborts. Key changes hide old data immediately; same-key refresh can keep loaded rows while showing refreshing. An error clears private data. These layers handle both transport cancellation and stale completion, not just one.

`pages/TasksPage.jsx` memoizes its loader with `[supervisor]`, fetching tasks and, for supervisors only, room/worker choices. Changing that value refreshes the loader; creating a new function each render would repeatedly retrigger an effect. The draft reducer uses `{ field, value }` or `{ reset }` to return new form data. Status filtering is local to the returned authorized list; it is not a new backend filter/pagination contract.

`pages/RoomsPage.jsx` adds a history loader keyed by selected room/mode and validates reconciliation against inspection history. `pages/TeamPage.jsx` uses listUsers and the existing role rules. `pages/DashboardPage.jsx` derives simple housekeeping counts with useMemo from the authorized arrays: USER's own tasks; supervisor tasks plus active rooms. There is no Analytics backend domain/endpoint here. “Today”/overdue use hotel time when calculated, not a new timed background polling service.

**Housekeeping example.** A same-session reload keeps row opener buttons mounted while the loading status announces refreshing. A switch to another account must not keep those private rows visible.

**Explain it aloud.**

- What restarts useRead? key/revision/load/keepPrevious dependency changes, not every unrelated input edit.
- Why both abort and an active flag? An obsolete completion must be ignored even if cancellation arrives too late.
- Is useMemo a source of permission? No; it derives display data from already scoped API results.

## Forms, writes and recovery: do not guess a commit result

**Purpose and local words.** A controlled input renders a value from React state and reports changes through a callback. A ref is immediate mutable local storage across renders. Reconciliation means read the server's current facts before deciding what to do after an uncertain write.

In [TasksPage.jsx](../frontend/src/pages/TasksPage.jsx), `void act(() => taskStatus(t.id, 'IN_PROGRESS'))` supplies a function, not its already-running result. `t` came from a validated list row; its id/status select the action. act checks submitting/mustReconcile before invoking the callback, updates pending state, awaits it, and reloads after confirmation. `void` discards the event handler's returned promise value; it does not make the operation synchronous or cancel it.

First submit calls `event.preventDefault()` to stop the form's native submit navigation. It does not disable every browser default behavior. Field values become the allowlisted JSON body, with numeric ids converted from input strings. `submitting.current=true` immediately guards another click; `setPending(true)` schedules the disabled/busy render. Success closes the dialog and reads lists again. Known validation/conflict errors preserve inputs and show safe feedback. Stale-session errors do not update a replacement session's view.

A lost response/network/5xx can mean a write committed but its acknowledgement was lost. The pages set mustReconcile/uncertain, block further writes and ask for Refresh before retrying. A failed refresh does not unlock them. RoomsPage additionally reads selected inspection history; if the completed task already has its inspection, it switches to history rather than inviting the same write again. We do not silently retry, invent idempotency keys or claim the server rolled back.

`pages/auth/AuthPage.jsx` uses the same state/ref distinction, plus an active ref to avoid unmounted feedback and a focus effect for errors. It clears password input after completion. Native/client validation improves feedback; backend validation, eligibility and byte-limit checks remain authoritative.

**Housekeeping example.** The supervisor records PASS, but the connection fails. Refresh room/tasks/history first: if history contains that task's inspection and the room is READY, show that recorded outcome instead of resubmitting.

**Explain it aloud.**

- Why not just disable a button with state? Before the next render, another event may still arrive; the ref changes synchronously.
- Why does 500 not mean “safe to retry”? The UI has not proven whether persistence succeeded.

## Shared UI, focus and theme: keep the view usable

**Purpose and local words.** Props are values/callbacks supplied by a parent. A ref can point to a DOM element. Effect cleanup releases a prior effect's resources when it ends, including on dependency changes or unmount. It is not only a page-close event.

In [Modal.jsx](../frontend/src/components/ui/Modal.jsx), `dialog.showModal();` opens the native dialog referred to by dialogRef. isOpen came from RoomsPage/TasksPage/TeamPage state. The effect remembers document.activeElement before opening; cleanup removes its close listener, closes the native dialog if needed and restores focus. A title id connects the accessible heading; Tab handling keeps focus among usable dialog controls. No network request is made by Modal.

The page's onClose can refuse closing while pending. The native close listener reopens an unexpectedly browser-closed dialog while controlled isOpen remains true, preserving the save-in-progress fix. We restore to an available original opener; otherwise fallbackFocusRef points to the page's stable heading (`tabIndex=-1`, focusable programmatically). A MutationObserver handles opener removal after a later background refresh and stops when focus moves or fallback is needed. Keeping rows mounted is useful, but cannot guarantee every opener will survive a real mutation/filter change.

`components/ui/Button.jsx` renders native disabled/busy behavior; Input/Select connect labels and field errors; Card presents supplied content; Table calls column render callbacks and supplies caption/headers/empty text/keyboard scroll region. ErrorNotice announces a safe message and invokes a parent's explicit Retry. Spinner provides status text; Skeleton is decorative. None of them performs business authorization or owns API data.

`components/layout/AppLayout.jsx` owns navigation, title, theme and main focus. Its pathname effect moves focus only on an actual route change, not initial mounting or every auth render. The main landmark, skip link, field labels and dialog fallback serve different navigation needs.

`utils/theme.js` reads/writes only the non-sensitive `rockey-theme` preference. AppLayout state applies `document.documentElement.dataset.theme` and `style.colorScheme`; `styles/index.css` selects light/dark variables and logo visibility. A denied storage operation does not block changing the current page theme. CSS provides visible focus, responsive layout/table scrolling and reduced-motion spinner behavior. This allowed preference storage is not permission to put JWTs in localStorage/sessionStorage. See [THEME_SYSTEM.md](../frontend/THEME_SYSTEM.md) for unique theme details and testing limits.

**Housekeeping example.** Close Edit room during a background read: focus returns to its usable opener. If that control genuinely disappeared, RoomsPage's heading becomes the logical fallback rather than leaving focus on document body.

**Explain it aloud.**

- Who decides a dialog can close? The parent controls isOpen/onClose; Modal coordinates native behavior and focus.
- Is a heading with tabIndex=-1 in the regular Tab sequence? No, but code can focus it as a logical destination.
- What can persist in browser storage? The light/dark preference only, not auth/session secrets.

## Put the whole housekeeping cycle together

First a supervisor marks an available room DIRTY and creates a task for an active USER. TaskService locks/checks assignment and room state and prevents a second active task for that room. Assignment itself leaves the room DIRTY.

Next the assigned USER starts their ASSIGNED task: task becomes IN_PROGRESS and room becomes CLEANING. Only that USER executes it; MANAGER/ADMIN cannot step in through a hidden button or direct status request. After cleaning, the same USER completes: task becomes COMPLETED, completedAt is stamped and room becomes INSPECTION. Completion is not proof that inspection passed.

Now the authenticated MANAGER/ADMIN records an inspection for the latest completed task. PASS gives READY; FAIL gives DIRTY. On FAIL, create new work, optionally for a different active USER. Preserve the completed task and both inspection outcomes across cycles. Deactivation changes eligibility while retaining references, and active-work/pending-inspection guards prevent invalid gaps.

The first ADMIN is a separate reviewed local setup action: register a USER, stop the backend, privately fill the ignored copy of [bootstrap-admin.sql.example](../database/bootstrap-admin.sql.example), run it in the fresh selected database, then sign in again. It promotes one active USER only if no ADMIN exists and clears refresh state. No default password or privileged public bootstrap endpoint exists. This explanation does not authorize running that SQL against another database.

**Explain it aloud.**

- Where does DIRTY → CLEANING happen? TaskService's valid USER start transaction, not the room form.
- Where does INSPECTION → READY happen? RoomService.inspect after a recorded PASS.
- If two people try to create work together, what matters? The shared room lock plus the current active-work check inside the transaction.
- If a browser loses a successful response, what do we trust next? A fresh authorized list/history read, not guessed local state.

Use the questions to trace a value or state change in the actual file. A good explanation can say both what a line does and what it deliberately leaves to the next layer.
