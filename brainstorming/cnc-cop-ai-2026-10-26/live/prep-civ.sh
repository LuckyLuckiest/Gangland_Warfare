#!/usr/bin/env bash
# prep-civ.sh <clone> <port> - prep.sh (GL/REV/KS from env, set by run-civ.sh) + the civ-911 overrides:
#   settings.yml       Debug.Modules ["Cops N Crooks", "Gangland", "Gangland Civilians"] (civilian state transitions)
#   npc/civilians.yml  extracted from the civilians module jar, one edit: Types.pedestrian.AI.Wander.Enabled false,
#                      so a placed civilian stays where the scenario puts it (its idle head turns still run)
# Everything else = shipped defaults (Witness.View_Angle 180, Call_Seconds 8, Intimidate_Range 4, Scared_Cooldown 60,
# Loud_Shot_Seconds 3; wanted.yml Witnesses.Crimes Car_Theft/Assault_Civilian/Kill_Civilian).
set -euo pipefail
LIVE="$(cd "$(dirname "$0")" && pwd -W)"
YSET="E:/Programming/java/Gangland Warfare [Cubed-GTA recoded]/brainstorming/cnc-overhaul-2026-10-05/acceptance/yset.js"
NAME=${1:?name}; PORT=${2:?port}
: "${GL:?GL unset}" "${REV:?REV unset}"
bash "$LIVE/prep.sh" "$NAME" "$PORT"
G="E:/Programming/java/wt/_programme/servers/$NAME/plugins/Gangland_Warfare"
case "$G" in */servers/cop-ai-*) ;; *) echo "refuse: $G"; exit 3;; esac
sed -i 's/^   Modules: \["Cops N Crooks", "Gangland"\]$/   Modules: ["Cops N Crooks", "Gangland", "Gangland Civilians"]/' "$G/settings.yml"
grep -q '^   Modules: \["Cops N Crooks", "Gangland", "Gangland Civilians"\]' "$G/settings.yml" || { echo "Debug.Modules edit failed"; exit 1; }
mkdir -p "$G/npc"
python -I -c "import sys,zipfile;sys.stdout.buffer.write(zipfile.ZipFile(sys.argv[1]).read('npc/civilians.yml'))" \
  "$GL/modules/gangland-civilians-$REV.jar" > "$G/npc/civilians.yml"
node "$YSET" "$G/npc/civilians.yml" Types.pedestrian.AI.Wander.Enabled false
sed -n '/^   pedestrian:/,/^   gang_member:/p' "$G/npc/civilians.yml" | grep -A1 '^         Wander:' | grep -q 'Enabled: false' \
  || { echo "pedestrian wander edit failed"; exit 1; }
echo "civ overrides ok ($NAME)"
