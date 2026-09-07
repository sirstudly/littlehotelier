# syntax=docker/dockerfile:1
# Build context: repo root (see docker-compose.yml).
# Cursor SDK needs glibc (not Alpine). MCP needs Linux native deps (not a Mac node_modules mount).

FROM node:22-bookworm-slim AS bridge-build
WORKDIR /app/bridge
COPY ronbot-bridge/package.json ronbot-bridge/package-lock.json* ./
RUN npm install
COPY ronbot-bridge/tsconfig.json ./
COPY ronbot-bridge/src ./src
RUN npm run build && npm prune --omit=dev

FROM node:22-bookworm-slim AS mcp-deps
WORKDIR /app/ronbot-mcp
COPY ronbot-mcp/package.json ronbot-mcp/package-lock.json* ./
# Production deps only (mysql2 native bindings for linux)
RUN npm install --omit=dev

FROM node:22-bookworm-slim
WORKDIR /app
ENV NODE_ENV=production
RUN apt-get update \
  && apt-get install -y --no-install-recommends gosu ca-certificates \
  && rm -rf /var/lib/apt/lists/* \
  && mkdir -p /app/bridge /app/ronbot-mcp /home/node/.cursor \
  && chown -R node:node /home/node

COPY --from=bridge-build /app/bridge/package.json /app/bridge/
COPY --from=bridge-build /app/bridge/node_modules /app/bridge/node_modules
COPY --from=bridge-build /app/bridge/dist /app/bridge/dist
COPY ronbot-bridge/config /app/bridge/config
COPY ronbot-bridge/docker-entrypoint.sh /docker-entrypoint.sh

COPY --from=mcp-deps /app/ronbot-mcp/node_modules /app/ronbot-mcp/node_modules
COPY --from=mcp-deps /app/ronbot-mcp/package.json /app/ronbot-mcp/
# dist/config mounted at runtime from host for faster iteration

RUN chmod +x /docker-entrypoint.sh \
  && sed -i 's/\r$//' /docker-entrypoint.sh \
  && chown -R node:node /app/bridge /app/ronbot-mcp

WORKDIR /app/bridge
ENV HOME=/home/node
EXPOSE 8787
ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["node", "dist/index.js"]
