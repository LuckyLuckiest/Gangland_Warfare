#!/usr/bin/env bash
# clone.sh <name> <port> - fresh clone of _programme/servers/base for one cop-ai row (copy of cnc21f2 acc-r3
# laneclone-r3.sh). libraries/cache/versions are junctions into servers/base (created with PowerShell; cmd mklink fails
# under Git Bash); world dirs are skipped because prep.sh regenerates them. Junctions are removed with rmdir (link only)
# before any recursive delete, so the base is never touched by rm -rf. Never touches the owner's live Test Server.
set -euo pipefail
NAME=$1; PORT=$2
S="E:/Programming/java/wt/_programme/servers"; B="$S/base"; D="$S/$NAME"
case "$D" in */servers/cop-ai-*) ;; *) echo "refuse $D"; exit 3;; esac
if [ -d "$D" ]; then
  for n in libraries cache versions; do
    if [ -e "$D/$n" ]; then cmd.exe //c "rmdir \"$(cygpath -w "$D/$n")\"" > /dev/null 2>&1 || true; fi
  done
fi
rm -rf "$D"; mkdir -p "$D"
for f in "$B"/* "$B"/.[!.]*; do
  [ -e "$f" ] || continue
  n=$(basename "$f")
  case $n in
    libraries|cache|versions|world|world_nether|world_the_end) ;;
    *) cp -a "$f" "$D/";;
  esac
done
for n in libraries cache versions; do
  WB=$(cygpath -w "$B/$n"); WD=$(cygpath -w "$D/$n")
  powershell.exe -NoProfile -Command "New-Item -ItemType Junction -Path '$WD' -Target '$WB' | Out-Null" > /dev/null
  [ -e "$D/$n/" ] || { echo "junction $n failed"; exit 4; }
done
sed -i -e "s/^server-port=.*/server-port=$PORT/" -e "s/^query.port=.*/query.port=$PORT/" "$D/server.properties"
echo "fresh clone $NAME port=$PORT ok"
