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
exec setpriv --reuid=appuser --regid=appuser --init-groups -- "$@"
