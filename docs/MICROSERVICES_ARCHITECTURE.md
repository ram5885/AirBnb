# Microservices Architecture

Target architecture for the AirBnb clone, derived from the code analysis in
[CURRENT_ARCHITECTURE.md](CURRENT_ARCHITECTURE.md). Goal: clear business ownership, runnable on a
developer Mac with Docker Compose, no unnecessary infrastructure.

## 1. Overview

```
 Browser ──► frontend (nginx, :3000) ──/api/*──► api-gateway (:8080) ──┬─► auth-service     (:8081) ─► schema auth
                                                (JWT, roles, CORS,      ├─► catalog-service  (:8082) ─► schema catalog
                                                 routing, timeouts)     ├─► booking-service  (:8083) ─► schema booking ──► Razorpay (HTTPS)
                                                                        └─► review-service   (:8084) ─► schema review
                                                 one PostgreSQL 16 container, one database "airbnb",
                                                 one schema + one login role per service
```

Synchronous REST between services (Spring `RestClient`, explicit DTO contracts under `/internal/**`).
No service discovery (Docker DNS names are enough), no message broker (eventual-consistency needs are
covered by idempotent APIs plus small retry/reconciliation jobs — see §5).

**Service dependency graph (acyclic):**

```
booking-service ──► catalog-service
booking-service ──► auth-service
review-service  ──► booking-service, catalog-service, auth-service
api-gateway     ──► all (routing) and auth-service (session check)
catalog-service ──► (nothing)        auth-service ──► (nothing)
```

## 2. Service boundaries and why

| Service | Business capability | Owns (tables) |
|---|---|---|
| **auth-service** | Identity: accounts, roles, passwords, JWT issuing | `app_user`, `user_roles` |
| **catalog-service** | Hotel catalog *and* sellable availability: hotels, room types, photos, amenities, cities/states, date-wise inventory + pricing, inventory holds, public search & filters | `hotel`, `hotel_photo`, `room`, `room_photo`, `inventory`, `inventory_hold`, `location`, `city_photo`, `hotel_rating` (read model) |
| **booking-service** | Reservation lifecycle *and* its money: bookings, saved guests, Razorpay payments/refunds/webhooks, pay-at-hotel, hold expiry, manager booking management, manager dashboard | `booking`, `booking_guest`, `guest`, `payment`, `payment_webhook_event` |
| **review-service** | Ratings & reviews of completed stays | `hotel_review` |
| **api-gateway** | Single entry point: routing, JWT validation, role rules, CORS, timeouts, uniform errors | — |

### Decisions taken from the code (and the alternatives rejected)

* **Search is not a separate service.** `HotelQueries.HAS_BOOKABLE_ROOM` filters hotels by joining
  `room` and `inventory` per night with price and amenity predicates in one SQL statement. A separate search
  service would need a full replica of hotels, rooms and 365 days of inventory per room type. Search stays
  next to the data it reads, inside catalog-service.
* **Inventory lives in catalog-service, not booking-service.** Managers edit it directly (close dates, surge
  pricing, room counts), search reads it constantly, and room-count changes must be validated against
  `booked_count`. Bookings consume it through an explicit, idempotent *hold* API (§5).
* **Locations live in catalog-service.** `LocationQueries.DESTINATIONS` joins `hotel`, `room` and
  `hotel_photo`; hotel creation validates city/state against `location`.
* **Payments stay inside booking-service (no separate payment-service).** Every payment step reads and
  changes booking state under the same row lock: verify confirms the booking, late payments re-acquire
  inventory or trigger a refund, cancellation refunds before releasing rooms, review eligibility depends
  on payment status, and every booking view computes `canPay` from the payment. A split would turn each of
  these into a saga with no business benefit at this scale. Payment code stays an isolated package behind
  the `PaymentGateway` interface, so it can be extracted later; Razorpay secrets are only given to
  booking-service.
* **Saved guests (`guest`) live in booking-service.** They exist only to be attached to bookings
  (`booking_guest`) and are validated against room capacity while booking.
* **Reviews are their own service.** Independent lifecycle (immutable, public reads), low coupling: it needs
  booking facts once at write time and pushes one aggregate (average/count) to catalog for the rating filter.
* **Dashboard moves to booking-service.** It is mostly booking counts; hotel counts come from catalog. Keeping
  it in catalog would create a catalog → booking dependency and a cycle.
* **No notification service** — the monolith sends no emails/SMS. **No service discovery, no Kafka.**
* **One PostgreSQL container, schema per service**, each service logging in with its own role that owns only
  its schema (no grants on other schemas). Same isolation as separate databases for this purpose, far
  simpler on a laptop, and it allows a single-transaction legacy data import.
* **Each service is an independent Maven project** (own `pom.xml`, Boot 4.0.7, Java 21). Small shared pieces
  (`GlobalExceptionHandler`, exceptions, `PageResponse`, JWT resource-server config) are copied, not shared
  as a library: no build-time coupling between services and no shared JPA entities.
  Moved classes keep their original package names (`com.HotelManagement.AirBnb.*`) to keep the diff
  reviewable.

## 3. Per-service detail

### auth-service (port 8081)
* **APIs (public):** `POST /api/auth/signup`, `POST /api/auth/login` (unchanged contract, JWT with `sub`=user id,
  `email`, `roles`, 24 h expiry, HS256).
* **APIs (internal):** `GET /internal/users/{id}` → `{id, name, email, roles}`; `POST /internal/users/lookup`
  `{ids}` → list.
* **Depends on:** nothing. **Failure impact:** login/signup unavailable; already-issued tokens keep working
  for up to the gateway's session-cache TTL.

### catalog-service (port 8082)
* **APIs (public, via gateway):** `/api/locations/**`, `/api/v1/amenities`, `/api/v1/hotels/search`,
  `/api/v1/hotels/sections`, `/api/v1/hotels/{id}`, `/api/v1/hotels/{id}/rooms/{roomId}`, public photos;
  manager: `/api/v1/admin/hotels/**` (hotels, room types, photos, inventory).
* **APIs (internal):** inventory holds (`POST /internal/inventory/holds`, `.../{key}/attach`, `.../{key}/release`,
  `.../{key}/reacquire`, `GET .../holds/unattached`), `POST /internal/hotels/summaries`,
  `GET /internal/hotels/{id}/public`, `GET /internal/owners/{ownerId}/hotel-counts`,
  `PUT /internal/hotels/ratings`.
* **Depends on:** nothing (ratings are pushed to it). **Failure impact:** search, hotel pages and new bookings
  unavailable; My Bookings still renders from booking-service snapshots.

### booking-service (port 8083)
* **APIs:** `/api/v1/bookings/**`, `/api/v1/guests/**`, `/api/v1/payments/**` (incl. public, HMAC-checked
  `/webhook`), `/api/v1/admin/bookings/**`, `/api/v1/admin/dashboard`.
* **APIs (internal, for reviews):** `GET /internal/bookings/{id}?userId=`,
  `GET /internal/bookings/confirmed?userId=&hotelId=`.
* **Depends on:** catalog (holds, hotel/room info), auth (user name/email), Razorpay.
* **Jobs:** hold expiry (every minute), hold-release retry and unattached-hold reconciliation (every minute /
  5 minutes).

### review-service (port 8084)
* **APIs:** `GET /api/v1/hotels/{id}/reviews`, `/summary` (public), `GET .../eligibility`, `POST .../reviews` (GUEST).
* **Depends on:** catalog (hotel is public; rating push), booking (eligibility facts), auth (reviewer name).
* **Jobs:** rating re-sync to catalog (startup + every 5 minutes).

### api-gateway (port 8080)
Spring Cloud Gateway (WebFlux, Spring Cloud 2025.1.3 — the release train for Boot 4.0.x).
Routes by path, validates the JWT signature/expiry, applies the original role rules from `SecurityConfig`,
checks the token's user still exists (auth-service, cached 60 s — preserves the monolith's
`CurrentUserService` guard), applies CORS, per-route timeouts, and returns `{"error": ...}` JSON for
401/403/502/503/504. `/internal/**` is never routed. Downstream services *also* validate the JWT (defense in
depth) and protect `/internal/**` with a shared internal token.

## 4. Authentication and authorization

* JWT issued only by auth-service; HS256 secret `JWT_SECRET` from the environment (never in source).
* Gateway: authenticates and enforces roles (GUEST: bookings/guests/payments/review writes; HOTEL_MANAGER:
  `/api/v1/admin/**`; public: auth, locations, amenities, hotel reads, review reads, webhook).
* Services: re-validate the same JWT with the same rules; the user id is always the token subject — never
  from request bodies or headers set by the browser. Ownership checks (owner/manager, booking owner) remain
  in the services exactly as before.
* Internal calls carry `X-Internal-Token: ${INTERNAL_API_TOKEN}`; the gateway strips that header from client
  requests.

## 5. Distributed transactions and failure handling

The monolith's single-transaction guarantees are replaced as follows. **Rule: when in doubt, keep rooms
blocked (never oversell); release later through an idempotent retry.**

### Create booking (hold → record → attach)
1. booking-service validates the request and the caller's saved guests (local), generates a `holdKey` (UUID).
2. `POST catalog /internal/inventory/holds {holdKey, hotelId, roomId, dates, rooms, guestCount}`. catalog runs
   the *original* locking algorithm in one local transaction (`hotel FOR SHARE`, `room FOR SHARE`,
   `inventory FOR UPDATE` in date order, CHECK constraint as last guard), increments `booked_count`, stores an
   `inventory_hold` row and returns the server-computed amount plus hotel/room snapshot data.
   **Idempotent**: the same `holdKey` returns the existing hold. Double booking is impossible because only
   catalog changes `booked_count`, under row locks.
3. booking-service inserts the `RESERVED` booking (with `hold_key`) and commits, then calls `attach`.
4. Failure handling: if step 3 fails, booking-service calls `release(holdKey)` (compensation). If the
   process dies in between, the **reconciler** (every 5 min) lists holds still unattached after 10 minutes and
   attaches them (booking exists) or releases them (no booking).

### Cancel / expire (record → release)
Under the booking row lock: refund first if paid (as before; a failed refund aborts), set `CANCELLED`/`EXPIRED`
and `hold_status = RELEASE_PENDING`, commit. After commit, `release(holdKey)` is called; on failure a retry job
(every minute) re-sends it. Release is idempotent (already released → no-op), so retries are safe.

### Late payment on an expired booking
`reacquire(holdKey)` re-takes the rooms under the same inventory locks (or returns `false`, in which case the
payment is refunded in full, as before). Idempotent: a still-held hold returns `true`.

### Payments
Unchanged rules: signature verification with the key secret, server-side fetch of the payment from Razorpay,
order/amount/currency checks, capture, explicit state transitions under the booking row lock, idempotent
webhooks keyed by `x-razorpay-event-id`. **The browser's success callback never confirms anything.**
Razorpay **test keys only** (`rzp_live_` keys are refused unless `RAZORPAY_ALLOW_LIVE=true`).

### Ratings
review-service pushes the absolute `(sum, count)` per hotel to catalog after each review and re-syncs all
hotels on startup and every 5 minutes. Search's min-rating filter and result ratings read that projection;
the review summary endpoint is always exact.

### Timeouts / downstream errors
`RestClient` connect 2–3 s / read 5–20 s; catalog or auth unavailable → `503 {"error": ...}`. Booking lists
fall back to the hotel/room snapshot stored on the booking when catalog is down.

## 6. Data ownership and migration

See [DATABASE_MIGRATION.md](DATABASE_MIGRATION.md). Summary: Flyway `V1` per service creates its schema with
the same column types/constraints as the legacy tables (plus new columns for ids that replace JPA relations
and for snapshots). A one-time, idempotent import script copies the legacy `AirBnb` database into the new
schemas, preserving ids, then resets sequences. The legacy database is only read (`pg_dump`), never modified.

## 7. Docker networking and ports

| Container | Internal port | Host port | Notes |
|---|---|---|---|
| frontend | 80 | **3000** | nginx serves the React build, proxies `/api/` to the gateway |
| api-gateway | 8080 | **127.0.0.1:8080** | for API testing / webhook tunnels |
| auth-service | 8081 | — | internal only |
| catalog-service | 8082 | — | internal only |
| booking-service | 8083 | — | internal only |
| review-service | 8084 | — | internal only |
| postgres | 5432 | — | internal only (optional debug override exposes 127.0.0.1:5433) |

One bridge network `airbnb`; services address each other by compose service name.

## 8. Migration risks and rollback

| Risk | Mitigation |
|---|---|
| Lost data during copy | Legacy DB is read-only input; import is one transaction per run and refuses to run into non-empty tables; row counts verified after import |
| Oversell after split | Only catalog changes inventory, under the original row locks + CHECK constraint; holds are idempotent; release only after the booking is durably cancelled |
| Orphan holds (rooms blocked) | Reconciler releases unattached holds after 10 min; retry job for pending releases |
| Rating filter slightly stale | Push after each review + periodic re-sync |
| Contract drift with the React app | Same paths, payloads and error bodies; frontend code unchanged except build/proxy config |
| Gateway/Spring Cloud compatibility | Spring Cloud 2025.1.3 is the official train for Boot 4.0.x |

**Rollback:** the original local `AirBnb` database was dropped after the migration (restore it from
`backups/legacy-AirBnb-*.dump` with `pg_restore`), and the monolith's code is in git history
(`git checkout fa3c6dc -- AirBnb`, then `cd AirBnb && ./mvnw spring-boot:run`). Stop the compose stack first
(`docker compose down`, which keeps volumes). Data created only in the new
stack stays in the `airbnb_pgdata` volume and can be exported with `pg_dump` if needed.

## 9. Implementation status (verified)

| Check | Result |
|---|---|
| Service builds + tests (`./scripts/test-all.sh`) | auth 5, catalog 58, booking 39, review 10, gateway 9 — **121 passed, 0 failed** |
| Frontend production build | passed (host and Docker) |
| `docker compose config` / `build` / `up -d` | valid / 6 images built / 7 containers healthy |
| Schema isolation | each service role is denied on other schemas and on `public` |
| End-to-end suite (`./scripts/e2e/run-e2e.sh`, isolated stack) | **60 checks passed**: register/login → search + filters → hotel page → availability → booking → Razorpay test-mode payment (test double) → backend signature/amount verification → CONFIRMED → My Bookings → review → rating filter; concurrent last-room booking (1 of 6 succeeds); signed/duplicate webhooks; refund on cancel; manager accept / pay at hotel; edge security; data intact after a full restart |
| Legacy data import | all legacy rows imported with ids, hashes identical, re-run refused, legacy DB unchanged |
| Demo seeder (API) | 10 users, 24 guests, 54 hotels, 161 room types, 161 bookings; second run creates nothing |

## 10. Known limitations and trade-offs

* **Real Razorpay Checkout not exercised here:** no test keys were available, so the browser popup and Razorpay's own
  API were not called. The backend verification path ran against a Razorpay test double with Razorpay's response shapes.
  With your `rzp_test_` keys in `.env`, do one manual payment (card `4111 1111 1111 1111`, any future expiry, any CVV).
* **The UI was not clicked through in a browser** in this environment; every API the UI calls was exercised through
  the same nginx proxy, and the React code itself is unchanged.
* **Eventual consistency, by design:** the search rating filter can lag a new review if catalog-service is down
  (re-synced every 5 minutes); rooms of a cancelled booking stay blocked until catalog confirms the release
  (retried every minute); an orphan hold (crash between holding rooms and saving the booking) is released after
  ~10–15 minutes. None of these can oversell.
* **Rare leak case:** if the database commit of a late-payment confirmation fails *after* catalog re-took the rooms,
  those rooms stay blocked (not oversold) and need a manual release. Not automatically reconciled.
* **Auth outage = no authenticated requests:** the gateway's account check fails closed (503); public browsing keeps
  working. Positive answers are cached for 60 s.
* **One PostgreSQL instance:** schema-level isolation, but a shared failure domain and shared resources. Splitting
  into separate databases later only changes `DB_URL`s (no cross-schema queries exist).
* **Demo seeder vs. soft-deleted hotels:** the API can't see soft-deleted hotels, so the seeder would recreate a demo
  hotel a manager deleted (the monolith's in-process seeder skipped it).
* **No central logging/tracing/metrics stack**: `docker compose logs` only (kept simple on purpose).
* **The monolith has been removed** from the working tree (React app moved to `frontend/`, demo CSVs to
  `scripts/demo-data/`, city-photo fetcher to `services/catalog-service/scripts/`); it remains in git history.
