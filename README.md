# SaathChalo

A carpooling web app for India. Drivers going between cities offer their empty seats; passengers find a ride going their way and split the cost.

Built with **Java 25 + Spring Boot 4** (backend) and **Angular 22 + Tailwind CSS 4** (frontend), on **PostgreSQL + PostGIS**.

## Status

| Phase | What it covers | Status |
|---|---|---|
| 1. Base | Project setup, database, phone-OTP login, profiles with photo and travel preferences, home page, light/dark mode, phone-friendly layout | ✅ Done |
| 2. Core rides | Publish a ride, search by route, book seats, driver accepts/rejects, my rides & bookings | Next |
| 3. Trust | Chat, ratings and reviews, ID verification, notifications | Planned |
| 4. Payments | Razorpay (UPI/cards), refunds | Planned |
| 5. Launch | Hindi, admin panel, real SMS provider, security review, deployment | Planned |

## Run it locally

You need Java 25, Node.js 24 LTS and PostgreSQL 16 with PostGIS (`sudo apt install postgresql postgis`).

**1. Create the database (once).** From the repo root:

```bash
sudo -u postgres psql < scripts/setup-db.sql
```

This creates the `saathchalo` user plus the `saathchalo` and `saathchalo_test` databases. It is safe to run again.

**2. Start the backend** (http://localhost:8080):

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Database tables are created automatically on first start. API docs: http://localhost:8080/swagger-ui.html

**3. Start the frontend** in a second terminal (http://localhost:4200):

```bash
cd frontend
npm install
npm start
```

**4. Log in** with any Indian mobile number. In dev mode no SMS is sent: the 6-digit code is shown on the login screen and in the backend log.

## Tests

```bash
cd backend && ./mvnw test                 # needs the saathchalo_test database from step 1
cd frontend && npm test -- --watch=false
```

## Project layout

```
backend/                Spring Boot API (package-by-feature)
  src/main/java/com/saathchalo/
    auth/               phone OTP, JWT access tokens, refresh-token sessions
    user/               profiles and public profiles
    files/              profile photo storage
    config/  common/    security, settings, error handling, helpers
  src/main/resources/db/migration/   Flyway database migrations
frontend/               Angular web app
  src/app/core/         auth, API client, theme, notifications
  src/app/features/     pages: home, login, onboarding, profile, search, offer a ride
  src/app/layout/       navbar, footer, toasts
  src/app/shared/       icons, avatar, code input, search form
scripts/setup-db.sql    one-time local database setup
```

## How login works

1. The user enters a mobile number; the API texts a 6-digit code (valid 5 minutes, 5 tries, 30 s between resends, 5 codes per hour).
2. On the right code, the API returns a short-lived access token (30 min) and sets a long-lived refresh token (30 days) as an HttpOnly cookie that page scripts cannot read.
3. The web app keeps the access token in memory only and silently renews it with the cookie when it expires. Refresh tokens are single-use and rotate on every renewal.

## Production settings

Set these environment variables (do not use the `dev` profile):

| Variable | Purpose |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | PostgreSQL connection |
| `JWT_SECRET` | Random secret, at least 32 characters |
| `STORAGE_DIR` | Where profile photos are stored |
| `CORS_ALLOWED_ORIGINS` | Only if the web app is served from a different domain than the API |

A real SMS provider (MSG91 or Twilio) replaces the development console sender in phase 5.
