import { useCallback, useMemo, useRef, useState } from 'react';
import useAuth from '../../hooks/useAuth.js';
import useRead from '../../hooks/useRead.js';
import useDialogFocus from '../../hooks/useDialogFocus.js';
import { authScopeKey } from '../../services/formValidation.js';
import { listEmployees, getEmployee, deactivateEmployee, employeeSortFields } from '../../services/employeeService.js';
import { listDepartments } from '../../services/departmentService.js';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import Table from '../../components/ui/Table.jsx';
import Select from '../../components/ui/Select.jsx';
import Modal from '../../components/ui/Modal.jsx';
import ConfirmDialog from '../../components/ui/ConfirmDialog.jsx';
import StatusBadge from '../../components/ui/StatusBadge.jsx';
import Pagination from '../../components/ui/Pagination.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import ErrorNotice from '../../components/ui/ErrorNotice.jsx';
import EmployeeDetails from '../../components/employees/EmployeeDetails.jsx';
import EmployeeForm from '../../components/employees/EmployeeForm.jsx';

/** @param {{id: number, mode: 'detail' | 'edit' | 'deactivate', departments: import('../../services/managementDtos.js').Department[], onClose: () => void, onSuccess: () => void}} props */
function EmployeeDialog({ id, mode, departments, onClose, onSuccess }) {
  const load = useCallback((/** @type {AbortSignal} */ signal) => getEmployee(id, signal), [id]);
  const read = useRead(String(id), load);
  const pending = useRef(false);
  const title = mode === 'edit' ? 'Edit employee' : mode === 'deactivate' ? 'Deactivate employee' : 'Employee details';
  if (mode === 'deactivate' && read.data) return <ConfirmDialog title={title} onClose={onClose} onSuccess={onSuccess} operation={(signal) => deactivateEmployee(id, signal)}><p>Deactivate {read.data.name}?</p></ConfirmDialog>;
  return <Modal isOpen title={title} onClose={() => { if (!pending.current) onClose(); }}>
    {read.loading && <Spinner label="Loading employee…" />}
    {read.error && <ErrorNotice title="Employee unavailable" message={read.error.message} onRetry={read.reload} />}
    {read.data && (mode === 'edit'
      ? <EmployeeForm employee={read.data} departments={departments} onSuccess={onSuccess} onPending={(value) => { pending.current = value; }} />
      : <EmployeeDetails employee={read.data} />)}
  </Modal>;
}

function EmployeeManagement() {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [departmentId, setDepartmentId] = useState('');
  const [status, setStatus] = useState('');
  const [sort, setSort] = useState('name,asc');
  const [notice, setNotice] = useState('');
  const [dialog, setDialog] = useState(/** @type {{mode: 'create' | 'detail' | 'edit' | 'deactivate', id: number} | null} */ (null));
  const pending = useRef(false);
  const filters = useMemo(() => ({ page, size, sort, ...(departmentId ? { departmentId: Number(departmentId) } : {}), ...(status ? { status: /** @type {'ACTIVE' | 'INACTIVE'} */ (status) } : {}) }), [page, size, sort, departmentId, status]);
  const load = useCallback((/** @type {AbortSignal} */ signal) => listEmployees(filters, signal), [filters]);
  const loadDepartments = useCallback((/** @type {AbortSignal} */ signal) => listDepartments(undefined, signal), []);
  const read = useRead(JSON.stringify(filters), load, true);
  const departments = useRead('department-lookup', loadDepartments, true);
  const focus = useDialogFocus(dialog !== null, read.loading || departments.loading);
  function close() { setDialog(null); read.reload(); departments.reload(); }
  function saved() { close(); setNotice('Employee change confirmed. The list has been reloaded.'); }
  /** @param {'detail' | 'edit' | 'deactivate'} mode @param {number} id @param {HTMLButtonElement} opener */
  function open(mode, id, opener) { focus.opener.current = opener; setNotice(''); setDialog({ mode, id }); }
  const error = read.error || departments.error;
  return <section className="management-page"><h1 ref={focus.fallback} tabIndex={-1}>Employees</h1>
    <p>Manage internal employee records and create-only application logins.</p>
    {notice && <p role="status" className="status-success">{notice}</p>}
    <div className="management-toolbar">
      <Select id="employee-filter-department" label="Filter department" value={departmentId} onChange={(event) => { setDepartmentId(event.target.value); setPage(0); }}><option value="">All departments</option>{departments.data?.map((department) => <option key={department.id} value={department.id}>{department.name}{department.active ? '' : ' (inactive)'}</option>)}</Select>
      <Select id="employee-filter-status" label="Filter employee status" value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }}><option value="">All statuses</option><option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option></Select>
      <Select id="employee-sort" label="Sort employees" value={sort} onChange={(event) => { setSort(event.target.value); setPage(0); }}>{employeeSortFields.flatMap((field) => ['asc', 'desc'].map((direction) => <option key={`${field},${direction}`} value={`${field},${direction}`}>{field} · {direction === 'asc' ? 'ascending' : 'descending'}</option>))}</Select>
      <Select id="employee-size" label="Employees per page" value={size} onChange={(event) => { setSize(Number(event.target.value)); setPage(0); }}>{[20, 50, 100].map((value) => <option key={value} value={value}>{value}</option>)}</Select>
    </div>
    <div className="management-actions"><Button disabled={!departments.data || !!error} onClick={(event) => { focus.opener.current = event.currentTarget; setNotice(''); setDialog({ mode: 'create', id: 0 }); }}>Create employee</Button><Button variant="secondary" onClick={() => { read.reload(); departments.reload(); setNotice(''); }}>Reload employees</Button></div>
    {(read.loading || departments.loading) && <Spinner label={read.refreshing || departments.refreshing ? 'Refreshing employees…' : 'Loading employees…'} />}
    {error && <ErrorNotice title="Employees unavailable" message={error.message} onRetry={() => { read.reload(); departments.reload(); }} />}
    {!error && read.data && <Card>
      <Table caption="Employee records" rows={read.data.content} getRowKey={(row) => row.id} emptyMessage="No employees match these filters." columns={[
        { key: 'name', label: 'Name', render: (row) => row.name }, { key: 'department', label: 'Department', render: (row) => row.department.name },
        { key: 'jobRole', label: 'Job role', render: (row) => row.jobRole }, { key: 'status', label: 'Status', render: (row) => <StatusBadge active={row.status === 'ACTIVE'} /> },
        { key: 'login', label: 'Login', render: (row) => row.userId === null ? 'No login account' : 'Linked account' },
        { key: 'actions', label: 'Actions', render: (row) => <div className="management-actions"><Button variant="secondary" onClick={(event) => open('detail', row.id, event.currentTarget)} aria-label={`View employee ${row.name}`}>View</Button><Button variant="secondary" onClick={(event) => open('edit', row.id, event.currentTarget)} aria-label={`Edit employee ${row.name}`}>Edit</Button><Button variant="danger" disabled={row.status === 'INACTIVE'} onClick={(event) => open('deactivate', row.id, event.currentTarget)} aria-label={`Deactivate employee ${row.name}`}>Deactivate</Button></div> },
      ]} />
      <Pagination page={read.data.page} totalPages={read.data.totalPages} totalElements={read.data.totalElements} last={read.data.last} onPage={setPage} />
    </Card>}
    {dialog?.mode === 'create' && departments.data && <Modal isOpen title="Create employee" onClose={() => { if (!pending.current) close(); }}><EmployeeForm departments={departments.data} onSuccess={saved} onPending={(value) => { pending.current = value; }} /></Modal>}
    {dialog && dialog.mode !== 'create' && departments.data && <EmployeeDialog key={`${dialog.mode}:${dialog.id}`} id={dialog.id} mode={dialog.mode} departments={departments.data} onClose={close} onSuccess={saved} />}
  </section>;
}

export default function EmployeesPage() {
  const auth = useAuth();
  if (auth.status !== 'authenticated' || auth.user?.role !== 'ADMIN') return <p role="alert">Administrator access required.</p>;
  return <EmployeeManagement key={authScopeKey(auth)} />;
}
