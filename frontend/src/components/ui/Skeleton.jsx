/*
 * STUDY NOTE: Pages render this visual placeholder while their own read state is loading.
 * CSS supplies its appearance; aria-hidden keeps decorative shapes out of announcements.
 * It takes no request data and does not decide when loading has finished; Spinner supplies status text.
 */
export default function Skeleton() {
  return <div className="skeleton" aria-hidden="true"><span /><span /></div>;
}
