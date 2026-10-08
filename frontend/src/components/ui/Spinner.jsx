/** @param {{label: string}} props */
export default function Spinner({ label }) {
  return <p role="status" className="loading-status"><span className="spinner" aria-hidden="true" />{label}</p>;
}
