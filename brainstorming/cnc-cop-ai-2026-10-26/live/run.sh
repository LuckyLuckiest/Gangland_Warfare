#!/usr/bin/env bash
# run.sh <key> [scenario] [tag] - fresh clone + prep + boot + scenario + stop for one cop-ai server.
#   key       spawn-dup | radio-far | brandish | handoff-lead   (server cop-ai-<key>, fixed port below)
#   scenario  scen/<scenario>.json, default <key>; boot / dry are the env checks
#   tag       run folder suffix, default <scenario>; output in _programme/runs/cop-ai-<key>--<tag>
# Waits for a heavy-job slot (harness slots.js); safe to run the three keys in parallel (distinct dirs and ports).
set -euo pipefail
LIVE="$(cd "$(dirname "$0")" && pwd -W)"
PROG="E:/Programming/java/wt/_programme"
KEY=${1:?key}; SC=${2:-$KEY}; TAG=${3:-$SC}
case $KEY in
  spawn-dup) PORT=25611;; radio-far) PORT=25612;; brandish) PORT=25613;; handoff-lead) PORT=25615;;
  *) echo "unknown key $KEY"; exit 2;;
esac
[ -f "$LIVE/scen/$SC.json" ] || { echo "no scenario $LIVE/scen/$SC.json (run: node gen-scen.js)"; exit 2; }
[ -f "$LIVE/run-copai.js" ] || node "$LIVE/mk-runner.js"
if netstat -ano | grep -qE ":$PORT .*LISTEN"; then echo "port $PORT busy, abort"; exit 1; fi
NAME="cop-ai-$KEY"; OUT="$PROG/runs/$NAME--$TAG"
# never overwrite a run (SD-R1 was lost that way): a reused tag is refused
[ -e "$OUT" ] && { echo "run dir exists, pick a new tag: $OUT"; exit 4; }
bash "$LIVE/clone.sh" "$NAME" "$PORT"
bash "$LIVE/prep.sh" "$NAME" "$PORT"
set +e
node "$LIVE/run-copai.js" --server "$PROG/servers/$NAME" --jar paper-1.21.11.jar \
  --scenario "$LIVE/scen/$SC.json" --out "$OUT" --version 26.1 < /dev/null
RC=$?
echo "RUN $NAME scenario=$SC rc=$RC out=$OUT"
exit $RC
