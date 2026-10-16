import { amenityLabels } from '../manager/AmenityPicker.jsx';
import { EMPTY_FILTERS, activeFilterCount } from './stay.js';

const rupees = (v) => `₹${Number(v).toLocaleString('en-IN')}`;

/** Chips for the active filters; each × removes just that filter, "Clear all" removes every filter. */
export default function ActiveFilters({ filters, categories, onChange }) {
  if (activeFilterCount(filters) === 0) return null;
  const chips = [];
  if (filters.minPrice !== '' || filters.maxPrice !== '') {
    const label =
      filters.minPrice !== '' && filters.maxPrice !== ''
        ? `${rupees(filters.minPrice)} – ${rupees(filters.maxPrice)} / night`
        : filters.minPrice !== ''
          ? `From ${rupees(filters.minPrice)} / night`
          : `Up to ${rupees(filters.maxPrice)} / night`;
    chips.push({ key: 'price', label, remove: { minPrice: '', maxPrice: '' } });
  }
  if (filters.minRating !== '') {
    chips.push({ key: 'rating', label: `${filters.minRating}+ ★ rating`, remove: { minRating: '' } });
  }
  const labels = amenityLabels(categories, filters.amenities); // falls back to the code until the catalog loads
  filters.amenities.forEach((code, i) => {
    chips.push({ key: code, label: labels[i], remove: { amenities: filters.amenities.filter((a) => a !== code) } });
  });

  return (
    <ul className="f-chips" aria-label="Active filters">
      {chips.map((c) => (
        <li key={c.key}>
          <span>{c.label}</span>
          <button type="button" onClick={() => onChange({ ...filters, ...c.remove })} aria-label={`Remove filter: ${c.label}`}>
            ×
          </button>
        </li>
      ))}
      <li className="f-chips__clear">
        <button type="button" className="m-link-btn" onClick={() => onChange(EMPTY_FILTERS)}>
          Clear all
        </button>
      </li>
    </ul>
  );
}
