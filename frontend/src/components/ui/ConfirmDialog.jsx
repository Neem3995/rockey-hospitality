import Modal from './Modal.jsx';
import Button from './Button.jsx';
import useAction from '../../hooks/useAction.js';
/** @param {{title: string, children: import('react').ReactNode, onClose: () => void, onSuccess: () => void, operation: (signal: AbortSignal) => Promise<unknown>}} props */
export default function ConfirmDialog({ title, children, onClose, onSuccess, operation }) {
  const action = useAction(onSuccess);
  return <Modal isOpen title={title} onClose={() => { if (!action.pending) onClose(); }}>
    {children}
    <p>History is preserved. This does not delete the record.</p>
    {action.error && <p role="alert" className="status-danger">{action.error.message}</p>}
    {action.uncertain && <p role="alert">The result is uncertain. Close and reload the list before trying again.</p>}
    <div className="management-actions"><Button variant="danger" disabled={action.uncertain} isLoading={action.pending} loadingLabel="Deactivating…" onClick={() => { void action.run(operation); }}>Confirm deactivation</Button>
      <Button variant="secondary" disabled={action.pending} onClick={onClose}>Cancel</Button></div>
  </Modal>;
}
