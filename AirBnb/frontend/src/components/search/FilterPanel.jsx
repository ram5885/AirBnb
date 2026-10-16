import { useEffect, useRef, useState } from 'react';
import AmenityIcon from '../manager/AmenityIcon.jsx';
import { EMPTY_FILTERS, RATING_OPTIONS, activeFilterCount, validateFilters } from './stay.js';

/**
 * Filters dialog for search results: price range, minimum guest rating and amenities (from the amenity
 * catalog). Edits a draft copy; only "Show stays" applies it (after validation). Closing (×, Escape or the
 * backdrop) discards the draft, and "Clear all" resets the draft to no filters.
 */
export default function FilterPanel({ open, filters, categories, onApply, onClose }) {
  const [draft, setDraft] = useState(filters);
  const [errors, setErrors] = useState({});
  const dialogRef = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    setDraft(filters);
    setErrors({});
    const previous = document.activeElement;
    dialogRef.current?.focus();
    const onKey = (e) => e.key === 'Escape' && onClose();
    document.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = '';
      previous?.focus?.();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  if (!open) return null;

  function set(key, value) {
    setDraft((d) => ({ ...d, [key]: value }));
    setErrors((e) => ({ ...e, [key]: undefined, maxPrice: key === 'minPrice' ? undefined : e.maxPrice }));
  }

  function toggleAmenity(code) {
    setDraft((d) => ({
      ...d,
      amenities: d.amenities.includes(code) ? d.amenities.filter((a) => a !== code) : [...d.amenities, code],
    }));
  }

  function apply(e) {
    e.preventDefault();
    const errs = validateFilters(draft);
    setErrors(errs);
    if (Object.keys(errs).length) return;
    // Normalise numbers ("01000" -> "1000") so the URL and chips stay tidy.
    onApply({
      ...draft,
      minPrice: draft.minPrice === '' ? '' : String(Number(draft.minPrice)),
      maxPrice: draft.maxPrice === '' ? '' : String(Number(draft.maxPrice)),
    });
  }

  const count = activeFilterCount(draft);
  return (
    <div className="f-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <form
        className="f-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="filters-title"
        tabIndex={-1}
        ref={dialogRef}
        onSubmit={apply}
        noValidate
      >
        <header className="f-dialog__header">
          <button type="button" className="f-dialog__close" onClick={onClose} aria-label="Close filters">
            <svg viewBox="0 0 32 32" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="4" aria-hidden="true">
              <path d="M6 6l20 20M26 6L6 26" />
            </svg>
          </button>
          <h2 id="filters-title">Filters</h2>
        </header>

        <div className="f-dialog__body">
          <section className="f-section">
            <h3>Price range</h3>
            <p className="s-muted s-small">Average price per room per night for your dates, before any extras.</p>
            <div className="f-price">
              <label className={errors.minPrice ? 'f-price__field f-price__field--error' : 'f-price__field'}>
                <span>Minimum</span>
                <span className="f-price__input">
                  ₹
                  <input
                    type="number"
                    inputMode="numeric"
                    min="0"
                    step="100"
                    placeholder="No min"
                    value={draft.minPrice}
                    onChange={(e) => set('minPrice', e.target.value)}
                    aria-invalid={!!errors.minPrice}
                  />
                </span>
                {errors.minPrice && <span className="s-field__error">{errors.minPrice}</span>}
              </label>
              <span className="f-price__dash" aria-hidden="true">–</span>
              <label className={errors.maxPrice ? 'f-price__field f-price__field--error' : 'f-price__field'}>
                <span>Maximum</span>
                <span className="f-price__input">
                  ₹
                  <input
                    type="number"
                    inputMode="numeric"
                    min="0"
                    step="100"
                    placeholder="No max"
                    value={draft.maxPrice}
                    onChange={(e) => set('maxPrice', e.target.value)}
                    aria-invalid={!!errors.maxPrice}
                  />
                </span>
                {errors.maxPrice && <span className="s-field__error">{errors.maxPrice}</span>}
              </label>
            </div>
          </section>

          <section className="f-section">
            <h3 id="rating-title">Guest rating</h3>
            <div className="f-pills" role="radiogroup" aria-labelledby="rating-title">
              {[['', 'Any'], ...RATING_OPTIONS.map((r) => [r, `${r}+ ★`])].map(([value, label]) => (
                <label key={value || 'any'} className={draft.minRating === value ? 'f-pill is-on' : 'f-pill'}>
                  <input
                    type="radio"
                    name="minRating"
                    value={value}
                    checked={draft.minRating === value}
                    onChange={() => set('minRating', value)}
                  />
                  {label}
                </label>
              ))}
            </div>
            <p className="s-muted s-small">Based on reviews from completed stays. Places without reviews are hidden while a rating is selected.</p>
          </section>

          <section className="f-section">
            <h3>Amenities</h3>
            <p className="s-muted s-small">
              Shows places where a room you can book has <strong>all</strong> the selected amenities, in the room or at the property.
            </p>
            {!categories && <p className="s-muted">Loading amenities…</p>}
            {categories?.map((c) => (
              <fieldset key={c.code} className="f-amenities">
                <legend>{c.label}</legend>
                <div className="f-amenities__grid">
                  {c.amenities.map((a) => (
                    <label key={a.code} className="f-check">
                      <input type="checkbox" checked={draft.amenities.includes(a.code)} onChange={() => toggleAmenity(a.code)} />
                      <AmenityIcon code={a.code} size={18} />
                      <span>{a.label}</span>
                    </label>
                  ))}
                </div>
              </fieldset>
            ))}
          </section>
        </div>

        <footer className="f-dialog__footer">
          <button type="button" className="m-link-btn" onClick={() => { setDraft(EMPTY_FILTERS); setErrors({}); }} disabled={count === 0}>
            Clear all
          </button>
          <button type="submit" className="m-btn m-btn--primary">
            Show stays{count ? ` (${count} filter${count === 1 ? '' : 's'})` : ''}
          </button>
        </footer>
      </form>
    </div>
  );
}
