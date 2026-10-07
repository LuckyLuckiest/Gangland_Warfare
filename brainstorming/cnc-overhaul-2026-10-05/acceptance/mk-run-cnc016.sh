#!/usr/bin/env bash
# mk-run-cnc016.sh - (re)build harness/run-cnc016.js = run.js plus ONE extra step, {leftClick:[x,y,z]} (the setup wand's pos1).
# run.js has no left click; the copy sits beside it (same node_modules), the stock run.js is never edited. Idempotent.
set -euo pipefail
H="E:/Programming/java/wt/_programme/harness"
STEP="  if ('leftClick' in st) { const b = botOf(st); const body = { status: 0, location: new Vec3(...st.leftClick), face: 1 }; if (b.supportFeature('useItemWithOwnPacket')) body.sequence = 0; b.swingArm(); b._client.write('block_dig', body); await sleep(st.after ?? 500); return 'leftClick ' + st.leftClick.join(','); }"
awk -v step="$STEP" '{print} /^  if \(.activateBlock. in st\)/ {print step}' "$H/run.js" > "$H/run-cnc016.js"
[ "$(grep -c leftClick "$H/run-cnc016.js")" = 1 ] || { echo "leftClick step not injected"; exit 1; }
echo "ok $H/run-cnc016.js"
