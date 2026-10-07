/**
 * @typedef {Omit<import('react').InputHTMLAttributes<HTMLInputElement>, 'id'> & {
 *   id: string, label: string, error?: string, hint?: string
 * }} InputProps
 */

/** @param {InputProps} props */
export default function Input({ id, label, error, hint, type = 'text', className = '', ...props }) {
  const descriptionIds = [
    props['aria-describedby'], hint ? `${id}-hint` : null, error ? `${id}-error` : null,
  ].filter(Boolean).join(' ') || undefined;

  return (
    <div className="field">
      <label htmlFor={id}>{props.required ? `${label} (required)` : label}</label>
      {hint && <p id={`${id}-hint`} className="field-hint">{hint}</p>}
      <input
        {...props}
        id={id}
        type={type}
        className={`input ${className}`.trim()}
        aria-invalid={error ? true : props['aria-invalid']}
        aria-describedby={descriptionIds}
      />
      {error && <p id={`${id}-error`} className="field-error" role="alert">{error}</p>}
    </div>
  );
}
