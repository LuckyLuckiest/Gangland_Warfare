#!/usr/bin/env bash
# prep-cnc016.sh <profile> [name] [port] - build one sandbox server for the 0.16 "Where they come from" acceptance rows.
# Never touches the live server. Clones _programme/servers/base, flat fresh world, Keystone 1.15.0 + Bartizan 0.6.0 + Gangland 0.16.0
# with all nine module jars (from the integration worktree target/, T21 builds it first). Debug is on for "Cops N Crooks" AND
# "Gangland" (the HOSPITAL / WARD_BILL / SHIELD lines are core log.debug). The 0.15 regression profiles keep their names and
# change only the jars (0.16.0 instead of 0.15.0/0.15.2); n4-old still stages Gangland 0.13.0.
#
# Profiles (name defaults to cnc016-<profile>, port to the number in the table; `default` is an alias of default-016):
#   default-016     25641  shipped 0.16 defaults + Debug                   rows S1-S13 S15 S16, R1 R2 N1 N2 N5-N8
#   s14             25642  default-016 + User.Death.Respawn.Enable true, Delay 3   row S14 (owner ruling D19: downed, then hospital)
#   legacy-settings 25643  0.16 jars + the 0.13.0 settings.yml              row N2 second pass
#   r3              25644  Repeating_Timer.Time 10                          row R3
#   r4              25645  Repeating_Timer.Time 10 + Evasion.Enable false    row R4
#   n3              25646  Take_Money.Enable true                           row N3
#   n3-broken       25647  Take_Money.Enable true + Formula "amount * ("    row N3b
#   n4-old          25648  0.13.0 jars, fresh data                          row N4 phase A
#   n4-new          25648  SAME clone as n4-old, 0.16.0 jars swapped in, data kept   row N4 phase B
#   auto            25649  Drop_Mode AUTO, learning gates 5 s               rows A1-A5 A7-A9
#   auto-compat     25650  the 0.15.1 copsncrooks/wanted.yml (no Auto block) row A6 (run-all adds the bad key)
# DRYRUN=1 checks every input path and prints the plan without cloning or copying anything.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd -W)"
PROG="E:/Programming/java/wt/_programme"
H="$PROG/harness"
LIVE="$PROG/servers/base"
WT16="${WT16:-E:/Programming/java/wt/gangland-0.16.0/target}"
WT13="E:/Programming/java/wt/gangland-0.13.0/target"
KS="E:/Programming/java/wt/keystone-1.15.0/keystone-plugin/target/Keystone-1.15.0.jar"
BZ="E:/Programming/java/wt/bartizan-0.6.0/bartizan-plugin/target/Bartizan-0.6.0.jar"
PROFILE=${1:?usage: prep-cnc016.sh <profile> [name] [port]}
[ "$PROFILE" = default ] && PROFILE=default-016
case $PROFILE in
  default-016) PORT=25641;; s14) PORT=25642;; legacy-settings) PORT=25643;; r3) PORT=25644;; r4) PORT=25645;; n3) PORT=25646;;
  n3-broken) PORT=25647;; n4-old|n4-new) PORT=25648;; auto) PORT=25649;; auto-compat) PORT=25650;; *) echo "unknown profile $PROFILE"; exit 2;;
esac
NAME=${2:-cnc016-$PROFILE}; PORT=${3:-$PORT}
[ "$PROFILE" = n4-new ] && NAME=${2:-cnc016-n4-old}
case $PROFILE in n4-old) GL=$WT13; REV=0.13.0;; *) GL=$WT16; REV=0.16.0;; esac

if [ "${DRYRUN:-0}" = 1 ]; then
  rc=0
  for f in "$KS" "$BZ" "$GL/gangland_warfare-$REV.jar" "$LIVE/plugins/Citizens/config.yml" "$H/clone.sh" "$HERE/yset.js"; do
    if [ -e "$f" ]; then echo "ok      $f"; else echo "MISSING $f"; rc=1; fi
  done
  n=$(ls "$GL"/modules/*-$REV.jar 2>/dev/null | wc -l); echo "modules $n jar(s) in $GL/modules (expect 9 on 0.16.0)"
  [ "$n" -ge 1 ] || rc=1
  echo "plan: profile=$PROFILE name=$NAME port=$PORT jars=$REV -> $PROG/servers/$NAME"
  exit $rc
fi

[ -e "$PROG/servers/base" ] || bash "$H/make-base.sh"
S="$PROG/servers/$NAME"; P="$S/plugins"; G="$P/Gangland_Warfare"

if [ "$PROFILE" = n4-new ]; then
  [ -e "$S" ] || { echo "run prep-cnc016.sh n4-old first (and the N4 phase A scenario)"; exit 1; }
else
  bash "$H/clone.sh" "$NAME" "$PORT" --fresh-data --fresh-world --force
  sed -i -e "s/^level-type=.*/level-type=flat/" -e "s/^generate-structures=.*/generate-structures=false/" "$S/server.properties"
fi
rm -f "$P"/Keystone-*.jar "$P"/Bartizan-*.jar "$P"/gangland_warfare-*.jar "$G"/modules/*.jar
mkdir -p "$G/modules" "$G/copsncrooks" "$G/npc" "$P/Citizens"
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
  -e 's/^   Modules: \[\]$/   Modules: ["Cops N Crooks", "Gangland"]/' "$G/settings.yml"
case $PROFILE in
  s14) Y "$G/settings.yml" User.Death.Respawn.Enable true
       Y "$G/settings.yml" User.Death.Respawn.Delay 3;;
  r3) Y "$G/settings.yml" Wanted.Repeating_Timer.Time 10;;
  r4) Y "$G/settings.yml" Wanted.Repeating_Timer.Time 10
      jarcat "$P/Gangland_Warfare/modules/cops-n-crooks-$REV.jar" copsncrooks/wanted.yml > "$G/copsncrooks/wanted.yml"
      Y "$G/copsncrooks/wanted.yml" Wanted.Evasion.Enable false;;
  n3) Y "$G/settings.yml" Wanted.Take_Money.Enable true;;
  auto) jarcat "$P/Gangland_Warfare/modules/cops-n-crooks-$REV.jar" copsncrooks/wanted.yml > "$G/copsncrooks/wanted.yml"
        Y "$G/copsncrooks/wanted.yml" Wanted.Evasion.Drop_Mode AUTO
        Y "$G/copsncrooks/wanted.yml" Wanted.Evasion.Auto.Learning.Min_Chase_Seconds 5
        Y "$G/copsncrooks/wanted.yml" Wanted.Evasion.Auto.Learning.Min_Seconds_Between_Outcomes 5;;
  auto-compat) git -C "E:/Programming/java/wt/gangland-0.15.2" show 970dfa96:gangland-features/cops-n-crooks/src/main/resources/copsncrooks/wanted.yml > "$G/copsncrooks/wanted.yml"
               # 0.15.1 file (merge 970dfa96): no Auto block, Drop_Mode as shipped; the new jar must boot on it
               ;;
  n3-broken) Y "$G/settings.yml" Wanted.Take_Money.Enable true
             Y "$G/settings.yml" Wanted.Take_Money.Formula '"amount * ("';;
esac
grep -n -A8 '^Debug:' "$G/settings.yml" | head -10
grep -n -A4 '^      Hospital:' "$G/settings.yml" | head -6 || true
cd "$P" && md5sum *.jar Gangland_Warfare/modules/*.jar Citizens/config.yml | sed 's#  # #'
echo "ready: $S (port $PORT)"
