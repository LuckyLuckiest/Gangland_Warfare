#!/usr/bin/env bash
# prep.sh <clone> <port> - deploys Gangland 0.21.0 (pre-fix, = master a14bcfbcb code) into one fresh cop-ai clone.
# Copy of cnc21f2 acc-r3 prep-r3.sh (the newest cnc-integ prep): core + all 10 module jars from wt/cnc-0.21.0/target,
# Keystone 1.15.3, Bartizan 0.6.0, Citizens config from servers/base, flat world, Debug on for "Cops N Crooks".
# Deletes only inside servers/cop-ai-*.
#
# Overrides vs shipped defaults (documented in live/README.md):
#   server.properties  level-type=flat, generate-structures=false (as every CnC acceptance prep)
#                      view-distance=16 (the owner's live server value; servers/base has 6, which would unload the
#                      chunks stranded walkers stand in and mask spawn-dup cause 1)
#   settings.yml       Debug.Enabled true, Debug.Modules ["Cops N Crooks", "Gangland"]; User.Death.Respawn on, 3 s
#   cops.yml / wanted.yml / cop_roles.yml: NONE (shipped defaults)
set -euo pipefail
PROG="E:/Programming/java/wt/_programme"; BASE="$PROG/servers/base"
YSET="E:/Programming/java/Gangland Warfare [Cubed-GTA recoded]/brainstorming/cnc-overhaul-2026-10-05/acceptance/yset.js"
# 2026-10-26 acceptance: GL/REV/KS overridable by env (run-acc.sh sets the 0.21.1 fixed build); defaults = the repro build
KS="${KS:-E:/Programming/java/wt/keystone-1.15.3/keystone-plugin/target/Keystone-1.15.3.jar}"
BZ="E:/Programming/java/wt/bartizan-0.6.0/bartizan-plugin/target/Bartizan-0.6.0.jar"
GL="${GL:-E:/Programming/java/wt/cnc-0.21.0/target}"; REV="${REV:-0.21.0}"
NAME=${1:?name}; PORT=${2:?port}
for f in "$KS" "$BZ" "$GL/gangland_warfare-$REV.jar" "$BASE/plugins/Citizens/config.yml" "$YSET"; do
  [ -e "$f" ] || { echo "MISSING $f"; exit 1; }
done
[ "$(ls "$GL"/modules/*-$REV.jar | wc -l)" -eq 10 ] || { echo "expected 10 module jars in $GL/modules"; exit 1; }
S="$PROG/servers/$NAME"; P="$S/plugins"; G="$P/Gangland_Warfare"
case "$S" in */servers/cop-ai-*) ;; *) echo "refuse: $S"; exit 3;; esac
[ -d "$S" ] || { echo "no clone $S"; exit 1; }
rm -rf "$S/world" "$S/world_nether" "$S/world_the_end" "$S/logs"
for d in "$P"/*/; do n=$(basename "$d"); [ "$n" = Gangland_Warfare ] || rm -rf "$d"; done
mkdir -p "$G"
find "$G" -mindepth 1 -maxdepth 1 ! -name modules -exec rm -rf {} +
mkdir -p "$G/modules"
find "$G/modules" -mindepth 1 ! -name '*.jar' -exec rm -rf {} + 2>/dev/null || true
rm -f "$P"/Keystone-*.jar "$P"/Bartizan-*.jar "$P"/gangland_warfare-*.jar "$G"/modules/*.jar
sed -i -e "s/^server-port=.*/server-port=$PORT/" -e "s/^query.port=.*/query.port=$PORT/" \
  -e "s/^level-type=.*/level-type=flat/" -e "s/^generate-structures=.*/generate-structures=false/" \
  -e "s/^view-distance=.*/view-distance=16/" "$S/server.properties"
mkdir -p "$G/copsncrooks" "$P/Citizens"
cp "$KS" "$BZ" "$GL/gangland_warfare-$REV.jar" "$P/"
cp "$GL"/modules/*-$REV.jar "$G/modules/"
cp "$BASE/plugins/Citizens/config.yml" "$P/Citizens/config.yml"
python -I -c "import sys,zipfile;sys.stdout.buffer.write(zipfile.ZipFile(sys.argv[1]).read('settings.yml'))" \
  "$GL/gangland_warfare-$REV.jar" > "$G/settings.yml"
sed -i -e '/^Debug:/,/^   Modules:/ s/^   Enabled: false/   Enabled: true/' \
  -e 's/^   Modules: \[\]$/   Modules: ["Cops N Crooks", "Gangland"]/' "$G/settings.yml"
grep -q '^   Modules: \["Cops N Crooks", "Gangland"\]' "$G/settings.yml" || { echo "Debug.Modules edit failed"; exit 1; }
node "$YSET" "$G/settings.yml" User.Death.Respawn.Enable true
node "$YSET" "$G/settings.yml" User.Death.Respawn.Delay 3
echo "prepped $NAME port=$PORT build=$REV ks=$(basename "$KS") bz=$(basename "$BZ")"
