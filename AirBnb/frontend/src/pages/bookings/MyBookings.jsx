import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import * as api from '../../api.js';
import Navbar from '../../components/Navbar.jsx';
import PublicImage from '../../components/search/PublicImage.jsx';
import { FlashMessage } from '../../components/manager/ManagerLayout.jsx';
import {
  Alert,
  EmptyState,
  Pagination,
  PaymentBadge,
  Spinner,
  StatusBadge,
  formatDate,
  formatPrice,
} from '../../components/manager/ui.jsx';
import '../../styles/manager.css';
import '../../styles/home.css';
import '../../styles/search.css';
import '../../styles/booking.css';

const TABS = [
  { scope: 'upcoming', label: 'Upcoming', empty: 'No upcoming trips yet.' },
  { scope: 'pending', label: 'Awaiting payment', empty: 'Nothing waiting for payment.' },
  { scope: 'past', label: 'Past', empty: 'No past stays yet.' },
  { scope: 'cancelled', label: 'Cancelled', empty: 'No cancelled or expired bookings.' },
  { scope: 'all', label: 'All', empty: 'You haven’t booked anything yet.' },
];

/** The signed-in guest's bookings, by tab, from GET /api/v1/bookings (owner taken from the token). */
export default function MyBookings() {
  const [params, setParams] = useSearchParams();
  const scope = TABS.some((t) => t.scope === params.get('scope')) ? params.get('scope') : 'upcoming';
  const page = Number(params.get('page') || 0);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    setData(null);
    setError('');
    api
      .listMyBookings({ scope, page, size: 10 })
      .then((d) => !cancelled && setData(d))
      .catch((e) => !cancelled && setError(e.message));
    return () => {
      cancelled = true;
    };
  }, [scope, page]);

  const tab = TABS.find((t) => t.scope === scope);
  return (
    <div className="home">
      <Navbar />
      <main className="container home__main">
        <FlashMessage />
        <div className="m-page-header">
          <h1>My bookings</h1>
          <Link to="/" className="m-btn m-btn--secondary">
            Find a stay
          </Link>
        </div>

        <div className="b-tabs" role="tablist" aria-label="Booking groups">
          {TABS.map((t) => (
            <button
              key={t.scope}
              type="button"
              role="tab"
              aria-selected={t.scope === scope}
              className={t.scope === scope ? 'is-active' : ''}
              onClick={() => setParams({ scope: t.scope })}
            >
              {t.label}
            </button>
          ))}
        </div>

        <Alert>{error}</Alert>
        {!data && !error && <Spinner />}
        {data && data.content.length === 0 && (
          <EmptyState title={tab.empty} action={<Link to="/" className="m-btn m-btn--primary">Search hotels</Link>} />
        )}
        {data && data.content.length > 0 && (
          <ul className="b-list">
            {data.content.map((b) => (
              <li key={b.id} className="b-item">
                <Link to={`/my-bookings/${b.id}`} className="b-item__image" tabIndex={-1} aria-hidden="true">
                  <PublicImage src={b.hotelPhotoUrl} alt="" />
                </Link>
                <div className="b-item__body">
                  <div className="b-item__top">
                    <h2>
                      <Link to={`/my-bookings/${b.id}`}>{b.hotelName}</Link>
                    </h2>
                    <span>
                      <StatusBadge status={b.status} /> <PaymentBadge status={b.paymentStatus} method={b.paymentMethod} />
                    </span>
                  </div>
                  <p className="s-muted">
                    {b.roomType} · {b.city}, {b.state}
                  </p>
                  <p>
                    {formatDate(b.checkInDate)} – {formatDate(b.checkOutDate)} · {b.roomsCount} room
                    {b.roomsCount === 1 ? '' : 's'} · {b.guestCount} guest{b.guestCount === 1 ? '' : 's'}
                  </p>
                  <p className="s-small s-muted">
                    {b.reference} · booked {formatDate(b.createdAt)} · <strong>{formatPrice(b.amount)}</strong>
                  </p>
                  <div className="m-row-actions">
                    {b.canPay && (
                      <Link to={`/my-bookings/${b.id}/pay`} className="m-btn m-btn--primary m-btn--sm">
                        Pay now
                      </Link>
                    )}
                    <Link to={`/my-bookings/${b.id}`} className="m-btn m-btn--secondary m-btn--sm">
                      Details
                    </Link>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
        {data && (
          <Pagination
            page={data.page}
            totalPages={data.totalPages}
            onChange={(p) => setParams({ scope, page: String(p) })}
          />
        )}
      </main>
    </div>
  );
}
