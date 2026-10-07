import Button from './Button.jsx';
/** @param {{page: number, totalPages: number, totalElements: number, last: boolean, onPage: (page: number) => void}} props */
export default function Pagination({ page, totalPages, totalElements, last, onPage }) {
  return <nav className="management-actions" aria-label="Employee pagination">
    <Button variant="secondary" disabled={page === 0} onClick={() => onPage(page - 1)}>Previous page</Button>
    <span>Page {totalPages === 0 ? 0 : page + 1} of {totalPages} · {totalElements} employees</span>
    <Button variant="secondary" disabled={last || totalPages === 0} onClick={() => onPage(page + 1)}>Next page</Button>
  </nav>;
}
