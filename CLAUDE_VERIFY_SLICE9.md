# Claude Code Slice 9 Final Verification — Alerts and Automation

## Codex Handoff Reviewed
`PHASE2_IMPLEMENTATION_LOG.md` (Slice 9 section). Claims were checked against the repository source, not taken as-is.

## Environment Deviation (disclosed)
No JDK 17 is installed on this machine. Verification ran on Eclipse Temurin 25.0.4 (`JAVA_HOME` set for the command only). The pom targets Java 17, so this is not a Java 17 runtime run. The earlier Slice 2A–8 runs used a Codex-cached Zulu 17, which is no longer present. Results should be re-confirmed on JDK 17 when available.

## Git State
Repository is not a git repository (no commits, no diff available). Changes were identified from the Slice 9 log file list and by inspecting the Alert source files directly. Nothing committed by this review.

## Specification Mapping
- FR-22 / BR-21 — `AlertAutomationService.scanRooms`: `Room.nextArrivalAt` within [now, now+2h] (inclusive), room active and not READY, one unresolved ROOM alert per active Housekeeping recipient. Past or missing arrivals are excluded. Verified in source and repository query `RoomRepository.findReadinessAlertSources`.
- FR-33 / BR-31 — `scanTasks`: assigned, non-terminal tasks; strict overdue (`dueAt < now`) and HIGH/URGENT priority use separate stable keys `TASK:<id>:OVERDUE` and `TASK:<id>:HIGH_PRIORITY`. Verified via `TaskRepository.findTaskAlertSources`.
- FR-46 / BR-41 — `scanInventory`: active items with `quantity <= reorderThreshold` (inclusive). Recipients are active Purchasing Employees; if none, active ADMIN Employees. Key `INVENTORY:<id>:AT_OR_BELOW_THRESHOLD`.
- FR-47 — STAFF lists and reads only its own alerts (`AlertService.listAlerts`, `ensureOwner`); ADMIN may list for oversight and filter by `employeeId`.
- FR-48 — recipient marks UNREAD → READ (`markRead`); DELETE records RESOLVED (`resolveAlert`); repeated READ or resolution returns 409. ADMIN may oversee but `markRead` is limited to its own alert.
- FR-49 — `AlertResponse` carries type, severity, status, Employee summary, optional Task summary, safe message, sourceKey, createdAt, readAt, resolvedAt. Rows are never physically deleted.
- FR-06 (supporting) — `SecurityConfiguration`: `GET /api/alerts/**` and `PUT /api/alerts/*/read` and `DELETE /api/alerts/*` → STAFF/ADMIN; all other `/api/alerts/**` → `denyAll()`. USER is denied.
- BR-11 (Alert-owned portion) — `reconcile` only generates for ACTIVE Employees in an active Department; alerts for inactive or former recipients are resolved automatically. Verified in source; covered by automation tests.
- BR-06 / BR-30 (supporting) — backend enforces roles and ownership; overdue definition (dueAt past, status not COMPLETED/CANCELLED) matches `TERMINAL` set.
- BR-42 — every Alert has a required Employee (`optional = false`, `employee_id NOT NULL`); `task_id` nullable and set only for Task alerts.
- BR-43 — DELETE resolves and retains the row (`Alert.resolve`, no delete call); default lists exclude RESOLVED; explicit status filter and ID retrieval preserve history.
- BR-44 — `@Transactional` on all write paths; `findByIdForUpdate` on Employee and Alert; `findUnresolvedForUpdate` serializes per-recipient reconciliation. Rollback behaviour and real lock behaviour require MySQL (see Deferred).
- Automation Boundary (13_BUSINESS_RULES.md) — in-application alerts only; no email, SMS, purchasing, staffing, task, or room changes. Verified by source inspection: no such calls exist in Alert code.

## Endpoint Verification (all 4, contract #42–45)
| # | Endpoint | Contract | Implementation | Test evidence |
|---|---|---|---|---|
| 42 | `GET /api/alerts` | STAFF own, ADMIN; 200 PagedResponse | `AlertController.listAlerts`, `AlertService.listAlerts` | `AlertControllerTest`, `AlertServiceTest` |
| 43 | `GET /api/alerts/{id}` | Recipient STAFF, ADMIN; 200 | `getAlert`, ownership check for STAFF | `AlertControllerTest`, `AlertServiceTest` |
| 44 | `PUT /api/alerts/{id}/read` | Recipient STAFF; ADMIN for own alert; 200 | `markRead`, 409 unless UNREAD | `AlertServiceTest` |
| 45 | `DELETE /api/alerts/{id}` | Recipient STAFF, ADMIN; 204 | `resolveAlert` → `ResponseEntity.noContent()`, RESOLVED, no delete, 409 if already resolved | `AlertControllerTest`, `AlertServiceTest` |

Notes on interpretation (not defects):
- `resolveAlert` lets ADMIN resolve any alert, consistent with FR-48 "ADMIN may oversee alerts under the same lifecycle rules".
- `readAt` is set when an alert is resolved while still UNREAD. This matches Data Dictionary wording: "Set when entering READ/RESOLVED" (`readAt` = first read/transition time).

## Automation Verification
- Single combined scan `AlertAutomationService.runChecks` runs rooms, tasks, and inventory in one transaction. Scan failures roll back.
- `AlertScheduler.scan()` uses `@Scheduled(fixedDelayString = "${rockey.alerts.scan-delay-ms:300000}", initialDelayString = same)`. Default 300000 ms (5 minutes); externally configurable via `ROCKEY_ALERT_SCAN_DELAY_MS` in `application.properties`. Failures log only the exception class name.
- `@EnableScheduling` is isolated in `AlertSchedulingConfiguration`.
- Idempotence: `reconcile` keeps an existing unresolved alert with the same source key and resolves duplicates. Re-running the scan without changes creates no rows (covered by automation tests).
- Automatic resolution: when a source condition clears, the alert becomes RESOLVED and the row is retained.
- Recurrence: after resolution, a true condition can create a new row with the same canonical sourceKey; unresolved duplicates are suppressed.
- Inactive recipients: no new alerts; existing derived alerts resolve.
- Recipient lookups use `findActiveRecipientIdsByDepartment` and `findActiveAdminRecipientIds` (department/status filters in JPQL).
- Clock: production uses the injected `Clock`, zoned to system default to match existing server-local `LocalDateTime` semantics. The authentication Clock is unchanged.
- Test hygiene: no `Thread.sleep`, `TimeUnit`, or Awaitility usage in any test file (grep confirmed). Test Clock fixtures were not individually re-read; the claim that tests use a controllable Clock is accepted from the log and not independently verified line by line.

## Dependency, Scope, and Secret Audit
- `pom.xml`: 13 artifact entries, unchanged from Slice 8 (no new dependency; scheduling is part of `spring-boot-starter`).
- No Analytics code: no `*analytic*`, `*dashboard*`, or `*report*` file in `src`.
- No hardcoded credentials in `src/main` (pattern scan). No secret values in repo docs. The disposable `ROCKEY_JWT_SECRET_BASE64` used for test runs was passed on the command line only and is not written to any file.

## Build and Test Verification
- Focused run (Alert + SecurityConfiguration): `AlertServiceTest` 28, `AlertAutomationServiceTest` 44, `AlertControllerTest` 12, `AlertSchedulerTest` 4, `SecurityConfigurationTest` 46 = **134/134 passed**. These per-class counts match Codex's final breakdown exactly.
- Full regression (`mvnw package`, run once, no `clean`): **413 tests, 24 classes, 0 failures, 0 errors, 0 skipped**. Executable JAR `target/rockey-hospitality-0.0.1-SNAPSHOT.jar` produced. BUILD SUCCESS.
- Arithmetic check: 413 − 321 baseline = 92 new tests, matching Codex's log ("92 additional cases").
- Prior 321 tests remain green (413 total minus the 92 Alert/security additions passing with 0 failures).

## Defects Found
None.

## Fixes Applied
None required.

## Ambiguities Noted (no action taken)
- Inventory fallback: Codex treats "active ADMIN" as active ADMIN-account Employees with an active Department. An ADMIN user without an Employee profile cannot receive alerts, since BR-42 requires an Employee owner. Consistent with the model; recorded for awareness.
- Room readiness "within two hours" is implemented as inclusive [now, now+2h]. Matches the spec's wording; boundary cases are covered by the log's nanosecond boundary test.

## Deferred Requirements
- Live MySQL schema validation (`ddl-auto=validate` against `alerts`), application startup, Postman execution (23 requests in `Rockey-Alert.postman_collection.json`, not re-run here), and real pessimistic-lock/rollback behaviour under concurrency: environment-blocked. No credentials were available and none were invented. The log's rollback and lock claims rest on mocked and Spring-advice tests.
- Analytics (FR-50/FR-51 dashboards and aggregation) remains a later slice. No Analytics code exists.
- Frontend, AWS, and CI/CD are outside this slice.

## Definition of Done
Met for the implemented and tested Alert and Automation scope. Not fully met at whole-application level because of the environment-blocked live verification above.

## Token / Usage Efficiency
- Targeted reads only: Alert service/automation/scheduler/configuration/entity/controller/repository, Room/Task/Inventory/Employee alert queries, security alert rules, schema alerts table, application.properties, and the spec rows (FR-06/22/33/46–49, BR-06/11/21/30/31/41–44, Automation Boundary, API #42–45, Data Dictionary `readAt`).
- Reused Slice 8 facts for the pom, the absence of analytics files, and the secret scan baseline; re-checked them cheaply.
- Commands: one focused run, one full `package` run. No `clean` (known OneDrive lock issue).
- Not run: live startup, DB, Postman.

## Final Verdict
**VERIFIED WITH DOCUMENTED DEFERRED REQUIREMENTS**
