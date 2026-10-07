import Card from '../components/ui/Card.jsx';

/** @param {{route: import('../routes/routeDefinitions.js').RouteDefinition}} props */
export default function ScaffoldPage({ route }) {
  const protectedRoute = route.access === 'protected';

  return (
    <section className="page-intro" aria-labelledby="page-heading">
      <p className="eyebrow">Rockey · Workspace</p>
      <h1 id="page-heading">{route.title}</h1>
      <p className="page-description">
        {protectedRoute
          ? 'Your session is verified. This business workspace will be connected in a later slice.'
          : 'A shared foundation for the hotel operations workspace.'}
      </p>
      <Card title={protectedRoute ? 'Protected workspace scaffold' : 'Workspace scaffold'}>
        <p>
          {protectedRoute
            ? 'No hotel operational data is loaded or displayed. The backend remains authoritative for role, ownership and department access.'
            : 'This business workspace will be connected in a later slice. No hotel operational data is loaded or displayed.'}
        </p>
        <p className="muted">This is a route scaffold, not a working feature.</p>
      </Card>
    </section>
  );
}
