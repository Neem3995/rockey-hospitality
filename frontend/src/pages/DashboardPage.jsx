import { useCallback, useMemo } from 'react';
import useAuth from '../auth/useAuth.js';
import useRead from '../hooks/useRead.js';
import Card from '../components/ui/Card.jsx';
import ErrorNotice from '../components/ui/ErrorNotice.jsx';
import Skeleton from '../components/ui/Skeleton.jsx';
import Spinner from '../components/ui/Spinner.jsx';
import { listTasks, listRooms, overdue, hotelNow, roleLabel } from '../services/housekeepingService.js';

export default function DashboardPage() {
  const auth = useAuth();
  const supervisor = auth.user?.role !== 'USER';
  const load = useCallback(async (/** @type {AbortSignal} */ signal) => ({
    tasks: await listTasks(signal), rooms: supervisor ? await listRooms(signal) : [],
  }), [supervisor]);
  const view = useRead(String(auth.sessionKey), load);
  const counts = useMemo(() => {
    if (!view.data) return [];
    const { tasks, rooms } = view.data;
    const today = hotelNow().slice(0, 10);
    const common = [
      ['Assigned tasks', tasks.filter(t => t.status === 'ASSIGNED').length],
      ['In progress', tasks.filter(t => t.status === 'IN_PROGRESS').length],
      ['Completed today', tasks.filter(t => t.status === 'COMPLETED' && t.completedAt?.slice(0, 10) === today).length],
      ['Overdue tasks', tasks.filter(t => overdue(t)).length],
    ];
    return supervisor ? [...common, ...['DIRTY', 'CLEANING', 'INSPECTION', 'READY'].map(status =>
      [status.replaceAll('_', ' ') + ' rooms', rooms.filter(r => r.active && r.status === status).length])] : common;
  }, [view.data, supervisor]);
  return <section className="page-intro"><h1>Dashboard</h1>
    <Card title="My account"><p>{auth.user?.name}</p><p>{auth.user?.email}</p><p>{auth.user ? roleLabel(auth.user.role) : ''}</p></Card>
    <p>{supervisor ? 'Housekeeping overview' : 'Only your assigned work is included.'}</p>
    {view.error ? <ErrorNotice title="Dashboard unavailable" message={view.error.message} onRetry={view.reload} />
      : view.loading ? <><Spinner label="Loading dashboard…" /><Skeleton /></> : <div className="metric-grid">{counts.map(([label, value]) =>
        <Card key={String(label)} title={String(label)}><p className="metric-value">{value}</p></Card>)}</div>}
  </section>;
}
