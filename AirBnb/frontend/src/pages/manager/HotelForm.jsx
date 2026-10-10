import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import * as api from '../../api.js';
import Autocomplete from '../../components/manager/Autocomplete.jsx';
import AmenityPicker from '../../components/manager/AmenityPicker.jsx';
import PhotoPicker from '../../components/manager/PhotoPicker.jsx';
import { Alert, Field, Spinner, ToggleSwitch } from '../../components/manager/ui.jsx';

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE_RE = /^(\+91[- ]?)?[6-9]\d{9}$/;

const EMPTY = {
  name: '',
  state: null,
  city: null,
  address: '',
  landmark: '',
  contactPerson: '',
  phoneNumber: '',
  email: '',
  amenities: [],
  active: true,
};

/** Create Hotel (/manager/hotels/new) and Edit Hotel (/manager/hotels/:hotelId/edit). */
export default function HotelForm() {
  const { hotelId } = useParams();
  const isEdit = !!hotelId;
  const navigate = useNavigate();

  const [form, setForm] = useState(EMPTY);
  const [existingPhotos, setExistingPhotos] = useState([]);
  const [removedPhotoIds, setRemovedPhotoIds] = useState([]);
  const [newPhotos, setNewPhotos] = useState([]);
  const [photoError, setPhotoError] = useState('');
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [loadingHotel, setLoadingHotel] = useState(isEdit);

  const [states, setStates] = useState({ list: [], loading: true, error: '' });
  const [cities, setCities] = useState({ list: [], loading: false, error: '' });
  const cityCache = useRef({});

  useEffect(() => {
    api
      .getStates()
      .then((list) => setStates({ list, loading: false, error: '' }))
      .catch((e) => setStates({ list: [], loading: false, error: `Could not load states: ${e.message}` }));
  }, []);

  useEffect(() => {
    if (!isEdit) return;
    api
      .getHotel(hotelId)
      .then((h) => {
        setForm({
          name: h.name,
          state: h.state,
          city: h.city,
          address: h.contactInfo?.address || '',
          landmark: h.contactInfo?.landmark || '',
          contactPerson: h.contactInfo?.contactPerson || '',
          phoneNumber: h.contactInfo?.phoneNumber || '',
          email: h.contactInfo?.email || '',
          amenities: h.amenities,
          active: h.active,
        });
        setExistingPhotos(h.photos);
      })
      .catch((e) => setFormError(e.message))
      .finally(() => setLoadingHotel(false));
  }, [hotelId, isEdit]);

  // Cities are loaded per selected state from the Location table.
  useEffect(() => {
    if (!form.state) {
      setCities({ list: [], loading: false, error: '' });
      return;
    }
    if (cityCache.current[form.state]) {
      setCities({ list: cityCache.current[form.state], loading: false, error: '' });
      return;
    }
    let cancelled = false;
    setCities({ list: [], loading: true, error: '' });
    api
      .getCities(form.state)
      .then((locs) => {
        const list = locs.map((l) => l.city);
        cityCache.current[form.state] = list;
        if (!cancelled) setCities({ list, loading: false, error: '' });
      })
      .catch((e) => !cancelled && setCities({ list: [], loading: false, error: `Could not load cities: ${e.message}` }));
    return () => {
      cancelled = true;
    };
  }, [form.state]);

  function update(key, value) {
    setForm((f) => ({ ...f, [key]: value }));
    setErrors((e) => ({ ...e, [key]: undefined }));
  }

  function selectState(state) {
    setForm((f) => ({ ...f, state, city: state === f.state ? f.city : null })); // clear incompatible city
    setErrors((e) => ({ ...e, state: undefined, city: undefined }));
  }

  function validate() {
    const e = {};
    if (!form.name.trim()) e.name = 'Hotel name is required';
    else if (form.name.trim().length > 120) e.name = 'Hotel name must be at most 120 characters';
    if (!form.state) e.state = 'Select a state from the suggestions';
    if (!form.city) e.city = form.state ? 'Select a city from the suggestions' : 'Select a state first';
    if (!form.address.trim()) e.address = 'Address is required';
    if (!form.contactPerson.trim()) e.contactPerson = 'Contact person is required';
    if (!form.phoneNumber.trim()) e.phoneNumber = 'Phone number is required';
    else if (!PHONE_RE.test(form.phoneNumber.replace(/\s/g, ''))) e.phoneNumber = 'Enter a valid 10-digit Indian mobile number';
    if (!form.email.trim()) e.email = 'Email is required';
    else if (!EMAIL_RE.test(form.email.trim())) e.email = 'Enter a valid email address';
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
      name: form.name.trim(),
      state: form.state,
      city: form.city,
      address: form.address.trim(),
      landmark: form.landmark.trim() || null,
      contactPerson: form.contactPerson.trim(),
      phoneNumber: form.phoneNumber.replace(/\s/g, ''),
      email: form.email.trim(),
      amenities: form.amenities,
      active: form.active,
      ...(isEdit ? { removedPhotoIds } : {}),
    };
    setSubmitting(true);
    try {
      const saved = isEdit
        ? await api.updateHotel(hotelId, payload, newPhotos)
        : await api.createHotel(payload, newPhotos);
      navigate(`/manager/hotels/${saved.id}`, {
        state: { flash: isEdit ? 'Hotel updated.' : 'Hotel created. Add its room types next.' },
      });
    } catch (e) {
      // Keep everything the user entered; surface field-level errors where the server gave them.
      setErrors(e.fieldErrors || {});
      setFormError(e.message);
      setSubmitting(false);
    }
  }

  if (loadingHotel) return <Spinner label="Loading hotel…" />;

  const visibleExisting = existingPhotos.filter((p) => !removedPhotoIds.includes(p.id));
  const cancelTo = isEdit ? `/manager/hotels/${hotelId}` : '/manager/hotels';

  return (
    <form className="m-form" onSubmit={handleSubmit} noValidate>
      <div className="m-page-header">
        <h1>{isEdit ? 'Edit hotel' : 'Create hotel'}</h1>
      </div>
      <Alert onDismiss={() => setFormError('')}>{formError}</Alert>

      <section className="m-section">
        <h2>Basics</h2>
        <Field id="hotel-name" label="Hotel name" required error={errors.name}>
          <input
            id="hotel-name"
            value={form.name}
            maxLength={120}
            onChange={(e) => update('name', e.target.value)}
            aria-invalid={!!errors.name}
          />
        </Field>
        <div className="m-grid-2">
          <Field id="hotel-state" label="State" required error={errors.state || states.error}>
            <Autocomplete
              id="hotel-state"
              options={states.list}
              value={form.state}
              onSelect={selectState}
              placeholder="Start typing a state"
              loading={states.loading}
              loadError={states.error}
              emptyText="No matching state"
              invalid={!!errors.state}
            />
          </Field>
          <Field
            id="hotel-city"
            label="City"
            required
            error={errors.city || cities.error}
            hint={!form.state ? 'Select a state first' : undefined}
          >
            <Autocomplete
              id="hotel-city"
              options={cities.list}
              value={form.city}
              onSelect={(c) => update('city', c)}
              placeholder={form.state ? 'Start typing a city' : 'Select a state first'}
              disabled={!form.state}
              loading={cities.loading}
              loadError={cities.error}
              emptyText={`No matching city in ${form.state}`}
              invalid={!!errors.city}
            />
          </Field>
        </div>
      </section>

      <section className="m-section">
        <h2>Contact information</h2>
        <p className="m-muted m-small">Shown to you and your team only; not exposed in public listings.</p>
        <Field id="hotel-address" label="Address" required error={errors.address}>
          <textarea
            id="hotel-address"
            rows={2}
            maxLength={255}
            value={form.address}
            onChange={(e) => update('address', e.target.value)}
            aria-invalid={!!errors.address}
          />
        </Field>
        <Field id="hotel-landmark" label="Landmark / directions" error={errors.landmark}>
          <input
            id="hotel-landmark"
            maxLength={255}
            value={form.landmark}
            onChange={(e) => update('landmark', e.target.value)}
          />
        </Field>
        <div className="m-grid-3">
          <Field id="hotel-contact" label="Contact person" required error={errors.contactPerson}>
            <input
              id="hotel-contact"
              maxLength={100}
              value={form.contactPerson}
              onChange={(e) => update('contactPerson', e.target.value)}
              aria-invalid={!!errors.contactPerson}
            />
          </Field>
          <Field id="hotel-phone" label="Phone number" required error={errors.phoneNumber} hint="10-digit mobile, e.g. 9876543210">
            <input
              id="hotel-phone"
              type="tel"
              inputMode="tel"
              value={form.phoneNumber}
              onChange={(e) => update('phoneNumber', e.target.value)}
              aria-invalid={!!errors.phoneNumber}
            />
          </Field>
          <Field id="hotel-email" label="Email" required error={errors.email}>
            <input
              id="hotel-email"
              type="email"
              value={form.email}
              onChange={(e) => update('email', e.target.value)}
              aria-invalid={!!errors.email}
            />
          </Field>
        </div>
      </section>

      <section className="m-section">
        <h2>Photos</h2>
        <Alert onDismiss={() => setPhotoError('')}>{photoError}</Alert>
        <PhotoPicker
          existing={visibleExisting}
          files={newPhotos}
          onFilesChange={setNewPhotos}
          onRemoveExisting={(id) => setRemovedPhotoIds((ids) => [...ids, id])}
          onError={setPhotoError}
          disabled={submitting}
        />
      </section>

      <section className="m-section">
        <h2>Amenities</h2>
        <AmenityPicker value={form.amenities} onChange={(a) => update('amenities', a)} disabled={submitting} />
      </section>

      <section className="m-section">
        <h2>Status</h2>
        <ToggleSwitch
          id="hotel-active"
          checked={form.active}
          onChange={(v) => update('active', v)}
          disabled={submitting}
          label={form.active ? 'Active — guests can book this hotel' : 'Inactive — hidden and not bookable'}
        />
      </section>

      <div className="m-form__actions">
        <Link to={cancelTo} className="m-btn m-btn--secondary">
          Cancel
        </Link>
        <button type="submit" className="m-btn m-btn--primary" disabled={submitting}>
          {submitting ? 'Saving…' : isEdit ? 'Save changes' : 'Create hotel'}
        </button>
      </div>
    </form>
  );
}
