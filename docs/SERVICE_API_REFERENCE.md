# Service API Reference

All public endpoints are reached through the API gateway (`http://localhost:8080/api/...`, or same-origin
`http://localhost:3000/api/...` through the frontend's nginx). **Paths, request bodies, response bodies and
error bodies are unchanged from the monolith**, so the React app works without code changes. Detailed
per-endpoint behaviour (status codes, validation rules) is documented in the Javadoc of each controller.

Conventions:
* Auth: `Authorization: Bearer <JWT>` from login/signup. Roles: `GUEST`, `HOTEL_MANAGER`.
* Errors: `{"error": "message"}`; validation errors add `"fieldErrors": {"field": "message"}`.
* Gateway-level errors use the same shape: `401` not logged in / session expired, `403` wrong role,
  `404` unknown path, `503` a service is unavailable, `504` a service timed out.
* Paging: `?page=0&size=N` → `{"content", "page", "size", "totalElements", "totalPages"}`.
* Dates: ISO `yyyy-MM-dd`; check-in inclusive, check-out exclusive.

## Public API (through the gateway)

### auth-service
| Method | Path | Access | Notes |
|---|---|---|---|
| POST | `/api/auth/signup` | public | `{name, email, password, role}` → 201 `{id, name, email, roles, message, token}`; 409 duplicate email |
| POST | `/api/auth/login` | public | `{email, password}` → 200 same shape; 401 `Invalid email or password` |

### catalog-service
| Method | Path | Access | Notes |
|---|---|---|---|
| GET | `/api/locations[?state=]` | public | `[{id, city, state}]` |
| GET | `/api/locations/states` | public | `["Andhra Pradesh", ...]` |
| GET | `/api/locations/destinations` | public | cities with listed hotels, photo URL + credit |
| GET | `/api/locations/{locationId}/photo` | public | image bytes |
| GET | `/api/v1/amenities` | public | amenity catalog grouped by category |
| GET | `/api/v1/hotels/search` | public | `city`, `state?`, `checkinDate`, `checkoutDate`, `numberOfRooms`, optional combinable filters `amenities` (comma list), `minPrice`, `maxPrice`, `minRating`, `page`, `size` |
| GET | `/api/v1/hotels/sections` | public | home page "Stays in {city}" rows |
| GET | `/api/v1/hotels/{hotelId}[?stay]` | public | public hotel page; with stay params adds availability per room type |
| GET | `/api/v1/hotels/{hotelId}/rooms/{roomId}[?stay]` | public | room-type page with night-by-night availability |
| GET | `/api/v1/hotels/{hotelId}/photos/{photoId}` | public | image bytes (visible hotels only) |
| GET | `/api/v1/hotels/{hotelId}/rooms/{roomId}/photos/{photoId}` | public | image bytes |
| POST / GET | `/api/v1/admin/hotels` | HOTEL_MANAGER | create (multipart `data` + `photos`) / list own hotels |
| GET / PUT / DELETE | `/api/v1/admin/hotels/{hotelId}` | owner | details / edit (multipart) / soft delete (409 while bookings hold rooms) |
| PATCH | `/api/v1/admin/hotels/{hotelId}/status` | owner | `{active}` |
| GET | `/api/v1/admin/hotels/{hotelId}/photos/{photoId}` | owner | image bytes |
| POST / GET | `/api/v1/admin/hotels/{hotelId}/rooms` | owner | create (multipart) / list room types |
| GET / PUT / DELETE | `/api/v1/admin/hotels/{hotelId}/rooms/{roomId}` | owner | room type details / edit / soft delete |
| GET | `/api/v1/admin/hotels/{hotelId}/rooms/{roomId}/photos/{photoId}` | owner | image bytes |
| GET / PATCH | `/api/v1/admin/hotels/{hotelId}/rooms/{roomId}/inventory` | owner | day-by-day inventory / close-open dates, surge factor |

### booking-service
| Method | Path | Access | Notes |
|---|---|---|---|
| GET | `/api/v1/admin/dashboard` | HOTEL_MANAGER | `{totalHotels, activeHotels, bookingsByStatus}` (hotel counts from catalog) |
| GET / POST | `/api/v1/guests` | GUEST | saved travelling guests |
| PUT | `/api/v1/guests/{guestId}` | GUEST, owner | edit |
| POST | `/api/v1/bookings` | GUEST | `{hotelId, roomId, checkInDate, checkOutDate, roomsCount, guestIds}` → 201 RESERVED (price computed on the server) |
| GET | `/api/v1/bookings?scope=all\|upcoming\|pending\|past\|cancelled` | GUEST | My Bookings |
| GET | `/api/v1/bookings/{bookingId}` | GUEST, owner | details incl. `canPay`, `canCancel`, `holdExpiresAt` |
| PATCH | `/api/v1/bookings/{bookingId}/guests` | GUEST, owner | `{guestIds}` |
| PATCH | `/api/v1/bookings/{bookingId}/cancel` | GUEST, owner | until the day before check-in; paid bookings refunded in full |
| POST | `/api/v1/payments/{bookingId}` | GUEST, owner | create/reuse Razorpay order → `{keyId, orderId, amount (paise), currency, ...}` |
| POST | `/api/v1/payments/verify` | GUEST, owner | `{bookingId, razorpayOrderId, razorpayPaymentId, razorpaySignature}` → 200 / 402 / 409 with `{confirmed, message, booking}` |
| POST | `/api/v1/payments/{bookingId}/failure` | GUEST, owner | record a failed Checkout attempt (never confirms) |
| POST | `/api/v1/payments/{bookingId}/pay-at-hotel` | GUEST, owner | confirm now, pay at the hotel |
| POST | `/api/v1/payments/webhook` | public (HMAC `X-Razorpay-Signature`) | Razorpay events, idempotent by `x-razorpay-event-id` |
| GET | `/api/v1/admin/bookings` | HOTEL_MANAGER | `hotelId`, `status`, `startDate`, `endDate`, `reference` (BK000123) filters |
| GET | `/api/v1/admin/bookings/{bookingId}` | owner of the hotel | details incl. booker name/email, guests, payment |
| PATCH | `/api/v1/admin/bookings/{bookingId}/accept` | owner | RESERVED → ACCEPTED |
| PATCH | `/api/v1/admin/bookings/{bookingId}/cancel` | owner | releases rooms, refunds paid bookings |
| PATCH | `/api/v1/admin/bookings/{bookingId}/payment-collected` | owner | pay-at-hotel payment received |

### review-service
| Method | Path | Access | Notes |
|---|---|---|---|
| GET | `/api/v1/hotels/{hotelId}/reviews?sort=recent\|highest\|lowest` | public | paged review cards (first name only) |
| GET | `/api/v1/hotels/{hotelId}/reviews/summary` | public | `{hotelId, averageRating, totalReviews, ratingDistribution}` |
| GET | `/api/v1/hotels/{hotelId}/reviews/eligibility` | GUEST | the caller's confirmed stays and whether each can be reviewed |
| POST | `/api/v1/hotels/{hotelId}/reviews` | GUEST | `{bookingId, rating 1-5, comment 10-1000}`; only completed, paid stays; once per booking |

## Internal API (service to service)

Never routed by the gateway (`/internal/**` is denied at the edge) and served only on the internal Docker network.
Every call must carry `X-Internal-Token: ${INTERNAL_API_TOKEN}` (checked in constant time by `InternalApiFilter`),
otherwise `401`. Contracts are Java records owned by each side (`*Dtos`, `*View`); there is no shared library.

### auth-service
| Method | Path | Response |
|---|---|---|
| GET | `/internal/users/{id}` | `{id, name, email, roles}` or 404 (used by the gateway's session check, booking and review services) |
| POST | `/internal/users/lookup` | body `{ids: [...]}` → `[{id, name, email, roles}]` (unknown ids absent) |

### catalog-service
| Method | Path | Behaviour |
|---|---|---|
| POST | `/internal/inventory/holds` | body `{holdKey (uuid), hotelId, roomId, checkInDate, checkOutDate, roomsCount, guestCount}` → `{holdKey, status: HELD, bookingId, hotelId, hotelOwnerId, hotelName, city, state, roomId, roomType, roomCapacity, checkInDate, checkOutDate, roomsCount, amount}`. Locks hotel/room/inventory rows, checks capacity and every night, takes the rooms. **Idempotent per holdKey.** 400/404/409 with the user-facing messages of the monolith. |
| POST | `/internal/inventory/holds/{holdKey}/attach` | body `{bookingId}` → hold; 409 if attached to another booking |
| POST | `/internal/inventory/holds/{holdKey}/release` | → `{status: "RELEASED" \| "NOT_FOUND"}`; idempotent |
| POST | `/internal/inventory/holds/{holdKey}/reacquire` | → `{reacquired: true \| false}`; re-takes released rooms if the stay is still bookable; true if still held |
| GET | `/internal/inventory/holds/unattached?olderThanMinutes=10` | HELD holds no booking has claimed (reconciliation) |
| POST | `/internal/hotels/summaries` | body `{hotelIds, roomIds}` → `{hotels: [{id, name, city, state, ownerId, active, deleted, coverPhotoUrl}], rooms: [{id, hotelId, type, capacity, deleted}]}` |
| GET | `/internal/hotels/{hotelId}/public` | `{id, name}` if active and not deleted, else 404 `Hotel not found` |
| GET | `/internal/owners/{ownerId}/hotel-counts` | `{totalHotels, activeHotels}` |
| PUT | `/internal/hotels/ratings` | body `[{hotelId, ratingSum, ratingCount}]` → `{updated}`; absolute values, idempotent upsert |

### booking-service
| Method | Path | Response |
|---|---|---|
| GET | `/internal/bookings/{bookingId}?userId=` | `{bookingId, userId, hotelId, roomType, checkInDate, checkOutDate, status, paymentStatus}`; 404 `Booking {id} not found` if it isn't that user's |
| GET | `/internal/bookings/confirmed?userId=&hotelId=` | the user's CONFIRMED bookings at the hotel, latest check-out first |

## Health

Every service: `GET /actuator/health` (and `/actuator/health/readiness`, used by the Docker health checks).
Only `health` is exposed. Frontend container: `GET /healthz`.
