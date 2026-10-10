import { useEffect, useState } from 'react';
import { getAmenities } from '../../api.js';
import { Alert, Spinner } from './ui.jsx';
import AmenityIcon from './AmenityIcon.jsx';

let catalogCache = null;

/** Hook returning the amenity catalog from the backend (cached for the session). */
export function useAmenityCatalog() {
  const [state, setState] = useState({ categories: catalogCache, error: '' });
  useEffect(() => {
    if (catalogCache) return;
    let cancelled = false;
    getAmenities()
      .then((c) => {
        catalogCache = c;
        if (!cancelled) setState({ categories: c, error: '' });
      })
      .catch((e) => !cancelled && setState({ categories: null, error: e.message }));
    return () => {
      cancelled = true;
    };
  }, []);
  return state;
}

export function amenityLabels(categories, codes) {
  return amenityItems(categories, codes).map((a) => a.label);
}

/** [{ code, label }] for display with icons. */
export function amenityItems(categories, codes) {
  const labels = {};
  (categories || []).forEach((c) => c.amenities.forEach((a) => (labels[a.code] = a.label)));
  return codes.map((code) => ({ code, label: labels[code] || code }));
}

/** Categorised checkbox grid; `value` is an array of amenity codes. */
export default function AmenityPicker({ value, onChange, disabled }) {
  const { categories, error } = useAmenityCatalog();

  if (error) return <Alert>Could not load amenities: {error}</Alert>;
  if (!categories) return <Spinner label="Loading amenities…" />;

  const selected = new Set(value);
  function toggle(code) {
    const next = new Set(selected);
    if (next.has(code)) next.delete(code);
    else next.add(code);
    onChange([...next]);
  }

  return (
    <div className="m-amenities">
      {categories.map((cat) => (
        <fieldset key={cat.code} className="m-amenities__group">
          <legend>{cat.label}</legend>
          {cat.amenities.map((a) => (
            <label key={a.code} className={`m-chip ${selected.has(a.code) ? 'is-checked' : ''}`}>
              <input
                type="checkbox"
                checked={selected.has(a.code)}
                onChange={() => toggle(a.code)}
                disabled={disabled}
              />
              <AmenityIcon code={a.code} size={18} />
              <span>{a.label}</span>
            </label>
          ))}
        </fieldset>
      ))}
      <p className="m-field__hint">{value.length} selected</p>
    </div>
  );
}
