#!/usr/bin/env bash
# Runs the backend as one instance per organisation (ADR 0003), plus the token
# sink that keeps their ledger API tokens fresh (ADR 0004).
#   scripts/backends.sh up [org...]   build, start in the background, wait until healthy
#   scripts/backends.sh down          stop everything this script started
#   scripts/backends.sh status        each instance's organisation, parties and offset
set -euo pipefail
export PATH="/usr/bin:/usr/local/bin:$PATH"
cd "$(dirname "$0")/.."
RUN=out/run
mkdir -p "$RUN"
declare -A PORT=([agent]=8081 [lendera]=8082 [lenderb]=8083)

start() {  # name, command...
  local name=$1; shift
  if [ -f "$RUN/$name.pid" ] && kill -0 "$(cat "$RUN/$name.pid")" 2>/dev/null; then
    echo "$name is already running"; return
  fi
  nohup "$@" > "$RUN/$name.log" 2>&1 &
  echo $! > "$RUN/$name.pid"
}

case "${1:-}" in
  up)
    shift
    ORGS=${*:-agent lendera lenderb}
    (cd backend && ./gradlew bootJar -q)
    JAR=$(cd backend && ls build/libs/*.jar | grep -v plain | head -1)
    start token-refresher ./scripts/token-refresher.sh $(for o in $ORGS; do printf '%s-backend ' "$o"; done)
    sleep 2
    PROJECTOR_DB_PASSWORD=$(grep '^PROJECTOR_DB_PASSWORD=' infra/.env | cut -d= -f2-)
    export PROJECTOR_DB_PASSWORD
    UI_DEV_PASSWORD=$(grep '^UI_DEV_PASSWORD=' infra/.env | cut -d= -f2-)
    export UI_DEV_PASSWORD
    for o in $ORGS; do
      start "backend-$o" bash -c "cd backend && exec java -jar $JAR --spring.profiles.active=$o"
    done
    for o in $ORGS; do
      for _ in $(seq 1 60); do
        curl -sf "localhost:${PORT[$o]}/actuator/health" > /dev/null && break
        sleep 1
      done
      if curl -sf "localhost:${PORT[$o]}/actuator/health" > /dev/null; then
        echo "$o ready on :${PORT[$o]}"
      else
        echo "$o did not become healthy, see $RUN/backend-$o.log"; exit 1
      fi
    done ;;
  down)
    for f in "$RUN"/*.pid; do
      [ -e "$f" ] || continue
      kill "$(cat "$f")" 2>/dev/null || true
      rm -f "$f"
    done
    echo "backends stopped" ;;
  status)
    for o in agent lendera lenderb; do
      printf '%-8s ' "$o"
      curl -sf -u "$o.ops:$(grep '^UI_DEV_PASSWORD=' infra/.env | cut -d= -f2-)" "localhost:${PORT[$o]}/api/status" || printf 'not running'
      echo
    done ;;
  *)
    echo "usage: $0 up [org...] | down | status"; exit 2 ;;
esac
