/*
 * STUDY NOTE: Vite supplies this public API base URL when the browser bundle is built.
 * apiClient imports it and appends the service path. The default points to the local backend.
 * VITE_* values are visible to browser users; never put database passwords or signing keys here.
 * This setting does not configure server CORS, authenticate requests or create a connection.
 */
// VITE_* is bundled into browser-visible code. Public API location only.
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';
