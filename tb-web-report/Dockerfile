FROM node:22-bookworm-slim

ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
        chromium \
        fonts-liberation \
        fonts-noto-color-emoji \
        fonts-dejavu \
        ca-certificates \
        dumb-init \
    && rm -rf /var/lib/apt/lists/*

ENV PUPPETEER_SKIP_CHROMIUM_DOWNLOAD=true \
    PUPPETEER_EXECUTABLE_PATH=/usr/bin/chromium

WORKDIR /app
COPY package.json ./
RUN npm install --omit=dev --no-audit --no-fund && npm cache clean --force

COPY server.js ./

ENV HTTP_BIND_ADDRESS=0.0.0.0 \
    HTTP_BIND_PORT=8383 \
    DEFAULT_PAGE_NAVIGATION_TIMEOUT=120000 \
    DASHBOARD_IDLE_WAIT_TIME=3000 \
    USE_NEW_PAGE_FOR_REPORT=true \
    LOGGER_LEVEL=info

EXPOSE 8383

ENTRYPOINT ["/usr/bin/dumb-init", "--"]
CMD ["node", "server.js"]
