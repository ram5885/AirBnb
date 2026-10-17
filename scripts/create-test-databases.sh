#!/usr/bin/env bash
# Creates the local PostgreSQL database used by the services' automated tests (if it doesn't exist yet).
# Non-destructive: an existing database is left alone. Each service's tests clean only their own schema in it.
set -euo pipefail
DB="${TEST_DB_NAME:-airbnb_services_test}"
if psql -lqt | cut -d'|' -f1 | grep -qw "$DB"; then
  echo "Test database '$DB' already exists"
else
  createdb "$DB"
  echo "Created test database '$DB'"
fi
