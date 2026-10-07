# Security and backend quality

This summary explains the backend's quality evidence and retained safeguards. It does not replace the API contract, source tests, JaCoCo output or Sonar project's analysis history.

## Existing verified baseline

The historical final backend gate passed **561/561** Java17/live tests (zero failures/errors/skips), including database persistence, rollback and coordinated lock/capacity/deduplication races. The packaged API run covered all **51 business operations**, **116 requests**, **291 assertions**, zero failures. The final full JaCoCo evidence was line **95.45%**, branch **83.76%**, instruction **95.50%**. The build's production line gate is **70%**, with **80%** target and no production coverage exclusions.

The final historical Sonar recheck on project `Rockey-Hospitality` passed its Quality Gate: **0 open vulnerabilities/Critical/High/Major/Medium/Bugs/security hotspots**, combined coverage **92.9%**. Human dispositions were **13 Accepted + 6 False Positive**; **89 Minor/Info findings** remained deferred. These historical results do not mean every concern is absent or that later changes automatically inherit them. Repeat live/coverage/API/Sonar checks when behavior changes; local reproduction is documented in [LOCAL_SETUP.md](LOCAL_SETUP.md).

## Reviewed findings and safeguards retained

- **CSRF / S4502:** Bearer APIs remain stateless. Cookie refresh enforces exact configured browser Origin, rejects unknown/duplicate Origin headers, and permits non-browser requests without Origin. HttpOnly, SameSite, configurable Secure, restrictive credentialed CORS, rotation and logout revocation remain. Browser/cross-site/TLS topology must be re-evaluated before deployment. This was human Accepted, not hidden through exclusions.
- **JPQL / S2077 (six findings):** appended fragments are fixed application-controlled strings; request values use named parameter binding, never string interpolation. Repository/service/live aggregate verification supported the human False Positive dispositions.
- **Parameter count / S107 (eleven accepted findings):** contract-shaped response constructors and cohesive domain state were deliberately retained. Internal Task/Room/Alert filters use small criteria objects where helpful. Public fields/query parameters did not change.
- **JJWT / S2143 (one accepted finding):** internal Instant/Duration/Clock remains; Date conversion exists only at the pinned JJWT 0.13.0 API boundary.

## Security boundaries

Public registration creates USER only. STAFF/ADMIN logins come from authorized internal Employee provisioning after a controlled first-ADMIN bootstrap. Services enforce active account/Employee/Department and ownership. No credential/hash/refresh secret is returned in DTOs. Access JWT is frontend memory-only; refresh is an opaque HttpOnly cookie whose hash/expiry is stored in User. One active refresh session per account is supported.

Frontend filtering applies only to already-authorized data. It cannot replace STAFF self-assignment/recipient/Department scoping or USER-own registration restrictions. CORS is an exact origin allowlist, not authentication. DB passwords/signing material must come from private runtime configuration, never `VITE_*` or committed environments. Previously disclosed credentials should be rotated privately, without reproducing values.

## Re-running verification

Run ordinary tests/coverage with the Java17 Maven Wrapper. Live fixture tests are opt-in and refuse schemas other than the disposable local `rockey_hospitality_hardening`; they intentionally reset their own test data. Never point them at `rockey_hospitality`. The existing `scripts/verify-hardening.cjs` and Postman collections remain available; provide runtime-generated fixtures/secrets privately and retain only sanitized results.

Production behavior, schema definitions, tests, OpenAPI, Maven configuration and the runtime verification script are retained; educational comments do not change Java behavior. No test was weakened/deleted. The Auth Postman collection generates a temporary password in run-local variable scope rather than saving it. Its five requests and assertions remain unchanged; collection titles may describe their domain without development-process labels.

Test signing keys and valid disposable fixture passwords are generated at runtime rather than saved as usable accounts or keys. Validation-only/mock literals are test inputs, not MySQL/application credentials; no real value is reproduced here. No strong private-key/cloud-token/JWT literal markers or tracked private credential files were found in the scoped candidate review. This is a bounded review, not a guarantee that all historical secrets are absent. The old test fixture value remains in previously published Git history; no history rewriting is authorized. Manually review any old test account created with it and rotate/remove its credential privately.
