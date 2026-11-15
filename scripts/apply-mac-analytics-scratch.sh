#!/usr/bin/env bash
# Create mac_analytics_scratch + analytics_adhoc_rw for Tier 2 WhatsApp materialize.
# Also GRANT SELECT on the scratch schema to existing metabase_ro.
# Requires a MariaDB admin user (or run the SQL via: sudo plesk db -e '…').
#
# Usage (from repo root):
#   export MYSQL_ADMIN_USER=root
#   export MYSQL_PWD='...'          # admin password; never commit
#   export MYSQL_HOST=...           # MariaDB host (Tailscale IP or reachable name)
#   ./scripts/apply-mac-analytics-scratch.sh
#
# Optional: ANALYTICS_ADHOC_RW_PASSWORD (otherwise a random password is generated and printed once).
# Store the password in gitignored metabase/.credentials-notes and host .env as ANALYTICS_ADHOC_RW_*.

set -euo pipefail

MYSQL_HOST="${MYSQL_HOST:-}"
MYSQL_ADMIN_USER="${MYSQL_ADMIN_USER:-root}"
MYSQL_PORT="${MYSQL_PORT:-3306}"

if [[ -z "${MYSQL_HOST}" || -z "${MYSQL_PWD:-}" ]]; then
  echo "Set MYSQL_HOST and MYSQL_PWD (and MYSQL_ADMIN_USER if not root)." >&2
  echo "On the MariaDB/Plesk host you can instead run the SQL block with: sudo plesk db" >&2
  exit 1
fi

rand_pw() {
  openssl rand -base64 24 | tr -d '/+=' | head -c 24
}

ANALYTICS_ADHOC_RW_PASSWORD="${ANALYTICS_ADHOC_RW_PASSWORD:-$(rand_pw)}"

mysql_admin() {
  docker run --rm -i -e MYSQL_PWD mysql:8.0 \
    mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_ADMIN_USER" --connect-timeout=15 "$@"
}

echo "Creating mac_analytics_scratch + analytics_adhoc_rw..."
mysql_admin <<SQL
CREATE DATABASE IF NOT EXISTS mac_analytics_scratch
  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

CREATE USER IF NOT EXISTS 'analytics_adhoc_rw'@'%' IDENTIFIED BY '${ANALYTICS_ADHOC_RW_PASSWORD}';
ALTER USER 'analytics_adhoc_rw'@'%' IDENTIFIED BY '${ANALYTICS_ADHOC_RW_PASSWORD}';

GRANT SELECT, SHOW VIEW ON wp_crh_backoffice.* TO 'analytics_adhoc_rw'@'%';
GRANT SELECT, SHOW VIEW ON wp_hsh_backoffice.* TO 'analytics_adhoc_rw'@'%';
GRANT SELECT, SHOW VIEW ON wp_rmb_backoffice.* TO 'analytics_adhoc_rw'@'%';
GRANT SELECT, SHOW VIEW ON wp_lsh_backoffice.* TO 'analytics_adhoc_rw'@'%';

GRANT CREATE, DROP, INSERT, SELECT, INDEX, ALTER ON mac_analytics_scratch.* TO 'analytics_adhoc_rw'@'%';

GRANT SELECT ON mac_analytics_scratch.* TO 'metabase_ro'@'%';
FLUSH PRIVILEGES;
SQL

echo
echo "Done. Store this secret (shown once):"
echo "  ANALYTICS_ADHOC_RW_USER=analytics_adhoc_rw"
echo "  ANALYTICS_ADHOC_RW_PASSWORD=${ANALYTICS_ADHOC_RW_PASSWORD}"
echo "  ANALYTICS_ADHOC_RW_HOST=mysql-tailscale   # on ronbot-bridge Compose"
echo
echo "Plesk host equivalent (no MYSQL_PWD): sudo plesk db  then paste the SQL above."
echo "See docs/hostel-analytics-metabase.md (Tier 2 scratch)."
