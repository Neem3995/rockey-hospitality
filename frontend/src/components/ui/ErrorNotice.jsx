/*
 * STUDY NOTE: A page gives us a safe error title/message and, optionally, its retry callback.
 * We render Card, an alert announcement and a keyboard-accessible Button when retry is available.
 * Clicking calls the parent's function. We do not fetch, translate raw errors or retry automatically.
 */
import Button from './Button.jsx';
import Card from './Card.jsx';

/** @param {{title: string, message: string, onRetry?: () => void, retryLabel?: string}} props */
export default function ErrorNotice({ title, message, onRetry, retryLabel = 'Retry' }) {
  return (
    <Card title={title}>
      <p role="alert" className="status-danger">{message}</p>
      {onRetry && <Button variant="secondary" onClick={onRetry}>{retryLabel}</Button>}
    </Card>
  );
}
