#!/usr/bin/env bash
# Apply mac_analytics + metabase DBs, users, and per-hostel job_scheduler seeds.
# Requires a MariaDB admin (or sufficiently privileged) user — not RONBOT_SQL_RO.
#
# Usage (from repo root):
#   export MYSQL_ADMIN_USER=root
#   export MYSQL_PWD='...'          # admin password; never commit
#   export MYSQL_HOST=...           # MariaDB host (Tailscale IP or reachable name)
#   ./scripts/apply-mac-analytics.sh
#
# Optional password overrides (otherwise random passwords are generated and printed once):
#   ANALYTICS_RW_PASSWORD  METABASE_RO_PASSWORD  METABASE_APP_PASSWORD

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MYSQL_HOST="${MYSQL_HOST:-}"
MYSQL_ADMIN_USER="${MYSQL_ADMIN_USER:-root}"
MYSQL_PORT="${MYSQL_PORT:-3306}"

if [[ -z "${MYSQL_HOST}" || -z "${MYSQL_PWD:-}" ]]; then
  echo "Set MYSQL_HOST and MYSQL_PWD (and MYSQL_ADMIN_USER if not root)." >&2
  exit 1
fi

rand_pw() {
  openssl rand -base64 24 | tr -d '/+=' | head -c 24
}

ANALYTICS_RW_PASSWORD="${ANALYTICS_RW_PASSWORD:-$(rand_pw)}"
METABASE_RO_PASSWORD="${METABASE_RO_PASSWORD:-$(rand_pw)}"
METABASE_APP_PASSWORD="${METABASE_APP_PASSWORD:-$(rand_pw)}"

mysql_admin() {
  docker run --rm -i -e MYSQL_PWD mysql:8.0 \
    mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_ADMIN_USER" --connect-timeout=15 "$@"
}

echo "Applying warehouse + metabase DDL..."
mysql_admin < "$ROOT/src/main/config/migrations/2026-11-12-mac-analytics.sql"

echo "Applying Phase 2 warehouse DDL (GHS + HW rank)..."
mysql_admin < "$ROOT/src/main/config/migrations/2026-11-15-mac-analytics-phase2.sql"

echo "Applying GHS competitor bed price SCD2 columns..."
mysql_admin < "$ROOT/src/main/config/migrations/2026-11-16-mac-analytics-competitor-bed-price-scd2.sql"

echo "Creating / updating users..."
mysql_admin <<SQL
CREATE USER IF NOT EXISTS 'analytics_rw'@'%' IDENTIFIED BY '${ANALYTICS_RW_PASSWORD}';
ALTER USER 'analytics_rw'@'%' IDENTIFIED BY '${ANALYTICS_RW_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE ON mac_analytics.* TO 'analytics_rw'@'%';

CREATE USER IF NOT EXISTS 'metabase_ro'@'%' IDENTIFIED BY '${METABASE_RO_PASSWORD}';
ALTER USER 'metabase_ro'@'%' IDENTIFIED BY '${METABASE_RO_PASSWORD}';
GRANT SELECT ON mac_analytics.* TO 'metabase_ro'@'%';

CREATE USER IF NOT EXISTS 'metabase_app'@'%' IDENTIFIED BY '${METABASE_APP_PASSWORD}';
ALTER USER 'metabase_app'@'%' IDENTIFIED BY '${METABASE_APP_PASSWORD}';
GRANT ALL PRIVILEGES ON metabase.* TO 'metabase_app'@'%';
FLUSH PRIVILEGES;
SQL

for db in wp_crh_backoffice wp_hsh_backoffice wp_rmb_backoffice wp_lsh_backoffice; do
  echo "Seeding Phase 1 job_scheduler on $db..."
  mysql_admin "$db" < "$ROOT/src/main/config/migrations/2026-11-12-analytics-job-scheduler.sql"
done

echo "Seeding Phase 2 job_scheduler on wp_crh_backoffice only..."
mysql_admin wp_crh_backoffice < "$ROOT/src/main/config/migrations/2026-11-15-analytics-phase2-job-scheduler-crh.sql"

echo
echo "Done. Store these secrets (shown once):"
echo "  GCP Secret Manager analyticsdb_username = analytics_rw"
echo "  GCP Secret Manager analyticsdb_password = ${ANALYTICS_RW_PASSWORD}"
echo "  metabase/.env MB_DB_USER=metabase_app"
echo "  metabase/.env MB_DB_PASS=${METABASE_APP_PASSWORD}"
echo "  Metabase Admin warehouse user metabase_ro / ${METABASE_RO_PASSWORD}"
echo
echo "Then rebuild processors so they pick up analyticsdb.*, deploy metabase/, and enqueue backfills."
echo "Phase 2 also needs host dir chromeprofile/ghs (CRH compose mount) before SyncCompetitorBedPriceJob."
