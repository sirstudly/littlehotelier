#!/usr/bin/env bash
# Create metabase_ops_ro: SELECT-only on wp_*_backoffice for Metabase adhoc native SQL.
# Keep metabase_ro warehouse-only (mac_analytics). Requires a MariaDB admin user.
#
# Usage (from repo root):
#   export MYSQL_ADMIN_USER=root
#   export MYSQL_PWD='...'          # admin password; never commit
#   export MYSQL_HOST=...           # MariaDB host (Tailscale IP or reachable name)
#   ./scripts/apply-metabase-ops-ro.sh
#
# Optional: METABASE_OPS_RO_PASSWORD (otherwise a random password is generated and printed once).
# Store the password in gitignored metabase/.credentials-notes (not in committed files).

set -euo pipefail

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

METABASE_OPS_RO_PASSWORD="${METABASE_OPS_RO_PASSWORD:-$(rand_pw)}"

mysql_admin() {
  docker run --rm -i -e MYSQL_PWD mysql:8.0 \
    mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_ADMIN_USER" --connect-timeout=15 "$@"
}

echo "Creating / updating metabase_ops_ro (SELECT on wp_*_backoffice)..."
mysql_admin <<SQL
CREATE USER IF NOT EXISTS 'metabase_ops_ro'@'%' IDENTIFIED BY '${METABASE_OPS_RO_PASSWORD}';
ALTER USER 'metabase_ops_ro'@'%' IDENTIFIED BY '${METABASE_OPS_RO_PASSWORD}';
GRANT SELECT, SHOW VIEW ON wp_crh_backoffice.* TO 'metabase_ops_ro'@'%';
GRANT SELECT, SHOW VIEW ON wp_hsh_backoffice.* TO 'metabase_ops_ro'@'%';
GRANT SELECT, SHOW VIEW ON wp_rmb_backoffice.* TO 'metabase_ops_ro'@'%';
GRANT SELECT, SHOW VIEW ON wp_lsh_backoffice.* TO 'metabase_ops_ro'@'%';
FLUSH PRIVILEGES;
SQL

echo
echo "Done. Store this secret (shown once):"
echo "  Metabase Admin DB connection name: backoffice"
echo "  User: metabase_ops_ro"
echo "  Password: ${METABASE_OPS_RO_PASSWORD}"
echo
echo "Then in Metabase Admin → Databases → Add → MySQL:"
echo "  Display name: backoffice"
echo "  Host: same Tailscale IP as MB_DB_HOST / warehouse"
echo "  Database: wp_crh_backoffice   (default; native SQL may use wp_*_backoffice.table)"
echo "  Username: metabase_ops_ro"
echo "  Password: (above)"
echo "Update Metabase Admin → Databases → backoffice to use metabase_ops_ro (if it was on RONBOT_SQL_RO interim)."
echo "Ensure the WhatsApp API key group can query this database."
echo "See docs/hostel-analytics-metabase.md (Adhoc / backoffice charts)."
