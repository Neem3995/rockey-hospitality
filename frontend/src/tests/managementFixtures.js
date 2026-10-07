/** @type {import('../services/managementDtos.js').Department} */
export const department = { id: 10, name: 'Housekeeping fixture', description: 'Synthetic department', active: true, createdAt: '2026-10-06T10:00:00', updatedAt: '2026-10-06T11:00:00' };
/** @type {import('../services/managementDtos.js').Employee} */
export const employee = { id: 2, userId: 1, name: 'Employee fixture', email: 'employee@example.test', department: { id: department.id, name: department.name }, jobRole: 'Housekeeper', status: 'ACTIVE', createdAt: department.createdAt, updatedAt: department.updatedAt };
/** @param {import('../services/managementDtos.js').Employee[]} [rows] @returns {import('../services/managementDtos.js').EmployeePage} */
export function employeePage(rows = [employee]) { return { content: rows, page: 0, size: 20, totalElements: rows.length, totalPages: rows.length ? 1 : 0, last: true }; }
/** @template T */
export function deferred() {
  /** @type {(value: T) => void} */
  let resolve = () => {};
  const promise = new Promise((done) => { resolve = done; });
  return { promise, resolve };
}
