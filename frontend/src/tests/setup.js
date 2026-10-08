import { cleanup } from '@testing-library/react';
import { afterEach, vi } from 'vitest';

afterEach(() => cleanup());

// jsdom has no browser top layer/focus trap. This adapter checks our controlled
// dialog wiring only; native focus trapping/Escape behavior needs browser QA.
Object.defineProperties(HTMLDialogElement.prototype, {
  showModal: {
    configurable: true,
    value: vi.fn(/** @this {HTMLDialogElement} */ function () { this.setAttribute('open', ''); }),
  },
  close: {
    configurable: true,
    value: vi.fn(/** @this {HTMLDialogElement} */ function () { this.removeAttribute('open'); }),
  },
});
