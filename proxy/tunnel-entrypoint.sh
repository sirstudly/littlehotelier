#!/bin/sh
set -eu

if [ -z "${TUNNEL_HOST:-}" ] || [ -z "${TUNNEL_USER:-}" ]; then
  echo "TUNNEL_HOST and TUNNEL_USER are required" >&2
  exit 1
fi

if [ ! -f /id_ed25519 ]; then
  echo "SSH key not mounted at /id_ed25519 (set TUNNEL_SSH_KEY)" >&2
  exit 1
fi

cp /id_ed25519 /tmp/id_ed25519
chmod 600 /tmp/id_ed25519

exec autossh -M 0 -N \
  -o StrictHostKeyChecking=accept-new \
  -o ServerAliveInterval=30 \
  -o ServerAliveCountMax=3 \
  -o ExitOnForwardFailure=yes \
  -i /tmp/id_ed25519 \
  -p "${TUNNEL_SSH_PORT:-22}" \
  -R 0.0.0.0:3128:bdc-forward-proxy:3128 \
  "${TUNNEL_USER}@${TUNNEL_HOST}"
