# SaathChalo architecture

> **Status:** v2, 19 November 2026. The owner's decisions are recorded in section 16. Phases 1 and 2 are built; every other phase starts only when the owner says "start".
>
> **Not legal advice.** The law sections describe what the software will do to support each rule. A lawyer and a chartered accountant should check them before public launch.

## 1. What SaathChalo is

SaathChalo is a web app (a responsive website for phone and laptop browsers) for India where members can:

1. **Share rides** between cities and split the cost. This is the core.
2. **Rent cars** from owners: self-drive, or with a driver.
3. **Hire a driver**: licensed drivers register with the areas they serve, and members book them for their own car or for a rental.
4. **Have fun and keep coming back**: free games (chess, ludo), rewards, squads and event rides.
5. **Promote** their business or project to other members, always labelled "Sponsored".

The **owner (super admin)** controls everything from an admin console: which features are on or off, what each staff member may do, and a full, tamper-evident record of every staff action.

**Main principle: useful first.** Members should feel on their first visit that SaathChalo is useful to them, and every phase must give members (or the owner) something they can use straight away. Legal and paperwork features stay light until public launch; most are switches or settings the owner can turn up later. Security is the one thing never cut: it protects members and the owner, and it is cheap to get right from the start.

## 2. Key decisions

| Topic | Decision | Why |
|---|---|---|
| Product principle | **Useful first**: member value in every phase; paperwork light until launch; security never cut | The owner's main goal is that members feel the app is useful |
| App type | One responsive website: member area plus an `/admin` console in the same Angular app | One codebase and one design; works on phones and laptops |
| Backend | **Modular monolith**: one Spring Boot app split into strict modules, with the boundaries checked by Spring Modulith | Simple to build, run and pay for. A busy module (for example games) can become its own service later |
| Database | PostgreSQL 16 + PostGIS. Redis joins when games arrive (Phase 8) | Postgres handles maps, money, search and driver service areas; Redis holds live game rooms and rate limits |
| Real-time | WebSocket (STOMP) inside the backend | Chat, live games, booking updates, instant feature switches |
| Files | S3-compatible storage in an India region (local disk during development) | Private, encrypted and cheap, and the data stays in India |
| Owner control | Roles and permissions, feature switches, audit log | The owner decides what members see, and every staff action can be traced |
| Games | Free only. The server referees online games; computer opponents run in the browser | Indian law bans money games; browser opponents cost nothing to run |
| Payments | Razorpay (UPI, cards), payouts through Razorpay Route; in-app credits are a closed wallet | Standard in India; a closed wallet needs no RBI licence |
| Hosting | India region (Mumbai). Start on one small server, grow later | Privacy and CERT-In logging rules, and a low starting cost |

## 3. System overview

```
 Members (phone and laptop browsers)                Owner and staff (browser + 2FA)
                 │                                                │
                 ▼                                                ▼
 ┌────────────────────────────────────────────────────────────────────────────────────────────┐
 │ Cloudflare: DNS · CDN · firewall (WAF) · DDoS protection · bot check                       │
 └────────────────────────────────────────────────────────────────────────────────────────────┘
                 │ website files                                  │ /api (HTTPS), /ws (WebSocket)
                 ▼                                                ▼
 ┌────────────────────────────────┐          ┌────────────────────────────────────────────────┐
 │ Angular web app                │ ──API──► │ Spring Boot backend: one app, strict modules   │
 │  • member area                 │ ◄─live── │                                                │
 │  • /admin console (staff)      │          │ platform: auth · access · features · audit     │
 └────────────────────────────────┘          │           privacy · files · notifications      │
                                             │           payments · geo                       │
                                             │ business: user · verification · vehicles       │
                                             │           rides · bookings · chat · reviews    │
                                             │           safety · rentals · games · rewards   │
                                             │           community · promotions · support     │
                                             │           admin                                │
                                             └────────────────────────────────────────────────┘
                                                      │               │               │
                                                      ▼               ▼               ▼
                                                 PostgreSQL        Redis        File storage
                                                 + PostGIS         (Phase 8+)   (India region)

 Outside services: Razorpay (payments, deposits, payouts) · SMS gateway (DLT-registered)
                   KYC provider / DigiLocker · OpenStreetMap (places, routes) · Web Push
```

Modules talk to each other only through each module's public service classes and through **events** such as `BookingConfirmed`. Events are saved in the database before they are handled (the Spring Modulith event registry), so nothing is lost if the server restarts. For example, when a booking is confirmed, the chat module opens a conversation, the rewards module adds XP and the notifications module tells the driver, each on its own.

## 4. Backend modules

**Platform modules**, used by everything:

| Module | Job | Phase |
|---|---|---|
| `auth` | Phone OTP login, sessions, staff two-factor login | 1 ✓, 2 |
| `access` | Roles, permissions, staff accounts | 2 |
| `features` | Feature switches and who they apply to | 2 |
| `audit` | Tamper-evident log of sensitive actions | 2 |
| `privacy` | Consents, data download, account deletion, retention clean-up | 4 |
| `files` | Storage, image processing, document encryption | 1 ✓, 4 |
| `notifications` | In-app inbox, browser push, SMS, email, member preferences | 4, 11 |
| `payments` | Razorpay orders, webhooks, refunds, payouts, ledger, credits | 5 |
| `geo` | Place search and routes (OpenStreetMap), PostGIS helpers | 3 |

**Business modules**:

| Module | Job | Phase |
|---|---|---|
| `user` | Profiles and preferences | 1 ✓ |
| `verification` | ID, driving licence and vehicle document checks | 4 |
| `vehicles` | Cars registered by members, for carpooling and rentals | 3 |
| `rides` | Publish and search rides | 3 |
| `bookings` | Seat requests, confirmations, cancellations | 3 |
| `chat` | Messages between members of a ride or rental | 4 |
| `reviews` | Ratings after completed trips | 4 |
| `safety` | SOS, trip sharing, reports, blocking | 4 |
| `support` | Help tickets and the grievance officer workflow | 4 |
| `rentals` | Rental listings (self-drive or with driver), calendar, bookings, deposits, pickup/return checks, disputes | 6 |
| `drivers` | Driver partners: licence check, service areas, availability, rates, hire-a-driver bookings | 7 |
| `games` | Chess, ludo, rooms, matchmaking, ratings, leaderboards | 8 |
| `rewards` | XP, levels, badges, streaks, challenges, referrals | 9 |
| `community` | Squads (college, office and friend groups), event rides | 9 |
| `promotions` | Business profiles, sponsored posts, review, billing, statistics | 10 |
| `admin` | Admin APIs; they call the other modules' public services | 2 onwards |

Rules: each module owns its tables. Other modules refer to that data by ID only and never read those tables directly. A Spring Modulith test fails the build if one module reaches into another module's internals.

## 5. Super admin, staff and permissions

### Roles

- **Member**: everyone who signs up.
- **Support**: helps members; reads profiles and tickets.
- **Moderator**: handles reports, chats, reviews, verifications and promotions; can suspend members.
- **Admin**: runs day-to-day operations: rentals, refunds and everything a moderator does.
- **Super admin**: the owner. Can do everything, including feature switches, staff, prices and payouts.

The code checks **permissions**, not role names. Phase 2 ships these four staff roles; a screen for creating custom roles can come later if the owner needs one.

| Permission | Super admin | Admin | Moderator | Support |
|---|:-:|:-:|:-:|:-:|
| Turn features on and off (`features.manage`) | ✓ | | | |
| Add or remove staff, edit roles (`staff.manage`) | ✓ | | | |
| Prices, commission, app settings (`settings.manage`) | ✓ | | | |
| Approve payouts (`payouts.approve`) | ✓ | | | |
| See members (`users.view`) | ✓ | ✓ | ✓ | ✓ |
| Reveal a member's phone number, audited (`users.view_contact`) | ✓ | ✓ | | ✓ |
| Suspend or restrict members (`users.restrict`) | ✓ | ✓ | ✓ | |
| Review ID and licence checks (`verification.review`) | ✓ | ✓ | ✓ | |
| Moderate chats, reviews and promotions (`content.moderate`) | ✓ | ✓ | ✓ | |
| Approve rental listings, settle disputes (`rentals.manage`) | ✓ | ✓ | | |
| Refund payments (`payments.refund`) | ✓ | ✓ | | |
| Read the audit log (`audit.view`) | ✓ | ✓ | | |
| Answer help tickets (`support.tickets`) | ✓ | ✓ | ✓ | ✓ |

### How staff log in

1. Normal phone OTP login, followed by a 6-digit code from an authenticator app (TOTP). Two-factor login is required for every staff account. Recovery codes are shown once, at setup.
2. Staff sessions end after 15 minutes without activity. Sensitive actions (payouts, staff changes, turning off bookings) ask for the 2FA code again.
3. Later, optionally: allow `/admin` only from approved networks (Cloudflare Access).

### Creating the super admin

- The owner's phone number goes into the server configuration (`SUPER_ADMIN_PHONE` environment variable), never into code or git. When that number logs in, it becomes super admin and the audit log records it.
- Only a super admin can add or remove staff. The last super admin cannot be removed.

### Audit log

Every staff action, and every sensitive member action (login from a new device, account deletion request, payout bank change), is written to an append-only table: who, what, which record, before and after (with personal data masked), time, IP address and request ID. Each row stores a hash of the previous row, so any edited or deleted row is detectable. In production the app's database user is not allowed to change or delete these rows at all.

### Restricting members

Staff can suspend a member completely or block them from a single area (for example games, chat or promotions), with a reason and an optional end date. The member sees the reason and can appeal through support.

## 6. Feature switches

The super admin turns features on and off in the console. A change takes effect immediately, with no new release.

**How the app decides whether a switch is on for a member** (example: `games.chess`):

1. Is maintenance mode on? If yes, the switch is off.
2. Is the parent switch (`games`) on? If not, it is off.
3. Is the switch itself on, and are we inside its start and end time? If not, it is off.
4. Is this member restricted from this area? If yes, it is off.
5. Does the member fit the switch's audience (everyone, verified members, women, beta testers, a percentage of members, chosen cities)? That gives the final answer.

Each switch stores: on/off, audience, a description, and who changed it last (also written to the audit log). Phase 2 builds the audiences "everyone", "verified members", "women", "beta testers" and "chosen cities". Start and end times and percentage roll-outs come later, when they are needed.

**Planned switches.** A feature's switch stays off until its phase is finished.

| Area | Switches |
|---|---|
| Core | `signups`, `rides.publish`, `rides.booking`, `rides.ladies_only`, `maintenance_mode` |
| Payments | `payments.online`, `payments.cash`, `wallet.credits` |
| Trust | `chat`, `reviews`, `safety.sos`, `safety.trip_share` |
| Rentals and drivers | `rentals`, `rentals.self_drive`, `rentals.with_driver`, `rentals.instant_book`, `drivers.register`, `drivers.hire` |
| Games | `games`, `games.chess`, `games.ludo`, `games.vs_computer`, `games.online`, `games.ride_lobby`, `games.leaderboards` |
| Social | `rewards`, `rewards.referrals`, `community.squads`, `community.events`, `vibe_match` |
| Promotions | `promotions`, `promotions.self_serve`, `promotions.in_search`, `promotions.in_games`, plus the setting "free promotions until" for the launch offer |

**How switches are enforced**

- **Server.** Every API of a feature is marked, for example `@RequiresFeature("games.chess")`. If the switch is off for that member, the API answers "not available". The server never relies on the website to hide things.
- **Website.** `GET /api/features` returns the switches that are on for the current member, and menus, pages and buttons appear only for those. When the owner flips a switch, open browsers update within seconds. Live updates arrive with WebSocket in Phase 4; until then, on the next page load or within a minute.

## 7. Feature designs

### 7.1 Carpooling (Phase 3)

- A driver adds a vehicle, then publishes a ride: from, to, optional stops, date and time, seats, price per seat, ladies-only or not, and instant or manual booking.
- **Price cap.** The app suggests a fair price per seat based on distance and caps it, so rides stay cost-sharing rather than a taxi business. This matters for the legal position of pooling in private cars.
- **Search.** Routes are stored as PostGIS lines. A ride matches when the passenger's pickup and drop points are both near the route (by default within 10 km) and the pickup comes before the drop along the route.
- **Seats.** A booking locks the ride while it is saved, so two people can never get the last seat.
- Phone numbers are shown only to confirmed co-travellers. Until then, everyone uses in-app chat.

```
 REQUESTED ──driver accepts──► CONFIRMED ──departure──► IN_PROGRESS ──► COMPLETED ──► reviews
     │   └──declined / expired──► DECLINED       │
     └──passenger cancels──► CANCELLED ◄──cancel (refund by policy)
 (Instant booking goes straight to CONFIRMED.)
```

### 7.2 Trust and safety (Phase 4)

- Verification levels: phone ✓ → email → ID (DigiLocker or a KYC provider; **Aadhaar numbers are never stored**) → driving licence (drivers and renters) → vehicle papers.
- Ladies-only rides need an ID-verified woman, so the option can't be misused.
- Reviews only after a completed trip, from both sides.
- **SOS button** during a trip: shares the live location with chosen emergency contacts, alerts support, and offers a one-tap call to 112.
- **Trip sharing:** a time-limited link that family can open to follow the trip.
- Report and block from anywhere (profiles, chats, games, promotions). Reports go to a moderation queue in the console.

### 7.3 Payments and wallet (Phase 5)

- Razorpay for UPI and cards. SaathChalo never sees card numbers.
- Every payment, refund, commission and payout goes into a **double-entry ledger** that is matched against Razorpay every day.
- Webhooks are verified by signature and processed only once.
- **SaathChalo credits** (referral rewards, refunds as credit) can be spent only inside the app. They can't be withdrawn or transferred, and they are never a prize for games.

### 7.4 Car rentals (Phase 6)

- Owners list a registered vehicle with photos, price per hour or day, kilometres included, the extra-km charge, fuel rule, security deposit, pickup area, an availability calendar and house rules.
- Two kinds of listing:
  - **Self-drive**: the renter drives.
  - **With driver**: the owner drives, or picks a registered driver partner (section 7.5). The price then includes the driver's charges, plus a daily allowance on outstation trips.
- **Documents.** Owners upload registration, permit and insurance, and staff check them; checked listings get a "Verified" badge. Whether unchecked listings may go live is a super admin setting.
- Self-drive renters need a verified driving licence and a minimum age set by the owner (21 by default).
- The database itself prevents double-booking, through an exclusion constraint on vehicle + time range.
- **Pickup and return checks.** Both sides take time-stamped photos plus odometer and fuel readings in the app. These settle damage disputes.
- **Deposit.** Held on the card where the payment method allows it; otherwise charged and refunded after the return. Damage and extra kilometres are deducted only with photo evidence, and disputes go to staff.

```
 REQUESTED ─► ACCEPTED ─► PAID (deposit held) ─► PICKED UP (photo check) ─► RETURNED (photo check) ─► COMPLETED
     └─► DECLINED / EXPIRED     └─► CANCELLED (refund by policy)                 └─► DISPUTE ─► settled by staff
```

### 7.5 Driver partners and hire-a-driver (Phase 7)

- **Who can register:** anyone with a valid driving licence. They add a photo, the licence (the number is stored encrypted, together with its class, such as LMV or transport), years of experience, languages and, optionally, a police verification certificate.
- **Service areas:** the driver picks the cities and areas they serve, either a point on the map with a radius or named localities. These are stored as PostGIS shapes, so the app can instantly find every driver who covers a pickup point.
- **Availability and rates:** an online/offline switch and a calendar; rates per hour (local), per day, and per day for outstation trips with a daily allowance for food and stay; night charges.
- **What members can book:**
  - **A driver for their own car**: local by the hour, a full day, or outstation.
  - **A car with driver**: the rental owner picks a driver partner, or the member chooses one while booking.
- **Matching:** drivers who cover the pickup point, are free at that time and hold the right licence class, sorted by distance, rating and price. The member chooses one, or the app offers the trip to the best matches one after another.
- **Trust:** licence checked by staff (or a KYC provider later), a "Verified" badge, ratings from both sides, SOS and trip sharing on every trip, and payment to the driver after the trip.

```
 REQUESTED ─► ACCEPTED ─► ON THE WAY ─► TRIP STARTED ─► COMPLETED ─► payout and reviews
     ├─► no answer in time ─► offered to the next matching driver
     └─► CANCELLED (fees by policy, before the trip starts)
```

### 7.6 Games (Phase 8)

- **Chess and ludo, free to play.** No betting, entry fees, cash prizes or paid advantages. The Online Gaming Act, 2025 bans money games.
- Ways to play:
  - **Against the computer**: easy, medium or hard. It runs in the browser, so it works on a slow connection and costs nothing to host.
  - **With friends**: room code or invite link.
  - **Quick match**: automatic matchmaking with someone online.
  - **With co-travellers**: a game room opens automatically for the confirmed members of a ride.
- **Automatic helpers.** One-tap dice roll and token moves. When only one move is possible, it plays itself. If a player leaves or runs out of time, a computer player takes over so the game can finish.
- **Fair play.** Online games are refereed by the server: it checks every move and rolls every die with a secure random generator, so a player's browser can't cheat.
- Chess rules on the server come from an open-source Java library (chesslib, Apache 2.0 licence). The computer opponent starts as our own engine. Stockfish (much stronger) is an option later, but its GPL licence means publishing the source of that part.
- Ratings and leaderboards count online games only. In-game chat is emojis and set phrases only, which keeps it safe.
- Original board, pieces and sounds. Nothing is copied from existing game apps.

### 7.7 Rewards and social: the Gen-Z layer (Phase 9)

| Feature | What it does |
|---|---|
| XP, levels, badges | XP for rides, reviews and streaks; badges such as "Eco Hero", "Night Owl", "Road Tripper" |
| Streaks and challenges | Weekly goals, such as "3 shared rides this month" |
| CO₂ and money saved | Personal dashboard plus a shareable story card (an Instagram-ready image) |
| Referrals | Invite friends; both get ride credits after the friend's first completed ride |
| Squads | Private groups for a college, office or friend circle, verified by email domain; rides visible only inside the squad |
| Event rides | Pages for concerts, fests and matches, listing every ride going there |
| Vibe match | A match score from music taste, chattiness and interests, shown on ride cards |
| Leaderboards | City-wise eco champions and game ratings (opt-in, nickname only) |

Anti-abuse: XP and referral rewards are given only for completed trips that both sides confirm, with device and payment checks to stop fake accounts.

### 7.8 Promotions: members promote their business (Phase 10)

- Any verified member, driver or passenger, can create a **business or creator profile** and **promotions** with a picture, text, link, category, cities and dates.
- Flow: draft → automatic checks (banned categories and words, link safety) → moderator review → payment (if paid) → live → ended. Super admin switches control whether promotions run, where they appear and what they cost.
- **Launch offer:** promotions are free for the first month after public launch (the super admin setting "free promotions until"), but are still reviewed before they go live.
- Where they appear: the Discover feed, between search results, on the screen after a ride, and optionally in game lobbies. Always labelled **Sponsored**, with limits on how often the same member sees them, and never inside chats.
- Targeting by city and broad interest only, never by sensitive data. Advertisers see totals (views, clicks), never who saw their promotion.
- Not allowed: betting and money games, alcohol, tobacco, adult content, weapons, misleading claims, and anything against the ASCI code.
- Paid promotions get GST invoices.

### 7.9 Notifications (Phases 4 and 11)

- An in-app inbox for everything; browser push notifications for bookings, chats and game turns; SMS only for login codes and SOS; email for receipts.
- Members choose which kinds they receive. Marketing messages are sent only with consent.

## 8. Security architecture

**No system is unhackable.** The aim is that no single mistake exposes everything, attacks are spotted quickly, and incidents are handled within the legal deadlines. The target checklist is OWASP ASVS Level 2.

| Layer | Protection |
|---|---|
| Edge | Cloudflare firewall and DDoS protection; HTTPS only (HSTS); bot check (Turnstile) on login after repeated attempts |
| Login | OTP limits (done); staff 2FA; short-lived tokens held only in memory; HttpOnly refresh cookie; re-check for sensitive actions; new-device alerts |
| Access | Deny by default; a permission check on every API; every query limited to the record's owner or participants, so changing an ID in a request can't reveal someone else's data (tested automatically) |
| Abuse | Rate limits per IP, member and phone number; SMS-pumping limits; automatic flags for suspicious patterns |
| Input and output | Every request validated; Angular escapes all output; strict Content Security Policy and security headers |
| Files | Images re-encoded, documents scanned and encrypted, private storage, short-lived download links |
| Money | Signed webhooks, idempotency keys, double-entry ledger, daily reconciliation |
| Real-time | WebSocket connections need a login; members can join only their own rooms; message size and rate limits |
| Data | Encrypted disks and backups; sensitive fields (licence and RC numbers, documents) encrypted by the app; phone numbers masked in the console unless a permitted staff member reveals one (audited) |
| Secrets | Environment variables locally, a secrets manager in production, secret scanning on every change |
| Supply chain | Pinned versions, a one-week minimum age for new packages, dependency and container scanning, software bill of materials |
| Operations | Central logs kept at least 1 year in India, alerts, encrypted backups with tested restores, an incident plan (CERT-In report within 6 hours) |
| Testing | Security tests in the test suite, an automated web scan (OWASP ZAP), and an external penetration test before public launch |

## 9. Privacy and Indian law

**Approach (the owner's decision): useful first.** Not every legal feature is built up front. There are two groups:

- **Built in from the start**, because it is cheap and protects members and the owner: strong security, no Aadhaar numbers stored, free games only, a privacy policy and terms page, "delete my account", and a grievance contact.
- **Added before or after public launch, when the owner decides**: full consent records, data download, document requirements for rentals and drivers, aggregator licences, GST and TCS set-up, and the CERT-In incident runbook. Most of these are switches or settings, so they can be turned up later without rebuilding anything.

The table is the reference for that later work.

| Law or rule | What it means for SaathChalo | What we build |
|---|---|---|
| Digital Personal Data Protection Act 2023 and DPDP Rules 2025 (notified November 2025; most duties apply from about May 2027) | Clear notice, consent for each purpose, member rights, security, breach reporting; this app is 18+ only | Privacy notice (English and Hindi), consent per purpose with history, a privacy centre (download data, correct, delete account, withdraw consent, nominate someone), retention clean-up, breach runbook, age check |
| IT Act 2000 and IT Rules 2021 | Rules for platforms with member content (chat, reviews, promotions) | Terms of use, privacy policy, grievance officer contact, complaints acknowledged within 24 hours and resolved within 15 days, takedown workflow |
| CERT-In Directions 2022 | Report cyber incidents within 6 hours; keep logs for 180 days in India; synchronised clocks | Incident runbook, logs kept in an India region, NTP time sync |
| Online Gaming Act 2025 | Money games and their advertising are banned | Free games only; no stakes or cash prizes; money games blocked as a promotion category |
| Motor Vehicles Aggregator Guidelines 2025 and state rules | Pooling in private cars needs state permission and, in many states, an aggregator licence; driver checks and a panic button are expected; data kept in India | Price cap (cost-sharing), driver verification, SOS, India hosting, a per-city switch so pooling runs only where it is allowed |
| Rent-a-Cab scheme / commercial registration | Paid self-drive rental generally needs commercially registered cars and permits | Document check before a rental listing goes live |
| Consumer Protection (E-Commerce) Rules 2020 | Marketplaces must show seller details and refund/cancellation terms, handle complaints, and allow no fake reviews | Owner and advertiser details on listings, clear policies, reviews only from real trips, grievance flow |
| Aadhaar Act and UIDAI rules | Don't store Aadhaar numbers | Use DigiLocker or a KYC provider; keep only the "verified" status and a masked number |
| RBI rules | No storing card data; closed in-app wallets need no licence | Razorpay handles cards; credits can't be withdrawn |
| GST | Tax and invoices on commission and promotion sales; e-commerce TCS rules | Invoice generation; a chartered accountant confirms rates and TCS |
| ASCI code | Ads must be honest and clearly labelled | "Sponsored" label, review before going live, banned categories |

## 10. Data storage, retention and backups

| Data | Kept for | Then |
|---|---|---|
| Login codes | 1 day | Deleted |
| Sessions | Until expiry + 7 days | Deleted |
| Chats | 6 months after the trip (longer if reported) | Deleted |
| Live location (SOS, trip sharing) | 24 hours after the trip | Deleted |
| ID and licence images | 30 days after the check | Deleted; the result and a masked number are kept |
| Security logs | At least 1 year | Deleted |
| Payments and invoices | As tax law requires (about 8 years) | Archived |
| Deleted accounts | 30-day grace period | Personal data erased or anonymised |

Backups: daily encrypted database backups plus point-in-time recovery in production, kept for 30 days, with a restore test every month.

## 11. Frontend architecture

```
frontend/src/app/
  core/       auth, API clients, feature switches, realtime (WebSocket), errors, theme
  shared/     design system: buttons, inputs, dialogs, bottom sheets, avatars, icons, skeletons
  features/   home · rides · bookings · rentals · drivers · games · rewards · community
              promotions · profile · privacy centre · support · chat · notifications
  admin/      console, lazy-loaded with its own layout: dashboard · members · verifications · rides
              rentals · drivers · promotions · games · reports and tickets · feature switches
              staff and roles · settings · audit log
```

- State lives in signals; each feature has a small signal-based store.
- `featureGuard('games')` on routes and `*appIfFeature="'games'"` in templates hide anything that is switched off.
- The admin console loads only for staff (role check plus 2FA), so normal members never download it. The server still checks every request.
- Games draw boards with SVG or canvas, and computer opponents run in Web Workers so the page stays smooth.
- Hindi and English with an in-app language switch (Phase 11); accessibility to WCAG 2.2 AA; page-size budgets enforced at build time.

## 12. Deployment and operations

| | Starter (launch) | Growth |
|---|---|---|
| Where | One server in Mumbai (Docker Compose: app, PostgreSQL, Redis, Caddy for HTTPS) behind Cloudflare | AWS Mumbai: load balancer, 2+ app containers, managed PostgreSQL (multi-AZ), managed Redis, S3, CloudFront |
| Rough cost | ₹3,000–6,000 per month | ₹25,000+ per month |
| Backups | Daily, to S3 in Mumbai | Automatic point-in-time |

- The website and the API share one domain (`/api` goes to the backend), so login cookies stay first-party.
- GitHub Actions run the tests and security scans on every change. A deployment needs the owner's manual approval.
- Monitoring: health checks, metrics (Micrometer with Prometheus and Grafana), uptime alerts, error tracking.
- Scheduled jobs (retention clean-up, reminders, payouts, reconciliation) run exactly once, even with several servers (ShedLock).

## 13. Testing, and the "done" checklist for every phase

- Backend unit and integration tests against real PostgreSQL, plus the module-boundary test (Spring Modulith).
- Permission-matrix tests (every admin API × every role) and ownership tests for member data.
- Frontend unit tests, and a production build within its size budgets.
- A real-browser walkthrough at phone and laptop sizes, in light and dark mode.
- The phase's security checklist; this file, the README and CLAUDE.md updated.
- The owner reviews it. Commit and push only when the owner asks.

## 14. Roadmap

| Phase | What you get | Sessions |
|---|---|---|
| 1 ✓ | Setup, phone login, profiles, home page | Done |
| 2 ✓ | **Owner control centre**: super admin, staff roles, 2FA, feature switches, audit log, member management | Done |
| 3 | **Carpooling**: vehicles, place search, publish/search/book rides, price cap | 4–5 |
| 4 | **Trust, safety and privacy**: chat, reviews, verification, SOS, trip sharing, reports, privacy centre, legal pages, help tickets | 4–5 |
| 5 | **Payments and wallet**: Razorpay, refunds, ledger, credits, payouts | 3 |
| 6 | **Car rentals**: self-drive and with driver | 5–6 |
| 7 | **Driver partners**: licensed drivers register with their service areas; members hire a driver for their own car or a rental | 3–4 |
| 8 | **Games**: chess and ludo (computer, friends, quick match, ride lobby), leaderboards | 4–6 |
| 9 | **Gen-Z layer**: XP, badges, streaks, referrals, CO₂ card, squads, events, vibe match | 3–5 |
| 10 | **Promotions marketplace** (free for the first month after launch) | 3–4 |
| 11 | **Launch**: Hindi, notifications, security hardening, fixes from an external penetration test, monitoring, deployment | 4–5 |

That is about 36–46 working sessions in total: roughly 2.5 to 3 months of steady work. Some non-coding steps take time in parallel: Razorpay KYC, SMS DLT registration, a KYC provider account, legal review and hosting.

**Why this order.** The control centre comes first (the owner's decision) because every later feature plugs into its switches and permissions. It is kept lean, so carpooling, the part members feel, arrives quickly. Trust and payments then make the core product usable. Rentals, driver partners, games, the Gen-Z layer and promotions are added one at a time, and the owner switches each one on when ready.

## 15. How Phase 2 (the control centre) will be built

Phase 2 is kept lean (3 sessions) so that carpooling, the part members feel, comes soon after.

**Step 1: foundations** (session 1)
- Reorganise the existing code into modules and add the Spring Modulith boundary test.
- Turn on permission checks in the backend; add request IDs and security headers.
- Database migration `V2`: the four staff roles and their permissions, staff role assignments, member restrictions, feature switches, the audit log, and encrypted staff 2FA secrets.
- Super admin creation from the `SUPER_ADMIN_PHONE` setting, plus the audit log with its hash chain.

**Step 2: staff security and switches** (session 2)
- Two-factor login with an authenticator app: setup by QR code, recovery codes, staff login, 15-minute staff sessions, and a re-check before sensitive actions.
- The feature switch engine from section 6: `@RequiresFeature` and `GET /api/features`, with every planned switch registered and off.
- Admin APIs for the dashboard, members (search, view, suspend or restrict), switches, staff and the audit log, each with its permission check and audit entry.

**Step 3: admin console and checking** (session 3)
- `/admin` with its own layout: a sidebar on laptops, a compact menu on phones.
- Pages: **Dashboard** (members, sign-ups per day, active switches), **Members** (search, profile, suspend or restrict with a reason), **Feature switches** (cards with on/off and audience), **Staff**, **Audit log**, **My security** (2FA).
- Member side: menus and pages hide when their feature is off; a maintenance banner; a "suspended account" screen.
- Permission-matrix and ownership tests, rate limits on admin APIs, an audit-chain test.
- Browser walkthrough: the owner logs in with 2FA → turns "Games" on and off → the member site changes → adds a moderator who can't touch switches → suspends a test member → every action appears in the audit log.
- Update the docs, show the owner screenshots, let the owner try it. Commit and push only if the owner asks.

**Later, when needed:** a custom-roles screen, switch start/end times, percentage roll-outs, and restricting `/admin` to approved networks.

**Done when** the owner can log in as super admin with 2FA, flip any switch and see the website change, give a staff member limited rights, suspend a member, and find every one of those actions in the audit log.

**What the owner prepares:** an authenticator app on their phone; their super-admin phone number in a local settings file that is never committed; the database set up with `scripts/setup-db.sql`.

## 16. The owner's decisions (19 November 2026)

| Topic | Decision |
|---|---|
| Main motto | **Useful first**: members must feel the app is useful; don't front-load every legal rule (security stays strong) |
| Order | Owner control centre first, then the roadmap in section 14 |
| Admin console | At `/admin` in the same website |
| Staff roles | Super admin, Admin, Moderator, Support |
| Games | Free only, with our own chess engine |
| Rentals | Self-drive **and** car with driver |
| Drivers | Licensed drivers can register with the areas they serve (section 7.5) |
| Promotions | Paid and reviewed before going live; free for the first month after launch |
| Gen-Z features | All of section 7.7 |
