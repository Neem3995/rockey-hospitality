/** @param {{active: boolean}} props */
export default function StatusBadge({ active }) {
  return <span className={active ? 'status-success' : 'muted'}>{active ? 'Active' : 'Inactive'}</span>;
}
