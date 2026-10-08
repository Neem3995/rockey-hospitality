/*
 * STUDY NOTE: A page supplies rows, column render callbacks, row keys, caption and empty text.
 * First we render headings, then call each column's render function for each row, or show an empty cell.
 * The scroll region supports keyboard access. The page owns filters, requests and row actions;
 * this table does not fetch, paginate or authorize data.
 */
/**
 * @template {object} T
 * @typedef {{key: string, label: string, render: (row: T) => import('react').ReactNode}} TableColumn
 */

/**
 * @template {object} T
 * @param {{caption: string, columns: TableColumn<T>[], rows: T[],
 *   getRowKey: (row: T) => string | number, emptyMessage?: string}} props
 */
export default function Table({ caption, columns, rows, getRowKey, emptyMessage = 'No results found.' }) {
  return (
    <div className="table-scroll" role="region" aria-label={`${caption} table`} tabIndex={0}>
      <table>
        <caption>{caption}</caption>
        <thead>
          <tr>{columns.map((column) => <th key={column.key} scope="col">{column.label}</th>)}</tr>
        </thead>
        <tbody>
          {rows.length === 0
            ? <tr><td colSpan={columns.length} className="empty-cell">{emptyMessage}</td></tr>
            : rows.map((row) => (
              <tr key={getRowKey(row)}>
                {columns.map((column) => <td key={column.key}>{column.render(row)}</td>)}
              </tr>
            ))}
        </tbody>
      </table>
    </div>
  );
}
