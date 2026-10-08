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

Scope: uncommitted `refactor/final-capstone-polish` working tree based on `2ccc706b2ee7f93efd395d60b00a009057a30dbd`. Frontend resilience/accessibility and documentation changes only; Java changes are comments, not executable behavior. No API/schema/dependency change. This pass has not been independently re-verified yet.

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
| MySQL / startup / transaction races | NOT RUN in this pass; live tests disabled; no fixture reset |
| Postman | Static operation audit PASS: 24/24 covered; unchanged collection has 67 requests/149 assertion definitions. Live requests/assertions NOT RUN |
| Sonar | NOT RUN; fresh housekeeping analysis still pending authorization/environment |
| Native browser / responsive QA | NOT RUN in this pass; jsdom is not native-browser evidence |

Focused frontend regressions preceded the final full run. One initial new test query matched text inside a closed native dialog as well as the visible dialog; it was scoped to the visible dialog and passed. Existing tests were retained. Final coverage includes the added safe field errors, synchronous guards, successful/failed read reconciliation, lost inspection success, session change, 401 write recovery, route focus and malformed-response cases.

Review: relative documentation links/anchors resolved; ERD attributes matched all four DDL tables; Java diff contained four comment-only added lines. Schema, OpenAPI, Postman and dependency files remained unchanged. `git diff --check` passed. No Mermaid rendering/native-browser verification is claimed.

Coverage percentages describe the measured scope/run, not universal correctness or performance. Current offline test discovery reports 13 disabled live entries: twelve ordinary tests and one parameterized entry. Enabled, the latter expands into MANAGER and ADMIN cases, producing fourteen live executions. Thus a fully enabled historical run can report 155 cases while an offline run reports 154 entries. Do not count disabled entries as executed.

No commit/push, service start, database reset, Sonar scan or deployment is performed by this polish pass. Main remains at the base commit. Local environment files remain private/untracked/ignored as applicable.

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
