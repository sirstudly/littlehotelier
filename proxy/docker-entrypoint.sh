#!/bin/sh
set -eu

if [ -z "${PROXY_USER:-}" ] || [ -z "${PROXY_PASS:-}" ]; then
  echo "PROXY_USER and PROXY_PASS are required" >&2
  exit 1
fi

envsubst '${PROXY_USER} ${PROXY_PASS}' \
  < /etc/3proxy/3proxy.cfg.template \
  > /etc/3proxy/3proxy.cfg
chmod 600 /etc/3proxy/3proxy.cfg

exec 3proxy /etc/3proxy/3proxy.cfg
