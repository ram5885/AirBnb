#!/usr/bin/env bash
# Creates .env from .env.example with freshly generated random secrets. Never overwrites an existing .env.
# Razorpay test keys are left empty: add yours to .env to enable online payments.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -f .env ]]; then
  echo ".env already exists; not touching it." >&2
  exit 0
fi
rand() { openssl rand -hex "${1:-24}"; }
sed -e "s/^POSTGRES_PASSWORD=.*/POSTGRES_PASSWORD=$(rand)/" \
    -e "s/^AUTH_DB_PASSWORD=.*/AUTH_DB_PASSWORD=$(rand)/" \
    -e "s/^CATALOG_DB_PASSWORD=.*/CATALOG_DB_PASSWORD=$(rand)/" \
    -e "s/^BOOKING_DB_PASSWORD=.*/BOOKING_DB_PASSWORD=$(rand)/" \
    -e "s/^REVIEW_DB_PASSWORD=.*/REVIEW_DB_PASSWORD=$(rand)/" \
    -e "s/^JWT_SECRET=.*/JWT_SECRET=$(rand 32)/" \
    -e "s/^INTERNAL_API_TOKEN=.*/INTERNAL_API_TOKEN=$(rand)/" \
    .env.example > .env
chmod 600 .env
echo "Wrote .env with random secrets (add Razorpay TEST keys to enable online payments)."
