import { Link } from 'react-router-dom';
import { formatPrice } from '../manager/ui.jsx';
import { amenityItems } from '../manager/AmenityPicker.jsx';
import AmenityIcon from '../manager/AmenityIcon.jsx';
import PublicImage from './PublicImage.jsx';
import { stayQuery } from './stay.js';

/** One search result. `stay` is carried into the hotel link so dates/rooms are preserved. */
export default function HotelCard({ hotel, stay, nights, categories }) {
  const amenities = amenityItems(categories, hotel.amenities).slice(0, 4);
  return (
    <article className="s-card">
      <Link to={`/hotels/${hotel.id}${stayQuery(stay)}`} className="s-card__image" tabIndex={-1} aria-hidden="true">
        <PublicImage src={hotel.photoUrls[0]} alt="" />
      </Link>
      <div className="s-card__body">
        <div className="s-card__heading">
          <h2 className="s-card__title">
            <Link to={`/hotels/${hotel.id}${stayQuery(stay)}`}>{hotel.name}</Link>
          </h2>
          {hotel.reviewCount > 0 && (
            <span className="s-card__rating" aria-label={`Rated ${hotel.averageRating} out of 5 from ${hotel.reviewCount} reviews`}>
              ★ {Number(hotel.averageRating).toFixed(1)} <span className="s-muted">({hotel.reviewCount})</span>
            </span>
          )}
        </div>
        <p className="s-muted">
          {hotel.city}, {hotel.state}
        </p>
        {amenities.length > 0 && (
          <ul className="s-card__amenities" aria-label="Top amenities">
            {amenities.map((a) => (
              <li key={a.code}>
                <AmenityIcon code={a.code} size={16} />
                <span>{a.label}</span>
              </li>
            ))}
          </ul>
        )}
        <p className="s-card__availability">
          {hotel.availableRoomTypes} room type{hotel.availableRoomTypes === 1 ? '' : 's'}{' '}
          {stay.amenities?.length || stay.minPrice || stay.maxPrice ? 'match your filters' : 'available'}
        </p>
        {hotel.lowestNightlyPrice != null && (
          <p className="s-card__price">
            <strong>{formatPrice(hotel.lowestNightlyPrice)}</strong> <span className="s-muted">/ night per room</span>
            <br />
            <span className="s-muted">
              {formatPrice(hotel.lowestTotalPrice)} total for {nights} night{nights === 1 ? '' : 's'}
            </span>
          </p>
        )}
      </div>
    </article>
  );
}
