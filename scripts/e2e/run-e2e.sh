#!/usr/bin/env bash
# End-to-end tests against a separate, throwaway Compose project ("airbnb-e2e": own database volume, ports 3100/8180,
# Razorpay test double), so your development data in the main stack is never touched.
#   ./scripts/e2e/run-e2e.sh            build, start, test, stop (the e2e volume is kept for inspection)
# Remove the e2e stack's own data afterwards, if you like, with:
#   docker compose -p airbnb-e2e -f compose.yaml -f compose.e2e.yaml down -v
set -euo pipefail
cd "$(dirname "$0")/../.."
[[ -f .env ]] || { echo "No .env: run ./scripts/init-env.sh first" >&2; exit 1; }
set -a; . ./.env; set +a
export PGDATA_VOLUME=airbnb_e2e_pgdata LOG_ROOT=./logs/e2e FRONTEND_PORT=3100 GATEWAY_PORT=8180 E2E_MOCK_PORT=9100
export E2E_API=http://localhost:3100/api E2E_GATEWAY=http://localhost:8180/api E2E_RAZORPAY_MOCK=http://localhost:9100
COMPOSE=(docker compose -p airbnb-e2e -f compose.yaml -f compose.e2e.yaml)

echo "Starting the e2e stack (first build takes a few minutes)..."
"${COMPOSE[@]}" up -d --build --wait --wait-timeout 300
# The Razorpay double's script is mounted, not built: restart it so it always runs the current version.
"${COMPOSE[@]}" restart razorpay-mock >/dev/null && sleep 2
status=0
python3 scripts/e2e/e2e_test.py || status=$?
if [[ "${KEEP_E2E_RUNNING:-0}" != "1" ]]; then
  "${COMPOSE[@]}" stop >/dev/null
fi
exit $status
