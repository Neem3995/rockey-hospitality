# Rockey — Housekeeping

Rockey is an internal hotel housekeeping application. **USER** is a housekeeper (shown as “Housekeeper” in the UI), **MANAGER** is a housekeeping supervisor, and **ADMIN** has account oversight. USER remains the backend role to preserve the rubric's USER/ADMIN requirement.

Exactly **four entities/tables**: User/users, Room/rooms, Task/tasks, Inspection/inspections.
Department, Employee, Event/registration, Inventory, Alert and Analytics domains are removed.

## Workflow

DIRTY → assigned Task → USER starts → CLEANING → USER completes → INSPECTION.
MANAGER/ADMIN records **PASS → READY**, or **FAIL → DIRTY**, followed by another cleaning task.
Inspections retain the room, completed task, inspecting user, result, notes and time. Cancellation/deactivation preserves history.

## Stack

Java 17; Spring Boot 3.5.16; Spring Web/Data JPA/Validation/Security; MySQL; BCrypt; JJWT 0.13.0; Maven Wrapper; Springdoc 2.8.17; JUnit 5/Mockito/MockMvc; JaCoCo 0.8.14.
React/Vite; checked JavaScript/JSDoc; React Router; native fetch; reusable Button/Input/Card/Modal/Table.
No new dependency was introduced.

## Run, study and verify

- [Fresh local setup and first ADMIN](docs/LOCAL_SETUP.md)
- [Model, permissions, API and study guide](docs/BACKEND_GUIDE.md)
- [Security and quality evidence](docs/SECURITY_AND_QUALITY.md)
- [Frontend routes and setup](frontend/README.md)
- [Generated OpenAPI snapshot](docs/rockey-openapi.json)
- [Postman workflow collection](postman/Rockey-Housekeeping.postman_collection.json)

**24 business API operations**. ADMIN-only /v3/api-docs and /v3/api-docs.yaml are documentation tools, not extra business operations.
The previous nine-table/51-operation plan describes earlier branches, not this authorized housekeeping-only version.

Public registration creates USER only. Backend role/ownership checks are authoritative.
Access JWTs stay in browser memory; refresh tokens stay in HttpOnly cookies, with hash-only rotation/revocation on User.
No credentials/auth tokens are stored in localStorage/sessionStorage. Only a non-sensitive theme preference persists.

Lists return complete arrays, not pagination wrappers. Dashboard counts use authorized lists, not a separate analytics domain.
Maven verify enforces **≥70% production line coverage** (≥80% target), without exclusions.
Opt-in live checks reset only disposable rockey_hospitality_hardening; skips are not passes.
AWS/CI-CD, deployment, presentation and remaining submission gates are separate, not implemented or waived here.

## Current local checks

151/151 backend tests (including 12 live MySQL), 149/149 frontend tests, 24/24 operations via 62 Postman requests/139 assertions; no failures. JaCoCo line 95.11%; frontend line 97.34%; lint/typecheck/build passed. Independent Claude review, fresh Sonar and real-browser UI checks remain separate; see the security/quality guide for limitations.
