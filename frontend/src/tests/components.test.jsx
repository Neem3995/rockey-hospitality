import { StrictMode, useState } from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import Button from '../components/ui/Button.jsx';
import Input from '../components/ui/Input.jsx';
import Card from '../components/ui/Card.jsx';
import Modal from '../components/ui/Modal.jsx';
import Table from '../components/ui/Table.jsx';
import ErrorNotice from '../components/ui/ErrorNotice.jsx';
import ScaffoldPage from '../pages/ScaffoldPage.jsx';

describe('ErrorNotice', () => {
  it('uses caller-provided wording and preserves alert announcements and Card styling', () => {
    render(<ErrorNotice title="Workspace unavailable" message="Please try again later." retryLabel="Reload workspace" onRetry={vi.fn()} />);
    expect(screen.getByRole('heading', { name: 'Workspace unavailable', level: 2 }).parentElement?.classList.contains('card')).toBe(true);
    expect(screen.getByRole('alert').textContent).toBe('Please try again later.');
    expect(screen.getByRole('button', { name: 'Reload workspace' }).classList.contains('button-secondary')).toBe(true);
    expect(screen.queryByText(/dashboard/i)).toBeNull();
  });

  it('supports Enter with a caller-provided retry label without submitting a form', async () => {
    const onRetry = vi.fn();
    render(<ErrorNotice title="Unavailable" message="Try again." retryLabel="Try loading again" onRetry={onRetry} />);
    const retry = screen.getByRole('button', { name: 'Try loading again' });
    expect(retry).toHaveProperty('type', 'button');
    retry.focus();
    await userEvent.keyboard('{Enter}');
    expect(onRetry).toHaveBeenCalledTimes(1);
    expect(document.activeElement).toBe(retry);
  });

  it('defaults to a generic Retry label and supports Space activation', async () => {
    const onRetry = vi.fn();
    render(<ErrorNotice title="Unavailable" message="Try again." onRetry={onRetry} />);
    screen.getByRole('button', { name: /^Retry$/ }).focus();
    await userEvent.keyboard(' ');
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('does not offer a non-functional retry action when no callback is supplied', () => {
    render(<ErrorNotice title="Access unavailable" message="Contact an administrator." />);
    expect(screen.getByRole('alert').textContent).toBe('Contact an administrator.');
    expect(screen.queryByRole('button')).toBeNull();
  });

  it('updates the announcement and renders message contents as text, not HTML', () => {
    const view = render(<ErrorNotice title="Unavailable" message="First error." />);
    view.rerender(<ErrorNotice title="Unavailable" message="<img src=x onerror=alert(1)>" />);
    expect(screen.getByRole('alert').textContent).toBe('<img src=x onerror=alert(1)>');
    expect(screen.queryByText('First error.')).toBeNull();
    expect(screen.queryByRole('img')).toBeNull();
  });
});

it.each(['public', 'protected'])('removes obsolete authentication wording from the %s scaffold fallback', (access) => {
  render(<ScaffoldPage route={{ path: '/unused', title: 'Workspace', access: /** @type {'public' | 'protected'} */ (access), roles: [] }} />);
  expect(screen.queryByText(/Authentication is coming in FE-02|Sign-in and registration are not connected yet/)).toBeNull();
  expect(screen.getByText(/No hotel operational data is loaded or displayed/)).toBeTruthy();
  expect(screen.getByRole('heading', { name: access === 'protected' ? 'Protected workspace scaffold' : 'Workspace scaffold', level: 2 })).toBeTruthy();
});

describe('Button', () => {
  it('defaults to a non-submit button and supports keyboard activation', async () => {
    const onClick = vi.fn();
    render(<Button onClick={onClick}>Save</Button>);
    const button = screen.getByRole('button', { name: 'Save' });
    expect(button).toHaveProperty('type', 'button');
    button.focus();
    await userEvent.keyboard('{Enter}');
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('disables duplicate activation while loading and announces pending state', async () => {
    const onClick = vi.fn();
    render(<Button isLoading loadingLabel="Saving…" onClick={onClick}>Save</Button>);
    const button = screen.getByRole('button', { name: 'Saving…' });
    expect(button).toHaveProperty('disabled', true);
    expect(button.getAttribute('aria-busy')).toBe('true');
    await userEvent.click(button);
    expect(onClick).not.toHaveBeenCalled();
  });

  it('preserves disabled, submit, variant and accessible label props', () => {
    render(<Button type="submit" disabled variant="danger" aria-label="Remove item">Remove</Button>);
    const button = screen.getByRole('button', { name: 'Remove item' });
    expect(button).toHaveProperty('type', 'submit');
    expect(button).toHaveProperty('disabled', true);
    expect(button.classList.contains('button-danger')).toBe(true);
  });
});

describe('Input', () => {
  it('associates its label, hint, validation error and existing description', () => {
    render(<><p id="extra">Additional help</p><Input id="room-label" label="Room label" required hint="Use a unique label." error="A label is required." aria-describedby="extra" /></>);
    const input = screen.getByRole('textbox', { name: 'Room label (required)' });
    expect(input).toHaveProperty('required', true);
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(input.getAttribute('aria-describedby')).toBe('extra room-label-hint room-label-error');
    expect(screen.getByRole('alert').textContent).toBe('A label is required.');
  });

  it('supports controlled values and onChange without error attributes', async () => {
    function InputHarness() {
      const [value, setValue] = useState('');
      return <Input id="name" label="Name" value={value} onChange={(event) => setValue(event.target.value)} />;
    }
    render(<InputHarness />);
    const input = screen.getByRole('textbox', { name: 'Name' });
    await userEvent.type(input, 'Sample');
    expect(input).toHaveProperty('value', 'Sample');
    expect(input.hasAttribute('aria-describedby')).toBe(false);
    expect(input.hasAttribute('aria-invalid')).toBe(false);
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('preserves explicit invalid, disabled and input type props', () => {
    render(<Input id="email" label="Email" type="email" disabled aria-invalid="true" />);
    const input = screen.getByRole('textbox', { name: 'Email' });
    expect(input).toHaveProperty('type', 'email');
    expect(input).toHaveProperty('disabled', true);
    expect(input.getAttribute('aria-invalid')).toBe('true');
  });
});

describe('Card', () => {
  it('renders a semantic heading and composed children', () => {
    render(<Card title="Summary" className="summary"><p>Content</p></Card>);
    expect(screen.getByRole('heading', { name: 'Summary', level: 2 })).toBeTruthy();
    expect(screen.getByText('Content').parentElement?.classList.contains('summary')).toBe(true);
  });

  it('supports a card without a heading', () => {
    render(<Card>Content only</Card>);
    expect(screen.queryByRole('heading')).toBeNull();
    expect(screen.getByText('Content only')).toBeTruthy();
  });
});

describe('Modal controlled behavior (jsdom adapter, not native focus-trap evidence)', () => {
  it('stays hidden while closed and exposes its accessible title when opened', () => {
    const onClose = vi.fn();
    const { rerender } = render(<Modal isOpen={false} onClose={onClose} title="Confirm">Details</Modal>);
    expect(screen.queryByRole('dialog')).toBeNull();
    rerender(<Modal isOpen onClose={onClose} title="Confirm" footer={<Button>Confirm</Button>}>Details</Modal>);
    expect(screen.getByRole('dialog', { name: 'Confirm' })).toHaveProperty('open', true);
    expect(screen.getByRole('button', { name: 'Confirm' })).toBeTruthy();
  });

  it('requests close on button and native cancel events without changing controlled state', async () => {
    const onClose = vi.fn();
    render(<Modal isOpen onClose={onClose} title="Confirm">Details</Modal>);
    await userEvent.click(screen.getByRole('button', { name: 'Close Confirm' }));
    const dialog = screen.getByRole('dialog');
    fireEvent(dialog, new Event('cancel', { cancelable: true }));
    expect(onClose).toHaveBeenCalledTimes(2);
    expect(dialog).toHaveProperty('open', true);
  });

  it('reopens after a forced native close while controlled state remains open', () => {
    const onClose = vi.fn();
    const { unmount } = render(<StrictMode><Modal isOpen onClose={onClose} title="Pending">Details</Modal></StrictMode>);
    const dialog = /** @type {HTMLDialogElement} */ (screen.getByRole('dialog'));
    dialog.close();
    fireEvent(dialog, new Event('close'));
    expect(dialog).toHaveProperty('open', true);
    unmount();
    expect(dialog).toHaveProperty('open', false);
  });

  it('closes and restores the opener when controlled state changes, including StrictMode', async () => {
    function ModalHarness() {
      const [isOpen, setIsOpen] = useState(false);
      return <><Button onClick={() => setIsOpen(true)}>Open</Button><Modal isOpen={isOpen} onClose={() => setIsOpen(false)} title="Confirm"><p>Details</p></Modal></>;
    }
    render(<StrictMode><ModalHarness /></StrictMode>);
    const opener = screen.getByRole('button', { name: 'Open' });
    await userEvent.click(opener);
    expect(screen.getByRole('dialog')).toHaveProperty('open', true);
    await userEvent.click(screen.getByRole('button', { name: 'Close Confirm' }));
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(document.activeElement).toBe(opener);
  });

  it('closes an initially open dialog on unmount', () => {
    const { unmount } = render(<StrictMode><Modal isOpen onClose={() => {}} title="Confirm">Details</Modal></StrictMode>);
    const dialog = screen.getByRole('dialog');
    unmount();
    expect(dialog).toHaveProperty('open', false);
  });

  it('wraps Tab and Shift+Tab at the visible dialog boundaries', async () => {
    render(<Modal isOpen onClose={vi.fn()} title="Confirm" footer={<Button>Last action</Button>}><Input id="modal-value" label="Value" /><Button hidden>Hidden action</Button><Button disabled>Disabled action</Button></Modal>);
    const first = screen.getByRole('button', { name: 'Close Confirm' });
    const last = screen.getByRole('button', { name: 'Last action' });
    first.focus();
    await userEvent.keyboard('{Shift>}{Tab}{/Shift}');
    expect(document.activeElement).toBe(last);
    await userEvent.keyboard('{Tab}');
    expect(document.activeElement).toBe(first);
    await userEvent.keyboard('{Tab}');
    expect(document.activeElement).toBe(screen.getByRole('textbox', { name: 'Value' }));
  });

  it('leaves non-Tab keys and disabled/negative-tab-index controls alone', async () => {
    render(<Modal isOpen onClose={vi.fn()} title="Confirm"><Input id="modal-value" label="Value" /><Button tabIndex={-1}>Not in tab order</Button></Modal>);
    const input = screen.getByRole('textbox', { name: 'Value' });
    input.focus();
    await userEvent.keyboard('x');
    expect(input).toHaveProperty('value', 'x');
    await userEvent.keyboard('{Tab}');
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Close Confirm' }));
  });
});

describe('Table', () => {
  const columns = [
    { key: 'name', label: 'Name', render: (/** @type {{id: number, name: string}} */ row) => row.name },
  ];

  it('renders a caption, column headers and stable row content', () => {
    render(<Table caption="Sample list" columns={columns} rows={[{ id: 1, name: 'Example' }]} getRowKey={(row) => row.id} />);
    expect(screen.getByRole('table', { name: 'Sample list' })).toBeTruthy();
    expect(screen.getByRole('columnheader', { name: 'Name' }).getAttribute('scope')).toBe('col');
    expect(screen.getByRole('cell', { name: 'Example' })).toBeTruthy();
    expect(screen.getByRole('region', { name: 'Sample list table' })).toHaveProperty('tabIndex', 0);
  });

  it('renders default and custom empty states without fake data', () => {
    const { rerender } = render(<Table caption="Sample list" columns={columns} rows={[]} getRowKey={(row) => row.id} />);
    expect(screen.getByRole('cell', { name: 'No results found.' })).toHaveProperty('colSpan', 1);
    rerender(<Table caption="Sample list" columns={columns} rows={[]} getRowKey={(row) => row.id} emptyMessage="Nothing to display." />);
    expect(screen.getByRole('cell', { name: 'Nothing to display.' })).toBeTruthy();
  });

  it('renders text as text, never as HTML', () => {
    render(<Table caption="Sample list" columns={columns} rows={[{ id: 1, name: '<script>example</script>' }]} getRowKey={(row) => row.id} />);
    expect(screen.getByRole('cell', { name: '<script>example</script>' }).querySelector('script')).toBeNull();
  });
});
