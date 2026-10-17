import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import * as api from '../../api.js';
import AmenityPicker from '../../components/manager/AmenityPicker.jsx';
import PhotoPicker from '../../components/manager/PhotoPicker.jsx';
import { Alert, EmptyState, Field, Spinner } from '../../components/manager/ui.jsx';

const EMPTY = { type: '', basePrice: '', totalCount: '1', capacity: '2', amenities: [] };

/** Create Room Type (/manager/hotels/:hotelId/rooms/new) and Edit (/.../rooms/:roomId/edit). */
export default function RoomTypeForm() {
  const { hotelId, roomId } = useParams();
  const isEdit = !!roomId;
  const navigate = useNavigate();

  const [hotel, setHotel] = useState(null);
  const [form, setForm] = useState(EMPTY);
  const [existingPhotos, setExistingPhotos] = useState([]);
  const [removedPhotoIds, setRemovedPhotoIds] = useState([]);
  const [newPhotos, setNewPhotos] = useState([]);
  const [photoError, setPhotoError] = useState('');
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState('');
  const [loadError, setLoadError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const requests = [api.getHotel(hotelId), isEdit ? api.getRoom(hotelId, roomId) : Promise.resolve(null)];
    Promise.all(requests)
      .then(([h, r]) => {
        setHotel(h);
        if (r) {
          setForm({
            type: r.type,
            basePrice: String(r.basePrice),
            totalCount: String(r.totalCount),
            capacity: String(r.capacity),
            amenities: r.amenities,
          });
          setExistingPhotos(r.photos);
        }
      })
      .catch((e) => setLoadError(e.message))
      .finally(() => setLoading(false));
  }, [hotelId, roomId, isEdit]);

  function update(key, value) {
    setForm((f) => ({ ...f, [key]: value }));
    setErrors((e) => ({ ...e, [key]: undefined }));
  }

  function validate() {
    const e = {};
    const price = Number(form.basePrice);
    const total = Number(form.totalCount);
    const capacity = Number(form.capacity);
    if (!form.type.trim()) e.type = 'Room type name is required';
    if (form.basePrice === '' || Number.isNaN(price)) e.basePrice = 'Base price is required';
    else if (price < 1 || price > 1000000) e.basePrice = 'Base price must be between 1 and 1,000,000';
    else if (!/^\d+(\.\d{1,2})?$/.test(form.basePrice.trim())) e.basePrice = 'Use at most 2 decimal places';
    if (!Number.isInteger(total) || total < 1 || total > 500) e.totalCount = 'Enter a whole number from 1 to 500';
    if (!Number.isInteger(capacity) || capacity < 1 || capacity > 20) e.capacity = 'Enter a whole number from 1 to 20';
    setErrors(e);
    return Object.keys(e).length === 0;
  }

  async function handleSubmit(ev) {
    ev.preventDefault();
    if (submitting) return;
    setFormError('');
    if (!validate()) {
      setFormError('Please correct the highlighted fields');
      return;
    }
    const payload = {
      type: form.type.trim(),
      basePrice: form.basePrice.trim(),
      totalCount: Number(form.totalCount),
      capacity: Number(form.capacity),
      amenities: form.amenities,
      ...(isEdit ? { removedPhotoIds } : {}),
    };
    setSubmitting(true);
    try {
      if (isEdit) await api.updateRoom(hotelId, roomId, payload, newPhotos);
      else await api.createRoom(hotelId, payload, newPhotos);
      navigate(`/manager/hotels/${hotelId}`, {
        state: { flash: isEdit ? `Room type "${payload.type}" updated.` : `Room type "${payload.type}" created.` },
      });
    } catch (e) {
      setErrors(e.fieldErrors || {});
      setFormError(e.message);
      setSubmitting(false);
    }
  }

  if (loading) return <Spinner />;
  if (loadError) {
    return (
      <EmptyState title="Not available" action={<Link to="/manager/hotels" className="m-btn m-btn--secondary">Back to hotels</Link>}>
        {loadError}
      </EmptyState>
    );
  }

  return (
    <form className="m-form" onSubmit={handleSubmit} noValidate>
      <nav className="m-breadcrumb" aria-label="Breadcrumb">
        <Link to="/manager/hotels">Hotels</Link> / <Link to={`/manager/hotels/${hotelId}`}>{hotel.name}</Link> /{' '}
        <span>{isEdit ? 'Edit room type' : 'New room type'}</span>
      </nav>
      <div className="m-page-header">
        <h1>{isEdit ? 'Edit room type' : 'Create room type'}</h1>
      </div>
      <Alert onDismiss={() => setFormError('')}>{formError}</Alert>

      <section className="m-section">
        <h2>Details</h2>
        <Field id="room-type" label="Room type name" required error={errors.type} hint="e.g. Deluxe King, Family Suite">
          <input
            id="room-type"
            maxLength={100}
            value={form.type}
            onChange={(e) => update('type', e.target.value)}
            aria-invalid={!!errors.type}
          />
        </Field>
        <div className="m-grid-3">
          <Field id="room-price" label="Base price per night (₹)" required error={errors.basePrice}>
            <input
              id="room-price"
              type="number"
              inputMode="decimal"
              min="1"
              step="0.01"
              value={form.basePrice}
              onChange={(e) => update('basePrice', e.target.value)}
              aria-invalid={!!errors.basePrice}
            />
          </Field>
          <Field
            id="room-count"
            label="Number of rooms"
            required
            error={errors.totalCount}
            hint="How many physical rooms of this type the hotel has"
          >
            <input
              id="room-count"
              type="number"
              min="1"
              max="500"
              step="1"
              value={form.totalCount}
              onChange={(e) => update('totalCount', e.target.value)}
              aria-invalid={!!errors.totalCount}
            />
          </Field>
          <Field id="room-capacity" label="Guests per room" required error={errors.capacity}>
            <input
              id="room-capacity"
              type="number"
              min="1"
              max="20"
              step="1"
              value={form.capacity}
              onChange={(e) => update('capacity', e.target.value)}
              aria-invalid={!!errors.capacity}
            />
          </Field>
        </div>
        {!isEdit && (
          <p className="m-muted m-small">
            Availability is opened for the next 12 months at the base price. You can close dates or adjust pricing from
            the room type's availability page.
          </p>
        )}
        {isEdit && (
          <p className="m-muted m-small">
            Changing the number of rooms or base price updates all future dates. The number of rooms can't go below what
            is already booked on any date.
          </p>
        )}
      </section>

      <section className="m-section">
        <h2>Photos</h2>
        <Alert onDismiss={() => setPhotoError('')}>{photoError}</Alert>
        <PhotoPicker
          existing={existingPhotos.filter((p) => !removedPhotoIds.includes(p.id))}
          files={newPhotos}
          onFilesChange={setNewPhotos}
          onRemoveExisting={(id) => setRemovedPhotoIds((ids) => [...ids, id])}
          onError={setPhotoError}
          disabled={submitting}
        />
      </section>

      <section className="m-section">
        <h2>Room amenities</h2>
        <AmenityPicker value={form.amenities} onChange={(a) => update('amenities', a)} disabled={submitting} />
      </section>

      <div className="m-form__actions">
        <Link to={`/manager/hotels/${hotelId}`} className="m-btn m-btn--secondary">
          Cancel
        </Link>
        <button type="submit" className="m-btn m-btn--primary" disabled={submitting}>
          {submitting ? 'Saving…' : isEdit ? 'Save changes' : 'Create room type'}
        </button>
      </div>
    </form>
  );
}
