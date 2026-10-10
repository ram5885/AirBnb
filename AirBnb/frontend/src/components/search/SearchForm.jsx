import { useEffect, useMemo, useState } from 'react';
import { getAllLocations } from '../../api.js';
import Autocomplete from '../manager/Autocomplete.jsx';
import StayFields from './StayFields.jsx';
import { defaultDates, validateDates } from './stay.js';

let locationsCache = null;

function label(loc) {
  return `${loc.city}, ${loc.state}`;
}

/**
 * City + dates + rooms. Cities come from the Location table and are shown as "City, State"
 * (city names can repeat across states); only a suggestion can be selected.
 */
export default function SearchForm({ initial, onSearch, busy }) {
  const [locations, setLocations] = useState({ list: locationsCache || [], loading: !locationsCache, error: '' });
  const [form, setForm] = useState(() => ({
    location: initial.city ? { city: initial.city, state: initial.state } : null,
    checkinDate: initial.checkinDate || defaultDates().checkinDate,
    checkoutDate: initial.checkoutDate || defaultDates().checkoutDate,
    numberOfRooms: initial.numberOfRooms || 1,
  }));
  const [errors, setErrors] = useState({});

  useEffect(() => {
    if (locationsCache) return;
    getAllLocations()
      .then((list) => {
        locationsCache = list;
        setLocations({ list, loading: false, error: '' });
      })
      .catch((e) => setLocations({ list: [], loading: false, error: `Could not load cities: ${e.message}` }));
  }, []);

  // Keep the form in step when the URL (e.g. browser back) changes the search.
  useEffect(() => {
    setForm((f) => ({
      ...f,
      location: initial.city ? { city: initial.city, state: initial.state } : f.location,
      checkinDate: initial.checkinDate || f.checkinDate,
      checkoutDate: initial.checkoutDate || f.checkoutDate,
      numberOfRooms: initial.numberOfRooms || f.numberOfRooms,
    }));
  }, [initial.city, initial.state, initial.checkinDate, initial.checkoutDate, initial.numberOfRooms]);

  const byLabel = useMemo(() => {
    const m = new Map();
    locations.list.forEach((l) => m.set(label(l), l));
    return m;
  }, [locations.list]);
  const options = useMemo(() => [...byLabel.keys()].sort((a, b) => a.localeCompare(b)), [byLabel]);

  // A URL may name a city without its state; match it to the location table when unambiguous.
  const selectedLabel = form.location
    ? form.location.state
      ? label(form.location)
      : (() => {
          const matches = locations.list.filter((l) => l.city.toLowerCase() === form.location.city.toLowerCase());
          return matches.length === 1 ? label(matches[0]) : null;
        })()
    : null;

  function submit(e) {
    e.preventDefault();
    const errs = validateDates(form);
    const loc = selectedLabel ? byLabel.get(selectedLabel) : null;
    if (!loc) errs.city = 'Choose a city from the suggestions';
    setErrors(errs);
    if (Object.keys(errs).length) return;
    onSearch({
      city: loc.city,
      state: loc.state,
      checkinDate: form.checkinDate,
      checkoutDate: form.checkoutDate,
      numberOfRooms: form.numberOfRooms,
    });
  }

  return (
    <form className="s-search" onSubmit={submit} noValidate role="search" aria-label="Search hotels">
      <div className={`s-field s-field--wide ${errors.city ? 's-field--error' : ''}`}>
        <label htmlFor="search-city">Where</label>
        <Autocomplete
          id="search-city"
          options={options}
          value={selectedLabel}
          onSelect={(v) => {
            setForm((f) => ({ ...f, location: v ? byLabel.get(v) : null }));
            setErrors((er) => ({ ...er, city: undefined }));
          }}
          placeholder="Search a city"
          loading={locations.loading}
          loadError={locations.error}
          emptyText="No matching city"
          invalid={!!errors.city}
        />
        {errors.city && <span className="s-field__error">{errors.city}</span>}
      </div>
      <StayFields
        idPrefix="search"
        value={form}
        errors={errors}
        onChange={(next) => {
          setForm(next);
          setErrors((er) => ({ ...er, checkinDate: undefined, checkoutDate: undefined, numberOfRooms: undefined }));
        }}
      />
      <button type="submit" className="s-search__button" disabled={busy}>
        <svg viewBox="0 0 32 32" width="16" height="16" fill="currentColor" aria-hidden="true">
          <path d="M13 24a11 11 0 118-18.7A11 11 0 0113 24zm8.6-3l6.7 6.7-1.6 1.6-6.7-6.7z" />
        </svg>
        <span>{busy ? 'Searching…' : 'Search'}</span>
      </button>
    </form>
  );
}
