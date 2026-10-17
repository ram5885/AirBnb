#!/usr/bin/env bash
# One-time migration of the monolith's PostgreSQL data into the microservice schemas of the Docker stack.
#
#   ./scripts/migrate-legacy-data.sh                 # legacy DB "AirBnb" on localhost:5432 as $USER
#   LEGACY_PGDATABASE=AirBnb LEGACY_PGHOST=localhost LEGACY_PGPORT=5432 LEGACY_PGUSER=ram ./scripts/migrate-legacy-data.sh
#   LEGACY_DUMP=backups/legacy-AirBnb-20261018023011.dump ./scripts/migrate-legacy-data.sh   # from an existing dump
#     (the legacy database itself was dropped after the migration; the dumps in backups/ are the remaining copy)
#
# What it does (see docs/DATABASE_MIGRATION.md):
#   1. pg_dump of the legacy database (READ-ONLY; the legacy database is never changed) into ./backups/.
#   2. Restores that dump into a temporary database inside the postgres container and renames its schema to a
#      staging schema (legacy_<timestamp>) in the "airbnb" database.
#   3. Runs scripts/legacy-import.sql in one transaction: copies rows (ids preserved) into auth / catalog / booking /
#      review, adds the new columns' data, resets sequences and verifies the row counts. Refuses to run if the
#      stack already has business data.
#   4. Drops the temporary database and the staging schema (the dump in ./backups/ is the copy to keep).
# Requires: the stack started at least once (`docker compose up -d`) so Flyway has created the schemas, and the
# host's pg_dump (PostgreSQL client tools, same or newer major version than the legacy server).
set -euo pipefail
cd "$(dirname "$0")/.."
[[ -f .env ]] || { echo "No .env: run ./scripts/init-env.sh first" >&2; exit 1; }
set -a; . ./.env; set +a

LEGACY_PGDATABASE="${LEGACY_PGDATABASE:-AirBnb}"
LEGACY_PGHOST="${LEGACY_PGHOST:-localhost}"
LEGACY_PGPORT="${LEGACY_PGPORT:-5432}"
LEGACY_PGUSER="${LEGACY_PGUSER:-$USER}"
STAMP="$(date +%Y%m%d%H%M%S)"
STAGE="legacy_${STAMP}"
TEMPDB="legacy_import_${STAMP}"
DUMP="${LEGACY_DUMP:-backups/legacy-${LEGACY_PGDATABASE}-${STAMP}.dump}"
PSQL=(docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U "${POSTGRES_USER:-airbnb_admin}")

echo "==> Checking the Docker stack"
docker compose ps --status running postgres | grep -q postgres || { echo "postgres container is not running: docker compose up -d" >&2; exit 1; }
for schema in auth catalog booking review; do
  "${PSQL[@]}" -d airbnb -Atc "SELECT 1 FROM ${schema}.flyway_schema_history LIMIT 1" >/dev/null 2>&1 \
    || { echo "Schema '${schema}' has no Flyway history yet: start the services once (docker compose up -d) and retry." >&2; exit 1; }
done

if [[ -n "${LEGACY_DUMP:-}" ]]; then
  [[ -f "$DUMP" ]] || { echo "LEGACY_DUMP file not found: $DUMP" >&2; exit 1; }
  echo "==> Using existing dump ${DUMP}"
else
  echo "==> Dumping legacy database '${LEGACY_PGDATABASE}' on ${LEGACY_PGHOST}:${LEGACY_PGPORT} (read-only) to ${DUMP}"
  mkdir -p backups
  pg_dump --format=custom --schema=public --no-owner --no-privileges \
          -h "$LEGACY_PGHOST" -p "$LEGACY_PGPORT" -U "$LEGACY_PGUSER" -d "$LEGACY_PGDATABASE" -f "$DUMP"
  chmod 600 "$DUMP"
fi

cleanup() {
  "${PSQL[@]}" -d postgres -qc "DROP DATABASE IF EXISTS \"${TEMPDB}\"" >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "==> Staging the dump in schema ${STAGE}"
"${PSQL[@]}" -d postgres -qc "CREATE DATABASE \"${TEMPDB}\""
# The dump re-creates schema public itself; start the temporary database without one.
"${PSQL[@]}" -d "$TEMPDB" -qc "DROP SCHEMA public"
docker compose exec -T postgres pg_restore --no-owner --no-privileges -U "${POSTGRES_USER:-airbnb_admin}" \
  -d "$TEMPDB" < "$DUMP"
"${PSQL[@]}" -d "$TEMPDB" -qc "ALTER SCHEMA public RENAME TO \"${STAGE}\""
docker compose exec -T postgres sh -c \
  "pg_dump -U \"\$POSTGRES_USER\" -d '${TEMPDB}' -n '${STAGE}' --format=custom --no-owner | pg_restore -U \"\$POSTGRES_USER\" -d airbnb --no-owner"

echo "==> Importing into the service schemas (one transaction)"
status=0
"${PSQL[@]}" -d airbnb -v stage="$STAGE" < scripts/legacy-import.sql || status=$?

echo "==> Removing the staging schema ${STAGE} (the dump stays in ${DUMP})"
"${PSQL[@]}" -d airbnb -qc "SET client_min_messages = warning; DROP SCHEMA IF EXISTS \"${STAGE}\" CASCADE"

if [[ $status -ne 0 ]]; then
  echo "Import FAILED and was rolled back; the stack's data is unchanged. See the messages above." >&2
  exit $status
fi
echo "==> Done. Restart review-service to re-sync ratings right away (optional): docker compose restart review-service"
