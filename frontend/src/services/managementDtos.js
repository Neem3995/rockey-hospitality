import { ApiError } from './apiClient.js';

/** @typedef {{id: number, name: string}} DepartmentSummary */
/** @typedef {{id: number, name: string, description: string | null, active: boolean, createdAt: string, updatedAt: string}} Department */
/** @typedef {{id: number, userId: number | null, name: string, email: string, department: DepartmentSummary, jobRole: string, status: 'ACTIVE' | 'INACTIVE', createdAt: string, updatedAt: string}} Employee */
/** @typedef {{content: Employee[], page: number, size: number, totalElements: number, totalPages: number, last: boolean}} EmployeePage */
/** @param {unknown} value @returns {value is number} */
export function positiveId(value) { return Number.isSafeInteger(value) && Number(value) > 0; }
/** @param {unknown} value @returns {value is Record<string, unknown>} */
function object(value) { return typeof value === 'object' && value !== null && !Array.isArray(value); }
/** @param {unknown} value @returns {Department} */
export function departmentDto(value) {
  if (!object(value) || !positiveId(value.id) || typeof value.name !== 'string'
      || (value.description !== null && typeof value.description !== 'string') || typeof value.active !== 'boolean'
      || typeof value.createdAt !== 'string' || typeof value.updatedAt !== 'string') throw new ApiError(502);
  return { id: value.id, name: value.name, description: value.description, active: value.active, createdAt: value.createdAt, updatedAt: value.updatedAt };
}
/** @param {unknown} value @returns {Employee} */
export function employeeDto(value) {
  if (!object(value) || !positiveId(value.id) || (value.userId !== null && !positiveId(value.userId))
      || typeof value.name !== 'string' || typeof value.email !== 'string' || typeof value.jobRole !== 'string'
      || !object(value.department) || !positiveId(value.department.id) || typeof value.department.name !== 'string'
      || (value.status !== 'ACTIVE' && value.status !== 'INACTIVE') || typeof value.createdAt !== 'string'
      || typeof value.updatedAt !== 'string') throw new ApiError(502);
  return { id: value.id, userId: value.userId, name: value.name, email: value.email,
    department: { id: value.department.id, name: value.department.name }, jobRole: value.jobRole,
    status: value.status, createdAt: value.createdAt, updatedAt: value.updatedAt };
}
/** @param {unknown} value @returns {EmployeePage} */
export function employeePageDto(value) {
  if (!object(value) || !Array.isArray(value.content) || !Number.isSafeInteger(value.page) || Number(value.page) < 0
      || !positiveId(value.size) || value.size > 100 || !Number.isSafeInteger(value.totalElements) || Number(value.totalElements) < 0
      || !Number.isSafeInteger(value.totalPages) || Number(value.totalPages) < 0 || typeof value.last !== 'boolean') throw new ApiError(502);
  return { content: value.content.map(employeeDto), page: Number(value.page), size: value.size,
    totalElements: Number(value.totalElements), totalPages: Number(value.totalPages), last: value.last };
}
