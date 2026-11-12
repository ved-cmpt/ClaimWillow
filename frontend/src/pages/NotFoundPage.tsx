import { Link } from 'react-router-dom';
import { EmptyState } from '../components/ui';
import { homePathFor, useCurrentUser } from '../features/auth/useAuth';

export function NotFoundPage() {
  const me = useCurrentUser();
  return (
    <EmptyState title="Page not found" icon="search"
      action={<Link className="font-medium text-accent underline underline-offset-2"
        to={me.data ? homePathFor(me.data) : '/'}>Go to your home page</Link>}>
      The page you were looking for does not exist or you do not have access to it.
    </EmptyState>
  );
}
