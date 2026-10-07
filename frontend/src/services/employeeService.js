import { apiRequest, ApiError } from './apiClient.js';
import { employeeDto, employeePageDto, positiveId } from './managementDtos.js';

export const employeeSortFields = ['name', 'email', 'jobRole', 'status', 'createdAt'];
/** @typedef {{name: string, email: string, departmentId: number, jobRole: string}} EmployeeFields */
/** @typedef {EmployeeFields & {createLogin: boolean, loginEmail?: string, temporaryPassword?: string, securityRole?: 'STAFF' | 'ADMIN'}} CreateEmployee */
/** @typedef {EmployeeFields & {status: 'ACTIVE' | 'INACTIVE'}} UpdateEmployee */
/** @typedef {{departmentId?: number, status?: 'ACTIVE' | 'INACTIVE', page?: number, size?: number, sort?: string}} EmployeeFilters */
/** @param {EmployeeFilters} [filters] @param {AbortSignal} [signal] */
export async function listEmployees({ departmentId, status, page = 0, size = 20, sort = 'name,asc' } = {}, signal) {
  const [field, direction, extra] = sort.split(',');
  if (!Number.isSafeInteger(page) || page < 0 || !Number.isSafeInteger(size) || size < 1 || size > 100
      || !employeeSortFields.includes(field) || !['asc', 'desc'].includes(direction) || extra !== undefined
      || (departmentId !== undefined && !positiveId(departmentId)) || (status !== undefined && !['ACTIVE', 'INACTIVE'].includes(status))) throw new ApiError(400);
  const query = new URLSearchParams({ page: String(page), size: String(size), sort });
  if (departmentId !== undefined) query.set('departmentId', String(departmentId));
  if (status !== undefined) query.set('status', status);
  return employeePageDto(await apiRequest(`/employees?${query}`, { signal }));
}
/** @param {number} id @param {AbortSignal} [signal] */
export async function getEmployee(id, signal) {
  if (!positiveId(id)) throw new ApiError(400);
  const result = employeeDto(await apiRequest(`/employees/${id}`, { signal }));
  if (result.id !== id) throw new ApiError(502);
  return result;
}
/** @param {EmployeeFields} value */
function fields(value) { return { name: value.name, email: value.email, departmentId: value.departmentId, jobRole: value.jobRole }; }
/** @param {CreateEmployee} value @param {AbortSignal} [signal] */
export async function createEmployee(value, signal) {
  const body = value.createLogin
    ? { ...fields(value), createLogin: true, loginEmail: value.loginEmail, temporaryPassword: value.temporaryPassword, securityRole: value.securityRole }
    : { ...fields(value), createLogin: false };
  return employeeDto(await apiRequest('/employees', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body), signal }));
}
/** @param {number} id @param {UpdateEmployee} value @param {AbortSignal} [signal] */
export async function updateEmployee(id, value, signal) {
  if (!positiveId(id)) throw new ApiError(400);
  return employeeDto(await apiRequest(`/employees/${id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...fields(value), status: value.status }), signal }));
}
/** @param {number} id @param {AbortSignal} [signal] */
export async function deactivateEmployee(id, signal) {
  if (!positiveId(id)) throw new ApiError(400);
  if (await apiRequest(`/employees/${id}`, { method: 'DELETE', signal }) !== null) throw new ApiError(502);
}
