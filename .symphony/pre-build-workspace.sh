#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$ROOT_DIR"

MODE="${1:-all}"
LOCAL_M2_DIR="$ROOT_DIR/.symphony/m2/repository"
HOST_M2_DIR="${HOME:-}/.m2/repository"

is_empty_dir() {
  local path="$1"
  [ -z "$(find "$path" -mindepth 1 -print -quit 2>/dev/null)" ]
}

seed_local_m2() {
  mkdir -p "$LOCAL_M2_DIR"
  if [ ! -d "$HOST_M2_DIR" ] || ! is_empty_dir "$LOCAL_M2_DIR"; then
    return
  fi
  printf '[INFO] seeding workspace Maven repo from %s\n' "$HOST_M2_DIR"
  if command -v rsync >/dev/null 2>&1; then
    rsync -a --ignore-existing "$HOST_M2_DIR"/ "$LOCAL_M2_DIR"/
    return
  fi
  cp -a -n "$HOST_M2_DIR"/. "$LOCAL_M2_DIR"/
}

prewarm_backend() {
  seed_local_m2
  printf '[INFO] prewarming Maven dependencies into %s\n' "$LOCAL_M2_DIR"
  mvn -pl common -DskipTests -Dspotless.skip=true dependency:go-offline
}

prewarm_frontend() {
  if [ ! -f "$ROOT_DIR/webapp/package.json" ] || [ ! -f "$ROOT_DIR/webapp/pnpm-lock.yaml" ]; then
    return
  fi
  printf '[INFO] prewarming pnpm workspace dependencies\n'
  pnpm install --dir webapp --frozen-lockfile
}

case "$MODE" in
  all)
    prewarm_backend
    prewarm_frontend
    ;;
  backend)
    prewarm_backend
    ;;
  frontend)
    prewarm_frontend
    ;;
  *)
    echo "Usage: bash .symphony/pre-build-workspace.sh [all|backend|frontend]"
    exit 2
    ;;
esac
