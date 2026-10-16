import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api.js';
import { isHotelManager, useAuth } from '../../context/AuthContext.jsx';
import { Alert, EmptyState, Spinner } from '../manager/ui.jsx';
import ReviewCard from './ReviewCard.jsx';
import ReviewForm, { MyStays } from './ReviewForm.jsx';
import ReviewSummary from './ReviewSummary.jsx';
import '../../styles/reviews.css';

const PAGE_SIZE = 10;
const SORTS = [
  ['recent', 'Most recent'],
  ['highest', 'Highest rated'],
  ['lowest', 'Lowest rated'],
];

/**
 * The hotel page's Reviews tab: summary, the guest's review form (only for stays the backend says are
 * reviewable), and the review list sorted and paged by the backend ("Show more" loads the next page).
 * `summary` is loaded by the page (it also shows the rating in the header); onChanged reloads it.
 */
export default function HotelReviews({ hotelId, summary, summaryError, onChanged }) {
  const { user } = useAuth();
  const isGuest = !!user && !isHotelManager(user);

  const [sort, setSort] = useState('recent');
  const [list, setList] = useState({ items: [], page: -1, totalPages: 0, loading: true, error: '' });
  const [eligibility, setEligibility] = useState({ stays: null, error: '' });
  const [saved, setSaved] = useState(false);

  const loadPage = useCallback(
    (page, append) => {
      setList((l) => ({ ...l, loading: true, error: '' }));
      api
        .getReviews(hotelId, { sort, page, size: PAGE_SIZE })
        .then((r) =>
          setList((l) => ({
            items: append ? [...l.items, ...r.content] : r.content,
            page: r.page,
            totalPages: r.totalPages,
            loading: false,
            error: '',
          })),
        )
        .catch((e) => setList((l) => ({ ...l, loading: false, error: e.message })));
    },
    [hotelId, sort],
  );

  const loadEligibility = useCallback(() => {
    if (!isGuest) return;
    api
      .getReviewEligibility(hotelId)
      .then((r) => setEligibility({ stays: r.bookings, error: '' }))
      .catch((e) => setEligibility({ stays: null, error: e.message }));
  }, [hotelId, isGuest]);

  useEffect(() => loadPage(0, false), [loadPage]);
  useEffect(loadEligibility, [loadEligibility]);

  function retry() {
    onChanged();
    loadPage(0, false);
    loadEligibility();
  }

  function handleSaved() {
    setSaved(true);
    onChanged();
    loadEligibility();
    if (sort === 'recent') loadPage(0, false);
    else setSort('recent'); // show the new review at the top
  }

  const hasReviews = summary && summary.totalReviews > 0;
  const stays = eligibility.stays || [];
  const canReview = stays.some((s) => s.status === 'REVIEWABLE');

  return (
    <div className="rv">
      {(summaryError || list.error) && (
        <Alert>
          Reviews couldn't be loaded right now.{' '}
          <button type="button" className="m-link-btn" onClick={retry}>
            Try again
          </button>
        </Alert>
      )}
      {hasReviews && <ReviewSummary summary={summary} />}

      {saved && <Alert type="success" onDismiss={() => setSaved(false)}>Thanks! Your review has been published.</Alert>}

      {!user && (
        <p className="rv-note">
          <Link to="/login">Log in</Link> to review a stay you've completed here.
        </p>
      )}
      {isGuest && eligibility.error && !summaryError && (
        <Alert>
          Couldn't check whether you can review a stay here.{' '}
          <button type="button" className="m-link-btn" onClick={loadEligibility}>
            Try again
          </button>
        </Alert>
      )}
      {isGuest && eligibility.stays && (
        <>
          <MyStays stays={stays} />
          {canReview ? (
            <ReviewForm key={stays.map((s) => s.bookingId + s.status).join()} hotelId={hotelId} stays={stays} onSaved={handleSaved} />
          ) : (
            stays.length === 0 && (
              <p className="rv-note">You can review this hotel after completing a confirmed, paid stay booked here.</p>
            )
          )}
        </>
      )}

      {summary && !hasReviews && (
        <EmptyState title="No ratings yet">No reviews yet. Be the first to share your experience after your stay.</EmptyState>
      )}

      {hasReviews && (
        <section aria-labelledby="reviews-list-title">
          <div className="rv-list__header">
            <h3 id="reviews-list-title">
              {summary.totalReviews} review{summary.totalReviews === 1 ? '' : 's'}
            </h3>
            <label className="rv-sort">
              <span className="visually-hidden">Sort reviews</span>
              <select value={sort} onChange={(e) => setSort(e.target.value)}>
                {SORTS.map(([v, l]) => (
                  <option key={v} value={v}>
                    {l}
                  </option>
                ))}
              </select>
            </label>
          </div>
          <div className="rv-list">
            {list.items.map((r) => (
              <ReviewCard key={r.id} review={r} />
            ))}
          </div>
          {list.loading && <Spinner label="Loading reviews…" />}
          {!list.loading && list.page + 1 < list.totalPages && (
            <button type="button" className="m-btn m-btn--secondary" onClick={() => loadPage(list.page + 1, true)}>
              Show more reviews
            </button>
          )}
        </section>
      )}
    </div>
  );
}
