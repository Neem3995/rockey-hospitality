import Button from '../../components/ui/Button.jsx';
import Card from '../../components/ui/Card.jsx';
import useAuth from '../../hooks/useAuth.js';

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
