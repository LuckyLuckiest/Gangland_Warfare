#!/usr/bin/env bash
# run-all-cnc016.sh [rows...] - unattended 0.16 acceptance: prep, run and judge every row (default: the sixteen 0.16 rows S1-S16,
# then the 0.15 regression rows R1-R4, N1-N8 and A1-A9 UNCHANGED: same scenarios, same cnc-verdict.js, 0.16 jars). S rows use
# cnc016-verdict.js and the default-016 profile (S14: s14); every row stages E:/Programming/java/wt/gangland-0.16.0/target
# (gangland_warfare-0.16.0.jar + target/modules/*-0.16.0.jar) with Keystone 1.15.0 and Bartizan 0.6.0. T21 builds it first.
# Scenarios: node gen-cnc016.js (S rows), node gen-cnc015.js (R/N/A rows; regenerate both before a run).
# Prints one verdict block per row and a final table; a row that cannot run is reported as ERROR, never silently skipped.
# Sequential on purpose: every server boots with a flat world on its own port, and the harness slot guard caps heavy jobs.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd -W)"
PROG="E:/Programming/java/wt/_programme"
RUNS="$PROG/runs"
SC="$HERE/scenarios"
ROWS=("$@"); [ ${#ROWS[@]} -eq 0 ] && ROWS=(S1 S2 S3 S4 S5 S6 S7 S8 S9 S10 S11 S12 S13 S14 S15 S16 R1 R2 R3 R4 N1 N2 N3 N3b N4 N5 N6 N7 N8 A1 A2 A3 A4 A5 A6 A7 A8 A9)
declare -A RESULT
prep() { bash "$HERE/prep-cnc016.sh" "$@" > "$RUNS/prep-$1.log" 2>&1 || { echo "prep $1 FAILED, see $RUNS/prep-$1.log"; return 1; }; }
run()  { bash "$HERE/run-row-cnc016.sh" "$@" > /dev/null; }   # exit code != 0 = a step failed; the verdict reads the run dir anyway
judge() { local row=$1 v=cnc-verdict.js; shift; case $row in S[0-9]*) v=cnc016-verdict.js;; esac; node "$HERE/$v" "$row" "$@"; RESULT[$row]=$?; return 0; }
# srow <row> <scenario-basename> [profile]: prep the profile, run the scenario once, judge it
srow() { local r=$1 sc=$2 prof=${3:-default-016}; prep $prof && { run cnc016-$prof "$SC/$sc.json"; judge $r "$RUNS/cnc016-$prof--$sc"; } || RESULT[$r]=ERROR; }
mkdir -p "$RUNS"

for row in "${ROWS[@]}"; do
  echo; echo "######## $row"
  case $row in
    S1) srow S1 cnc016-S1-wand-station-docks;;
    S2) srow S2 cnc016-S2-dispatch-from-station;;
    S3) srow S3 cnc016-S3-mixed-tiers;;
    S4) srow S4 cnc016-S4-wipe-breather;;
    S5) srow S5 cnc016-S5-perimeter-posts;;
    S6) srow S6 cnc016-S6-outrun-handoff;;
    S7) srow S7 cnc016-S7-hideout-faster;;
    S8) srow S8 cnc016-S8-quiet-minute;;
    S9) srow S9 cnc016-S9-contact-phone;;
    S10) srow S10 cnc016-S10-sign-refused;;
    S11) srow S11 cnc016-S11-self-defence;;
    S12) srow S12 cnc016-S12-posted-bounty;;
    S13) srow S13 cnc016-S13-unposted-kill;;
    S14) srow S14 cnc016-S14-hospital-downed s14;;
    S15) srow S15 cnc016-S15-hospital-vanilla-death;;
    S16) srow S16 cnc016-S16-logout-restore;;
    S17a) srow S17a cnc016-S17a-bribe-star-taken;;
    S17b) srow S17b cnc016-S17b-bribe-star-seen;;
    R1) prep default-016 && { run cnc016-default-016 "$SC/R1-reg-ladder-1-3-5.json"; judge R1 "$RUNS/cnc016-default-016--R1-reg-ladder-1-3-5"; } || RESULT[R1]=ERROR;;
    R2) prep default-016 && { run cnc016-default-016 "$SC/R2-reg-los-break.json"
          rm -rf "$RUNS/_r2"; mkdir -p "$RUNS/_r2"; cp -r "$RUNS/cnc016-default-016--R2-reg-los-break" "$RUNS/_r2/h11-losbreak-1"
          # h11-verdict.js judges every H11 row; only the "Line of sight broken" row is meaningful here
          ( cd "$PROG/harness" && H11_RUNS="$RUNS/_r2" node "$HERE/h11-verdict-cnc015.js" | grep -E "^\| #|^\|---|Line of sight broken" | tee "$RUNS/R2-verdict.txt" )
          grep -q "Line of sight broken | PASS" "$RUNS/R2-verdict.txt" && RESULT[R2]=0 || RESULT[R2]=1; } || RESULT[R2]=ERROR;;
    R3) prep r3 && { run cnc016-r3 "$SC/R3-reg-cuffed.json"; judge R3 "$RUNS/cnc016-r3--R3-reg-cuffed"; } || RESULT[R3]=ERROR;;
    R4) prep r4 && { run cnc016-r4 "$SC/R4a-reg-logout-quit.json"; run cnc016-r4 "$SC/R4b-reg-logout-rejoin.json"
          judge R4 "$RUNS/cnc016-r4--R4a-reg-logout-quit" "$RUNS/cnc016-r4--R4b-reg-logout-rejoin"; } || RESULT[R4]=ERROR;;
    N1) prep default-016 && { run cnc016-default-016 "$SC/N1-evasion-drop.json"; judge N1 "$RUNS/cnc016-default-016--N1-evasion-drop"; } || RESULT[N1]=ERROR;;
    N2) prep default-016 && prep legacy-settings && {
          run cnc016-default-016 "$SC/N2-no-money-on-drop.json"; run cnc016-legacy-settings "$SC/N2-no-money-on-drop.json"
          judge N2 "$RUNS/cnc016-default-016--N2-no-money-on-drop" "$RUNS/cnc016-legacy-settings--N2-no-money-on-drop"; } || RESULT[N2]=ERROR;;
    N3) prep n3 && { run cnc016-n3 "$SC/N3-charge-switched-on.json"; judge N3 "$RUNS/cnc016-n3--N3-charge-switched-on"; } || RESULT[N3]=ERROR;;
    N3b) prep n3-broken && { run cnc016-n3-broken "$SC/N3b-charge-broken-formula.json"; judge N3b "$RUNS/cnc016-n3-broken--N3b-charge-broken-formula"; } || RESULT[N3b]=ERROR;;
    N4) prep n4-old && { run cnc016-n4-old "$SC/N4a-bounty-post-on-0.13.0.json"; prep n4-new && run cnc016-n4-old "$SC/N4b-bounty-kill-on-0.15.0.json"
          judge N4 "$RUNS/cnc016-n4-old--N4a-bounty-post-on-0.13.0" "$RUNS/cnc016-n4-old--N4b-bounty-kill-on-0.15.0"; } || RESULT[N4]=ERROR;;
    N5) prep default-016 && { run cnc016-default-016 "$SC/N5-shots-fired.json"; judge N5 "$RUNS/cnc016-default-016--N5-shots-fired"; } || RESULT[N5]=ERROR;;
    N6) prep default-016 && { run cnc016-default-016 "$SC/N6-assault-cop-star.json"; judge N6 "$RUNS/cnc016-default-016--N6-assault-cop-star"; } || RESULT[N6]=ERROR;;
    N7) prep default-016 && { run cnc016-default-016 "$SC/N7-charge-sheet.json"; judge N7 "$RUNS/cnc016-default-016--N7-charge-sheet"; } || RESULT[N7]=ERROR;;
    N8) prep default-016 && { run cnc016-default-016 "$SC/N8-regroup.json"; judge N8 "$RUNS/cnc016-default-016--N8-regroup"; } || RESULT[N8]=ERROR;;
    A1) prep auto && { run cnc016-auto "$SC/A1-auto-hunker.json"; judge A1 "$RUNS/cnc016-auto--A1-auto-hunker"; } || RESULT[A1]=ERROR;;
    A2) prep auto && { run cnc016-auto "$SC/A2-auto-petty.json"; judge A2 "$RUNS/cnc016-auto--A2-auto-petty"; } || RESULT[A2]=ERROR;;
    A3) prep auto && { run cnc016-auto "$SC/A3-auto-rampage-lock.json"; judge A3 "$RUNS/cnc016-auto--A3-auto-rampage-lock"; } || RESULT[A3]=ERROR;;
    A4) prep auto && { run cnc016-auto "$SC/A4a-auto-logout-quit.json"; run cnc016-auto "$SC/A4b-auto-logout-rejoin.json"
          judge A4 "$RUNS/cnc016-auto--A4a-auto-logout-quit" "$RUNS/cnc016-auto--A4b-auto-logout-rejoin"; } || RESULT[A4]=ERROR;;
    A5) prep auto && { run cnc016-auto "$SC/A5a-auto-learn-two-chases.json"
          # server is stopped now: read the learned row (one *.db file in plugins/Gangland_Warfare/) for the verdict, then restart on it
          DB=$(find "$PROG/servers/cnc016-auto/plugins/Gangland_Warfare" -name gangland.db | head -1)
          /c/msys64/ucrt64/bin/sqlite3 "$DB" "SELECT player_uuid, n, actual, expected FROM chase_habit;" | tee "$RUNS/cnc016-auto--A5a-auto-learn-two-chases/chase_habit.txt"
          run cnc016-auto "$SC/A5b-auto-learn-after-restart.json"
          judge A5 "$RUNS/cnc016-auto--A5a-auto-learn-two-chases" "$RUNS/cnc016-auto--A5b-auto-learn-after-restart"; } || RESULT[A5]=ERROR;;
    A6) prep auto-compat && { run cnc016-auto-compat "$SC/A6-auto-config-compat.json"
          rm -rf "$RUNS/cnc016-auto-compat--A6-first"; mv "$RUNS/cnc016-auto-compat--A6-auto-config-compat" "$RUNS/cnc016-auto-compat--A6-first"
          node "$HERE/yset.js" "$PROG/servers/cnc016-auto-compat/plugins/Gangland_Warfare/copsncrooks/wanted.yml" Wanted.Evasion.Auto.Momentum.Step_Speed 1.5
          run cnc016-auto-compat "$SC/A6-auto-config-compat.json"
          judge A6 "$RUNS/cnc016-auto-compat--A6-first" "$RUNS/cnc016-auto-compat--A6-auto-config-compat"; } || RESULT[A6]=ERROR;;
    A7) prep auto && { run cnc016-auto "$SC/A7-auto-clean-break.json"; judge A7 "$RUNS/cnc016-auto--A7-auto-clean-break"; } || RESULT[A7]=ERROR;;
    A8) prep auto && { run cnc016-auto "$SC/A8-auto-flee-on-foot.json"; judge A8 "$RUNS/cnc016-auto--A8-auto-flee-on-foot"; } || RESULT[A8]=ERROR;;
    A9) prep auto && { run cnc016-auto "$SC/A9-auto-teleport.json"; judge A9 "$RUNS/cnc016-auto--A9-auto-teleport"; } || RESULT[A9]=ERROR;;
    *) echo "unknown row $row"; RESULT[$row]=ERROR;;
  esac
done

echo; echo "======== SUMMARY (0 = PASS, 1 = FAIL, 2 = REVIEW by a human, ERROR = could not run)"
for row in "${ROWS[@]}"; do echo "$row: ${RESULT[$row]:-ERROR}"; done
echo "Pass criterion for 0.16: S1-S16 all 0 (S2 S5 S6 S8 may be 2 where the radio rate-limits or a spawn spot is soft: read the transcript); R1-R4, N1-N8, A1-A9 as in 0.15 and 0.15.2:"
echo "  R1 R2 R3 R4 N1 N2 N3 N4 = 0 (R1 and N4 may be 2), A1 A2 A3 A5 A6 = 0, A4 = 0 or 2, A7 A8 A9 = 2 read by a human."
