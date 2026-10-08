# Rockey theme

Unchanged supplied Light_Mode_Logo.png/Dark_Mode_Logo.png assets live in src/assets/brand/.
AppLayout imports both; CSS displays the matching decorative logo inside the named Rockey home link.

AppLayout's effect sets document data-theme/native color-scheme. Its keyboard-operable toggle uses theme.js to persist only rockey-theme.
Missing/invalid/denied storage defaults to light; no tokens/credentials/role/auth state is stored.

Small semantic CSS palette: bg, surface, text, muted, border, accent, on-accent, danger, focus.
Light retains navy/blue/pale surfaces; dark retains navy/cyan. Shared components consume these variables.

Visible focus/skip link, wrapping navigation/actions, keyboard-focusable table-scroll regions and reduced-motion spinner support basic accessibility.
Modal preserves controlled open state, traps Tab/Shift+Tab, restores opener/fallback focus and resists forced browser close while saving.
Tests cover preference, logos, keyboard controls/dialog and token contrast. jsdom is not native-browser/responsive evidence.
