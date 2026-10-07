import { Link } from 'react-router';

export default function NotFoundPage() {
  return (
    <section className="page-intro" aria-labelledby="page-heading">
      <p className="eyebrow">404 · Page not found</p>
      <h1 id="page-heading">This page isn’t here.</h1>
      <p className="page-description">Check the address or return to the sign-in page.</p>
      <Link className="button button-primary" to="/login">Back to sign in</Link>
    </section>
  );
}
