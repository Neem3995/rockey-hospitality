import { apiRequest, ApiError, StaleRequestError } from './apiClient.js';

/** @typedef {import('./apiClient.js').Role} Role */
/** @typedef {{key: string, label: string, description: string}} Metric */
/** @typedef {{role: Role, asOf: string, section: Record<string, number>}} Dashboard */

// These are presentation labels for the frozen fields, not calculated metrics.
/** @type {Record<Role, Metric[]>} */
export const dashboardMetrics = {
  USER: [
    { key: 'registrationCount', label: 'Your registrations', description: 'Retained registrations, including cancelled and completed event history.' },
  ],
  STAFF: [
    { key: 'nonTerminalAssignedTaskCount', label: 'Your assigned tasks', description: 'Your OPEN, ASSIGNED and IN_PROGRESS tasks.' },
    { key: 'overdueAssignedTaskCount', label: 'Your overdue tasks', description: 'Your non-terminal tasks with a due time before this snapshot.' },
    { key: 'unreadAlertCount', label: 'Your unread alerts', description: 'Your UNREAD alerts.' },
    { key: 'unresolvedAlertCount', label: 'Your unresolved alerts', description: 'Your UNREAD and READ alerts.' },
    { key: 'activeInventoryItemCount', label: 'Department inventory items', description: 'Active inventory items in your department only.' },
    { key: 'lowStockItemCount', label: 'Department low-stock items', description: 'Active items in your department at or below their reorder threshold.' },
  ],
  ADMIN: [
    { key: 'activeRoomCount', label: 'Active rooms', description: 'All active rooms.' },
    { key: 'readyRoomCount', label: 'Ready rooms', description: 'Active rooms with READY status.' },
    { key: 'nonTerminalTaskCount', label: 'Non-terminal tasks', description: 'All OPEN, ASSIGNED and IN_PROGRESS tasks.' },
    { key: 'overdueTaskCount', label: 'Overdue tasks', description: 'Non-terminal tasks with a due time before this snapshot.' },
    { key: 'completedTaskCount', label: 'Completed tasks', description: 'All COMPLETED tasks, including history.' },
    { key: 'activeDepartmentCount', label: 'Active departments', description: 'All active departments.' },
    { key: 'unresolvedAlertCount', label: 'Unresolved alerts', description: 'All UNREAD and READ alerts, including inactive-recipient history.' },
    { key: 'activeInventoryItemCount', label: 'Active inventory items', description: 'All active inventory items.' },
    { key: 'lowStockItemCount', label: 'Low-stock items', description: 'Active inventory items at or below their reorder threshold.' },
    { key: 'nonTerminalEventCount', label: 'Non-terminal events', description: 'All DRAFT, OPEN, CLOSED and IN_PROGRESS events.' },
    { key: 'registrationCount', label: 'Retained registrations', description: 'All retained registrations, regardless of event or user status.' },
  ],
};

/** @param {unknown} value @returns {Record<string, unknown>} */
function object(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new ApiError(502);
  return /** @type {Record<string, unknown>} */ (value);
}

/** @param {unknown} value @returns {number} */
function count(value) {
  if (!Number.isSafeInteger(value) || Number(value) < 0) throw new ApiError(502);
  return /** @type {number} */ (value);
}

/** Fetch only the role-specific dashboard; the server owns all counts/scope.
 * @param {Role} role @param {AbortSignal} signal @returns {Promise<Dashboard>}
 */
export async function getDashboard(role, signal) {
  const value = await apiRequest('/analytics/dashboard', { signal });
  if (signal.aborted) throw new StaleRequestError();
  const response = object(value);
  if (response.role !== role || typeof response.asOf !== 'string'
      || !/(?:Z|[+-]\d{2}:\d{2})$/.test(response.asOf)
      || !Number.isFinite(Date.parse(response.asOf))) throw new ApiError(502);
  const sectionName = role.toLowerCase();
  for (const other of ['user', 'staff', 'admin']) {
    if (other !== sectionName && response[other] !== undefined) throw new ApiError(502);
  }
  const source = object(response[sectionName]);
  /** @type {Record<string, number>} */
  const section = {};
  for (const { key } of dashboardMetrics[role]) section[key] = count(source[key]);
  if (role === 'STAFF') {
    for (const key of ['employeeId', 'departmentId']) {
      section[key] = count(source[key]);
      if (section[key] === 0) throw new ApiError(502);
    }
  }
  // Unknown fields (including credentials/tokens) never enter view state.
  return { role, asOf: response.asOf, section };
}
