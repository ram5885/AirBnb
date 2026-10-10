import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import * as api from '../../api.js';
import { Alert, EmptyState, Field, Spinner, addDays, formatPrice, isoDate } from '../../components/manager/ui.jsx';

const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

/** Availability for one room type: view a date window, select a range, open/close it or set a surge factor. */
export default function InventoryPage() {
  const { hotelId, roomId } = useParams();
  const today = isoDate(new Date());

  const [hotel, setHotel] = useState(null);
  const [room, setRoom] = useState(null);
  const [view, setView] = useState({ start: today, end: addDays(today, 27) });
  const [days, setDays] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const [selStart, setSelStart] = useState('');
  const [selEnd, setSelEnd] = useState('');
  const [surge, setSurge] = useState('');
  const [formErrors, setFormErrors] = useState({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    Promise.all([api.getHotel(hotelId), api.getRoom(hotelId, roomId)])
      .then(([h, r]) => {
        setHotel(h);
        setRoom(r);
      })
      .catch((e) => setError(e.message));
  }, [hotelId, roomId]);

  const loadDays = useCallback(() => {
    setDays(null);
    api
      .getInventory(hotelId, roomId, view.start, view.end)
      .then(setDays)
      .catch((e) => {
        setDays([]);
        setError(e.message);
      });
  }, [hotelId, roomId, view]);

  useEffect(loadDays, [loadDays]);

  function selectDay(date) {
    if (date < today) return;
    if (!selStart || (selStart && selEnd)) {
      setSelStart(date);
      setSelEnd('');
    } else if (date < selStart) {
      setSelStart(date);
    } else {
      setSelEnd(date);
    }
    setFormErrors({});
  }

  function inSelection(date) {
    const end = selEnd || selStart;
    return selStart && date >= selStart && date <= end;
  }

  function shift(weeks) {
    setView((v) => ({ start: addDays(v.start, weeks * 7), end: addDays(v.end, weeks * 7) }));
  }

  async function apply(change) {
    const start = selStart;
    const end = selEnd || selStart;
    const errs = {};
    if (!start) errs.range = 'Select a start date (click a day or use the date fields)';
    else if (start < today) errs.range = "Past dates can't be changed";
    else if (end < start) errs.range = 'End date must be on or after the start date';
    if (change.surgeFactor !== undefined) {
      const n = Number(change.surgeFactor);
      if (change.surgeFactor === '' || Number.isNaN(n) || n < 0.5 || n > 5) errs.surge = 'Enter a factor between 0.50 and 5.00';
    }
    setFormErrors(errs);
    if (Object.keys(errs).length) return;

    setSaving(true);
    setError('');
    setNotice('');
    try {
      const res = await api.updateInventory(hotelId, roomId, { startDate: start, endDate: end, ...change });
      setNotice(`Updated ${res.updatedDays} day(s).`);
      loadDays();
    } catch (e) {
      setError(e.message);
    } finally {
      setSaving(false);
    }
  }

  if (error && !room) {
    return (
      <EmptyState title="Not available" action={<Link to="/manager/hotels" className="m-btn m-btn--secondary">Back to hotels</Link>}>
        {error}
      </EmptyState>
    );
  }
  if (!room || !hotel) return <Spinner />;

  return (
    <>
      <nav className="m-breadcrumb" aria-label="Breadcrumb">
        <Link to="/manager/hotels">Hotels</Link> / <Link to={`/manager/hotels/${hotelId}`}>{hotel.name}</Link> /{' '}
        <span>{room.type} availability</span>
      </nav>
      <div className="m-page-header">
        <div>
          <h1>{room.type} — availability</h1>
          <p className="m-muted">
            {room.totalCount} room(s) · base price {formatPrice(room.basePrice)} / night
          </p>
        </div>
      </div>

      <Alert onDismiss={() => setError('')}>{error}</Alert>
      <Alert type="success" onDismiss={() => setNotice('')}>
        {notice}
      </Alert>

      <section className="m-section">
        <h2>Change selected dates</h2>
        <div className="m-grid-3">
          <Field id="inv-start" label="From" error={formErrors.range}>
            <input id="inv-start" type="date" min={today} value={selStart} onChange={(e) => setSelStart(e.target.value)} />
          </Field>
          <Field id="inv-end" label="To (inclusive)">
            <input id="inv-end" type="date" min={selStart || today} value={selEnd} onChange={(e) => setSelEnd(e.target.value)} />
          </Field>
          <Field id="inv-surge" label="Surge factor" error={formErrors.surge} hint="1.00 = base price, 1.50 = +50%">
            <input
              id="inv-surge"
              type="number"
              min="0.5"
              max="5"
              step="0.05"
              value={surge}
              onChange={(e) => setSurge(e.target.value)}
            />
          </Field>
        </div>
        <div className="m-row-actions">
          <button type="button" className="m-btn m-btn--secondary" disabled={saving} onClick={() => apply({ closed: true })}>
            Close dates
          </button>
          <button type="button" className="m-btn m-btn--secondary" disabled={saving} onClick={() => apply({ closed: false })}>
            Open dates
          </button>
          <button
            type="button"
            className="m-btn m-btn--primary"
            disabled={saving}
            onClick={() => apply({ surgeFactor: surge })}
          >
            Apply surge factor
          </button>
          {(selStart || selEnd) && (
            <button
              type="button"
              className="m-btn m-btn--text"
              onClick={() => {
                setSelStart('');
                setSelEnd('');
              }}
            >
              Clear selection
            </button>
          )}
        </div>
        <p className="m-muted m-small">
          Closed dates can't receive new bookings; existing bookings are unaffected. Tip: click a day in the calendar to
          start a selection, and another day to end it.
        </p>
      </section>

      <section className="m-section">
        <div className="m-section__header">
          <h2>Calendar</h2>
          <div className="m-row-actions">
            <button type="button" className="m-btn m-btn--secondary m-btn--sm" onClick={() => shift(-4)} disabled={view.start <= today}>
              ← Previous
            </button>
            <button type="button" className="m-btn m-btn--secondary m-btn--sm" onClick={() => setView({ start: today, end: addDays(today, 27) })}>
              Today
            </button>
            <button type="button" className="m-btn m-btn--secondary m-btn--sm" onClick={() => shift(4)}>
              Next →
            </button>
          </div>
        </div>
        {!days && <Spinner />}
        {days && days.length === 0 && <EmptyState title="No availability in this window">Inventory is opened 12 months ahead.</EmptyState>}
        {days && days.length > 0 && (
          <ul className="m-calendar" aria-label="Daily availability">
            {days.map((d) => {
              const past = d.date < today;
              const selected = inSelection(d.date);
              const dt = new Date(`${d.date}T00:00:00`);
              return (
                <li key={d.date}>
                  <button
                    type="button"
                    className={`m-day ${d.closed ? 'is-closed' : ''} ${d.available === 0 ? 'is-full' : ''} ${selected ? 'is-selected' : ''}`}
                    onClick={() => selectDay(d.date)}
                    disabled={past}
                    aria-pressed={selected}
                    aria-label={`${d.date}: ${d.closed ? 'closed' : `${d.available} of ${d.totalCount} available`}, ${formatPrice(d.price)}`}
                  >
                    <span className="m-day__date">
                      {WEEKDAYS[dt.getDay()]} {dt.getDate()} {dt.toLocaleString(undefined, { month: 'short' })}
                    </span>
                    <span className="m-day__avail">
                      {d.closed ? 'Closed' : `${d.available}/${d.totalCount} free`}
                    </span>
                    <span className="m-day__meta">{d.bookedCount} booked</span>
                    <span className="m-day__meta">
                      {formatPrice(d.price)}
                      {Number(d.surgeFactor) !== 1 && ` (×${Number(d.surgeFactor).toFixed(2)})`}
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </section>
    </>
  );
}
