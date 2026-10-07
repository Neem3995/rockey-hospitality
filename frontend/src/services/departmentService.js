import { apiRequest, ApiError } from './apiClient.js';
import { departmentDto, positiveId } from './managementDtos.js';
/** @typedef {{name: string, description: string}} DepartmentFields */
/** @param {boolean} [active] @param {AbortSignal} [signal] */
export async function listDepartments(active, signal) {
  const value = await apiRequest(`/departments${active === undefined ? '' : `?active=${active}`}`, { signal });
  if (!Array.isArray(value)) throw new ApiError(502);
  return value.map(departmentDto);
}
/** @param {number} id @param {AbortSignal} [signal] */
export async function getDepartment(id, signal) {
  if (!positiveId(id)) throw new ApiError(400);
  const result = departmentDto(await apiRequest(`/departments/${id}`, { signal }));
  if (result.id !== id) throw new ApiError(502);
  return result;
}
/** @param {DepartmentFields} value @param {AbortSignal} [signal] */
export async function createDepartment(value, signal) {
  return departmentDto(await apiRequest('/departments', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name: value.name, description: value.description }), signal }));
}
/** @param {number} id @param {DepartmentFields} value @param {AbortSignal} [signal] */
export async function updateDepartment(id, value, signal) {
  if (!positiveId(id)) throw new ApiError(400);
  return departmentDto(await apiRequest(`/departments/${id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name: value.name, description: value.description }), signal }));
}
/** @param {number} id @param {AbortSignal} [signal] */
export async function deactivateDepartment(id, signal) {
  if (!positiveId(id)) throw new ApiError(400);
  if (await apiRequest(`/departments/${id}`, { method: 'DELETE', signal }) !== null) throw new ApiError(502);
}
