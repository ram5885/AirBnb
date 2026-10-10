import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import * as api from '../../api.js';
import {
  Alert,
  EmptyState,
  PaymentBadge,
  Spinner,
  StatusBadge,
  formatDate,
  formatDateTime,
  formatPrice,
  isoDate,
} from '../../components/manager/ui.jsx';
import { BookingActionDialog, refundNote } from './ManageBookings.jsx';
import { canAccept, canCancel, canMarkCollected } from './bookingActions.js';
import ConfirmDialog from '../../components/manager/ConfirmDialog.jsx';

export default function BookingDetails() {
  const { bookingId } = useParams();
  const today = isoDate(new Date());
  const [booking, setBooking] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [action, setAction] = useState(null);
  const [busy, setBusy] = useState(false);
  const [collectOpen, setCollectOpen] = useState(false);

  useEffect(() => {
    api.getBooking(bookingId).then(setBooking).catch((e) => setError(e.message));
  }, [bookingId]);

  async function runAction() {
    setBusy(true);
    setError('');
    try {
      const updated = action.kind === 'accept' ? await api.acceptBooking(booking.id) : await api.cancelBooking(booking.id);
      setBooking(updated);
      setNotice(
        action.kind === 'accept'
          ? 'Booking accepted.'
          : `Booking cancelled and its rooms released.${refundNote(updated.paymentStatus)}`,
      );
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
      setAction(null);
    }
  }

  async function markCollected() {
    setBusy(true);
    setError('');
    try {
      setBooking(await api.markPaymentCollected(booking.id));
      setNotice('Payment marked as collected at the hotel.');
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
      setCollectOpen(false);
    }
  }

  if (error && !booking) {
    return (
      <EmptyState title="Booking not available" action={<Link to="/manager/bookings" className="m-btn m-btn--secondary">Back to bookings</Link>}>
        {error}
      </EmptyState>
    );
  }
  if (!booking) return <Spinner />;

  return (
    <>
      <nav className="m-breadcrumb" aria-label="Breadcrumb">
        <Link to="/manager/bookings">Bookings</Link> / <span>#{booking.id}</span>
      </nav>
      <div className="m-page-header">
        <div>
          <h1>
            Booking #{booking.id} <StatusBadge status={booking.status} />
          </h1>
          <p className="m-muted">Created {formatDateTime(booking.createdAt)} · updated {formatDateTime(booking.updatedAt)}</p>
        </div>
        <div className="m-page-header__actions">
          {canMarkCollected(booking) && (
            <button type="button" className="m-btn m-btn--primary" onClick={() => setCollectOpen(true)}>
              Mark payment collected
            </button>
          )}
          {canAccept(booking, today) && (
            <button type="button" className="m-btn m-btn--primary" onClick={() => setAction({ kind: 'accept', booking })}>
              Accept booking
            </button>
          )}
          {canCancel(booking, today) && (
            <button type="button" className="m-btn m-btn--danger" onClick={() => setAction({ kind: 'cancel', booking })}>
              Cancel booking
            </button>
          )}
        </div>
      </div>

      <Alert onDismiss={() => setError('')}>{error}</Alert>
      <Alert type="success" onDismiss={() => setNotice('')}>
        {notice}
      </Alert>

      <div className="m-detail-grid">
        <section className="m-section">
          <h2>Stay</h2>
          <dl className="m-dl">
            <dt>Hotel</dt>
            <dd>
              {booking.hotelDeleted ? (
                `${booking.hotelName} (deleted)`
              ) : (
                <Link to={`/manager/hotels/${booking.hotelId}`}>{booking.hotelName}</Link>
              )}
              , {booking.hotelCity}
            </dd>
            <dt>Room type</dt>
            <dd>{booking.roomType}</dd>
            <dt>Check-in</dt>
            <dd>{formatDate(booking.checkInDate)}</dd>
            <dt>Check-out</dt>
            <dd>
              {formatDate(booking.checkOutDate)} ({booking.nights} night(s))
            </dd>
            <dt>Rooms</dt>
            <dd>{booking.roomsCount}</dd>
          </dl>
        </section>
        <section className="m-section">
          <h2>Guest</h2>
          <dl className="m-dl">
            <dt>Booked by</dt>
            <dd>{booking.bookedByName}</dd>
            <dt>Email</dt>
            <dd>{booking.bookedByEmail}</dd>
          </dl>
          <h3 className="m-subheading">Guests ({booking.guests.length})</h3>
          {booking.guests.length === 0 ? (
            <p className="m-muted">No guest details added yet.</p>
          ) : (
            <ul className="m-list">
              {booking.guests.map((g, i) => (
                <li key={i}>
                  {g.name}
                  {g.age ? `, ${g.age}` : ''}
                  {g.gender ? ` · ${g.gender.toLowerCase()}` : ''}
                </li>
              ))}
            </ul>
          )}
        </section>
        <section className="m-section">
          <h2>Payment</h2>
          <dl className="m-dl">
            <dt>Booking total</dt>
            <dd>{formatPrice(booking.amount)}</dd>
            <dt>Status</dt>
            <dd>
              <PaymentBadge status={booking.paymentStatus} method={booking.paymentMethod} />
            </dd>
            <dt>Method</dt>
            <dd>{booking.paymentMethod === 'PAY_AT_HOTEL' ? 'Pay at hotel' : booking.paymentMethod ? 'Online (Razorpay)' : '—'}</dd>
            {booking.paymentAmount != null && (
              <>
                <dt>Paid amount</dt>
                <dd>{formatPrice(booking.paymentAmount)}</dd>
              </>
            )}
          </dl>
        </section>
      </div>

      <BookingActionDialog
        action={action && { ...action, booking: { ...booking } }}
        busy={busy}
        onCancel={() => setAction(null)}
        onConfirm={runAction}
      />
      <ConfirmDialog
        open={collectOpen}
        title="Mark payment as collected?"
        confirmLabel="Payment collected"
        cancelLabel="Not yet"
        danger={false}
        busy={busy}
        onCancel={() => setCollectOpen(false)}
        onConfirm={markCollected}
      >
        <p>
          Confirm that {booking.bookedByName} paid {formatPrice(booking.amount)} at the hotel. After this the booking can no
          longer be cancelled online; any cancellation or refund has to be handled at the hotel.
        </p>
      </ConfirmDialog>
    </>
  );
}
