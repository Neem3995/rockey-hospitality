import StatusBadge from '../ui/StatusBadge.jsx';
/** @param {{employee: import('../../services/managementDtos.js').Employee}} props */
export default function EmployeeDetails({ employee }) {
  return <dl className="record-details">
    <div><dt>Name</dt><dd>{employee.name}</dd></div>
    <div><dt>Email</dt><dd>{employee.email}</dd></div>
    <div><dt>Department</dt><dd>{employee.department.name}</dd></div>
    <div><dt>Job role</dt><dd>{employee.jobRole}</dd></div>
    <div><dt>Status</dt><dd><StatusBadge active={employee.status === 'ACTIVE'} /></dd></div>
    <div><dt>Application login</dt><dd>{employee.userId === null ? 'No login account' : 'Linked account'}</dd></div>
    <div><dt>Created (server local time)</dt><dd>{employee.createdAt.replace('T', ' ')}</dd></div>
    <div><dt>Updated (server local time)</dt><dd>{employee.updatedAt.replace('T', ' ')}</dd></div>
  </dl>;
}
