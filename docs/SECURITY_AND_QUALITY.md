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
Current final measured results are recorded below. Skips are never counted as passes.

## Secrets and reproduction

Only disposable rockey_hospitality_hardening is reset; LOCAL_SETUP.md explains fresh setup and first ADMIN.
.env, filled bootstrap-admin.sql, target/, node_modules/, dist/, coverage/, IDE/log/Sonar artifacts remain ignored.
Postman uses runtime synthetic fixtures; no raw reports or environments are exported.
Source examples are placeholders or synthetic test-only values. No default ADMIN password is shipped.

## Remaining gates

New-model Sonar analysis **NOT RUN**; previous dispositions do not certify new source.
jsdom checks are not real-browser evidence; fresh responsive/native-dialog checks are distinct.
AWS/CI-CD, CloudWatch/deployment, presentation/peer review and final submission remain separate capstone gates, not implemented or waived here.

## Current housekeeping measurements

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
- **New-model Sonar and real-browser UI checks NOT RUN** in this pass; no prior Sonar/browser certification is reused. Independent Claude review remains the next gate.
- No dependency/POM/package/lockfile changes, commit or push. The lean-backend, frontend and main branch pointers were preserved.
