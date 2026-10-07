import { StrictMode, useState } from 'react';
import { createRoot } from 'react-dom/client';
import Button from '../../components/ui/Button.jsx';
import Input from '../../components/ui/Input.jsx';
import Card from '../../components/ui/Card.jsx';
import Modal from '../../components/ui/Modal.jsx';
import Table from '../../components/ui/Table.jsx';
import ThemeToggle from '../../components/layout/ThemeToggle.jsx';
import '../../styles/index.css';

// Development-only browser QA, not an App route or a production build entry.
function UiFixture() {
  const [isOpen, setIsOpen] = useState(false);
  return (
    <main className="main-content">
      <h1>FE-01 UI test fixture</h1>
      <ThemeToggle />
      <p>Synthetic component examples only. No authentication or API requests.</p>
      <Card title="Shared components">
        <Input id="fixture-name" label="Sample input" hint="Example hint" required />
        <Input id="fixture-error" label="Validation example" error="Example validation message" />
        <Input id="fixture-disabled" label="Disabled example" disabled value="Disabled sample" />
        <p className="status-success">Example success message</p>
        <p className="status-warning">Example warning message</p>
        <Button>Primary example</Button>
        <Button variant="secondary">Secondary example</Button>
        <Button variant="danger">Danger example</Button>
        <Button disabled>Disabled example</Button>
        <Button isLoading>Pending example</Button>
        <Button onClick={() => setIsOpen(true)}>Open test dialog</Button>
      </Card>
      <Table
        caption="Example table"
        rows={[{ id: 1, label: 'Synthetic row' }]}
        columns={[{ key: 'label', label: 'Sample column', render: (row) => row.label }]}
        getRowKey={(row) => row.id}
      />
      <Modal isOpen={isOpen} onClose={() => setIsOpen(false)} title="Test dialog" footer={<Button onClick={() => setIsOpen(false)}>Dismiss example</Button>}>
        <Input id="fixture-dialog-input" label="Dialog input" />
      </Modal>
    </main>
  );
}

const rootElement = document.getElementById('root');
if (!rootElement) throw new Error('The test fixture root is missing.');
createRoot(rootElement).render(<StrictMode><UiFixture /></StrictMode>);
