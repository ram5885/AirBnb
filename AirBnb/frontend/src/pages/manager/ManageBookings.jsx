import { useCallback, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import * as api from '../../api.js';
import ConfirmDialog from '../../components/manager/ConfirmDialog.jsx';
import {
  Alert,
  EmptyState,
  Pagination,
  PaymentBadge,
  Spinner,
  StatusBadge,
  formatDate,
  formatPrice,
  isoDate,
} from '../../components/manager/ui.jsx';
import { canAccept, canCancel } from './bookingActions.js';

const STATUSES = ['RESERVED', 'ACCEPTED', 'CONFIRMED', 'CANCELLED', 'EXPIRED'];

export default function ManageBookings() {
  const [params, setParams] = useSearchParams();
  const filters = {
    hotelId: params.get('hotelId') || '',
    status: params.get('status') || '',
    startDate: params.get('startDate') || '',
    endDate: params.get('endDate') || '',
    reference: params.get('reference') || '',
    page: Number(params.get('page') || 0),
  };
  const today = isoDate(new Date());

  const [hotels, setHotels] = useState([]);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [action, setAction] = useState(null); // { kind: 'accept'|'cancel', booking }
  const [busy, setBusy] = useState(false);
  const [referenceInput, setReferenceInput] = useState(filters.reference);

  useEffect(() => {
    api
      .listHotels({ size: 100 })
      .then((p) => setHotels(p.content))
      .catch(() => setHotels([]));
  }, []);

  const key = params.toString();
  const load = useCallback(() => {
    setData(null);
    setError('');
    if (filters.startDate && filters.endDate && filters.endDate < filters.startDate) {
      setError('End date must be on or after the start date');
      setData({ content: [], page: 0, totalPages: 0, totalElements: 0 });
      return;
    }
    api
      .listBookings({ ...filters, size: 20 })
      .then(setData)
      .catch((e) => setError(e.message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);

  useEffect(load, [load]);

  function setFilter(k, v) {
    const next = new URLSearchParams(params);
    if (v === '' || v === null || v === undefined) next.delete(k);
    else next.set(k, v);
    if (k !== 'page') next.delete('page');
    setParams(next);
  }

  async function runAction() {
    setBusy(true);
    setError('');
    try {
      const { kind, booking } = action;
      const updated = kind === 'accept' ? await api.acceptBooking(booking.id) : await api.cancelBooking(booking.id);
      setNotice(
        kind === 'accept'
          ? `Booking #${booking.id} accepted.`
          : `Booking #${booking.id} cancelled and its rooms released.${refundNote(updated.paymentStatus)}`,
      );
      load();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
      setAction(null);
    }
  }

  const hasFilters = filters.hotelId || filters.status || filters.startDate || filters.endDate || filters.reference;
  return (
    <>
      <div className="m-page-header">
        <div>
          <h1>Bookings</h1>
          {data && <p className="m-muted">{data.totalElements} booking(s)</p>}
        </div>
      </div>

      <div className="m-filters" role="search">
        <div>
          <label htmlFor="bf-hotel">Hotel</label>
          <select id="bf-hotel" value={filters.hotelId} onChange={(e) => setFilter('hotelId', e.target.value)}>
            <option value="">All hotels</option>
            {hotels.map((h) => (
              <option key={h.id} value={h.id}>
                {h.name}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="bf-status">Status</label>
          <select id="bf-status" value={filters.status} onChange={(e) => setFilter('status', e.target.value)}>
            <option value="">All statuses</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {s.charAt(0) + s.slice(1).toLowerCase()}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="bf-start">Stay from</label>
          <input id="bf-start" type="date" value={filters.startDate} onChange={(e) => setFilter('startDate', e.target.value)} />
        </div>
        <div>
          <label htmlFor="bf-end">Stay to</label>
          <input id="bf-end" type="date" value={filters.endDate} onChange={(e) => setFilter('endDate', e.target.value)} />
        </div>
        <form
          className="b-ref-filter"
          onSubmit={(e) => {
            e.preventDefault();
            setFilter('reference', referenceInput.trim());
          }}
        >
          <label htmlFor="bf-ref">Reference</label>
          <input
            id="bf-ref"
            placeholder="BK000123"
            value={referenceInput}
            onChange={(e) => setReferenceInput(e.target.value)}
          />
        </form>
        {hasFilters && (
          <button
            type="button"
            className="m-btn m-btn--text"
            onClick={() => {
              setReferenceInput('');
              setParams(new URLSearchParams());
            }}
          >
            Clear filters
          </button>
        )}
      </div>

      <Alert onDismiss={() => setError('')}>{error}</Alert>
      <Alert type="success" onDismiss={() => setNotice('')}>
        {notice}
      </Alert>

      {!data && !error && <Spinner />}
      {data && data.content.length === 0 && (
        <EmptyState title={hasFilters ? 'No bookings match these filters' : 'No bookings yet'}>
          {hasFilters ? 'Try widening the dates or clearing filters.' : 'Bookings for your hotels will appear here.'}
        </EmptyState>
      )}
      {data && data.content.length > 0 && (
        <div className="m-table-wrap">
          <table className="m-table">
            <thead>
              <tr>
                <th scope="col">Ref</th>
                <th scope="col">Hotel / room type</th>
                <th scope="col">Check-in</th>
                <th scope="col">Check-out</th>
                <th scope="col">Rooms</th>
                <th scope="col">Guests</th>
                <th scope="col">Booked by</th>
                <th scope="col">Amount</th>
                <th scope="col">Status</th>
                <th scope="col">Payment</th>
                <th scope="col">Created</th>
                <th scope="col">
                  <span className="visually-hidden">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((b) => (
                <tr key={b.id}>
                  <td>
                    <Link to={`/manager/bookings/${b.id}`}>#{b.id}</Link>
                  </td>
                  <td>
                    <strong>{b.hotelName}</strong>
                    <div className="m-small m-muted">{b.roomType}</div>
                  </td>
                  <td>{formatDate(b.checkInDate)}</td>
                  <td>
                    {formatDate(b.checkOutDate)}
                    <div className="m-small m-muted">{b.nights} night(s)</div>
                  </td>
                  <td>{b.roomsCount}</td>
                  <td>{b.guestCount || '—'}</td>
                  <td>{b.bookedBy}</td>
                  <td>{formatPrice(b.amount)}</td>
                  <td>
                    <StatusBadge status={b.status} />
                  </td>
                  <td>
                    <PaymentBadge status={b.paymentStatus} method={b.paymentMethod} />
                  </td>
                  <td>{formatDate(b.createdAt)}</td>
                  <td>
                    <div className="m-row-actions">
                      {canAccept(b, today) && (
                        <button type="button" className="m-btn m-btn--primary m-btn--sm" onClick={() => setAction({ kind: 'accept', booking: b })}>
                          Accept
                        </button>
                      )}
                      {canCancel(b, today) && (
                        <button type="button" className="m-btn m-btn--danger-text m-btn--sm" onClick={() => setAction({ kind: 'cancel', booking: b })}>
                          Cancel
                        </button>
                      )}
                      <Link to={`/manager/bookings/${b.id}`} className="m-btn m-btn--secondary m-btn--sm">
                        View
                      </Link>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={(p) => setFilter('page', p)} />}

      <BookingActionDialog action={action} busy={busy} onCancel={() => setAction(null)} onConfirm={runAction} />
    </>
  );
}

export function BookingActionDialog({ action, busy, onCancel, onConfirm }) {
  const b = action?.booking;
  const isCancel = action?.kind === 'cancel';
  return (
    <ConfirmDialog
      open={!!action}
      title={isCancel ? `Cancel booking #${b?.id}?` : `Accept booking #${b?.id}?`}
      confirmLabel={isCancel ? 'Cancel booking' : 'Accept booking'}
      cancelLabel={isCancel ? 'Keep booking' : 'Not now'}
      danger={isCancel}
      busy={busy}
      onCancel={onCancel}
      onConfirm={onConfirm}
    >
      {b && (
        <p>
          {b.roomsCount} room(s) of <strong>{b.roomType}</strong> at <strong>{b.hotelName}</strong>,{' '}
          {formatDate(b.checkInDate)} – {formatDate(b.checkOutDate)}.{' '}
          {isCancel
            ? `The rooms will be released for these dates. ${
                b.paymentStatus === 'CONFIRMED' && b.paymentMethod !== 'PAY_AT_HOTEL'
                  ? `The guest paid ${formatPrice(b.amount)} online; it will be refunded in full through Razorpay.`
                  : 'The guest hasn’t paid, so nothing is refunded.'
              } This cannot be undone.`
            : 'The guest’s reservation will be marked as accepted. They still need to pay to confirm it.'}
        </p>
      )}
    </ConfirmDialog>
  );
}

export function refundNote(paymentStatus) {
  if (paymentStatus === 'REFUNDED') return ' The payment was refunded in full.';
  if (paymentStatus === 'REFUND_PENDING') return ' A full refund has been requested from Razorpay.';
  return '';
}
