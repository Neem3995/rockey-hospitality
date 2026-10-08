/*
 * STUDY NOTE: main.jsx renders App inside the router and auth provider.
 * We match the current URL to a page, put it inside AppLayout and use AuthGuard for protected views.
 * Props select registration mode and role eligibility; route changes select a new rendered page.
 * This file does not fetch work or authorize the API. Backend security remains the final check.
 */
import { Navigate, Route, Routes } from 'react-router';
import AppLayout from './components/layout/AppLayout.jsx';
import AuthGuard from './routes/AuthGuard.jsx';
import AuthPage from './pages/auth/AuthPage.jsx';
import DashboardPage from './pages/DashboardPage.jsx';
import TasksPage from './pages/TasksPage.jsx';
import RoomsPage from './pages/RoomsPage.jsx';
import TeamPage from './pages/TeamPage.jsx';
export default function App() {
  return <Routes><Route element={<AppLayout />}>
    <Route index element={<Navigate to="/dashboard" replace />} />
    <Route path="/login" element={<AuthGuard publicOnly><AuthPage /></AuthGuard>} />
    <Route path="/register" element={<AuthGuard publicOnly><AuthPage registration /></AuthGuard>} />
    <Route path="/dashboard" element={<AuthGuard><DashboardPage /></AuthGuard>} />
    <Route path="/tasks" element={<AuthGuard><TasksPage /></AuthGuard>} />
    <Route path="/rooms" element={<AuthGuard roles={['MANAGER', 'ADMIN']}><RoomsPage /></AuthGuard>} />
    <Route path="/team" element={<AuthGuard roles={['MANAGER', 'ADMIN']}><TeamPage /></AuthGuard>} />
    <Route path="*" element={<section><h1>Page not found</h1><a href="/dashboard">Return to dashboard</a></section>} />
  </Route></Routes>;
}
