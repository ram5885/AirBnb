# Local Development

## Repository layout

```
AirBnb/                          # repository root
├── compose.yaml                 # the whole stack (Docker Compose)
├── compose.e2e.yaml             # overlay for the end-to-end tests (Razorpay test double)
├── .env.example                 # configuration template (copy to .env, git-ignored)
├── services/
│   ├── pom.xml                  # convenience aggregator (mvn -f services/pom.xml verify)
│   ├── api-gateway/             # Spring Cloud Gateway (WebFlux)
│   ├── auth-service/            # package com.HotelManagement.AirBnb.auth
│   ├── catalog-service/         # ...hotel, location, search, hotel.hold, hotel.rating, internal
│   ├── booking-service/         # ...booking, guest, payment, dashboard, client, internal
│   └── review-service/          # ...review, client
├── frontend/                    # React app (Vite; Dockerfile + nginx.conf)
├── docker/postgres/init/        # creates one schema + role per service on first start
├── docker/razorpay-mock/        # Razorpay test double for the e2e tests only
├── scripts/                     # init-env, migrate-legacy-data, seed-demo-data (+ demo-data/), test-all, e2e/
└── docs/
```

Every service is an independent Maven project (Spring Boot 4.0.7, Java 21) with its own `pom.xml`, `Dockerfile`,
Flyway migrations and tests. Moved classes kept their original package names to keep the history readable; small
shared pieces (exceptions, error handler, JWT config, `InternalApiFilter`) are copied per service on purpose, so no
service depends on another at build time.

## Prerequisites (for running tests / developing outside Docker)

* Java 21+ (the build targets 21; JDK 26 works), Maven 3.9, Node 18+ (22 recommended)
* A local PostgreSQL for the automated tests (Homebrew `postgresql@18` is what the monolith used)

## Run the tests

```bash
./scripts/test-all.sh                  # all service tests + frontend build
cd services/booking-service && mvn verify   # one service
./scripts/e2e/run-e2e.sh               # end-to-end tests of the whole stack in Docker
```

Service tests use the local database `airbnb_services_test` (created if missing; never dropped; each service cleans
only its own schema). Override with `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`. Other services are replaced
by in-memory fakes (`FakeCatalog`, `FakeAuth`, `Fakes`), Razorpay by a Mockito mock with real HMAC signatures.

| Suite | Tests | What it covers |
|---|---|---|
| auth-service | 5 | signup/login/JWT claims, duplicate email, internal user API + token |
| catalog-service | 58 | hotels, rooms, photos, inventory, search + filters, sections, destinations (ported from the monolith), inventory holds incl. 10-thread no-oversell test, internal API |
| booking-service | 39 | booking, payments (signature, amount checks, capture, retries), webhooks, refunds, hold expiry, late payments, pay at hotel, My Bookings, manager actions (ported), dashboard, outage handling, release retry, orphan-hold reconciliation |
| review-service | 10 | eligibility rules, validation, concurrency, summary/sort/paging, rating push |
| api-gateway | 9 | route table, role rules, header stripping, session check, JSON 503/404, CORS |
| e2e (Docker) | 60 checks | the full critical workflow through nginx + gateway, see `scripts/e2e/e2e_test.py` |

## Developing one service

The quickest loop is to keep the stack running in Docker and rebuild just the service you change:

```bash
docker compose up -d --build catalog-service
docker compose logs -f catalog-service
```

To run a service from the IDE instead, give it the environment the container gets (see `compose.yaml`): a
`DB_URL`/`DB_USERNAME`/`DB_PASSWORD` for a reachable PostgreSQL, `JWT_SECRET`, `INTERNAL_API_TOKEN` and the other
services' URLs (defaults are `http://localhost:808x`). The Docker database isn't published to the host by default;
for that, add a local `compose.override.yaml` (git-ignored by you) with
`services: { postgres: { ports: ["127.0.0.1:5433:5432"] } }`.

## Frontend development

```bash
cd frontend
npm ci
npm run dev        # http://localhost:5173, proxies /api to the gateway on http://localhost:8080
```

`VITE_API_PROXY_TARGET` overrides the proxy target. The app only uses relative `/api/...` URLs, so the same build
works behind nginx (Docker) and Vite (dev). No frontend source changes were needed for the migration.

## Demo data

```bash
python3 scripts/seed-demo-data.py                        # against http://localhost:8080/api
python3 scripts/seed-demo-data.py --api http://localhost:8180/api   # e.g. the e2e stack
```

Same CSV files (`scripts/demo-data/`) and rules as the monolith's former `demo` profile, but through the
public API. Demo accounts use `DEMO_PASSWORD` (default `Demo@12345`). Idempotent.

## Adding a database change

Add a new Flyway file to the owning service, e.g. `services/booking-service/src/main/resources/db/migration/V2__add_x.sql`.
Never edit an applied `V1__...` file (Flyway checksums would fail at startup). Keep changes additive; Hibernate
validates the mapping at startup, so a missing column fails fast.

## City photos

`services/catalog-service/scripts/fetch_city_photos.py` re-downloads the bundled city photos (Wikimedia Commons)
into `services/catalog-service/src/main/resources/data/` (run it from anywhere; it resolves paths itself).

## The old monolith

Removed from the working tree after the migration. To look at it or run it for comparison:
`git checkout fa3c6dc -- AirBnb` (then `cd AirBnb && ./mvnw spring-boot:run`, with the gateway stopped: same port 8080).
