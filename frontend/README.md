# Rockey frontend

React + Vite foundations, Rockey themes and authentication, on `feat/react-frontend` from verified backend baseline `930182fb57e60107b16667dda8cf32c3cfcf11d3`. Application code is JavaScript/JSX; JSDoc and development-only TypeScript check props and source with `allowJs`, `checkJs`, `strict`, and `noEmit`. There are no TS/TSX application files.

## Local setup

Verified runtime: Node 22.18.0, npm 10.9.3. Packages are pinned in `package.json`/`package-lock.json`; newer Router/jsdom releases requiring a newer Node are not selected. From this directory:

```powershell
npm ci
npm run dev
```

Open `http://localhost:5173`. Vite binds to localhost with strict port 5173. There is no backend proxy and no automatic browser launch. An occupied port is an error, not a silent move to an unapproved origin. `npm run preview` uses localhost:4173 for scaffold review only; it is not an approved API/CORS origin.

The optional ignored `frontend/.env.local` may contain `VITE_API_BASE_URL=http://localhost:8080/api`. No environment file is needed for the default endpoint prefix; create one locally only when overriding it. **All `VITE_*` values are public. Never put credentials, access/refresh tokens, JWT keys, or backend database configuration here.** Vite's environment root is this frontend directory, not the backend's private `.env`.

## Implemented boundary

- Router and responsive layout with skip link, semantic landmarks, role-filtered navigation and a keyboard-operable mobile menu.
- Required reusable Button/Input/Card/Modal/Table with JSDoc props. Button supports pending/disabled state; Input supports accessible hints/errors; Table supports a caption and empty state. Modal uses native `dialog.showModal()` with controlled state, explicit Tab/Shift+Tab boundary handling and focus restoration.
- Native-fetch authentication client, AuthContext/provider, login, public USER registration, read-only account panel, route guards and recoverable session/error views. The five frozen authentication endpoints remain unchanged.
- Role-specific `/dashboard` using only `GET /api/analytics/dashboard`: USER registrations, STAFF identity/department-scoped counts, ADMIN approved global counts. No lists, query filters or frontend aggregate calculations.
- ADMIN Employee/Department management using the ten frozen CRUD operations; STAFF read-only own Employee profile opens from AccountPanel without adding a route. Loaded management rows remain mounted during same-filter background refresh; dialog focus returns to its opener or the stable page heading if that opener is unavailable.
- Public API-location configuration and America/New_York display formatting of the dashboard's offset-bearing `asOf` instant only; backend timestamps remain unchanged.
- Lint, checked-JavaScript, component/router/config tests and production build commands.

FR-52 remains **partially implemented**: operational business pages are deferred. BR-05/BR-06 are preserved: credentials are transient form/request values, access tokens remain private in API-client memory, and the backend controls refresh cookies and authorization. Authentication uses `useState`, `useEffect` and `useContext` where needed; other rubric hooks should be used only when they fit a real feature.

## Brand and theme

The shared branding/theme system is documented in [THEME_SYSTEM.md](THEME_SYSTEM.md). The actual light/dark logo files live in `src/assets/brand/`. All components consume semantic CSS variables from `src/styles/index.css`, including surfaces, text, button states, borders, focus and status colors. New UI work must use these tokens instead of hardcoded colors.

Light is the default when no valid preference exists, regardless of OS color scheme. The shared native button switches the document `data-theme`, native `color-scheme`, and displayed logo without reloading. **localStorage is permitted only for the non-sensitive UI preference `rockey-theme` (`light` / `dark`). Never store JWTs, refresh tokens, credentials, roles, or authentication state in localStorage or sessionStorage.** Denied reads fall back to light; denied writes allow in-page toggling without persistence. Authentication pages consume the same semantic tokens.

## Authentication

Startup performs one credentialed `POST /api/auth/refresh`, shared across StrictMode mounts. Login/register/refresh keep the returned access JWT in the client's private variable, then validate identity through `GET /api/auth/me`. Context exposes only safe profile/state and actions. Public registration sends name/email/password only; there is no role selector.

Protected requests attach Bearer authorization. Concurrent 401s share one refresh including `/me`; each original request retries once. Authentication requests never refresh recursively. Terminal refresh rejection clears local authentication/private views; network/429/server failures expose a recoverable state and Retry without claiming server logout. Epoch, identity and private-view keys discard stale responses and clear mounted private data on authentication/authorization changes.

Logout refreshes first when a valid access token is unavailable, and confirms sign-out only after `POST /api/auth/logout` returns 204. Failures remain visible and do not falsely claim revocation. No refresh cookie is read by JavaScript and no refresh token is sent in JSON. State is `checking`, `authenticated`, `anonymous` or `recoverable-error`.

During development, fully reload after editing the singleton auth client/provider modules. Vite hot replacement can otherwise retain subscribers from the previous module alongside new actions. Production page lifecycle and logout are covered by integrated provider and browser checks.

## Dashboard

Only the authenticated role's exact section is accepted; mismatched/malformed responses fail safely. Counts stay server-owned. Loading skeletons, safe errors with keyboard Retry and a zero-count notice accompany the metric cards. AccountPanel retains account/sign-out behavior and a STAFF-only, lazy-loaded Employee profile entry point.

Dashboard requests reuse the authentication client's memory-only Bearer token, credentialed fetch and single 401 refresh/retry. Ordinary 403 does not refresh. Effect cleanup aborts requests; scope keys hide old private data immediately on auth/identity/authorization changes. Failures clear all dashboard counts. No dashboard data or authentication state is persisted in browser storage.

## Routes and remaining scaffolds

| Routes | Implemented access |
|---|---|
| `/login`, `/register` | Public; authenticated visitors redirect to `/dashboard` |
| `/dashboard`, `/events`, `/events/:eventId` | USER / STAFF / ADMIN |
| `/registrations` | USER |
| `/rooms`, `/rooms/:roomId`, `/tasks`, `/tasks/:taskId` | STAFF / ADMIN |
| `/inventory`, `/inventory/:itemId`, `/alerts`, `/alerts/:alertId` | STAFF / ADMIN |
| `/analytics`, `/admin/employees`, `/admin/departments` | ADMIN |
| `/` | Waits for bootstrap; redirects to `/dashboard` or `/login`, or shows recoverable session error |
| `*` | Safe not-found page |

Protected paths enforce authentication and the existing role permissions before rendering. Ineligible users see Access denied. `/dashboard` displays authorized backend counts and the read-only `/me` account panel. `/admin/employees` and `/admin/departments` now provide ADMIN management; other business routes remain scaffolds with **no operational hotel data** or operational requests. Hidden navigation is UX only. Backend authorization remains authoritative for identity, role, department and ownership. No dev role switcher or mock login is included in the application.

## Verification

```powershell
npm test -- src/tests/apiClient.test.js src/tests/authUi.test.jsx src/tests/authProvider.test.jsx
npm test -- src/tests/dashboardService.test.js src/tests/dashboardPage.test.jsx
npm test -- src/tests/managementServices.test.js src/tests/managementPages.test.jsx
npm test
npm run test:coverage
npm run lint
npm run typecheck
npm run build
```

Tests cover component/theme behavior, auth/account state, exact dashboard fields, role isolation, zero/loading/error/retry states, 403 without refresh, 401 refresh/retry, stale-response cancellation and private-state clearing. Authentication tests generate synthetic credentials and opaque token values only at runtime in memory; no real credentials/JWT fixtures are saved. Remaining scaffold tests still assert no operational fetch. Coverage omits test helpers and the DOM bootstrap `main.jsx`; it is frontend evidence, not backend coverage.

jsdom cannot prove native dialog focus trapping, real Escape handling, CSS responsiveness, or screen-reader output. Its dialog adapter is documented in `src/tests/setup.js`. Check those behaviors in a real browser as feature forms adopt Modal. The shell should be checked at mobile/tablet/desktop widths, with keyboard navigation and visible focus. No browser behavior is claimed solely from jsdom tests.

For repeatable native-dialog/component QA while Vite dev is running, open `http://localhost:5173/src/tests/browser/ui-fixture.html`. This synthetic test fixture is not an application route, has no authentication/API integration, and is not a production build entry. Check modal Tab/Shift+Tab, Escape and opener-focus restoration.

Employee/Department management includes background-refresh focus restoration and the Modal save-in-progress close guard. Its regression suite passed 278 tests. Browser QA used temporary owned fixtures in `rockey_hospitality_hardening`, removed afterward without resetting that schema. Other business pages and ADMIN analytics beyond the dashboard are not implemented. No AWS, CI/CD or deployment is included. Local startup instructions and backend reproduction are in [../docs/LOCAL_SETUP.md](../docs/LOCAL_SETUP.md); future presentation scope is in [../docs/SIMPLIFICATION_OPTIONS.md](../docs/SIMPLIFICATION_OPTIONS.md).

## Folder map

`src/components/{ui,layout}` holds shared UI/shell/account presentation, `pages/auth` holds forms/session status, `context` and `hooks` hold the safe auth provider/hook, `routes` holds guards and approved permissions, `services` holds the private native-fetch client and public configuration, `utils` holds hotel display context, `styles` holds semantic CSS and `tests` holds focused checks.

## Tooling references

Official setup references: [Vite](https://vite.dev/guide/), [React Router declarative mode](https://reactrouter.com/start/declarative/installation), [TypeScript checkJs](https://www.typescriptlang.org/tsconfig/checkJs.html), [Vitest](https://vitest.dev/guide/). These govern tool usage, not Rockey requirements.
