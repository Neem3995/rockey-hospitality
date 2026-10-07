import { useCallback, useState } from 'react';
import useAuth from '../../hooks/useAuth.js';
import useRead from '../../hooks/useRead.js';
import { authScopeKey } from '../../services/formValidation.js';
import { getEmployee } from '../../services/employeeService.js';
import { ApiError } from '../../services/apiClient.js';
import EmployeeDetails from './EmployeeDetails.jsx';
import Button from '../ui/Button.jsx';
import Modal from '../ui/Modal.jsx';
import Spinner from '../ui/Spinner.jsx';
import ErrorNotice from '../ui/ErrorNotice.jsx';

/** @param {{userId: number, employeeId: number}} props */
function OwnEmployee({ userId, employeeId }) {
  const [open, setOpen] = useState(false);
  return <><Button variant="secondary" onClick={() => setOpen(true)}>View employee profile</Button>
    {open && <SelfDialog userId={userId} employeeId={employeeId} onClose={() => setOpen(false)} />}</>;
}
/** @param {{userId: number, employeeId: number, onClose: () => void}} props */
function SelfDialog({ userId, employeeId, onClose }) {
  const load = useCallback(async (/** @type {AbortSignal} */ signal) => {
    const employee = await getEmployee(employeeId, signal);
    if (employee.userId !== userId) throw new ApiError(403);
    return employee;
  }, [userId, employeeId]);
  const read = useRead(`${userId}:${employeeId}`, load);
  return <Modal isOpen title="Your employee profile" onClose={onClose}>
    {read.loading && <Spinner label="Loading your employee profile…" />}
    {read.error && <ErrorNotice title="Employee profile unavailable" message={read.error.message} onRetry={read.reload} />}
    {read.data && <EmployeeDetails employee={read.data} />}
  </Modal>;
}
export default function EmployeeSelfProfile() {
  const auth = useAuth();
  if (auth.status !== 'authenticated' || auth.user?.role !== 'STAFF' || !auth.user.employeeId) return null;
  return <OwnEmployee key={authScopeKey(auth)} userId={auth.user.id} employeeId={auth.user.employeeId} />;
}
