import { useEffect, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import * as api from '../../api.js';
import Navbar from '../../components/Navbar.jsx';
import PublicImage from '../../components/search/PublicImage.jsx';
import { amenityItems, useAmenityCatalog } from '../../components/manager/AmenityPicker.jsx';
import { AmenityList } from '../../components/manager/AmenityIcon.jsx';
import { EmptyState, Spinner, formatDate, formatPrice } from '../../components/manager/ui.jsx';
import { hasDates, readStay, stayParams, stayQuery } from '../../components/search/stay.js';
import StayBar from './StayBar.jsx';
import '../../styles/manager.css';
import '../../styles/home.css';
import '../../styles/search.css';

/** Public room type details with a night-by-night availability breakdown. */
export default function RoomPage() {
  const { hotelId, roomId } = useParams();
  const [params, setParams] = useSearchParams();
  const stay = readStay(params);
  const { categories } = useAmenityCatalog();

  const [room, setRoom] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const key = params.toString();
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError('');
    api
      .getPublicRoom(hotelId, roomId, stayParams(stay))
      .then((r) => !cancelled && setRoom(r))
      .catch((e) => !cancelled && setError(e.message))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hotelId, roomId, key]);

  function applyStay(v) {
    const next = new URLSearchParams(params);
    next.set('checkinDate', v.checkinDate);
    next.set('checkoutDate', v.checkoutDate);
    next.set('numberOfRooms', v.numberOfRooms);
    setParams(next);
  }

  const hotelLink = `/hotels/${hotelId}${stayQuery(stay)}`;
  const resultsLink = stay.city && hasDates(stay) ? `/${stayQuery(stay)}` : '/';

  return (
    <div className="home">
      <Navbar />
      <main className="container home__main">
        <nav className="m-breadcrumb" aria-label="Breadcrumb">
          <Link to={resultsLink}>{stay.city && hasDates(stay) ? 'Search results' : 'Search'}</Link> /{' '}
          <Link to={hotelLink}>{room?.hotelName || 'Hotel'}</Link>
          {room && <> / <span>{room.type}</span></>}
        </nav>

        {loading && !room && <Spinner label="Loading room…" />}
        {error && !room && (
          <EmptyState title="Room not available" action={<Link to={hotelLink} className="m-btn m-btn--secondary">Back to hotel</Link>}>
            {error}
          </EmptyState>
        )}

        {room && (
          <>
            <div className="m-page-header">
              <div>
                <h1>{room.type}</h1>
                <p className="s-muted">
                  {room.hotelName} · {room.city}, {room.state}
                </p>
              </div>
            </div>

            <div className={`s-gallery s-gallery--${Math.min(room.photoUrls.length, 5) || 1}`}>
              {room.photoUrls.length === 0 && <PublicImage src={null} alt={`${room.type} (no photos yet)`} />}
              {room.photoUrls.slice(0, 5).map((url, i) => (
                <PublicImage key={url} src={url} alt={`${room.type} photo ${i + 1}`} />
              ))}
            </div>

            <div className="s-detail">
              <div>
                <section className="m-section">
                  <h2>About this room</h2>
                  <dl className="m-dl">
                    <dt>Guests</dt>
                    <dd>Up to {room.capacity} per room</dd>
                    <dt>Base price</dt>
                    <dd>{formatPrice(room.basePrice)} / night</dd>
                  </dl>
                </section>
                <section className="m-section">
                  <h2>Room amenities</h2>
                  {room.amenities.length ? (
                    <AmenityList items={amenityItems(categories, room.amenities)} />
                  ) : (
                    <p className="s-muted">No amenities listed.</p>
                  )}
                </section>
              </div>

              <section className="m-section s-rooms" aria-labelledby="avail-heading">
                <h2 id="avail-heading">Availability</h2>
                <StayBar stay={stay} onApply={applyStay} busy={loading} />
                {!room.availability && <p className="s-muted">Choose dates to see availability and prices.</p>}
                {room.availability && (
                  <>
                    <p className={room.availability.available ? 's-available' : 's-unavailable'}>
                      {room.availability.available
                        ? `Available: ${room.availability.availableRooms} room${room.availability.availableRooms === 1 ? '' : 's'} free every night`
                        : `Not available for ${room.stay.numberOfRooms} room${room.stay.numberOfRooms === 1 ? '' : 's'} on every night`}
                    </p>
                    {room.availability.available && (
                      <Link to={`/book/${hotelId}/${roomId}${stayQuery(stay)}`} className="m-btn m-btn--primary s-reserve">
                        Reserve
                      </Link>
                    )}
                    {room.availability.totalPrice != null && (
                      <p>
                        <strong>{formatPrice(room.availability.totalPrice)}</strong> total for {room.stay.nights} night
                        {room.stay.nights === 1 ? '' : 's'} × {room.stay.numberOfRooms} room
                        {room.stay.numberOfRooms === 1 ? '' : 's'} ({formatPrice(room.availability.averageNightlyPrice)} / night avg)
                      </p>
                    )}
                    <div className="m-table-wrap">
                      <table className="m-table">
                        <thead>
                          <tr>
                            <th scope="col">Night of</th>
                            <th scope="col">Price / room</th>
                            <th scope="col">Rooms free</th>
                          </tr>
                        </thead>
                        <tbody>
                          {room.nights.map((n) => (
                            <tr key={n.date} className={n.bookable ? '' : 's-row-unavailable'}>
                              <td>{formatDate(n.date)}</td>
                              <td>{n.price == null ? 'Not on sale' : formatPrice(n.price)}</td>
                              <td>{n.availableRooms}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </>
                )}
              </section>
            </div>
          </>
        )}
      </main>
    </div>
  );
}
