#!/usr/bin/env bash
# run-acc.sh <key> <scenario> <tag> - 0.21.1 ACCEPTANCE run: run.sh with the FIXED build deployed
#   Gangland E:/Programming/java/wt/gangland-0.21.1/target (core + 10 module jars, branch 0.21.1 @ cdd447d7d, civ merged),
#   Keystone 1.15.4 (wt/keystone-1.15.4 @ 08c8453), Bartizan 0.6.0 and Citizens/base as the repro.
#   scenario  scen/<scenario>.json (acc-* from gen-acc.js, or a repro scenario)
#   tag       REQUIRED, unique: output _programme/runs/cop-ai-<key>--<tag>; an existing dir is refused, never overwritten
# The deployed jar md5s are written to <out>/deployed.md5. Fresh clone + prep on every run (clone.sh/prep.sh).
set -euo pipefail
LIVE="$(cd "$(dirname "$0")" && pwd -W)"
KEY=${1:?key}; SC=${2:?scenario}; TAG=${3:?tag (unique per run)}
OUT="E:/Programming/java/wt/_programme/runs/cop-ai-$KEY--$TAG"
[ -e "$OUT" ] && { echo "run dir exists, pick a new tag: $OUT"; exit 4; }
export GL="E:/Programming/java/wt/gangland-0.21.1/target" REV=0.21.1
export KS="E:/Programming/java/wt/keystone-1.15.4/keystone-plugin/target/Keystone-1.15.4.jar"
set +e
bash "$LIVE/run.sh" "$KEY" "$SC" "$TAG"
RC=$?
set -e
if [ -d "$OUT" ]; then
  S="E:/Programming/java/wt/_programme/servers/cop-ai-$KEY/plugins"
  md5sum "$S"/*.jar "$S"/Gangland_Warfare/modules/*.jar | sed 's#E:/Programming/java/wt/_programme/servers/##' > "$OUT/deployed.md5"
fi
exit $RC
