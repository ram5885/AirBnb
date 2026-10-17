# AirBnb Clone — Project Guide

Everything needed to set up, run and understand the project in one place: setup, database access, every service
and its API, how the services talk to each other, and how Docker is configured.

**Stack:** Java 21 · Spring Boot 4.0.7 · Spring Cloud Gateway 2025.1 · PostgreSQL 18 · React 18 + Vite 5 · nginx ·
Docker Compose · Razorpay (test mode).

---

## Contents

1. [Setup and run](#1-setup-and-run)
2. [Connecting to the database](#2-connecting-to-the-database)
3. [Services and their APIs](#3-services-and-their-apis)
4. [How the services are interconnected](#4-how-the-services-are-interconnected)
5. [How Docker is configured](#5-how-docker-is-configured)

---

## 1. Setup and run

### 1.1 Prerequisites

| To… | You need |
|---|---|
| Run the application | **Docker Desktop** (with Compose v2), ≥ 4 GB memory for Docker (Settings → Resources) |
| Run the automated tests | Java 21+, Maven 3.9+, a local PostgreSQL (e.g. `brew install postgresql@18`) |
| Develop the frontend with hot reload | Node.js 18+ (22 recommended) |
| Use the helper scripts | `python3`, `openssl` (both preinstalled on macOS) |

### 1.2 Project layout

```
AirBnb/                          repository root (run every command from here)
├── compose.yaml                 the whole stack
├── compose.db.yaml              optional: publish PostgreSQL on 127.0.0.1:5433
├── compose.e2e.yaml             end-to-end test overlay (Razorpay test double)
├── .env.example                 configuration template → copy to .env (git-ignored)
├── frontend/                    React app (Vite) + Dockerfile + nginx.conf
├── services/
│   ├── api-gateway/             Spring Cloud Gateway (entry point)
│   ├── auth-service/            accounts, login, JWT
│   ├── catalog-service/         hotels, rooms, inventory, search
│   ├── booking-service/         bookings, guests, payments
│   ├── review-service/          reviews and ratings
│   └── pom.xml                  builds/tests all services at once
├── docker/postgres/init/        creates one schema + login per service (first start)
├── docker/razorpay-mock/        Razorpay test double (e2e tests only)
├── scripts/                     init-env, test-all, seed-demo-data, migrate-legacy-data, e2e/
├── backups/                     database dumps (git-ignored)
└── docs/
```

### 1.3 First-time setup

```bash
cd /Users/ram/AirBnb_Docker/AirBnb

# 1. Configuration: creates .env with random secrets (never overwrites an existing .env)
./scripts/init-env.sh

# 2. Optional: enable online payments with Razorpay TEST keys (Dashboard → Account & Settings → API Keys, test mode)
#    edit .env:  RAZORPAY_KEY_ID=rzp_test_xxxxxxxx   RAZORPAY_KEY_SECRET=xxxxxxxx
#    Without keys, everything works except online payment ("pay at hotel" still works).

# 3. Build and start (first build ≈ 3–5 minutes, then ≈ 30 s to become healthy)
docker compose up -d --build

# 4. Check: every line must say "healthy"
docker compose ps
```

Then open:

| URL | What |
|---|---|
| **http://localhost:3000** | the web application |
| http://localhost:8080/api/... | the API gateway directly (Postman / curl) |
| http://localhost:8080/actuator/health | gateway health |

### 1.4 Optional data

```bash
python3 scripts/seed-demo-data.py
# 10 demo users (password Demo@12345, e.g. manager1@example.com / guest1@example.com),
# 54 hotels, 161 room types, 161 bookings. Safe to run again (creates only what's missing).
```

The original monolith's data has already been imported. To import it again into an *empty* stack, from the dump:
`LEGACY_DUMP=backups/legacy-AirBnb-20261018023011.dump ./scripts/migrate-legacy-data.sh`.

### 1.5 Daily commands

```bash
docker compose up -d                         # start (or apply compose/.env changes)
docker compose ps                            # status
docker compose logs -f                       # all logs   (… logs -f booking-service  for one)
docker compose restart catalog-service       # restart one service
docker compose up -d --build catalog-service # rebuild one service after a code change
docker compose stop                          # stop, keep containers and data
docker compose down                          # remove containers; DATA IS KEPT
```

> ⚠️ `docker compose down -v` and `docker volume rm airbnb_pgdata` **delete all data**. Back up first (§2.5).

### 1.6 Frontend development with hot reload

```bash
docker compose up -d          # backend must be running (gateway on localhost:8080)
cd frontend
npm ci                        # first time
npm run dev                   # http://localhost:5173  — /api is proxied to http://localhost:8080
```

### 1.7 Tests

```bash
./scripts/test-all.sh         # all 121 service tests + frontend build (uses local DB airbnb_services_test)
./scripts/e2e/run-e2e.sh      # 60 end-to-end checks on a separate throwaway stack (ports 3100/8180/9100)
cd services/booking-service && mvn verify    # one service
```

### 1.8 Logging in

| Account type | How |
|---|---|
| Existing (migrated) users | their old email and password still work (password hashes were copied unchanged) |
| New users | Sign up in the app as **Guest** (book stays) or **Hotel manager** (list hotels) |
| Demo users | after seeding: `manager1@example.com` … / `guest1@example.com` …, password `Demo@12345` |

---

## 2. Connecting to the database

### 2.1 Layout

One PostgreSQL 18 container, one database **`airbnb`**, **one schema per service**, each owned by its own login:

| Schema | Login (owner) | Password variable in `.env` | Tables |
|---|---|---|---|
| `auth` | `auth_svc` | `AUTH_DB_PASSWORD` | `app_user`, `user_roles` |
| `catalog` | `catalog_svc` | `CATALOG_DB_PASSWORD` | `location`, `city_photo`, `hotel`, `hotel_photo`, `room`, `room_photo`, `inventory`, `inventory_hold`, `hotel_rating` |
| `booking` | `booking_svc` | `BOOKING_DB_PASSWORD` | `guest`, `payment`, `payment_webhook_event`, `booking`, `booking_guest` |
| `review` | `review_svc` | `REVIEW_DB_PASSWORD` | `hotel_review` |
| *(all)* | `airbnb_admin` (superuser) | `POSTGRES_USER` / `POSTGRES_PASSWORD` | everything |

A service login can **only** see its own schema (`catalog_svc` querying `auth.app_user` → *permission denied*).
Use a service login to look at one service's data safely, the superuser to see everything. Each schema also has a
`flyway_schema_history` table (applied migrations).

Get the passwords from your `.env` (`cat .env`, or `grep DB_PASSWORD .env`).

### 2.2 Quickest: psql inside the container (no setup)

```bash
docker compose exec postgres psql -U airbnb_admin -d airbnb
```

```sql
\dn                                   -- list schemas
\dt catalog.*                         -- tables of one service
SELECT hotel_id, name, active FROM catalog.hotel;
SELECT booking_id, booking_status, hold_status FROM booking.booking ORDER BY booking_id DESC LIMIT 10;
\q
```

### 2.3 From the Mac (DBeaver, pgAdmin, TablePlus, IntelliJ, psql)

The database is not published to the host by default. Start it with the port overlay (localhost only):

```bash
docker compose -f compose.yaml -f compose.db.yaml up -d postgres
```

| Setting | Value |
|---|---|
| Host | `127.0.0.1` |
| Port | `5433` (change with `DB_HOST_PORT` in `.env`) |
| Database | `airbnb` |
| User / password | `airbnb_admin` / `POSTGRES_PASSWORD` — or a service login, e.g. `catalog_svc` / `CATALOG_DB_PASSWORD` |
| JDBC URL | `jdbc:postgresql://127.0.0.1:5433/airbnb` (add `?currentSchema=catalog` to default to one schema) |
| SSL | off |

```bash
psql -h 127.0.0.1 -p 5433 -U airbnb_admin -d airbnb
```

To unpublish the port again: `docker compose up -d postgres` (without the overlay). Port 5433 is used so it doesn't
clash with a PostgreSQL installed on the Mac (5432).

### 2.4 How the services connect (inside Docker)

Each service gets, from `compose.yaml`:

```
DB_URL=jdbc:postgresql://postgres:5432/airbnb?currentSchema=<schema>
DB_USERNAME=<schema>_svc
DB_PASSWORD=${<SCHEMA>_DB_PASSWORD}
```

`postgres` is the Docker service name (never `localhost` inside containers). On startup each service runs **Flyway**
(`services/<name>/src/main/resources/db/migration/V*.sql`) on its schema, then Hibernate *validates* the entities
against it. Schema changes = a new `V<n>__description.sql` file in the owning service.

### 2.5 Backup and restore

```bash
set -a; . ./.env; set +a
# backup
docker compose exec -T postgres pg_dump -U "$POSTGRES_USER" -d airbnb -Fc > backups/airbnb-$(date +%Y%m%d%H%M).dump
# restore into an empty stack (fresh volume)
docker compose exec -T postgres pg_restore -U "$POSTGRES_USER" -d airbnb --clean --if-exists < backups/<file>.dump
```

---

## 3. Services and their APIs

### 3.1 Overview

| Service | Container port | Published | Schema | Responsibility |
|---|---|---|---|---|
| frontend | 80 | **3000** | – | React app; nginx proxies `/api/*` to the gateway |
| api-gateway | 8080 | **127.0.0.1:8080** | – | single entry point: routing, JWT, roles, CORS, timeouts |
| auth-service | 8081 | – | `auth` | accounts, passwords, login, JWT issuing |
| catalog-service | 8082 | – | `catalog` | hotels, room types, photos, cities, amenities, inventory/pricing, public search |
| booking-service | 8083 | – | `booking` | bookings, saved guests, Razorpay payments/refunds/webhooks, manager booking actions, dashboard |
| review-service | 8084 | – | `review` | reviews and ratings of completed stays |
| postgres | 5432 | – (5433 with overlay) | all | database |

### 3.2 Common conventions

* **Base URL:** `http://localhost:3000/api` (through the frontend) or `http://localhost:8080/api` (gateway).
* **Authentication:** `Authorization: Bearer <token>`; the token comes from signup/login and is valid 24 h.
  It carries the user id, email and role (`GUEST` or `HOTEL_MANAGER`).
* **Access levels used below:** *public* (no token), *GUEST*, *MANAGER* (`HOTEL_MANAGER`), *owner* (the manager who
  owns the hotel / the guest who owns the booking — others get `404`, so ids can't be probed).
* **Errors:** always JSON `{"error": "message"}`; validation errors also have `"fieldErrors": {"field": "message"}`.
  `400` invalid input · `401` not logged in / session expired · `402` payment failed · `403` wrong role ·
  `404` not found · `409` conflict (not available, already cancelled…) · `502` Razorpay error ·
  `503` a service is down · `504` timeout.
* **Pages:** `?page=0&size=N` → `{"content": [...], "page", "size", "totalElements", "totalPages"}`.
* **Dates:** `yyyy-MM-dd`; check-in inclusive, check-out exclusive. Stays: 1–30 nights, 1–10 rooms, check-in ≥ today.
* **Prices:** INR; computed on the server from inventory (base price × surge factor per night). Client amounts are ignored.

---

### 3.3 api-gateway (port 8080)

**Purpose:** the only API entry point. It does not contain business logic.

* **Routing** (first match wins):

  | Path | → Service |
  |---|---|
  | `/api/auth/**` | auth-service |
  | `/api/v1/hotels/*/reviews`, `/api/v1/hotels/*/reviews/**` | review-service |
  | `/api/v1/bookings/**`, `/api/v1/guests/**`, `/api/v1/payments/**`, `/api/v1/admin/bookings/**`, `/api/v1/admin/dashboard` | booking-service |
  | `/api/locations/**`, `/api/v1/amenities`, `/api/v1/hotels/**`, `/api/v1/admin/hotels/**` | catalog-service |
  | `/internal/**` | **never routed** (denied) |
* **Security:** validates the JWT signature (HS256, `JWT_SECRET`) and expiry; applies the role rules of the API tables
  below; checks the token's account still exists in auth-service (cached 60 s; `401 "Your session has expired"`
  otherwise); strips any client-sent `X-Internal-Token` header.
* **CORS:** allows `CORS_ALLOWED_ORIGINS` (default `localhost:3000`, `localhost:5173`). The bundled frontend is
  same-origin and needs no CORS.
* **Timeouts:** connect 3 s; response 30 s (60 s for booking/payment and catalog routes — payments and photo uploads).
* **Errors:** service down → `503`, timeout → `504`, unknown path → `404`, all as `{"error": ...}`.
* **Health:** `GET /actuator/health`.

---

### 3.4 auth-service (port 8081, schema `auth`)

**Purpose:** user accounts and login. Passwords are stored as BCrypt hashes. Issues the JWT every other service trusts.
**Depends on:** nothing.

| Method | Path | Access | Request | Response |
|---|---|---|---|---|
| POST | `/api/auth/signup` | public | `{"name","email","password","role":"GUEST"\|"HOTEL_MANAGER"}` | `201 {"id","name","email","roles":[...],"message":"Signup successful","token"}` · `409` email already registered |
| POST | `/api/auth/login` | public | `{"email","password"}` | `200` same shape, `"message":"Login successful"` · `401 "Invalid email or password"` |

Example:
```bash
curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' \
     -d '{"email":"guest1@example.com","password":"Demo@12345"}'
```

**Internal API** (other services only, see §4):

| Method | Path | Response |
|---|---|---|
| GET | `/internal/users/{id}` | `{"id","name","email","roles"}` or `404` |
| POST | `/internal/users/lookup` | body `{"ids":[1,2]}` → list of users (unknown ids skipped) |

---

### 3.5 catalog-service (port 8082, schema `catalog`)

**Purpose:** everything about what can be booked: hotels, room types, photos (stored in the DB), the Indian
city/state list, amenities, the date-wise inventory (365 days per room type, rooms free per night, price per night),
and the public search with filters. Also the *authority on availability*: only catalog changes room counts.
**Depends on:** nothing (other services call it). **On startup:** loads 252 cities and 201 city photos (if missing).

#### Public: locations and amenities

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/api/locations` | public | all cities `[{"id","city","state"}]`; `?state=Kerala` filters by state |
| GET | `/api/locations/states` | public | list of states |
| GET | `/api/locations/destinations` | public | cities that have listed hotels: `{"locationId","city","state","hotelCount","photoUrl","photoCredit"}` |
| GET | `/api/locations/{locationId}/photo` | public | city photo (image) |
| GET | `/api/v1/amenities` | public | amenity catalog by category: `[{"code","label","amenities":[{"code":"WIFI","label":"Wi-Fi"}]}]` |

#### Public: search and hotel pages

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/api/v1/hotels/search` | public | **Search.** Required: `city`, `checkinDate`, `checkoutDate`, `numberOfRooms` (`state` if the city name exists in several states). Optional, combinable (AND): `amenities=WIFI,TV` (all required, in one bookable room type or the hotel), `minPrice`, `maxPrice` (average nightly price per room), `minRating` (1–5), `page`, `size` (≤ 50). Returns hotels with a room type free on every night: `{"id","name","city","state","photoUrls","amenities","availableRoomTypes","lowestNightlyPrice","lowestTotalPrice","averageRating","reviewCount"}` |
| GET | `/api/v1/hotels/sections` | public | home page "Stays in {city}" rows for a stay: `checkinDate`, `checkoutDate`, `numberOfRooms`, `maxCities` (≤ 50), `perCity` (≤ 20) |
| GET | `/api/v1/hotels/{hotelId}` | public | hotel page: name, city, address, landmark, amenities, photo URLs, room types (no private contact data). With `checkinDate`/`checkoutDate`/`numberOfRooms` each room type gets `availability {available, availableRooms, totalPrice, averageNightlyPrice}` |
| GET | `/api/v1/hotels/{hotelId}/rooms/{roomId}` | public | room type page; with stay parameters adds a night-by-night list `{date, price, availableRooms, bookable}` |
| GET | `/api/v1/hotels/{hotelId}/photos/{photoId}` | public | hotel photo (only for active hotels) |
| GET | `/api/v1/hotels/{hotelId}/rooms/{roomId}/photos/{photoId}` | public | room photo |

Example:
```bash
curl -s "http://localhost:8080/api/v1/hotels/search?city=Bhubaneswar&state=Odisha&checkinDate=2026-11-01&checkoutDate=2026-11-03&numberOfRooms=1&amenities=WIFI&maxPrice=5000"
```

#### Manager: hotels

| Method | Path | Access | Description |
|---|---|---|---|
| POST | `/api/v1/admin/hotels` | MANAGER | create a hotel. **multipart/form-data**: part `data` (JSON `{"name","state","city","address","landmark","contactPerson","phoneNumber" (10-digit Indian mobile),"email","amenities":["WIFI",...],"active":true}`) and up to 10 `photos` (JPEG/PNG/WebP, ≤ 5 MB each) → `201` |
| GET | `/api/v1/admin/hotels` | MANAGER | own hotels, newest first; `?active=true\|false`, `page`, `size` |
| GET | `/api/v1/admin/hotels/{hotelId}` | owner | full details incl. contact info and photos |
| PUT | `/api/v1/admin/hotels/{hotelId}` | owner | edit (multipart like create; `removedPhotoIds` in `data` removes photos) |
| PATCH | `/api/v1/admin/hotels/{hotelId}/status` | owner | `{"active": false}` hides it from guests and stops new bookings |
| DELETE | `/api/v1/admin/hotels/{hotelId}` | owner | soft delete → `204`; `409` while bookings still hold rooms for upcoming stays |
| GET | `/api/v1/admin/hotels/{hotelId}/photos/{photoId}` | owner | photo (also for inactive hotels) |

#### Manager: room types and inventory

| Method | Path | Access | Description |
|---|---|---|---|
| POST | `/api/v1/admin/hotels/{hotelId}/rooms` | owner | create a room type (multipart `data`: `{"type","basePrice","totalCount" (1–500),"capacity" (1–20),"amenities"}` + `photos`). Generates 365 days of inventory → `201` |
| GET | `/api/v1/admin/hotels/{hotelId}/rooms` | owner | room types of the hotel |
| GET | `/api/v1/admin/hotels/{hotelId}/rooms/{roomId}` | owner | one room type |
| PUT | `/api/v1/admin/hotels/{hotelId}/rooms/{roomId}` | owner | edit; new count/price is pushed into future inventory (`409` if the count drops below rooms already booked) |
| DELETE | `/api/v1/admin/hotels/{hotelId}/rooms/{roomId}` | owner | soft delete; `409` while upcoming bookings hold rooms |
| GET | `/api/v1/admin/hotels/{hotelId}/rooms/{roomId}/photos/{photoId}` | owner | photo |
| GET | `/api/v1/admin/hotels/{hotelId}/rooms/{roomId}/inventory?startDate=&endDate=` | owner | per day (≤ 366 days): `{date,totalCount,bookedCount,available,closed,surgeFactor,price}` |
| PATCH | `/api/v1/admin/hotels/{hotelId}/rooms/{roomId}/inventory` | owner | `{"startDate","endDate","closed"?:true,"surgeFactor"?:1.5}` (0.50–5.00; future dates only) → `{"updatedDays"}` |

#### Internal API (for booking-service and review-service)

| Method | Path | Purpose |
|---|---|---|
| POST | `/internal/inventory/holds` | take rooms for a booking: `{"holdKey","hotelId","roomId","checkInDate","checkOutDate","roomsCount","guestCount"}` → hold incl. server-computed `amount`, hotel owner, names, room capacity. Idempotent per `holdKey` |
| POST | `/internal/inventory/holds/{holdKey}/attach` | link the hold to the saved booking `{"bookingId"}` |
| POST | `/internal/inventory/holds/{holdKey}/release` | give the rooms back → `{"status":"RELEASED"\|"NOT_FOUND"}` (idempotent) |
| POST | `/internal/inventory/holds/{holdKey}/reacquire` | re-take released rooms (late payment) → `{"reacquired":true\|false}` |
| GET | `/internal/inventory/holds/unattached?olderThanMinutes=10` | holds no booking claimed (clean-up job) |
| POST | `/internal/hotels/summaries` | `{"hotelIds":[...],"roomIds":[...]}` → current names, cities, owner, active/deleted, cover photo, room type/capacity |
| GET | `/internal/hotels/{hotelId}/public` | `200` if guests can see the hotel, else `404` |
| GET | `/internal/owners/{ownerId}/hotel-counts` | `{"totalHotels","activeHotels"}` |
| PUT | `/internal/hotels/ratings` | `[{"hotelId","ratingSum","ratingCount"}]` → rating copy used by search |

---

### 3.6 booking-service (port 8083, schema `booking`)

**Purpose:** the reservation lifecycle and its money — saved travelling guests, bookings, Razorpay payments,
refunds, webhooks, pay at hotel, unpaid-hold expiry, manager booking management, manager dashboard.
**Depends on:** catalog-service (rooms, prices, hotel facts), auth-service (user name/email), Razorpay API.

**Booking statuses:** `RESERVED` (rooms held 15 min for payment) → `ACCEPTED` (manager approved; no expiry) →
`CONFIRMED` (paid / pay at hotel) · `CANCELLED` · `EXPIRED` (unpaid hold timed out).
**Payment statuses:** `PENDING`, `CONFIRMED`, `FAILED` (retry allowed), `CANCELLED`, `REFUND_PENDING`, `REFUNDED`;
method `ONLINE` or `PAY_AT_HOTEL`.

#### Guest: saved guests

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/api/v1/guests` | GUEST | own saved guests `[{"id","name","gender","age"}]` |
| POST | `/api/v1/guests` | GUEST | `{"name","gender":"MALE"\|"FEMALE"\|"OTHER","age" (0–120)}` → `201` |
| PUT | `/api/v1/guests/{guestId}` | owner | edit |

#### Guest: bookings ("My Bookings")

| Method | Path | Access | Description |
|---|---|---|---|
| POST | `/api/v1/bookings` | GUEST | `{"hotelId","roomId","checkInDate","checkOutDate","roomsCount","guestIds":[...]}` → `201` RESERVED with server price. `409` not available / hotel not accepting bookings, `400` too many guests for the rooms |
| GET | `/api/v1/bookings?scope=all\|upcoming\|pending\|past\|cancelled` | GUEST | own bookings (paged) |
| GET | `/api/v1/bookings/{bookingId}` | owner | details incl. `reference` (BK000123), `guests`, `amount`, `status`, `paymentStatus`, `holdExpiresAt`, `canPay`, `canCancel`, `canEditGuests` |
| PATCH | `/api/v1/bookings/{bookingId}/guests` | owner | `{"guestIds":[...]}` (active bookings before check-in) |
| PATCH | `/api/v1/bookings/{bookingId}/cancel` | owner | until the day before check-in; paid bookings are refunded in full |

#### Guest: payments

| Method | Path | Access | Description |
|---|---|---|---|
| POST | `/api/v1/payments/{bookingId}` | owner | create (or reuse) the Razorpay order → `{"bookingId","keyId","orderId","amount" (paise),"currency","displayAmount","name","description","customerName","customerEmail"}` (never the secret). `503` if Razorpay isn't configured |
| POST | `/api/v1/payments/verify` | owner | after Checkout succeeds: `{"bookingId","razorpayOrderId","razorpayPaymentId","razorpaySignature"}`. Server checks the signature, fetches the payment from Razorpay, checks order/amount/currency, captures → `200 {"confirmed":true,"message","booking"}` · `402` payment failed · `409` mismatch / not complete · `400` bad signature |
| POST | `/api/v1/payments/{bookingId}/failure` | owner | record a failed attempt `{"razorpayOrderId","razorpayPaymentId"?,"code"?,"description"?}` (never confirms) |
| POST | `/api/v1/payments/{bookingId}/pay-at-hotel` | owner | confirm now, pay at the hotel |
| POST | `/api/v1/payments/webhook` | public, verified by `X-Razorpay-Signature` HMAC | Razorpay events `payment.captured`, `order.paid`, `payment.failed`, `refund.processed`, `refund.failed`; duplicates (same `x-razorpay-event-id`) ignored |

Payment flow from the browser: `POST /payments/{id}` → open Razorpay Checkout with `keyId`/`orderId` → on success
`POST /payments/verify` → booking `CONFIRMED`. The browser's word alone never confirms a booking.

#### Manager: bookings and dashboard

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/api/v1/admin/dashboard` | MANAGER | `{"totalHotels","activeHotels","bookingsByStatus":{"RESERVED":n,...}}` |
| GET | `/api/v1/admin/bookings` | MANAGER | bookings of own hotels; filters `hotelId`, `status`, `startDate`/`endDate` (stays overlapping), `reference` (`BK000123` or `123`), `page`, `size` |
| GET | `/api/v1/admin/bookings/{bookingId}` | owner of the hotel | details incl. booker name/email, guests, payment |
| PATCH | `/api/v1/admin/bookings/{bookingId}/accept` | owner | RESERVED → ACCEPTED |
| PATCH | `/api/v1/admin/bookings/{bookingId}/cancel` | owner | cancel (until check-out), refunds paid bookings |
| PATCH | `/api/v1/admin/bookings/{bookingId}/payment-collected` | owner | pay-at-hotel booking paid at the front desk |

**Background jobs:** expire unpaid RESERVED bookings after 15 min (every minute) · retry room releases that couldn't
reach catalog (every minute) · release orphan holds (every 5 min).

**Internal API** (for review-service):

| Method | Path | Response |
|---|---|---|
| GET | `/internal/bookings/{bookingId}?userId=` | `{"bookingId","userId","hotelId","roomType","checkInDate","checkOutDate","status","paymentStatus"}` or `404` |
| GET | `/internal/bookings/confirmed?userId=&hotelId=` | the user's confirmed bookings at the hotel |

---

### 3.7 review-service (port 8084, schema `review`)

**Purpose:** ratings (1–5) and comments for **completed stays only**: the booking must be the guest's own, at that
hotel, `CONFIRMED`, payment `CONFIRMED` (online or collected at the hotel) and check-out reached. One review per
booking; reviews can't be edited or deleted.
**Depends on:** catalog-service (hotel visible? + rating copy for search), booking-service (booking facts),
auth-service (reviewer name). **Job:** re-sends all hotel ratings to catalog on startup and every 5 minutes.

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/api/v1/hotels/{hotelId}/reviews?sort=recent\|highest\|lowest&page=&size=` | public | review cards `{"id","rating","comment","createdAt","reviewerName" (first name),"stayedOn","roomType","verifiedStay"}` (size ≤ 50) |
| GET | `/api/v1/hotels/{hotelId}/reviews/summary` | public | `{"hotelId","averageRating" (1 decimal, null if none),"totalReviews","ratingDistribution":{"5":n,...,"1":n}}` |
| GET | `/api/v1/hotels/{hotelId}/reviews/eligibility` | GUEST | own confirmed stays at the hotel, each `REVIEWABLE`, `REVIEWED`, `UPCOMING` or `NOT_ELIGIBLE` (with reason) |
| POST | `/api/v1/hotels/{hotelId}/reviews` | GUEST | `{"bookingId","rating" (1–5),"comment" (10–1000 chars)}` → `201`; `409` not completed / already reviewed |

---

### 3.8 frontend (port 3000)

React single-page app served by nginx. All API calls use relative `/api/...` URLs; nginx forwards them to
`http://api-gateway:8080` (uploads up to 60 MB, 90 s timeout). Unknown paths return `index.html` so deep links work.
Razorpay Checkout's script is loaded in the browser from `checkout.razorpay.com`. Health: `GET /healthz`.

---

## 4. How the services are interconnected

### 4.1 Picture

```
                         ┌───────────────────────────── Docker network "airbnb" ─────────────────────────────┐
 Browser                 │                                                                                    │
 localhost:3000 ───────► │ frontend (nginx) ──/api/*──► api-gateway ──┬──► auth-service ─────────┐            │
                         │                              :8080          ├──► catalog-service ──────┤            │
 Postman / Vite dev      │                                 ▲           ├──► booking-service ──────┤──► postgres │
 localhost:8080 ─────────┼─────────────────────────────────┘           └──► review-service ───────┘    :5432   │
                         │                                                                                    │
                         │  internal calls (/internal/**, X-Internal-Token):                                  │
                         │    booking-service ──► catalog-service   (holds, hotel facts, hotel counts)       │
                         │    booking-service ──► auth-service      (user name / email)                      │
                         │    review-service  ──► booking-service   (is the stay completed?)                 │
                         │    review-service  ──► catalog-service   (hotel visible?, push ratings)           │
                         │    review-service  ──► auth-service      (reviewer name)                          │
                         │    api-gateway     ──► auth-service      (does the token's account still exist?)  │
                         └───────────────────────────────────────────────┬────────────────────────────────────┘
                                                                         │ HTTPS
                                                         booking-service ──► api.razorpay.com (orders, payments, refunds)
```

* **No cycles:** catalog-service and auth-service call nobody; booking calls catalog + auth; review calls
  booking + catalog + auth.
* **Communication style:** synchronous REST/JSON over the Docker network, using service names as host names
  (`http://catalog-service:8082`). No service discovery, no message broker.
* **No shared database access:** each service reads/writes only its own schema; data owned by another service is
  always fetched through that service's API.

### 4.2 Security between the pieces

1. The user logs in → **auth-service** issues a JWT signed with `JWT_SECRET`.
2. Every request goes **browser → nginx → gateway**. The gateway checks the token and the role, then forwards the
   request *with the same token* to the service.
3. The service checks the token again (same secret) and uses the **user id from the token** — never from the request
   body — for ownership checks.
4. Service-to-service calls go to `/internal/**` with header `X-Internal-Token: ${INTERNAL_API_TOKEN}`. Those paths are
   never routed by the gateway, the service ports aren't published, and the token is checked anyway.

### 4.3 Key flows

**Search** — browser → gateway → catalog-service (one SQL query over hotels, room types, per-night inventory and the
rating copy). No other service involved.

**Create a booking** (no double booking possible):
```
guest ─POST /api/v1/bookings─► gateway ─► booking-service
   booking-service ─GET /internal/users/{id}──────────► auth-service      (name/email for the booking)
   booking-service ─POST /internal/inventory/holds────► catalog-service   locks hotel, room type and each night,
                                                                          checks free rooms + capacity, takes them,
                                                                          returns the price (amount)
   booking-service saves the booking RESERVED (amount from catalog, snapshot of names)
   booking-service ─POST /holds/{key}/attach──────────► catalog-service
   ◄── 201 RESERVED
```
If saving fails, booking-service releases the hold; if it crashed in between, a job releases the orphan hold
within ~15 minutes. Two guests racing for the last room: catalog's row locks let exactly one win (`409` for the other).

**Pay online:**
```
guest ─POST /payments/{id}─► booking-service ─► Razorpay: create order ─► {keyId, orderId, amount}
browser opens Razorpay Checkout, the guest pays
guest ─POST /payments/verify─► booking-service: check signature (key secret)
                                               ─► Razorpay: fetch payment, check order/amount/currency, capture
                                               ─► booking CONFIRMED, payment CONFIRMED
(Razorpay ─POST /api/v1/payments/webhook─► same confirmation, if the browser never reported back)
```

**Cancel / expire:** booking-service (refunds through Razorpay first if paid) marks the booking `CANCELLED`/`EXPIRED`
and saves it, *then* asks catalog-service to release the rooms; failed releases are retried every minute.

**Review:** review-service → catalog (hotel visible?) → booking-service (booking is the user's, CONFIRMED, paid,
checked out?) → auth (reviewer name) → saves the review → pushes the hotel's rating total to catalog so search's
`minRating` filter sees it.

**Manager dashboard:** booking-service counts bookings by status for the manager's hotels and asks catalog-service
for the hotel counts.

### 4.4 When something is down

| Down | Effect |
|---|---|
| auth-service | login/signup fail; requests with a token get `503` (public browsing still works) |
| catalog-service | search, hotel pages and new bookings fail (`503`); My Bookings still shows bookings from stored snapshots |
| booking-service | bookings and payments fail; search still works |
| review-service | reviews fail; everything else works |
| api-gateway | the whole API is unavailable |

---

## 5. How Docker is configured

### 5.1 Files

| File | Role |
|---|---|
| `compose.yaml` | the stack: 7 services, network, volume, health checks, environment |
| `compose.db.yaml` | optional overlay: publishes PostgreSQL on `127.0.0.1:5433` |
| `compose.e2e.yaml` | overlay for end-to-end tests: adds the Razorpay test double |
| `.env` / `.env.example` | secrets and settings (`.env` is git-ignored; `scripts/init-env.sh` creates it) |
| `services/<name>/Dockerfile` | image of each Java service |
| `frontend/Dockerfile`, `frontend/nginx.conf` | frontend image and its nginx configuration |
| `docker/postgres/init/01-service-schemas.sh` | creates schemas + logins on the first database start |
| `.dockerignore` (each build folder) | keeps `target/`, `node_modules/`, `dist/` out of builds |
| `services/<name>/src/main/resources/logback-spring.xml` | app / access / audit log files of each service |

### 5.2 Containers

| Container | Image | Build | Ports | Memory | Depends on (must be healthy) |
|---|---|---|---|---|---|
| `postgres` | `postgres:18-alpine` | – | internal 5432 | – | – |
| `auth-service` | `airbnb/auth-service:local` | `services/auth-service` | internal 8081 | 768 MB | postgres |
| `catalog-service` | `airbnb/catalog-service:local` | `services/catalog-service` | internal 8082 | 768 MB | postgres |
| `booking-service` | `airbnb/booking-service:local` | `services/booking-service` | internal 8083 | 768 MB | postgres, catalog, auth |
| `review-service` | `airbnb/review-service:local` | `services/review-service` | internal 8084 | 768 MB | postgres, catalog, booking, auth |
| `api-gateway` | `airbnb/api-gateway:local` | `services/api-gateway` | **127.0.0.1:8080** | 768 MB | all four services |
| `frontend` | `airbnb/frontend:local` | `frontend` | **3000** | – | api-gateway |

Startup order follows the health checks: postgres → auth + catalog → booking → review → gateway → frontend.
All containers restart automatically (`restart: unless-stopped`); logs rotate at 10 MB × 3 files.

### 5.3 Images

**Java services** (two-stage build):
```
build:  maven:3.9-eclipse-temurin-21
        copy pom.xml → download dependencies (cached between builds in a BuildKit cache for ~/.m2)
        copy src     → mvn package -DskipTests      (tests run with ./scripts/test-all.sh instead)
run:    eclipse-temurin:21-jre-alpine, non-root user "app", only the jar
        java -XX:MaxRAMPercentage=75 -jar app.jar   (heap = 75 % of the container's memory limit)
        HEALTHCHECK: wget /actuator/health/readiness every 10 s
```

**Frontend:**
```
build:  node:22-alpine → npm ci → npm run build (Vite)
run:    nginx:1.27-alpine serving dist/ with frontend/nginx.conf
        HEALTHCHECK: GET /healthz
```

### 5.4 Network

One bridge network, `airbnb_airbnb`. Containers reach each other by service name (`postgres`,
`catalog-service`, …). Only two ports reach the Mac: the frontend (3000) and the gateway (8080, localhost only).
Services and the database can't be reached from outside unless you use `compose.db.yaml`.

### 5.5 Storage

| Volume | Mounted at | Holds |
|---|---|---|
| `airbnb_pgdata` (named) | `/var/lib/postgresql` in `postgres` | all database data — survives restarts, `down`, rebuilds |
| `./docker/postgres/init` (bind, read-only) | `/docker-entrypoint-initdb.d` | init script, runs only when the volume is new |
| `./logs/<service>` (bind) | `/app/logs` (`/var/log/nginx` for the frontend) | app, access and audit logs |

Photos are stored inside the database (no file volume needed).

### 5.6 Configuration (`.env`)

| Variable | Required | Used by | Meaning |
|---|---|---|---|
| `POSTGRES_USER`, `POSTGRES_PASSWORD` | yes | postgres | database superuser |
| `AUTH_DB_PASSWORD`, `CATALOG_DB_PASSWORD`, `BOOKING_DB_PASSWORD`, `REVIEW_DB_PASSWORD` | yes | postgres + each service | per-service logins (applied only when the volume is created) |
| `JWT_SECRET` | yes (≥ 32 chars) | all Java services | signs/validates login tokens |
| `INTERNAL_API_TOKEN` | yes (≥ 16 chars) | all Java services | protects `/internal/**` |
| `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` | for online payments | booking-service | **test** keys only (`rzp_live_` keys are refused) |
| `RAZORPAY_WEBHOOK_SECRET` | for webhooks | booking-service | webhook HMAC secret |
| `FRONTEND_PORT` (3000), `GATEWAY_PORT` (8080), `DB_HOST_PORT` (5433) | no | compose | host ports |
| `BOOKING_HOLD_MINUTES` (15) | no | booking-service | unpaid reservation hold |
| `JAVA_MEM_LIMIT` (768m), `JAVA_OPTS` | no | Java services | container memory / JVM options |
| `CORS_ALLOWED_ORIGINS` | no | gateway | extra browser origins allowed to call the gateway |
| `TZ` (Asia/Kolkata) | no | all | time zone (booking dates use the local date) |
| `APP_LOG_LEVEL` (INFO) | no | Java services | `DEBUG` traces every controller and internal call |
| `LOG_ROOT` (./logs) | no | compose | host folder for the log files |

`docker compose` refuses to start if a required variable is missing (`set JWT_SECRET in .env`).

### 5.7 Logs

Each container writes `app.log`, `access.log` and `audit.log` to `./logs/<service>/` on the Mac (nginx:
`./logs/frontend/access.log`), and every line carries the request id, so one request can be traced through all
services with `grep -r "req=<id>" logs/`. `APP_LOG_LEVEL=DEBUG` in `.env` logs every controller and internal call.
Details: [LOGGING.md](LOGGING.md).

### 5.8 Useful Docker commands

```bash
docker compose config --quiet                       # validate compose.yaml + .env
docker compose build                                # build all images
docker compose up -d --build <service>              # rebuild + restart one
docker compose ps                                   # status / health
docker compose logs -f --tail 100 <service>         # follow logs
docker compose exec <service> sh                    # shell in a container
docker compose exec postgres psql -U airbnb_admin -d airbnb
docker stats                                        # CPU / memory per container
docker compose -f compose.yaml -f compose.db.yaml up -d postgres   # publish the DB on 127.0.0.1:5433
docker compose down                                 # remove containers, keep data
```

More detail: [Docker setup](DOCKER_SETUP.md) · [troubleshooting](TROUBLESHOOTING.md) ·
[architecture decisions](MICROSERVICES_ARCHITECTURE.md) · [database migration](DATABASE_MIGRATION.md) ·
[logging](LOGGING.md).
