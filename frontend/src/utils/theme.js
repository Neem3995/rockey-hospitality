/*
 * STUDY NOTE: AppLayout calls these helpers to read/save light or dark preference.
 * Only rockey-theme goes in localStorage, never auth data; denied storage safely falls back to light
 * or leaves the current in-memory choice usable. AppLayout applies the document attributes.
 * This module does not choose account permissions, fetch data or manage a session.
 */
/** @typedef {'light' | 'dark'} Theme */

export const THEME_STORAGE_KEY = 'rockey-theme';

/** Only a non-sensitive UI preference is read. Storage can be denied by browsers.
 * @returns {Theme}
 */
export function readThemePreference() {
  try {
    return window.localStorage.getItem(THEME_STORAGE_KEY) === 'dark' ? 'dark' : 'light';
  } catch {
    return 'light';
  }
}

/** @param {Theme} theme */
export function saveThemePreference(theme) {
  try {
    window.localStorage.setItem(THEME_STORAGE_KEY, theme);
  } catch {
    // A denied write must not prevent the current page's theme from changing.
  }
}
