/*
 * STUDY NOTE: Pages and other UI components pass labels, native button props and callbacks here.
 * On render we show a normal button, or a disabled busy button with a loading label.
 * The caller handles clicks and API work. This component has no auth state or network request
 * and does not prevent every possible duplicate write; pages also use a submitting ref.
 */
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
