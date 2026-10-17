# AirBnb Clone — microservices

Hotel booking app (Spring Boot 4 / Java 21, React 18, PostgreSQL) split into microservices and run with Docker Compose.

```bash
./scripts/init-env.sh              # .env with random secrets (add Razorpay TEST keys for online payments)
docker compose up -d --build       # http://localhost:3000  (API gateway: http://localhost:8080)
./scripts/migrate-legacy-data.sh   # once, optional: import the old monolith database
```

| Service | Responsibility |
|---|---|
| `api-gateway` | routing, JWT validation, role rules, CORS, timeouts |
| `auth-service` | accounts, login, JWT |
| `catalog-service` | hotels, room types, photos, cities, amenities, inventory, search & filters |
| `booking-service` | bookings, saved guests, Razorpay payments/refunds, manager booking management |
| `review-service` | ratings and reviews of completed stays |
| `frontend` | React app (nginx) |

Documentation — start with the **[project guide](docs/PROJECT_GUIDE.md)** (setup, database access, all APIs, how services connect, Docker):
[current architecture](docs/CURRENT_ARCHITECTURE.md) ·
[microservices architecture](docs/MICROSERVICES_ARCHITECTURE.md) ·
[API reference](docs/SERVICE_API_REFERENCE.md) ·
[database migration](docs/DATABASE_MIGRATION.md) ·
[Docker setup](docs/DOCKER_SETUP.md) ·
[local development](docs/LOCAL_DEVELOPMENT.md) ·
[troubleshooting](docs/TROUBLESHOOTING.md) ·
[logging](docs/LOGGING.md)

The original monolith was removed after the migration; it is in git history (commit `fa3c6dc` and earlier).
