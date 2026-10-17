# AirBnb clone — frontend

A basic React (Vite) frontend for the Spring Boot backend in this repo. Landing page is a
dummy Airbnb-style home feed; "Log in" / "Sign up" opens an Airbnb-style auth page wired to
the real `/api/auth/login` and `/api/auth/signup` endpoints.

## Run it

You need Node.js 18+ installed.

```bash
# 1. Start the backend first (from the repo root, in another terminal)
./mvnw spring-boot:run
# backend runs on http://localhost:8080

# 2. Install and start the frontend
cd frontend
npm install
npm run dev
# opens on http://localhost:5173
```

The Vite dev server proxies any `/api/*` request to `http://localhost:8080` (see
`vite.config.js`), so the frontend can call the backend with plain `fetch('/api/auth/login')`
calls and no CORS setup is needed in dev. A `WebConfig` CORS bean was also added on the
backend as a fallback for the case where the frontend is opened without the proxy.

## What's here

- `src/pages/Home.jsx` — dummy landing page (navbar, category pills, a grid of placeholder
  listings). Public, no login required — matches how real Airbnb/MakeMyTrip let you browse
  before signing in.
- `src/pages/Auth.jsx` — the login/signup card (`/login`, `/signup`), styled after Airbnb's
  auth modal: tabs, email/password fields with validation, a role picker for signup (Guest /
  Hotel manager / Admin, matching the backend's `Role` enum), and inert "Continue with
  Google/Facebook/Apple" buttons for visual completeness.
- `src/context/AuthContext.jsx` — holds the logged-in user (from the backend's `AuthResponse`)
  in memory + `localStorage`, since the backend doesn't issue a session token/JWT yet. This is
  a demo-level "logged in" state, not real auth security.
- `src/api.js` — thin fetch wrapper around `/api/auth/login` and `/api/auth/signup`.

## Known limitations (by design, since this is a basic/demo scaffold)

- No JWT/session — refreshing works (state persists in `localStorage`), but there's no
  server-verified session, and nothing is protected server-side yet.
- Listings on the home page are static placeholder data (`src/data/listings.js`), not fetched
  from the backend.
- Search bar and category pills are visual only.
