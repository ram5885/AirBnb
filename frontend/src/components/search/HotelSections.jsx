import { useEffect, useState } from 'react';
import { getHotelSections } from '../../api.js';
import { Alert, EmptyState, Spinner, formatDate } from '../manager/ui.jsx';
import Carousel from './Carousel.jsx';
import HotelCard from './HotelCard.jsx';
import { nightsBetween } from './stay.js';

/**
 * "Stays in {city}" rows: hotels that can take `stay` (dates + rooms), grouped by city, from
 * GET /api/v1/hotels/sections. Refetches only when the stay changes (debounced while dates are edited);
 * scrolling the rows never refetches. `stay` is null while the search bar's dates are invalid.
 */
export default function HotelSections({ stay, categories, onPickCity }) {
  const [state, setState] = useState({ sections: null, error: '', loading: false });
  const [attempt, setAttempt] = useState(0);
  const key = stay ? `${stay.checkinDate}|${stay.checkoutDate}|${stay.numberOfRooms}` : '';

  useEffect(() => {
    if (!stay) return undefined;
    let cancelled = false;
    setState((s) => ({ ...s, loading: true, error: '' }));
    const timer = setTimeout(() => {
      getHotelSections(stay)
        .then((sections) => !cancelled && setState({ sections, error: '', loading: false }))
        .catch((e) => !cancelled && setState({ sections: null, error: e.message, loading: false }));
    }, 300);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, attempt]);

  if (!stay) {
    return <p className="s-muted sections__note">Choose valid dates in the search bar to see available stays.</p>;
  }
  if (state.error) {
    return (
      <Alert>
        Could not load stays: {state.error}{' '}
        <button type="button" className="m-link-btn" onClick={() => setAttempt((a) => a + 1)}>
          Try again
        </button>
      </Alert>
    );
  }
  if (!state.sections) return <Spinner label="Finding available stays…" />;

  const nights = nightsBetween(stay.checkinDate, stay.checkoutDate);
  const when = `${formatDate(stay.checkinDate)} – ${formatDate(stay.checkoutDate)} · ${stay.numberOfRooms} room${
    stay.numberOfRooms === 1 ? '' : 's'
  }`;

  if (state.sections.length === 0) {
    return (
      <EmptyState title="No stays available for these dates">
        No hotel has {stay.numberOfRooms} room{stay.numberOfRooms === 1 ? '' : 's'} free for every night of {when.split(' · ')[0]}.
        Try other dates or fewer rooms.
      </EmptyState>
    );
  }

  return (
    <div className={state.loading ? 'sections sections--stale' : 'sections'} aria-busy={state.loading}>
      {state.sections.map((s) => {
        const linkStay = { city: s.city, state: s.state, ...stay };
        return (
          <Carousel
            key={s.locationId}
            id={`stays-${s.locationId}`}
            variant="stays"
            title={`Stays in ${s.city}`}
            subtitle={`${s.availableHotels} available in ${s.state} · ${when}`}
            titleAction={
              <button
                type="button"
                className="carousel__more"
                onClick={() => onPickCity({ city: s.city, state: s.state })}
                aria-label={`See all ${s.availableHotels} available stays in ${s.city}`}
              >
                <svg viewBox="0 0 32 32" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="3.5" aria-hidden="true">
                  <path d="M4 16h22M18 8l8 8-8 8" />
                </svg>
              </button>
            }
          >
            {s.hotels.map((h) => (
              <li key={h.id}>
                <HotelCard hotel={h} stay={linkStay} nights={nights} categories={categories} />
              </li>
            ))}
          </Carousel>
        );
      })}
    </div>
  );
}
