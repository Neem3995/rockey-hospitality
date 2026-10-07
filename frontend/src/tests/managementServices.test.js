import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { testUser } from './authTestHelpers.jsx';
import { department, employee, employeePage } from './managementFixtures.js';

/** @type {typeof import('../services/apiClient.js')} */ let client;
/** @type {typeof import('../services/employeeService.js')} */ let employees;
/** @type {typeof import('../services/departmentService.js')} */ let departments;
const fetchMock = vi.fn();
/** @param {unknown} [value] @param {number} [status] */
const reply = (value = {}, status = 200) => new Response(status === 204 ? null : JSON.stringify(value), { status });
beforeEach(async () => {
  vi.resetModules(); fetchMock.mockReset(); vi.stubGlobal('fetch', fetchMock);
  client = await import('../services/apiClient.js');
  employees = await import('../services/employeeService.js');
  departments = await import('../services/departmentService.js');
  fetchMock.mockResolvedValueOnce(reply({ accessToken: crypto.randomUUID(), tokenType: 'Bearer', accessExpiresAt: new Date(Date.now() + 60000).toISOString() })).mockResolvedValueOnce(reply(testUser('ADMIN')));
  await client.bootstrapAuth(); fetchMock.mockClear();
});
afterEach(() => vi.unstubAllGlobals());
describe('FE-04 authenticated services and safe DTOs', () => {
  it('uses default server paging, credentialed Bearer requests and strips unexpected fields', async () => {
    fetchMock.mockResolvedValueOnce(reply({ ...employeePage(), content: [{ ...employee, passwordHash: crypto.randomUUID(), temporaryPassword: crypto.randomUUID() }] }));
    expect(await employees.listEmployees()).toEqual(employeePage());
    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe('http://localhost:8080/api/employees?page=0&size=20&sort=name%2Casc');
    expect(options.credentials).toBe('include'); expect(options.headers.get('Authorization')).toMatch(/^Bearer /);
  });
  it('uses exact employee filters and department active filter without invented pagination', async () => {
    fetchMock.mockResolvedValueOnce(reply(employeePage())).mockResolvedValueOnce(reply([department]));
    await employees.listEmployees({ page: 1, size: 100, sort: 'createdAt,desc', departmentId: 10, status: 'INACTIVE' });
    await departments.listDepartments(false);
    expect(fetchMock.mock.calls[0][0]).toContain('page=1&size=100&sort=createdAt%2Cdesc&departmentId=10&status=INACTIVE');
    expect(fetchMock.mock.calls[1][0]).toBe('http://localhost:8080/api/departments?active=false');
  });
  it.each([{ page: -1 }, { size: 0 }, { size: 101 }, { size: 1.5 }, { departmentId: 0 }, { sort: 'passwordHash,asc' }, { sort: 'name,ASC' }, { sort: 'name,asc,extra' }])('rejects invalid paging/sort/filter %j before sending', async (filters) => {
    await expect(employees.listEmployees(filters)).rejects.toMatchObject({ status: 400 }); expect(fetchMock).not.toHaveBeenCalled();
  });
  it('creates without a login and sends no leftover provisioning/unknown fields', async () => {
    fetchMock.mockResolvedValueOnce(reply({ ...employee, userId: null }, 201));
    const value = { name: employee.name, email: employee.email, departmentId: 10, jobRole: employee.jobRole, createLogin: false, loginEmail: 'unused@example.test', temporaryPassword: crypto.randomUUID(), role: 'ADMIN' };
    expect((await employees.createEmployee(value)).userId).toBeNull();
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ name: employee.name, email: employee.email, departmentId: 10, jobRole: employee.jobRole, createLogin: false });
  });
  it.each(['STAFF', 'ADMIN'])('provisions only the canonical %s create fields', async (role) => {
    fetchMock.mockResolvedValueOnce(reply(employee, 201));
    const value = { name: employee.name, email: employee.email, departmentId: 10, jobRole: employee.jobRole, createLogin: true, loginEmail: 'internal@example.test', temporaryPassword: crypto.randomUUID(), securityRole: /** @type {'STAFF' | 'ADMIN'} */ (role) };
    expect(await employees.createEmployee(value)).toEqual(employee);
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual(value);
  });
  it('sends complete Employee PUT but no login fields; reads and soft-deactivates by exact id', async () => {
    fetchMock.mockResolvedValueOnce(reply(employee)).mockResolvedValueOnce(reply(employee)).mockResolvedValueOnce(reply(null, 204));
    const value = { name: employee.name, email: employee.email, departmentId: 10, jobRole: employee.jobRole, status: employee.status, temporaryPassword: crypto.randomUUID() };
    await employees.updateEmployee(2, value); await employees.getEmployee(2); await employees.deactivateEmployee(2);
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ name: value.name, email: value.email, departmentId: 10, jobRole: value.jobRole, status: value.status });
    expect(fetchMock.mock.calls.map((call) => call[1].method || 'GET')).toEqual(['PUT', 'GET', 'DELETE']);
  });
  it('creates/updates/reads/deactivates departments with no lifecycle field in PUT', async () => {
    fetchMock.mockResolvedValueOnce(reply(department, 201)).mockResolvedValueOnce(reply(department)).mockResolvedValueOnce(reply(department)).mockResolvedValueOnce(reply(null, 204));
    const value = { name: department.name, description: '', active: false };
    await departments.createDepartment(value); await departments.updateDepartment(10, value); await departments.getDepartment(10); await departments.deactivateDepartment(10);
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({ name: value.name, description: '' });
    expect(fetchMock.mock.calls.map((call) => call[1].method || 'GET')).toEqual(['POST', 'PUT', 'GET', 'DELETE']);
  });
  it.each(['Employee email is already registered.', 'Login email is already registered.', 'Department name already exists.', 'Employee cannot be deactivated while active tasks are assigned.', 'Department cannot be deactivated while active employees are assigned.', 'Department cannot be deactivated while non-terminal tasks exist.', 'Department cannot be deactivated while active inventory exists.', 'Employees cannot be assigned to an inactive department.'])('allows fixed canonical conflict: %s', async (message) => {
    fetchMock.mockResolvedValueOnce(reply({ message }, 409));
    await expect(employees.getEmployee(2)).rejects.toMatchObject({ status: 409, message });
  });
  it('sanitizes domain field errors and arbitrary private messages', async () => {
    fetchMock.mockResolvedValueOnce(reply({ message: crypto.randomUUID(), fieldErrors: { departmentId: crypto.randomUUID(), temporaryPassword: crypto.randomUUID(), passwordHash: crypto.randomUUID() } }, 400));
    await expect(employees.getEmployee(2)).rejects.toMatchObject({ status: 400, fieldErrors: { departmentId: 'Check this value.', temporaryPassword: 'Check this value.' } });
    fetchMock.mockResolvedValueOnce(reply({ message: 'Unsafe private message' }, 409));
    await expect(employees.getEmployee(2)).rejects.toMatchObject({ message: 'Request conflicts with existing data.' });
  });
  it.each([403, 404, 429, 500])('handles %s without refreshing ordinary non-401 failures', async (status) => {
    fetchMock.mockResolvedValueOnce(reply({}, status)); await expect(employees.getEmployee(2)).rejects.toMatchObject({ status }); expect(fetchMock).toHaveBeenCalledTimes(1);
  });
  it('does not retry a network write automatically', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Synthetic failure'));
    await expect(departments.createDepartment({ name: 'Fixture', description: '' })).rejects.toMatchObject({ status: 0 }); expect(fetchMock).toHaveBeenCalledTimes(1);
  });
  it('reuses the verified 401 refresh/me/one-retry flow for Employee reads', async () => {
    fetchMock.mockResolvedValueOnce(reply({}, 401))
      .mockResolvedValueOnce(reply({ accessToken: crypto.randomUUID(), tokenType: 'Bearer', accessExpiresAt: new Date(Date.now() + 60000).toISOString() }))
      .mockResolvedValueOnce(reply(testUser('ADMIN'))).mockResolvedValueOnce(reply(employee));
    expect(await employees.getEmployee(2)).toEqual(employee);
    expect(fetchMock.mock.calls.map((call) => new URL(call[0]).pathname)).toEqual(['/api/employees/2', '/api/auth/refresh', '/api/auth/me', '/api/employees/2']);
  });
  it.each([{ ...employee, status: 'UNKNOWN' }, { ...employee, userId: 0 }, { ...employee, department: null }, { ...employee, id: 3 }])('rejects malformed or wrong-id detail', async (value) => {
    fetchMock.mockResolvedValueOnce(reply(value)); await expect(employees.getEmployee(2)).rejects.toMatchObject({ status: 502 });
  });
  it.each([{ ...employeePage(), size: 101 }, { ...employeePage(), totalPages: -1 }, { ...employeePage(), content: {} }])('rejects malformed page', async (value) => {
    fetchMock.mockResolvedValueOnce(reply(value)); await expect(employees.listEmployees()).rejects.toMatchObject({ status: 502 });
  });
  it('rejects malformed department lists, wrong ids, and non-204 deletion responses', async () => {
    fetchMock.mockResolvedValueOnce(reply({})).mockResolvedValueOnce(reply({ ...department, id: 11 })).mockResolvedValueOnce(reply(department));
    await expect(departments.listDepartments()).rejects.toMatchObject({ status: 502 });
    await expect(departments.getDepartment(10)).rejects.toMatchObject({ status: 502 });
    await expect(departments.deactivateDepartment(10)).rejects.toMatchObject({ status: 502 });
  });
  it('rejects invalid resource ids before any request', async () => {
    await expect(employees.getEmployee(0)).rejects.toMatchObject({ status: 400 }); await expect(employees.updateEmployee(0, { ...employee, departmentId: 10 })).rejects.toMatchObject({ status: 400 });
    await expect(employees.deactivateEmployee(0)).rejects.toMatchObject({ status: 400 }); await expect(departments.getDepartment(0)).rejects.toMatchObject({ status: 400 });
    await expect(departments.updateDepartment(0, { name: 'Fixture', description: '' })).rejects.toMatchObject({ status: 400 }); await expect(departments.deactivateDepartment(0)).rejects.toMatchObject({ status: 400 }); expect(fetchMock).not.toHaveBeenCalled();
  });
});
