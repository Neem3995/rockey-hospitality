/*
 * STUDY NOTE: AuthGuard renders this instead of private content while session checking is unresolved.
 * useAuth supplies checking/error state; we show a status or an error with an explicit retry.
 * The retry action delegates to apiClient bootstrap. This component does not claim a server logout
 * or reveal work data when the session cannot be confirmed.
 */
import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import useAuth from '../../auth/useAuth.js';

export default function SessionStatus() {
  const auth = useAuth();
  if (auth.status === 'checking') return <p role="status" className="session-status">Checking your session…</p>;
  return (
    <section className="page-intro">
      <h1>Session unavailable</h1>
      <Card>
        <p role="alert">{auth.error?.message || 'Your session could not be checked.'}</p>
        <p>Your server session has not been confirmed as signed out.</p>
        <Button onClick={() => void auth.retry()}>Retry session check</Button>
      </Card>
    </section>
  );
}
