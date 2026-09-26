#!/usr/bin/env bash
# Rebuilds and restarts compose services with the git commit baked into the images
# (label org.opencontainers.image.revision + env GIT_COMMIT).
#
# Usage: scripts/compose-build.sh [service...]   e.g. scripts/compose-build.sh hsh-processor
set -euo pipefail

cd "$(dirname "$0")/.."

if commit=$(git rev-parse --short HEAD 2>/dev/null); then
    if [ -n "$(git status --porcelain -- src pom.xml Dockerfile docker-entrypoint.sh ronbot-bridge ronbot-mcp 2>/dev/null)" ]; then
        commit="${commit}-dirty"
    fi
else
    commit=unknown
fi

export GIT_COMMIT="$commit"
echo "Building with GIT_COMMIT=$GIT_COMMIT"
docker compose up -d --build "$@"
