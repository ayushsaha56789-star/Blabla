# The website (Angular) served by Caddy, which also gets the HTTPS certificate and passes /api to the backend.
# Build context: the repository root (see docker-compose.yml).

FROM node:24-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
# --ignore-scripts: no package may run code while installing.
RUN npm ci --ignore-scripts --no-audit --no-fund
COPY frontend/ ./
RUN npx ng build

FROM caddy:2-alpine
COPY deploy/Caddyfile /etc/caddy/Caddyfile
COPY --from=web /web/dist/saathchalo-web/browser /srv
