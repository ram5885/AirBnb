# Logging, Tracing, Access and Audit Logs

Every container writes **three separate logs** to files on your Mac (and everything also appears in
`docker compose logs`):

| File | Contains | Logger | Kept |
|---|---|---|---|
| `logs/<service>/app.log` | everything the code logs: startup, business steps, warnings, errors | all classes | 14 days |
| `logs/<service>/access.log` | **one line per HTTP request**: who, what, status, duration | `ACCESS` | 30 days |
| `logs/<service>/audit.log` | **business and security events**: logins, bookings, payments, refunds, reviews, denials | `AUDIT` | 90 days |
| `logs/frontend/access.log`, `error.log` | nginx: every browser request (with the request id) | nginx | until removed |

`<service>` is `api-gateway`, `auth-service`, `catalog-service`, `booking-service` or `review-service`.
Files roll over daily and at 20 MB (`app.2026-10-18.0.log.gz`, …). `logs/` is git-ignored. The E2E test stack writes
to `logs/e2e/` instead, so its logs never mix with yours.

## Tracing one request through all services

nginx gives every request an id and sends it on as `X-Request-Id`; the gateway forwards it to the service, and every
service forwards it on its internal calls. **Every log line contains `req=<id>`**, and the response carries the same
`X-Request-Id` header (visible in the browser's network tab).

```bash
grep -r "req=45aa1412ca29548ece51203b65c10b0a" logs/ | sort
```

Example — one "book a room" request, in time order (shortened):

```
[auth-service]    access  "GET /internal/users/32" status=200 2ms                    ← booking-service looks up the guest
[catalog-service] app     InventoryHoldService - Held 1 room(s) of room type 170 in hotel 59 ...
[catalog-service] audit   event=ROOMS_HELD outcome=SUCCESS actor=32 holdKey=43fa... hotelId=59 roomId=170 ...
[catalog-service] access  "POST /internal/inventory/holds" status=200 11ms
[booking-service] app     BookingService - Booking 175 reserved for user 32: hotel 59 ...
[booking-service] audit   event=BOOKING_CREATED outcome=SUCCESS actor=32 bookingId=175 ... amount=5000.00
[catalog-service] access  "POST /internal/inventory/holds/43fa.../attach" status=200 3ms
[booking-service] access  user=32 "POST /api/v1/bookings" status=201 34ms
[api-gateway]     access  user=32 "POST /api/v1/bookings" status=201 37ms
[frontend/nginx]  access  "POST /api/v1/bookings HTTP/1.1" status=201 0.038s
```

Background jobs (hold expiry, release retries, hold reconciliation, rating sync) get their own ids:
`req=job-hold-expiry-…`, so their lines can be followed the same way.

## Line formats

**app.log** — `time level [service] [req=… user=…] [thread] logger - message`
```
2026-10-18 03:03:34.234 INFO  [auth-service] [req=4c3ce07e… user=-] [http-nio-8081-exec-5] c.H.AirBnb.auth.service.AuthService - Login failed: unknown email
```

**access.log** — `time [service] req=… ip=… user=… "METHOD path?query" status=… duration`
```
2026-10-18 03:05:35.855 [api-gateway] req=45aa1412… ip=192.168.65.1 user=32 "POST /api/v1/bookings" status=201 37ms
```
`user` is the logged-in user id (from the validated token) or `-`. On `/internal/**` lines it is the user the calling
service acts for (header `X-Acting-User`, used only for logging, never for authorization). Health checks aren't logged.

**audit.log** — `time [service] req=… event=… outcome=SUCCESS|FAILURE actor=… key=value …`
```
2026-10-18 03:04:36.302 [auth-service] req=ff1bba5a… event=LOGIN outcome=FAILURE actor=anonymous email=nobody@example.com reason="unknown email"
```
`actor` = user id, `anonymous` (not logged in) or `system` (background job).

## Audit events

| Service | Events |
|---|---|
| api-gateway | `AUTHENTICATION_REJECTED` (no/invalid token), `ACCESS_DENIED` (wrong role), `SESSION_REJECTED` (token of a deleted account) |
| auth-service | `SIGNUP` (success / email already registered), `LOGIN` (success / unknown email / wrong password) |
| catalog-service | `HOTEL_CREATED`, `HOTEL_UPDATED`, `HOTEL_ACTIVATED`, `HOTEL_DEACTIVATED`, `HOTEL_DELETED` (incl. refused), `ROOM_TYPE_CREATED`, `ROOM_TYPE_UPDATED`, `ROOM_TYPE_DELETED` (incl. refused), `INVENTORY_UPDATED`, `ROOMS_HELD`, `ROOMS_RELEASED`, `ROOMS_REACQUIRED` |
| booking-service | `BOOKING_CREATED`, `BOOKING_GUESTS_UPDATED`, `BOOKING_ACCEPTED`, `BOOKING_CANCELLED` (by guest / hotel manager), `BOOKING_EXPIRED`, `PAYMENT_ORDER_CREATED`, `PAYMENT_VERIFIED` (success / invalid signature / amount or order mismatch / payment failed), `PAYMENT_ATTEMPT` (failure reported by Checkout), `PAY_AT_HOTEL_CHOSEN`, `PAYMENT_COLLECTED_AT_HOTEL`, `REFUND_ISSUED`, `REFUND_COMPLETED`, `PAYMENT_REFUNDED_UNBOOKABLE`, `WEBHOOK_RECEIVED` (incl. invalid signature), `ORPHAN_HOLD_RELEASED`, `GUEST_PROFILE_CREATED`, `GUEST_PROFILE_UPDATED` |
| review-service | `REVIEW_CREATED` (success / not eligible / already reviewed) |

Refusals are audited where they are decided: the gateway audits only the requests it rejects itself; a 401/403/409
returned by a service is audited by that service (and appears in every access log on the way).

**Never logged:** passwords, tokens, Razorpay key secret, payment signatures, card data, request bodies.

## Log levels

```bash
# .env
APP_LOG_LEVEL=DEBUG     # default INFO
docker compose up -d    # recreates the containers with the new level
```

At `DEBUG` every controller call is logged with its parameters (ids, dates, filters), plus every internal call
(`Internal call POST http://catalog-service:8082/internal/inventory/holds -> 200 in 11 ms`), every session check and
every error response. `INFO` keeps state changes, warnings and errors.

## Useful commands

```bash
tail -f logs/booking-service/app.log                           # follow one service
tail -f logs/*/audit.log                                       # all audit events, live
grep -h "event=PAYMENT_VERIFIED" logs/booking-service/audit.log
grep -h "outcome=FAILURE" logs/*/audit.log                     # everything that was refused
grep -h " status=5" logs/*/access.log                          # server errors
grep -h "user=32 " logs/api-gateway/access.log                 # what one user did
grep -r "req=<id>" logs/ | sort                                # one request across all services
docker compose logs -f booking-service                         # console view (all three logs together)
```

## Where it is configured

| Piece | File |
|---|---|
| Log files, patterns, rotation, retention | `services/<service>/src/main/resources/logback-spring.xml` |
| Request id + access log (services) | `shared/logging/RequestLoggingFilter.java` |
| User id on every line | `shared/logging/UserMdcFilter.java` (added in each `SecurityConfig`) |
| Audit log | `shared/logging/AuditLogger.java` (`AuditLogger.success(...)` / `failure(...)`) |
| Request id on internal calls | `client/InternalRestClients.java` (booking, review) |
| Gateway access + audit | `services/api-gateway/.../RequestLoggingWebFilter.java`, `SessionCheckFilter.java` |
| nginx access log with request id | `frontend/nginx.conf` (`log_format traced`) |
| Host folders | `compose.yaml` volumes `${LOG_ROOT:-./logs}/<service>:/app/logs`; `LOG_DIR`, `APP_LOG_LEVEL` env |
