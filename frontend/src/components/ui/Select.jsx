/** @param {import('react').SelectHTMLAttributes<HTMLSelectElement> & {id: string, label: string, error?: string}} props */
export default function Select({ id, label, error, children, ...props }) {
  return <div className="field"><label htmlFor={id}>{label}{props.required ? ' (required)' : ''}</label>
    <select {...props} id={id} className="input" aria-invalid={!!error} aria-describedby={error ? `${id}-error` : undefined}>{children}</select>
    {error && <p id={`${id}-error`} role="alert" className="field-error">{error}</p>}
  </div>;
}
