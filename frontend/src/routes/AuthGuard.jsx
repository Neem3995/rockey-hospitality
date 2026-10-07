import { Navigate } from 'react-router';
import useAuth from '../hooks/useAuth.js';
import SessionStatus from '../pages/auth/SessionStatus.jsx';

/** @param {{roles?: import('./routeDefinitions.js').Role[], publicOnly?: boolean, children: import('react').ReactNode}} props */
export default function AuthGuard({ roles, publicOnly = false, children }) {
  const auth = useAuth();
  if (auth.status === 'checking' || auth.status === 'recoverable-error') return <SessionStatus />;
  if (publicOnly) return auth.status === 'authenticated' ? <Navigate to="/dashboard" replace /> : children;
  if (auth.status !== 'authenticated' || !auth.user) return <Navigate to="/login" replace />;
  if (roles && !roles.includes(auth.user.role)) return (
    <section className="page-intro"><h1>Access denied</h1><p role="alert">Your account cannot access this workspace.</p></section>
  );
  return <div key={auth.sessionKey}>{children}</div>;
}
