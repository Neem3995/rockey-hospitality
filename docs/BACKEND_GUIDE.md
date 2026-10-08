# Housekeeping backend study guide

## Approved implementation plan

Keep USER/MANAGER/ADMIN and exactly four related core tables, retaining the four-table Excellent ERD target.
Order: entity/schema → repositories → auth/team/room/task services → DTOs/controllers/security → task/inspection workflow → six React routes → focused tests, fresh MySQL/API, coverage and docs.

React → Controller → Service → Repository → Entity → MySQL.

Controllers validate DTOs and accept the trusted principal. Services enforce permissions, transitions and transactions.
Repositories contain ordinary JPA lookups and row locks. Entities are stored rows, never controller responses.
There are four flat DTO grouping files and four controllers/services. Minimal Inspection behavior stays in RoomService/RoomController/RoomDtos; no separate inspection service, controller, engine, analytics or alerts.

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

Mutations lock assigned User when needed, then Room, then Task. Active-work guard queries are **locking current reads**, not repeatable-read snapshot existence queries. Live races prove this avoids duplicate active work. Task/Room and Inspection/Room changes share transactions.

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
No JPA entity, credential/hash/session state is returned.

Errors: 400 validation/JSON/id parsing; 401 invalid/missing/inactive/mismatched auth; 403 role/ownership/Origin; 404 missing; 409 duplicate/lifecycle/active-work; 429 auth rate limit; safe 500 unexpected.
USER cancellation through status is an invalid transition (409); DELETE cancellation is supervisor-only (403).
ApiError: timestamp/status/error/message/path/fieldErrors, no secrets/stack trace.

## Time and study notes

JWT/refresh expiry uses Instant/Clock. Operational LocalDateTime is offset-free hotel wall-clock time; run JVM in America/New_York. UI does not relabel it UTC.
“Completed today” uses that hotel date; overdue means dueAt < now and non-terminal status.
Every production Java file has STUDY NOTE guidance for its layer/annotations/collaborators. Start with Room/Task/Inspection and their services.

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
