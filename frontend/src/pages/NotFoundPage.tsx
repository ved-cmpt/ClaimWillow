import { Link } from 'react-router-dom';
import { EmptyState } from '../components/ui';

export function NotFoundPage() {
  return (
    <EmptyState title="Page not found"
      action={<Link className="text-accent underline" to="/">Go to your home page</Link>}>
      The page you were looking for does not exist or you do not have access to it.
    </EmptyState>
  );
}
