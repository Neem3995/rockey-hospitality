# Rockey housekeeping frontend

React/Vite, checked JavaScript/JSDoc (no TS/TSX application files), React Router, native fetch.
Pinned dependencies unchanged. Verified Node 22.18.0.

```powershell
npm ci
npm run dev
```

http://localhost:5173, strict port/loopback; default API http://localhost:8080/api.
Ignored .env.local can contain only public VITE_API_BASE_URL. No credentials/keys/tokens in VITE_*.

## Six routes

| Route | Roles | Behavior |
|---|---|---|
| /login | Public | Sign in |
| /register | Public | USER-only housekeeper registration |
| /dashboard | USER/MANAGER/ADMIN | Own task counts or supervisor housekeeping overview; safe account |
| /tasks | USER/MANAGER/ADMIN | Own USER work; supervisor task/assignment management |
| /rooms | MANAGER/ADMIN | Rooms, minimal PASS/FAIL inspection/history |
| /team | MANAGER/ADMIN | Manager USER-only provisioning/view; Admin non-ADMIN management |

Unknown routes display Page not found with a link back to the protected dashboard.
No dormant removed-domain scaffolds. Backend role/ownership checks are authoritative.

## State and workflow

Only the assigned USER sees start/complete controls; MANAGER/ADMIN create, assign/reassign, edit, cancel and inspect instead.
Task start/complete changes Room on backend; UI re-reads authorized lists. PASS makes READY; FAIL permits rework.
Lists are arrays; local task status filter, no pagination engine or analytics endpoint.
useState: UI/form state; useEffect/useContext: authenticated reads; useReducer: task draft; useMemo: counts; useCallback: cancellable loaders.

Private client access JWT stays in memory. HttpOnly cookie handles refresh. 401 refresh/retry once (shared), ordinary 403 never refreshes.
Epoch/session keys ignore stale responses/remount private views on identity/authorization/logout change.
Network/429/5xx failures remain recoverable. No auth state in localStorage/sessionStorage; only rockey-theme persists.
Business writes have synchronous submission guards. An uncertain network/server/unreadable-response outcome blocks further writes until an explicit authorized list/history refresh succeeds; it is never automatically replayed. A confirmed 400/409 can be corrected and submitted again.
Rooms, Tasks and Team associate allowlisted safe field errors with their controls, retain drafts after validation errors and clear corrected field errors. Essential business response shapes are checked before use, without duplicating backend business rules.

## Accessibility and theme

Shared Button/Input/Card/Modal/Table plus small select/loading/error components.
Supplied original light/dark logos and navy/blue/cyan palette remain; document theme/native color-scheme.
Semantic headings/labels, status/error announcements, skip link/visible focus.
Each route has a meaningful document title. Client pathname navigation focuses main; theme/read refreshes do not steal focus or change modal restoration.
Modal traps focus, handles Escape/forced browser close during save, restores opener or stable view-heading fallback.
Loaded content remains mounted during background refresh; opener controls stay usable during ordinary reads, but are blocked during pending or unreconciled writes.
Navigation wraps; wide tables scroll within named keyboard-focusable regions. Reduced-motion spinner rule.
Hotel time is America/New_York; naive backend operational timestamps are not relabeled UTC.

## Verify

npm run test:coverage; npm run lint; npm run typecheck; npm run build.
Unit/jsdom tests do not certify live backend RBAC or native-browser focus/responsiveness.
See ../docs/LOCAL_SETUP.md for backend reproduction/Postman.
See [canonical verification evidence](../docs/SECURITY_AND_QUALITY.md#current-verification--2026-10-08) for dated results and [submission draft](../docs/CAPSTONE_SUBMISSION.md) for current requirements and pending gates.

## Application source tree (29 files; tests and supplied assets counted separately)

```text
src/
  App.jsx  main.jsx
  components/layout/AppLayout.jsx
  components/ui/
    Button.jsx  Input.jsx  Card.jsx  Modal.jsx  Table.jsx
    ErrorNotice.jsx  Select.jsx  Spinner.jsx  Skeleton.jsx
  context/AuthProvider.jsx  context/authContext.js
  hooks/useAuth.js  hooks/useRead.js
  routes/AuthGuard.jsx  routes/routeDefinitions.js
  services/apiClient.js  services/config.js  services/housekeepingService.js
  utils/theme.js
  styles/index.css
  pages/
    DashboardPage.jsx  TasksPage.jsx  RoomsPage.jsx  TeamPage.jsx
    auth/AuthPage.jsx  auth/SessionStatus.jsx
```
