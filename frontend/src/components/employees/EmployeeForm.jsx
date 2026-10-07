import { useMemo, useState } from 'react';
import Input from '../ui/Input.jsx';
import Select from '../ui/Select.jsx';
import Button from '../ui/Button.jsx';
import useAction from '../../hooks/useAction.js';
import { createEmployee, updateEmployee } from '../../services/employeeService.js';
import { emailError, textError } from '../../services/formValidation.js';

/** @param {{employee?: import('../../services/managementDtos.js').Employee, departments: import('../../services/managementDtos.js').Department[], onSuccess: () => void, onPending: (pending: boolean) => void}} props */
export default function EmployeeForm({ employee, departments, onSuccess, onPending }) {
  const [name, setName] = useState(employee?.name || '');
  const [email, setEmail] = useState(employee?.email || '');
  const [departmentId, setDepartmentId] = useState(employee ? String(employee.department.id) : '');
  const [jobRole, setJobRole] = useState(employee?.jobRole || '');
  const [status, setStatus] = useState(/** @type {'ACTIVE' | 'INACTIVE'} */ (employee?.status || 'ACTIVE'));
  const [createLogin, setCreateLogin] = useState(false);
  const [loginEmail, setLoginEmail] = useState('');
  const [temporaryPassword, setTemporaryPassword] = useState('');
  const [securityRole, setSecurityRole] = useState(/** @type {'STAFF' | 'ADMIN'} */ ('STAFF'));
  const [errors, setErrors] = useState(/** @type {Record<string, string>} */ ({}));
  const action = useAction(onSuccess);
  // Historical inactive assignment may remain only on an already-inactive Employee.
  const choices = useMemo(() => departments.filter((department) => department.active
    || (employee?.status === 'INACTIVE' && department.id === employee.department.id)), [departments, employee]);
  const fieldErrors = { ...errors, ...action.error?.fieldErrors };
  function clearLogin() { setLoginEmail(''); setTemporaryPassword(''); setSecurityRole('STAFF'); }
  /** @param {import('react').FormEvent<HTMLFormElement>} event */
  async function submit(event) {
    event.preventDefault();
    if (action.pending || action.uncertain) return;
    /** @type {Record<string, string>} */
    const issues = { name: textError(name, 2, 100), email: emailError(email), jobRole: textError(jobRole, 2, 80) };
    const department = departments.find((item) => item.id === Number(departmentId));
    if (!department || (!department.active && (!employee || employee.department.id !== department.id || status !== 'INACTIVE'))) issues.departmentId = 'Choose an eligible active department.';
    if (!employee && createLogin) {
      issues.loginEmail = emailError(loginEmail);
      if (temporaryPassword.length < 8 || temporaryPassword.length > 72) issues.temporaryPassword = 'Use 8–72 characters.';
    }
    setErrors(issues);
    if (Object.values(issues).some(Boolean)) return;
    const fields = { name, email, departmentId: Number(departmentId), jobRole };
    const payload = { ...fields, createLogin, ...(createLogin ? { loginEmail, temporaryPassword, securityRole } : {}) };
    // Clear credential controls before the network round trip, including failed submissions.
    clearLogin();
    onPending(true);
    try {
      await action.run((signal) => employee
        ? updateEmployee(employee.id, { ...fields, status }, signal)
        : createEmployee(payload, signal));
    } finally { onPending(false); }
  }
  return <form noValidate onSubmit={submit} className="management-form">
    <Input id="employee-name" label="Employee name" required minLength={2} maxLength={100} value={name} onChange={(event) => setName(event.target.value)} error={fieldErrors.name} />
    <Input id="employee-email" label="Employee email" type="email" required maxLength={120} value={email} onChange={(event) => setEmail(event.target.value)} error={fieldErrors.email} />
    <Select id="employee-department" label="Department" required value={departmentId} onChange={(event) => setDepartmentId(event.target.value)} error={fieldErrors.departmentId}>
      <option value="">Choose department</option>{choices.map((department) => <option key={department.id} value={department.id}>{department.name}{department.active ? '' : ' (inactive history)'}</option>)}
    </Select>
    <Input id="employee-job-role" label="Job role" required minLength={2} maxLength={80} value={jobRole} onChange={(event) => setJobRole(event.target.value)} error={fieldErrors.jobRole} />
    {employee ? <Select id="employee-status" label="Employee status" required value={status} onChange={(event) => setStatus(/** @type {'ACTIVE' | 'INACTIVE'} */ (event.target.value))} error={fieldErrors.status}><option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option></Select>
      : <>
        <label className="checkbox-field"><input type="checkbox" checked={createLogin} onChange={(event) => { setCreateLogin(event.target.checked); clearLogin(); }} />Create application login</label>
        {createLogin && <fieldset><legend>New internal login</legend>
          <Input id="employee-login-email" label="Login email" type="email" required maxLength={120} autoComplete="off" value={loginEmail} onChange={(event) => setLoginEmail(event.target.value)} error={fieldErrors.loginEmail} />
          <Input id="employee-temporary-password" label="Temporary password" type="password" required minLength={8} maxLength={72} autoComplete="new-password" value={temporaryPassword} onChange={(event) => setTemporaryPassword(event.target.value)} error={fieldErrors.temporaryPassword} />
          <Select id="employee-security-role" label="Security role" required value={securityRole} onChange={(event) => setSecurityRole(/** @type {'STAFF' | 'ADMIN'} */ (event.target.value))} error={fieldErrors.securityRole}><option value="STAFF">STAFF</option><option value="ADMIN">ADMIN</option></Select>
          <p className="muted">Creates a new account only. Credentials are cleared on submit and never shown in the result.</p>
        </fieldset>}
      </>}
    {fieldErrors.loginConfigurationValid && <p role="alert">Check the new login configuration.</p>}
    {action.error && <p role="alert" className="status-danger">{action.error.message}</p>}
    {action.uncertain && <p role="alert">The result is uncertain. Close and reload the list before trying again.</p>}
    <Button type="submit" disabled={action.uncertain} isLoading={action.pending} loadingLabel="Saving employee…">Save employee</Button>
  </form>;
}
