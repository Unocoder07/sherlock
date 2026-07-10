# Multi-stage Next.js build. Build context is ./frontend.
# The browser bundle bakes NEXT_PUBLIC_* at build time, so the gateway WS URL is a
# build ARG (defaulting to the host gateway for the local demo).

# ── deps ──
FROM node:20-alpine AS deps
WORKDIR /app
COPY package.json ./
RUN npm install

# ── build ──
FROM node:20-alpine AS build
WORKDIR /app
ARG NEXT_PUBLIC_GATEWAY_WS_URL=http://localhost:8094/ws
ENV NEXT_PUBLIC_GATEWAY_WS_URL=$NEXT_PUBLIC_GATEWAY_WS_URL
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

# ── runtime (standalone server) ──
FROM node:20-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
ENV PORT=3000
# Bind all interfaces (Next standalone otherwise binds the container hostname,
# so localhost/127.0.0.1 healthchecks inside the container would fail).
ENV HOSTNAME=0.0.0.0
RUN addgroup -S sherlock && adduser -S sherlock -G sherlock
COPY --from=build /app/public ./public
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
USER sherlock
EXPOSE 3000
CMD ["node", "server.js"]
