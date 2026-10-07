#!/usr/bin/env bash
# run-row.sh <name> <scenario.json> [client-version=26.1] - run one scenario on prepared server <name> (prep-cnc015.sh),
# output in E:/Programming/java/wt/_programme/runs/<name>--<scenario>/ (server.log, chat.txt, summary.txt). Exit 1 = a step failed.
set -uo pipefail
H="E:/Programming/java/wt/_programme/harness"
NAME=$1 SC=$2 VER=${3:-26.1}
B=$(basename "$SC" .json)
mkdir -p "$H/../runs"
cd "$H" && node run.js --server "../servers/$NAME" --jar paper-1.21.11.jar --scenario "$SC" \
  --out "../runs/$NAME--$B" --version "$VER" > "../runs/$NAME--$B.stdout" 2>&1
RC=$?; echo "exit=$RC"; head -3 "../runs/$NAME--$B.stdout"; exit $RC
