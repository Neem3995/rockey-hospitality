import { useEffect, useState } from 'react';
import Button from '../ui/Button.jsx';
import { readThemePreference, saveThemePreference } from '../../utils/theme.js';

export default function ThemeToggle() {
  const [theme, setTheme] = useState(readThemePreference);
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    document.documentElement.style.colorScheme = theme;
  }, [theme]);

  function toggleTheme() {
    const next = theme === 'light' ? 'dark' : 'light';
    saveThemePreference(next);
    setTheme(next);
  }

  const label = `Switch to ${theme === 'light' ? 'dark' : 'light'} mode`;
  return (
    <Button variant="secondary" className="theme-toggle" onClick={toggleTheme}
      aria-label={label} title={label}>
      {theme === 'light' ? 'Dark mode' : 'Light mode'}
    </Button>
  );
}
