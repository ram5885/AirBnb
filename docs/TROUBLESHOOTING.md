# Troubleshooting

Start with `docker compose ps` (which container isn't healthy?) and `docker compose logs --tail 100 <service>`.
For a failing request, copy the `X-Request-Id` response header (browser network tab) and run
`grep -r "req=<id>" logs/ | sort` to see its path through nginx, the gateway and every service
([LOGGING.md](LOGGING.md)). `grep -h outcome=FAILURE logs/*/audit.log` lists everything that was refused.

## The stack doesn't start

| Symptom | Cause / fix |
|---|---|
| `required variable JWT_SECRET is missing a value: set JWT_SECRET in .env` | No `.env` or a missing value: `./scripts/init-env.sh` or copy `.env.example` |
| A service restarts with `app.jwt.secret must be at least 32 bytes` / `app.internal-api-token ... at least 16 characters` | Secrets too short in `.env` |
| `Bind for 0.0.0.0:3000 failed: port is already allocated` (or 8080) | Something else uses the port (e.g. the old monolith on 8080). Stop it or set `FRONTEND_PORT` / `GATEWAY_PORT` in `.env` |
| Service unhealthy, log shows `password authentication failed for user "catalog_svc"` | The `*_DB_PASSWORD` in `.env` changed after the volume was created (passwords are set only on first initialisation). Put the old value back, or change it inside the DB: `docker compose exec postgres psql -U airbnb_admin -d airbnb -c "ALTER ROLE catalog_svc PASSWORD '<new>'"` |
| `Validate failed: Migrations have failed validation` / checksum mismatch | An applied Flyway file was edited. Restore it and put the change in a new `V<n>__...sql` |
| `Schema-validation: missing column` | Entity and migration disagree; add a Flyway migration for the column |
| `could not find a network matching network mode ...` after editing `compose.yaml` | Containers were created for an old network: `docker compose up -d --force-recreate` (data is kept) |
| Image build fails with `Premature end of Content-Length` / Maven download errors | Network hiccup while downloading dependencies; rerun `docker compose build` (the Maven cache keeps what was downloaded) |
| Containers killed / `OutOfMemoryError` | Raise Docker Desktop's memory (Settings → Resources, ≥ 4 GB) or `JAVA_MEM_LIMIT` in `.env` |

## In the browser

| Symptom | Cause / fix |
|---|---|
| "This part of the site is temporarily unavailable" (503) | A service is down or restarting: `docker compose ps`, then that service's logs |
| "The server took too long to respond" (504) | A service didn't answer within the gateway timeout (30 s, 60 s for bookings/payments/uploads) |
| Logged out right after starting the stack | Tokens from another stack/secret, or the account doesn't exist in this database (e.g. before the legacy import). Log in or sign up again |
| "Your session has expired. Please log in again." | The token's account doesn't exist (anymore) in auth-service — e.g. a token issued by the e2e stack |
| "Online payments aren't available right now" | No Razorpay keys: add `RAZORPAY_KEY_ID`/`RAZORPAY_KEY_SECRET` (test mode) to `.env`, `docker compose up -d booking-service`. Pay at hotel works without keys |
| Razorpay popup doesn't open | The browser must reach `checkout.razorpay.com` (ad blockers sometimes block it) |
| Payment succeeded in Razorpay but booking still RESERVED | Verification failed (see booking-service logs: signature, amount or order mismatch) or the browser closed before `/verify`. With a webhook configured the booking is confirmed automatically; otherwise the payment can be retried from My Bookings |
| Hotel search doesn't show a just-reviewed hotel for "minimum rating" | The rating read model is updated right after each review and re-synced every 5 minutes; check review-service logs for "Rating ... not pushed" |
| Rooms look booked after a cancellation | The release is retried every minute if catalog-service was unavailable (booking hold status `RELEASE_PENDING`); check booking-service logs |

## Data

```bash
set -a; . ./.env; set +a
docker compose exec postgres psql -U "$POSTGRES_USER" -d airbnb
# \dn                      schemas          \dt catalog.*      tables of one service
# select booking_id, booking_status, hold_status from booking.booking order by 1 desc limit 10;
# select hold_key, status, booking_id from catalog.inventory_hold order by id desc limit 10;
```

* **Never** run `docker compose down -v` or `docker volume rm airbnb_pgdata` unless you intend to delete all data;
  take a backup first (see DOCKER_SETUP.md).
* `migrate-legacy-data.sh` says "Refusing to import: these tables already contain data": it only imports into an
  empty stack, by design.
* `pg_dump: error: server version mismatch` during the legacy import: the host's `pg_dump` must be the same or a newer
  major version than the legacy server (Homebrew: `brew install postgresql@18`).

## Tests

* `connection refused` / `database "airbnb_services_test" does not exist`: start the local PostgreSQL and run
  `./scripts/create-test-databases.sh`; set `TEST_DB_USERNAME`/`TEST_DB_PASSWORD` if your local server needs them.
* e2e: ports 3100, 8180, 9100 must be free. `KEEP_E2E_RUNNING=1 ./scripts/e2e/run-e2e.sh` keeps the e2e stack up for
  debugging (`docker compose -p airbnb-e2e logs -f`).
