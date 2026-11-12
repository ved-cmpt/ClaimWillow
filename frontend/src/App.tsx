import { Route, Routes } from 'react-router-dom';
import { AppShell } from './components/AppShell';
import { ProtectedRoute } from './components/ProtectedRoute';
import { ClaimDetailPage } from './pages/ClaimDetailPage';
import { ClaimFormPage } from './pages/ClaimFormPage';
import { LandingPage } from './pages/LandingPage';
import { LoginPage } from './pages/LoginPage';
import { MyClaimsPage } from './pages/MyClaimsPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { PoliciesPage } from './pages/PoliciesPage';
import { StaffQueuePage } from './pages/StaffQueuePage';
import { ReportsPage } from './pages/ReportsPage';
import { AdminUsersPage } from './pages/AdminUsersPage';

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={<LandingPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppShell />}>
          <Route element={<ProtectedRoute roles={['POLICYHOLDER']} />}>
            <Route path="/claims" element={<MyClaimsPage />} />
            <Route path="/claims/new" element={<ClaimFormPage />} />
            <Route path="/claims/:id/edit" element={<ClaimFormPage />} />
            <Route path="/policies" element={<PoliciesPage />} />
          </Route>
          <Route path="/claims/:id" element={<ClaimDetailPage />} />
          <Route element={<ProtectedRoute roles={['ADJUSTER', 'ADMIN']} />}>
            <Route path="/staff/queue" element={<StaffQueuePage />} />
            <Route path="/reports" element={<ReportsPage />} />
          </Route>
          <Route element={<ProtectedRoute roles={['ADMIN']} />}>
            <Route path="/admin/users" element={<AdminUsersPage />} />
          </Route>
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  );
}
