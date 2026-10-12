import Button from './Button.jsx';

/** STUDY NOTE: Totals come from the same authorized server query as the displayed items.
 * @param {{page:number,totalPages:number,totalElements:number,onPage:(page:number)=>void,disabled?:boolean,label?:string}} props
 */
export default function Pagination({ page, totalPages, totalElements, onPage, disabled = false, label = 'List pages' }) {
  return <nav aria-label={label} className="page-actions">
    <Button type="button" disabled={disabled || page === 0} onClick={() => onPage(page - 1)}>Previous page</Button>
    <span role="status">Page {page + 1} of {Math.max(1, totalPages)} · {totalElements} records</span>
    <Button type="button" disabled={disabled || page + 1 >= totalPages} onClick={() => onPage(page + 1)}>Next page</Button>
  </nav>;
}
