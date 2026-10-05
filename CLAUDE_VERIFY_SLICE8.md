# Claude Code Slice 8 Final Verification — Inventory

## Codex Handoff Reviewed
`PHASE2_IMPLEMENTATION_LOG.md` (Slice 8 section). Claims checked against the repository, not taken as-is.

## Git State
Repository has no commits; all files untracked (unchanged gate condition). Nothing committed by this review.

## Specification Mapping
- FR-44 — STAFF sees active items of its own active Department; ADMIN sees all. Implemented in `InventoryService.listInventory`/`getInventoryItem` via `findStaffDepartment`.
- FR-45 — ADMIN create, update, restock (absolute quantity via PUT), and deactivate. Implemented; restock is the PUT quantity field, which matches contract #40 "Update/restock item" without inventing a delta field.
- FR-06 (supporting) — route-level role gates in `SecurityConfiguration`: `GET /api/inventory/**` → STAFF/ADMIN, `/api/inventory/**` otherwise → ADMIN. USER denied.
- FR-13 (supporting) — Department deactivation now also blocked by active Inventory (see below).
- BR-39 — quantity and threshold `>= 0` (DTO `@Min(0)`, entity `@Min(0)`, service `validateCounts`). SKU normalized to upper-case, pattern `[A-Z0-9-]{1,40}`, unique via `existsBySkuIgnoreCase` and `uk_inventory_items_sku`. SKU is `updatable = false` and absent from `UpdateInventoryItemRequest`, so it cannot be changed after creation.
- BR-40 — STAFF is scoped to own active Department and active items; cross-department and inactive filters/details return 403. ADMIN unscoped.
- BR-44 — `@Transactional` on all write methods; pessimistic locks on the Department (`findByIdForUpdate`) and item (`findByIdForUpdate`) serialize inventory writes against Department deactivation.
- BR-06 — backend enforces roles and ownership; frontend is not relied on.
- BR-13 (Inventory-owned portion) — `DepartmentService.deactivateDepartment` now rejects when `inventoryItemRepository.existsByDepartmentIdAndActiveTrue` is true, under the same Department lock. Proven by `deactivateDepartmentRejectsActiveInventoryWithoutChangingHistory`.
- BR-14 (Inventory-owned portion) — creating an item in, or moving an item into, an inactive Department is rejected (`findDepartmentForUpdate(..., requireActive=true)`). Historical inactive items keep their Department reference; they are not forced to move.
- FR-46 / BR-41 — correctly NOT implemented (see Deferred).

## Endpoint Verification (all 5)
| # | Endpoint | Contract | Implementation | Test evidence |
|---|---|---|---|---|
| 37 | `GET /api/inventory` | STAFF scoped, ADMIN; 200 | paginated, filters `departmentId`/`active`, allowlisted sort | `InventoryControllerTest` list cases; service tests |
| 38 | `POST /api/inventory` | ADMIN; 201 | normalized name/SKU, counts validated, inactive dept → 409, duplicate SKU → 409 | `adminMayCreateRestockAndSoftDeactivateInventory` (SecurityConfigurationTest) |
| 39 | `GET /api/inventory/{id}` | STAFF scoped, ADMIN; 200 | STAFF out-of-department/inactive → 403, missing → 404 | service + controller tests |
| 40 | `PUT /api/inventory/{id}` | ADMIN; 200 | absolute quantity, active flag, move gated by active Department | service tests |
| 41 | `DELETE /api/inventory/{id}` | ADMIN; 204 | soft-deactivate, no repository delete call, empty body | controller test asserts 204 no body |

## DTO / Transactions / Errors
- Requests carry validation annotations matching the data dictionary (`name` 2-120, `quantity`/`reorderThreshold` `>= 0`, `departmentId` positive).
- `InventoryItemResponse` exposes only id, name, sku, counts, a shallow `DepartmentSummary`, active flag, and timestamps. No entity serialization.
- Errors use the existing `GlobalExceptionHandler` contract (400/403/404/409).

## Data Integrity
- `inventory_items` schema: `sku` unique, `quantity` and `reorder_threshold` NOT NULL, FK to `departments`, index on `(department_id, active)`. Matches the data dictionary.
- History is preserved: no delete path exists for inventory, and Department deactivation is blocked while active items exist rather than cascading.

## Pagination / Filtering
Page `>= 0`, size 1-100, sort field allowlisted (`name`, `sku`, `quantity`, `reorderThreshold`, `createdAt`), direction validated. Department filter must be positive.

## Authorization Review
Route rule ordering is correct: `GET /api/inventory/**` (STAFF/ADMIN) precedes the catch-all ADMIN rule, so reads are allowed to STAFF and every write is ADMIN-only. Service-level STAFF scoping is enforced in addition to the route gate.

## Dependency and Secret Audit
`pom.xml` still has 13 artifacts, identical to the verified Slice 7 state. No new dependency. Secret scan of `src/main` returned no hardcoded credentials.

## Future-Domain Check
No `*alert*` or `*analytic*` file exists in `src`. No Alert or Analytics code in `src/main`.

## Build and Test Verification
- Focused run: `InventoryServiceTest` 56, `InventoryControllerTest` 19, `DepartmentServiceTest` 13, `DepartmentControllerTest` 9, `SecurityConfigurationTest` 42 = **139/139 passed**. The log's focused count of 130 omitted `DepartmentControllerTest`; the 9 extra pass.
- Full regression (`mvnw package`, run once): **321 tests, 20 classes, 0 failures, 0 errors, 0 skipped**. Executable JAR produced.
- Arithmetic check: 321 total − 240 prior = 81 new (75 Inventory, 5 security, 1 Department guard), matching the log exactly.
- Department regression: 22/22 (13 service + 9 controller), green.
- All prior 240 tests remain green.

## Defects Found
None.

## Fixes Applied
None required.

## Deferred Requirements
- FR-46 / BR-41: inventory alert generation, recipient fallback (Purchasing, then ADMIN), and deduplication belong to Alerts/Automation. Only the quantity/threshold source data exists now. Correctly deferred.
- Alert/Automation items still deferred from earlier slices: FR-22/BR-21 (room readiness), FR-33/BR-31 (task alerts).
- Full Analytics (FR-51 operations analytics) remains a later slice.
- Live MySQL schema validation, application startup, Postman execution, and real pessimistic-lock behavior under concurrency: environment-blocked. No credentials were available and none were invented. Lock behavior is verified by design and mocked tests, not by a live database.

## Definition of Done
Met for the implemented and tested Inventory scope and the Department guard. Not fully met at whole-application level because of the environment-blocked live verification above.

## Token / Usage Efficiency
- targeted reads: Inventory service, entity, controller, repository, DTOs, the Department deactivation path, the security route block, and the Inventory spec rows (FR-44–46, BR-39–41, BR-13/14, API #37–41, data dictionary).
- broad scans avoided: no full-repository reread; prior-slice facts reused where unchanged.
- commands: one focused run, one full `package` run. No `clean` (known OneDrive lock issue).
- intentionally not run: live startup, DB, and Postman, as above.

## Final Verdict
**VERIFIED WITH DOCUMENTED DEFERRED REQUIREMENTS**
