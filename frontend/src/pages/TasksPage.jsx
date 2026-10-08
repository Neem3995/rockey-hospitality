import { useCallback, useEffect, useReducer, useRef, useState } from 'react';
import useAuth from '../auth/useAuth.js';
import useRead from '../hooks/useRead.js';
import { listTasks, listRooms, listUsers, save, deactivate, taskStatus, overdue, displayTime } from '../services/housekeepingService.js';
import { ApiError, StaleRequestError, formFieldErrors, isUncertainWriteFailure, UNCERTAIN_WRITE_MESSAGE } from '../services/apiClient.js';
import Button from '../components/ui/Button.jsx';
import Input from '../components/ui/Input.jsx';
import Select from '../components/ui/Select.jsx';
import Modal from '../components/ui/Modal.jsx';
import Table from '../components/ui/Table.jsx';
import ErrorNotice from '../components/ui/ErrorNotice.jsx';
import Skeleton from '../components/ui/Skeleton.jsx';
import Spinner from '../components/ui/Spinner.jsx';

/** @typedef {import('../services/housekeepingService.js').Task} Task */
/** @typedef {{title:string,description:string,priority:string,assignedUserId:string,roomId:string,dueAt:string}} Draft */
/** @param {Draft} state @param {{field:keyof Draft,value:string}|{reset:Draft}} action */
function draftReducer(state, action) { return 'reset' in action ? action.reset : { ...state, [action.field]: action.value }; }
/** @returns {Draft} */
const blank = () => ({ title: 'Clean room', description: '', priority: 'MEDIUM', assignedUserId: '', roomId: '', dueAt: '' });

export default function TasksPage() {
  const auth = useAuth();
  const supervisor = auth.user?.role !== 'USER';
  const heading = useRef(/** @type {HTMLHeadingElement|null} */ (null));
  const load = useCallback(async (/** @type {AbortSignal} */ signal) => ({
    tasks: await listTasks(signal),
    rooms: supervisor ? await listRooms(signal) : [],
    users: supervisor ? await listUsers(signal) : [],
  }), [supervisor]);
  const view = useRead(String(auth.sessionKey), load, true);
  const [filter, setFilter] = useState('');
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState(/** @type {Task|null} */ (null));
  const [draft, dispatch] = useReducer(draftReducer, undefined, blank);
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
  /** @param {keyof Draft} field @param {string} value */
  function change(field, value) {
    dispatch({ field, value });
    setFieldErrors(previous => ({ ...previous, [field]: '' }));
    if (!mustReconcile.current) setError('');
  }

  /** @param {Task|null} task */
  function edit(task) {
    if (submitting.current || mustReconcile.current) return;
    setEditing(task); setError(''); setFieldErrors({});
    dispatch({ reset: task ? { title: task.title, description: task.description || '', priority: task.priority,
      assignedUserId: String(task.assignedUser.id), roomId: String(task.room.id), dueAt: task.dueAt?.slice(0, 16) || '' } : blank() });
    setOpen(true);
  }
  /** @param {() => Promise<unknown>} operation */
  async function act(operation) {
    if (submitting.current || mustReconcile.current) return;
    submitting.current = true;
    setPending(true); setError(''); setFieldErrors({});
    try { await operation(); setOpen(false); view.reload(); }
    catch (failure) {
      if (!(failure instanceof StaleRequestError)) {
        const unknown = isUncertainWriteFailure(failure);
        mustReconcile.current = unknown; setUncertain(unknown);
        setError(unknown ? UNCERTAIN_WRITE_MESSAGE : failure instanceof ApiError ? failure.message : 'Action failed.');
        setFieldErrors(formFieldErrors(failure, ['title', 'description', 'roomId', 'assignedUserId', 'priority', 'dueAt']));
      }
    }
    finally { submitting.current = false; setPending(false); }
  }
  /** @param {import('react').FormEvent<HTMLFormElement>} event */
  function submit(event) {
    event.preventDefault();
    void act(() => save('tasks', editing?.id ?? null, { ...draft, priority: draft.priority,
      roomId: Number(draft.roomId), assignedUserId: Number(draft.assignedUserId), dueAt: draft.dueAt || null }));
  }
  const rows = (view.data?.tasks || []).filter(task => !filter || task.status === filter);
  /** @type {import('../components/ui/Table.jsx').TableColumn<Task>[]} */
  const columns = [
    { key: 'title', label: 'Work', render: t => <><strong>{t.title}</strong><p>{t.description}</p></> },
    { key: 'room', label: 'Room', render: t => <><span>{t.room.roomNumber} · Floor {t.room.floor}</span><p>{t.room.status.replaceAll('_', ' ')}</p></> },
    { key: 'user', label: 'Housekeeper', render: t => t.assignedUser.name },
    { key: 'status', label: 'Status', render: t => <>{t.status.replaceAll('_', ' ')}{overdue(t) && <strong className="status-danger"> · OVERDUE</strong>}</> },
    { key: 'due', label: 'Due / priority', render: t => <>{displayTime(t.dueAt)}<p>{t.priority}</p></> },
    { key: 'actions', label: 'Actions', render: t => <div className="row-actions">
      {auth.user?.role === 'USER' && t.assignedUser.id === auth.user.id && t.status === 'ASSIGNED' && <Button disabled={pending || uncertain} onClick={() => void act(() => taskStatus(t.id, 'IN_PROGRESS'))}>Start {t.title}</Button>}
      {auth.user?.role === 'USER' && t.assignedUser.id === auth.user.id && t.status === 'IN_PROGRESS' && <Button disabled={pending || uncertain} onClick={() => void act(() => taskStatus(t.id, 'COMPLETED'))}>Complete {t.title}</Button>}
      {supervisor && !['COMPLETED', 'CANCELLED'].includes(t.status) && <>
        <Button variant="secondary" disabled={pending || uncertain} onClick={() => edit(t)}>Edit {t.title}</Button>
        <Button variant="secondary" disabled={pending || uncertain} onClick={() => void act(() => deactivate('tasks', t.id))}>Cancel {t.title}</Button></>}
    </div> },
  ];
  return <section className="page-intro"><h1 ref={heading} tabIndex={-1}>{supervisor ? 'Tasks' : 'My Tasks'}</h1>
    <p>{supervisor ? 'Assign and manage cleaning work; housekeepers start and complete it. Inspect completed rooms from Rooms.' : 'Start your assigned work, complete cleaning, then let a supervisor record the inspection.'}</p>
    {supervisor && <Button disabled={pending || uncertain} onClick={() => edit(null)}>Create task</Button>}
    <Button variant="secondary" disabled={pending || reconciling} onClick={refresh}>Refresh tasks</Button>
    <Select id="task-filter" label="Task status filter" value={filter} onChange={e => setFilter(e.target.value)}>
      <option value="">All statuses</option>{['ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'].map(s => <option key={s}>{s}</option>)}</Select>
    {view.refreshing && <p role="status">Refreshing tasks…</p>}
    {error && !open && <p role="alert" className="status-danger">{error}</p>}
    {view.error ? <ErrorNotice title="Tasks unavailable" message={view.error.message} onRetry={refresh} />
      : !view.data ? <><Spinner label="Loading tasks…" /><Skeleton /></> : <Table caption="Housekeeping tasks" rows={rows} columns={columns} getRowKey={t => t.id} emptyMessage="No tasks in this view." />}
    <Modal isOpen={open} onClose={() => { if (!pending) setOpen(false); }} title={editing ? 'Edit task' : 'Create task'} fallbackFocusRef={heading}>
      <form onSubmit={submit} aria-busy={pending}>
        {error && <p role="alert" className="status-danger">{error}</p>}
        {uncertain && <Button type="button" disabled={reconciling} onClick={refresh}>Refresh before retrying</Button>}
        <Input id="task-title" label="Title" error={fieldErrors.title} required minLength={2} maxLength={150} value={draft.title} onChange={e => change('title', e.target.value)} disabled={pending} />
        <label htmlFor="task-description">Description</label><textarea id="task-description" maxLength={1000} value={draft.description} onChange={e => change('description', e.target.value)} disabled={pending} aria-invalid={!!fieldErrors.description} aria-describedby={fieldErrors.description ? 'task-description-error' : undefined} />
        {fieldErrors.description && <p id="task-description-error" role="alert">{fieldErrors.description}</p>}
        <Select id="task-room" label="Room" error={fieldErrors.roomId} required disabled={pending || !!editing} value={draft.roomId} onChange={e => change('roomId', e.target.value)}>
          <option value="">Choose room</option>{(view.data?.rooms || []).filter(r => r.id === editing?.room.id || (r.active && r.status === 'DIRTY')).map(r => <option key={r.id} value={r.id}>{r.roomNumber} · Floor {r.floor}</option>)}</Select>
        <Select id="task-worker" label="Housekeeper" error={fieldErrors.assignedUserId} required disabled={pending} value={draft.assignedUserId} onChange={e => change('assignedUserId', e.target.value)}>
          <option value="">Choose housekeeper</option>{(view.data?.users || []).filter(u => u.active && u.role === 'USER').map(u => <option key={u.id} value={u.id}>{u.name}</option>)}</Select>
        <Select id="task-priority" label="Priority" error={fieldErrors.priority} required disabled={pending} value={draft.priority} onChange={e => change('priority', e.target.value)}>
          {['LOW', 'MEDIUM', 'HIGH', 'URGENT'].map(p => <option key={p}>{p}</option>)}</Select>
        <Input id="task-due" label="Due time (America/New_York)" error={fieldErrors.dueAt} type="datetime-local" value={draft.dueAt} onChange={e => change('dueAt', e.target.value)} disabled={pending} />
        <Button type="submit" disabled={uncertain || reconciling} isLoading={pending}>Save task</Button>
      </form>
    </Modal>
  </section>;
}
