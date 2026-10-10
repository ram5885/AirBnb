// Amenity pictures are local SVG files in src/assets/amenities (one per amenity code, lowercased),
// bundled by Vite. Nothing is fetched from external sites.
const ICONS = import.meta.glob('../../assets/amenities/*.svg', { eager: true, query: '?url', import: 'default' });

function iconUrl(code) {
  const key = `../../assets/amenities/${String(code).toLowerCase()}.svg`;
  return ICONS[key] || ICONS['../../assets/amenities/default.svg'];
}

/** Decorative icon for an amenity code; the text label next to it carries the meaning. */
export default function AmenityIcon({ code, size = 20 }) {
  return <img className="m-amenity-icon" src={iconUrl(code)} alt="" width={size} height={size} aria-hidden="true" />;
}

/** Icon + label list item, used wherever a hotel's or room's amenities are listed. */
export function AmenityList({ items, className = 'm-tags m-tags--icons' }) {
  return (
    <ul className={className}>
      {items.map((a) => (
        <li key={a.code}>
          <AmenityIcon code={a.code} />
          <span>{a.label}</span>
        </li>
      ))}
    </ul>
  );
}
