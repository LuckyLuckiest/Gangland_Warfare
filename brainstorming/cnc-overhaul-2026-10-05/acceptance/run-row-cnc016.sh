#!/usr/bin/env bash
# run-row-cnc016.sh <name> <scenario.json> [client-version=26.1] - run-row.sh with the 0.16 harness copy (adds {leftClick}).
# Output in E:/Programming/java/wt/_programme/runs/<name>--<scenario>/ (server.log, chat.txt, summary.txt). Exit 1 = a step failed.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd -W)"
H="E:/Programming/java/wt/_programme/harness"
NAME=$1 SC=$2 VER=${3:-26.1}
B=$(basename "$SC" .json)
bash "$HERE/mk-run-cnc016.sh" > /dev/null || exit 2
mkdir -p "$H/../runs"
cd "$H" && node run-cnc016.js --server "../servers/$NAME" --jar paper-1.21.11.jar --scenario "$SC" \
  --out "../runs/$NAME--$B" --version "$VER" > "../runs/$NAME--$B.stdout" 2>&1
RC=$?; echo "exit=$RC"; head -3 "../runs/$NAME--$B.stdout"; exit $RC
