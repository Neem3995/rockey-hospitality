import { useEffect, useId, useRef } from 'react';
import Button from './Button.jsx';

/** @param {import('react').KeyboardEvent<HTMLDialogElement>} event */
function keepTabInDialog(event) {
  if (event.key !== 'Tab') return;
  /** @type {NodeListOf<HTMLElement>} */
  const candidates = event.currentTarget.querySelectorAll(
    'button, input, select, textarea, a[href], [tabindex]',
  );
  const controls = Array.from(candidates).filter((element) => (
    element.tabIndex >= 0 && !element.matches(':disabled, input[type="hidden"]')
    && !element.closest('[hidden]')
    && getComputedStyle(element).display !== 'none'
    && getComputedStyle(element).visibility !== 'hidden'
  ));
  const first = controls[0];
  const last = controls.at(-1);
  if (event.shiftKey && first && document.activeElement === first) {
    event.preventDefault();
    last?.focus();
  } else if (!event.shiftKey && last && document.activeElement === last) {
    event.preventDefault();
    first?.focus();
  }
}

/** Restore immediately; keep a fallback if a later background refresh removes that opener.
 * @param {Element|null} opener @param {HTMLElement|null|undefined} fallback
 */
function restoreFocus(opener, fallback) {
  const available = () => opener instanceof HTMLElement && opener.isConnected && !opener.matches(':disabled');
  if (!available()) { if (fallback?.isConnected) fallback.focus(); return; }
  if (!(opener instanceof HTMLElement)) return;
  opener.focus();
  if (!fallback) return;
  const scope = opener.closest('main') || document.body;
  const stop = () => { observer.disconnect(); document.removeEventListener('focusin', changedFocus); };
  const changedFocus = () => { if (document.activeElement !== opener) stop(); };
  const observer = new MutationObserver(() => {
    if (available()) return;
    if ((document.activeElement === document.body || document.activeElement === opener) && fallback.isConnected) fallback.focus();
    stop();
  });
  observer.observe(scope, { childList: true, subtree: true, attributes: true, attributeFilter: ['disabled','hidden'] });
  document.addEventListener('focusin', changedFocus);
}

/**
 * @param {{isOpen: boolean, onClose: () => void, title: string,
 *   children: import('react').ReactNode, footer?: import('react').ReactNode,
 *   fallbackFocusRef?: import('react').RefObject<HTMLElement | null>}} props
 */
export default function Modal({ isOpen, onClose, title, children, footer, fallbackFocusRef }) {
  const dialogRef = useRef(/** @type {HTMLDialogElement | null} */ (null));
  const titleId = useId();

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog || !isOpen) return;

    const previousFocus = document.activeElement;
    const fallbackFocus = fallbackFocusRef?.current;
    dialog.showModal();
    // Chrome force-closes after a repeated Escape even when onClose refuses
    // (e.g. a pending write); controlled isOpen stays authoritative.
    const keepOpen = () => { if (dialog.isConnected && !dialog.open) dialog.showModal(); };
    dialog.addEventListener('close', keepOpen);
    return () => {
      dialog.removeEventListener('close', keepOpen);
      if (dialog.open) dialog.close();
      restoreFocus(previousFocus, fallbackFocus);
    };
  }, [isOpen, fallbackFocusRef]);

  return (
    <dialog
      ref={dialogRef}
      className="modal"
      aria-labelledby={titleId}
      onKeyDown={keepTabInDialog}
      onCancel={(event) => { event.preventDefault(); onClose(); }}
    >
      <div className="modal-header">
        <h2 id={titleId}>{title}</h2>
        <Button variant="secondary" aria-label={`Close ${title}`} onClick={onClose}>Close</Button>
      </div>
      <div className="modal-body">{children}</div>
      {footer && <div className="modal-footer">{footer}</div>}
    </dialog>
  );
}
