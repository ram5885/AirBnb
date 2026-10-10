import { useEffect, useRef, useState } from 'react';
import { useLocation, useSearchParams } from 'react-router-dom';
import Navbar from '../components/Navbar.jsx';
import SearchForm from '../components/search/SearchForm.jsx';
import HotelCard from '../components/search/HotelCard.jsx';
import { useAmenityCatalog } from '../components/manager/AmenityPicker.jsx';
import { Alert, EmptyState, Pagination, Spinner, formatDate } from '../components/manager/ui.jsx';
import { hasDates, nightsBetween, readStay } from '../components/search/stay.js';
import { useAuth } from '../context/AuthContext.jsx';
import * as api from '../api.js';
import '../styles/manager.css';
import '../styles/home.css';
import '../styles/search.css';

const PAGE_SIZE = 12;

/** Guest home: hotel search form, and results for the search held in the URL. */
export default function Home() {
  const { user } = useAuth();
  const location = useLocation();
  const [params, setParams] = useSearchParams();
  const stay = readStay(params);
  const page = Number(params.get('page') || 0);
  const searched = !!stay.city && hasDates(stay);
  const { categories } = useAmenityCatalog();

  const [results, setResults] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const resultsRef = useRef(null);

  // The navbar's search pill sends guests here asking to focus the city field.
  useEffect(() => {
    if (location.state?.focusSearch) document.getElementById('search-city')?.focus();
  }, [location.state]);

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
  }, [key]);

  function runSearch(next) {
    setParams({ ...next, numberOfRooms: String(next.numberOfRooms) });
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

  return (
    <div className="home">
      <Navbar />

      <section className="s-hero">
        <div className="container">
          {user && !searched && <p className="home__greeting">Welcome back, {user.name.split(' ')[0]} 👋</p>}
          <h1 className="s-hero__title">{searched ? 'Find your stay' : 'Where are you going?'}</h1>
          <SearchForm initial={stay} onSearch={runSearch} busy={loading} />
        </div>
      </section>

      <main className="container home__main" ref={resultsRef}>
        {!searched && (
          <EmptyState title="Search hotels across India">
            Pick a city, your dates and the number of rooms to see hotels with availability.
          </EmptyState>
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
                {stay.numberOfRooms} room{stay.numberOfRooms === 1 ? '' : 's'}
              </p>
            </div>

            <Alert>{error}</Alert>
            {loading && <Spinner label="Searching hotels…" />}

            {!loading && results && results.content.length === 0 && (
              <EmptyState title="No hotels available">
                No hotel in {stay.city} has {stay.numberOfRooms} room{stay.numberOfRooms === 1 ? '' : 's'} free for every
                night of this stay. Try other dates, fewer rooms or another city.
              </EmptyState>
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
