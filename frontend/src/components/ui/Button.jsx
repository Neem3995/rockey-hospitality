/**
 * @typedef {import('react').ButtonHTMLAttributes<HTMLButtonElement> & {
 *   variant?: 'primary' | 'secondary' | 'danger',
 *   isLoading?: boolean,
 *   loadingLabel?: string
 * }} ButtonProps
 */

/** @param {ButtonProps} props */
export default function Button({
  children, variant = 'primary', isLoading = false, loadingLabel = 'Please wait…',
  disabled = false, type = 'button', className = '', ...props
}) {
  return (
    <button
      {...props}
      type={type}
      className={`button button-${variant} ${className}`.trim()}
      disabled={disabled || isLoading}
      aria-busy={isLoading || undefined}
    >
      {isLoading ? <><span className="spinner" aria-hidden="true" />{loadingLabel}</> : children}
    </button>
  );
}
