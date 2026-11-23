# Putting SaathChalo online

This guide takes SaathChalo from this repository to a live website on one server, with HTTPS, backups and updates.
It uses the files in `deploy/`: Docker Compose runs three containers:

- **db**: PostgreSQL with PostGIS;
- **app**: the API;
- **web**: Caddy, which serves the website, gets the HTTPS certificate and passes `/api` to the app.

Only ports 80 and 443 are open to the internet.

## 1. What you need first

| What | Why | Notes |
|---|---|---|
| A server in India (Mumbai) | Runs everything | Ubuntu 24.04, 2 vCPU, 4 GB RAM, 40 GB disk is enough to start (about ₹3,000–6,000 a month) |
| A domain name | The site's address | Point an **A record** (and `www`) at the server's IP address |
| An SMS account | Login codes and SOS messages | Twilio is built in. Indian numbers need **DLT registration** (sender ID and message templates) with a telecom operator first; this takes days to weeks, so start early |
| An email (SMTP) service | Email confirmation codes | Any provider with SMTP: Amazon SES, Zoho Mail, Brevo, Google Workspace… Set up SPF and DKIM for your domain so emails don't land in spam |
| Razorpay (optional at first) | Online payments | Finish Razorpay's business verification (KYC). Until then keep `PAYMENTS_PROVIDER=none`: members pay drivers directly |

## 2. Prepare the server

1. Log in as an ordinary user with `sudo` rights (not root). Use SSH keys, not passwords.
2. Install Docker Engine and the Compose plugin with Docker's official instructions for Ubuntu:
   https://docs.docker.com/engine/install/ubuntu/
3. Open only SSH and the web ports:
   ```bash
   sudo ufw allow OpenSSH && sudo ufw allow 80 && sudo ufw allow 443 && sudo ufw enable
   ```
4. Turn on automatic security updates: `sudo apt install unattended-upgrades`.

## 3. Get the code and fill in the settings

```bash
sudo mkdir -p /opt/saathchalo && sudo chown $USER /opt/saathchalo
git clone https://github.com/ayushsaha56789-star/Blabla.git /opt/saathchalo   # use a read-only deploy key if the repo is private
cd /opt/saathchalo/deploy
cp .env.example .env
chmod 600 .env
nano .env
```

Fill in every value. Make random secrets with `openssl rand -base64 32`. The settings are:

- `SITE_ADDRESS`: your domain, without `https://`.
- `DB_PASSWORD`, `JWT_SECRET` and `DATA_ENCRYPTION_KEY`: random values. **Keep a safe copy of `DATA_ENCRYPTION_KEY`**: without it, encrypted data such as licence numbers and 2FA secrets can't be read back.
- `SUPER_ADMIN_PHONE`: your number, `+91…`.
- Twilio, for text messages: `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, and `TWILIO_FROM` (or a messaging service).
- SMTP, for email: `SMTP_HOST`, `SMTP_USERNAME`, `SMTP_PASSWORD` and `EMAIL_FROM`.
- Grievance officer details. The IT Rules 2021 require them.
- `PROMOTIONS_FREE_UNTIL`: one month after launch.

`.env` never goes into git: `deploy/.gitignore` keeps it out.

## 4. Start it

```bash
cd /opt/saathchalo/deploy
docker compose up -d --build
docker compose ps            # all three should be "running" (db "healthy")
docker compose logs -f app   # watch the first start; Ctrl+C to stop watching
```

The first start creates the database tables. Caddy gets the HTTPS certificate within a minute once DNS points at the server.

**If the app stops with "SaathChalo can't start with these production settings"**, it lists each setting to fix.
It refuses to run with settings that are only safe on a developer's computer:

- login codes shown on screen;
- pretend payments;
- text messages and emails written to the log;
- short secrets;
- a site address without HTTPS.

Fix `.env`, then run `docker compose up -d` again.

## 5. First steps on the live site

1. Open `https://<your domain>` and log in with `SUPER_ADMIN_PHONE`. You become super admin.
2. Account menu → **Staff console**. Set up two-step verification, and **save the recovery codes** somewhere safe and offline.
3. **Feature switches**: switch on what you want members to use (rentals, drivers, games, rewards, squads and events, vibe match, promotions, notifications…).
4. Add your team under **Staff** (they log in once first).
5. If you use Razorpay, add the webhook: `https://<your domain>/api/payments/webhook`, with the events listed in the README.

## 6. Backups

`deploy/backup.sh` saves the database and the uploads to `deploy/backups/`, keeping two weeks. Run it every night:

```bash
chmod +x /opt/saathchalo/deploy/backup.sh
crontab -e
# add this line:
30 2 * * * cd /opt/saathchalo/deploy && ./backup.sh >> backups/backup.log 2>&1
```

**Copy the backups off the server** (another server, or encrypted cloud storage such as S3 or Backblaze B2). A backup kept only on the same server is lost with it.

**Test a restore every month.** Do it on a separate test server, never on the live site, because a restore replaces all data:

```bash
docker compose exec -T db pg_restore -U saathchalo -d saathchalo --clean --if-exists < backups/db-YYYY-MM-DD.dump
```

## 7. Updating to a new version

```bash
cd /opt/saathchalo/deploy
./backup.sh                       # always back up first
git -C .. pull
docker compose up -d --build      # database changes (Flyway migrations) run automatically on start
docker compose logs -f app
```

Every push to GitHub runs the tests and a production build in GitHub Actions (`.github/workflows/ci.yml`). Only deploy a version whose checks are green.

## 8. Keeping an eye on it

- **Uptime:** use a free uptime monitor (for example UptimeRobot) on `https://<your domain>/api/features` and turn on its email alerts.
- **Logs:** `docker compose logs --since 1h app`. Logs never contain login codes, message texts or card details.
- **Disk:** `df -h` now and then; uploads and backups grow over time.
- **Payments:** the console's Payments page shows three health checks (ledger, wallets, held money). They should always be green.

## 9. Before the public launch

These are not code, and they take time:

- **SMS and email:** DLT registration for SMS, then SPF/DKIM for email.
- **Razorpay:** business verification (KYC). Decide the service fee: `app.payments.service-fee-percent`, currently 0.
- **Legal:**
  - have a lawyer review the Terms, Privacy policy, Community guidelines and Refund rules (drafts in the app);
  - appoint the grievance officer.
- **GST:** a chartered accountant confirms GST on fees and on paid promotions before they are switched on.
- **Security:** an external penetration test, and fixing what it finds.
- **Hindi:** a native speaker reviews the Hindi wording (see the README).
- **Rehearsal:** practise a restore from backup, and invite a small group of beta testers first. Feature switches can limit a feature to beta testers.
