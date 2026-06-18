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
  output=$("$DOCKER" exec "$container" sh -c \
    'java -server $JAVA_OPTS -Dchrome.binary.path=$CHROME_BINARY_PATH -Dspring.main.web-application-type=none -Dspring.main.banner-mode=off -Dlogging.level.root=ERROR -jar /app/lilhotelier.jar --check-running -n 2>&1')
  exit_code=$?
  set -e

  count=$(echo "$output" | grep '^PROCESSING_JOB_COUNT=' | tail -1 | cut -d= -f2)
  if [ -z "$count" ]; then
    count=$(echo "$output" | tail -1 | tr -d '[:space:]')
  fi

  if [ -z "$count" ] || ! [[ "$count" =~ ^[0-9]+$ ]]; then
    echo "Error checking running jobs in $container (exit $exit_code)" >&2
    echo "$output" >&2
    exit 2
  fi

  if [ "$count" -gt 0 ]; then
    echo "Refusing stop: $container has ${count} job(s) in processing" >&2
    BUSY=1
  elif [ "$exit_code" -ne 0 ]; then
    echo "Error checking running jobs in $container (exit $exit_code)" >&2
    echo "$output" >&2
    exit "$exit_code"
  fi
done

if [ "$BUSY" -ne 0 ]; then
  exit 1
fi

exec "$DOCKER" compose down
