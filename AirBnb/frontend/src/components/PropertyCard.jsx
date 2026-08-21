import { useState } from 'react';
import '../styles/property-card.css';

export default function PropertyCard({ listing }) {
  const [saved, setSaved] = useState(false);

  return (
    <article className="property-card">
      <div className="property-card__image-wrap">
        <img src={listing.image} alt={listing.location} loading="lazy" />
        <button
          type="button"
          className={`property-card__heart ${saved ? 'is-saved' : ''}`}
          aria-label={saved ? 'Remove from wishlist' : 'Add to wishlist'}
          onClick={() => setSaved((v) => !v)}
        >
          <svg viewBox="0 0 32 32" width="20" height="20">
            <path
              d="M16 28c7-4.7 13-9.9 13-16a7 7 0 00-13-3.5A7 7 0 003 12c0 6.1 6 11.3 13 16z"
              fill={saved ? 'var(--color-primary)' : 'rgba(0,0,0,0.5)'}
              stroke="#fff"
              strokeWidth="1.5"
            />
          </svg>
        </button>
      </div>

      <div className="property-card__body">
        <div className="property-card__row">
          <span className="property-card__location">{listing.location}</span>
          <span className="property-card__rating">
            <svg viewBox="0 0 32 32" width="12" height="12" fill="currentColor">
              <path d="M15.1 1.3a1 1 0 011.8 0l4 8.4 9.2 1.3a1 1 0 01.6 1.7l-6.7 6.5 1.6 9.2a1 1 0 01-1.5 1.1L16 25l-8.2 4.3a1 1 0 01-1.5-1l1.6-9.2-6.7-6.6a1 1 0 01.6-1.7l9.2-1.3z" />
            </svg>
            {listing.rating}
          </span>
        </div>
        <p className="property-card__muted">{listing.distance}</p>
        <p className="property-card__muted">{listing.dates}</p>
        <p className="property-card__price">
          <strong>₹{listing.price.toLocaleString('en-IN')}</strong> night
        </p>
      </div>
    </article>
  );
}
