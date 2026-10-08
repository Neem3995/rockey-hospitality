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
