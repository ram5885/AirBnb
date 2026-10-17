import { useCallback, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import * as api from '../../api.js';
import AuthImage from '../../components/manager/AuthImage.jsx';
import ConfirmDialog from '../../components/manager/ConfirmDialog.jsx';
import { FlashMessage } from '../../components/manager/ManagerLayout.jsx';
import { ActiveBadge, Alert, EmptyState, Pagination, Spinner, ToggleSwitch, formatDate } from '../../components/manager/ui.jsx';

export default function ManageHotels() {
  const [params, setParams] = useSearchParams();
  const active = params.get('active') ?? '';
  const page = Number(params.get('page') || 0);

  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busyId, setBusyId] = useState(null);
  const [toDelete, setToDelete] = useState(null);
  const [deleting, setDeleting] = useState(false);

  const load = useCallback(() => {
    setError('');
    api
      .listHotels({ active, page, size: 12 })
      .then(setData)
      .catch((e) => setError(e.message));
  }, [active, page]);

  useEffect(load, [load]);

  function setFilter(key, value) {
    const next = new URLSearchParams(params);
    if (value === '' || value === null) next.delete(key);
    else next.set(key, value);
    if (key !== 'page') next.delete('page');
    setParams(next);
  }

  async function toggleActive(hotel) {
    setBusyId(hotel.id);
    setNotice('');
    try {
      await api.setHotelActive(hotel.id, !hotel.active);
      setNotice(`${hotel.name} is now ${hotel.active ? 'inactive' : 'active'}.`);
      load();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusyId(null);
    }
  }

  async function confirmDelete() {
    setDeleting(true);
    try {
      await api.deleteHotel(toDelete.id);
      setNotice(`${toDelete.name} was deleted.`);
      setToDelete(null);
      load();
    } catch (e) {
      setError(e.message);
      setToDelete(null);
    } finally {
      setDeleting(false);
    }
  }

  return (
    <>
      <FlashMessage />
      <div className="m-page-header">
        <div>
          <h1>Your hotels</h1>
          {data && <p className="m-muted">{data.totalElements} hotel(s)</p>}
        </div>
        <Link to="/manager/hotels/new" className="m-btn m-btn--primary">
          Create hotel
        </Link>
      </div>

      <div className="m-filters">
        <label htmlFor="hotel-filter-active">Status</label>
        <select id="hotel-filter-active" value={active} onChange={(e) => setFilter('active', e.target.value)}>
          <option value="">All</option>
          <option value="true">Active</option>
          <option value="false">Inactive</option>
        </select>
      </div>

      <Alert onDismiss={() => setError('')}>{error}</Alert>
      <Alert type="success" onDismiss={() => setNotice('')}>
        {notice}
      </Alert>

      {!data && !error && <Spinner />}
      {data && data.content.length === 0 && (
        <EmptyState
          title={active ? 'No hotels match this filter' : 'You have no hotels yet'}
          action={
            <Link to="/manager/hotels/new" className="m-btn m-btn--primary">
              Create your first hotel
            </Link>
          }
        />
      )}

      {data && data.content.length > 0 && (
        <div className="m-card-grid">
          {data.content.map((h) => (
            <article key={h.id} className="m-card m-hotel-card">
              <Link to={`/manager/hotels/${h.id}`} className="m-hotel-card__image">
                <AuthImage src={h.coverPhotoUrl} alt={`${h.name} cover photo`} />
              </Link>
              <div className="m-hotel-card__body">
                <div className="m-hotel-card__title">
                  <h2>
                    <Link to={`/manager/hotels/${h.id}`}>{h.name}</Link>
                  </h2>
                  <ActiveBadge active={h.active} />
                </div>
                <p className="m-muted">
                  {h.city}, {h.state}
                </p>
                <p className="m-muted m-small">
                  {h.roomTypeCount} room type(s) · {h.photoCount} photo(s) · updated {formatDate(h.updatedAt)}
                </p>
                <div className="m-hotel-card__actions">
                  <ToggleSwitch
                    id={`active-${h.id}`}
                    checked={h.active}
                    disabled={busyId === h.id}
                    onChange={() => toggleActive(h)}
                  />
                  <Link to={`/manager/hotels/${h.id}/edit`} className="m-btn m-btn--secondary m-btn--sm">
                    Edit
                  </Link>
                  <button type="button" className="m-btn m-btn--danger-text m-btn--sm" onClick={() => setToDelete(h)}>
                    Delete
                  </button>
                </div>
              </div>
            </article>
          ))}
        </div>
      )}
      {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={(p) => setFilter('page', p)} />}

      <ConfirmDialog
        open={!!toDelete}
        title={`Delete ${toDelete?.name}?`}
        confirmLabel="Delete hotel"
        busy={deleting}
        onCancel={() => setToDelete(null)}
        onConfirm={confirmDelete}
      >
        <p>
          The hotel and its room types will be removed from your listings and can no longer be booked. Past and
          cancelled bookings are kept for your records. Hotels with upcoming bookings can't be deleted — cancel those
          bookings or deactivate the hotel instead.
        </p>
      </ConfirmDialog>
    </>
  );
}
