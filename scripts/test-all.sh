#!/usr/bin/env bash
# Builds and tests every service (unit + integration tests against a local PostgreSQL test database), then the
# frontend production build. The Docker images skip tests to build fast; this is where they run.
#   ./scripts/test-all.sh            (needs Java 21+, Maven, Node 18+ and a local PostgreSQL)
# Test database: TEST_DB_URL (default jdbc:postgresql://localhost:5432/airbnb_services_test), TEST_DB_USERNAME
# (default: your OS user), TEST_DB_PASSWORD. Each service cleans only its own schema in it.
set -euo pipefail
cd "$(dirname "$0")/.."
./scripts/create-test-databases.sh
mvn -B -f services/pom.xml verify
(cd frontend && npm ci --no-audit --no-fund && npm run build)
echo "All service tests and the frontend build passed."
