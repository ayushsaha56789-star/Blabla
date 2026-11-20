# SaathChalo

A carpooling web app for India. Drivers going between cities offer their empty seats; passengers find a ride going their way and split the cost. The owner runs everything from a staff console.

Built with **Java 25 + Spring Boot 4** (backend) and **Angular 22 + Tailwind CSS 4** (frontend), on **PostgreSQL + PostGIS**. The full plan is in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Status

| Phase | What it covers | Status |
|---|---|---|
| 1. Base | Phone-OTP login, profiles, home page, light/dark mode | ✅ Done |
| 2. Control centre | Super admin, staff roles, two-step verification, feature switches, member restrictions, audit log | ✅ Done |
| 3. Carpooling | My cars, place search, offer/search/book rides, requests, My rides, price cap | ✅ Done |
| 4. Trust & safety | Chat, reviews, SOS and trip sharing, reports and blocks, help centre, privacy centre, email and ID checks | ✅ Done |
| 5. Payments | Pay online (Razorpay), refunds, SaathChalo credits, driver earnings and payouts, double-entry ledger | ✅ Done |
| 6. Car rentals | List your car; rent one self-drive or with a driver; document checks, pickup and return records, deposits and disputes | ✅ Done |
| 7. Driver partners | Licensed drivers with service areas; hire a driver | Next |
| 8–11 | Games, Gen-Z features, promotions, launch | Planned — see the architecture doc |

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

## Trust and safety (try it)

- **Chat:** on a ride page, a passenger can message the driver (and the driver any booked passenger) without sharing phone numbers. New messages show as a badge on the chat icon. Chats close a week after the trip and are deleted after 6 months.
- **Reviews:** after a completed trip, driver and passengers rate each other (1–5 stars and a comment) from **My rides**. Ratings show on profiles, rides and search results.
- **Safety:** add up to 3 **emergency contacts** on your profile. Around a trip, the ride page shows **Share my trip** (a private link family can open, with live location if you allow it) and an **SOS** button (call 112, alert SaathChalo staff, text your contacts the link). In dev mode the texts appear in the backend log.
- **Report and block:** on a profile, chat message, review or ride. Reports go to the console; blocking stops messages and bookings both ways.
- **Help centre** (`/help`): answers to common questions and a contact form. Staff reply in the console; the member sees "1 new reply" in the menu.
- **Privacy centre** (account menu): what others see, **Download my data** (a JSON file), blocked members, and **Delete my account** (switched off at once, erased after 30 days; logging in again before then cancels it).
- **Verification** (on your profile): confirm your email with a 6-digit code (in dev mode the code is shown on screen), and send a photo of an ID for staff to check. Approved members get an "ID verified" badge.
- **Policies** (`/legal/terms`, `/legal/privacy`, `/legal/community`, `/legal/grievance`): plain-English drafts, marked as drafts until a lawyer has reviewed them (`LEGAL_DRAFT` in `frontend/src/app/features/legal/legal-docs.ts`).

## Payments (try it)

Online payments are **off** until you switch them on (console → Feature switches → **Online payments**, and **SaathChalo credits** if you want credits). In dev mode a **pretend checkout** opens instead of Razorpay: no real money moves.

- **Pay now:** on a ride page choose **Pay now** (or **Pay the driver** for cash/UPI on the day). The seat is held for 15 minutes while you pay. With instant booking the seat is confirmed once paid; otherwise the driver accepts or declines (declined = everything back). An unfinished payment can be completed from the ride page.
- **Refunds happen by themselves:** a declined or unanswered request, or a ride cancelled by the driver or staff, gives everything back. If you cancel at least 24 hours before pickup you get it all back; later, 50% of the seat price comes back and the driver keeps 50% (`app.payments.full-refund-hours`, `app.payments.late-cancel-refund-percent`).
- **Drivers** get the seat money once the trip is over (**Wallet** page). Staff pay it out from the console (a Razorpay payout link, or record a payment made by hand).
- **Credits** (Wallet page): from refunds, goodwill (staff can give them on a member's page) and later referrals. They pay for seats online and can't be withdrawn.
- A service fee is possible (`app.payments.service-fee-percent`, default 0).

## Car rentals (try it)

Rentals are **off** until you switch them on (console → Feature switches → **Car rentals**). Before you do, check the rules in the cities you start in: paid self-drive rental generally needs commercially registered cars and permits (India's Rent-a-Cab scheme; see "Privacy and Indian law" in the architecture doc). You can switch rentals on for chosen cities only.

- **List your car** (`/rentals/new`, or **Explore → Rent a car → List it for rent**): pick one of your cars, say how it's rented (self-drive, with you driving, or both), set the price per day (and per hour if you like), the refundable deposit, the km included per day and the price for extra km, the fuel rule, and the pickup area. The exact pickup address is shown only to a confirmed renter. Add photos and upload the RC and insurance (stored encrypted; only checking staff see them), then **Send for checking**.
- **Staff check it** in the console (**Rentals → To check**) and approve it or send it back with a note. Turn on **Unchecked listings go live** to skip this while testing.
- **Rent a car** (`/rentals`): search near a town for your dates. Each car shows the price for those dates; its page shows the deposit, the km included and the fuel rule. Self-drive needs an approved **driving licence** (Profile → Verification) and the owner's minimum age. The owner accepts or declines within a day (or the car is instantly booked if the owner allows it).
- **Paying:** with online payments on, the renter pays rent and deposit online after the owner accepts (24 hours to pay). Otherwise both are paid to the owner at pickup.
- **Pickup and return:** the owner records the odometer, fuel and photos at both ends (the renter can add their own pickup photos). At the return the owner can keep part of the deposit for extra km (worked out by itself) and other charges with a note. The renter accepts, or asks SaathChalo to check; it finishes by itself after 48 hours.
- **Disputes:** staff see both sides with the photos (**Rentals → Disputes**) and decide what the owner keeps; both see the decision.
- **Cancelling** (rentals paid online): the deposit always comes back. If the renter cancels less than 24 hours before pickup, half the rent goes to the owner (the same two `app.payments.*` settings as rides). When the owner or staff cancel, everything comes back.
- Blocked dates: owners can mark when the car isn't available. Two bookings can never overlap; the database refuses it.

## The staff console (`/admin`)

1. Log in with the number from step 2. You become **super admin** automatically (only when no super admin exists yet).
2. Open the account menu → **Staff console**.
3. The first time, set up **two-step verification**: install an authenticator app (Google Authenticator, Microsoft Authenticator, …), scan the QR code, type the 6-digit code, and **save the 10 recovery codes** somewhere safe.
4. After that, the console asks for a code from the app each time you open it, after 15 minutes without activity, and before sensitive changes.

What you can do there:

- **Feature switches** — turn any part of SaathChalo on or off, for everyone or only for ID-verified members, women, beta testers or chosen cities. Unbuilt features show members a "coming soon" page. Maintenance mode closes the member site; staff can still use the console.
- **Members** — search, see profiles (phone numbers masked unless you reveal them, which is recorded), suspend accounts or block single areas such as games, with a reason the member sees.
- **Rides** — every ride, searchable by any town on the route, with its bookings and passengers' messages. Admins and moderators can cancel a ride (for example after a safety report) with a reason; everyone on it sees the reason.
- **SOS alerts** — open alerts first (also shown as a red banner on the dashboard), with the member's location and a one-tap call; mark as handled and resolved.
- **Reports** — members' reports with the reported message or review attached; acknowledge within 24 hours, resolve within 15 days (remove the content, take action, or dismiss).
- **Help requests** — members' questions, oldest first; reply within 24 hours. Answered requests close by themselves after a quiet week.
- **Payments** — what's held for upcoming trips, owed to drivers and given as credits, with three health checks (the ledger balances, wallets match it, held money matches open payments); every payment and refund (failed refunds can be retried); **Payouts** to drivers. A member's page shows their wallet and lets refund-handling staff give goodwill credits.
- **Rentals** — check new car listings (photos, RC and insurance; every document view is recorded) and approve or send them back; settle disputes about charges after a return; see every rental and cancel one if needed (everything is refunded).
- **ID checks** — compare the ID photo (or driving licence) with the profile, then approve or reject with a reason. Every photo view is recorded; photos are stored encrypted and deleted 30 days after the decision (at once if a full Aadhaar number is visible).
- **Staff** — add people (they must have logged in once) as Admin, Moderator or Support; change roles; reset two-step verification for a lost phone.
- **Audit log** — every staff action, permanently recorded and hash-chained; "Verify integrity" proves nothing was changed.

| Role | Can do |
|---|---|
| Super admin | Everything, including feature switches, staff, settings and driver payouts |
| Admin | Members (incl. phone numbers), restrictions, ID checks, reports, SOS alerts, help requests, rentals, payments and credits, audit log |
| Moderator | Members, restrictions, ID checks, reports, SOS alerts, help requests |
| Support | Members (incl. phone numbers), SOS alerts, help requests |

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
    chat/ reviews/      ride chats; ratings after a trip
    safety/             emergency contacts, trip sharing, SOS
    moderation/         reports
    support/            help requests
    privacy/            data download, account deletion, legal contacts
    verification/       email codes, ID checks
    payments/           checkout (Razorpay), refunds, credits, earnings, payouts, ledger
    rentals/            car listings, rental bookings, pickup/return records, disputes
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
| `STORAGE_DIR` | Where profile and car photos and (encrypted) ID photos, car documents and pickup/return photos are stored |
| `PUBLIC_URL` | The site's address, used in links sent by text message (e.g. trip-sharing links) |
| `CORS_ALLOWED_ORIGINS` | Only if the web app is served from a different domain than the API |
| `LEGAL_COMPANY`, `GRIEVANCE_OFFICER`, `GRIEVANCE_EMAIL`, `LEGAL_ADDRESS` | Shown on the Grievance officer page (the IT Rules 2021 ask for them) |
| `PAYMENTS_PROVIDER` | `none` (default: online payments off) or `razorpay`. (`fake` works only in development and tests.) |
| `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET` | From the Razorpay dashboard (required with `razorpay`) |
| `RAZORPAYX_ACCOUNT_NUMBER` | Optional: lets staff send drivers payout links (RazorpayX). Without it, payouts are recorded by hand |

A real SMS provider (MSG91 or Twilio) and a real email provider replace the development console senders before launch (`app.sms.provider`, `app.email.provider`).

**Razorpay set-up:** finish Razorpay's business verification (KYC); turn on **automatic capture**; add a webhook to `https://<your-site>/api/payments/webhook` with the secret above and the events `payment.captured`, `order.paid`, `refund.processed`, `refund.failed` (and `payout_link.*` with RazorpayX). The site's Content Security Policy must allow `https://checkout.razorpay.com` (script) and `https://api.razorpay.com` (frame).
