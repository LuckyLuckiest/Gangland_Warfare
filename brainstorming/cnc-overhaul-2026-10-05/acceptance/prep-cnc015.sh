#!/usr/bin/env bash
# prep-cnc015.sh <profile> [name] [port] - build one sandbox server for the 0.15 "Lose them" acceptance rows.
# Never touches the live server (its Citizens config is only read). Clones _programme/servers/base (built on first use by
# make-base.sh from the live server, read-only), flat fresh world, Keystone 1.14.0 + Bartizan 0.6.1 + Gangland 0.15.0 with all
# nine module jars (from the integration worktree target/ - T18 builds it first), Debug on for "Cops N Crooks".
#
# Profiles (name defaults to cnc015-<profile>, port to the number in the table):
#   default         25631  shipped 0.15 defaults                          rows R1 R2 N1 N2 N5 N6 N7 N8
#   legacy-settings 25632  0.15 jars + the 0.13.0 settings.yml (no Take_Money.Enable key)   row N2 second pass
#   r3              25633  Repeating_Timer.Time 10 (a drop while cuffed would show fast)    row R3
#   r4              25634  Repeating_Timer.Time 10 + Evasion.Enable false (wanted.yml)       row R4
#   n3              25635  Take_Money.Enable true                                           row N3
#   n3-broken       25636  Take_Money.Enable true + Formula "amount * ("                    row N3b
#   n4-old          25637  0.13.0 jars (Gangland 0.13.0 modules), fresh data               row N4 phase A
#   n4-new          25637  SAME clone as n4-old, 0.15.0 jars swapped in, data kept          row N4 phase B
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd -W)"
PROG="E:/Programming/java/wt/_programme"
H="$PROG/harness"
LIVE="E:/Programming/java/wt/_programme/servers/base"  # T18: base copied from the Keystone harness base, live server never read
WT15="E:/Programming/java/wt/gangland-0.15.0/target"
WT13="E:/Programming/java/wt/gangland-0.13.0/target"
KS="E:/Programming/java/wt/keystone-1.14.0/keystone-plugin/target/Keystone-1.14.0.jar"
BZ="E:/Programming/java/wt/bartizan-0.6.1/bartizan-plugin/target/Bartizan-0.6.1.jar"
PROFILE=${1:?usage: prep-cnc015.sh <profile> [name] [port]}
case $PROFILE in
  default) PORT=25631;; legacy-settings) PORT=25632;; r3) PORT=25633;; r4) PORT=25634;; n3) PORT=25635;;
  n3-broken) PORT=25636;; n4-old|n4-new) PORT=25637;; *) echo "unknown profile $PROFILE"; exit 2;;
esac
NAME=${2:-cnc015-$PROFILE}; PORT=${3:-$PORT}
[ "$PROFILE" = n4-new ] && NAME=${2:-cnc015-n4-old}

[ -e "$PROG/servers/base" ] || bash "$H/make-base.sh"
S="$PROG/servers/$NAME"; P="$S/plugins"; G="$P/Gangland_Warfare"

if [ "$PROFILE" = n4-new ]; then
  [ -e "$S" ] || { echo "run prep-cnc015.sh n4-old first (and the N4 phase A scenario)"; exit 1; }
  GL=$WT15; REV=0.15.0
else
  bash "$H/clone.sh" "$NAME" "$PORT" --fresh-data --fresh-world --force
  sed -i -e "s/^level-type=.*/level-type=flat/" -e "s/^generate-structures=.*/generate-structures=false/" "$S/server.properties"
  if [ "$PROFILE" = n4-old ]; then GL=$WT13; REV=0.13.0; else GL=$WT15; REV=0.15.0; fi
fi
rm -f "$P"/Keystone-*.jar "$P"/Bartizan-*.jar "$P"/gangland_warfare-*.jar "$G"/modules/*.jar
mkdir -p "$G/modules" "$G/npc" "$P/Citizens"
cp "$KS" "$BZ" "$P/"
cp "$GL/gangland_warfare-$REV.jar" "$P/"
cp "$GL"/modules/*-$REV.jar "$G/modules/"
cp "$LIVE/plugins/Citizens/config.yml" "$P/Citizens/config.yml"
[ "$PROFILE" = n4-new ] && { cd "$P" && md5sum *.jar Gangland_Warfare/modules/*.jar | sed 's#  # #'; exit 0; }

# settings.yml: shipped file of the jar under test (a server that has none gets this one at first boot anyway); legacy = 0.13.0's
jarcat() { python -c "import sys,zipfile;sys.stdout.buffer.write(zipfile.ZipFile(sys.argv[1]).read(sys.argv[2]))" "$1" "$2"; }
SRC="$P/gangland_warfare-$REV.jar"
[ "$PROFILE" = legacy-settings ] && SRC="$WT13/gangland_warfare-0.13.0.jar"
jarcat "$SRC" settings.yml > "$G/settings.yml"
Y() { node "$HERE/yset.js" "$@"; }
sed -i -e '/^Debug:/,/^   Modules:/ s/^   Enabled: false/   Enabled: true/' \
  -e 's/^   Modules: \[\]$/   Modules: ["Cops N Crooks"]/' "$G/settings.yml"
case $PROFILE in
  r3) Y "$G/settings.yml" Wanted.Repeating_Timer.Time 10;;
  r4) Y "$G/settings.yml" Wanted.Repeating_Timer.Time 10
      jarcat "$P/Gangland_Warfare/modules/cops-n-crooks-$REV.jar" copsncrooks/wanted.yml > "$G/copsncrooks/wanted.yml"
      Y "$G/copsncrooks/wanted.yml" Wanted.Evasion.Enable false;;
  n3) Y "$G/settings.yml" Wanted.Take_Money.Enable true;;
  n3-broken) Y "$G/settings.yml" Wanted.Take_Money.Enable true
             Y "$G/settings.yml" Wanted.Take_Money.Formula '"amount * ("';;
esac
grep -n -A8 '^Wanted:' "$G/settings.yml" | head -14
cd "$P" && md5sum *.jar Gangland_Warfare/modules/*.jar Citizens/config.yml | sed 's#  # #'
echo "ready: $S (port $PORT)"
