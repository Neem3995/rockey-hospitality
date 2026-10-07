import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../App.jsx';
import EmployeesPage from '../pages/admin/EmployeesPage.jsx';
import DepartmentsPage from '../pages/admin/DepartmentsPage.jsx';
import AccountPanel from '../components/layout/AccountPanel.jsx';
import EmployeeForm from '../components/employees/EmployeeForm.jsx';
import DepartmentForm from '../components/departments/DepartmentForm.jsx';
import { TestAuth, testUser } from './authTestHelpers.jsx';
import { department, employee, employeePage, deferred } from './managementFixtures.js';
import * as employees from '../services/employeeService.js';
import * as departments from '../services/departmentService.js';
import { ApiError } from '../services/apiClient.js';

vi.mock('../services/employeeService.js', async (importOriginal) => ({ ...await importOriginal(), listEmployees: vi.fn(), getEmployee: vi.fn(), createEmployee: vi.fn(), updateEmployee: vi.fn(), deactivateEmployee: vi.fn() }));
vi.mock('../services/departmentService.js', () => ({ listDepartments: vi.fn(), getDepartment: vi.fn(), createDepartment: vi.fn(), updateDepartment: vi.fn(), deactivateDepartment: vi.fn() }));
const listEmployees = vi.mocked(employees.listEmployees);
const getEmployee = vi.mocked(employees.getEmployee);
const createEmployee = vi.mocked(employees.createEmployee);
const updateEmployee = vi.mocked(employees.updateEmployee);
const deactivateEmployee = vi.mocked(employees.deactivateEmployee);
const listDepartments = vi.mocked(departments.listDepartments);
const getDepartment = vi.mocked(departments.getDepartment);
const createDepartment = vi.mocked(departments.createDepartment);
const updateDepartment = vi.mocked(departments.updateDepartment);
const deactivateDepartment = vi.mocked(departments.deactivateDepartment);
beforeEach(() => {
  listEmployees.mockReset().mockResolvedValue(employeePage()); getEmployee.mockReset().mockResolvedValue(employee);
  createEmployee.mockReset().mockResolvedValue(employee); updateEmployee.mockReset().mockResolvedValue(employee); deactivateEmployee.mockReset().mockResolvedValue(undefined);
  listDepartments.mockReset().mockResolvedValue([department]); getDepartment.mockReset().mockResolvedValue(department);
  createDepartment.mockReset().mockResolvedValue(department); updateDepartment.mockReset().mockResolvedValue(department); deactivateDepartment.mockReset().mockResolvedValue(undefined);
});
/** @param {import('react').ReactNode} children @param {import('../services/apiClient.js').Role} [role] */
function mount(children, role = 'ADMIN') { return render(<TestAuth value={{ status: 'authenticated', user: testUser(role) }}>{children}</TestAuth>); }
/** @param {string} label @param {string} value */
function fill(label, value) { fireEvent.change(screen.getByLabelText(label), { target: { value } }); }
async function employeeFields() {
  fill('Employee name (required)', 'New employee'); fill('Employee email (required)', 'new@example.test'); fill('Job role (required)', 'Housekeeper');
  await userEvent.selectOptions(screen.getByLabelText('Department (required)'), '10');
}
describe('FE-04 pages, forms and role boundaries', () => {
  it.each(['/admin/employees', '/admin/departments'])('replaces %s scaffold with management for ADMIN', async (path) => {
    mount(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>);
    expect(await screen.findByRole('table')).toBeTruthy(); expect(screen.queryByText(/No hotel operational data is loaded/)).toBeNull();
  });
  it.each(['USER', 'STAFF'])('denies %s direct page access without operational requests', (value) => {
    mount(<><EmployeesPage /><DepartmentsPage /></>, /** @type {'USER' | 'STAFF'} */ (value));
    expect(screen.getAllByRole('alert')).toHaveLength(2); expect(listEmployees).not.toHaveBeenCalled(); expect(listDepartments).not.toHaveBeenCalled();
  });
  it.each([['USER', '/admin/employees'], ['STAFF', '/admin/departments']])('route guard denies %s visiting %s', (valuesRole, path) => {
    mount(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>, /** @type {'USER' | 'STAFF'} */ (valuesRole));
    expect(screen.getByRole('heading', { name: 'Access denied' })).toBeTruthy(); expect(listEmployees).not.toHaveBeenCalled(); expect(listDepartments).not.toHaveBeenCalled();
  });
  it('loads employees with defaults, captions, login/status text and server-only paging', async () => {
    mount(<EmployeesPage />); await screen.findByRole('table');
    expect(listEmployees.mock.calls[0][0]).toEqual({ page: 0, size: 20, sort: 'name,asc' });
    expect(screen.getByText('Employee records')).toBeTruthy(); expect(screen.getByText('Linked account')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Previous page' }).hasAttribute('disabled')).toBe(true);
  });
  it('applies canonical filters/sort/page size and resets page on filter changes', async () => {
    listEmployees.mockResolvedValue({ ...employeePage(), last: false, totalPages: 2, totalElements: 21 });
    mount(<EmployeesPage />); await screen.findByRole('table'); await userEvent.click(screen.getByRole('button', { name: 'Next page' }));
    await waitFor(() => expect(listEmployees.mock.calls.at(-1)?.[0]?.page).toBe(1));
    await userEvent.selectOptions(screen.getByLabelText('Filter employee status'), 'INACTIVE');
    await userEvent.selectOptions(screen.getByLabelText('Filter department'), '10');
    await userEvent.selectOptions(screen.getByLabelText('Sort employees'), 'email,desc');
    await userEvent.selectOptions(screen.getByLabelText('Employees per page'), '100');
    await waitFor(() => expect(listEmployees.mock.calls.at(-1)?.[0]).toEqual({ page: 0, size: 100, sort: 'email,desc', departmentId: 10, status: 'INACTIVE' }));
  });
  it('department filters use a plain list and do not render pagination', async () => {
    mount(<DepartmentsPage />); await screen.findByRole('table');
    await userEvent.selectOptions(screen.getByLabelText('Filter department status'), 'false');
    await waitFor(() => expect(listDepartments.mock.calls.at(-1)?.[0]).toBe(false)); expect(screen.queryByRole('navigation', { name: 'Employee pagination' })).toBeNull();
  });
  it.each(['employee', 'department'])('shows empty %s data honestly', async (kind) => {
    listEmployees.mockResolvedValue(employeePage([])); listDepartments.mockResolvedValue([]);
    mount(kind === 'employee' ? <EmployeesPage /> : <DepartmentsPage />);
    expect(await screen.findByText(kind === 'employee' ? 'No employees match these filters.' : 'No departments match this filter.')).toBeTruthy();
  });
  it.each([0, 403, 404, 429, 500])('safe %s read failures remove private data and allow keyboard retry', async (status) => {
    listDepartments.mockRejectedValueOnce(new ApiError(status)); mount(<DepartmentsPage />);
    expect((await screen.findByRole('alert')).textContent).toBe(new ApiError(status).message); expect(screen.queryByRole('table')).toBeNull();
    screen.getByRole('button', { name: 'Retry' }).focus(); await userEvent.keyboard('{Enter}'); await screen.findByRole('table'); expect(listDepartments).toHaveBeenCalledTimes(2);
  });
  it('employee read and department lookup failures remain retryable without stale table data', async () => {
    listDepartments.mockRejectedValueOnce(new ApiError(500)); mount(<EmployeesPage />);
    await screen.findByRole('alert'); expect(screen.queryByRole('table')).toBeNull(); await userEvent.click(screen.getByRole('button', { name: 'Retry' })); await screen.findByRole('table');
  });
  it('ignores stale reads after filters change', async () => {
    const old = deferred(); listDepartments.mockReturnValueOnce(/** @type {Promise<import('../services/managementDtos.js').Department[]>} */ (old.promise));
    mount(<DepartmentsPage />); expect(screen.getByRole('status').textContent).toContain('Loading');
    await userEvent.selectOptions(screen.getByLabelText('Filter department status'), 'true'); await screen.findByRole('table');
    await act(async () => old.resolve([{ ...department, name: 'Private stale department' }])); expect(screen.queryByText('Private stale department')).toBeNull();
    expect(listDepartments.mock.calls[0][1]?.aborted).toBe(true);
  });
  it.each(['USER', 'STAFF'])('clears ADMIN records immediately on role change to %s', async (value) => {
    const view = mount(<EmployeesPage />); await screen.findByRole('table');
    view.rerender(<TestAuth value={{ status: 'authenticated', user: testUser(/** @type {'USER' | 'STAFF'} */ (value)), sessionKey: 2 }}><EmployeesPage /></TestAuth>);
    expect(screen.queryByText(employee.name)).toBeNull(); expect(screen.queryByRole('table')).toBeNull();
  });
  it('clears department data immediately on logout and ignores a late response', async () => {
    const late = deferred(); listDepartments.mockReturnValueOnce(/** @type {Promise<import('../services/managementDtos.js').Department[]>} */ (late.promise)); const view = mount(<DepartmentsPage />);
    view.rerender(<TestAuth><DepartmentsPage /></TestAuth>); await act(async () => late.resolve([department])); expect(screen.queryByText(department.name)).toBeNull();
  });
  it('clears a previous ADMIN identity and reloads without keeping their dialog', async () => {
    const view = mount(<EmployeesPage />); await screen.findByRole('table'); await userEvent.click(screen.getByRole('button', { name: 'Create employee' }));
    const next = deferred(); listEmployees.mockReturnValueOnce(/** @type {Promise<import('../services/managementDtos.js').EmployeePage>} */ (next.promise));
    view.rerender(<TestAuth value={{ status: 'authenticated', user: { ...testUser('ADMIN'), id: 99, employeeId: 77 }, sessionKey: 2 }}><EmployeesPage /></TestAuth>);
    expect(screen.queryByRole('table')).toBeNull(); expect(screen.queryByRole('dialog')).toBeNull(); expect(screen.getByRole('status').textContent).toContain('Loading');
    await act(async () => next.resolve(employeePage([]))); await screen.findByText('No employees match these filters.');
  });
  it('reads Employee detail by id, edits complete writable fields and re-fetches the list', async () => {
    mount(<EmployeesPage />); await screen.findByRole('table'); await userEvent.click(screen.getByRole('button', { name: `View employee ${employee.name}` }));
    expect(await screen.findByText(employee.email)).toBeTruthy(); expect(screen.getByText(employee.createdAt.replace('T', ' '))).toBeTruthy();
    await userEvent.click(screen.getByRole('button', { name: 'Close Employee details' })); await screen.findByRole('table');
    await userEvent.click(screen.getByRole('button', { name: `Edit employee ${employee.name}` })); await screen.findByLabelText('Employee name (required)'); fill('Job role (required)', 'Supervisor');
    expect(screen.queryByLabelText('Create application login')).toBeNull(); await userEvent.click(screen.getByRole('button', { name: 'Save employee' }));
    await waitFor(() => expect(updateEmployee).toHaveBeenCalledWith(2, { name: employee.name, email: employee.email, departmentId: 10, jobRole: 'Supervisor', status: 'ACTIVE' }, expect.any(AbortSignal)));
    await screen.findByText(/Employee change confirmed/); expect(listEmployees.mock.calls.length).toBeGreaterThan(1);
  });
  it('creates Employee without a User or provisioning fields and refetches', async () => {
    mount(<EmployeesPage />); await screen.findByRole('table'); await userEvent.click(screen.getByRole('button', { name: 'Create employee' })); await employeeFields();
    await userEvent.click(screen.getByRole('button', { name: 'Save employee' }));
    await screen.findByText(/Employee change confirmed/); expect(createEmployee.mock.calls[0][0]).toEqual({ name: 'New employee', email: 'new@example.test', departmentId: 10, jobRole: 'Housekeeper', createLogin: false });
  });
  it.each(['STAFF', 'ADMIN'])('provisions %s only on create and clears credentials immediately without persistence', async (role) => {
    const saved = deferred(); createEmployee.mockReturnValueOnce(/** @type {Promise<import('../services/managementDtos.js').Employee>} */ (saved.promise));
    const storage = vi.spyOn(Storage.prototype, 'setItem'); mount(<EmployeesPage />); await screen.findByRole('table');
    await userEvent.click(screen.getByRole('button', { name: 'Create employee' })); await employeeFields(); await userEvent.click(screen.getByLabelText('Create application login'));
    fill('Login email (required)', 'internal@example.test'); const secret = crypto.randomUUID(); fill('Temporary password (required)', secret); await userEvent.selectOptions(screen.getByLabelText('Security role (required)'), role);
    await userEvent.click(screen.getByRole('button', { name: 'Save employee' }));
    expect(/** @type {HTMLInputElement} */ (screen.getByLabelText('Temporary password (required)')).value).toBe('');
    expect(/** @type {HTMLInputElement} */ (screen.getByLabelText('Login email (required)')).value).toBe('');
    expect(createEmployee.mock.calls[0][0]?.securityRole).toBe(role); expect(storage).not.toHaveBeenCalled();
    fireEvent.submit(screen.getByRole('button', { name: 'Saving employee…' }).closest('form') || document.body); expect(createEmployee).toHaveBeenCalledTimes(1);
    await act(async () => saved.resolve(employee)); await screen.findByText(/Employee change confirmed/); expect(document.body.textContent).not.toContain(secret); expect(screen.queryByLabelText('Temporary password (required)')).toBeNull();
  });
  it('rejects empty/basic/provisioning fields before sending, then clears toggled login fields', async () => {
    mount(<EmployeeForm departments={[department]} onSuccess={vi.fn()} onPending={vi.fn()} />);
    await userEvent.click(screen.getByRole('button', { name: 'Save employee' })); expect(createEmployee).not.toHaveBeenCalled(); expect(screen.getAllByRole('alert').length).toBeGreaterThan(2);
    await employeeFields(); await userEvent.click(screen.getByLabelText('Create application login')); await userEvent.click(screen.getByRole('button', { name: 'Save employee' })); expect(createEmployee).not.toHaveBeenCalled();
    fill('Temporary password (required)', crypto.randomUUID()); await userEvent.click(screen.getByLabelText('Create application login')); await userEvent.click(screen.getByLabelText('Create application login'));
    expect(/** @type {HTMLInputElement} */ (screen.getByLabelText('Temporary password (required)')).value).toBe('');
  });
  it('preserves an inactive historical department only for an inactive Employee, not reactivation', async () => {
    mount(<EmployeeForm employee={{ ...employee, status: 'INACTIVE' }} departments={[{ ...department, active: false }]} onSuccess={vi.fn()} onPending={vi.fn()} />);
    await userEvent.click(screen.getByRole('button', { name: 'Save employee' })); expect(updateEmployee).toHaveBeenCalledTimes(1);
    await userEvent.selectOptions(screen.getByLabelText('Employee status (required)'), 'ACTIVE'); await userEvent.click(screen.getByRole('button', { name: 'Save employee' }));
    expect(await screen.findByText('Choose an eligible active department.')).toBeTruthy(); expect(updateEmployee).toHaveBeenCalledTimes(1);
  });
  it('excludes inactive departments from new assignment choices', async () => {
    mount(<EmployeeForm departments={[department, { ...department, id: 11, name: 'Inactive fixture', active: false }]} onSuccess={vi.fn()} onPending={vi.fn()} />);
    expect(screen.queryByRole('option', { name: 'Inactive fixture' })).toBeNull();
  });
  it('creates/reads/updates a Department and does not invent active/restore controls', async () => {
    mount(<DepartmentsPage />); await screen.findByRole('table'); await userEvent.click(screen.getByRole('button', { name: 'Create department' }));
    fill('Department name (required)', 'New department'); fill('Description', 'Description fixture'); await userEvent.click(screen.getByRole('button', { name: 'Save department' })); await screen.findByText(/Department change confirmed/);
    expect(createDepartment).toHaveBeenCalledWith({ name: 'New department', description: 'Description fixture' }, expect.any(AbortSignal)); await screen.findByRole('table');
    await userEvent.click(screen.getByRole('button', { name: `View department ${department.name}` })); const detailDialog = await screen.findByRole('dialog', { name: 'Department details' }); await within(detailDialog).findByText(department.description || '');
    await userEvent.click(screen.getByRole('button', { name: 'Close Department details' })); await screen.findByRole('table'); await userEvent.click(screen.getByRole('button', { name: `Edit department ${department.name}` }));
    await screen.findByLabelText('Department name (required)'); fill('Description', 'Updated fixture'); await userEvent.click(screen.getByRole('button', { name: 'Save department' }));
    await waitFor(() => expect(updateDepartment).toHaveBeenCalledWith(10, { name: department.name, description: 'Updated fixture' }, expect.any(AbortSignal)));
    expect(screen.queryByRole('button', { name: /Reactivate/ })).toBeNull();
  });
  it('validates department text limits and maps backend 400 field errors', async () => {
    createDepartment.mockRejectedValueOnce(new ApiError(400, { name: 'Check this value.' })); mount(<DepartmentForm onSuccess={vi.fn()} onPending={vi.fn()} />);
    fill('Department name (required)', 'X'); fill('Description', 'x'.repeat(256)); await userEvent.click(screen.getByRole('button', { name: 'Save department' })); expect(createDepartment).not.toHaveBeenCalled();
    fill('Department name (required)', 'Valid fixture'); fill('Description', ''); await userEvent.click(screen.getByRole('button', { name: 'Save department' })); expect(await screen.findByText('Check this value.')).toBeTruthy();
  });
  it.each(['employee', 'department'])('soft-deactivates %s with confirmation and refetch, not hard deletion', async (kind) => {
    mount(kind === 'employee' ? <EmployeesPage /> : <DepartmentsPage />); await screen.findByRole('table');
    await userEvent.click(screen.getByRole('button', { name: kind === 'employee' ? `Deactivate employee ${employee.name}` : `Deactivate department ${department.name}` }));
    await screen.findByText('History is preserved. This does not delete the record.'); await userEvent.click(screen.getByRole('button', { name: 'Confirm deactivation' }));
    await screen.findByText(kind === 'employee' ? /Employee change confirmed/ : /Department change confirmed/);
    expect(kind === 'employee' ? deactivateEmployee : deactivateDepartment).toHaveBeenCalledWith(kind === 'employee' ? 2 : 10, expect.any(AbortSignal));
  });
  it.each(['active employees are assigned', 'non-terminal tasks exist', 'active inventory exists'])('shows canonical Department 409 guard: %s; preserves record', async (reason) => {
    const error = new ApiError(409); error.message = `Department cannot be deactivated while ${reason}.`; deactivateDepartment.mockRejectedValueOnce(error);
    mount(<DepartmentsPage />); await screen.findByRole('table'); await userEvent.click(screen.getByRole('button', { name: `Deactivate department ${department.name}` }));
    await screen.findByRole('button', { name: 'Confirm deactivation' }); await userEvent.click(screen.getByRole('button', { name: 'Confirm deactivation' })); expect((await screen.findByRole('alert')).textContent).toBe(error.message); expect(screen.getByRole('table')).toBeTruthy();
  });
  it('blocks automatic or manual same-dialog retry after an uncertain mutation', async () => {
    createDepartment.mockRejectedValueOnce(new ApiError(0)); mount(<DepartmentsPage />); await screen.findByRole('table'); await userEvent.click(screen.getByRole('button', { name: 'Create department' }));
    fill('Department name (required)', 'Uncertain fixture'); await userEvent.click(screen.getByRole('button', { name: 'Save department' })); await screen.findByText(/The result is uncertain/);
    expect(screen.getByRole('button', { name: 'Save department' }).hasAttribute('disabled')).toBe(true); expect(createDepartment).toHaveBeenCalledTimes(1);
    await userEvent.click(screen.getByRole('button', { name: 'Close Create department' })); await screen.findByRole('table'); expect(listDepartments).toHaveBeenCalledTimes(2);
  });
  it('locks Department submissions and modal close while a write is pending', async () => {
    const pending = deferred(); createDepartment.mockReturnValueOnce(/** @type {Promise<import('../services/managementDtos.js').Department>} */ (pending.promise));
    mount(<DepartmentsPage />); await screen.findByRole('table'); await userEvent.click(screen.getByRole('button', { name: 'Create department' })); fill('Department name (required)', 'Pending fixture');
    const form = screen.getByRole('button', { name: 'Save department' }).closest('form');
    if (!form) throw new Error('Form missing');
    fireEvent.submit(form); fireEvent.submit(form); expect(createDepartment).toHaveBeenCalledTimes(1);
    await userEvent.click(screen.getByRole('button', { name: 'Close Create department' })); expect(screen.getByRole('dialog')).toBeTruthy();
    await act(async () => pending.resolve(department)); await screen.findByText(/Department change confirmed/);
  });
  it('locks deactivation confirmation and Cancel while a write is pending', async () => {
    const pending = deferred(); deactivateEmployee.mockReturnValueOnce(/** @type {Promise<void>} */ (pending.promise)); mount(<EmployeesPage />); await screen.findByRole('table');
    await userEvent.click(screen.getByRole('button', { name: `Deactivate employee ${employee.name}` })); const confirm = await screen.findByRole('button', { name: 'Confirm deactivation' }); fireEvent.click(confirm); fireEvent.click(confirm);
    expect(deactivateEmployee).toHaveBeenCalledTimes(1); expect(screen.getByRole('button', { name: 'Cancel' }).hasAttribute('disabled')).toBe(true);
    await act(async () => pending.resolve(undefined)); await screen.findByText(/Employee change confirmed/);
  });
  it('provides modal focus trap, Escape, restoration and contained table navigation', async () => {
    mount(<DepartmentsPage />); await screen.findByRole('table'); const trigger = screen.getByRole('button', { name: 'Create department' }); await userEvent.click(trigger);
    const dialog = screen.getByRole('dialog', { name: 'Create department' }); const close = within(dialog).getByRole('button', { name: 'Close Create department' }); const save = within(dialog).getByRole('button', { name: 'Save department' });
    close.focus(); await userEvent.keyboard('{Shift>}{Tab}{/Shift}'); expect(document.activeElement).toBe(save);
    await userEvent.keyboard('{Tab}'); expect(document.activeElement).toBe(close);
    fireEvent(dialog, new Event('cancel', { bubbles: true, cancelable: true })); expect(screen.queryByRole('dialog')).toBeNull(); expect(document.activeElement).toBe(trigger);
    await screen.findByRole('table'); expect(screen.getByRole('region', { name: 'Department records table' }).getAttribute('tabindex')).toBe('0');
  });
  it('restores Employee Create focus without disabling or replacing loaded content during background reads', async () => {
    mount(<EmployeesPage />); const table = await screen.findByRole('table'); const opener = screen.getByRole('button', { name: 'Create employee' });
    const refreshedEmployees = deferred(); const refreshedDepartments = deferred();
    listEmployees.mockReturnValueOnce(/** @type {Promise<import('../services/managementDtos.js').EmployeePage>} */ (refreshedEmployees.promise));
    listDepartments.mockReturnValueOnce(/** @type {Promise<import('../services/managementDtos.js').Department[]>} */ (refreshedDepartments.promise));
    opener.focus(); await userEvent.keyboard('{Enter}'); const dialog = screen.getByRole('dialog');
    screen.getByRole('button', { name: 'Close Create employee' }).focus(); fireEvent(dialog, new Event('cancel', { bubbles: true, cancelable: true }));
    expect(document.activeElement).toBe(opener); expect(opener.hasAttribute('disabled')).toBe(false); expect(screen.getByRole('table')).toBe(table);
    expect(screen.getByRole('status').textContent).toBe('Refreshing employees…');
    await act(async () => { refreshedEmployees.resolve(employeePage()); refreshedDepartments.resolve([department]); });
    expect(document.activeElement).toBe(opener); expect(screen.queryByText('Refreshing employees…')).toBeNull();
  });
  it('keeps a Department row View opener mounted and focused while its list re-fetches', async () => {
    mount(<DepartmentsPage />); const table = await screen.findByRole('table'); const opener = screen.getByRole('button', { name: `View department ${department.name}` });
    const refreshed = deferred(); listDepartments.mockReturnValueOnce(/** @type {Promise<import('../services/managementDtos.js').Department[]>} */ (refreshed.promise));
    opener.focus(); await userEvent.keyboard('{Enter}'); await screen.findByText(department.createdAt.replace('T', ' '));
    await userEvent.click(screen.getByRole('button', { name: 'Close Department details' }));
    expect(screen.getByRole('table')).toBe(table); expect(document.activeElement).toBe(opener); expect(screen.getByRole('status').textContent).toBe('Refreshing departments…');
    await act(async () => refreshed.resolve([department])); expect(document.activeElement).toBe(opener);
  });
  it.each(['employee', 'department'])('moves focus to the %s heading when a refreshed mutation result disables its opener', async (kind) => {
    mount(kind === 'employee' ? <EmployeesPage /> : <DepartmentsPage />); await screen.findByRole('table');
    const refreshed = deferred();
    if (kind === 'employee') listEmployees.mockReturnValueOnce(/** @type {Promise<import('../services/managementDtos.js').EmployeePage>} */ (refreshed.promise));
    else listDepartments.mockReturnValueOnce(/** @type {Promise<import('../services/managementDtos.js').Department[]>} */ (refreshed.promise));
    const name = kind === 'employee' ? `Deactivate employee ${employee.name}` : `Deactivate department ${department.name}`;
    const opener = screen.getByRole('button', { name }); await userEvent.click(opener); await screen.findByRole('button', { name: 'Confirm deactivation' });
    await userEvent.click(screen.getByRole('button', { name: 'Confirm deactivation' }));
    await screen.findByText(kind === 'employee' ? 'Refreshing employees…' : 'Refreshing departments…'); expect(document.activeElement).toBe(opener);
    await act(async () => refreshed.resolve(kind === 'employee' ? employeePage([{ ...employee, status: 'INACTIVE' }]) : [{ ...department, active: false }]));
    expect(opener.hasAttribute('disabled')).toBe(true);
    const heading = screen.getByRole('heading', { level: 1, name: kind === 'employee' ? 'Employees' : 'Departments' });
    expect(document.activeElement).toBe(heading); expect(heading.tabIndex).toBe(-1);
    await userEvent.keyboard('{Tab}'); expect(document.activeElement).toBe(screen.getByLabelText(kind === 'employee' ? 'Filter department' : 'Filter department status'));
  });
  it('uses the stable Department heading when a deactivated row disappears from the active filter', async () => {
    mount(<DepartmentsPage />); await screen.findByRole('table'); await userEvent.selectOptions(screen.getByLabelText('Filter department status'), 'true');
    await waitFor(() => expect(listDepartments.mock.calls.at(-1)?.[0]).toBe(true)); await screen.findByRole('table');
    const refreshed = deferred(); listDepartments.mockReturnValueOnce(/** @type {Promise<import('../services/managementDtos.js').Department[]>} */ (refreshed.promise));
    const opener = screen.getByRole('button', { name: `Deactivate department ${department.name}` }); await userEvent.click(opener);
    await screen.findByRole('button', { name: 'Confirm deactivation' }); await userEvent.click(screen.getByRole('button', { name: 'Confirm deactivation' }));
    await screen.findByText('Refreshing departments…'); expect(document.activeElement).toBe(opener);
    await act(async () => refreshed.resolve([])); expect(opener.isConnected).toBe(false); expect(document.activeElement).toBe(screen.getByRole('heading', { level: 1, name: 'Departments' }));
  });
  it('does not steal focus if the user navigates elsewhere during a background refresh', async () => {
    mount(<DepartmentsPage />); await screen.findByRole('table'); const refreshed = deferred();
    listDepartments.mockReturnValueOnce(/** @type {Promise<import('../services/managementDtos.js').Department[]>} */ (refreshed.promise));
    await userEvent.click(screen.getByRole('button', { name: `View department ${department.name}` })); await screen.findByRole('dialog');
    await userEvent.click(screen.getByRole('button', { name: 'Close Department details' }));
    const other = screen.getByRole('button', { name: 'Create department' }); other.focus();
    await act(async () => refreshed.resolve([])); expect(document.activeElement).toBe(other);
  });
  it('clears retained Employee content immediately on a filter change, not across request scopes', async () => {
    mount(<EmployeesPage />); await screen.findByRole('table'); const filtered = deferred();
    listEmployees.mockReturnValueOnce(/** @type {Promise<import('../services/managementDtos.js').EmployeePage>} */ (filtered.promise));
    await userEvent.selectOptions(screen.getByLabelText('Filter employee status'), 'INACTIVE'); expect(screen.queryByRole('table')).toBeNull();
    expect(screen.getByRole('status').textContent).toBe('Loading employees…');
    await act(async () => filtered.resolve(employeePage([]))); await screen.findByText('No employees match these filters.');
  });
  it('clears retained Department content on refresh error and restores a logical fallback', async () => {
    mount(<DepartmentsPage />); await screen.findByRole('table'); listDepartments.mockRejectedValueOnce(new ApiError(403));
    await userEvent.click(screen.getByRole('button', { name: `View department ${department.name}` })); await screen.findByRole('dialog');
    await userEvent.click(screen.getByRole('button', { name: 'Close Department details' })); await screen.findByRole('alert');
    expect(screen.queryByRole('table')).toBeNull(); expect(document.activeElement).toBe(screen.getByRole('heading', { level: 1, name: 'Departments' }));
  });
  it('fetches STAFF own detail lazily in AccountPanel with no list or writes', async () => {
    mount(<AccountPanel />, 'STAFF'); expect(getEmployee).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: 'View employee profile' })); await screen.findByText(employee.email);
    expect(getEmployee).toHaveBeenCalledWith(2, expect.any(AbortSignal)); expect(listEmployees).not.toHaveBeenCalled(); expect(listDepartments).not.toHaveBeenCalled();
    expect(screen.queryByRole('button', { name: 'Save employee' })).toBeNull(); expect(screen.queryByRole('button', { name: 'Create employee' })).toBeNull();
  });
  it.each(['USER', 'ADMIN'])('%s account has no STAFF self-profile entry point', (role) => {
    mount(<AccountPanel />, /** @type {'USER' | 'ADMIN'} */ (role)); expect(screen.queryByRole('button', { name: 'View employee profile' })).toBeNull(); expect(getEmployee).not.toHaveBeenCalled();
  });
  it('rejects STAFF wrong-owner data without displaying it', async () => {
    getEmployee.mockResolvedValueOnce({ ...employee, userId: 999, name: 'Cross employee private fixture' }); mount(<AccountPanel />, 'STAFF');
    await userEvent.click(screen.getByRole('button', { name: 'View employee profile' })); await screen.findByRole('alert'); expect(screen.queryByText('Cross employee private fixture')).toBeNull();
  });
  it('handles STAFF backend 403 with safe retry and clears on identity change', async () => {
    getEmployee.mockRejectedValueOnce(new ApiError(403)); const view = mount(<AccountPanel />, 'STAFF');
    await userEvent.click(screen.getByRole('button', { name: 'View employee profile' })); await screen.findByRole('alert'); await userEvent.click(screen.getByRole('button', { name: 'Retry' })); await screen.findByText(employee.email);
    view.rerender(<TestAuth value={{ status: 'authenticated', user: { ...testUser('STAFF'), id: 99, employeeId: 77 }, sessionKey: 3 }}><AccountPanel /></TestAuth>);
    expect(screen.queryByRole('dialog')).toBeNull(); expect(screen.queryByText(employee.email)).toBeNull();
  });
});
