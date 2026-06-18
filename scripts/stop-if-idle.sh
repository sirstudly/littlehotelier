#!/usr/bin/env bash
set -euo pipefail

COMPOSE_DIR="/home/botpress/littlehotelier"
DOCKER="/usr/bin/docker"
CONTAINERS=(crh-processor hsh-processor rmb-processor lsh-processor)

cd "$COMPOSE_DIR"

BUSY=0

for container in "${CONTAINERS[@]}"; do
  if ! "$DOCKER" ps --format '{{.Names}}' | grep -qx "$container"; then
    continue
  fi

  set +e
  count=$("$DOCKER" exec "$container" sh -c \
    'java -server $JAVA_OPTS -Dchrome.binary.path=$CHROME_BINARY_PATH -Dspring.main.banner-mode=off -Dlogging.level.root=WARN -jar /app/lilhotelier.jar --check-running -n 2>/dev/null')
  exit_code=$?
  set -e

  if [ "$exit_code" -eq 1 ]; then
    echo "Refusing stop: $container has ${count} job(s) in processing" >&2
    BUSY=1
  elif [ "$exit_code" -ne 0 ]; then
    echo "Error checking running jobs in $container (exit $exit_code)" >&2
    exit "$exit_code"
  fi
done

if [ "$BUSY" -ne 0 ]; then
  exit 1
fi

exec "$DOCKER" compose down
