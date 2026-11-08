import { Navigate, Outlet, useLocation } from 'react-router-dom';
import type { Role } from '../api/types';
import { homePathFor, useCurrentUser } from '../features/auth/useAuth';
import { Alert } from './ui';
import { Spinner } from './Spinner';

/** Client-side route guard for navigation only; the backend enforces every permission. */
export function ProtectedRoute({ roles }: { roles?: Role[] }) {
  const me = useCurrentUser();
  const location = useLocation();

  if (me.isPending) {
    return <div className="py-16"><Spinner label="Checking your session" /></div>;
  }
  if (me.isError) {
    return <Alert tone="error" title="We couldn't reach ClaimWillow.">Check your connection and refresh.</Alert>;
  }
  if (!me.data) {
    const next = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?next=${next}`} replace />;
  }
  if (roles && !roles.includes(me.data.role)) {
    return <Navigate to={homePathFor(me.data)} replace />;
  }
  return <Outlet />;
}
