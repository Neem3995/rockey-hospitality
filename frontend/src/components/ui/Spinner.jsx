/*
 * STUDY NOTE: Pages pass a loading label to this presentational status component.
 * We render announced text plus a decorative CSS spinner. The caller owns request/cleanup state.
 * This component does not wait for a promise or initiate polling.
 */
/** @param {{label: string}} props */
export default function Spinner({ label }) {
  return <p role="status" className="loading-status"><span className="spinner" aria-hidden="true" />{label}</p>;
}
