import { useEffect, useRef, useState } from 'react';
import useAuth from '../../hooks/useAuth.js';
import Button from '../ui/Button.jsx';
import Card from '../ui/Card.jsx';
import { StaleRequestError } from '../../services/apiClient.js';
import EmployeeSelfProfile from '../employees/EmployeeSelfProfile.jsx';

export default function AccountPanel() {
  const auth = useAuth();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const attempt = useRef(0);
  const active = useRef(true);
  const errorRef = useRef(/** @type {HTMLParagraphElement | null} */ (null));
  useEffect(() => { active.current = true; return () => { active.current = false; }; }, []);
  useEffect(() => { if (error) errorRef.current?.focus(); }, [error]);
  async function signOut() {
    if (pending) return;
    const current = ++attempt.current;
    setPending(true);
    setError('');
    try { await auth.logout(); }
    catch (failure) {
      if (active.current && current === attempt.current && !(failure instanceof StaleRequestError)) setError('Sign-out could not be confirmed. Try again.');
    } finally { if (active.current && current === attempt.current) setPending(false); }
  }
  if (!auth.user) return null;
  return (
    <Card title="Your account" className="account-panel">
      <dl>
        <div><dt>Name</dt><dd>{auth.user.name}</dd></div>
        <div><dt>Email</dt><dd>{auth.user.email}</dd></div>
        <div><dt>Role</dt><dd>{auth.user.role}</dd></div>
        <div><dt>Status</dt><dd>{auth.user.status}</dd></div>
        {auth.user.departmentSummary && <div><dt>Department</dt><dd>{auth.user.departmentSummary.name}</dd></div>}
      </dl>
      <EmployeeSelfProfile />
      {error && <p role="alert" className="status-danger" tabIndex={-1} ref={errorRef}>{error}</p>}
      <Button variant="secondary" onClick={signOut} isLoading={pending} loadingLabel="Signing out…">Sign out</Button>
    </Card>
  );
}
