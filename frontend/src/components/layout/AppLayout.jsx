import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router';
import Button from '../ui/Button.jsx';
import { HOTEL_TIME_ZONE } from '../../utils/hotelTime.js';
import ThemeToggle from './ThemeToggle.jsx';
import lightLogo from '../../assets/brand/Light_Mode_Logo.png';
import darkLogo from '../../assets/brand/Dark_Mode_Logo.png';
import useAuth from '../../hooks/useAuth.js';
import { routeDefinitions } from '../../routes/routeDefinitions.js';

export default function AppLayout() {
  const [menuOpen, setMenuOpen] = useState(false);
  const auth = useAuth();
  const location = useLocation();
  const previousPath = useRef(location.pathname);
  useEffect(() => {
    const title = routeDefinitions.find((route) => route.path === location.pathname)?.title || 'Hotel operations';
    document.title = `${title} | Rockey`;
    if (previousPath.current !== location.pathname) {
      document.getElementById('main-content')?.focus();
      previousPath.current = location.pathname;
    }
  }, [location.pathname]);
  const currentRole = auth.user?.role;
  const navigation = auth.status === 'authenticated' && currentRole
    ? routeDefinitions.filter((route) => route.access === 'protected' && !route.path.includes(':') && route.roles.includes(currentRole))
    : routeDefinitions.filter((route) => route.access === 'public');

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">Skip to content</a>
      <header className="site-header">
        <div className="header-inner">
          <Link className="brand" to="/" aria-label="Rockey home" onClick={() => setMenuOpen(false)}>
            <span className="brand-logos" aria-hidden="true">
              <img className="brand-logo logo-light" src={lightLogo} alt="" width="64" height="64" />
              <img className="brand-logo logo-dark" src={darkLogo} alt="" width="64" height="64" />
            </span>
            <span>Rockey<span className="brand-subtitle">HOTEL OPERATIONS</span></span>
          </Link>
          <div className="header-actions">
            <ThemeToggle />
            <Button
              className="menu-toggle"
              variant="secondary"
              aria-expanded={menuOpen}
              aria-controls="main-navigation"
              onClick={() => setMenuOpen(!menuOpen)}
            >
              {menuOpen ? 'Close menu' : 'Menu'}
            </Button>
          </div>
          <nav id="main-navigation" aria-label="Main navigation" className={menuOpen ? 'navigation is-open' : 'navigation'}>
            {navigation.map((route) => <NavLink key={route.path} to={route.path} onClick={() => setMenuOpen(false)}>{route.title}</NavLink>)}
            {auth.status === 'authenticated' && <NavLink to="/dashboard" onClick={() => setMenuOpen(false)}>Your account</NavLink>}
          </nav>
        </div>
      </header>
      <main id="main-content" tabIndex={-1} className="main-content">
        <Outlet />
      </main>
      <footer className="site-footer">
        <span>Rockey · Hotel operations</span>
        <span>Hotel time zone: {HOTEL_TIME_ZONE}</span>
      </footer>
    </div>
  );
}
