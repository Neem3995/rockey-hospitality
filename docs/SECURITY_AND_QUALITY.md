# Housekeeping security and quality

## Controls

- BCrypt and 72-byte UTF-8 limit; no plaintext password persistence.
- JJWT signature/expiry; database id/role/active reloaded for each authenticated request.
- Memory-only browser access JWT; HttpOnly refresh cookie, configurable Secure, SameSite=Lax, /api/auth path.
- Hash-only single refresh session; row-locked rotation, logout revocation/cookie expiry.
- Preserved bounded login/register/refresh rate limits.
- Explicit credentialed CORS allowlist; present refresh Origin must be a single configured approved origin.
- Non-browser Origin-absent refresh is supported. Stateless bearer APIs do not invent a CSRF endpoint. Cookie/Origin protections are compensating controls, not zero-risk certification.
- Safe errors, Spring headers, no raw credentials/hashes/refresh values in public responses.
- Backend service role/ownership checks; only the assigned USER can start/complete work. MANAGER/ADMIN manage tasks and inspections, never execute cleaning work.
- Locking current reads for task/room/worker guards; task/room and inspection/room changes are atomic.
- Soft lifecycle and foreign keys preserve history.

## Evidence boundary

The former 561-test/nine-table/51-operation Sonar baseline is historical, not verification of rewritten housekeeping source.
Existing JaCoCo gate remains ≥70% overall production line coverage (≥80% target), no exclusions/weakened thresholds.
New unit/MVC/live MySQL/Postman/frontend checks and independent Claude review are required.
This is the canonical measured-results record; other project docs link here rather than duplicate volatile totals. Current verification and historical live evidence are separate. Skips are never counted as passes.

## Secrets and reproduction

Only disposable rockey_hospitality_hardening is reset; LOCAL_SETUP.md explains fresh setup and first ADMIN.
.env, filled bootstrap-admin.sql, target/, node_modules/, dist/, coverage/, IDE/log/Sonar artifacts remain ignored.
Postman uses runtime synthetic fixtures; no raw reports or environments are exported.
Source examples are placeholders or synthetic test-only values. No default ADMIN password is shipped.

## Remaining gates

New-model Sonar analysis **NOT RUN**; previous dispositions do not certify new source.
jsdom checks are not real-browser evidence; fresh responsive/native-dialog checks are distinct.
AWS (including its infrastructure/CloudWatch work) is explicitly instructor-waived due insufficient training. Do not interpret that waiver as waiving CI/CD, deployment, accessible URLs or branch protection: those expectations remain PENDING clarification. Pagination, a standalone testing seed script/accepted alternative, presentation, peer review, Jira and self-assessment also remain PENDING. See [CAPSTONE_SUBMISSION.md](CAPSTONE_SUBMISSION.md).

## Current verification — 2026-10-08

Scope: published quality checkpoint on `refactor/final-capstone-polish`, based on `2ccc706b2ee7f93efd395d60b00a009057a30dbd` and committed/pushed as `51efcd06768070a51913fb9874eefdcd966d0533`. Frontend resilience/accessibility and documentation changes only; Java changes are comments, not executable behavior. No API/schema/dependency change. Measurements below describe the original quality pass before publication, not a fresh execution of the subsequent repository cleanup; pending gates remain unchanged.

Runtime: Temurin Java 17.0.20.1, Maven Wrapper 3.9.16; Node 22.18.0, npm 10.9.3. No machine-specific runtime path is required by production configuration.

Commands (process-local Java 17; `ROCKEY_HARDENING_LIVE=false`):

```text
.\mvnw.cmd -v
.\mvnw.cmd -q verify
cd frontend
npm run test:coverage -- --maxWorkers=1
npm run lint
npm run typecheck
npm run build
cd ..
node scripts/verify-hardening.cjs --audit-only
git diff --check
```

| Check | Current execution evidence |
|---|---|
| Backend verify / tests | PASS: 154 reported entries; 141 executed/passed, 0 failures, 0 errors, 13 live entries skipped |
| JaCoCo | PASS: line 93.85%, branch 80.24%, instruction 93.33%; all production Java, no exclusions; fresh report (`append=false`); gate >=70% line, target >=80% |
| Frontend tests / coverage | PASS: 217/217, 0 failed/skipped; V8 line 99.14%, branch 92.16%, statements 96.82%, functions 96.31%; `src/**/*.{js,jsx}`, excluding tests and main entry point |
| Lint / checked-JS / build | PASS: lint, standalone typecheck and final production build; build also runs checked-JS typecheck |
| MySQL / startup / transaction races | NOT RUN in this pass; live tests disabled; no fixture reset. Superseded 2026-10-12: see [MySQL seed and live verification](#mysql-seed-and-live-verification---2026-10-12). |
| Postman | Static operation audit PASS: 24/24 covered; unchanged collection has 67 requests/149 assertion definitions. Live requests/assertions NOT RUN |
| Sonar | NOT RUN; fresh housekeeping analysis still pending authorization/environment |
| Native browser / responsive QA | NOT RUN in this pass; jsdom is not native-browser evidence |

Focused frontend regressions preceded the final full run. One initial new test query matched text inside a closed native dialog as well as the visible dialog; it was scoped to the visible dialog and passed. Existing tests were retained. Final coverage includes the added safe field errors, synchronous guards, successful/failed read reconciliation, lost inspection success, session change, 401 write recovery, route focus and malformed-response cases.

Review: relative documentation links/anchors resolved; ERD attributes matched all four DDL tables; Java diff contained four comment-only added lines. Schema, OpenAPI, Postman and dependency files remained unchanged. `git diff --check` passed. No Mermaid rendering/native-browser verification is claimed.

Coverage percentages describe the measured scope/run, not universal correctness or performance. Current offline test discovery reports 13 disabled live entries: twelve ordinary tests and one parameterized entry. Enabled, the latter expands into MANAGER and ADMIN cases, producing fourteen live executions. Thus a fully enabled historical run can report 155 cases while an offline run reports 154 entries. Do not count disabled entries as executed.

At the time of that local polish measurement, no commit/push, service start, database reset, Sonar scan or deployment was performed, and main remained at the base commit. This is historical checkpoint context: the source was subsequently published and main advanced to `9373d7370fe188d6854035384eb8a72e9c9b8596`. Local environment files remain private/untracked/ignored as applicable.

## Historical pre-polish housekeeping live evidence

The following measurements were already recorded before the main promotion/polish pass. Exact original execution date was not recorded in this document. They are retained for provenance, not presented as fresh execution after frontend changes.

- Java 17.0.20.1 (Temurin), Maven Wrapper 3.9.16; production compilation/package and full `mvnw.cmd verify` passed.
- **155 backend tests: 155 pass, 0 failures, 0 errors, 0 skipped.** Includes 94 service, 9 retained crypto/rate-limit, 38 MVC/security and 14 opt-in live MySQL cases.
- JaCoCo production coverage: **line 95.12%, branch 80.65%, instruction 94.97%**. Existing ≥70% gate passed; ≥80% line target exceeded.
- Fresh four-table MySQL/Hibernate validation and actual loopback application startup passed.
- Persisted PASS/FAIL/rework/history, rollback, ownership, assignment/deactivation races, duplicate task/inspection races and refresh rotation/logout checks passed.
- Disposable first-ADMIN registration/promotion/relogin passed; no default credentials or privileged endpoint.
- Live Postman: **67 requests, 149 assertions, 0 failures; 24/24 API operations**. Live ADMIN OpenAPI has exactly 24 operations and 17 DTO schemas; all operations have concise access notes and the frozen snapshot is reconciled.
- Frontend: **153/153 tests**, line **97.34%**, branch **88.24%**, statements **94.70%**, functions **92.16%**; lint/checked-JS/build passed. Serial execution avoids concurrent-build resource contention without relaxing test timeouts.
- Focus tests cover both immediate opener restoration and the later removal of an inspection opener during background refresh. Existing native forced-close/save regression remains.
- Credential-candidate scan over 99 eligible project text files found no candidates; active source scan found no removed-domain/old-role references.
- That measurement pass recorded **new-model Sonar and real-browser UI checks NOT RUN**. Later user-reported housekeeping approval/browser verification is not a fresh browser run of this polish working tree.
- The earlier statement that no commit/push had occurred applied to that historical pass. Housekeeping source was subsequently published and promoted to main; it is not an outstanding publication blocker.

## Remediation verification 2026-10-09

Remediation implementation checkpoint: `fix/final-audit-remediation`, based on `9373d7370fe188d6854035384eb8a72e9c9b8596`, measured with uncommitted AuthService/UserRepository and focused test changes before source publication. Documentation records the historical-main correction, measured evidence and pending decisions. Existing untracked environment example/archive were preserved. No commit/push occurred during this implementation checkpoint. The subsequent reviewed commit, fast-forward integration and normal origin/main push are separately user-authorized; their exact revision is recorded in Git history and the publication handoff. No GitHub setting changes, deployments, database resets or service installations were performed. See [the finding checklist and remaining plan](FINAL_AUDIT_REMEDIATION.md).

Validated code/test/contract fingerprint (SHA-256 over sorted tracked source paths and working-file bytes, including frontend/database/Postman/scripts/pom/OpenAPI, excluding root/docs Markdown and untracked files): `310f51bc8f448f57f6ad41fe84e5db4c14c8b1c28f22212b6b7e90c8ed089e86`. The fingerprint identifies this dirty validation scope; it is not a Git commit SHA.

Runtime: process-local Temurin Java 17.0.20.1 and Maven Wrapper 3.9.16; Node 22.18.0 and npm 10.9.3. Default machine Java remains 25; no PATH/runtime installation or machine configuration change was made.

Actual commands (repository root unless noted; stdout/stderr redirected to ignored target logs for test runs):

```text
# Process-local JAVA_HOME selected the existing Java 17 runtime.
# ROCKEY_HARDENING_LIVE=false explicitly disabled database fixtures.
.\mvnw.cmd -v
.\mvnw.cmd -q verify
# frontend directory:
npm.cmd --version
npm.cmd run test:coverage -- --maxWorkers=1
npm.cmd run lint
npm.cmd run typecheck
npm.cmd run build
# repository root:
python target/audit-remediation-static.py
node scripts/verify-hardening.cjs --audit-only
git diff --check
# Temporary loopback browser verification only; stopped after inspection:
# frontend directory:
npm.cmd run dev
```

| Check | Actual result and scope |
|---|---|
| Backend verify / tests | PASS, exit 0: 162 reported entries; 141 executed/passed, 0 failures, 0 errors, 21 skipped. The eight added persisted interleaving tests are among the skips. |
| JaCoCo | PASS: line 93.85%, branch 80.24%, instruction 93.33%; all production Java, no exclusions; append=false; unchanged >=70% line gate and >=80% target. |
| Frontend tests / coverage | PASS, exit 0: 9 files, 217 passed, 0 failed/skipped; V8 line 99.14% (581/586), branch 92.16% (729/791), statements 96.82% (763/788), functions 96.31% (235/244). Scope `src/**/*.{js,jsx}`, excluding test sources and main entry. |
| Lint / typecheck / build | PASS, exit 0 for all three commands. Build's embedded typecheck also passed; both original PNG logos were bundled. |
| Static contract / references | PASS: 24/24 controller route/method pairs match frozen OpenAPI; 54 local schema references resolve. Static Postman operation coverage remains 24/24; no live requests run. |
| Documentation / imports / whitespace | 56 relative Markdown paths/anchors verified after final doc edits; imports compiled with backend/frontend checks. Complete scoped diff reviewed; `git diff --check` passed. |
| Live MySQL / Postman | NOT RUN; no permission for disposable fixture reset was assumed. New tests compile but provide no executed persisted proof. |
| Sonar | NOT RUN; current loopback server unavailable and no current task/provenance metadata found. No old-model findings represented as current vulnerabilities or quality-gate evidence. |
| Native browser | LIMITED PASS on actual session-unavailable shell at 390x844, 768x1024 and 1440x900: no horizontal overflow in dark mode; light mode checked at desktop/mobile; both logos loaded; initial main focus and Tab/Shift+Tab between theme/retry controls observed. Theme preference and viewport restored. Backend was unreachable, so authenticated route/modal/session/write-reconciliation behavior is NOT RUN. jsdom coverage is separate. |
| GitHub protection | Read-only original-repo main metadata: protected=false at the audited SHA; classic protection 404; parent-inclusive rulesets and active main rules empty. No enforcement established and no setting changed. |

One initial static-verifier assertion matched annotation names inside controller comments; the verifier was corrected to remove comments before comparing routes and then passed. This was a verifier issue, not an application failure. Offline Maven emitted an expected exception-handler log for a negative MVC test; Surefire recorded zero errors/failures.

The offline live-test entry count is now 21 (the former 13 plus eight new ordinary tests). If explicitly enabled, the existing MANAGER/ADMIN parameterized test expands one discovery entry into two executions. Actual enabled execution must establish its own counts and outcomes; no live total or pass is claimed here.

F01's stale-write coordination is corrected in code and F08's historical wording is corrected. No API/schema/dependency contract change was introduced. Real MySQL lock behavior and current Sonar remain unverified; pagination/seed acceptance, effective protection, non-AWS delivery and genuine submission artifacts remain mandatory unresolved gates. No submission-readiness verdict is asserted.


## Publication gate 2026-10-09 — blocked

Fresh publication verification was executed on `fix/final-audit-remediation` at baseline HEAD `9373d7370fe188d6854035384eb8a72e9c9b8596`, with the same uncommitted code/test/contract fingerprint recorded above. Prior passing measurements remain historical evidence and do not override this failed gate. Documentation wording was prepared to remain accurate after publication; publication did not occur.

Runtime: Temurin Java 17.0.20.1 (process-local JAVA_HOME), Maven Wrapper 3.9.16, Node 22.18.0, npm 10.9.3. Explicit `ROCKEY_HARDENING_LIVE=false`; no fixture reset. Actual commands: `.\mvnw.cmd -v`, `.\mvnw.cmd -q verify`, frontend `npm.cmd run test:coverage -- --maxWorkers=1`, root `python target/audit-remediation-static.py`, `node scripts/verify-hardening.cjs --audit-only` and `git diff --check`. Backend and frontend test commands ran concurrently; a load-related cause is not established.

| Check | Fresh publication result |
|---|---|
| Backend verify | PASS, exit 0: 162 entries; 141 passed, 21 skipped, 0 failed, 0 errors. Eight added persisted regressions remain skipped. |
| JaCoCo | PASS, unchanged all-production scope/gate: line 93.85%, branch 80.24%, instruction 93.33%. |
| Frontend tests | FAIL, exit 1: 214 passed, 3 failed, 0 skipped out of 217; 8 files passed, 1 failed. Failed assertions/timeouts are listed below; no separate runner error was reported. |
| Frontend coverage | No completed coverage summary from the failed fresh run. The earlier 99.14% line/92.16% branch result is historical, not fresh publication coverage. |
| Frontend lint/typecheck/build | NOT RUN in the publication pass: the command chain stopped immediately after the failed test command. Earlier passing results remain historical. |
| Static checks | PASS: 54 OpenAPI references; 24 controller/API operations; 24/24 Postman operation coverage; 56 relative documentation paths/anchors; normal configured `git diff --check`. Final documentation checks rerun after recording this failure. |
| Live MySQL/Postman/Sonar/authenticated browser | NOT RUN. Separate reset/environment authorization remains pending. No new browser run was attempted. |
| Publication | BLOCKED before staging/commit/fast-forward/push under the user's explicit failed-executable-check stop rule. |

All three failures were in `frontend/src/tests/authUi.test.jsx`, group `FE-02 forms and guards`:

- `public registration has no role selector and validates name/password lengths`: exceeded the existing 5000ms timeout.
- `renders safe API field errors, clears password and preserves focus`: exceeded the existing 5000ms timeout.
- `prevents duplicate submits while pending and ignores stale completion`: could not find the expected accessible `Signing in…` button.

These are actual test-gate failures, not newly established application vulnerabilities. No frontend source, timeout, dependency or assertion was changed; the accepted Java remediation was not changed. No retry was used to replace the failed result.

Ignored local evidence: `target/publication-backend-verify.log`, `target/publication-frontend-verify.log`, `target/publication-verification-results.json` and `target/audit-publication-state.json`, plus Surefire/JaCoCo outputs. No source/credential files or raw secret-bearing reports are staged. No commit, push, merge, force-push, GitHub setting change, deployment, service installation, privilege change or database reset occurred in this publication attempt. Existing untracked environment example/archive were preserved byte-for-byte. No published-source handoff was generated because no remediation commit was published.

## Publication verification - 2026-10-12

The current local demo and 217-test frontend verification supersede the historical unauthenticated browser/test status above for their stated scope; see LOCAL_DEMO_WALKTHROUGH.md. The login/logout locking changes require no schema migration. Removed local diagnostic SQL appended after the four table definitions; database/schema.sql retains the canonical definitions only. Live concurrency reset tests and Sonar remain outside this publication scope.


Fresh publication checks on 2026-10-12: Maven verify passed (141 passed, 21 opt-in live tests skipped, zero failures/errors). The concurrent single-worker frontend run had one five-second registration-test timeout (216 passed); a separate run after backend completion passed all 217 with unchanged tests, then lint and production build (including typecheck) passed. Concurrent-load causation is not established. Read-only demo metadata confirmed 4 application tables, 5 foreign keys and 2 CHECK constraints; no database writes or destructive tests occurred. Private configuration, generated target/dist artifacts, the audit ZIP and frontend/.env.example are excluded from publication.


## Submission closeout - 2026-10-12

Fresh bounded verification executed against clean tracked application source on main at `aa8d2b9c7e2ee43b20b88fc895a613f12d18c8e7`; GitHub main independently matched. Only documentation changed afterward. Existing untracked frontend/.env.example and rockey-final-audit.zip were preserved. Execution date is October 12, America/New_York. Prior October 8-12 measurements above remain historical, not substitutes for this run.

Runtime: process-local Temurin Java 17.0.20.1, Maven Wrapper 3.9.16, Node 22.18.0, npm 10.9.3. Backend verify completed before frontend coverage; lint, typecheck and build followed sequentially. No tests, assertions, timeouts, coverage gates, dependencies or application source were changed. No fresh executable failure occurred, so no diagnosis retry was needed.

| Check | Actual closeout outcome / evidence |
|---|---|
| `.\mvnw.cmd -q verify`, ROCKEY_HARDENING_LIVE=false | PASS exit 0; 162 entries, 141 executed/passed, 0 failed, 0 errors, 21 skipped. target/closeout-backend.log and target/surefire-reports. |
| JaCoCo | All production Java, no exclusions: line 885/943 = 93.85%; branch 199/248 = 80.24%; instruction 4100/4393 = 93.33%. Existing 70% line gate passed. target/site/jacoco. |
| `npm.cmd run test:coverage -- --maxWorkers=1` | PASS exit 0, first run: 9 files, 217 passed, 0 failed/skipped, no runner errors. V8 lines 581/586 = 99.14%, branches 729/791 = 92.16%, statements 763/788 = 96.82%, functions 235/244 = 96.31%. Scope src/**/*.{js,jsx}, excluding tests and main.jsx. target/closeout-frontend-coverage.log and frontend/coverage. |
| lint / typecheck / build | PASS exit 0 each; build also runs typecheck. target/closeout-lint.log, closeout-typecheck.log, closeout-build.log. Imports compiled in backend/frontend checks. |
| Static checks | 54 OpenAPI refs resolve; 24 controller operations match snapshot; Postman static 24/24; 56 links/anchors before doc edits. Final 57 links/anchors passed after doc edits; target/closeout-static.json. `python target/audit-remediation-static.py`, `node scripts/verify-hardening.cjs --audit-only`, `git diff --check`. No live Postman run. |
| Eight account/session races | Inspected both orders of login/deactivation, identity change, refresh and logout. Explicit opt-in, exact loopback URL prefix and SELECT DATABASE() safety checks precede four-table fixture deletion; real connection lock waits use performance_schema.data_lock_waits joined to threads. NOT RUN in this closeout: user explicitly chose to leave live tests NOT RUN. Superseded later on 2026-10-12: all eight passed in live run 2 (see [MySQL seed and live verification](#mysql-seed-and-live-verification---2026-10-12)). |
| Hardening prerequisites | Required URL prefix jdbc:mysql://127.0.0.1:3306/rockey_hospitality_hardening?; no configured hardening URL established. Private configuration targets rockey_hospitality_demo. SELECT metadata succeeded using demo account (0 lock waits, 39 threads); no hardening schema visible to that account. Existence/privileges of a dedicated hardening account/database remain unconfirmed. No reset, schema creation, grant or fixture mutation. |
| Sonar | NOT RUN: SONAR_TOKEN present (never printed); SONAR_HOST_URL and SONAR_PROJECT_KEY absent from process/private project configuration; no project scanner task file, no local port 9000 listener. Authorized host/project and instructor disposition required. No installation and no historical deleted-domain evidence reused. |
| Native browser | LIMITED PASS on existing demo: automatic session recovery as Demo USER; own task view with retained completed Room 204 and FAIL/rework Room 205 rows; USER /rooms denied; logout reached sign-in, login with existing redacted browser fields reached housekeeper dashboard. Main focus on normal task navigation/login/logout; Tab from Refresh tasks reached task-filter. Task layout visually inspected at 390x844 and 1280x900 in light/dark; no document horizontal overflow, table scroll on mobile. Theme/viewport restored. |
| Native browser limits | Supervisor login/navigation and history dialog, tablet, exhaustive keyboard/modal trap, pending-close, opener removal/focus restoration, uncertain-write reconciliation NOT RUN in this pass; existing jsdom tests passed but are separate. No business writes or new fixtures. Loopback backend/Vite started for this check and stopped afterward; ddl-auto=validate. Historical October 11 safe screenshot inspected: target/frontend-demo-completed.png. No new recording claimed. |
| Original main protection | Read-only GitHub main protected=false, classic protection 404, parent-inclusive rulesets [] and effective main rules []. No protection established; no settings changed. |

Only AWS is waived by supplied user/current project records. No further instructor exception was found in inspected local records; Jira/Canvas/instructor systems were unavailable. Submission is NOT certified. Remaining gates, external artifacts and rehearsal plan are in [CAPSTONE_SUBMISSION.md](CAPSTONE_SUBMISSION.md#submission-closeout-checklist---2026-10-12).


## Required completion verification - 2026-10-12

AWS is the only waiver. Required local implementation changes were made on top of baseline `aa8d2b9c7e2ee43b20b88fc895a613f12d18c8e7` and later committed on branch `required-completion` (`a032bbd`, then `38f47af`). Previous closeout records above predate pagination/seed/pipeline changes and are historical. The originally planned target/required-results.json was never written; the ignored target/required-*-final-utf8.log files are the surviving Codex receipts. Source provenance is established by the independent rerun below, not by those logs alone.

The first changed-source backend run had 171 entries, 3 failures, 0 errors, 21 skips: three existing MVC assertions still indexed bare-array responses after the intentional page-envelope change. Their field/ownership assertions were updated to $.items with scope/envelope regression coverage, not removed or loosened. HousekeepingApiTest passed in isolation, then full verify passed. The first changed-source frontend run had 225 passes and one failure: AuthProvider's bootstrap fetch mock returned an obsolete array, causing the guarded adapter to reject it. The mock was updated to the required empty page envelope; all context/token/storage assertions retained. The affected test passed in isolation and the full suite passed. Original five-second test timeouts, dependencies and coverage gate remain unchanged.

Final checks use process-local Temurin Java 17.0.20.1/Maven 3.9.16, Node 22.18.0/npm 10.9.3. Backend verify precedes frontend coverage, lint, typecheck and build sequentially; live mode=false. Final results: backend 171 entries, 150 passed, 0 failures/errors, 21 skipped. JaCoCo all-production Java, no exclusions: line 904/962 (93.97%), branch 211/260 (81.15%), unchanged >=70% line gate. Frontend: 9 files, 226 passed, 0 failed/skipped; V8 line 606/612 (99.01%), branch 789/851 (92.71%), statements 800/826 (96.85%), functions 249/259 (96.13%). Scope remains src/**/*.{js,jsx}, excluding tests/main.jsx. Lint/typecheck/build and static counts were independently re-executed; see the independent rerun section below for receipts.

Three seed-runner input-safety tests passed without connecting to MySQL. Static seed checks validate FK/status/completion/inspector relationships and guard placement. Four bounded pagination contracts and Postman 24-operation static coverage are checked. Deployment scripts pass bash -n; Jenkins structure is checked locally, not compiled/executed by Jenkins. Exact final Markdown link/reference counts are in target/required-static.json. No Sonar/Jenkins server, installation, hosting purchase, deployment, protection settings, commit or push occurred. At that point seed/live/reset/Postman/business-browser mutations were NOT RUN. Seed execution and the live suite later ran with separate approvals (see [MySQL seed and live verification](#mysql-seed-and-live-verification---2026-10-12)); Postman and business-browser mutations remain NOT RUN. No source secrets or usable seed credentials are shipped.

Seven-slide editable PPTX and matching seven-page PDF prepared with notes and existing logo/historical screenshot. Package, geometry and first-party import checks passed; all slide renders visually inspected, including corrected embedded images. The bundled PDF exporter lacked #standard-fonts/Helvetica, so PDF was assembled from the same inspected slide images; it is raster, while PPTX text remains editable. On 2026-10-12 desktop PowerPoint opened the deck and exported the seven renders used for the current image-only PDF (ReportLab assembly, not native PDF export). Rehearsal and Canvas delivery NOT RUN. Historical screenshot is labeled in notes and with a visible slide 4 caption. See [required completion and access/approval plan](REQUIRED_COMPLETION.md), which replaces other-waiver candidates with REQUIRED statuses and preserves external evidence boundaries. Submission readiness is NOT certified.


## Independent rerun and provenance - 2026-10-12

Source: uncommitted working tree on main at `aa8d2b9c7e2ee43b20b88fc895a613f12d18c8e7`. Fingerprint `b6cf1cfb6da43f8c7aee8a96954526b83d7827fbaf4efb212aa672cce80e4648` = SHA-256 of `git diff HEAD --binary` followed by each untracked, non-ignored file (sorted, excluding rockey-final-audit.zip). Afterward only the activation script's line endings, .gitattributes, documentation and the presentation pair changed; no application or test source changed. Commands ran sequentially from 15:26 to 15:34 America/New_York. Logs: ignored target/required-rerun-*.log.

| Check | Outcome |
|---|---|
| `./mvnw -B verify`, Temurin 17.0.20.1, Maven 3.9.16, ROCKEY_HARDENING_LIVE=false | PASS exit 0: 171 present/executed entries, 150 passed, 0 failed, 0 errors, 21 skipped (all HousekeepingMySqlTest, opt-in live). JaCoCo line 904/962 (93.97%), branch 211/260 (81.15%); 70% line gate passed. Repeated on Java 25.0.4.1 with identical counts. |
| `npm run test:coverage` (Node 22.18.0) | PASS exit 0: 9 files, 226 passed, 0 failed/skipped. V8 lines 606/612, branches 789/851, statements 800/826, functions 249/259. |
| lint / typecheck / build | PASS exit 0 each. |
| verify-submission.py, seed node tests, bash -n (both deploy scripts), verify-hardening --audit-only, git diff --check | PASS: 58 OpenAPI refs, 24 operations, 77 links/anchors, 4 pagination contracts, 3/3 seed input tests, Postman static 24/24. |

These counts match the Codex claims (150/21 backend, 226 frontend) and the 15:03 surefire reports; earlier 141/217 results are historical for baseline source only. Not executed in this rerun: the new TaskRepository.findScoped JPQL and derived paging queries against MySQL (unit and MockMvc tests mock repositories; the 21 live tests are skipped), seed execution, Jenkins, Sonar, deployment, live Postman and authenticated browser checks of pagination. The MySQL paging queries and seed execution were verified later; see [MySQL seed and live verification](#mysql-seed-and-live-verification---2026-10-12). jsdom page tests are not browser evidence.


## MySQL seed and live verification - 2026-10-12

These runs used a disposable `rockey_hospitality_hardening` database on 127.0.0.1, created with separate approval and accessed by a dedicated least-privilege account (not root). That account has database-scoped SELECT/INSERT/UPDATE/DELETE/CREATE TEMPORARY TABLES plus SELECT on `performance_schema.data_lock_waits` and `threads`. Demo and other databases were not targeted. Credentials, hashes and fixture passwords stayed in a private file outside the repository. Raw logs and sanitized records are kept only in the local, git-ignored `target/` directory and have not been published.

| Check | Outcome |
|---|---|
| Seed execution and inspection | PASS. Pre-write identity, schema and zero-row checks passed. Dry run and seed exited 0. Read-only inspection matched expectations: 2 users (MANAGER, USER), 3 rooms (T101 DIRTY, T102 READY, T103 DIRTY), 3 tasks (1 ASSIGNED, 2 COMPLETED with completion times), 2 inspections (PASS on T102, FAIL on T103, MANAGER inspector). There were no orphaned foreign keys and no ADMIN, and the stored hashes equalled the private inputs. |
| Live `HousekeepingMySqlTest`, run 1 | FAIL, retained separately: 26 executions, 24 passed, 2 failed with MySQL 1213 deadlocks (`identityChangeThenLoginCannotUseOldEmailOrRole`, `loginThenRefreshRejectsPreviousSession`). Cause and fix: [dated correction](FINAL_AUDIT_REMEDIATION.md#auth-lock-order-correction-and-live-verification---2026-10-12). |
| Offline backend verify after the fix | PASS: 180 entries discovered, 155 passed, 0 failures, 0 errors, 25 skipped (the opt-in live suite). The JaCoCo gate was evaluated and passed (line 907/965). |
| Live `HousekeepingMySqlTest`, run 2 | PASS: 26/26 executions, 0 failures, errors or skips. This includes the 4 persisted pagination tests, all 8 account/session interleavings and both supervisor parameterized cases. There were no deadlocks. The targeted command did not evaluate the coverage gate. |

Provenance: the offline verify and live run 2 used `a032bbd7ec157a1b5dc383723c218eec67461157` plus four uncommitted files (AuthService, UserRepository, AuthServiceTest, HousekeepingMySqlTest). Those files were later committed as `38f47af`, identical in content apart from line-ending normalization. Seed verification and live run 1 used `a032bbd` plus only the uncommitted HousekeepingMySqlTest additions. The live fixtures deleted the seeded rows, and a later approved guarded reset left the disposable database empty.

Still NOT RUN: live Postman/Newman, Sonar analysis and quality gate, Jenkins execution, non-AWS deployment and rollback, and outstanding authenticated browser checks. Branch protection and the external submission items remain outstanding. Submission readiness is NOT certified.
