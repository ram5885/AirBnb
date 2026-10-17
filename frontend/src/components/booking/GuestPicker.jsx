import { useEffect, useState } from 'react';
import * as api from '../../api.js';
import { Alert, Field, Spinner } from '../manager/ui.jsx';

const EMPTY = { name: '', gender: '', age: '' };

/**
 * Choose who is travelling: the user's saved guests (checkboxes) plus an inline form to add one.
 * `selected` is an array of guest ids; `max` is rooms x capacity.
 */
export default function GuestPicker({ selected, onChange, max, disabled }) {
  const [guests, setGuests] = useState(null);
  const [loadError, setLoadError] = useState('');
  const [form, setForm] = useState(EMPTY);
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState('');

  useEffect(() => {
    api
      .listMyGuests()
      .then(setGuests)
      .catch((e) => setLoadError(e.message));
  }, []);

  function toggle(id) {
    if (selected.includes(id)) onChange(selected.filter((g) => g !== id));
    else onChange([...selected, id]);
  }

  async function addGuest(e) {
    e.preventDefault();
    const errs = {};
    if (!form.name.trim()) errs.name = 'Enter the guest’s name';
    if (!form.gender) errs.gender = 'Choose a gender';
    const age = Number(form.age);
    if (form.age === '' || !Number.isInteger(age) || age < 0 || age > 120) errs.age = 'Enter an age from 0 to 120';
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setSaving(true);
    setSaveError('');
    try {
      const guest = await api.createGuest({ name: form.name.trim(), gender: form.gender, age });
      setGuests((g) => [...(g || []), guest]);
      if (selected.length < max) onChange([...selected, guest.id]);
      setForm(EMPTY);
    } catch (err) {
      setErrors(err.fieldErrors || {});
      setSaveError(err.message);
    } finally {
      setSaving(false);
    }
  }

  if (loadError) return <Alert>Could not load your guests: {loadError}</Alert>;
  if (!guests) return <Spinner label="Loading your guests…" />;

  return (
    <div className="b-guests">
      {guests.length > 0 ? (
        <fieldset className="b-guests__list" disabled={disabled}>
          <legend className="visually-hidden">Who is travelling</legend>
          {guests.map((g) => {
            const checked = selected.includes(g.id);
            return (
              <label key={g.id} className={`m-chip ${checked ? 'is-checked' : ''}`}>
                <input
                  type="checkbox"
                  checked={checked}
                  onChange={() => toggle(g.id)}
                  disabled={!checked && selected.length >= max}
                />
                <span>
                  {g.name}
                  <span className="s-muted">
                    {' '}
                    · {g.age}
                    {g.gender ? `, ${g.gender.toLowerCase()}` : ''}
                  </span>
                </span>
              </label>
            );
          })}
        </fieldset>
      ) : (
        <p className="s-muted">You haven’t saved any guests yet. Add the people who are travelling below.</p>
      )}
      <p className="m-field__hint">
        {selected.length} of up to {max} guest{max === 1 ? '' : 's'} selected
      </p>

      <form className="b-guests__add" onSubmit={addGuest} noValidate aria-label="Add a guest">
        <Alert onDismiss={() => setSaveError('')}>{saveError}</Alert>
        <div className="m-grid-3">
          <Field id="guest-name" label="Name" required error={errors.name}>
            <input
              id="guest-name"
              maxLength={100}
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              disabled={disabled || saving}
            />
          </Field>
          <Field id="guest-gender" label="Gender" required error={errors.gender}>
            <select
              id="guest-gender"
              value={form.gender}
              onChange={(e) => setForm({ ...form, gender: e.target.value })}
              disabled={disabled || saving}
            >
              <option value="">Select…</option>
              <option value="FEMALE">Female</option>
              <option value="MALE">Male</option>
              <option value="OTHER">Other</option>
            </select>
          </Field>
          <Field id="guest-age" label="Age" required error={errors.age}>
            <input
              id="guest-age"
              type="number"
              min="0"
              max="120"
              value={form.age}
              onChange={(e) => setForm({ ...form, age: e.target.value })}
              disabled={disabled || saving}
            />
          </Field>
        </div>
        <button type="submit" className="m-btn m-btn--secondary m-btn--sm" disabled={disabled || saving}>
          {saving ? 'Saving…' : 'Add guest'}
        </button>
      </form>
    </div>
  );
}
