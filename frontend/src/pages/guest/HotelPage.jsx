import { useCallback, useEffect, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import * as api from '../../api.js';
import Navbar from '../../components/Navbar.jsx';
import PublicImage from '../../components/search/PublicImage.jsx';
import { amenityItems, amenityLabels, useAmenityCatalog } from '../../components/manager/AmenityPicker.jsx';
import { AmenityList } from '../../components/manager/AmenityIcon.jsx';
import { Alert, EmptyState, Spinner, formatDate, formatPrice } from '../../components/manager/ui.jsx';
import { hasDates, pickFilters, readStay, roomMatchesFilters, stayParams, stayQuery } from '../../components/search/stay.js';
import StayBar from './StayBar.jsx';
import HotelReviews from '../../components/reviews/HotelReviews.jsx';
import { Stars } from '../../components/reviews/Stars.jsx';
import '../../styles/manager.css';
import '../../styles/home.css';
import '../../styles/search.css';

/** Public hotel details with room types and their availability for the stay in the URL. */
export default function HotelPage() {
  const { hotelId } = useParams();
  const [params, setParams] = useSearchParams();
  const stay = readStay(params);
  const { categories } = useAmenityCatalog();

  const [hotel, setHotel] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const key = params.toString();
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError('');
    api
      .getPublicHotel(hotelId, stayParams(stay))
      .then((h) => !cancelled && setHotel(h))
      .catch((e) => !cancelled && setError(e.message))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hotelId, key]);

  // Rating summary: shown in the header and the Reviews tab; reloaded after a review is posted.
  const [summary, setSummary] = useState(null);
  const [summaryError, setSummaryError] = useState('');
  const loadSummary = useCallback(() => {
    api
      .getReviewSummary(hotelId)
      .then((s) => {
        setSummary(s);
        setSummaryError('');
      })
      .catch((e) => setSummaryError(e.message));
  }, [hotelId]);
  useEffect(loadSummary, [loadSummary]);

  const tab = params.get('tab') === 'reviews' ? 'reviews' : 'about';
  function showTab(next) {
    const p = new URLSearchParams(params);
    if (next === 'reviews') p.set('tab', 'reviews');
    else p.delete('tab');
    setParams(p, { replace: true });
  }

  function applyStay(v) {
    const next = new URLSearchParams(params);
    next.set('checkinDate', v.checkinDate);
    next.set('checkoutDate', v.checkoutDate);
    next.set('numberOfRooms', v.numberOfRooms);
    setParams(next);
  }

  const backToResults = stay.city && hasDates(stay) ? `/${stayQuery(stay)}` : '/';
  // Price/amenity filters from the search: mark the bookable room options that satisfy them.
  const filters = pickFilters(stay);
  const roomFiltersActive = filters.amenities.length > 0 || filters.minPrice !== '' || filters.maxPrice !== '';
  const matchesFilters = (r) =>
    roomFiltersActive &&
    r.availability?.available &&
    roomMatchesFilters(filters, r.availability.averageNightlyPrice, r.amenities, hotel?.amenities);

  return (
    <div className="home">
      <Navbar />
      <main className="container home__main">
        <nav className="m-breadcrumb" aria-label="Breadcrumb">
          <Link to={backToResults}>{stay.city && hasDates(stay) ? 'Search results' : 'Search'}</Link>
          {hotel && <> / <span>{hotel.name}</span></>}
        </nav>

        {loading && !hotel && <Spinner label="Loading hotel…" />}
        {error && !hotel && (
          <EmptyState title="Hotel not available" action={<Link to={backToResults} className="m-btn m-btn--secondary">Back to search</Link>}>
            {error}
          </EmptyState>
        )}

        {hotel && (
          <>
            <div className="m-page-header">
              <div>
                <h1>{hotel.name}</h1>
                <p className="s-muted">
                  {hotel.city}, {hotel.state}
                </p>
                {summary && summary.totalReviews > 0 && (
                  <button type="button" className="h-rating" onClick={() => showTab('reviews')}>
                    <Stars value={Number(summary.averageRating)} size={14} label={`Rated ${summary.averageRating} out of 5`} />
                    <strong>{Number(summary.averageRating).toFixed(1)}</strong>
                    <span>
                      {summary.totalReviews} review{summary.totalReviews === 1 ? '' : 's'}
                    </span>
                  </button>
                )}
                {summary && summary.totalReviews === 0 && (
                  <button type="button" className="h-rating h-rating--none" onClick={() => showTab('reviews')}>
                    <Stars value={0} size={14} label="No ratings yet" />
                    <span>No ratings yet</span>
                  </button>
                )}
              </div>
            </div>

            <div className={`s-gallery s-gallery--${Math.min(hotel.photoUrls.length, 5) || 1}`}>
              {hotel.photoUrls.length === 0 && <PublicImage src={null} alt={`${hotel.name} (no photos yet)`} />}
              {hotel.photoUrls.slice(0, 5).map((url, i) => (
                <PublicImage key={url} src={url} alt={`${hotel.name} photo ${i + 1}`} />
              ))}
            </div>

            <div className="s-detail">
              <div>
                <div className="h-tabs" role="tablist" aria-label="Hotel information">
                  <button
                    type="button"
                    role="tab"
                    id="tab-about"
                    aria-selected={tab === 'about'}
                    aria-controls="panel-about"
                    onClick={() => showTab('about')}
                  >
                    About
                  </button>
                  <button
                    type="button"
                    role="tab"
                    id="tab-reviews"
                    aria-selected={tab === 'reviews'}
                    aria-controls="panel-reviews"
                    onClick={() => showTab('reviews')}
                  >
                    Reviews{summary?.totalReviews ? ` (${summary.totalReviews})` : ''}
                  </button>
                </div>

                {tab === 'about' ? (
                  <div role="tabpanel" id="panel-about" aria-labelledby="tab-about">
                    <section className="m-section">
                      <h2>Location</h2>
                      <p>{hotel.address || `${hotel.city}, ${hotel.state}`}</p>
                      {hotel.landmark && <p className="s-muted">{hotel.landmark}</p>}
                    </section>
                    <section className="m-section">
                      <h2>What this place offers</h2>
                      {hotel.amenities.length ? (
                        <AmenityList items={amenityItems(categories, hotel.amenities)} />
                      ) : (
                        <p className="s-muted">No amenities listed.</p>
                      )}
                    </section>
                  </div>
                ) : (
                  <div role="tabpanel" id="panel-reviews" aria-labelledby="tab-reviews" className="m-section">
                    <HotelReviews hotelId={hotel.id} summary={summary} summaryError={summaryError} onChanged={loadSummary} />
                  </div>
                )}
              </div>

              <section className="m-section s-rooms" aria-labelledby="rooms-heading">
                <h2 id="rooms-heading">Choose a room</h2>
                <StayBar stay={stay} onApply={applyStay} busy={loading} />
                {hotel.stay && (
                  <p className="s-muted s-small">
                    {formatDate(hotel.stay.checkinDate)} – {formatDate(hotel.stay.checkoutDate)} · {hotel.stay.nights} night
                    {hotel.stay.nights === 1 ? '' : 's'} · {hotel.stay.numberOfRooms} room{hotel.stay.numberOfRooms === 1 ? '' : 's'}
                  </p>
                )}
                <Alert>{error}</Alert>
                {roomFiltersActive && hotel.stay && (
                  <p className="s-small f-room-note">
                    Room options marked <span className="f-match">Matches your filters</span> fit your price range and amenities.
                  </p>
                )}
                {hotel.roomTypes.length === 0 && <p className="s-muted">This hotel has no rooms listed yet.</p>}
                <ul className="s-room-list">
                  {hotel.roomTypes.map((r) => (
                    <li key={r.id} className="s-room">
                      <PublicImage src={r.photoUrls[0]} alt="" className="s-room__thumb" />
                      <div className="s-room__body">
                        <h3>
                          <Link to={`/hotels/${hotel.id}/rooms/${r.id}${stayQuery(stay)}`}>{r.type}</Link>
                          {matchesFilters(r) && <span className="f-match">Matches your filters</span>}
                        </h3>
                        <p className="s-muted s-small">
                          Up to {r.capacity} guest{r.capacity === 1 ? '' : 's'} per room
                          {r.amenities.length > 0 && ` · ${amenityLabels(categories, r.amenities).slice(0, 3).join(', ')}`}
                        </p>
                        <RoomAvailability room={r} stay={hotel.stay} />
                      </div>
                      {r.availability?.available ? (
                        <Link
                          to={`/book/${hotel.id}/${r.id}${stayQuery(stay)}`}
                          className="m-btn m-btn--primary m-btn--sm"
                          aria-label={`Reserve ${r.type}`}
                        >
                          Reserve
                        </Link>
                      ) : (
                        <Link
                          to={`/hotels/${hotel.id}/rooms/${r.id}${stayQuery(stay)}`}
                          className="m-btn m-btn--secondary m-btn--sm"
                          aria-label={`View ${r.type}`}
                        >
                          View
                        </Link>
                      )}
                    </li>
                  ))}
                </ul>
              </section>
            </div>
          </>
        )}
      </main>
    </div>
  );
}

function RoomAvailability({ room, stay }) {
  if (!room.availability) {
    return <p className="s-small">From {formatPrice(room.basePrice)} / night · choose dates to check availability</p>;
  }
  const a = room.availability;
  if (!a.available) {
    return (
      <p className="s-small s-unavailable">
        {a.totalPrice == null
          ? 'Not available for these dates'
          : `Only ${a.availableRooms} room${a.availableRooms === 1 ? '' : 's'} left for these dates`}
      </p>
    );
  }
  return (
    <p className="s-small">
      <span className="s-available">Available</span> · {a.availableRooms} left ·{' '}
      <strong>{formatPrice(a.averageNightlyPrice)}</strong> / night · {formatPrice(a.totalPrice)} total for {stay.numberOfRooms}{' '}
      room{stay.numberOfRooms === 1 ? '' : 's'}
    </p>
  );
}
