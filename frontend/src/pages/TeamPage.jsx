import { useRef, useState } from 'react';
import useAuth from '../hooks/useAuth.js';
import useRead from '../hooks/useRead.js';
import { listUsers, save, deactivate, roleLabel } from '../services/housekeepingService.js';
import { ApiError, StaleRequestError } from '../services/apiClient.js';
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
  /** @param {TeamUser|null} user */
  function edit(user) { setEditing(user); setDraft(user ? { name: user.name, email: user.email, password: '', role: user.role, active: user.active } : { name: '', email: '', password: '', role: 'USER', active: true }); setError(''); setOpen(true); }
  /** @param {() => Promise<unknown>} action */
  async function act(action) {
    if (pending) return;
    setPending(true); setError('');
    try { await action(); setOpen(false); setDraft(previous => ({ ...previous, password: '' })); view.reload(); }
    catch (failure) { if (!(failure instanceof StaleRequestError)) setError(failure instanceof ApiError ? failure.message : 'Action failed. Retry.'); }
    finally { setPending(false); }
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
      <Button variant="secondary" onClick={() => edit(u)}>Edit {u.name}</Button>
      {u.active && <Button variant="secondary" disabled={pending} onClick={() => void act(() => deactivate('users', u.id))}>Deactivate {u.name}</Button>}</div> : 'Read only' },
  ];
  return <section className="page-intro"><h1 ref={heading} tabIndex={-1}>Team</h1><p>{admin ? 'Manage housekeeper and supervisor accounts. The first Admin uses the local bootstrap.' : 'View and create housekeeper accounts.'}</p>
    <Button onClick={() => edit(null)}>Create housekeeper{admin ? ' or manager' : ''}</Button>
    <Button variant="secondary" onClick={view.reload}>Refresh team</Button>
    {view.refreshing && <p role="status">Refreshing team…</p>}
    {error && !open && <p role="alert" className="status-danger">{error}</p>}
    {view.error ? <ErrorNotice title="Team unavailable" message={view.error.message} onRetry={view.reload} /> : !view.data ? <><Spinner label="Loading team…" /><Skeleton /></> :
      <Table caption="Housekeeping team" rows={view.data} columns={columns} getRowKey={u => u.id} emptyMessage="No team members found." />}
    <Modal isOpen={open} onClose={() => { if (!pending) { setOpen(false); setDraft(previous => ({ ...previous, password: '' })); } }} title={editing ? 'Edit team member' : 'Create team member'} fallbackFocusRef={heading}>
      <form onSubmit={submit} aria-busy={pending}>{error && <p role="alert" className="status-danger">{error}</p>}
        <Input id="team-name" label="Name" required minLength={2} maxLength={100} value={draft.name} onChange={e => setDraft({ ...draft, name: e.target.value })} disabled={pending} />
        <Input id="team-email" label="Email" type="email" required maxLength={120} value={draft.email} onChange={e => setDraft({ ...draft, email: e.target.value })} disabled={pending} />
        {!editing && <Input id="team-password" label="Initial password" type="password" autoComplete="new-password" required minLength={8} maxLength={72} value={draft.password} onChange={e => setDraft({ ...draft, password: e.target.value })} disabled={pending} />}
        {admin && <Select id="team-role" label="Role" required value={draft.role} onChange={e => setDraft({ ...draft, role: e.target.value })} disabled={pending}><option value="USER">Housekeeper</option><option value="MANAGER">Manager</option></Select>}
        {editing && <label><input type="checkbox" checked={draft.active} onChange={e => setDraft({ ...draft, active: e.target.checked })} disabled={pending} /> Active account</label>}
        <Button type="submit" isLoading={pending}>Save team member</Button>
      </form>
    </Modal>
  </section>;
}
