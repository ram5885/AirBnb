import { Alert } from '../manager/ui.jsx';
import Carousel from './Carousel.jsx';
import PublicImage from './PublicImage.jsx';

/**
 * "Explore stays by city": cities with listed hotels (from GET /api/locations/destinations, loaded by the
 * page) with their photo. Picking one calls onPick({ city, state }). Renders nothing while loading or when
 * there are no cities; on error it offers a retry.
 */
export default function Destinations({ destinations, error, onRetry, onPick }) {
  if (error) {
    return (
      <div className="carousel">
        <Alert>
          Could not load destinations: {error}{' '}
          <button type="button" className="m-link-btn" onClick={onRetry}>
            Try again
          </button>
        </Alert>
      </div>
    );
  }
  if (!destinations?.length) return null;

  return (
    <Carousel id="destinations" title="Explore stays by city">
      {destinations.map((d) => (
        <li key={d.locationId}>
          <button type="button" className="d-card" onClick={() => onPick({ city: d.city, state: d.state })}>
            <PublicImage src={d.photoUrl} alt={d.city} title={d.photoCredit || undefined} className="d-card__image" />
            <span className="d-card__city">{d.city}</span>
            <span className="d-card__meta">{d.state}</span>
            <span className="d-card__meta">
              {d.hotelCount} {d.hotelCount === 1 ? 'hotel' : 'hotels'}
            </span>
          </button>
        </li>
      ))}
    </Carousel>
  );
}
