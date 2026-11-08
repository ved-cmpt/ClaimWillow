import { Navigate } from 'react-router-dom';
import { homePathFor, useCurrentUser } from '../features/auth/useAuth';
import { Spinner } from '../components/Spinner';

export function HomeRedirect() {
  const me = useCurrentUser();
  if (me.isPending) return <Spinner />;
  return <Navigate to={me.data ? homePathFor(me.data) : '/login'} replace />;
}
