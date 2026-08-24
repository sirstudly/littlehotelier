#!/bin/sh
set -e

# Bind-mounted host dirs are often created as root; ensure appuser can write.
for dir in /app/config /app/logs /app/chromeprofile; do
  if [ -d "$dir" ]; then
    chown -R appuser:appuser "$dir"
  fi
done

# SSH mounts are applied after image build; tighten permissions at runtime.
if [ -d /home/appuser/.ssh ]; then
  chmod 700 /home/appuser/.ssh
  find /home/appuser/.ssh -type f -exec chmod 600 {} +
fi

# Drop privileges and replace this process so the app becomes PID 1 and receives SIGTERM.
# setpriv keeps the parent env; without this Chrome inherits HOME=/root and exits
# (mkdir /root: Permission denied; chrome_crashpad_handler: --database is required).
export HOME=/home/appuser
export USER=appuser
export XDG_CONFIG_HOME=/home/appuser/.config
export XDG_CACHE_HOME=/home/appuser/.cache
export XDG_DATA_HOME=/home/appuser/.local/share
mkdir -p "$XDG_CONFIG_HOME" "$XDG_CACHE_HOME" "$XDG_DATA_HOME/applications" /tmp/chrome-crash
chown -R appuser:appuser /home/appuser /tmp/chrome-crash

exec setpriv --reuid=appuser --regid=appuser --init-groups -- "$@"
