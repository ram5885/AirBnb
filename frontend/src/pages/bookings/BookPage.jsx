import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import * as api from '../../api.js';
import Navbar from '../../components/Navbar.jsx';
import GuestPicker from '../../components/booking/GuestPicker.jsx';
import { Alert, EmptyState, Spinner, formatDate, formatPrice } from '../../components/manager/ui.jsx';
import { hasDates, readStay, stayParams, stayQuery } from '../../components/search/stay.js';
import '../../styles/manager.css';
import '../../styles/home.css';
import '../../styles/search.css';
import '../../styles/booking.css';

/**
 * Step 1 of booking: review the stay, choose guests, then reserve. The reservation's price is computed
 * by the backend; the estimate here comes from the same public availability API.
 */
export default function BookPage() {
  const { hotelId, roomId } = useParams();
  const [params] = useSearchParams();
  const stay = readStay(params);
  const navigate = useNavigate();

  const [room, setRoom] = useState(null);
  const [error, setError] = useState('');
  const [guestIds, setGuestIds] = useState([]);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState('');
  const submitted = useRef(false);

  useEffect(() => {
    if (!hasDates(stay)) return;
    api
      .getPublicRoom(hotelId, roomId, stayParams(stay))
      .then(setRoom)
      .catch((e) => setError(e.message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hotelId, roomId, params.toString()]);

  const back = `/hotels/${hotelId}/rooms/${roomId}${stayQuery(stay)}`;

  async function reserve() {
    if (submitted.current) return; // guards against double clicks creating two bookings
    if (guestIds.length === 0) {
      setSubmitError('Choose at least one guest who is travelling.');
      return;
    }
    submitted.current = true;
    setSubmitting(true);
    setSubmitError('');
    try {
      const booking = await api.createBooking({
        hotelId: Number(hotelId),
        roomId: Number(roomId),
        checkInDate: stay.checkinDate,
        checkOutDate: stay.checkoutDate,
        roomsCount: stay.numberOfRooms,
        guestIds,
      });
      // replace: going "back" from payment shouldn't resubmit this page
      navigate(`/my-bookings/${booking.id}/pay`, { replace: true });
    } catch (e) {
      submitted.current = false;
      setSubmitting(false);
      setSubmitError(e.message);
    }
  }

  let body;
  if (!hasDates(stay)) {
    body = (
      <EmptyState title="Choose your dates first" action={<Link to={back} className="m-btn m-btn--secondary">Back</Link>}>
        Pick check-in and check-out dates on the room page to book.
      </EmptyState>
    );
  } else if (error) {
    body = (
      <EmptyState title="This room can't be booked" action={<Link to={back} className="m-btn m-btn--secondary">Back</Link>}>
        {error}
      </EmptyState>
    );
  } else if (!room) {
    body = <Spinner label="Checking availability…" />;
  } else {
    const max = room.capacity * stay.numberOfRooms;
    const a = room.availability;
    body = (
      <div className="b-layout">
        <div>
          <section className="m-section">
            <h2>Who’s travelling?</h2>
            <p className="s-muted s-small">
              Up to {room.capacity} guest{room.capacity === 1 ? '' : 's'} per room · {stay.numberOfRooms} room
              {stay.numberOfRooms === 1 ? '' : 's'}
            </p>
            <GuestPicker selected={guestIds} onChange={setGuestIds} max={max} disabled={submitting} />
          </section>
        </div>
        <aside className="m-section b-summary" aria-label="Booking summary">
          <h2>{room.type}</h2>
          <p className="s-muted">
            {room.hotelName} · {room.city}, {room.state}
          </p>
          <dl className="m-dl">
            <dt>Check-in</dt>
            <dd>{formatDate(stay.checkinDate)}</dd>
            <dt>Check-out</dt>
            <dd>{formatDate(stay.checkoutDate)}</dd>
            <dt>Nights</dt>
            <dd>{room.stay.nights}</dd>
            <dt>Rooms</dt>
            <dd>{stay.numberOfRooms}</dd>
            <dt>Guests</dt>
            <dd>{guestIds.length}</dd>
          </dl>
          {a?.available ? (
            <p className="b-summary__total">
              <span>Total</span>
              <strong>{formatPrice(a.totalPrice)}</strong>
            </p>
          ) : (
            <Alert>These dates are no longer available for {stay.numberOfRooms} room(s).</Alert>
          )}
          <Alert onDismiss={() => setSubmitError('')}>{submitError}</Alert>
          <button
            type="button"
            className="m-btn m-btn--primary b-summary__cta"
            onClick={reserve}
            disabled={submitting || !a?.available}
          >
            {submitting ? 'Reserving…' : 'Reserve and continue to payment'}
          </button>
          <p className="s-muted s-small">
            Rooms are held for a few minutes while you pay. The final price is confirmed by our server.
          </p>
        </aside>
      </div>
    );
  }

  return (
    <div className="home">
      <Navbar />
      <main className="container home__main">
        <nav className="m-breadcrumb" aria-label="Breadcrumb">
          <Link to={back}>{room ? room.type : 'Room'}</Link> / <span>Book</span>
        </nav>
        <div className="m-page-header">
          <h1>Confirm and pay</h1>
        </div>
        {body}
      </main>
    </div>
  );
}
