/*
 * STUDY NOTE: Vite loads this module to connect React to index.html's root element.
 * First we create the root, then wrap App in BrowserRouter and AuthProvider and load our CSS.
 * StrictMode adds development checks, including effect setup/cleanup checks; it is not authorization.
 * This entry point renders the application. Pages and apiClient handle requests and session state.
 */
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
