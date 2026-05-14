#!/usr/bin/env bash
# Fortress local tunnel.
#
# Starts an ngrok HTTP tunnel against the local Node backend port and writes the
# resulting public URL into local.properties as `fortress.baseUrl`.
#
# local.properties is gitignored, so this override is per-developer. The Android
# build (app/build.gradle.kts) prefers local.properties#fortress.baseUrl over the
# committed gradle.properties default (Vercel). Stop the tunnel and remove the
# override with: ./scripts/stop-local-tunnel.sh
#
# Requirements: ngrok in $PATH (https://ngrok.com/download) and an active config
# (`ngrok config add-authtoken ...`). The backend should already be running:
# `cd backend && npm run dev`.

set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." &> /dev/null && pwd)"
cd "$repo_root"

read_prop() {
  # read_prop <file> <key> — prints value or empty string
  local file="$1" key="$2"
  [[ -f "$file" ]] || { echo ""; return; }
  awk -F= -v k="$key" '
    $0 ~ "^[[:space:]]*#" { next }
    {
      sub(/^[[:space:]]+/, "", $1); sub(/[[:space:]]+$/, "", $1)
      if ($1 == k) {
        sub(/^[^=]*=/, "")
        sub(/^[[:space:]]+/, ""); sub(/[[:space:]]+$/, "")
        print
        exit
      }
    }
  ' "$file"
}

upsert_prop() {
  # upsert_prop <file> <key> <value>
  local file="$1" key="$2" value="$3"
  touch "$file"
  if grep -qE "^[[:space:]]*${key}=" "$file"; then
    # portable in-place edit (BSD + GNU sed)
    local tmp
    tmp="$(mktemp)"
    awk -v k="$key" -v v="$value" -F= '
      {
        line=$0
        kk=$1; sub(/^[[:space:]]+/, "", kk); sub(/[[:space:]]+$/, "", kk)
        if (kk == k) print k "=" v
        else print line
      }
    ' "$file" > "$tmp"
    mv "$tmp" "$file"
  else
    # ensure trailing newline
    [[ -s "$file" && -z "$(tail -c1 "$file")" ]] || echo "" >> "$file"
    echo "${key}=${value}" >> "$file"
  fi
}

# --- locate ngrok -------------------------------------------------------------
if ! command -v ngrok > /dev/null 2>&1; then
  echo "✗ ngrok not found in PATH. Install it from https://ngrok.com/download" >&2
  exit 127
fi

# --- determine port -----------------------------------------------------------
port="$(read_prop gradle.properties fortress.localBackendPort)"
port="${port:-8787}"
echo "→ Fortress tunnel: ngrok http $port"

# --- check backend is up (best-effort) ----------------------------------------
if ! curl -sf -m 2 "http://127.0.0.1:${port}/healthz" > /dev/null \
  && ! curl -sf -m 2 "http://127.0.0.1:${port}/" > /dev/null; then
  echo "  (heads up) nothing responded on 127.0.0.1:${port} — start the backend with \`cd backend && npm run dev\` if you haven't yet."
fi

# --- kill stale tunnel --------------------------------------------------------
pkill -f "ngrok http ${port}" 2> /dev/null || true
sleep 0.5

# --- start ngrok detached -----------------------------------------------------
log_dir="${repo_root}/.gradle"
mkdir -p "$log_dir"
log_file="${log_dir}/ngrok.log"
nohup ngrok http "$port" --log=stdout --log-format=logfmt > "$log_file" 2>&1 &
echo $! > "${log_dir}/ngrok.pid"

# --- poll local ngrok api for the public URL ----------------------------------
public_url=""
for _ in $(seq 1 40); do
  sleep 0.25
  resp="$(curl -sf -m 2 http://127.0.0.1:4040/api/tunnels || true)"
  [[ -z "$resp" ]] && continue
  # extract the first public_url whose proto is https
  public_url="$(printf '%s' "$resp" | tr ',{}' '\n\n\n' \
    | awk -F'"' '/"public_url"[[:space:]]*:[[:space:]]*"https:/ { print $4; exit }')"
  [[ -n "$public_url" ]] && break
done

if [[ -z "$public_url" ]]; then
  echo "✗ Could not read tunnel URL from ngrok API after 10s. Tail of $log_file:" >&2
  tail -n 20 "$log_file" >&2 || true
  exit 1
fi

# normalise trailing slash
[[ "$public_url" == */ ]] || public_url="${public_url}/"

upsert_prop local.properties fortress.baseUrl "$public_url"

echo "✓ fortress.baseUrl=$public_url"
echo "  local.properties updated (gitignored). The next Android build will use this URL."
echo "  Stop with: ./scripts/stop-local-tunnel.sh (or ./gradlew fortressTunnelStop)"
