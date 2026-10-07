import { useCallback, useRef, useState } from 'react';
import useAuth from '../../hooks/useAuth.js';
import useRead from '../../hooks/useRead.js';
import useDialogFocus from '../../hooks/useDialogFocus.js';
import { authScopeKey } from '../../services/formValidation.js';
import { listDepartments, getDepartment, deactivateDepartment } from '../../services/departmentService.js';
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import Select from '../../components/ui/Select.jsx';
import Table from '../../components/ui/Table.jsx';
import Modal from '../../components/ui/Modal.jsx';
import ConfirmDialog from '../../components/ui/ConfirmDialog.jsx';
import StatusBadge from '../../components/ui/StatusBadge.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import ErrorNotice from '../../components/ui/ErrorNotice.jsx';
import DepartmentForm from '../../components/departments/DepartmentForm.jsx';

/** @param {{id: number, mode: 'detail' | 'edit' | 'deactivate', onClose: () => void, onSuccess: () => void}} props */
function DepartmentDialog({ id, mode, onClose, onSuccess }) {
  const load = useCallback((/** @type {AbortSignal} */ signal) => getDepartment(id, signal), [id]);
  const read = useRead(String(id), load);
  const pending = useRef(false);
  const title = mode === 'edit' ? 'Edit department' : mode === 'deactivate' ? 'Deactivate department' : 'Department details';
  if (mode === 'deactivate' && read.data) return <ConfirmDialog title={title} onClose={onClose} onSuccess={onSuccess} operation={(signal) => deactivateDepartment(id, signal)}><p>Deactivate {read.data.name}?</p></ConfirmDialog>;
  return <Modal isOpen title={title} onClose={() => { if (!pending.current) onClose(); }}>
    {read.loading && <Spinner label="Loading department…" />}
    {read.error && <ErrorNotice title="Department unavailable" message={read.error.message} onRetry={read.reload} />}
    {read.data && (mode === 'edit' ? <DepartmentForm department={read.data} onSuccess={onSuccess} onPending={(value) => { pending.current = value; }} />
      : <dl className="record-details"><div><dt>Name</dt><dd>{read.data.name}</dd></div><div><dt>Description</dt><dd>{read.data.description || 'No description'}</dd></div><div><dt>Status</dt><dd><StatusBadge active={read.data.active} /></dd></div><div><dt>Created (server local time)</dt><dd>{read.data.createdAt.replace('T', ' ')}</dd></div><div><dt>Updated (server local time)</dt><dd>{read.data.updatedAt.replace('T', ' ')}</dd></div></dl>)}
  </Modal>;
}
function DepartmentManagement() {
  const [filter, setFilter] = useState('');
  const [notice, setNotice] = useState('');
  const [dialog, setDialog] = useState(/** @type {{mode: 'create' | 'detail' | 'edit' | 'deactivate', id: number} | null} */ (null));
  const pending = useRef(false);
  const load = useCallback((/** @type {AbortSignal} */ signal) => listDepartments(filter === '' ? undefined : filter === 'true', signal), [filter]);
  const read = useRead(filter, load, true);
  const focus = useDialogFocus(dialog !== null, read.loading);
  function close() { setDialog(null); read.reload(); }
  function saved() { close(); setNotice('Department change confirmed. The list has been reloaded.'); }
  /** @param {'detail' | 'edit' | 'deactivate'} mode @param {number} id @param {HTMLButtonElement} opener */
  function open(mode, id, opener) { focus.opener.current = opener; setNotice(''); setDialog({ mode, id }); }
  return <section className="management-page"><h1 ref={focus.fallback} tabIndex={-1}>Departments</h1><p>Manage departments without deleting their operational history.</p>
    {notice && <p role="status" className="status-success">{notice}</p>}
    <div className="management-toolbar"><Select id="department-filter" label="Filter department status" value={filter} onChange={(event) => setFilter(event.target.value)}><option value="">All departments</option><option value="true">Active</option><option value="false">Inactive</option></Select></div>
    <div className="management-actions"><Button onClick={(event) => { focus.opener.current = event.currentTarget; setNotice(''); setDialog({ mode: 'create', id: 0 }); }}>Create department</Button><Button variant="secondary" onClick={() => { read.reload(); setNotice(''); }}>Reload departments</Button></div>
    {read.loading && <Spinner label={read.refreshing ? 'Refreshing departments…' : 'Loading departments…'} />}
    {read.error && <ErrorNotice title="Departments unavailable" message={read.error.message} onRetry={read.reload} />}
    {read.data && <Card><Table caption="Department records" rows={read.data} getRowKey={(row) => row.id} emptyMessage="No departments match this filter." columns={[
      { key: 'name', label: 'Name', render: (row) => row.name }, { key: 'description', label: 'Description', render: (row) => row.description || 'No description' },
      { key: 'status', label: 'Status', render: (row) => <StatusBadge active={row.active} /> },
      { key: 'actions', label: 'Actions', render: (row) => <div className="management-actions"><Button variant="secondary" aria-label={`View department ${row.name}`} onClick={(event) => open('detail', row.id, event.currentTarget)}>View</Button><Button variant="secondary" aria-label={`Edit department ${row.name}`} onClick={(event) => open('edit', row.id, event.currentTarget)}>Edit</Button><Button variant="danger" disabled={!row.active} aria-label={`Deactivate department ${row.name}`} onClick={(event) => open('deactivate', row.id, event.currentTarget)}>Deactivate</Button></div> },
    ]} /></Card>}
    {dialog?.mode === 'create' && <Modal isOpen title="Create department" onClose={() => { if (!pending.current) close(); }}><DepartmentForm onSuccess={saved} onPending={(value) => { pending.current = value; }} /></Modal>}
    {dialog && dialog.mode !== 'create' && <DepartmentDialog key={`${dialog.mode}:${dialog.id}`} id={dialog.id} mode={dialog.mode} onClose={close} onSuccess={saved} />}
  </section>;
}
export default function DepartmentsPage() {
  const auth = useAuth();
  if (auth.status !== 'authenticated' || auth.user?.role !== 'ADMIN') return <p role="alert">Administrator access required.</p>;
  return <DepartmentManagement key={authScopeKey(auth)} />;
}
