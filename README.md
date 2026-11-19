# SaathChalo

A carpooling web app for India. Drivers going between cities offer their empty seats; passengers find a ride going their way and split the cost. The owner runs everything from a staff console.

Built with **Java 25 + Spring Boot 4** (backend) and **Angular 22 + Tailwind CSS 4** (frontend), on **PostgreSQL + PostGIS**. The full plan is in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Status

| Phase | What it covers | Status |
|---|---|---|
| 1. Base | Phone-OTP login, profiles, home page, light/dark mode | ✅ Done |
| 2. Control centre | Super admin, staff roles, two-step verification, feature switches, member restrictions, audit log | ✅ Done |
| 3. Carpooling | My cars, place search, offer/search/book rides, requests, My rides, price cap | ✅ Done |
| 4. Trust & safety | Chat, reviews, verification, SOS, trip sharing, privacy centre, help | Next — needs the owner's "start" |
| 5–11 | Payments, rentals, driver partners, games, Gen-Z features, promotions, launch | Planned — see the architecture doc |

## Run it locally

You need Java 25, Node.js 24 LTS and PostgreSQL 16 with PostGIS (`sudo apt install postgresql postgis`).

**1. Create the database (once).** From the repo root:

```bash
sudo -u postgres psql < scripts/setup-db.sql
```

**2. Make yourself the super admin (once).** Copy the example settings file and put your mobile number in it. The copy is git-ignored, so your number never reaches GitHub.

```bash
cd backend
cp application-local.properties.example application-local.properties
# edit application-local.properties:  app.bootstrap.super-admin-phone=+91XXXXXXXXXX
```

**3. Start the backend** (http://localhost:8080):

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

**4. Start the frontend** in a second terminal (http://localhost:4200):

```bash
cd frontend
npm install
npm start
```

**5. Log in** with any Indian mobile number. In dev mode no SMS is sent: the 6-digit code is shown on the login screen.

## Carpooling (try it)

1. **Offer a ride:** log in, finish your profile (first name and gender), then **Offer a ride**. Pick start and destination (old names like "Bangalore" or "Gurgaon" work), add suggested stops, choose the time, add your car once, accept or change the suggested price, and publish.
2. **Find a ride:** use the search bar on the home page with a second account (another browser or a private window). Rides that stop near your start and destination show up, priced for your part of the trip.
3. **Book:** with instant booking the seat is confirmed at once; otherwise the driver sees the request under **My rides** (with a badge) and accepts or declines it.
4. Once a seat is confirmed, the driver and passenger see each other's phone number (Call / WhatsApp) and the passenger sees the car's number plate.

Rules you can change in `backend/src/main/resources/application.properties` (`app.rides.*`): suggested and maximum price per km, minimum price, search radius, how far ahead rides can be offered, and how long requests wait for an answer.

## The staff console (`/admin`)

1. Log in with the number from step 2. You become **super admin** automatically (only when no super admin exists yet).
2. Open the account menu → **Staff console**.
3. The first time, set up **two-step verification**: install an authenticator app (Google Authenticator, Microsoft Authenticator, …), scan the QR code, type the 6-digit code, and **save the 10 recovery codes** somewhere safe.
4. After that, the console asks for a code from the app each time you open it, after 15 minutes without activity, and before sensitive changes.

What you can do there:

- **Feature switches** — turn any part of SaathChalo on or off, for everyone or only for ID-verified members, women, beta testers or chosen cities. Unbuilt features show members a "coming soon" page. Maintenance mode closes the member site; staff can still use the console.
- **Members** — search, see profiles (phone numbers masked unless you reveal them, which is recorded), suspend accounts or block single areas such as games, with a reason the member sees.
- **Rides** — every ride, searchable by any town on the route, with its bookings and passengers' messages. Admins and moderators can cancel a ride (for example after a safety report) with a reason; everyone on it sees the reason.
- **Staff** — add people (they must have logged in once) as Admin, Moderator or Support; change roles; reset two-step verification for a lost phone.
- **Audit log** — every staff action, permanently recorded and hash-chained; "Verify integrity" proves nothing was changed.

| Role | Can do |
|---|---|
| Super admin | Everything, including feature switches, staff and settings |
| Admin | Members (incl. phone numbers), restrictions, verifications, moderation, rentals, refunds, audit log |
| Moderator | Members, restrictions, verifications, moderation |
| Support | Members (incl. phone numbers), help tickets |

## Tests

```bash
cd backend && ./mvnw test                 # needs the saathchalo_test database from step 1
cd frontend && npm test -- --watch=false
```

## Project layout

```
backend/                Spring Boot API, one module per feature (checked by Spring Modulith)
  src/main/java/com/saathchalo/
    auth/               phone OTP, sessions, staff two-step verification
    access/             staff roles and permissions
    features/           feature switches
    audit/              hash-chained audit log
    user/               profiles, member restrictions
    geo/                Indian place search, distance and time estimates
    vehicles/           members' cars (registration numbers encrypted)
    rides/              offering, searching and booking rides; My rides
    admin/              staff console APIs
    files/ config/ common/
  src/main/resources/db/migration/   Flyway database migrations
  src/main/resources/geo/            the place list (GeoNames, CC BY 4.0)
frontend/               Angular web app
  src/app/admin/        the staff console
  src/app/core/         auth, API clients, feature switches, theme
  src/app/features/     member pages
  src/app/layout/ shared/
scripts/setup-db.sql    one-time local database setup
scripts/build_places.py rebuilds the place list from GeoNames (instructions inside)
docs/ARCHITECTURE.md    architecture, roadmap and the owner's decisions
```

## Production settings

Set these environment variables (do not use the `dev` profile):

| Variable | Purpose |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | PostgreSQL connection |
| `JWT_SECRET` | Random secret, at least 32 characters |
| `DATA_ENCRYPTION_KEY` | Base64 of 32 random bytes; encrypts 2FA secrets and other sensitive fields |
| `SUPER_ADMIN_PHONE` | The owner's number (+91…), made super admin at first login |
| `STORAGE_DIR` | Where profile photos are stored |
| `CORS_ALLOWED_ORIGINS` | Only if the web app is served from a different domain than the API |

A real SMS provider (MSG91 or Twilio) replaces the development console sender before launch.
