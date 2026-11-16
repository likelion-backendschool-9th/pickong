# Build context: frontend/ (Compose: build.context: ./frontend)
FROM node:24-bookworm-slim AS development
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1

FROM development AS builder
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# Next.js rewrites are fixed at build time. Use the Compose service address.
ARG API_BASE_URL=http://backend:8080
ENV API_BASE_URL=${API_BASE_URL}
RUN npm run test && npm run build

FROM node:24-bookworm-slim AS runner
WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    HOSTNAME=0.0.0.0 \
    PORT=3000

COPY --from=builder --chown=node:node /app/.next/standalone ./
COPY --from=builder --chown=node:node /app/.next/static ./.next/static
COPY --from=builder --chown=node:node /app/public ./public

USER node
EXPOSE 3000
CMD ["node", "server.js"]
