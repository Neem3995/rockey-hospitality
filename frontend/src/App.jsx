import { Navigate, Route, Routes } from 'react-router';
import AppLayout from './components/layout/AppLayout.jsx';
import ScaffoldPage from './pages/ScaffoldPage.jsx';
import NotFoundPage from './pages/NotFoundPage.jsx';
import { routeDefinitions } from './routes/routeDefinitions.js';
import AuthGuard from './routes/AuthGuard.jsx';
import AuthPage from './pages/auth/AuthPage.jsx';
import SessionStatus from './pages/auth/SessionStatus.jsx';
import useAuth from './hooks/useAuth.js';
import DashboardPage from './pages/DashboardPage.jsx';
import EmployeesPage from './pages/admin/EmployeesPage.jsx';
import DepartmentsPage from './pages/admin/DepartmentsPage.jsx';

function HomeRedirect() {
  const auth = useAuth();
  if (auth.status === 'checking' || auth.status === 'recoverable-error') return <SessionStatus />;
  return <Navigate to={auth.status === 'authenticated' ? '/dashboard' : '/login'} replace />;
}

/** @param {{route: import('./routes/routeDefinitions.js').RouteDefinition}} props */
function ProtectedPage({ route }) {
  return (
    <AuthGuard roles={route.roles}>
      {route.path === '/dashboard' ? <DashboardPage />
        : route.path === '/admin/employees' ? <EmployeesPage />
          : route.path === '/admin/departments' ? <DepartmentsPage /> : <ScaffoldPage route={route} />}
    </AuthGuard>
  );
}

export default function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<HomeRedirect />} />
        <Route path="/login" element={<AuthGuard publicOnly><AuthPage /></AuthGuard>} />
        <Route path="/register" element={<AuthGuard publicOnly><AuthPage registration /></AuthGuard>} />
        {routeDefinitions.filter((route) => route.access === 'protected').map((route) => (
          <Route key={route.path} path={route.path} element={<ProtectedPage route={route} />} />
        ))}
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
