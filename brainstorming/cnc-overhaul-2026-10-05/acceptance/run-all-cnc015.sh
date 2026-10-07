#!/usr/bin/env bash
# run-all-cnc015.sh [rows...] - unattended 0.15 acceptance: prep, run and judge every row (default: the pass-criterion rows
# R1 R2 R3 R4 N1 N2 N3 N3b N4, then the best-effort rows N5 N6 N7 N8, then the 0.15.2 AUTO rows A1-A9; the A rows stage
# E:/Programming/java/wt/gangland-0.15.2/target (profiles auto, auto-compat), the others the 0.15.0 jars). Needs the integration worktree built:
#   E:/Programming/java/wt/gangland-0.15.0/target/gangland_warfare-0.15.0.jar + target/modules/*-0.15.0.jar  (T18 does the build).
# Prints one verdict block per row and a final table; a row that cannot run is reported as ERROR, never silently skipped.
# Sequential on purpose: every server boots with a flat world on its own port, and the harness slot guard caps heavy jobs.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd -W)"
PROG="E:/Programming/java/wt/_programme"
RUNS="$PROG/runs"
SC="$HERE/scenarios"
ROWS=("$@"); [ ${#ROWS[@]} -eq 0 ] && ROWS=(R1 R2 R3 R4 N1 N2 N3 N3b N4 N5 N6 N7 N8)
declare -A RESULT
prep() { bash "$HERE/prep-cnc015.sh" "$@" > "$RUNS/prep-$1.log" 2>&1 || { echo "prep $1 FAILED, see $RUNS/prep-$1.log"; return 1; }; }
run()  { bash "$HERE/run-row.sh" "$@" > /dev/null; }   # exit code != 0 = a step failed; the verdict reads the run dir anyway
judge() { local row=$1; shift; node "$HERE/cnc-verdict.js" "$row" "$@"; RESULT[$row]=$?; return 0; }
mkdir -p "$RUNS"

for row in "${ROWS[@]}"; do
  echo; echo "######## $row"
  case $row in
    R1) prep default && { run cnc015-default "$SC/R1-reg-ladder-1-3-5.json"; judge R1 "$RUNS/cnc015-default--R1-reg-ladder-1-3-5"; } || RESULT[R1]=ERROR;;
    R2) prep default && { run cnc015-default "$SC/R2-reg-los-break.json"
          rm -rf "$RUNS/_r2"; mkdir -p "$RUNS/_r2"; cp -r "$RUNS/cnc015-default--R2-reg-los-break" "$RUNS/_r2/h11-losbreak-1"
          # h11-verdict.js judges every H11 row; only the "Line of sight broken" row is meaningful here
          ( cd "$PROG/harness" && H11_RUNS="$RUNS/_r2" node "$HERE/h11-verdict-cnc015.js" | grep -E "^\| #|^\|---|Line of sight broken" | tee "$RUNS/R2-verdict.txt" )
          grep -q "Line of sight broken | PASS" "$RUNS/R2-verdict.txt" && RESULT[R2]=0 || RESULT[R2]=1; } || RESULT[R2]=ERROR;;
    R3) prep r3 && { run cnc015-r3 "$SC/R3-reg-cuffed.json"; judge R3 "$RUNS/cnc015-r3--R3-reg-cuffed"; } || RESULT[R3]=ERROR;;
    R4) prep r4 && { run cnc015-r4 "$SC/R4a-reg-logout-quit.json"; run cnc015-r4 "$SC/R4b-reg-logout-rejoin.json"
          judge R4 "$RUNS/cnc015-r4--R4a-reg-logout-quit" "$RUNS/cnc015-r4--R4b-reg-logout-rejoin"; } || RESULT[R4]=ERROR;;
    N1) prep default && { run cnc015-default "$SC/N1-evasion-drop.json"; judge N1 "$RUNS/cnc015-default--N1-evasion-drop"; } || RESULT[N1]=ERROR;;
    N2) prep default && prep legacy-settings && {
          run cnc015-default "$SC/N2-no-money-on-drop.json"; run cnc015-legacy-settings "$SC/N2-no-money-on-drop.json"
          judge N2 "$RUNS/cnc015-default--N2-no-money-on-drop" "$RUNS/cnc015-legacy-settings--N2-no-money-on-drop"; } || RESULT[N2]=ERROR;;
    N3) prep n3 && { run cnc015-n3 "$SC/N3-charge-switched-on.json"; judge N3 "$RUNS/cnc015-n3--N3-charge-switched-on"; } || RESULT[N3]=ERROR;;
    N3b) prep n3-broken && { run cnc015-n3-broken "$SC/N3b-charge-broken-formula.json"; judge N3b "$RUNS/cnc015-n3-broken--N3b-charge-broken-formula"; } || RESULT[N3b]=ERROR;;
    N4) prep n4-old && { run cnc015-n4-old "$SC/N4a-bounty-post-on-0.13.0.json"; prep n4-new && run cnc015-n4-old "$SC/N4b-bounty-kill-on-0.15.0.json"
          judge N4 "$RUNS/cnc015-n4-old--N4a-bounty-post-on-0.13.0" "$RUNS/cnc015-n4-old--N4b-bounty-kill-on-0.15.0"; } || RESULT[N4]=ERROR;;
    N5) prep default && { run cnc015-default "$SC/N5-shots-fired.json"; judge N5 "$RUNS/cnc015-default--N5-shots-fired"; } || RESULT[N5]=ERROR;;
    N6) prep default && { run cnc015-default "$SC/N6-assault-cop-star.json"; judge N6 "$RUNS/cnc015-default--N6-assault-cop-star"; } || RESULT[N6]=ERROR;;
    N7) prep default && { run cnc015-default "$SC/N7-charge-sheet.json"; judge N7 "$RUNS/cnc015-default--N7-charge-sheet"; } || RESULT[N7]=ERROR;;
    N8) prep default && { run cnc015-default "$SC/N8-regroup.json"; judge N8 "$RUNS/cnc015-default--N8-regroup"; } || RESULT[N8]=ERROR;;
    A1) prep auto && { run cnc015-auto "$SC/A1-auto-hunker.json"; judge A1 "$RUNS/cnc015-auto--A1-auto-hunker"; } || RESULT[A1]=ERROR;;
    A2) prep auto && { run cnc015-auto "$SC/A2-auto-petty.json"; judge A2 "$RUNS/cnc015-auto--A2-auto-petty"; } || RESULT[A2]=ERROR;;
    A3) prep auto && { run cnc015-auto "$SC/A3-auto-rampage-lock.json"; judge A3 "$RUNS/cnc015-auto--A3-auto-rampage-lock"; } || RESULT[A3]=ERROR;;
    A4) prep auto && { run cnc015-auto "$SC/A4a-auto-logout-quit.json"; run cnc015-auto "$SC/A4b-auto-logout-rejoin.json"
          judge A4 "$RUNS/cnc015-auto--A4a-auto-logout-quit" "$RUNS/cnc015-auto--A4b-auto-logout-rejoin"; } || RESULT[A4]=ERROR;;
    A5) prep auto && { run cnc015-auto "$SC/A5a-auto-learn-two-chases.json"
          # server is stopped now: read the learned row (one *.db file in plugins/Gangland_Warfare/) for the verdict, then restart on it
          DB=$(ls "$PROG/servers/cnc015-auto/plugins/Gangland_Warfare/"*.db | head -1)
          /c/msys64/ucrt64/bin/sqlite3 "$DB" "SELECT player_uuid, n, actual, expected FROM chase_habit;" | tee "$RUNS/cnc015-auto--A5a-auto-learn-two-chases/chase_habit.txt"
          run cnc015-auto "$SC/A5b-auto-learn-after-restart.json"
          judge A5 "$RUNS/cnc015-auto--A5a-auto-learn-two-chases" "$RUNS/cnc015-auto--A5b-auto-learn-after-restart"; } || RESULT[A5]=ERROR;;
    A6) prep auto-compat && { run cnc015-auto-compat "$SC/A6-auto-config-compat.json"
          rm -rf "$RUNS/cnc015-auto-compat--A6-first"; mv "$RUNS/cnc015-auto-compat--A6-auto-config-compat" "$RUNS/cnc015-auto-compat--A6-first"
          node "$HERE/yset.js" "$PROG/servers/cnc015-auto-compat/plugins/Gangland_Warfare/copsncrooks/wanted.yml" Wanted.Evasion.Auto.Momentum.Step_Speed 1.5
          run cnc015-auto-compat "$SC/A6-auto-config-compat.json"
          judge A6 "$RUNS/cnc015-auto-compat--A6-first" "$RUNS/cnc015-auto-compat--A6-auto-config-compat"; } || RESULT[A6]=ERROR;;
    A7) prep auto && { run cnc015-auto "$SC/A7-auto-clean-break.json"; judge A7 "$RUNS/cnc015-auto--A7-auto-clean-break"; } || RESULT[A7]=ERROR;;
    A8) prep auto && { run cnc015-auto "$SC/A8-auto-flee-on-foot.json"; judge A8 "$RUNS/cnc015-auto--A8-auto-flee-on-foot"; } || RESULT[A8]=ERROR;;
    A9) prep auto && { run cnc015-auto "$SC/A9-auto-teleport.json"; judge A9 "$RUNS/cnc015-auto--A9-auto-teleport"; } || RESULT[A9]=ERROR;;
    *) echo "unknown row $row"; RESULT[$row]=ERROR;;
  esac
done

echo; echo "======== SUMMARY (0 = PASS, 1 = FAIL, 2 = REVIEW by a human, ERROR = could not run)"
for row in "${ROWS[@]}"; do echo "$row: ${RESULT[$row]:-ERROR}"; done
echo "Pass criterion for 0.15: R1 R2 R3 R4 N1 N2 N3 N4 all 0 (R1 and N4 may be 2 when only the human read-through is left)."
echo "Pass criterion for 0.15.2: A1 A2 A3 A5 A6 = 0, A4 = 0 or 2, A7 A8 A9 = 2 read by a human; R1-R4 and N1-N8 above (default profile, ONE_STAR) unchanged."
