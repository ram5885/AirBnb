import { Stars } from './Stars.jsx';

const monthYear = (iso) =>
  new Date(`${iso.slice(0, 10)}T00:00:00`).toLocaleDateString('en-IN', { month: 'long', year: 'numeric' });

/** One review. The comment is rendered as text (React escapes it), keeping the reviewer's line breaks. */
export default function ReviewCard({ review }) {
  return (
    <article className="rv-card">
      <header className="rv-card__header">
        <span className="rv-card__avatar" aria-hidden="true">
          {review.reviewerName.charAt(0).toUpperCase()}
        </span>
        <div>
          <p className="rv-card__name">{review.reviewerName}</p>
          <p className="s-muted s-small">
            Stayed {monthYear(review.stayedOn)} · {review.roomType}
          </p>
        </div>
      </header>
      <div className="rv-card__meta">
        <Stars value={review.rating} size={14} />
        <span className="s-muted s-small">{monthYear(review.createdAt)}</span>
        {review.verifiedStay && (
          <span className="rv-card__verified" title="Written by a guest after a completed, paid stay booked here">
            Verified stay
          </span>
        )}
      </div>
      <p className="rv-card__comment">{review.comment}</p>
    </article>
  );
}
