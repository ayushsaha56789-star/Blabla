# SaathChalo — instructions for Claude

SaathChalo is an India-focused intercity carpooling **web application**: a responsive website for laptop and phone browsers, not a native mobile app. It works like BlaBlaCar but has its own name, logo and look. Never copy BlaBlaCar's name, branding, text or screens.

The owner publishes to GitHub as `ayushsaha56789-star` (repo `ayushsaha56789-star/Blabla`). Explain things in plain, simple English.

## Rules that always apply

1. **Never commit or push unless the owner asks for it in the current conversation**, with words like "commit", "push", "publish", or the `/publish` command. Finishing a task or a phase is *not* permission, and neither is "start" or "continue". When work is done, describe the changes and offer to commit/push.
2. **Plan first, then wait.** Before a new phase, a big feature or a change of approach: explain what you will build, how, roughly how long it takes and what the owner must do, then wait for the owner's "start" or "yes".
3. **Stay inside this project.** No `sudo`. No edits to `~/.bashrc`, `~/.profile`, `~/.gitconfig` or `~/.ssh`, no global git config, no global installs. When something needs admin rights or a change outside the project, give the owner the exact command and let them run it.
4. **Protect secrets and data.** Never put passwords, keys or tokens in code, logs, commits or messages. Never delete data (database rows or tables, uploads, git history) without the owner's OK.
5. **Never work around a safety block.** `.claude/settings.json` makes risky commands (commit, push, sudo, history rewrites, data deletion, …) pause for the owner's approval. If one is paused or denied, do not retry it another way: explain why you wanted it and ask.
6. **Be honest about results.** Say what you tested and how. Report failures with the real output. Never say something works without checking it.

More detail, loaded automatically with this file: `.claude/rules/git-and-github.md`, `.claude/rules/security.md`, `.claude/rules/working-agreement.md`.

## Project status and plan

The full architecture, roadmap and the owner's decisions are in **`docs/ARCHITECTURE.md`**. Follow it, and start each phase only after the owner says "start".

**Main motto: useful first.** Members must feel the app is useful. Every phase should give them (or the owner) something they can use straight away. Keep legal and paperwork features light and switchable until launch, but never weaken security.

| Phase | Scope | Status |
|---|---|---|
| 1 | Setup, phone-OTP login, profiles, home page, light/dark mode | Done |
| 2 | Owner control centre: super admin, staff roles, 2FA, feature switches, audit log, member management | Done |
| 3 | Carpooling: vehicles, place search, publish/search/book rides | Done |
| 4 | Trust, safety, privacy: chat, reviews, verification, SOS, privacy centre, support | Done |
| 5 | Payments and wallet (Razorpay) | Done |
| 6 | Car rentals: self-drive and with driver | Done |
| 7 | Driver partners: licensed drivers with service areas, hire a driver | Done |
| 8 | Games: chess and ludo, free | Done |
| 9 | Gen-Z layer: XP, badges, referrals, squads, events, vibe match | Done |
| 10 | Promotions: members promote a business, staff checks, Sponsored cards (free during the launch offer) | Done |
| 11 | Launch: notification inbox, Hindi (beta), real SMS/email, hardening, Docker deployment, CI | Done |

All planned phases are built. What's left is the launch checklist in `docs/DEPLOYMENT.md` (mostly the owner's accounts and reviews) and the follow-ups at the end of the roadmap in `docs/ARCHITECTURE.md` (browser push, more Hindi, penetration test fixes). Plan each one with the owner first.

## Stack and commands

- **Backend** `backend/`: Java 25, Spring Boot 4.1, PostgreSQL 16 + PostGIS, Flyway, Maven wrapper.
  - Run: `cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` (port 8080, Swagger at `/swagger-ui.html`).
  - Test: `cd backend && ./mvnw test`. Needs the `saathchalo_test` database from `scripts/setup-db.sql`. If the owner hasn't created it, start a private PostgreSQL in the scratchpad instead (`initdb`, then `pg_ctl` on port 5544 with `-c unix_socket_directories=''`), run tests with `TEST_DB_URL=jdbc:postgresql://localhost:5544/saathchalo_test`, and stop it afterwards.
  - Several Claude sessions may work here at once. Before starting a database or server, check the port (`ss -ltnp`); if 5544, 8080 or 4200 is taken, use another (e.g. 5545, `--server.port=8081`, `ng serve --port 4201` with a proxy file in the scratchpad). Never stop a process another session started.
  - A backend started with `spring-boot:run` (devtools) restarts whenever `backend/target/classes` changes, so another session's `./mvnw test` reloads it with half-finished code and runs new migrations on its database. While migrations are still changing, build and test in a copy (`rsync -a --exclude target backend/ <scratchpad>/backend-build/`).
- **Frontend** `frontend/`: Angular 22 (standalone, zoneless, signals), Tailwind CSS 4, Angular CDK, Vitest.
  - Node 24 is installed at `~/.local/share/node-v24.22.3/bin` and is not on Claude's PATH: start shell commands with `export PATH="$HOME/.local/share/node-v24.22.3/bin:$PATH"`.
  - Run: `npm start` (port 4200, proxies `/api` to 8080). Test: `npm test -- --watch=false`. Build: `npx ng build`.

## Conventions

- Backend is package-by-feature under `com.saathchalo` (`auth`, `user`, `access`, `features`, `audit`, `admin`, `geo`, `vehicles`, `rides`, `chat`, `reviews`, `safety`, `moderation`, `support`, `privacy`, `verification`, `payments`, `rentals`, `drivers`, `games`, `rewards`, `community`, `promotions`, `notifications`, `files`, `config`, `common`); Spring Modulith's test fails the build if a module reaches into another module's `internal` package. Controllers stay thin; services hold the logic and transactions; request/response types are records in `*Dtos.java`.
- Errors: throw `ApiException` with a stable `code` and a friendly message; the API answers with RFC 9457 problem details.
- Spring Boot 4 uses Jackson 3: optional true/false fields in request records must be `Boolean`, not `boolean`, or a missing field fails the whole request.
- Database changes only through a **new** Flyway migration `V<n>__description.sql`. Never edit one that has already run.
- Every module that stores data about members implements `common.PersonalData` (`export` for "Download my data", `erase` for account deletion).
- Money is in paise (`long`) and every movement goes through `payments.Ledger.post(...)` as a balanced journal. Never call a payment gateway while holding a ride's row lock. Anything new that members pay for implements `payments.PayableKind` (see `RidePayments`, `RentalPayments`, `DriverPayments`) and starts its checkout with `PaymentService.startPayment(...)`.
- Frontend: state in signals (the app is zoneless, so plain fields don't re-render), `OnPush` components, lazy-loaded routes, API calls in `core/*-api.ts`, errors shown through `problemOf()`. Styling uses Tailwind classes plus the shared classes in `src/styles.css` (`.btn`, `.card`, `.input`, `.chip`, `.segmented`, `.switch`, `.data-table`, `.role-*`). Icons come from `app-icon`. Every page must work in the white and the black theme and on phones.
- **New features plug into the control centre:** add a switch to `FeatureCatalog` (backend), mark the APIs `@RequiresFeature("key")`, gate menus/routes with `FeaturesService.isOn()` / `featureGuard()`. Staff APIs go under `/api/admin/**` with `@PreAuthorize(Perm.X)`; sensitive ones also get `@RequiresConfirmation` (fresh 2FA code). Services record sensitive actions with `AuditService.record(...)`.
- Lists that pop out of a field or button and must never be covered or cut off (like the place picker) render in the CDK overlay (`cdkConnectedOverlay`, see `shared/place-input.ts`), not as `absolute` children of cards. A bar fixed to the bottom of a phone screen gets the `appBottomBar` directive so it never covers the page's end. On phones the top bar has room for four icons; put anything more in the menu.
- Tell members about things they must act on with `notifications.Notifier.send(member, Notice.trips(...))` inside the same transaction (links: in-app paths only).
- New interface text gets its Hindi in `frontend/src/app/core/i18n-hi.ts` (keys are the exact English on screen); mark members' own words (messages, bios, promotions) `translate="no"`.
- Staff console tokens live only in memory: a full page reload asks for the 2FA code again (by design). In browser tests, navigate the console with the sidebar instead of reloading.
