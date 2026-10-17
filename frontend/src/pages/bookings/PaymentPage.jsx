import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import * as api from '../../api.js';
import Navbar from '../../components/Navbar.jsx';
import { loadRazorpay, openCheckout } from '../../components/booking/razorpay.js';
import { Alert, EmptyState, PaymentBadge, Spinner, StatusBadge, formatDate, formatPrice } from '../../components/manager/ui.jsx';
import '../../styles/manager.css';
import '../../styles/home.css';
import '../../styles/search.css';
import '../../styles/booking.css';

/**
 * Step 2: pay for a reserved booking with Razorpay Checkout. The amount shown and charged comes from
 * the backend. A booking counts as confirmed only when the backend verifies the payment; reaching
 * any page in the browser proves nothing.
 */
export default function PaymentPage() {
  const { bookingId } = useParams();
  const navigate = useNavigate();
  const [booking, setBooking] = useState(null);
  const [loadError, setLoadError] = useState('');
  // idle | starting | checkout | verifying | confirming | failed | dismissed | error
  const [phase, setPhase] = useState('idle');
  const [method, setMethod] = useState('ONLINE'); // ONLINE | PAY_AT_HOTEL
  const [message, setMessage] = useState('');
  const remaining = useCountdown(booking?.holdExpiresAt);

  const refresh = useCallback(
    () =>
      api
        .getMyBooking(bookingId)
        .then((b) => {
          setBooking(b);
          return b;
        })
        .catch((e) => setLoadError(e.message)),
    [bookingId],
  );

  useEffect(() => {
    refresh();
  }, [refresh]);

  async function verify(response) {
    setPhase('verifying');
    setMessage('');
    try {
      const result = await api.verifyPayment({
        bookingId: Number(bookingId),
        razorpayOrderId: response.razorpay_order_id,
        razorpayPaymentId: response.razorpay_payment_id,
        razorpaySignature: response.razorpay_signature,
      });
      setBooking(result.booking);
      if (result.confirmed) {
        navigate(`/my-bookings/${bookingId}`, { replace: true, state: { flash: result.message } });
      } else {
        setPhase('failed');
        setMessage(result.message);
      }
    } catch (e) {
      // Network or server problem while verifying: the payment may still have gone through.
      setPhase('error');
      setMessage(
        `${e.message} If money was deducted, your booking will be confirmed automatically or refunded. ` +
          'Check My Bookings in a minute.',
      );
    }
  }

  /** Confirm now, pay at the hotel: the backend confirms the booking; no online payment is made. */
  async function confirmPayAtHotel() {
    if (phase === 'confirming' || phase === 'verifying') return;
    setPhase('confirming');
    setMessage('');
    try {
      const confirmed = await api.payAtHotel(bookingId);
      navigate(`/my-bookings/${bookingId}`, {
        replace: true,
        state: { flash: `Booking confirmed. Please pay ${formatPrice(confirmed.amount)} at the hotel when you check in.` },
      });
    } catch (e) {
      setPhase('error');
      setMessage(e.message);
      refresh();
    }
  }

  async function payNow() {
    if (phase === 'starting' || phase === 'checkout' || phase === 'verifying' || phase === 'confirming') return;
    setPhase('starting');
    setMessage('');
    try {
      const [order, loaded] = await Promise.all([api.createPaymentOrder(bookingId), loadRazorpay()]);
      if (!loaded) throw new Error('Could not load Razorpay Checkout. Check your connection and try again.');
      setPhase('checkout');
      openCheckout(order, {
        onSuccess: verify,
        onFailure: (err) => {
          // The popup stays open so the guest can try another method; record the failed attempt.
          setPhase('failed');
          setMessage(err.description || 'The payment failed. You can try again.');
          api
            .reportPaymentFailure(bookingId, {
              razorpayOrderId: order.orderId,
              razorpayPaymentId: err.metadata?.payment_id,
              code: err.code,
              description: err.description,
            })
            .catch(() => {});
        },
        onDismiss: () => {
          setPhase((p) => (p === 'verifying' ? p : p === 'failed' ? 'failed' : 'dismissed'));
          refresh();
        },
      });
    } catch (e) {
      setPhase('error');
      setMessage(e.message);
      refresh();
    }
  }

  if (loadError) {
    return (
      <Shell>
        <EmptyState title="Booking not available" action={<Link to="/my-bookings" className="m-btn m-btn--secondary">My bookings</Link>}>
          {loadError}
        </EmptyState>
      </Shell>
    );
  }
  if (!booking) {
    return (
      <Shell>
        <Spinner label="Loading booking…" />
      </Shell>
    );
  }

  const busy = phase === 'starting' || phase === 'checkout' || phase === 'verifying' || phase === 'confirming';
  const expired = booking.holdExpiresAt && remaining === 0;
  return (
    <Shell>
      <div className="m-page-header">
        <div>
          <h1>Payment</h1>
          <p className="s-muted">
            Booking {booking.reference} · <StatusBadge status={booking.status} /> <PaymentBadge status={booking.paymentStatus} method={booking.paymentMethod} />
          </p>
        </div>
      </div>

      <div className="b-layout">
        <section className="m-section">
          <h2>{booking.hotelName}</h2>
          <p className="s-muted">
            {booking.roomType} · {booking.city}, {booking.state}
          </p>
          <dl className="m-dl">
            <dt>Check-in</dt>
            <dd>{formatDate(booking.checkInDate)}</dd>
            <dt>Check-out</dt>
            <dd>
              {formatDate(booking.checkOutDate)} ({booking.nights} night{booking.nights === 1 ? '' : 's'})
            </dd>
            <dt>Rooms</dt>
            <dd>{booking.roomsCount}</dd>
            <dt>Guests</dt>
            <dd>{booking.guests?.map((g) => g.name).join(', ') || '—'}</dd>
          </dl>
        </section>

        <aside className="m-section b-summary" aria-live="polite">
          <p className="b-summary__total">
            <span>Total to pay</span>
            <strong>{formatPrice(booking.amount)}</strong>
          </p>
          {booking.canPay && booking.holdExpiresAt && (
            <p className={`s-small ${remaining < 120 ? 's-unavailable' : 's-muted'}`}>
              {expired ? 'Your hold has ended.' : `Rooms held for ${formatRemaining(remaining)}.`}
            </p>
          )}

          {phase === 'verifying' && <Spinner label="Confirming your payment…" />}
          {phase === 'failed' && <Alert>{message}</Alert>}
          {phase === 'error' && <Alert>{message}</Alert>}
          {phase === 'dismissed' && (
            <Alert type="success">Payment window closed. Nothing was charged; you can pay whenever you’re ready.</Alert>
          )}

          {booking.canPay && !expired ? (
            <>
              <fieldset className="b-methods" disabled={busy}>
                <legend className="s-small">How would you like to pay?</legend>
                <label className={`b-method ${method === 'ONLINE' ? 'is-selected' : ''}`}>
                  <input type="radio" name="method" value="ONLINE" checked={method === 'ONLINE'} onChange={() => setMethod('ONLINE')} />
                  <span>
                    <strong>Pay now online</strong>
                    <span className="s-small s-muted">Card, UPI or netbanking via Razorpay. Confirmed instantly.</span>
                  </span>
                </label>
                <label className={`b-method ${method === 'PAY_AT_HOTEL' ? 'is-selected' : ''}`}>
                  <input
                    type="radio"
                    name="method"
                    value="PAY_AT_HOTEL"
                    checked={method === 'PAY_AT_HOTEL'}
                    onChange={() => setMethod('PAY_AT_HOTEL')}
                  />
                  <span>
                    <strong>Pay at hotel</strong>
                    <span className="s-small s-muted">
                      Your booking is confirmed now; pay {formatPrice(booking.amount)} when you check in.
                    </span>
                  </span>
                </label>
              </fieldset>
              {method === 'ONLINE' ? (
                <button type="button" className="m-btn m-btn--primary b-summary__cta" onClick={payNow} disabled={busy}>
                  {phase === 'starting' ? 'Opening payment…' : busy ? 'Payment in progress…' : phase === 'failed' ? 'Try again' : 'Pay now'}
                </button>
              ) : (
                <button type="button" className="m-btn m-btn--primary b-summary__cta" onClick={confirmPayAtHotel} disabled={busy}>
                  {phase === 'confirming' ? 'Confirming…' : 'Confirm booking, pay at hotel'}
                </button>
              )}
            </>
          ) : (
            <>
              <Alert type={booking.status === 'CONFIRMED' ? 'success' : 'error'}>
                {booking.status === 'CONFIRMED'
                  ? booking.paymentMethod === 'PAY_AT_HOTEL' && booking.paymentStatus === 'PENDING'
                    ? `This booking is confirmed. Pay ${formatPrice(booking.amount)} at the hotel.`
                    : 'This booking is already paid and confirmed.'
                  : booking.status === 'EXPIRED' || expired
                    ? 'This reservation has expired and its rooms were released. Please search and book again.'
                    : 'This booking can’t be paid.'}
              </Alert>
              <Link to={`/my-bookings/${booking.id}`} className="m-btn m-btn--secondary b-summary__cta">
                View booking
              </Link>
            </>
          )}
          {phase === 'error' && (
            <button type="button" className="m-btn m-btn--text" onClick={refresh}>
              Refresh status
            </button>
          )}
          <p className="s-muted s-small">Online payments are processed securely by Razorpay (test mode in development).</p>
        </aside>
      </div>
    </Shell>
  );
}

function Shell({ children }) {
  return (
    <div className="home">
      <Navbar />
      <main className="container home__main">
        <nav className="m-breadcrumb" aria-label="Breadcrumb">
          <Link to="/my-bookings">My bookings</Link> / <span>Payment</span>
        </nav>
        {children}
      </main>
    </div>
  );
}

/** Seconds left until `iso` (local date-time from the server), ticking every second. */
function useCountdown(iso) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    if (!iso) return undefined;
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, [iso]);
  if (!iso) return null;
  return Math.max(0, Math.floor((new Date(iso).getTime() - now) / 1000));
}

function formatRemaining(seconds) {
  const m = Math.floor(seconds / 60);
  const s = String(seconds % 60).padStart(2, '0');
  return `${m}:${s}`;
}
