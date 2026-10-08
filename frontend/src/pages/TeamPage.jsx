import { useEffect, useRef, useState } from 'react';
import useAuth from '../hooks/useAuth.js';
import useRead from '../hooks/useRead.js';
import { listUsers, save, deactivate, roleLabel } from '../services/housekeepingService.js';
import { ApiError, StaleRequestError, formFieldErrors, isUncertainWriteFailure, UNCERTAIN_WRITE_MESSAGE } from '../services/apiClient.js';
import Button from '../components/ui/Button.jsx';
import Input from '../components/ui/Input.jsx';
import Select from '../components/ui/Select.jsx';
import Modal from '../components/ui/Modal.jsx';
import Table from '../components/ui/Table.jsx';
import ErrorNotice from '../components/ui/ErrorNotice.jsx';
import Skeleton from '../components/ui/Skeleton.jsx';
import Spinner from '../components/ui/Spinner.jsx';

/** @typedef {import('../services/housekeepingService.js').TeamUser} TeamUser */
export default function TeamPage() {
  const auth = useAuth();
  const admin = auth.user?.role === 'ADMIN';
  const heading = useRef(/** @type {HTMLHeadingElement|null} */ (null));
  const view = useRead(String(auth.sessionKey), listUsers, true);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState(/** @type {TeamUser|null} */ (null));
  const [draft, setDraft] = useState({ name: '', email: '', password: '', role: 'USER', active: true });
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState(/** @type {Record<string, string>} */ ({}));
  const submitting = useRef(false);
  const mustReconcile = useRef(false);
  const [uncertain, setUncertain] = useState(false);
  const [reconciling, setReconciling] = useState(false);
  useEffect(() => {
    if (reconciling && !view.loading) {
      setReconciling(false);
      if (!view.error && view.data) {
        mustReconcile.current = false; setUncertain(false); setError(''); setFieldErrors({});
      }
    }
  }, [reconciling, view.loading, view.error, view.data]);
  function refresh() {
    if (submitting.current || reconciling) return;
    if (mustReconcile.current) setReconciling(true);
    view.reload();
  }
  /** @param {keyof typeof draft} field @param {string|boolean} value */
  function change(field, value) {
    setDraft(previous => ({ ...previous, [field]: value }));
    setFieldErrors(previous => ({ ...previous, [field]: '' }));
    if (!mustReconcile.current) setError('');
  }
  /** @param {TeamUser|null} user */
  function edit(user) { if (submitting.current || mustReconcile.current) return; setEditing(user); setDraft(user ? { name: user.name, email: user.email, password: '', role: user.role, active: user.active } : { name: '', email: '', password: '', role: 'USER', active: true }); setError(''); setFieldErrors({}); setOpen(true); }
  /** @param {() => Promise<unknown>} action */
  async function act(action) {
    if (submitting.current || mustReconcile.current) return;
    submitting.current = true;
    setPending(true); setError(''); setFieldErrors({});
    try { await action(); setOpen(false); setDraft(previous => ({ ...previous, password: '' })); view.reload(); }
    catch (failure) {
      if (!(failure instanceof StaleRequestError)) {
        const unknown = isUncertainWriteFailure(failure);
        mustReconcile.current = unknown; setUncertain(unknown);
        setError(unknown ? UNCERTAIN_WRITE_MESSAGE : failure instanceof ApiError ? failure.message : 'Action failed.');
        setFieldErrors(formFieldErrors(failure, ['name', 'email', 'password', 'role', 'active']));
      }
    }
    finally { submitting.current = false; setPending(false); }
  }
  /** @param {import('react').FormEvent<HTMLFormElement>} event */
  function submit(event) {
    event.preventDefault();
    const body = editing ? { name: draft.name, email: draft.email, role: draft.role, active: draft.active }
      : { name: draft.name, email: draft.email, password: draft.password, role: admin ? draft.role : 'USER' };
    void act(() => save('users', editing?.id ?? null, body));
  }
  /** @type {import('../components/ui/Table.jsx').TableColumn<TeamUser>[]} */
  const columns = [
    { key: 'name', label: 'Name', render: u => u.name },
    { key: 'email', label: 'Email', render: u => u.email },
    { key: 'role', label: 'Role', render: u => roleLabel(u.role) },
    { key: 'active', label: 'Active', render: u => u.active ? 'Yes' : 'No' },
    { key: 'actions', label: 'Actions', render: u => admin && u.role !== 'ADMIN' ? <div className="row-actions">
      <Button variant="secondary" disabled={pending || uncertain} onClick={() => edit(u)}>Edit {u.name}</Button>
      {u.active && <Button variant="secondary" disabled={pending || uncertain} onClick={() => void act(() => deactivate('users', u.id))}>Deactivate {u.name}</Button>}</div> : 'Read only' },
  ];
  return <section className="page-intro"><h1 ref={heading} tabIndex={-1}>Team</h1><p>{admin ? 'Manage housekeeper and supervisor accounts. The first Admin uses the local bootstrap.' : 'View and create housekeeper accounts.'}</p>
    <Button disabled={pending || uncertain} onClick={() => edit(null)}>Create housekeeper{admin ? ' or manager' : ''}</Button>
    <Button variant="secondary" disabled={pending || reconciling} onClick={refresh}>Refresh team</Button>
    {view.refreshing && <p role="status">Refreshing team…</p>}
    {error && !open && <p role="alert" className="status-danger">{error}</p>}
    {view.error ? <ErrorNotice title="Team unavailable" message={view.error.message} onRetry={refresh} /> : !view.data ? <><Spinner label="Loading team…" /><Skeleton /></> :
      <Table caption="Housekeeping team" rows={view.data} columns={columns} getRowKey={u => u.id} emptyMessage="No team members found." />}
    <Modal isOpen={open} onClose={() => { if (!pending) { setOpen(false); setDraft(previous => ({ ...previous, password: '' })); } }} title={editing ? 'Edit team member' : 'Create team member'} fallbackFocusRef={heading}>
      <form onSubmit={submit} aria-busy={pending}>{error && <p role="alert" className="status-danger">{error}</p>}
        {uncertain && <Button type="button" disabled={reconciling} onClick={refresh}>Refresh before retrying</Button>}
        <Input id="team-name" label="Name" error={fieldErrors.name} required minLength={2} maxLength={100} value={draft.name} onChange={e => change('name', e.target.value)} disabled={pending} />
        <Input id="team-email" label="Email" error={fieldErrors.email} type="email" required maxLength={120} value={draft.email} onChange={e => change('email', e.target.value)} disabled={pending} />
        {!editing && <Input id="team-password" label="Initial password" error={fieldErrors.password} type="password" autoComplete="new-password" required minLength={8} maxLength={72} value={draft.password} onChange={e => change('password', e.target.value)} disabled={pending} />}
        {admin && <Select id="team-role" label="Role" error={fieldErrors.role} required value={draft.role} onChange={e => change('role', e.target.value)} disabled={pending}><option value="USER">Housekeeper</option><option value="MANAGER">Manager</option></Select>}
        {editing && <label><input id="team-active" type="checkbox" checked={draft.active} onChange={e => change('active', e.target.checked)} disabled={pending} aria-invalid={!!fieldErrors.active} aria-describedby={fieldErrors.active ? 'team-active-error' : undefined} /> Active account</label>}
        {fieldErrors.active && <p id="team-active-error" role="alert">{fieldErrors.active}</p>}
        <Button type="submit" disabled={uncertain || reconciling} isLoading={pending}>Save team member</Button>
      </form>
    </Modal>
  </section>;
}
