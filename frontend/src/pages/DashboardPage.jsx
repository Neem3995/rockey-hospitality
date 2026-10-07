import { useEffect, useState } from 'react';
import useAuth from '../hooks/useAuth.js';
import AccountPanel from '../components/layout/AccountPanel.jsx';
import Card from '../components/ui/Card.jsx';
import Spinner from '../components/ui/Spinner.jsx';
import Skeleton from '../components/ui/Skeleton.jsx';
import ErrorNotice from '../components/ui/ErrorNotice.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import { dashboardMetrics, getDashboard } from '../services/dashboardService.js';
import { ApiError, StaleRequestError } from '../services/apiClient.js';
import { formatHotelTime } from '../utils/hotelTime.js';

/** @typedef {{scope: string, status: 'loading' | 'ready' | 'error', data: import('../services/dashboardService.js').Dashboard | null, error: string}} ViewState */

export default function DashboardPage() {
  const auth = useAuth();
  const role = auth.user?.role;
  const scope = JSON.stringify([auth.status, auth.sessionKey, auth.user?.id, role,
    auth.user?.status, auth.user?.employeeId, auth.user?.departmentSummary?.id]);
  const authenticated = auth.status === 'authenticated';
  const [attempt, setAttempt] = useState(0);
  const [view, setView] = useState(/** @type {ViewState} */ ({ scope: '', status: 'loading', data: null, error: '' }));

  useEffect(() => {
    const controller = new AbortController();
    setView({ scope, status: 'loading', data: null, error: '' });
    if (authenticated && role) {
      getDashboard(role, controller.signal).then((data) => {
        if (!controller.signal.aborted) setView({ scope, status: 'ready', data, error: '' });
      }).catch((error) => {
        if (controller.signal.aborted) return;
        const message = error instanceof StaleRequestError
          ? 'The dashboard request was interrupted. Try again.'
          : error instanceof ApiError ? error.message : 'The dashboard is unavailable. Please try again.';
        setView({ scope, status: 'error', data: null, error: message });
      });
    }
    return () => controller.abort();
  }, [scope, role, authenticated, attempt]);

  if (!authenticated || !role) return null;
  // Hide old private state synchronously, before the identity-change effect runs.
  const current = view.scope === scope;
  const data = current && view.status === 'ready' ? view.data : null;
  const metrics = dashboardMetrics[role];
  const descriptions = {
    USER: 'Your retained event registrations. No hotel operational data is included.',
    STAFF: 'Your assigned work and alerts, with inventory from your department only.',
    ADMIN: 'Global hotel operations. Counts include the history described on each card.',
  };
  return (
    <>
      <div className="page-intro">
        <p className="eyebrow">{role} overview</p>
        <h1>Dashboard</h1>
        <p className="page-description">{descriptions[role]}</p>
      </div>
      <section aria-label={`${role} dashboard`} aria-busy={!current || view.status === 'loading'}>
        {(!current || view.status === 'loading') && <>
          <Spinner label="Loading dashboard…" />
          <div className="dashboard-grid" aria-hidden="true">{metrics.map(({ key }) => <Card key={key}><Skeleton /></Card>)}</div>
        </>}
        {current && view.status === 'error' && <ErrorNotice title="Dashboard unavailable" message={view.error} retryLabel="Retry dashboard" onRetry={() => setAttempt((value) => value + 1)} />}
        {data && <>
          <p className="muted dashboard-as-of">As of <time dateTime={data.asOf}>{formatHotelTime(data.asOf)}</time> · America/New_York</p>
          <div className="dashboard-grid">
            {metrics.map(({ key, label, description }) => (
              <Card key={key} title={label} className="metric-card">
                <p className="metric-count">{data.section[key]}</p>
                <p className="muted">{description}</p>
              </Card>
            ))}
          </div>
          {metrics.every(({ key }) => data.section[key] === 0) && <EmptyState message="All counts in this snapshot are zero." />}
        </>}
      </section>
      <AccountPanel />
    </>
  );
}
