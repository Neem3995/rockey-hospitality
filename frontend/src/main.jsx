import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import App from './App.jsx';
import AuthProvider from './auth/AuthProvider.jsx';
import './styles/index.css';

const rootElement = document.getElementById('root');
if (!rootElement) throw new Error('The application root is missing.');

createRoot(rootElement).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider><App /></AuthProvider>
    </BrowserRouter>
  </StrictMode>,
);
