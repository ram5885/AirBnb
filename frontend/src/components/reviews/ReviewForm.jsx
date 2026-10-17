import { useState } from 'react';
import { createReview } from '../../api.js';
import { Alert, formatDate } from '../manager/ui.jsx';
import { RATING_LABELS, StarInput, Stars } from './Stars.jsx';

export const COMMENT_MIN = 10;
export const COMMENT_MAX = 1000;

/**
 * Write a review for one of the guest's completed stays (from GET .../reviews/eligibility). Mirrors the
 * backend rules for quick feedback; the backend re-checks everything. Calls onSaved() after a successful
 * save so the page reloads the summary, list and eligibility from the server.
 */
export default function ReviewForm({ hotelId, stays, onSaved }) {
  const reviewable = stays.filter((s) => s.status === 'REVIEWABLE');
  const [bookingId, setBookingId] = useState(reviewable[0]?.bookingId ?? null);
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState('');
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  async function submit(e) {
    e.preventDefault();
    const errs = {};
    const text = comment.trim();
    if (!rating) errs.rating = 'Choose a rating from 1 to 5 stars';
    if (!text) errs.comment = 'Write a few words about your stay';
    else if (text.length < COMMENT_MIN) errs.comment = `Write at least ${COMMENT_MIN} characters about your stay`;
    else if (text.length > COMMENT_MAX) errs.comment = `Keep your review under ${COMMENT_MAX} characters`;
    setErrors(errs);
    setError('');
    if (Object.keys(errs).length) return;
    setSaving(true);
    try {
      await createReview(hotelId, { bookingId, rating, comment: text });
      onSaved();
    } catch (err) {
      setErrors(err.fieldErrors || {});
      setError(err.message);
      setSaving(false);
    }
  }

  return (
    <form className="rv-form" onSubmit={submit} noValidate aria-labelledby="review-form-title">
      <h3 id="review-form-title">Rate your stay</h3>
      {reviewable.length > 1 && (
        <label className="rv-form__field">
          <span>Which stay?</span>
          <select value={bookingId} onChange={(e) => setBookingId(Number(e.target.value))} disabled={saving}>
            {reviewable.map((s) => (
              <option key={s.bookingId} value={s.bookingId}>
                {s.roomType} · {formatDate(s.checkInDate)} – {formatDate(s.checkOutDate)}
              </option>
            ))}
          </select>
        </label>
      )}
      {reviewable.length === 1 && (
        <p className="s-muted s-small">
          Your stay: {reviewable[0].roomType} · {formatDate(reviewable[0].checkInDate)} – {formatDate(reviewable[0].checkOutDate)}
        </p>
      )}
      <div className="rv-form__field">
        <StarInput value={rating} onChange={(n) => { setRating(n); setErrors((x) => ({ ...x, rating: undefined })); }} disabled={saving} />
        {errors.rating && <span className="s-field__error">{errors.rating}</span>}
      </div>
      <label className="rv-form__field">
        <span>Your review</span>
        <textarea
          rows={5}
          maxLength={COMMENT_MAX}
          value={comment}
          onChange={(e) => {
            setComment(e.target.value);
            setErrors((x) => ({ ...x, comment: undefined }));
          }}
          placeholder="What did you like? What could be better?"
          disabled={saving}
          aria-invalid={!!errors.comment}
          aria-describedby="review-comment-help"
        />
        <span id="review-comment-help" className="s-muted s-small">
          {comment.trim().length}/{COMMENT_MAX} characters · at least {COMMENT_MIN}
        </span>
        {errors.comment && <span className="s-field__error">{errors.comment}</span>}
      </label>
      <Alert onDismiss={() => setError('')}>{error}</Alert>
      <button type="submit" className="m-btn m-btn--primary" disabled={saving || !bookingId}>
        {saving ? 'Submitting…' : 'Submit review'}
      </button>
    </form>
  );
}

/** The guest's own reviews and why other stays can't be reviewed yet. */
export function MyStays({ stays }) {
  const reviewed = stays.filter((s) => s.status === 'REVIEWED');
  const waiting = stays.filter((s) => s.status === 'UPCOMING' || s.status === 'NOT_ELIGIBLE');
  if (!reviewed.length && !waiting.length) return null;
  return (
    <div className="rv-mine">
      {reviewed.map((s) => (
        <div key={s.bookingId} className="rv-mine__item">
          <p className="rv-card__name">Your review · {s.roomType}, {formatDate(s.checkInDate)}</p>
          <Stars value={s.review.rating} size={14} label={`You rated ${s.review.rating} – ${RATING_LABELS[s.review.rating]}`} />
          <p className="rv-card__comment">{s.review.comment}</p>
        </div>
      ))}
      {waiting.map((s) => (
        <p key={s.bookingId} className="s-muted s-small">
          {s.roomType}, {formatDate(s.checkInDate)} – {formatDate(s.checkOutDate)}: {s.reason}
        </p>
      ))}
    </div>
  );
}
