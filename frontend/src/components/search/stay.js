// The guest's search ("stay") lives in the URL so it survives navigation between results,
// hotel and room pages, and page refreshes. Limits mirror the backend (BookingService).
import { addDays, isoDate } from '../manager/ui.jsx';

export const MAX_NIGHTS = 30;
export const MAX_ROOMS = 10;

export function readStay(searchParams) {
  return {
    city: searchParams.get('city') || '',
    state: searchParams.get('state') || '',
    checkinDate: searchParams.get('checkinDate') || '',
    checkoutDate: searchParams.get('checkoutDate') || '',
    numberOfRooms: Number(searchParams.get('numberOfRooms')) || 1,
    ...readFilters(searchParams),
  };
}

// Optional search filters, kept in the URL next to the stay (so they survive refresh, back/forward and the
// hotel/room pages). Empty values mean "not filtered". Mirrors GET /api/v1/hotels/search.
export const EMPTY_FILTERS = { amenities: [], minPrice: '', maxPrice: '', minRating: '' };
export const RATING_OPTIONS = ['4.5', '4.0', '3.5', '3.0'];

export function readFilters(searchParams) {
  const amenities = (searchParams.get('amenities') || '').split(',').map((a) => a.trim()).filter(Boolean);
  return {
    amenities: [...new Set(amenities)],
    minPrice: searchParams.get('minPrice') || '',
    maxPrice: searchParams.get('maxPrice') || '',
    minRating: searchParams.get('minRating') || '',
  };
}

export function pickFilters(stay) {
  return { amenities: stay.amenities || [], minPrice: stay.minPrice || '', maxPrice: stay.maxPrice || '', minRating: stay.minRating || '' };
}

export function activeFilterCount(f) {
  return f.amenities.length + (f.minPrice !== '' || f.maxPrice !== '' ? 1 : 0) + (f.minRating !== '' ? 1 : 0);
}

/** Field errors for the filter panel (same rules as the backend), or {} when valid. */
export function validateFilters(f) {
  const errors = {};
  const min = f.minPrice === '' ? null : Number(f.minPrice);
  const max = f.maxPrice === '' ? null : Number(f.maxPrice);
  if (min !== null && (!Number.isFinite(min) || min < 0)) errors.minPrice = 'Enter 0 or more';
  else if (min !== null && min > MAX_PRICE) errors.minPrice = `At most ₹${MAX_PRICE.toLocaleString('en-IN')}`;
  if (max !== null && (!Number.isFinite(max) || max < 0)) errors.maxPrice = 'Enter 0 or more';
  else if (max !== null && max > MAX_PRICE) errors.maxPrice = `At most ₹${MAX_PRICE.toLocaleString('en-IN')}`;
  if (!errors.minPrice && !errors.maxPrice && min !== null && max !== null && min > max)
    errors.maxPrice = 'Maximum price must be at least the minimum price';
  return errors;
}

export const MAX_PRICE = 1000000;

/** Whether a bookable room option (its nightly price and amenities, plus the hotel's) matches the filters. */
export function roomMatchesFilters(f, nightlyPrice, roomAmenities, hotelAmenities) {
  if (f.minPrice !== '' && Number(nightlyPrice) < Number(f.minPrice)) return false;
  if (f.maxPrice !== '' && Number(nightlyPrice) > Number(f.maxPrice)) return false;
  const offered = new Set([...(roomAmenities || []), ...(hotelAmenities || [])]);
  return f.amenities.every((a) => offered.has(a));
}

export function hasDates(stay) {
  return !!(stay.checkinDate && stay.checkoutDate);
}

/** Query string for links/API calls; empty values are left out. */
export function stayQuery(stay, extra = {}) {
  const qs = new URLSearchParams();
  Object.entries({ ...stay, ...extra }).forEach(([k, v]) => {
    if (Array.isArray(v)) {
      if (v.length) qs.set(k, v.join(','));
    } else if (v !== '' && v !== null && v !== undefined) qs.set(k, v);
  });
  const s = qs.toString();
  return s ? `?${s}` : '';
}

/** Only the date/room part, for hotel and room APIs. */
export function stayParams(stay) {
  return hasDates(stay)
    ? { checkinDate: stay.checkinDate, checkoutDate: stay.checkoutDate, numberOfRooms: stay.numberOfRooms }
    : undefined;
}

export function nightsBetween(checkin, checkout) {
  return Math.round((new Date(`${checkout}T00:00:00`) - new Date(`${checkin}T00:00:00`)) / 86400000);
}

export function defaultDates() {
  const today = isoDate(new Date());
  return { checkinDate: addDays(today, 1), checkoutDate: addDays(today, 3) };
}

export function validateDates(stay) {
  const errors = {};
  const today = isoDate(new Date());
  if (!stay.checkinDate) errors.checkinDate = 'Choose a check-in date';
  else if (stay.checkinDate < today) errors.checkinDate = "Check-in can't be in the past";
  if (!stay.checkoutDate) errors.checkoutDate = 'Choose a check-out date';
  else if (stay.checkinDate && stay.checkoutDate <= stay.checkinDate) errors.checkoutDate = 'Check-out must be after check-in';
  else if (stay.checkinDate && nightsBetween(stay.checkinDate, stay.checkoutDate) > MAX_NIGHTS)
    errors.checkoutDate = `A stay can be at most ${MAX_NIGHTS} nights`;
  const rooms = Number(stay.numberOfRooms);
  if (!Number.isInteger(rooms) || rooms < 1 || rooms > MAX_ROOMS) errors.numberOfRooms = `Choose 1 to ${MAX_ROOMS} rooms`;
  return errors;
}
