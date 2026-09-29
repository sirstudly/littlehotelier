#!/usr/bin/env bash
# Rebuilds and restarts compose services with the git commit baked into the images
# (label org.opencontainers.image.revision + env GIT_COMMIT).
#
# Usage: scripts/compose-build.sh [service...]   e.g. scripts/compose-build.sh hsh-processor
set -euo pipefail

cd "$(dirname "$0")/.."

if commit=$(git rev-parse --short HEAD 2>/dev/null); then
    if [ -n "$(git status --porcelain --untracked-files=no -- src pom.xml Dockerfile docker-entrypoint.sh ronbot-bridge ronbot-mcp 2>/dev/null)" ]; then
        commit="${commit}-dirty"
    fi
else
    commit=unknown
fi

export GIT_COMMIT="$commit"
echo "Building with GIT_COMMIT=$GIT_COMMIT"
docker compose up -d --build "$@"

# Each rebuild leaves the previous images dangling and grows the build cache; without
# this the host disk fills up. Cache used in the last week is kept so rebuilds stay fast.
echo "Pruning dangling images and build cache older than 7 days"
docker image prune -f || true
docker builder prune -af --filter until=168h || true
