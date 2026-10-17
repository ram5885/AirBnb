#!/bin/bash
# Runs once, when the postgres volume is first initialised (docker-entrypoint-initdb.d).
# One login role per service, owning only its own schema: a service can't read or write another service's
# tables. Passwords come from the environment (.env), never from source control.
set -euo pipefail

: "${AUTH_DB_PASSWORD:?AUTH_DB_PASSWORD is required}"
: "${CATALOG_DB_PASSWORD:?CATALOG_DB_PASSWORD is required}"
: "${BOOKING_DB_PASSWORD:?BOOKING_DB_PASSWORD is required}"
: "${REVIEW_DB_PASSWORD:?REVIEW_DB_PASSWORD is required}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v db="$POSTGRES_DB" <<'SQL'
-- Nobody but the owners may create objects or connect by default.
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
SQL

create_service_schema() {
  local schema="$1" role="$2" password="$3"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
       -v db="$POSTGRES_DB" -v schema="$schema" -v role="$role" -v password="$password" <<'SQL'
CREATE ROLE :"role" LOGIN PASSWORD :'password';
GRANT CONNECT ON DATABASE :"db" TO :"role";
CREATE SCHEMA :"schema" AUTHORIZATION :"role";
ALTER ROLE :"role" SET search_path = :"schema";
SQL
  echo "Created schema '$schema' owned by role '$role'"
}

create_service_schema auth    auth_svc    "$AUTH_DB_PASSWORD"
create_service_schema catalog catalog_svc "$CATALOG_DB_PASSWORD"
create_service_schema booking booking_svc "$BOOKING_DB_PASSWORD"
create_service_schema review  review_svc  "$REVIEW_DB_PASSWORD"
