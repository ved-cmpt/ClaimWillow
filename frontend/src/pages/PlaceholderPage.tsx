import { PageHeader } from '../components/ui';

/** Temporary landing page until the corresponding feature lands. */
export function PlaceholderPage({ title }: { title: string }) {
  return <PageHeader title={title} subtitle="This area is being built." />;
}
