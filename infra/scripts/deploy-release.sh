#!/usr/bin/env bash
set -Eeuo pipefail

fail() {
  echo "deploy: $*" >&2
  exit 1
}

require_absolute_path() {
  case "$1" in
    /*) ;;
    *) fail "$2 must be an absolute path" ;;
  esac
}

restart_service() {
  local service="$1"
  if [ -z "$service" ] || [ "$service" = "skip" ]; then
    return 0
  fi
  if ! command -v systemctl >/dev/null 2>&1; then
    echo "systemctl not found; skipping $service restart"
    return 0
  fi
  if ! "${SUDO_CMD[@]}" systemctl cat "$service" >/dev/null 2>&1; then
    echo "systemd service $service not found; skipping restart"
    return 0
  fi

  echo "restarting $service"
  "${SUDO_CMD[@]}" systemctl restart "$service"
  "${SUDO_CMD[@]}" systemctl is-active --quiet "$service"
}

check_url() {
  local url="$1"
  if [ -z "$url" ]; then
    return 0
  fi
  if ! command -v curl >/dev/null 2>&1; then
    echo "curl not found; skipping health check $url"
    return 0
  fi

  echo "checking $url"
  for _ in $(seq 1 30); do
    if curl -fsS "$url" >/dev/null; then
      return 0
    fi
    sleep 2
  done
  fail "health check failed: $url"
}

: "${DEPLOY_PATH:?DEPLOY_PATH is required}"
: "${RELEASE_ARCHIVE:?RELEASE_ARCHIVE is required}"

require_absolute_path "$DEPLOY_PATH" DEPLOY_PATH
require_absolute_path "$RELEASE_ARCHIVE" RELEASE_ARCHIVE
[ -f "$RELEASE_ARCHIVE" ] || fail "release archive not found: $RELEASE_ARCHIVE"

KEEP_RELEASES="${KEEP_RELEASES:-5}"
case "$KEEP_RELEASES" in
  ''|*[!0-9]*) fail "KEEP_RELEASES must be a non-negative integer" ;;
esac

RUN_SUDO="${RUN_SUDO:-sudo -n}"
SUDO_CMD=()
if [ -n "$RUN_SUDO" ] && [ "$RUN_SUDO" != "none" ]; then
  # shellcheck disable=SC2206
  SUDO_CMD=($RUN_SUDO)
fi

release_id="$(date -u +%Y%m%d%H%M%S)"
release_dir="$DEPLOY_PATH/releases/$release_id"
shared_dir="$DEPLOY_PATH/shared"

umask 027
mkdir -p "$DEPLOY_PATH/releases" "$shared_dir/env" "$shared_dir/logs" "$shared_dir/tmp"
mkdir "$release_dir"

tar -xzf "$RELEASE_ARCHIVE" -C "$release_dir"
[ -f "$release_dir/backend/app.jar" ] || fail "backend/app.jar missing from release"
[ -f "$release_dir/agent-service/app.jar" ] || fail "agent-service/app.jar missing from release"
[ -f "$release_dir/sandbox-runner/app.py" ] || fail "sandbox-runner/app.py missing from release"
[ -d "$release_dir/frontend" ] || fail "frontend directory missing from release"

chmod o+x "$DEPLOY_PATH" "$DEPLOY_PATH/releases" "$release_dir" 2>/dev/null || true
chmod -R o+rX "$release_dir/frontend"

ln -sfn "$shared_dir/env/backend.env" "$release_dir/backend/.env"
ln -sfn "$shared_dir/env/agent-service.env" "$release_dir/agent-service/.env"
ln -sfn "$shared_dir/env/sandbox-runner.env" "$release_dir/sandbox-runner/.env"

if [ "${INSTALL_SANDBOX_DEPS:-auto}" != "false" ] && command -v python3 >/dev/null 2>&1; then
  if python3 -m venv "$shared_dir/sandbox-venv" >/dev/null 2>&1; then
    "$shared_dir/sandbox-venv/bin/python" -m pip install --upgrade pip >/dev/null
    "$shared_dir/sandbox-venv/bin/pip" install -r "$release_dir/sandbox-runner/requirements.txt"
  else
    echo "python3 venv is not available; skipping sandbox dependency install"
  fi
fi

ln -sfn "$release_dir" "$DEPLOY_PATH/current"

restart_service "${BACKEND_SERVICE:-superagent-backend}"
restart_service "${AGENT_SERVICE:-superagent-agent-service}"
restart_service "${SANDBOX_SERVICE:-superagent-sandbox-runner}"

if [ "${HEALTHCHECK_URLS:-}" != "skip" ]; then
  for url in ${HEALTHCHECK_URLS:-}; do
    check_url "$url"
  done
fi

rm -f "$RELEASE_ARCHIVE"

if [ "$KEEP_RELEASES" -gt 0 ]; then
  find "$DEPLOY_PATH/releases" -mindepth 1 -maxdepth 1 -type d |
    sort -r |
    tail -n +"$((KEEP_RELEASES + 1))" |
    while IFS= read -r old_release; do
      [ -n "$old_release" ] || continue
      rm -rf "$old_release"
    done
fi

echo "deployed $release_dir"
