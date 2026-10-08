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
