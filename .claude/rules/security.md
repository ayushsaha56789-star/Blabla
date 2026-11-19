# Security rules

## The owner's computer

- Never use `sudo`. Give the owner the exact command to run instead.
- Do not edit shell start-up files (`~/.bashrc`, `~/.profile`, …), `~/.gitconfig`, `~/.ssh` or system files. Do not create, replace or delete SSH keys.
- Install tools only inside the project or under `~/.local/share`, and only after saying why. Node 24 already lives in `~/.local/share/node-v24.22.3`.
- Download only from official sources, and verify checksums when they are published. Never pipe a downloaded script into a shell (`curl … | bash`).
- Put temporary databases, servers and test files in the session scratchpad. Stop everything you started before you finish, and never stop processes you did not start.

## Secrets

- No secrets in code, commits, logs or chat. Production values come from environment variables: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `STORAGE_DIR`, `CORS_ALLOWED_ORIGINS`.
- Development-only defaults belong only in `application-dev.properties` and the test configuration, clearly labelled as such.
- Never print the contents of `.env` files or private keys. If you find a real secret in the repository, stop and tell the owner.

## App security that must never be weakened

- **Login codes:** stored only as HMAC hashes; valid 5 minutes; 5 wrong tries; 30 seconds between resends; 5 codes per hour. `app.otp.expose-code=true` is allowed only in the dev and test profiles, never in production.
- **Sessions:** access tokens (JWT, 30 minutes) live only in memory in the browser, never in `localStorage`. Refresh tokens live only in an HttpOnly, SameSite=Strict cookie scoped to `/api/auth` (Secure in production), are stored hashed, and rotate on every use.
- **Access:** every API endpoint requires login unless `SecurityConfig` lists it as public on purpose. A new public endpoint needs a stated reason.
- **Input and errors:** validate every request body. Errors are problem details with friendly messages. Never return stack traces or internal error text.
- **Privacy:** public profiles never show phone, email or exact date of birth. A phone number is shared only between a driver and a passenger with a confirmed booking (phase 2 onwards).
- **Uploads:** images are decoded and re-encoded on the server (this removes GPS data). Check type and size, let the server choose file names, and serve files with `nosniff`.
- **Redirects:** only to in-app paths (`safeReturnUrl`).
- **SQL:** only JPA or parameterised queries. Never build SQL by joining strings.
- **Payments (phase 4):** never store card or UPI details, and always verify Razorpay webhook signatures.

## Data

- Never run DROP, TRUNCATE or DELETE on the real `saathchalo` database, and never delete uploads, without the owner's OK. Tests use `saathchalo_test`.
- Never edit a Flyway migration that has already run. Add a new one.

## Dependencies

- Prefer well-known packages, pin versions, and avoid packages that run install scripts.
- Install npm packages with a one-week release cut-off for supply-chain safety, e.g. `npm_config_before=<date one week ago> npm install …`.
- Don't add a dependency for something a few lines of code can do.
