/** @param {{message: string}} props */
export default function EmptyState({ message }) {
  return <p className="muted empty-state">{message}</p>;
}
