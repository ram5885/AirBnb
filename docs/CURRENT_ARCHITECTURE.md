# Current Architecture (monolith, before migration)

This describes the application as found on branch `Doker_implimentation` (commit `fa3c6dc`) before the
microservices migration. The monolith has since been removed from the working tree; its code is in git history (commit `fa3c6dc`).

## Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot **4.0.7** (Spring Framework 7, Hibernate 7, Jackson 3), Maven wrapper (Maven 3.9.16) |
| Security | `spring-boot-starter-security` + OAuth2 resource server; HS256 JWT issued by the app itself |
| Persistence | Spring Data JPA, **all reads are native SQL** (`repository/query/*Queries.java`), writes via JPA |
| Schema management | `spring.jpa.hibernate.ddl-auto=update` (no Flyway/Liquibase) |
| Database | PostgreSQL (local, `jdbc:postgresql://localhost:5432/AirBnb`, user `ram`, empty password) |
| Frontend | React 18 + Vite 5 + react-router 6 (then `AirBnb/frontend`, now `frontend/`), dev proxy `/api -> http://localhost:8080` |
| Payments | Razorpay Standard Checkout, server-side REST calls (`RestClient`), HMAC-SHA256 signature checks |
| Images | Stored in PostgreSQL as `bytea` (`hotel_photo`, `room_photo`, `city_photo`) |
| Scheduling | `@Scheduled` job expiring unpaid reservations every minute |

Single deployable: `AirBnbApplication` on port 8080. No Docker files existed.

## Modules (packages under `com.HotelManagement.AirBnb`)

| Package | Responsibility | Tables |
|---|---|---|
| `auth` | Signup/login, BCrypt passwords, JWT issuing, `CurrentUserService` (user from token, checks user still exists and email matches) | `app_user`, `user_roles` |
| `hotel` | Hotel CRUD (manager), room types, photos (bytea), amenity catalog (enum), date-wise inventory (365 days generated per room type), surge pricing, open/close dates, manager dashboard | `hotel`, `hotel_photo`, `room`, `room_photo`, `inventory` |
| `location` | Indian city/state list (CSV seeded at startup), city photos (Wikimedia, seeded at startup), "destinations" | `location`, `city_photo` |
| `search` | Public search with combinable filters (amenities, price range, min rating), home-page sections, public hotel/room pages, public photos | reads hotel/room/inventory/photos/location/**hotel_review** |
| `guest` | Saved travelling companions of a guest account | `guest` |
| `booking` | Create booking (row-locked inventory reservation), My Bookings, guest cancel, update guests, manager list/accept/cancel/mark-paid, hold expiry job | `booking`, `booking_guest` |
| `payment` | Razorpay order, verify (signature + server fetch + amount/currency check + capture), failure recording, webhooks (idempotent by event id), refunds, pay-at-hotel | `payment`, `payment_webhook_event` |
| `review` | Reviews for completed, paid stays (one per booking), list/sort, summary, eligibility | `hotel_review` |
| `demodata` | Optional CSV demo-data seeder (`demo` profile) using the services | — |
| `shared` | Exceptions + `GlobalExceptionHandler` (`{"error": ...}` bodies), `PageResponse`, CORS, `WriteRepository` | — |

## API surface (all consumed by the React app through `src/api.js`)

| Area | Endpoints | Access |
|---|---|---|
| Auth | `POST /api/auth/signup`, `POST /api/auth/login` | public |
| Locations | `GET /api/locations[?state]`, `/api/locations/states`, `/api/locations/destinations`, `/api/locations/{id}/photo` | public |
| Amenities | `GET /api/v1/amenities` | public |
| Public hotels | `GET /api/v1/hotels/search`, `/sections`, `/{id}`, `/{id}/rooms/{roomId}`, `/{id}/photos/{p}`, `/{id}/rooms/{r}/photos/{p}` | public |
| Reviews | `GET /api/v1/hotels/{id}/reviews`, `/summary` (public); `GET .../eligibility`, `POST .../reviews` (GUEST) | mixed |
| Manager | `/api/v1/admin/dashboard`, `/api/v1/admin/hotels/**` (hotels, rooms, photos, inventory), `/api/v1/admin/bookings/**` | HOTEL_MANAGER |
| Guest | `/api/v1/guests/**`, `/api/v1/bookings/**`, `/api/v1/payments/**` | GUEST |
| Webhook | `POST /api/v1/payments/webhook` | public, HMAC-authenticated |

## Data model and relationships

```
app_user 1─* user_roles
app_user 1─* hotel (owner_id) ── location (location_id)
hotel 1─* hotel_photo, hotel 1─* room 1─* room_photo
hotel/room 1─* inventory (unique hotel_id, room_id, date; CHECK 0 <= booked_count <= total_count)
app_user 1─* guest
booking *─1 hotel, *─1 room, *─1 app_user, 1─1 payment (payment_id), *─* guest (booking_guest)
hotel_review *─1 hotel, *─1 app_user, 1─1 booking (unique booking_id)
location 1─1 city_photo
```

Data found in the local `AirBnb` database at analysis time (row estimates): 2 users, 3 hotels, 5 room types,
1,825 inventory rows, 252 locations, 201 city photos, 7 photos, 0 bookings/payments/reviews.

## Transaction boundaries (the important part)

All of these are **single local ACID transactions** using PostgreSQL row locks:

1. **Create booking** — `hotel FOR SHARE` → `room FOR SHARE` → `inventory FOR UPDATE` (date order) → check
   closed/free rooms → increment `booked_count` → insert booking with the server-computed amount.
2. **Cancel booking (guest/manager)** — `booking FOR UPDATE` → refund through Razorpay if paid (failure aborts
   everything) → decrement inventory → status `CANCELLED`.
3. **Hold expiry job** — per booking: `booking FOR UPDATE` → re-check → release inventory → `EXPIRED`.
4. **Payment verify / webhook** — `booking FOR UPDATE` → signature check → fetch payment from Razorpay →
   amount/currency/order check → capture → booking `CONFIRMED`; for a late payment on an `EXPIRED` booking
   the inventory is re-acquired, otherwise the payment is refunded.
5. **Hotel / room delete** — refused while bookings hold inventory for upcoming stays (query on `booking`).
6. **Review create** — `booking FOR UPDATE` + unique `booking_id`.

## Cross-module coupling found in the code

| From → To | How |
|---|---|
| booking → hotel | JPA `@ManyToOne Hotel/Room`, inventory row locks, `HotelPhotoRepository` for cover photos |
| booking ↔ payment | `Booking.payment` 1-1, payment state drives booking state, `PaymentService` locks bookings, cancellation refunds |
| hotel → booking | `HotelService.delete/dashboard`, `RoomService.delete` query `BookingRepository` |
| search → review | `HotelQueries.MEETS_MIN_RATING` SQL subquery on `hotel_review`; `ReviewRepository.ratingsByHotelIds` |
| review → booking, hotel, auth | eligibility needs booking + payment status; list query joins `app_user`, `booking`, `room` |
| location → hotel | `LocationQueries.DESTINATIONS` joins `hotel`, `room`, `hotel_photo` |
| every module → auth | `CurrentUserService.require(jwt)` returns the `User` entity |
| everything → shared | exceptions, `PageResponse`, error handler |

## Main risks of the current architecture

- One deployable: any failure or redeploy takes down search, booking and payments together.
- Schema managed by `ddl-auto=update`: no migration history, no repeatable schema, risky on refactors.
- Database credentials in `application.properties` (local user, empty password); JWT secret has a dev default.
- Every module reads every table; there are no ownership boundaries to enforce.
- No container/deployment definition; setup depends on a hand-configured local PostgreSQL.
- Tests require a hand-created `AirBnb_test` database on the developer machine.
