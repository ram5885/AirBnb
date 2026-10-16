import { Stars } from './Stars.jsx';

/** Average out of 5, review count and the 5-to-1 star distribution, from GET .../reviews/summary. */
export default function ReviewSummary({ summary }) {
  const { averageRating, totalReviews, ratingDistribution } = summary;
  return (
    <div className="rv-summary">
      <div className="rv-summary__score">
        <p className="rv-summary__average">
          {Number(averageRating).toFixed(1)}
          <span> / 5</span>
        </p>
        <Stars value={Number(averageRating)} size={20} label={`Rated ${averageRating} out of 5`} />
        <p className="s-muted">
          Based on {totalReviews} review{totalReviews === 1 ? '' : 's'}
        </p>
      </div>
      <ul className="rv-distribution" aria-label="Rating distribution">
        {[5, 4, 3, 2, 1].map((stars) => {
          const count = ratingDistribution[stars] ?? 0;
          const pct = totalReviews ? Math.round((count / totalReviews) * 100) : 0;
          return (
            <li key={stars}>
              <span className="rv-distribution__label">{stars} star{stars === 1 ? '' : 's'}</span>
              <span className="rv-distribution__bar" aria-hidden="true">
                <span style={{ width: `${pct}%` }} />
              </span>
              <span className="rv-distribution__count">
                {count} <span className="visually-hidden">reviews ({pct}%)</span>
              </span>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
