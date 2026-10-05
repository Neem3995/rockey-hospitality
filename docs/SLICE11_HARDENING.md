# Slice 11 — Backend Hardening and Final Backend Gate

Date: 2026-10-05. Workflow: Codex builds → Claude independently verifies → human approves. No frontend, AWS, CI/CD, new business feature, commit or push is authorized by this gate.

## Evidence reconciliation and approved continuation

Claude's pre-configuration verification discovered **545 tests: 534 executed/passed, 11 skipped, 0 failures/errors**. Its offline coverage was LINE **94.78%**, BRANCH **82.35%**, INSTRUCTION **95.03%**. The skipped cases were all opt-in `HardeningMySqlTest` cases; they were not passes. The earlier Temurin installation/path claim was not confirmed and is superseded by the verified cached Azul Zulu runtime below. No JDK installation or global environment change was performed in this continuation.

After the human configured the Git-ignored `.env`, this continuation recreated **only `rockey_hospitality_hardening`**, applied the canonical schema and six Department seeds, ran the **11 live tests with no skips**, then packaged live startup and the 51-operation API suite, followed by **one complete live-enabled Java 17 `mvnw.cmd verify`**. The current results below supersede the environment-blocked baseline, not Claude's historical independent report. No production source, business behavior, dependency or source target was changed.

## Verified environment and automated results

| Check | Evidence/result |
|---|---|
| Actual compiler/test/package/startup runtime | Azul Zulu 17.0.20.1+1 (Zulu17.68+203-CA); `java -version`, `javac -version` and Maven runtime verified |
| JDK availability | Existing cached `C:\Users\hoese\.codex\cache\rockey-phase2a\zulu-jdk17\zulu17.68.203-ca-jdk17.0.20.1-win_x64`; per-process `JAVA_HOME` only; no new installation, production path requirement or global PATH change |
| Maven | Wrapper 3.9.16; Java source/release remains 17; application class major version 61 |
| Complete final `mvnw.cmd verify` | **561 tests, 0 failures, 0 errors, 0 skips**, 31 classes after approved Sonar remediation; executable JAR built; all 545 verified baseline cases preserved |
| New hardening tests | Original 7 contract/security + 11 live MySQL cases; remediation adds 10 refresh Origin/controller, 3 actual CORS/simple-refresh and 3 OpenAPI cases |
| Department regression | Controller 9 + Service 13 = **22/22**, including guards added in prior slices |
| JaCoCo LINE | **2646/2772 = 95.45%**; ≥70% hard gate and ≥80% target both met |
| JaCoCo BRANCH | **660/788 = 83.76%** |
| JaCoCo INSTRUCTION | **10917/11431 = 95.50%** |
| Coverage integrity | All 119 production classes analyzed; no exclusions. Final agent run starts fresh (`append=false`), rather than accumulating earlier focused runs |
| MySQL | Running local MySQL 8.0.46; only `rockey_hospitality_hardening` created/seeded/reset |
| Schema | Clean canonical script applied; 9 tables, 9 primary keys, 12 foreign keys, 8 unique constraints, 3 CHECK constraints; Hibernate `validate` startup succeeds |
| Live API | All 51 canonical operations covered by existing collections and the disposable hardening collection; Newman 6.2.2 on Node 22.18.0 |
| Live request evidence | 116 requests, 291 assertions, zero failures; real packaged application on loopback port 18081, no mock service/DB |
| SonarQube | **SUCCESS; Quality Gate PASSED** on `Rockey-Hospitality`; final recheck combined coverage **92.9%**, line **95.5%**, branch **83.8%**. Open vulnerabilities/Critical/High/Major/Medium/Bugs/hotspots **0**; human dispositions verified: **13 Accepted + 6 False Positive**. **40 fixed** findings and **89 deferred Minor/Info** remain separately accounted for. See `SONAR_REMEDIATION.md` for exact keys/evidence; Codex made no issue-status transitions |

Raw local build/coverage artifacts are ignored. Inspect `target/final-java17-verify.log`, `target/surefire-reports/`, `target/site/jacoco/index.html` / `jacoco.xml`, `target/hardening/continuation-live-tests.log`, `target/hardening/continuation-api-run.log`, `target/hardening/startup.log`, and sanitized `target/hardening/api-results.json` / `newman-cli.log`. The ignored `target/hardening/InitializeDisposable.java` harness pins its connection/reset to the literal approved local schema and never accepts credentials in arguments. Do not publish an entire `target/` directory or private runtime environment.

## Reproduced defects and minimum fixes

| Defect | Fix and regression proof |
|---|---|
| Startup rejected Room `floor`: SQL SMALLINT versus inferred Hibernate INTEGER | Explicit SMALLINT JDBC mapping on the existing Integer property. Canonical schema/DTO type and 1–99 validation unchanged; real schema validation and live Room CRUD pass |
| Real Spring startup could not choose RefreshTokenService constructor | Annotated the existing public constructor for injection; package-private deterministic-test constructor retained; no authentication behavior/dependency change |
| Task creation could commit an inactive Department/Employee/Room reference during deactivation | Task reference checks now acquire existing-style pessimistic row locks; Room repository gains the same lock lookup; Employee/Room mutations lock before guard checks |
| Employee-versus-Task locks initially deadlocked at FK update | Consistent Department → Employee lock order; scalar Department-ID routing avoids prematurely caching the Employee; concurrent transfer mismatch is a safe conflict |
| Deactivation could use a stale Task snapshot after an assignment won the lock | READ_COMMITTED for Employee deactivation; real tests cover both winner orders and require the conflicting loser to reject |
| Event capacity could exceed its limit despite the Event lock | READ_COMMITTED for register/withdraw, preserving the current attendee-first validation order while count/duplicate checks see the preceding commit; capacity and duplicate races serialize |
| `GET /api/events/registrations/me` returned 500 with default sorting | Repository query now roots at Event with the same membership subquery, so pageable Event fields bind to Event rather than User. Live paginated registrations and history tests pass |

Controller changes are documentation annotations only, apart from explicit Analytics query documentation that hides the internal parameter map. No business endpoint, DTO field, metric, filter, lifecycle or role scope was added. Signed `%2B1` Analytics integer parsing remains accepted.

## Persisted transaction, query and security evidence

- Independent SQL reconciles ADMIN Dashboard metrics, active Room totals, Task history totals, Department employee/workload counts, scoped Inventory, global Event registrations/preparation, STAFF assigned work, and USER-only registration counts against actual persisted data. Mixed active/inactive/terminal/overdue fixtures are used; no mocked aggregate results.
- User/Employee Department updates commit consistently, concurrent transfers retain agreement, and a controlled post-update exception rolls both rows back. Live refresh rotation persists hashed state, rejects replay, logout clears both stored refresh fields, repeated logout is safe, and Employee deactivation disables login/revokes refresh state.
- Real InnoDB waits are observed through disposable-schema-filtered `performance_schema.data_lock_waits/data_locks`; bounded latches/futures coordinate races. No arbitrary concurrency-test sleeps, lowered locking guarantees, retry-until-pass loop or swallowed unexpected DB error.
- Task-versus-Department/Employee/Room guards, Inventory-versus-Department guard, Event capacity/duplicate registration, and concurrent Alert dedup/idempotency/clearing/recurrence are exercised on MySQL. Nullable Employee linkage, non-null uniqueness, numeric CHECK and FK enforcement are verified.
- Live Postman verifies USER/STAFF/ADMIN access, cross-identity/Department denial, STAFF-without-eligible-Employee 403, password/hash non-exposure, BCrypt-backed provisioning/login, malformed/tampered/expired signed JWT rejection, rotation/replay/logout/deactivation, validation/404/409, soft lifecycle history, and failed-login 429 behavior.
- Live CORS preflight permits exactly the configured local frontend origin with credentials and rejects an unapproved origin; cache-control, `nosniff`, and `DENY` frame headers are present. MockMvc additionally verifies HSTS only for secure requests. **Real TLS/proxy/cross-site-cookie deployment is not claimed**; those configuration assumptions must be verified at the later approved deployment gate.

## Reproduction and schema safety

Use the verified cached JDK directory (or another approved JDK 17), not JDK 25. This is a local reproduction path, not a production configuration requirement:

```powershell
$env:JAVA_HOME = 'C:\Users\hoese\.codex\cache\rockey-phase2a\zulu-jdk17\zulu17.68.203-ca-jdk17.0.20.1-win_x64'
.\mvnw.cmd --version
& "$env:JAVA_HOME\bin\java.exe" -version
.\mvnw.cmd verify
```

For the **opt-in disposable gate only**, configure the approved local username/password privately in process environment variables `ROCKEY_DB_USERNAME` / `ROCKEY_DB_PASSWORD`. Do not put values in commands, committed config, reports or chat. Spring does not automatically load a `.env` file; load an approved ignored source securely if used. Set the URL explicitly:

```powershell
$env:ROCKEY_DB_URL = 'jdbc:mysql://127.0.0.1:3306/rockey_hospitality_hardening?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
$env:ROCKEY_HARDENING_LIVE = 'true'
.\mvnw.cmd verify
node scripts/verify-hardening.cjs
```

The schema must already exist with the reviewed `database/schema.sql` applied specifically to `rockey_hospitality_hardening`. Its script contains no CREATE DATABASE/USE or unrelated schema target. Do not apply/reset it against another database. Live tests refuse another local JDBC schema before context creation and assert `SELECT DATABASE()` before FK-ordered fixture resets. The concurrency observer needs approved read access to `performance_schema`; do not grant new privileges automatically. Unset `ROCKEY_HARDENING_LIVE` for normal development; otherwise its test fixtures intentionally reset this disposable database on each invocation.

Fixtures are deterministic in shape/IDs and unmistakably test-only (`example.test`); passwords and JWT keys are runtime-random, never normal seeds. The runner privately reads ignored `target/hardening/fixtures.json`, starts only its own Java child on loopback, supplies secrets through the child environment, uses approved transient `npx newman`, and terminates only that child. It removes raw reports, runtime Postman environment, and (after success) the disposable password fixture file; only sanitized names/statuses/counts remain. No package manifest/global Newman install is added.

## Frozen API and minimum documentation reconciliation

- Frozen generated OpenAPI: [rockey-openapi.json](rockey-openapi.json); 51 business operations, 52 DTO/schema definitions, JSON request/response/error schemas, numeric/filter metadata, 204 empty bodies and existing Bearer/HttpOnly-refresh-cookie security. Server URL `/` is portable.
- ADMIN-only JSON/YAML documentation; no Swagger UI dependency; USER/STAFF documentation access denied. Cookie authentication is documentation of the existing refresh mechanism, not a new mechanism.
- Parent `10_ROCKEY_PROJECT_SPEC.md`, `17_BACKEND_BUILD_PLAN.md`, `18_DEFINITION_OF_DONE.md` now say 51 operations, with Authentication count 5. Original wireframe's historical 50 remains correctly described in the canonical contract. FR-50 and endpoint #50 identifiers are not renumbered.
- `14_API_CONTRACT.md` documents the implemented refresh cookie/no-body contract and freeze; `16_TESTING_TRACEABILITY.md` links hardening evidence. Approved Analytics metrics, Inventory-only Department filter, date-free behavior, defaults and errors are unchanged.
- `20_PHASE1_REVIEW.md` remains unchanged as a historical Phase 1 review. Its stale "50 endpoints" / "50-endpoint" wording at lines 30, 236 and 244 is not the current contract. Current README, frozen OpenAPI and `14_API_CONTRACT.md` are authoritative at **51 operations**, including the human-approved logout operation.

## Secret/config review and remaining gates

Scoped source, Postman exports, configuration and runtime logs were reviewed for saved credentials/JWTs, AWS/GitHub tokens and private keys. Real DB values are confined to the human-configured, Git-ignored `.env` and private child-process environment; they were never printed or inserted into application files, command arguments or evidence. Codex previously created `.env` with placeholders only; the human subsequently configured it. `.env` remains ignored by `.gitignore:7`. Earlier credentials were pasted into chat by the human; **manual rotation is recommended**, without repeating them. Runtime fixture values are test-only and ignored/removed, not production credentials. The final regression regenerates `fixtures.json`; it must be removed after verification as well as after the API runner.

Git is valid, on `main`, with configured origin `https://github.com/Neem3995/rockey-hospitality.git`, but has **no HEAD/commits and zero tracked project files**. All project material is untracked; empty `git diff`/staged diff cannot prove unchanged content. No add/commit/push, remote mutation or branch protection change occurred. After Claude verification, human authorization is needed to review/stage the intended baseline, create its initial commit and push it, verify the remote state, and configure the officially required branch protection. CI/CD must wait for that versioned baseline and separate authorization.

The original live-blocker continuation results above remain historical evidence. The completed remediation regression passed **561/561 Java17/live tests** and **116 Postman requests/291 assertions**. The final Sonar-only recheck passed the Quality Gate and verified all 19 human dispositions (**13 Accepted + 6 False Positive**), leaving **0 open vulnerabilities/Critical/High/Major/Medium**; **89 Minor/Info** findings remain deferred. No builds/tests/live/API reruns or source/test/POM/dependency/Git-state changes occurred during this recheck; only these two Slice 11 evidence documents were reconciled after the clean result. Full current evidence and analysis ID are in `SONAR_REMEDIATION.md`. **PASS TO CLAUDE FINAL BACKEND RECHECK**; acceptance is not a zero-risk or capstone/submission/deployment readiness claim. Required AWS/HTTPS/proxy/config, monitoring, CI/CD, React and presentation/submission requirements remain future approval-gated work, not waived. STOP after Slice 11.

## Files changed in this slice

- Build/config: `pom.xml`, `src/main/resources/application.properties`, `configuration/SecurityConfiguration.java`, new `configuration/OpenApiConfiguration.java`.
- Documentation metadata: all nine existing controllers (`Auth`, `Department`, `Employee`, `Room`, `Task`, `Event`, `Inventory`, `Alert`, `Analytics`).
- Runtime fixes: `entity/Room.java`, `security/RefreshTokenService.java`, `service/RegistrationService.java`, `service/TaskService.java`, `service/EmployeeService.java`, `service/RoomService.java`, repositories `EmployeeRepository.java`, `RoomRepository.java`, `EventRepository.java`.
- Tests: new `configuration/BackendContractTest.java`, new `hardening/HardeningMySqlTest.java`; existing `EmployeeServiceTest`, `RoomServiceTest`, `TaskServiceTest` mocks now target the locking lookups while preserving their assertions/case counts.
- Artifacts/docs: new `postman/Rockey-Hardening.postman_collection.json`, new `scripts/verify-hardening.cjs`, `README.md`, this report, frozen OpenAPI snapshot, `PHASE2_IMPLEMENTATION_LOG.md`, and only the five directly affected parent documents listed above. Original PDFs, historical repositories, pharmacy Rockey and rockey-helper were untouched.

This blocker-resolution continuation changed only this evidence record, `PHASE2_IMPLEMENTATION_LOG.md` and the README historical-contract note. Initialization helper/logs/reports were generated under ignored `target/`; `.env` was consumed without modification. The seven runtime fixes above are historical Slice 11 fixes, not new changes in this continuation.

## Tooling authority references

Springdoc version family selected using the [official Spring Boot compatibility matrix](https://springdoc.org/v2/). JaCoCo [official coverage-gate documentation](https://www.jacoco.org/jacoco/trunk/doc/check-mojo.html) informed the BUNDLE/LINE minimum; [official releases](https://www.jacoco.org/jacoco/trunk/doc/changes.html) identify 0.8.14. Current JDK evidence is the existing Azul Zulu runtime's version output and Maven runtime verification; no new acquisition/checksum claim is made.
