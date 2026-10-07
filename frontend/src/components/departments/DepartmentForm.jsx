import { useState } from 'react';
import Input from '../ui/Input.jsx';
import Button from '../ui/Button.jsx';
import useAction from '../../hooks/useAction.js';
import { createDepartment, updateDepartment } from '../../services/departmentService.js';
import { textError } from '../../services/formValidation.js';
/** @param {{department?: import('../../services/managementDtos.js').Department, onSuccess: () => void, onPending: (pending: boolean) => void}} props */
export default function DepartmentForm({ department, onSuccess, onPending }) {
  const [name, setName] = useState(department?.name || '');
  const [description, setDescription] = useState(department?.description || '');
  const [errors, setErrors] = useState(/** @type {Record<string, string>} */ ({}));
  const action = useAction(onSuccess);
  const fieldErrors = { ...errors, ...action.error?.fieldErrors };
  /** @param {import('react').FormEvent<HTMLFormElement>} event */
  async function submit(event) {
    event.preventDefault();
    if (action.pending || action.uncertain) return;
    const issues = { name: textError(name, 2, 100), description: description.length > 255 ? 'Use at most 255 characters.' : '' };
    setErrors(issues);
    if (Object.values(issues).some(Boolean)) return;
    onPending(true);
    try { await action.run((signal) => department ? updateDepartment(department.id, { name, description }, signal) : createDepartment({ name, description }, signal)); }
    finally { onPending(false); }
  }
  return <form noValidate onSubmit={submit} className="management-form">
    <Input id="department-name" label="Department name" required minLength={2} maxLength={100} value={name} onChange={(event) => setName(event.target.value)} error={fieldErrors.name} />
    <Input id="department-description" label="Description" maxLength={255} value={description} onChange={(event) => setDescription(event.target.value)} error={fieldErrors.description} />
    {action.error && <p role="alert" className="status-danger">{action.error.message}</p>}
    {action.uncertain && <p role="alert">The result is uncertain. Close and reload the list before trying again.</p>}
    <Button type="submit" disabled={action.uncertain} isLoading={action.pending} loadingLabel="Saving department…">Save department</Button>
  </form>;
}
