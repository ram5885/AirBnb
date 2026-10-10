import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import * as api from '../../api.js';
import Navbar from '../../components/Navbar.jsx';
import ConfirmDialog from '../../components/manager/ConfirmDialog.jsx';
import GuestPicker from '../../components/booking/GuestPicker.jsx';
import PublicImage from '../../components/search/PublicImage.jsx';
import { FlashMessage } from '../../components/manager/ManagerLayout.jsx';
import {
  Alert,
  EmptyState,
  PaymentBadge,
  Spinner,
  StatusBadge,
  formatDate,
  formatDateTime,
  formatPrice,
} from '../../components/manager/ui.jsx';
import '../../styles/manager.css';
import '../../styles/home.css';
import '../../styles/search.css';
import '../../styles/booking.css';

const REFUND_TEXT = {
  REFUND_PENDING: 'A full refund has been requested from Razorpay and is being processed.',
  REFUNDED: 'Your payment has been refunded in full.',
};

/** One of the guest's bookings: details, guests, pay, cancel. Also the confirmation page after payment. */
export default function MyBookingDetails() {
  const { bookingId } = useParams();
  const [booking, setBooking] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [editingGuests, setEditingGuests] = useState(null); // array of ids while editing
  const [savingGuests, setSavingGuests] = useState(false);

  useEffect(() => {
    api.getMyBooking(bookingId).then(setBooking).catch((e) => setError(e.message));
  }, [bookingId]);

  async function cancel() {
    setCancelling(true);
    setError('');
    try {
      const updated = await api.cancelMyBooking(bookingId);
      setBooking(updated);
      setNotice(
        `Booking cancelled. ${REFUND_TEXT[updated.paymentStatus] || 'Nothing was charged.'}`,
      );
    } catch (e) {
      setError(e.message);
    } finally {
      setCancelling(false);
      setConfirmOpen(false);
    }
  }

  async function saveGuests() {
    setSavingGuests(true);
    setError('');
    try {
      setBooking(await api.updateBookingGuests(bookingId, editingGuests));
      setEditingGuests(null);
      setNotice('Guests updated.');
    } catch (e) {
      setError(e.message);
    } finally {
      setSavingGuests(false);
    }
  }

  if (error && !booking) {
    return (
      <Shell>
        <EmptyState title="Booking not available" action={<Link to="/my-bookings" className="m-btn m-btn--secondary">My bookings</Link>}>
          {error}
        </EmptyState>
      </Shell>
    );
  }
  if (!booking) {
    return (
      <Shell>
        <Spinner />
      </Shell>
    );
  }

  const paid = ['CONFIRMED', 'REFUND_PENDING', 'REFUNDED'].includes(booking.paymentStatus);
  const atHotel = booking.paymentMethod === 'PAY_AT_HOTEL';
  return (
    <Shell reference={booking.reference}>
      <FlashMessage />
      {booking.status === 'CONFIRMED' && (
        <div className="b-confirmed" role="status">
          <strong>You’re booked!</strong> {booking.hotelName} is expecting you on {formatDate(booking.checkInDate)}.
        </div>
      )}
      <div className="m-page-header">
        <div>
          <h1>
            {booking.hotelName} <StatusBadge status={booking.status} />
          </h1>
          <p className="s-muted">
            {booking.reference} · {booking.roomType} · {booking.city}, {booking.state}
          </p>
        </div>
        <div className="m-page-header__actions">
          {booking.canPay && (
            <Link to={`/my-bookings/${booking.id}/pay`} className="m-btn m-btn--primary">
              Pay {formatPrice(booking.amount)}
            </Link>
          )}
          {booking.canCancel && (
            <button type="button" className="m-btn m-btn--danger" onClick={() => setConfirmOpen(true)}>
              Cancel booking
            </button>
          )}
        </div>
      </div>

      <Alert onDismiss={() => setError('')}>{error}</Alert>
      <Alert type="success" onDismiss={() => setNotice('')}>
        {notice}
      </Alert>

      <div className="b-layout">
        <div>
          <div className="b-cover">
            <PublicImage src={booking.hotelPhotoUrl} alt={booking.hotelName} />
          </div>
          <section className="m-section">
            <div className="m-section__header">
              <h2>Guests ({booking.guests?.length || 0})</h2>
              {booking.canEditGuests && !editingGuests && (
                <button
                  type="button"
                  className="m-btn m-btn--secondary m-btn--sm"
                  onClick={() => setEditingGuests(booking.guests.map((g) => g.id))}
                >
                  Edit guests
                </button>
              )}
            </div>
            {editingGuests ? (
              <>
                <GuestPicker
                  selected={editingGuests}
                  onChange={setEditingGuests}
                  max={booking.maxGuests}
                  disabled={savingGuests}
                />
                <div className="m-row-actions">
                  <button type="button" className="m-btn m-btn--secondary" onClick={() => setEditingGuests(null)} disabled={savingGuests}>
                    Cancel
                  </button>
                  <button
                    type="button"
                    className="m-btn m-btn--primary"
                    onClick={saveGuests}
                    disabled={savingGuests || editingGuests.length === 0}
                  >
                    {savingGuests ? 'Saving…' : 'Save guests'}
                  </button>
                </div>
              </>
            ) : booking.guests?.length ? (
              <ul className="m-list">
                {booking.guests.map((g) => (
                  <li key={g.id}>
                    {g.name} · {g.age}
                    {g.gender ? `, ${g.gender.toLowerCase()}` : ''}
                  </li>
                ))}
              </ul>
            ) : (
              <p className="s-muted">No guests added yet.</p>
            )}
          </section>
        </div>

        <aside className="m-section b-summary">
          <dl className="m-dl">
            <dt>Check-in</dt>
            <dd>{formatDate(booking.checkInDate)}</dd>
            <dt>Check-out</dt>
            <dd>{formatDate(booking.checkOutDate)}</dd>
            <dt>Nights</dt>
            <dd>{booking.nights}</dd>
            <dt>Rooms</dt>
            <dd>{booking.roomsCount}</dd>
            <dt>Booked on</dt>
            <dd>{formatDateTime(booking.createdAt)}</dd>
            <dt>Payment</dt>
            <dd>
              <PaymentBadge status={booking.paymentStatus} method={booking.paymentMethod} />
            </dd>
          </dl>
          <p className="b-summary__total">
            <span>{paid ? 'Paid' : 'Total'}</span>
            <strong>{formatPrice(booking.amount)}</strong>
          </p>
          {REFUND_TEXT[booking.paymentStatus] && <p className="s-small">{REFUND_TEXT[booking.paymentStatus]}</p>}
          {atHotel && booking.status === 'CONFIRMED' && booking.paymentStatus === 'PENDING' && (
            <p className="s-small">Pay {formatPrice(booking.amount)} at the hotel when you check in.</p>
          )}
          {atHotel && paid && (
            <p className="s-small s-muted">Paid at the hotel. For changes or cancellation, please contact the hotel.</p>
          )}
          {booking.status === 'EXPIRED' && (
            <p className="s-small s-muted">This reservation wasn’t paid in time, so its rooms were released.</p>
          )}
          {!booking.canCancel && ['RESERVED', 'ACCEPTED', 'CONFIRMED'].includes(booking.status) && (
            <p className="s-small s-muted">Cancellation is available until the day before check-in.</p>
          )}
          <Link to={`/hotels/${booking.hotelId}`} className="m-btn m-btn--text">
            View hotel
          </Link>
        </aside>
      </div>

      <ConfirmDialog
        open={confirmOpen}
        title={`Cancel booking ${booking.reference}?`}
        confirmLabel="Cancel booking"
        cancelLabel="Keep booking"
        busy={cancelling}
        onCancel={() => setConfirmOpen(false)}
        onConfirm={cancel}
      >
        <p>
          Your {booking.roomsCount} room{booking.roomsCount === 1 ? '' : 's'} at {booking.hotelName} for{' '}
          {formatDate(booking.checkInDate)} – {formatDate(booking.checkOutDate)} will be released.{' '}
          {paid && !atHotel
            ? `The full amount of ${formatPrice(booking.amount)} will be refunded to your original payment method through Razorpay.`
            : 'You haven’t been charged, so there’s nothing to refund.'}{' '}
          This can’t be undone.
        </p>
      </ConfirmDialog>
    </Shell>
  );
}

function Shell({ reference, children }) {
  return (
    <div className="home">
      <Navbar />
      <main className="container home__main">
        <nav className="m-breadcrumb" aria-label="Breadcrumb">
          <Link to="/my-bookings">My bookings</Link>
          {reference && <> / <span>{reference}</span></>}
        </nav>
        {children}
      </main>
    </div>
  );
}
