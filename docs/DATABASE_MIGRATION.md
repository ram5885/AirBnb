# Database Migration

## Target layout

One PostgreSQL 18 container (`postgres`), one database `airbnb`, **one schema and one login role per service**:

| Schema | Role (owner) | Tables | Flyway migration |
|---|---|---|---|
| `auth` | `auth_svc` | `app_user`, `user_roles` | `services/auth-service/src/main/resources/db/migration/V1__auth_schema.sql` |
| `catalog` | `catalog_svc` | `location`, `city_photo`, `hotel`, `hotel_photo`, `room`, `room_photo`, `inventory`, `inventory_hold`, `hotel_rating` | `services/catalog-service/.../V1__catalog_schema.sql` |
| `booking` | `booking_svc` | `guest`, `payment`, `payment_webhook_event`, `booking`, `booking_guest` | `services/booking-service/.../V1__booking_schema.sql` |
| `review` | `review_svc` | `hotel_review` | `services/review-service/.../V1__review_schema.sql` |

* Roles and schemas are created by `docker/postgres/init/01-service-schemas.sh` the **first time** the data
  volume is initialised. It also revokes `CREATE` on `public` and `CONNECT` on the database from `PUBLIC`.
  A role can use only its own schema (verified: `catalog_svc` reading `auth.app_user` → `permission denied for schema auth`).
* Each service runs **Flyway** on startup (`spring.flyway.schemas=<schema>`), then Hibernate **validates** the
  entity mapping against it (`ddl-auto=validate`). `create`/`create-drop`/`update` are not used anywhere except
  that tests clean *their own schema* in the separate test database.
* Credentials come only from `.env` (git-ignored); see `.env.example`.
* Data lives in the named volume **`airbnb_pgdata`**; it survives `docker compose down`, restarts and rebuilds.

### Schema changes compared to the monolith (all additive)

| Table | Change | Why |
|---|---|---|
| `hotel` | no FK on `owner_id` | users are owned by auth-service |
| `inventory_hold` | **new** | the rooms each booking holds; replaces the single booking+inventory transaction |
| `hotel_rating` | **new** | rating read model for the search filter (pushed by review-service) |
| `booking` | no FKs to hotel/room/app_user; new `hotel_owner_id`, `hold_key`, `hold_status`, snapshot columns `hotel_name`, `hotel_city`, `hotel_state`, `room_type`, `room_capacity`, `user_name`, `user_email` | owner scoping without joins; hold tracking; views when other services are down |
| `guest` | no FK on `user_id` | users are owned by auth-service |
| `hotel_review` | no FKs; new `reviewer_name`, `stayed_on`, `room_type` | the public card no longer joins other services' tables |

All other column types, constraint names (`chk_inventory_booked_count`, `uk_hotel_review_booking`, ...) and index
names are identical to what Hibernate created in the monolith.

## Migrating existing data (one time)

The monolith's data (local database `AirBnb`, since dropped; dumps kept in `backups/`) is imported with
(`LEGACY_DUMP=backups/<file>.dump` imports from a dump instead of a live database):

```bash
docker compose up -d                 # first start: Flyway creates the four schemas
./scripts/migrate-legacy-data.sh     # LEGACY_PGDATABASE / LEGACY_PGHOST / LEGACY_PGPORT / LEGACY_PGUSER to override
```

Steps (all in `scripts/migrate-legacy-data.sh` and `scripts/legacy-import.sql`):

1. **Read-only dump** of the legacy database with the host's `pg_dump` → `backups/legacy-<db>-<timestamp>.dump`
   (custom format, `chmod 600`, git-ignored). The legacy database is never written to.
2. The dump is restored into a temporary database inside the container, its schema renamed to
   `legacy_<timestamp>` and copied into the `airbnb` database as a staging schema.
3. `legacy-import.sql` runs in **one transaction** as the database superuser:
   * **refuses** if any target business table already has rows (no merging into live data);
   * copies users, roles, hotels, rooms, photos, inventory, guests, payments, webhook events, bookings,
     booking guests and reviews **with their original ids** (photo URLs, booking references and review links
     keep working);
   * matches locations by `(city, state)` (catalog-service may already have seeded the same city list) and remaps
     `hotel.location_id`; adds legacy city photos for cities without one;
   * fills the new columns: booking snapshots and `hotel_owner_id` from the legacy hotel/room/user rows, one
     `inventory_hold` per booking with a deterministic `hold_key = md5('legacy-booking-' || booking_id)::uuid`
     (status HELD for RESERVED/ACCEPTED/CONFIRMED bookings, RELEASED otherwise; the copied
     `inventory.booked_count` already includes those rooms), review snapshots, and `catalog.hotel_rating`;
   * moves every identity sequence past the imported ids;
   * prints a per-table report and **raises (rolls back) if any count differs**.
4. The temporary database and the staging schema are dropped; the dump in `backups/` is the copy to keep.

### Result of the migration run on this machine

```
 table_name                       | legacy_rows | imported_rows | check
 app_user                         |           2 |             2 | ok
 user_roles                       |           2 |             2 | ok
 hotel                            |           3 |             3 | ok
 hotel_photo                      |           3 |             3 | ok
 room                             |           5 |             5 | ok
 room_photo                       |           4 |             4 | ok
 inventory                        |        1825 |          1825 | ok
 location (legacy cities present) |         252 |           252 | ok
 guest / payment / booking / ...  |           0 |             0 | ok
```

Verified afterwards: password hashes and roles byte-identical; hotels, room types and photos served through the
gateway (`/api/v1/hotels/1/photos/1` → 200 image/jpeg); a second run refused with
`Refusing to import: these tables already contain data`; legacy row counts unchanged.

### Re-running / repeatability
* The import only runs into an empty stack. To redo it, you would have to empty the stack's business tables
  first — this is deliberately **not** automated (it deletes data); ask before doing it.
* A failed run changes nothing (single transaction) and can simply be retried.

## Rollback

* **Application rollback:** the monolith's code was removed from the working tree but is in git history
  (`git checkout fa3c6dc -- AirBnb` restores it). Its local `AirBnb` database was **dropped after the migration**;
  the read-only dumps in `backups/legacy-AirBnb-*.dump` are the remaining copy. Restore it with
  `createdb AirBnb && pg_restore --no-owner -d AirBnb backups/legacy-AirBnb-20261018023011.dump`, stop the stack
  (`docker compose down` — keeps the volume) and run the restored monolith.
* **Data created in the new stack** is in the `airbnb_pgdata` volume. Export it before going back if needed:
  ```bash
  docker compose exec -T postgres pg_dump -U "$POSTGRES_USER" -d airbnb -Fc > backups/airbnb-stack-$(date +%Y%m%d%H%M).dump
  ```
  Mapping back to the monolith's single schema would be the reverse of `legacy-import.sql` (same ids); it is not
  scripted.
* **Backups of the stack:** the command above; restore into a fresh volume with `pg_restore -d airbnb`.

## Tests and the database

Automated tests use a separate local database, `airbnb_services_test` (created by
`scripts/create-test-databases.sh`, never dropped). Each service's tests run Flyway `clean` + `migrate` on **its own
schema in that test database only**, so every run starts empty. The development and Docker databases are never used
by tests.
