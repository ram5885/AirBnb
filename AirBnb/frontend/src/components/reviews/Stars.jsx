export const RATING_LABELS = { 1: 'Very poor', 2: 'Poor', 3: 'Average', 4: 'Good', 5: 'Excellent' };

const STAR = 'M12 2.5l2.9 6.1 6.6.8-4.9 4.6 1.3 6.6L12 17.3l-5.9 3.3 1.3-6.6-4.9-4.6 6.6-.8z';

/** Read-only stars; fractional values (an average) fill the last star partially. */
export function Stars({ value, size = 16, label }) {
  return (
    <span className="rv-stars" role="img" aria-label={label ?? `${value} out of 5 stars`}>
      {[1, 2, 3, 4, 5].map((n) => {
        const fill = Math.max(0, Math.min(1, value - (n - 1)));
        return (
          <span key={n} className="rv-star" style={{ width: size, height: size }}>
            <svg viewBox="0 0 24 24" width={size} height={size} aria-hidden="true">
              <path d={STAR} className="rv-star__empty" />
            </svg>
            <span className="rv-star__fill" style={{ width: `${fill * 100}%` }}>
              <svg viewBox="0 0 24 24" width={size} height={size} aria-hidden="true">
                <path d={STAR} />
              </svg>
            </span>
          </span>
        );
      })}
    </span>
  );
}

/** 1-5 star picker: a radio group, so it works with keyboard (arrow keys) and screen readers. */
export function StarInput({ value, onChange, disabled, name = 'rating' }) {
  return (
    <fieldset className="rv-star-input" disabled={disabled}>
      <legend className="visually-hidden">Rating</legend>
      {[1, 2, 3, 4, 5].map((n) => (
        <label key={n} className={n <= value ? 'is-on' : ''} title={`${n} – ${RATING_LABELS[n]}`}>
          <input
            type="radio"
            name={name}
            value={n}
            checked={value === n}
            onChange={() => onChange(n)}
            aria-label={`${n} star${n === 1 ? '' : 's'} – ${RATING_LABELS[n]}`}
          />
          <svg viewBox="0 0 24 24" width="32" height="32" aria-hidden="true">
            <path d={STAR} />
          </svg>
        </label>
      ))}
      <span className="rv-star-input__label" aria-live="polite">
        {value ? RATING_LABELS[value] : 'Select a rating'}
      </span>
    </fieldset>
  );
}
