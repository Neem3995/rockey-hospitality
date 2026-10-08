/*
 * STUDY NOTE: A parent renders this presentational box with children and an optional title/class.
 * We show the supplied content; the parent owns its state, fetching and empty/error decision.
 * This component does not calculate dashboard values or read authentication.
 */
/**
 * @param {{title?: string, children: import('react').ReactNode, className?: string}} props
 */
export default function Card({ title, children, className = '' }) {
  return (
    <div className={`card ${className}`.trim()}>
      {title && <h2 className="card-title">{title}</h2>}
      {children}
    </div>
  );
}
