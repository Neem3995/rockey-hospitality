import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet } from 'react-router';
import useAuth from '../../hooks/useAuth.js';
import Button from '../ui/Button.jsx';
import { routeDefinitions } from '../../routes/routeDefinitions.js';
import { readThemePreference, saveThemePreference } from '../../utils/theme.js';
import { ApiError } from '../../services/apiClient.js';
import { roleLabel } from '../../services/housekeepingService.js';
import lightLogo from '../../assets/brand/Light_Mode_Logo.png';
import darkLogo from '../../assets/brand/Dark_Mode_Logo.png';

export default function AppLayout() {
  const auth = useAuth();
  const [theme, setTheme] = useState(readThemePreference);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const errorRef = useRef(/** @type {HTMLParagraphElement|null} */ (null));
  useEffect(() => { document.documentElement.dataset.theme = theme; document.documentElement.style.colorScheme = theme; }, [theme]);
  useEffect(() => { if (error) errorRef.current?.focus(); }, [error]);
  async function signOut() {
    setPending(true); setError('');
    try { await auth.logout(); } catch (failure) { setError('Sign-out could not be confirmed. ' + (failure instanceof ApiError ? failure.message : 'Retry.')); }
    finally { setPending(false); }
  }
  function toggleTheme() { const next = theme === 'light' ? 'dark' : 'light'; setTheme(next); saveThemePreference(next); }
  const user = auth.user;
  const links = user ? routeDefinitions.filter(route => route.access === 'protected' && route.roles.includes(user.role)) : [];
  return <div className="app-shell">
    <a className="skip-link" href="#main">Skip to content</a>
    <header className="app-header">
      <Link to={auth.user ? '/dashboard' : '/login'} aria-label="Rockey home"><img className="brand-logo logo-light" src={lightLogo} alt="" width="56" height="56" /><img className="brand-logo logo-dark" src={darkLogo} alt="" width="56" height="56" /></Link>
      <div className="header-actions"><Button variant="secondary" onClick={toggleTheme} aria-label={'Switch to ' + (theme === 'light' ? 'dark' : 'light') + ' mode'} title={'Switch to ' + (theme === 'light' ? 'dark' : 'light') + ' mode'}>Theme: {theme}</Button>
        {auth.user && <Button variant="secondary" isLoading={pending} onClick={() => void signOut()}>Sign out</Button>}</div>
    </header>
    {error && <p className="auth-error" role="alert" ref={errorRef} tabIndex={-1}>{error}</p>}
    {auth.user && <nav className="main-nav" aria-label="Housekeeping navigation">{links.map(route => <NavLink key={route.path} to={route.path}>{route.path === '/tasks' && auth.user?.role === 'USER' ? 'My Tasks' : route.title}</NavLink>)}<span>{roleLabel(auth.user.role)}</span></nav>}
    <main id="main" tabIndex={-1}><Outlet /></main>
    <footer>Rockey · Housekeeping · America/New_York</footer>
  </div>;
}
