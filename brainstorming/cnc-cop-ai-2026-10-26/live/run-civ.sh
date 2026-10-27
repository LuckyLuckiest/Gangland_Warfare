#!/usr/bin/env bash
# run-civ.sh <scenario> <tag> - 0.21.1 civilian-911 acceptance on server cop-ai-civ (port 25614):
#   Gangland E:/Programming/java/wt/gangland-0.21.1/target (core + 10 modules, branch 0.21.1, which merged 0.21.1-civ
#   at f52cda0cd; env GL overrides), Keystone 1.15.4,
#   Bartizan 0.6.0, Citizens/base as the other cop-ai rows. Fresh clone + prep-civ.sh on every run.
#   scenario  scen/<scenario>.json (civ-boot, civ-911; from gen-civ.js)
#   tag       REQUIRED, unique: output _programme/runs/cop-ai-civ--<tag>; an existing dir is refused (exit 4)
set -euo pipefail
LIVE="$(cd "$(dirname "$0")" && pwd -W)"
PROG="E:/Programming/java/wt/_programme"
SC=${1:?scenario}; TAG=${2:?tag (unique per run)}
NAME=cop-ai-civ; PORT=25614; OUT="$PROG/runs/$NAME--$TAG"
[ -e "$OUT" ] && { echo "run dir exists, pick a new tag: $OUT"; exit 4; }
[ -f "$LIVE/scen/$SC.json" ] || { echo "no scenario $LIVE/scen/$SC.json (run: node gen-civ.js)"; exit 2; }
if netstat -ano | grep -qE ":$PORT .*LISTEN"; then echo "port $PORT busy, abort"; exit 1; fi
export GL="${GL:-E:/Programming/java/wt/gangland-0.21.1/target}" REV=0.21.1
export KS="E:/Programming/java/wt/keystone-1.15.4/keystone-plugin/target/Keystone-1.15.4.jar"
bash "$LIVE/clone.sh" "$NAME" "$PORT"
bash "$LIVE/prep-civ.sh" "$NAME" "$PORT"
set +e
node "$LIVE/run-copai.js" --server "$PROG/servers/$NAME" --jar paper-1.21.11.jar \
  --scenario "$LIVE/scen/$SC.json" --out "$OUT" --version 26.1 < /dev/null
RC=$?
set -e
S="$PROG/servers/$NAME/plugins"
[ -d "$OUT" ] && md5sum "$S"/*.jar "$S"/Gangland_Warfare/modules/*.jar | sed 's#E:/Programming/java/wt/_programme/servers/##' > "$OUT/deployed.md5"
echo "RUN $NAME scenario=$SC rc=$RC out=$OUT"
exit $RC
