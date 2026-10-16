import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import Navbar from '../components/Navbar.jsx';
import SearchForm from '../components/search/SearchForm.jsx';
import HotelCard from '../components/search/HotelCard.jsx';
import Destinations from '../components/search/Destinations.jsx';
import HotelSections from '../components/search/HotelSections.jsx';
import { useAmenityCatalog } from '../components/manager/AmenityPicker.jsx';
import { Alert, EmptyState, Pagination, Spinner, formatDate } from '../components/manager/ui.jsx';
import { defaultDates, hasDates, nightsBetween, readStay, validateDates } from '../components/search/stay.js';
import { useAuth } from '../context/AuthContext.jsx';
import * as api from '../api.js';
import '../styles/manager.css';
import '../styles/home.css';
import '../styles/search.css';

const PAGE_SIZE = 12;

/** Cities with listed hotels (GET /api/locations/destinations), loaded once per visit with a retry. */
function useDestinations() {
  const [state, setState] = useState({ list: null, error: '' });
  const load = useCallback(() => {
    setState({ list: null, error: '' });
    api
      .getDestinations()
      .then((list) => setState({ list, error: '' }))
      .catch((e) => setState({ list: null, error: e.message }));
  }, []);
  useEffect(load, [load]);
  return { ...state, reload: load };
}

/**
 * Guest home. Without a search: "Explore stays by city" and "Stays in {city}" rows for the dates and rooms
 * in the search bar. With a search in the URL: results for it, using the same availability rule.
 */
export default function Home() {
  const { user } = useAuth();
  const [params, setParams] = useSearchParams();
  const stay = readStay(params);
  const page = Number(params.get('page') || 0);
  const searched = !!stay.city && hasDates(stay);
  const { categories } = useAmenityCatalog();
  const destinations = useDestinations();

  // Dates/rooms currently in the search bar (not yet submitted): city cards and sections follow them.
  const [draft, setDraft] = useState(() => ({
    checkinDate: stay.checkinDate || defaultDates().checkinDate,
    checkoutDate: stay.checkoutDate || defaultDates().checkoutDate,
    numberOfRooms: stay.numberOfRooms,
  }));
  const draftValid = Object.keys(validateDates(draft)).length === 0;

  const [results, setResults] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const resultsRef = useRef(null);

  const key = params.toString();
  useEffect(() => {
    if (!searched) {
      setResults(null);
      setError('');
      return undefined;
    }
    let cancelled = false;
    setLoading(true);
    setError('');
    api
      .searchHotels({ ...stay, page, size: PAGE_SIZE })
      .then((r) => !cancelled && setResults(r))
      .catch((e) => {
        if (!cancelled) {
          setResults(null);
          setError(e.message);
        }
      })
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, attempt]);

  function runSearch(next) {
    setParams({ ...next, numberOfRooms: String(next.numberOfRooms) });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  /** City card / section arrow: search that city keeping the search bar's dates and rooms. */
  function searchCity(city) {
    const dates = draftValid ? draft : { ...defaultDates(), numberOfRooms: draft.numberOfRooms };
    runSearch({ ...city, checkinDate: dates.checkinDate, checkoutDate: dates.checkoutDate, numberOfRooms: dates.numberOfRooms });
  }

  function goToPage(p) {
    const next = new URLSearchParams(params);
    next.set('page', p);
    setParams(next);
    resultsRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  const nights = searched ? nightsBetween(stay.checkinDate, stay.checkoutDate) : 0;
  const stayForLinks = searched
    ? {
        city: stay.city,
        state: stay.state,
        checkinDate: stay.checkinDate,
        checkoutDate: stay.checkoutDate,
        numberOfRooms: stay.numberOfRooms,
      }
    : {};
  const rooms = `${stay.numberOfRooms} room${stay.numberOfRooms === 1 ? '' : 's'}`;
  // How many hotels the searched city lists at all (any dates), to explain an empty result.
  const listedInCity = destinations.list?.find(
    (d) => d.city.toLowerCase() === stay.city.toLowerCase() && (!stay.state || d.state.toLowerCase() === stay.state.toLowerCase()),
  )?.hotelCount;

  const cityRow = (
    <Destinations
      destinations={destinations.list}
      error={destinations.error}
      onRetry={destinations.reload}
      onPick={searchCity}
    />
  );

  return (
    <div className="home">
      <Navbar />

      <section className="s-hero">
        <div className="container">
          {user && !searched && <p className="home__greeting">Welcome back, {user.name.split(' ')[0]} 👋</p>}
          <h1 className="s-hero__title">{searched ? 'Find your stay' : 'Where are you going?'}</h1>
          <SearchForm initial={stay} onSearch={runSearch} busy={loading} onStayChange={setDraft} />
        </div>
      </section>

      <main className="container home__main" ref={resultsRef}>
        {!searched && (
          <>
            {cityRow}
            {destinations.list?.length === 0 ? (
              <EmptyState title="No stays listed yet">
                Hotels appear here once a host lists one with at least one room type.
              </EmptyState>
            ) : (
              destinations.list && (
                <HotelSections stay={draftValid ? draft : null} categories={categories} onPickCity={searchCity} />
              )
            )}
          </>
        )}

        {searched && (
          <>
            <div className="s-results-header">
              <h2>
                {results ? `${results.totalElements} hotel${results.totalElements === 1 ? '' : 's'}` : 'Hotels'} in{' '}
                {stay.city}
                {stay.state ? `, ${stay.state}` : ''}
              </h2>
              <p className="s-muted">
                {formatDate(stay.checkinDate)} – {formatDate(stay.checkoutDate)} · {nights} night{nights === 1 ? '' : 's'} ·{' '}
                {rooms}
              </p>
            </div>

            {error && (
              <Alert>
                {error}{' '}
                <button type="button" className="m-link-btn" onClick={() => setAttempt((a) => a + 1)}>
                  Try again
                </button>
              </Alert>
            )}
            {loading && <Spinner label="Searching hotels…" />}

            {!loading && results && results.content.length === 0 && (
              <>
                {listedInCity ? (
                  <EmptyState
                    title="No availability for these dates"
                    action={
                      <button
                        type="button"
                        className="m-btn m-btn--secondary m-btn--sm"
                        onClick={() => document.getElementById('search-in')?.focus()}
                      >
                        Change dates
                      </button>
                    }
                  >
                    {stay.city} has {listedInCity} hotel{listedInCity === 1 ? '' : 's'} listed, but none has {rooms} free for
                    every night from {formatDate(stay.checkinDate)} to {formatDate(stay.checkoutDate)}. Try other dates,
                    fewer rooms or another city below.
                  </EmptyState>
                ) : (
                  <EmptyState title={`No hotels listed in ${stay.city} yet`}>
                    No host has listed a hotel in {stay.city} so far. Try one of the cities below.
                  </EmptyState>
                )}
                {cityRow}
              </>
            )}

            {!loading && results && results.content.length > 0 && (
              <>
                <div className="s-grid">
                  {results.content.map((h) => (
                    <HotelCard key={h.id} hotel={h} stay={stayForLinks} nights={nights} categories={categories} />
                  ))}
                </div>
                <Pagination page={results.page} totalPages={results.totalPages} onChange={goToPage} />
              </>
            )}
          </>
        )}
      </main>

      <footer className="home__footer">
        <div className="container">
          <p>Prices and availability are checked again when you book.</p>
        </div>
      </footer>
    </div>
  );
}
