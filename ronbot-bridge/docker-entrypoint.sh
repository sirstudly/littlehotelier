#!/bin/sh
set -e

# Hybrid DNS for Docker Desktop:
# - 127.0.0.11 first so compose service names (waha, ronbot-read-api) resolve
# - public resolvers as fallback for external hosts
# Do NOT use only public DNS (breaks service discovery) and avoid use-vc here
# (it can break Docker's embedded resolver).
if [ "${FIX_CONTAINER_DNS:-0}" = "1" ]; then
  printf 'nameserver 127.0.0.11\nnameserver 8.8.8.8\nnameserver 1.1.1.1\noptions ndots:0 timeout:2 attempts:3\n' > /etc/resolv.conf

  # Pin api.cursor.com via DNS-over-HTTPS to 1.1.1.1 by IP (no DNS needed).
  # Containers on Docker Desktop often get EAI_AGAIN for external names even when
  # HTTPS by IP works.
  if [ "${PIN_CURSOR_API_DNS:-1}" = "1" ]; then
    CURSOR_IP="$(
      node -e "
fetch('https://1.1.1.1/dns-query?name=api.cursor.com&type=A', {
  headers: { accept: 'application/dns-json' },
  signal: AbortSignal.timeout(8000),
})
  .then((r) => r.json())
  .then((j) => {
    const a = (j.Answer || []).find((x) => x.type === 1);
    if (a && a.data) process.stdout.write(String(a.data));
  })
  .catch(() => process.exit(0));
" 2>/dev/null || true
    )"
    if [ -n "$CURSOR_IP" ]; then
      # Replace prior pin if present
      if grep -q '[[:space:]]api\.cursor\.com$' /etc/hosts 2>/dev/null; then
        sed -i '/[[:space:]]api\.cursor\.com$/d' /etc/hosts
      fi
      echo "$CURSOR_IP api.cursor.com" >> /etc/hosts
      echo "pinned api.cursor.com -> $CURSOR_IP"
    else
      echo "WARNING: could not pin api.cursor.com via DoH; Cursor SDK may fail DNS"
    fi
  fi
fi

if [ "$(id -u)" = "0" ]; then
  exec gosu node "$@"
fi
exec "$@"
