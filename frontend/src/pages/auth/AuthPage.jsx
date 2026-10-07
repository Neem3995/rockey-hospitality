import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import Button from '../../components/ui/Button.jsx';
import Input from '../../components/ui/Input.jsx';
import Card from '../../components/ui/Card.jsx';
import useAuth from '../../hooks/useAuth.js';
import { ApiError, StaleRequestError } from '../../services/apiClient.js';

/** @param {{registration?: boolean}} props */
export default function AuthPage({ registration = false }) {
  const auth = useAuth();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState(/** @type {Record<string, string>} */ ({}));
  const errorRef = useRef(/** @type {HTMLDivElement | null} */ (null));
  const active = useRef(true);
  useEffect(() => { active.current = true; return () => { active.current = false; }; }, []);
  useEffect(() => { if (error) errorRef.current?.focus(); }, [error]);

  /** @param {import('react').FormEvent<HTMLFormElement>} event */
  async function submit(event) {
    event.preventDefault();
    if (pending) return;
    /** @type {Record<string, string>} */
    const fields = {};
    if (registration && (name.trim().length < 2 || name.length > 100)) fields.name = 'Enter a name between 2 and 100 characters.';
    if (!email || email.length > 120 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) fields.email = 'Enter a valid email address of up to 120 characters.';
    if (!password.trim() || password.length > 72 || (registration && password.length < 8)) {
      fields.password = registration ? 'Enter a password between 8 and 72 characters.' : 'Enter your password (up to 72 characters).';
    }
    setFieldErrors(fields);
    setError('');
    if (Object.keys(fields).length) { setError('Check the highlighted fields.'); return; }
    setPending(true);
    try {
      if (registration) await auth.register({ name, email, password });
      else await auth.login({ email, password });
    } catch (failure) {
      if (active.current && !(failure instanceof StaleRequestError)) {
        setError(failure instanceof ApiError ? failure.message : 'Unable to complete this request. Try again.');
        setFieldErrors(failure instanceof ApiError ? failure.fieldErrors : {});
      }
    } finally {
      if (active.current) { setPending(false); setPassword(''); }
    }
  }

  return (
    <section className="page-intro auth-page">
      <p className="eyebrow">Rockey · Hotel operations</p>
      <h1>{registration ? 'Create an account' : 'Sign in'}</h1>
      <p className="page-description">{registration ? 'Create a USER account to browse events and manage your own registrations.' : 'Sign in to your hotel operations workspace.'}</p>
      <Card>
        <form noValidate onSubmit={submit} aria-busy={pending}>
          {error && <div className="auth-error" role="alert" tabIndex={-1} ref={errorRef}>{error}</div>}
          {registration && <Input id="auth-name" name="name" label="Name" autoComplete="name" required minLength={2} maxLength={100} value={name} onChange={(e) => setName(e.target.value)} error={fieldErrors.name} disabled={pending} />}
          <Input id="auth-email" name="email" type="email" label="Email" autoComplete="username" required maxLength={120} value={email} onChange={(e) => setEmail(e.target.value)} error={fieldErrors.email} disabled={pending} />
          <Input id="auth-password" name="password" type="password" label="Password" autoComplete={registration ? 'new-password' : 'current-password'} required minLength={registration ? 8 : undefined} maxLength={72} value={password} onChange={(e) => setPassword(e.target.value)} error={fieldErrors.password} disabled={pending} hint={registration ? 'Use 8–72 characters.' : undefined} />
          <Button type="submit" isLoading={pending} loadingLabel={registration ? 'Creating account…' : 'Signing in…'}>{registration ? 'Create USER account' : 'Sign in'}</Button>
        </form>
        <p className="auth-form-link">{registration ? <Link to="/login">Already have an account? Sign in</Link> : <Link to="/register">Create a USER account</Link>}</p>
      </Card>
    </section>
  );
}
