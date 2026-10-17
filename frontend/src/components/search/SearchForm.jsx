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
 * onStayChange (optional) receives { checkinDate, checkoutDate, numberOfRooms } whenever those change,
 * so the page can follow the dates being chosen before a search is submitted.
 */
export default function SearchForm({ initial, onSearch, busy, onStayChange }) {
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

  useEffect(() => {
    onStayChange?.({ checkinDate: form.checkinDate, checkoutDate: form.checkoutDate, numberOfRooms: form.numberOfRooms });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [form.checkinDate, form.checkoutDate, form.numberOfRooms]);

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
      <div className={`s-field s-field--where ${errors.city ? 's-field--error' : ''}`}>
        <label htmlFor="search-city">Where</label>
        <Autocomplete
          id="search-city"
          options={options}
          value={selectedLabel}
          onSelect={(v) => {
            setForm((f) => ({ ...f, location: v ? byLabel.get(v) : null }));
            setErrors((er) => ({ ...er, city: undefined }));
          }}
          placeholder="Search destinations"
          loading={locations.loading}
          loadError={locations.error}
          emptyText="No matching city"
          invalid={!!errors.city}
        />
        {errors.city && <span className="s-field__error">{errors.city}</span>}
      </div>
      <StayFields
        idPrefix="search"
        pill
        value={form}
        errors={errors}
        onChange={(next) => {
          setForm(next);
          setErrors((er) => ({ ...er, checkinDate: undefined, checkoutDate: undefined, numberOfRooms: undefined }));
        }}
      />
      <button type="submit" className="s-search__button" disabled={busy} aria-label={busy ? 'Searching' : 'Search'}>
        <svg viewBox="0 0 32 32" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="4" aria-hidden="true">
          <circle cx="13" cy="13" r="9" />
          <path d="M20 20l8 8" strokeLinecap="round" />
        </svg>
      </button>
    </form>
  );
}
