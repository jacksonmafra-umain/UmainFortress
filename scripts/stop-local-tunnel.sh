#!/usr/bin/env bash
# Stops the ngrok tunnel started by start-local-tunnel.sh and removes the
# fortress.baseUrl override from local.properties so the next build falls back
# to the Vercel default in gradle.properties.

set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." &> /dev/null && pwd)"
cd "$repo_root"

pid_file=".gradle/ngrok.pid"
if [[ -f "$pid_file" ]]; then
  pid="$(cat "$pid_file" || true)"
  if [[ -n "${pid:-}" ]] && kill -0 "$pid" 2> /dev/null; then
    kill "$pid" || true
  fi
  rm -f "$pid_file"
fi
# belt and suspenders — kill any straggler
pkill -f "ngrok http " 2> /dev/null || true

if [[ -f local.properties ]]; then
  tmp="$(mktemp)"
  awk -F= '
    {
      kk=$1; sub(/^[[:space:]]+/, "", kk); sub(/[[:space:]]+$/, "", kk)
      if (kk == "fortress.baseUrl") next
      print
    }
  ' local.properties > "$tmp"
  mv "$tmp" local.properties
fi

echo "✓ Tunnel stopped, fortress.baseUrl override cleared from local.properties."
echo "  Next build will use the gradle.properties default (Vercel)."
