# Sonar Critical/Major remediation and reviewed exceptions

Date: 2026-10-05. Project key: `Rockey-Hospitality`. Human-authorized backend hardening only.

## Baseline and accounting

Baseline: 148 unresolved issue instances across 18 rules; 7 vulnerabilities, 18 Critical and 40 Major. Vulnerability type overlaps severity: 58 distinct Critical/Major issues, not 65. Baseline combined Sonar coverage was 92.7%; Quality Gate passed, but that alone does not satisfy the official requirement to address Critical/Major findings and leave no known vulnerabilities.

Codex changed no Sonar issue status, quality profile, threshold, exclusion or suppression. Human UI dispositions were subsequently verified by the final Sonar-only recheck below. Minor/Info findings remain deferred, except S2143's explicitly approved acceptance; its code is unchanged.

## Implemented fixes

- S1186: remove ten redundant explicit no-argument request DTO constructors; implicit public construction and Jackson fields/validation remain unchanged.
- S1192: reuse existing Role enum names in the same security matcher order; local constants preserve analytics parameter names and Employee error text. Analytics JPQL structure is unchanged.
- S3776: private OpenAPI normalization/auth-documentation helpers; private per-recipient alert reconciliation. The caller's transactions, sorted recipient/condition order, Employee lock before unresolved Alert locks, deduplication, clearing and history remain unchanged.
- S107: internal Task/Room/Alert search criteria and pagination records group cohesive arguments. Service/repository callers and mocks pass the same values; controller parameters/defaults/response DTOs and the 51-operation contract remain unchanged. Task repository criteria fields are evaluated into bound JPQL parameters, never interpolated query values. Identity/role stays separate and service-owned.
- S5778: setup/getters/request factories outside exception lambdas. The intentional linked-update failure remains inside TransactionTemplate; both rollback rows and the intended failure message are asserted.
- S5785: direct refresh-token inequality using test-only wrappers with value equality and redacted toString; failed assertions cannot disclose raw token values.
- S6213: private record renamed recordAttempt; limits/windows/concurrent map semantics unchanged.
- S9142: compile the identical contract-row regex once outside its loop.
- S4502 compensating control: exact configured allowed-origin validation before refresh service/rotation; reject unapproved, empty, opaque-null, combined or duplicate Origin headers. Missing Origin remains allowed for non-browser compatibility by explicit human decision.

## Reviewed/accepted candidates — no API status mutation

### S4502 — securityFilterChain CSRF disable

Human-approved REVIEWED/ACCEPTED candidate with compensating controls, subject to test evidence. Bearer-only operational APIs remain stateless and unchanged; no global CSRF enablement and no new CSRF-token endpoint. Cookie refresh has its own exact Origin validation using SecurityProperties.allowedOrigins, also used by CORS. Requests without Origin are accepted only as the approved non-browser compatibility behavior; this is not a claim that arbitrary cookie authentication is immune to CSRF.

Evidence: AuthControllerTest covers existing absent-Origin success, trusted default/configured origin, production Secure flag, HttpOnly/SameSite/path retention, seven unapproved/malformed origins and duplicate headers; service must not be called for rejected requests. BackendContractTest exercises actual security/CORS rejection of simple form POST refresh requests from foreign, same-site-unapproved and opaque origins. Existing JWT, rotation, replay, logout, rate-limit and ownership regression must remain green.

Residual deployment assumptions: preserve browser Origin headers at any future proxy; maintain exact approved origins and appropriate production Secure/SameSite configuration. Cookie-policy or client-contract changes need separate review. No reproducible browser Origin bypass is claimed found; no frontend/TLS/deployment readiness is claimed.

### S2077 — six AnalyticsRepository findings

Reviewed false-positive/accepted candidates: each optional query fragment is a fixed application-controlled literal. User IDs, Department IDs, Employee IDs, floor and time values are supplied through named setParameter binding. taskScope returns only fixed clauses; lowStock selects only a fixed predicate. No request value is concatenated into JPQL, no query fragment is accepted from callers, and aggregates remain read-only.

The stable query implementation is preserved as explicitly requested. AnalyticsRepositoryTest verifies scoped SQL/query shape and parameter binding; AnalyticsServiceTest covers counts, role/identity scope, filters and empty data; HardeningMySqlTest.allPersistedAnalyticsReconcileWithIndependentSql reconciles database aggregates against independent SQL. The S1192 statuses binding constant does not rewrite these queries. Do not claim scanner findings are closed until human UI disposition and a follow-up read confirm it.

### S107 — eleven contract/domain exceptions

Nine response constructors represent approved flat immutable wire fields, including distinct STAFF/ADMIN dashboard projections and Task compatibility constructor. Removing fields, changing JSON nesting or adding builders solely for a metric would violate the approved narrow scope. Reviewed contract-shaped exceptions are preferable to broad DTO redesign.

Task's eight-argument constructor and ten-argument update describe one cohesive validated domain state, including nullable operational relationships and completion timestamp. Preserve atomic validated assignment/update rather than split mutation merely to meet a parameter threshold. These two are approved cohesive-domain-state exceptions.

Six internal service/repository parameter-list findings are fixed, not accepted: TaskRepository.search; AlertService.listAlerts; RoomService.listRooms; TaskService.listTasks/listAssignedTasks/search. Do not raise the rule limit.

### S2143 — JJWT boundary (Info)

Explicit human-approved acceptance rationale: business time calculations use Instant/Duration/Clock. Pinned JJWT 0.13.0 issuedAt/expiration/parser Clock interfaces require java.util.Date adaptation. Keep conversion at that library boundary; no dependency upgrade or fake time replacement. JwtServiceTest and Auth regression remain required. All other Minor/Info work is deferred.

## Exact issue keys and verified human UI dispositions

All following **18 Critical/Major candidates plus one approved Info candidate** retain the human dispositions in the successful final recheck: S4502 **1 Accepted**, S2077 **6 False Positive**, S107 **11 Accepted**, S2143 **1 Accepted**. None remains open. Acceptance records the documented exception/compensating controls; it does not mean the underlying code was removed or that security risk is zero.

| Rule | Severity | Issue key | Current file:line |
|---|---|---|---|
| java:S4502 | CRITICAL | 3ae03d2d-a12e-4c06-9374-6bfb5c541b65 | src/main/java/com/rockey/hospitality/configuration/SecurityConfiguration.java:43 |
| java:S107 | MAJOR | f2ed08b4-1e39-4814-9f56-ab3c77c069c4 | src/main/java/com/rockey/hospitality/dto/alert/AlertResponse.java:22 |
| java:S107 | MAJOR | 18c585b8-7488-4437-98dd-1b6399894b75 | src/main/java/com/rockey/hospitality/dto/analytics/DashboardResponse.java:47 |
| java:S107 | MAJOR | 3a08056e-2cf5-4f14-b07b-9bd2063cd50c | src/main/java/com/rockey/hospitality/dto/analytics/DashboardResponse.java:82 |
| java:S107 | MAJOR | 9389d1b6-4e31-4193-8fac-234141b726ff | src/main/java/com/rockey/hospitality/dto/employee/EmployeeResponse.java:20 |
| java:S107 | MAJOR | 5709e55f-e7fc-4da6-b253-22415fc385f0 | src/main/java/com/rockey/hospitality/dto/event/EventResponse.java:23 |
| java:S107 | MAJOR | 12cbf3f2-3dce-44c3-bed9-4a7c286da105 | src/main/java/com/rockey/hospitality/dto/inventory/InventoryItemResponse.java:19 |
| java:S107 | MAJOR | 9ed74c99-1421-4dfc-8ac5-14052f4da12e | src/main/java/com/rockey/hospitality/dto/room/RoomResponse.java:19 |
| java:S107 | MAJOR | 9679e8b0-9b91-4203-8de2-463b2713a227 | src/main/java/com/rockey/hospitality/dto/task/TaskResponse.java:25 |
| java:S107 | MAJOR | 0e1d9629-a1b9-432d-aaa3-fce3b9ac9124 | src/main/java/com/rockey/hospitality/dto/task/TaskResponse.java:55 |
| java:S107 | MAJOR | fd786266-5f36-4154-9c19-d1057b8cf404 | src/main/java/com/rockey/hospitality/entity/Task.java:95 |
| java:S107 | MAJOR | 51cc9762-d6d6-468e-bfcf-84bad652c17e | src/main/java/com/rockey/hospitality/entity/Task.java:155 |
| java:S2077 | MAJOR | 4c8c0a01-2a8c-4400-919b-c58dd031dc61 | src/main/java/com/rockey/hospitality/repository/AnalyticsRepository.java:24 |
| java:S2077 | MAJOR | 4c0cd258-d06a-4ae2-8d97-06c0103b868d | src/main/java/com/rockey/hospitality/repository/AnalyticsRepository.java:32 |
| java:S2077 | MAJOR | 7ceea869-f4d7-4358-8c0c-f9f7fd92c68a | src/main/java/com/rockey/hospitality/repository/AnalyticsRepository.java:41 |
| java:S2077 | MAJOR | 5c83a9b7-9ee2-4a3e-916b-f5dbb7269300 | src/main/java/com/rockey/hospitality/repository/AnalyticsRepository.java:49 |
| java:S2077 | MAJOR | 3e422173-b521-4a14-867a-d654daa97187 | src/main/java/com/rockey/hospitality/repository/AnalyticsRepository.java:60 |
| java:S2077 | MAJOR | 8ceed859-c9d7-498d-a564-79cada9b0dce | src/main/java/com/rockey/hospitality/repository/AnalyticsRepository.java:75 |
| java:S2143 | INFO | 0f99b022-339b-4020-98a9-7f73c64bb278 | src/main/java/com/rockey/hospitality/security/JwtService.java:file-level |

The 19 exact issue keys above were checked through the read-only Sonar API after reanalysis. Total: **13 Accepted, 6 False Positive**; no issue-status API transition or automatic acceptance was performed by Codex.

## Verification evidence

Final recheck ran the pinned Sonar Maven goal only, importing the existing final JaCoCo report. Builds, tests, live MySQL and Postman were not rerun; their passing evidence below is retained from the completed remediation regression. Protected source/test/POM/wrapper files were SHA-256 fingerprinted before and after the scan and remained unchanged; Git status was unchanged.

| Check | Final evidence |
|---|---|
| Java / Maven | Azul Zulu 17.0.20.1+1 for compile/test/package/startup; Maven Wrapper 3.9.16; source/release 17 unchanged |
| Focused security/auth | 91 passed; original JWT/rate-limit/security tests plus refresh Origin cases |
| Focused Analytics/live reconciliation | 55 passed, including independent persisted SQL counts |
| Focused DTO/controllers | 34 passed |
| Focused Task/Room/Alert filters/security | 176 passed |
| Focused automation/OpenAPI/auth | 74 passed |
| Focused affected services/live hardening | 138 passed, including all 11 real MySQL tests |
| One complete final Java17/live verify | **561 tests, 0 failures, 0 errors, 0 skips**, 31 classes; all 545 baseline cases preserved plus 16 cases (10 Origin/controller, 3 actual CORS/simple-refresh, 3 OpenAPI) |
| Department regression | **22/22** (controller 9 + service 13) |
| JaCoCo | Line **2646/2772 = 95.45%**; branch **660/788 = 83.76%**; instruction **10917/11431 = 95.50%**; all 119 production classes included, no exclusions; required 70% and target 80% line coverage pass |
| Packaged live Postman | **51/51 operations; 116 requests; 291 assertions; 0 failures**; live CORS/security headers pass |
| Sonar analysis | **SUCCESS**; final recheck analysis ID `a6d6bf88-d354-49b0-966d-4dc335a25809`; project `Rockey-Hospitality` |
| Quality Gate | **PASSED**; new coverage **99.0%**, new duplication **0.0%**, new violations **0** |
| Sonar coverage import | One final JaCoCo XML report imported; combined **92.9%**, line **95.5%**, branch **83.8%** |
| Sonar remaining open | **89 total**: **0 vulnerabilities**, **0 Critical/High**, **0 Major/Medium**, Bugs **0**, Security Hotspots **0**; **64 Minor + 25 Info** code smells remain deferred; no open HIGH/MEDIUM software-quality impact |
| Critical/Major reconciliation | **40 fixed** (17 Critical + 23 Major); the other **18** are verified approved S4502/S2077/S107 dispositions, not code fixes; no unexpected Critical/Major issue |
| Human UI dispositions | **19 verified**: S4502 x1 Accepted; S2077 x6 False Positive; S107 x11 Accepted; Info S2143 x1 Accepted. **13 Accepted + 6 False Positive**; no status API mutation by Codex |
| Scope/security | No dependency/POM/schema/public-API/business-rule change; no suppression, threshold or exclusion change; no Minor/Info cleanup, commit or push |

Ignored local evidence: `target/sonar-remediation-security.log`, `-analytics.log`, `-dto.log`, `-filters.log`, `-automation.log`, `-live.log`, `-final-verify.log`, `-postman.log`, `-analysis-final.log`; `target/site/jacoco/jacoco.xml`; `target/surefire-reports/`; sanitized `target/hardening/api-results.json`. Never publish the whole target directory.

Final Sonar-only recheck evidence: `target/sonar-final-recheck.log` (one JaCoCo report imported, Quality Gate PASSED, BUILD SUCCESS); scanner report `C:\Users\hoese\AppData\Local\RockeyHardening\sonar-work\final-recheck-20261005-140657\report-task.txt`. Sonar server 26.9.0.129388, Maven 3.9.16, pinned scanner 5.8.0.7211 and Temurin 25 runtime; verified JDK17 supplied as `sonar.java.jdkHome`. The private token was read only from environment, was absent from the sanitized scan log, and `.env` stayed Git-ignored. No database access or dependency/POM change was needed. Admin-only health API access was unavailable to the analysis token; public server status was UP and analysis/Quality Gate/project APIs verified successfully, without escalating privileges.

Scanner/runtime only: pinned Maven scanner 5.8.0.7211 and installed Temurin 25 run Sonar, with sonar.java.jdkHome pointing to the verified JDK17. The first attempt hit AccessDeniedException in the old OneDrive scanner temp directory; the workspace retry initially had a PowerShell argument-array quoting error. Both were corrected without source/POM changes, test reruns, deletes or permission changes. Final scanner workspace: `C:\Users\hoese\AppData\Local\RockeyHardening\sonar-work\critical-major-20261005-1327`. Working-directory configuration is a documented scanner analysis parameter, not an analysis exclusion: https://docs.sonarsource.com/sonarqube-server/analyzing-source-code/analysis-parameters/parameters-not-settable-in-ui

API runner terminated only its own Java17 child; port 18081 is no longer listening. Temporary fixtures, runtime Postman environment and raw response report were removed by the runner. Git-ignored .env remained private; Sonar token came from private environment only, never command arguments/files/docs. Scan logs redact the private token. MySQL work remained pinned to rockey_hospitality_hardening.

**Recommendation: PASS TO CLAUDE FINAL BACKEND RECHECK.** Human dispositions are verified and no unresolved vulnerability/Critical/High/Major/Medium finding remains. The 89 Minor/Info findings remain deferred. This is not an overall capstone/deployment or zero-risk claim. No further slice, frontend, deployment or Git publication is authorized.


## Scope preserved

No public fields, operations, enum/lifecycle rules, dependencies, schema/table changes, Sonar exclusions or thresholds, Minor/Info cleanup, frontend, AWS/CI/CD, commit or push. No historical specification rewrite.
