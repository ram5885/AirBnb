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
  };
}

export function hasDates(stay) {
  return !!(stay.checkinDate && stay.checkoutDate);
}

/** Query string for links/API calls; empty values are left out. */
export function stayQuery(stay, extra = {}) {
  const qs = new URLSearchParams();
  Object.entries({ ...stay, ...extra }).forEach(([k, v]) => {
    if (v !== '' && v !== null && v !== undefined) qs.set(k, v);
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
