# Docker Setup

Runs the whole application on a Mac with Docker Desktop (tested with Docker Desktop 4.95, Engine 29.8, Compose v5.6,
Apple Silicon). Nothing else needs to be installed to *run* it.

## Containers and ports

| Container | Image | Host port | Purpose |
|---|---|---|---|
| `frontend` | `airbnb/frontend:local` (nginx 1.27) | **3000** | React build + proxy of `/api/*` to the gateway |
| `api-gateway` | `airbnb/api-gateway:local` | **127.0.0.1:8080** | single API entry point |
| `auth-service` | `airbnb/auth-service:local` | – | accounts, login, JWT |
| `catalog-service` | `airbnb/catalog-service:local` | – | hotels, rooms, inventory, search |
| `booking-service` | `airbnb/booking-service:local` | – | bookings, guests, payments |
| `review-service` | `airbnb/review-service:local` | – | reviews and ratings |
| `postgres` | `postgres:18-alpine` | – | database (volume `airbnb_pgdata`) |

Internal services and the database are reachable only on the Compose network by their service names
(`http://catalog-service:8082`, `postgres:5432`, ...). Change host ports with `FRONTEND_PORT` / `GATEWAY_PORT` in `.env`.

## First start

```bash
cd /path/to/AirBnb_Docker/AirBnb      # repository root (contains compose.yaml)
./scripts/init-env.sh                  # creates .env with random secrets (or: cp .env.example .env and edit)
# optional: add Razorpay TEST keys (rzp_test_...) to .env for online payments
docker compose config --quiet          # validate the configuration
docker compose build                   # build the 6 images (first time: ~3-5 minutes)
docker compose up -d                   # start; services wait for their dependencies to be healthy
docker compose ps                      # all should be "healthy"
open http://localhost:3000
./scripts/migrate-legacy-data.sh       # optional, once: import the monolith's existing data
python3 scripts/seed-demo-data.py      # optional: demo dataset (10 users, 54 hotels, 161 bookings)
```

Startup order (enforced with health checks): `postgres` → `auth-service`, `catalog-service` → `booking-service` →
`review-service` → `api-gateway` → `frontend`. A cold start takes about 30–40 seconds.

## Environment variables (`.env`)

| Variable | Used by | Notes |
|---|---|---|
| `POSTGRES_USER`, `POSTGRES_PASSWORD` | postgres | superuser, used for setup and the legacy import only |
| `AUTH_DB_PASSWORD`, `CATALOG_DB_PASSWORD`, `BOOKING_DB_PASSWORD`, `REVIEW_DB_PASSWORD` | postgres, services | one login per service; set only at first volume initialisation |
| `JWT_SECRET` | all Java services | HS256 key, ≥ 32 characters; changing it logs everyone out |
| `INTERNAL_API_TOKEN` | all Java services | service-to-service token, ≥ 16 characters |
| `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` | booking-service | **test** keys only; empty = online payments disabled (pay at hotel works) |
| `RAZORPAY_WEBHOOK_SECRET` | booking-service | only if you configure a webhook |
| `FRONTEND_PORT` (3000), `GATEWAY_PORT` (8080) | compose | host ports |
| `BOOKING_HOLD_MINUTES` (15) | booking-service | unpaid reservation hold |
| `JAVA_MEM_LIMIT` (768m), `JAVA_OPTS` | Java services | container memory limit; the heap is 75 % of it |
| `CORS_ALLOWED_ORIGINS` | gateway | only needed when a browser calls the gateway directly from another origin |
| `TZ` (Asia/Kolkata) | all | booking dates use the server's local date |

Missing required variables make `docker compose` stop with a clear message (`set JWT_SECRET in .env`).
**Never commit `.env`** (it is git-ignored). Changing a `*_DB_PASSWORD` after the first start does not change the
password inside the existing database; see TROUBLESHOOTING.md.

## Everyday commands

```bash
docker compose ps                              # status and health
docker compose logs -f                         # all logs (Ctrl-C to stop following)
docker compose logs -f booking-service         # one service
docker compose logs --since 10m api-gateway
docker compose restart booking-service         # restart one service
docker compose up -d --build booking-service   # rebuild and restart one service after a code change
docker compose build && docker compose up -d   # rebuild everything
docker compose stop                            # stop containers, keep them and the data
docker compose start
docker compose down                            # remove containers and network; DATA IS KEPT (volume airbnb_pgdata)
docker compose exec postgres psql -U airbnb_admin -d airbnb   # SQL shell (superuser)
```

**Data persistence:** the database is in the named volume `airbnb_pgdata`. It survives `stop`, `down`, image
rebuilds and Docker Desktop restarts (verified by the E2E suite, which restarts the stack and checks the data).
`docker compose down -v` or `docker volume rm airbnb_pgdata` **deletes all data** — don't run them unless you mean
it; back up first:

```bash
set -a; . ./.env; set +a
docker compose exec -T postgres pg_dump -U "$POSTGRES_USER" -d airbnb -Fc > backups/airbnb-$(date +%Y%m%d%H%M).dump
```

## Logs

`./logs/<service>/app.log`, `access.log` and `audit.log` on the host (nginx: `./logs/frontend/access.log`), traced by
request id across all services — see [LOGGING.md](LOGGING.md). `docker compose logs` still shows everything.

## Images

* Java services: multi-stage `maven:3.9-eclipse-temurin-21` → `eclipse-temurin:21-jre-alpine`, non-root user,
  BuildKit cache for `~/.m2`, `HEALTHCHECK` on `/actuator/health/readiness`. Tests are not run in the image build
  (they need a database); run them with `./scripts/test-all.sh`.
* Frontend: `node:22-alpine` (`npm ci`, `vite build`) → `nginx:1.27-alpine` with `frontend/nginx.conf`
  (SPA fallback, `/api/` proxy, 60 MB upload limit, `/healthz`).
* `.dockerignore` in every build context keeps `target/`, `node_modules/`, `dist/` and IDE files out.

## Razorpay in Docker

* Put **test** keys in `.env` (`RAZORPAY_KEY_ID=rzp_test_...`, `RAZORPAY_KEY_SECRET=...`) and run
  `docker compose up -d booking-service`. The browser loads Checkout from `checkout.razorpay.com`; the backend
  verifies every payment with Razorpay before confirming.
* Live keys (`rzp_live_...`) are refused (`RAZORPAY_ALLOW_LIVE` is hard-wired to `false` in `compose.yaml`).
* Webhooks need a public URL: tunnel `http://localhost:8080` (e.g. `ngrok http 8080`) and register
  `https://<tunnel>/api/v1/payments/webhook` in the Razorpay test dashboard with `RAZORPAY_WEBHOOK_SECRET`.

## End-to-end tests in Docker

```bash
./scripts/e2e/run-e2e.sh
```

Starts a **separate** Compose project `airbnb-e2e` (volume `airbnb_e2e_pgdata`, ports 3100 / 8180 / 9100) with a
Razorpay test double (`compose.e2e.yaml`, `docker/razorpay-mock/`), runs `scripts/e2e/e2e_test.py` and stops it.
Your main stack and its data are not touched. Remove the e2e project's own data with
`docker compose -p airbnb-e2e -f compose.yaml -f compose.e2e.yaml down -v` (only the e2e volume).
