// Thin wrapper around the Spring Boot API (see AuthController and the /api/v1 controllers).
// In dev, Vite proxies /api/* to http://localhost:8080 (see vite.config.js).
const BASE_URL = '/api/auth';

// Bearer token from login/signup, set by AuthContext. onUnauthorized lets AuthContext
// log the user out when the backend rejects an expired/invalid token.
let authToken = null;
let onUnauthorized = null;

export function configureAuth({ token, handleUnauthorized }) {
  authToken = token || null;
  if (handleUnauthorized !== undefined) onUnauthorized = handleUnauthorized;
}

async function handleResponse(res) {
  const raw = await res.text();
  let data = {};
  if (raw) {
    try {
      data = JSON.parse(raw);
    } catch {
      data = { message: raw };
    }
  }

  if (!res.ok) {
    // GlobalExceptionHandler returns { error: "..." } (plus fieldErrors for validation);
    // anything unhandled falls back to Spring's default { message } shape.
    const message = data.error || data.message || `Request failed (${res.status})`;
    const err = new Error(message);
    err.status = res.status;
    err.fieldErrors = data.fieldErrors || {};
    err.body = data;
    throw err;
  }

  return data;
}

function post(path, body) {
  return fetch(`${BASE_URL}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }).then(handleResponse);
}

export function login({ email, password }) {
  return post('/login', { email, password });
}

export function signup({ name, email, password, role }) {
  return post('/signup', { name, email, password, role });
}

// ------------------------------------------------------------ authenticated API

function query(params) {
  if (!params) return '';
  const qs = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') qs.set(k, v);
  });
  const s = qs.toString();
  return s ? `?${s}` : '';
}

async function request(path, { method = 'GET', json, formData, params } = {}) {
  const headers = {};
  if (authToken) headers.Authorization = `Bearer ${authToken}`;
  let body;
  if (json !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(json);
  } else if (formData) {
    body = formData; // browser sets the multipart boundary
  }

  let res;
  try {
    res = await fetch(`${path}${query(params)}`, { method, headers, body });
  } catch {
    const err = new Error('Could not reach the server. Check your connection and try again.');
    err.status = 0;
    throw err;
  }
  if (res.status === 401 && onUnauthorized) onUnauthorized();
  return handleResponse(res);
}

/** Multipart body: "data" = JSON payload, "photos" = new image files. */
function multipart(data, photos = []) {
  const fd = new FormData();
  fd.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }));
  photos.forEach((file) => fd.append('photos', file, file.name));
  return fd;
}

/** Fetches an authenticated image and returns an object URL (caller must revoke it). */
export async function fetchImage(url) {
  const res = await fetch(url, { headers: authToken ? { Authorization: `Bearer ${authToken}` } : {} });
  if (res.status === 401 && onUnauthorized) onUnauthorized();
  if (!res.ok) throw new Error('Could not load image');
  return URL.createObjectURL(await res.blob());
}

// Locations & amenities (public)
export const getStates = () => request('/api/locations/states');
export const getCities = (state) => request('/api/locations', { params: { state } });
export const getAmenities = () => request('/api/v1/amenities');

// Hotels
const ADMIN = '/api/v1/admin';
export const getDashboard = () => request(`${ADMIN}/dashboard`);
export const listHotels = (params) => request(`${ADMIN}/hotels`, { params });
export const getHotel = (id) => request(`${ADMIN}/hotels/${id}`);
export const createHotel = (data, photos) =>
  request(`${ADMIN}/hotels`, { method: 'POST', formData: multipart(data, photos) });
export const updateHotel = (id, data, photos) =>
  request(`${ADMIN}/hotels/${id}`, { method: 'PUT', formData: multipart(data, photos) });
export const setHotelActive = (id, active) =>
  request(`${ADMIN}/hotels/${id}/status`, { method: 'PATCH', json: { active } });
export const deleteHotel = (id) => request(`${ADMIN}/hotels/${id}`, { method: 'DELETE' });

// Room types
export const listRooms = (hotelId) => request(`${ADMIN}/hotels/${hotelId}/rooms`);
export const getRoom = (hotelId, roomId) => request(`${ADMIN}/hotels/${hotelId}/rooms/${roomId}`);
export const createRoom = (hotelId, data, photos) =>
  request(`${ADMIN}/hotels/${hotelId}/rooms`, { method: 'POST', formData: multipart(data, photos) });
export const updateRoom = (hotelId, roomId, data, photos) =>
  request(`${ADMIN}/hotels/${hotelId}/rooms/${roomId}`, { method: 'PUT', formData: multipart(data, photos) });
export const deleteRoom = (hotelId, roomId) =>
  request(`${ADMIN}/hotels/${hotelId}/rooms/${roomId}`, { method: 'DELETE' });

// Inventory (dates are ISO yyyy-MM-dd, endDate inclusive)
export const getInventory = (hotelId, roomId, startDate, endDate) =>
  request(`${ADMIN}/hotels/${hotelId}/rooms/${roomId}/inventory`, { params: { startDate, endDate } });
export const updateInventory = (hotelId, roomId, body) =>
  request(`${ADMIN}/hotels/${hotelId}/rooms/${roomId}/inventory`, { method: 'PATCH', json: body });

// Bookings
export const listBookings = (params) => request(`${ADMIN}/bookings`, { params });
export const getBooking = (id) => request(`${ADMIN}/bookings/${id}`);
export const acceptBooking = (id) => request(`${ADMIN}/bookings/${id}/accept`, { method: 'PATCH' });
export const cancelBooking = (id) => request(`${ADMIN}/bookings/${id}/cancel`, { method: 'PATCH' });
export const markPaymentCollected = (id) => request(`${ADMIN}/bookings/${id}/payment-collected`, { method: 'PATCH' });

// Guest hotel search (public; dates are ISO yyyy-MM-dd, check-out exclusive)
export const getAllLocations = () => request('/api/locations');
export const searchHotels = ({ city, state, checkinDate, checkoutDate, numberOfRooms, page, size }) =>
  request('/api/v1/hotels/search', { params: { city, state, checkinDate, checkoutDate, numberOfRooms, page, size } });
export const getPublicHotel = (hotelId, stay) => request(`/api/v1/hotels/${hotelId}`, { params: stay });
export const getPublicRoom = (hotelId, roomId, stay) =>
  request(`/api/v1/hotels/${hotelId}/rooms/${roomId}`, { params: stay });

// Guest bookings, saved guests and Razorpay payments (guest accounts)
export const listMyGuests = () => request('/api/v1/guests');
export const createGuest = (guest) => request('/api/v1/guests', { method: 'POST', json: guest });
export const createBooking = (body) => request('/api/v1/bookings', { method: 'POST', json: body });
export const listMyBookings = (params) => request('/api/v1/bookings', { params });
export const getMyBooking = (id) => request(`/api/v1/bookings/${id}`);
export const updateBookingGuests = (id, guestIds) =>
  request(`/api/v1/bookings/${id}/guests`, { method: 'PATCH', json: { guestIds } });
export const cancelMyBooking = (id) => request(`/api/v1/bookings/${id}/cancel`, { method: 'PATCH' });
export const createPaymentOrder = (bookingId) => request(`/api/v1/payments/${bookingId}`, { method: 'POST' });
export const payAtHotel = (bookingId) => request(`/api/v1/payments/${bookingId}/pay-at-hotel`, { method: 'POST' });
export const reportPaymentFailure = (bookingId, body) =>
  request(`/api/v1/payments/${bookingId}/failure`, { method: 'POST', json: body });

/**
 * Verify returns 200 when the booking is confirmed and 402/409 (with a body) when it isn't;
 * both carry { confirmed, message, booking }, so the body is returned either way.
 */
export async function verifyPayment(body) {
  try {
    return await request('/api/v1/payments/verify', { method: 'POST', json: body });
  } catch (e) {
    if (e.body?.booking) return e.body;
    throw e;
  }
}
