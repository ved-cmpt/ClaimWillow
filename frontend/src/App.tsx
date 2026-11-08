import { Route, Routes } from 'react-router-dom';
import { AppShell } from './components/AppShell';
import { ProtectedRoute } from './components/ProtectedRoute';
import { HomeRedirect } from './pages/HomeRedirect';
import { LoginPage } from './pages/LoginPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { PlaceholderPage } from './pages/PlaceholderPage';
import { PoliciesPage } from './pages/PoliciesPage';

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={<HomeRedirect />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppShell />}>
          <Route element={<ProtectedRoute roles={['POLICYHOLDER']} />}>
            <Route path="/claims" element={<PlaceholderPage title="My claims" />} />
            <Route path="/policies" element={<PoliciesPage />} />
          </Route>
          <Route element={<ProtectedRoute roles={['ADJUSTER', 'ADMIN']} />}>
            <Route path="/staff/queue" element={<PlaceholderPage title="Work queue" />} />
          </Route>
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  );
}
