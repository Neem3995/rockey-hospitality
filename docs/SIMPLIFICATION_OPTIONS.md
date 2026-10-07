# Rockey model and frontend presentation scope

## Current backend retained

Rockey remains one Spring Boot application: eight entities, nine MySQL tables and 51 existing business API operations. No tables, endpoints, business rules or security boundaries are removed. Analytics is read-only without a table; Event membership uses the existing join table without a separate registration entity.

The model separates account identity, employment, Departments, Room turnover, assigned Tasks, Events/registration, stock and recipient Alerts. Transactions, scoped queries, DTOs, FK/unique constraints and intentional row locks protect correctness. Their purpose is explained in [BACKEND_GUIDE.md](BACKEND_GUIDE.md).

`Employee.jobRole` and Department already supply position/workflow context. Permissions remain USER/STAFF/ADMIN plus service ownership/eligibility checks. Do not add a Position table, infer permission from jobRole, build a workflow engine, or combine APIs into a generic workflow endpoint.

## Future frontend direction — not implemented here

- Keep the available authentication, role dashboard and Employee/Department management.
- A future **Work** presentation may consolidate Rooms and Tasks using their existing endpoints and ordinary React components. This changes presentation, not backend lifecycle or STAFF ownership.
- Alerts may be compact supporting UI rather than a large separate feature surface.
- Events and own registrations remain compact USER workflows; USER must not receive hotel operational data.
- Inventory and additional ADMIN Analytics are **not waived**. Compact views are possible, but their required behavior still needs implementation and verification.
- Do not remove rubric-facing frontend features without explicit instructor approval. What is omitted from a five-minute demo is not automatically omitted from project delivery.

No operational frontend page is redesigned by this document. Other than the implemented dashboard/Employee/Department views, business routes currently remain scaffolds. AWS/CI-CD/deployment are not added here; any instructor exception must remain explicit rather than silently waiving unrelated rubric requirements.

## Filtering and security

React may map authorized DTOs, display role-relevant controls and filter **loaded results**. A paginated page is not the whole dataset and cannot produce global counts. Use existing server filters/pagination for complete searches and the approved Analytics API for aggregates.

Keep STAFF own-assignment Tasks, own Alert recipient, own active Department Inventory, and USER-own registration scope on the backend. STAFF Room browsing has its existing shared access, not an invented Department restriction. Active eligibility, lifecycle validation, credentials and field non-exposure remain service/security responsibilities. Navigation labels can reflect position/context; hidden UI is never a security boundary.
