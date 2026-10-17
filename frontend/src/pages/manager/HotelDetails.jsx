import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import * as api from '../../api.js';
import AuthImage from '../../components/manager/AuthImage.jsx';
import ConfirmDialog from '../../components/manager/ConfirmDialog.jsx';
import { amenityItems, amenityLabels, useAmenityCatalog } from '../../components/manager/AmenityPicker.jsx';
import { AmenityList } from '../../components/manager/AmenityIcon.jsx';
import { FlashMessage } from '../../components/manager/ManagerLayout.jsx';
import {
  ActiveBadge,
  Alert,
  EmptyState,
  Spinner,
  ToggleSwitch,
  formatDateTime,
  formatPrice,
} from '../../components/manager/ui.jsx';

export default function HotelDetails() {
  const { hotelId } = useParams();
  const navigate = useNavigate();
  const { categories } = useAmenityCatalog();

  const [hotel, setHotel] = useState(null);
  const [rooms, setRooms] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [toggling, setToggling] = useState(false);
  const [confirm, setConfirm] = useState(null); // { kind: 'hotel' } | { kind: 'room', room }
  const [deleting, setDeleting] = useState(false);

  const load = useCallback(() => {
    Promise.all([api.getHotel(hotelId), api.listRooms(hotelId)])
      .then(([h, r]) => {
        setHotel(h);
        setRooms(r);
      })
      .catch((e) => setError(e.message));
  }, [hotelId]);

  useEffect(load, [load]);

  async function toggleActive(active) {
    setToggling(true);
    setNotice('');
    try {
      setHotel(await api.setHotelActive(hotelId, active));
      setNotice(active ? 'Hotel activated — guests can book it.' : 'Hotel deactivated — it can no longer be booked.');
    } catch (e) {
      setError(e.message);
    } finally {
      setToggling(false);
    }
  }

  async function confirmDelete() {
    setDeleting(true);
    setError('');
    try {
      if (confirm.kind === 'hotel') {
        await api.deleteHotel(hotelId);
        navigate('/manager/hotels', { state: { flash: `${hotel.name} was deleted.` } });
        return;
      }
      await api.deleteRoom(hotelId, confirm.room.id);
      setNotice(`Room type "${confirm.room.type}" was deleted.`);
      load();
    } catch (e) {
      setError(e.message);
    } finally {
      setDeleting(false);
      setConfirm(null);
    }
  }

  if (error && !hotel) {
    return (
      <EmptyState title="Hotel not available" action={<Link to="/manager/hotels" className="m-btn m-btn--secondary">Back to hotels</Link>}>
        {error}
      </EmptyState>
    );
  }
  if (!hotel || !rooms) return <Spinner label="Loading hotel…" />;

  const c = hotel.contactInfo || {};
  return (
    <>
      <FlashMessage />
      <nav className="m-breadcrumb" aria-label="Breadcrumb">
        <Link to="/manager/hotels">Hotels</Link> / <span>{hotel.name}</span>
      </nav>
      <div className="m-page-header">
        <div>
          <h1>
            {hotel.name} <ActiveBadge active={hotel.active} />
          </h1>
          <p className="m-muted">
            {hotel.city}, {hotel.state}
          </p>
        </div>
        <div className="m-page-header__actions">
          <Link to={`/manager/bookings?hotelId=${hotel.id}`} className="m-btn m-btn--secondary">
            Bookings
          </Link>
          <Link to={`/manager/hotels/${hotel.id}/edit`} className="m-btn m-btn--secondary">
            Edit hotel
          </Link>
          <button type="button" className="m-btn m-btn--danger-text" onClick={() => setConfirm({ kind: 'hotel' })}>
            Delete
          </button>
        </div>
      </div>

      <Alert onDismiss={() => setError('')}>{error}</Alert>
      <Alert type="success" onDismiss={() => setNotice('')}>
        {notice}
      </Alert>

      {hotel.photos.length > 0 ? (
        <div className="m-gallery">
          {hotel.photos.map((p) => (
            <AuthImage key={p.id} src={p.url} alt={p.fileName} />
          ))}
        </div>
      ) : (
        <p className="m-muted">No photos yet.</p>
      )}

      <div className="m-detail-grid">
        <section className="m-section">
          <h2>Status</h2>
          <ToggleSwitch
            id="detail-active"
            checked={hotel.active}
            disabled={toggling}
            onChange={toggleActive}
            label={hotel.active ? 'Active — bookable by guests' : 'Inactive — not bookable'}
          />
          <dl className="m-dl">
            <dt>Created</dt>
            <dd>{formatDateTime(hotel.createdAt)}</dd>
            <dt>Last updated</dt>
            <dd>{formatDateTime(hotel.updatedAt)}</dd>
          </dl>
        </section>
        <section className="m-section">
          <h2>Contact</h2>
          <dl className="m-dl">
            <dt>Contact person</dt>
            <dd>{c.contactPerson || '—'}</dd>
            <dt>Phone</dt>
            <dd>{c.phoneNumber || '—'}</dd>
            <dt>Email</dt>
            <dd>{c.email || '—'}</dd>
            <dt>Address</dt>
            <dd>{c.address || '—'}</dd>
            {c.landmark && (
              <>
                <dt>Landmark</dt>
                <dd>{c.landmark}</dd>
              </>
            )}
          </dl>
        </section>
        <section className="m-section">
          <h2>Amenities</h2>
          {hotel.amenities.length ? (
            <AmenityList items={amenityItems(categories, hotel.amenities)} />
          ) : (
            <p className="m-muted">No amenities selected.</p>
          )}
        </section>
      </div>

      <section className="m-section">
        <div className="m-section__header">
          <h2>Room types</h2>
          <Link to={`/manager/hotels/${hotel.id}/rooms/new`} className="m-btn m-btn--primary">
            Create room type
          </Link>
        </div>
        {rooms.length === 0 ? (
          <EmptyState title="No room types yet">
            Create a room type to open its availability for the next 12 months.
          </EmptyState>
        ) : (
          <div className="m-table-wrap">
            <table className="m-table">
              <thead>
                <tr>
                  <th scope="col">Room type</th>
                  <th scope="col">Base price / night</th>
                  <th scope="col">Rooms</th>
                  <th scope="col">Capacity</th>
                  <th scope="col">Amenities</th>
                  <th scope="col">
                    <span className="visually-hidden">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {rooms.map((r) => (
                  <tr key={r.id}>
                    <td>
                      <div className="m-room-cell">
                        <AuthImage src={r.photos[0]?.url} alt={`${r.type} photo`} className="m-thumb" />
                        <strong>{r.type}</strong>
                      </div>
                    </td>
                    <td>{formatPrice(r.basePrice)}</td>
                    <td>{r.totalCount}</td>
                    <td>{r.capacity} guest(s)</td>
                    <td className="m-small">{amenityLabels(categories, r.amenities).join(', ') || '—'}</td>
                    <td>
                      <div className="m-row-actions">
                        <Link to={`/manager/hotels/${hotel.id}/rooms/${r.id}/inventory`} className="m-btn m-btn--secondary m-btn--sm">
                          Availability
                        </Link>
                        <Link to={`/manager/hotels/${hotel.id}/rooms/${r.id}/edit`} className="m-btn m-btn--secondary m-btn--sm">
                          Edit
                        </Link>
                        <button
                          type="button"
                          className="m-btn m-btn--danger-text m-btn--sm"
                          onClick={() => setConfirm({ kind: 'room', room: r })}
                        >
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <ConfirmDialog
        open={!!confirm}
        title={confirm?.kind === 'hotel' ? `Delete ${hotel.name}?` : `Delete room type "${confirm?.room?.type}"?`}
        confirmLabel={confirm?.kind === 'hotel' ? 'Delete hotel' : 'Delete room type'}
        busy={deleting}
        onCancel={() => setConfirm(null)}
        onConfirm={confirmDelete}
      >
        <p>
          {confirm?.kind === 'hotel'
            ? 'The hotel and all its room types will be removed from your listings and can no longer be booked.'
            : 'This room type will be removed and its future dates closed for booking.'}{' '}
          Past and cancelled bookings are kept. This isn't possible while there are upcoming bookings — cancel them first.
        </p>
      </ConfirmDialog>
    </>
  );
}
