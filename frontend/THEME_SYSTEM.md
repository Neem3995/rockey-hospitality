# Rockey brand and theme system

## Provided assets

Source: the supplied `rockeyLogo` brand-asset folder (outside this repository); the copies live in `src/assets/brand/`.
The explicit filenames and visual inspection identify `Light_Mode_Logo.png` for light and `Dark_Mode_Logo.png` for dark. Both are 1024 × 1024 PNGs with their own backgrounds and internal whitespace.

The two original files are copied once into `frontend/src/assets/brand/` so this frontend remains independently buildable. `AppLayout.jsx` imports them; Vite fingerprints each once in the production assets. No cropping, editing, redrawing, generated logos, or external asset-path dependency is used. SHA-256 matches the supplied originals:

| Asset | SHA-256 |
|---|---|
| Light_Mode_Logo.png | `F29D18C056FCAA0B201787D5449C6A4B9CC7953587613EFA20D4EA3D1C05BA0C` |
| Dark_Mode_Logo.png | `B536FC525246EC6028440C8D6788F59CB288065979D988CC12365129C812083B` |

Images retain square aspect ratio via explicit dimensions and `object-fit: contain`. They are decorative inside the link named **Rockey home**, with empty alt text and an aria-hidden wrapper to avoid repeating the accessible name. CSS displays only the matching image.

## Palette derivation

Actual light-logo pixel samples: near-white `#fefefe`, navy `#041829` / `#08223c` / `#0a2f4e`, blue `#1c6192`, cyan `#49d0d7` / `#41cdd6`. The light surfaces are pale navy tints; the cyan accent is darkened to `#006f7b` for readable text. Navy carries navigation and primary actions; blue carries focus. Saturated colors are confined to actions, focus, and small accents.

Actual dark-logo pixel samples: background `#0d1c2e` (nearby gradient pixels vary), navy `#123252`, blue `#3875ad`, cyan `#63dde2`, near-white `#fefefe`. The dark surfaces extend these navy shades; cyan carries actions, accents and focus. Both modes use the same layout and semantic roles.

Success and warning are supporting functional green/amber hues with theme-specific luminance, rather than claiming those colors occur in the logos. Danger red supports accessible error states. These functional exceptions do not change the navy/blue/cyan brand palette. Light input borders use the logo-navy gray `#6d8091`, and disabled surfaces use `#edf0f3`, a near-white tint of the light logo's `#041829` navy (approximately 7% navy / 93% near-white). This preserves input-border contrast while aligning its hue with the brand. Dark tokens use the same component semantics.

## Semantic tokens

Defined centrally in `src/styles/index.css`: `:root` defaults to light; `:root[data-theme="dark"]` overrides the same roles. Components use `var(...)`; literal component colors are prohibited by a focused regression test. Future pages, controls, and status messages must consume these roles.

| Token | Light | Dark |
|---|---|---|
| `--background` | `#f3f6f9` | `#0d1c2e` |
| `--surface` | `#fefefe` | `#10243b` |
| `--surface-elevated` | `#ffffff` | `#123252` |
| `--text` | `#041829` | `#fefefe` |
| `--muted-text` | `#465e73` | `#bfd1e0` |
| `--border` | `#6d8091` | `#8094a7` |
| `--input-border` | `#6d8091` | `#8094a7` |
| `--primary` | `#08223c` | `#63dde2` |
| `--primary-hover` | `#0a2f4e` | `#8ae8ec` |
| `--primary-active` | `#041829` | `#49d0d7` |
| `--on-primary` | `#fefefe` | `#041829` |
| `--accent` | `#006f7b` | `#63dde2` |
| `--accent-surface` | `#e2f3f5` | `#123252` |
| `--focus` | `#1c6192` | `#63dde2` |
| `--success` | `#146348` | `#78deb7` |
| `--warning` | `#795006` | `#f2cb78` |
| `--danger` | `#a82b2b` | `#ffabab` |
| `--danger-hover` | `#8a2020` | `#ffc5c5` |
| `--on-danger` | `#ffffff` | `#041829` |
| `--disabled-surface` | `#edf0f3` | `#182d44` |
| `--disabled-text` | `#526273` | `#bfd1e0` |
| `--shadow` | `#0418290d` | `#00000033` |
| `--backdrop` | `#041829b3` | `#041829cc` |

`--surface-elevated` supplies dialogs; navigation and table headers use `--accent-surface`. Status helpers `.status-success`, `.status-warning`, `.status-danger` consume the status tokens; input errors retain associated text and invalid borders. Disabled controls remain legible without whole-control opacity. Focus uses a 3px outline with 4px offset. No theme transition animation is introduced; the existing spinner respects `prefers-reduced-motion`.

## Theme preference and security boundary

`ThemeToggle.jsx` owns the shared shell's theme state. It initializes with `readThemePreference()` and updates the root `data-theme` and inline native `color-scheme` through an effect. Native button semantics support Enter/Space, a meaningful changing aria-label/title and a visible focus indicator. Routes inherit the document theme without reloading. The QA fixture uses the same toggle independently.

Only exact saved `dark` chooses dark; saved `light`, missing, invalid, or denied reads yield light. OS dark preference does not override the first-visit light default. Only explicit toggles call `saveThemePreference()`. If a write is denied the user can still toggle the current page; a later visit without readable preference defaults to light. No other storage keys are read, overwritten, or cleared by application code.

**localStorage is approved only for non-sensitive UI preference (`rockey-theme`: `light` / `dark`). Never store JWTs, refresh tokens, credentials, roles, or authentication state in localStorage or sessionStorage.** Theme code does not access sessionStorage, authentication state or cookies. Authentication is implemented separately with memory-only access JWT and an HttpOnly refresh cookie.

## Verification

Run `npm test -- src/tests/theme.test.jsx src/tests/themeContrast.test.js src/tests/components.test.jsx`, `npm run test:coverage`, `npm run lint`, `npm run typecheck`, and `npm run build` from `frontend/`. The contrast tests calculate WCAG relative luminance directly from the actual CSS tokens, including hover/active, elevated/disabled surfaces and status text. jsdom tests assert behavior and document state; actual logo visibility and CSS responsiveness require browser checks.

For shared control QA start Vite and open `/src/tests/browser/ui-fixture.html`, then test both themes with its toggle. The fixture includes primary/secondary/danger, pending/disabled, normal/error/disabled inputs, success/warning text, table and native modal examples. It is not a production entry or feature route. Check native keyboard/dialog behavior in a real browser; jsdom results alone cannot prove it.
