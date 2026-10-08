import { useCallback, useRef, useState } from 'react';
import useAuth from '../hooks/useAuth.js';
import useRead from '../hooks/useRead.js';
import { listRooms, listTasks, listInspections, save, deactivate, roomStatus, inspect, displayTime } from '../services/housekeepingService.js';
import { ApiError, StaleRequestError } from '../services/apiClient.js';
import Button from '../components/ui/Button.jsx';
import Input from '../components/ui/Input.jsx';
import Select from '../components/ui/Select.jsx';
import Modal from '../components/ui/Modal.jsx';
import Table from '../components/ui/Table.jsx';
import ErrorNotice from '../components/ui/ErrorNotice.jsx';
import Skeleton from '../components/ui/Skeleton.jsx';
import Spinner from '../components/ui/Spinner.jsx';

/** @typedef {import('../services/housekeepingService.js').Room} Room */
export default function RoomsPage() {
  const auth = useAuth();
  const heading = useRef(/** @type {HTMLHeadingElement|null} */ (null));
  const load = useCallback(async (/** @type {AbortSignal} */ signal) => ({ rooms: await listRooms(signal), tasks: await listTasks(signal) }), []);
  const view = useRead(String(auth.sessionKey), load, true);
  const [mode, setMode] = useState(/** @type {'room'|'inspect'|'history'|null} */ (null));
  const [selected, setSelected] = useState(/** @type {Room|null} */ (null));
  const [draft, setDraft] = useState({ roomNumber: '', floor: '1', status: 'READY', active: true });
  const [inspectionTask, setInspectionTask] = useState('');
  const [result, setResult] = useState(/** @type {'PASS'|'FAIL'} */ ('PASS'));
  const [notes, setNotes] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const selectedId = selected?.id;
  const historyLoad = useCallback((/** @type {AbortSignal} */ signal) =>
    selectedId && (mode === 'inspect' || mode === 'history') ? listInspections(selectedId, signal) : Promise.resolve([]), [selectedId, mode]);
  const history = useRead(auth.sessionKey + ':' + selectedId + ':' + mode, historyLoad);

  /** @param {Room|null} room */
  function edit(room) {
    setSelected(room); setDraft(room ? { roomNumber: room.roomNumber, floor: String(room.floor), status: room.status, active: room.active } : { roomNumber: '', floor: '1', status: 'READY', active: true });
    setError(''); setMode('room');
  }
  /** @param {Room} room @param {'inspect'|'history'} nextMode */
  function inspection(room, nextMode) {
    setSelected(room); setResult('PASS'); setNotes(''); setError('');
    const latest = (view.data?.tasks || []).filter(t => t.room.id === room.id && t.status === 'COMPLETED').sort((a,b) => b.id - a.id)[0];
    setInspectionTask(latest ? String(latest.id) : '');
    setMode(nextMode);
  }
  /** @param {() => Promise<unknown>} action */
  async function act(action) {
    if (pending) return;
    setPending(true); setError('');
    try { await action(); setMode(null); view.reload(); }
    catch (failure) { if (!(failure instanceof StaleRequestError)) setError(failure instanceof ApiError ? failure.message : 'Action failed. Retry.'); }
    finally { setPending(false); }
  }
  /** @param {import('react').FormEvent<HTMLFormElement>} event */
  function submit(event) {
    event.preventDefault();
    void act(() => save('rooms', selected?.id ?? null, { ...draft, floor: Number(draft.floor) }));
  }
  /** @type {import('../components/ui/Table.jsx').TableColumn<Room>[]} */
  const columns = [
    { key: 'number', label: 'Room', render: r => r.roomNumber },
    { key: 'floor', label: 'Floor', render: r => r.floor },
    { key: 'status', label: 'Status', render: r => r.status.replaceAll('_', ' ') },
    { key: 'active', label: 'Active', render: r => r.active ? 'Yes' : 'No' },
    { key: 'actions', label: 'Actions', render: r => <div className="row-actions">
      <Button variant="secondary" onClick={() => edit(r)}>Edit room {r.roomNumber}</Button>
      <Button variant="secondary" onClick={() => inspection(r, 'history')}>History room {r.roomNumber}</Button>
      {r.active && <>
        {['READY', 'OUT_OF_SERVICE'].includes(r.status) && <Button disabled={pending} onClick={() => void act(() => roomStatus(r.id, 'DIRTY'))}>Mark room {r.roomNumber} dirty</Button>}
        {['READY', 'DIRTY'].includes(r.status) && <Button disabled={pending} variant="secondary" onClick={() => void act(() => roomStatus(r.id, 'OUT_OF_SERVICE'))}>Take room {r.roomNumber} out of service</Button>}
        {r.status === 'INSPECTION' && <Button onClick={() => inspection(r, 'inspect')}>Inspect room {r.roomNumber}</Button>}
        <Button variant="secondary" disabled={pending} onClick={() => void act(() => deactivate('rooms', r.id))}>Deactivate room {r.roomNumber}</Button>
      </>}
    </div> },
  ];
  return <section className="page-intro"><h1 ref={heading} tabIndex={-1}>Rooms</h1>
    <p>Cleaning moves rooms to inspection. Only a recorded PASS makes a cleaned room ready.</p>
    <Button onClick={() => edit(null)}>Create room</Button>
    <Button variant="secondary" onClick={view.reload}>Refresh rooms</Button>
    {view.refreshing && <p role="status">Refreshing rooms…</p>}
    {error && !mode && <p role="alert" className="status-danger">{error}</p>}
    {view.error ? <ErrorNotice title="Rooms unavailable" message={view.error.message} onRetry={view.reload} /> : !view.data ?
      <><Spinner label="Loading rooms…" /><Skeleton /></> : <Table caption="Housekeeping rooms" rows={view.data.rooms} columns={columns} getRowKey={r => r.id} emptyMessage="No rooms yet." />}
    <Modal isOpen={mode === 'room'} onClose={() => { if (!pending) setMode(null); }} title={selected ? 'Edit room' : 'Create room'} fallbackFocusRef={heading}>
      <form onSubmit={submit} aria-busy={pending}>{error && <p role="alert" className="status-danger">{error}</p>}
        <Input id="room-number" label="Room number" required maxLength={10} value={draft.roomNumber} disabled={pending || !!selected} onChange={e => setDraft({ ...draft, roomNumber: e.target.value })} />
        <Input id="room-floor" label="Floor" type="number" required min={1} max={99} value={draft.floor} disabled={pending} onChange={e => setDraft({ ...draft, floor: e.target.value })} />
        {!selected && <Select id="room-status" label="Initial status" value={draft.status} disabled={pending} onChange={e => setDraft({ ...draft, status: e.target.value })}>
          <option>READY</option><option>DIRTY</option><option>OUT_OF_SERVICE</option></Select>}
        {selected && <label><input type="checkbox" checked={draft.active} disabled={pending} onChange={e => setDraft({ ...draft, active: e.target.checked })} /> Active room</label>}
        <Button type="submit" isLoading={pending}>Save room</Button>
      </form>
    </Modal>
    <Modal isOpen={mode === 'inspect' || mode === 'history'} onClose={() => { if (!pending) setMode(null); }}
      title={(mode === 'inspect' ? 'Inspect room ' : 'Inspection history room ') + (selected?.roomNumber || '')} fallbackFocusRef={heading}>
      {mode === 'inspect' && <form aria-busy={pending} onSubmit={e => { e.preventDefault(); if (selected) void act(() => inspect(selected.id, Number(inspectionTask), result, notes)); }}>
        {error && <p role="alert" className="status-danger">{error}</p>}
        <Input id="inspection-task" label="Latest completed task ID" type="number" value={inspectionTask} required min={1} readOnly />
        <Select id="inspection-result" label="Result" value={result} disabled={pending} onChange={e => setResult(e.target.value === 'PASS' ? 'PASS' : 'FAIL')}><option>PASS</option><option>FAIL</option></Select>
        <label htmlFor="inspection-notes">Inspection notes</label><textarea id="inspection-notes" maxLength={1000} value={notes} disabled={pending} onChange={e => setNotes(e.target.value)} />
        <p>PASS → READY. FAIL → DIRTY; create another cleaning task.</p>
        <Button type="submit" isLoading={pending}>Record inspection</Button>
      </form>}
      <h3>Recorded inspections</h3>
      {history.error ? <ErrorNotice title="History unavailable" message={history.error.message} onRetry={history.reload} />
        : history.loading ? <Spinner label="Loading inspections…" /> : history.data?.length ? <ul>{history.data.map(i =>
          <li key={i.id}>{i.result} · Task {i.taskId} · {i.inspectedBy.name} · {displayTime(i.inspectedAt)}<p>{i.notes}</p></li>)}</ul> : <p>No inspections recorded.</p>}
    </Modal>
  </section>;
}
