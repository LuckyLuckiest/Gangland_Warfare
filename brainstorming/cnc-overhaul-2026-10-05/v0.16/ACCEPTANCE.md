# ACCEPTANCE - Cops N Crooks 0.16 "Where they come from" (Gangland 0.16.0, Keystone 1.15.0, Bartizan 0.6.0)

Task 21. Built and run from the lane worktree `E:/Programming/java/wt/cnc016-t21` (branch `cnc-0.16-t21`, identical code to the
integration head 2b9a7221, only acceptance tooling differs). Sandbox harness only (`E:/Programming/java/wt/_programme/harness`,
Paper 1.21.11 test jar, flat fresh world per row, Debug on for "Cops N Crooks" and "Gangland"). The live test server
`E:/Documents/Minecraft/Test Server` was never started, deployed to or touched. Raw verdicts: `acceptance-evidence/` beside this file;
full run dirs (server.log, chat.txt, summary.txt) in `E:/Programming/java/wt/_programme/runs/<server>--<scenario>/`, first-pass copies in
`E:/Programming/java/wt/_programme/cnc-0.16/t21/pass1/`.

## Verdict in one paragraph
Build and 2489 tests green, nine module jars present, boot clean on Keystone 1.15.0, clean refusal on Keystone 1.14.0.
Of the sixteen new rows, **fourteen PASS** (S1-S5, S7-S14, S16; S5 carries one REVIEW note), **S6 FAILS** (hand-off: radio line never
heard, biased units never "ahead", HANDOFF itself fired in two of three runs) and **S15 FAILS on one diagnostic line only**
(behaviour right, `WARD_BILL ... at=DEATH` debug line missing). Regression rows R1-R4, N1-N8, A1-A6 PASS or are REVIEW-by-design;
R2 is flaky on 0.15.2 as well (baseline run); A7-A9 produce no AUTO drop in their window on 0.15.2 either (scenario, not a regression).
Three bugs are filed below for the orchestrator.

## Build
| Command (PowerShell, in `E:/Programming/java/wt/cnc016-t21`) | Result |
|---|---|
| `mvn clean verify` | BUILD SUCCESS, 3:10 min, **2489 tests, 0 failures, 0 errors, 0 skipped** (Core 124, Item 48, Sign API 66, API 153, impl 450, Gangs 142, Mail 34, Civilians 91, Turf 117, Cops N Crooks 986, Gadgets 174, Health Bars 18, NPC Shops 26, Loot Chests 60). Log: `acceptance-evidence/mvn-verify.txt` |
| `mvn -q clean package -DskipTests` | exit 0, `target/gangland_warfare-0.16.0.jar` + nine `target/modules/*-0.16.0.jar` (cops-n-crooks, civilians, gadget, gang, healthbars, lootchest, mail, npc-shops, turf) |
No `mvn install` anywhere. The rows were staged from this lane's `target/` (prep script now honours `WT16`, see Tooling changes); the jar md5 in the server equals the lane jar.

## Smoke rows
| Row | Verdict | Evidence |
|---|---|---|
| `cnc-016-boot` | **Not run as specified** (`smoke.py --deploy` drives the live test server). Sandbox equivalent PASS | `cnc016-default-016--boot`: Keystone 1.15.0 + Bartizan 0.6.0 + Gangland 0.16.0, "Runtime modules: 9 loaded, 0 fault(s)", `Done (` once, no Gangland ERROR/exception (only Paper's "No key layers in MapLike" noise), `glw module list`, `glw wanted help` ran |
| `cnc-016-old-keystone` | **Not run as specified**. Sandbox equivalent PASS | `cnc016-oldks--boot` (Keystone 1.14.0 jar swapped in): `[Gangland.Gangland] Gangland 0.16.0 needs Keystone 1.15.0 or newer; found 1.14.0` then `Disabling Gangland_Warfare v0.16.0`, server keeps running (the later `glw` command exceptions are the two console commands hitting a disabled plugin) |
**Owner-approved steps left:** `python smoke.py --rows cnc-016-boot,cnc-016-old-keystone --deploy --keystone` against `E:/Documents/Minecraft/Test Server` (old-keystone needs `Keystone-1.14.0.jar` staged by hand and no `--keystone`, T18 ruling 7; `smoke/scenarios.json` in the main checkout still points at gangland-0.15.0 paths, repoint before running).

## S1-S16 (profile default-016; S14 profile s14). Two full passes (run-all runs 1 and 4); failing/flaky rows re-run further
Pass 1 = `acceptance-evidence/run-all-1.txt` (re-judged after the verdict fix, `rejudge-S.txt`), pass 2 = `run-all-4.txt` (+ S14/S15 re-run after the scenario fix). Evidence lines are from the final run dirs (`final-S.txt`).
| Row | Verdict | Pass 1 / Pass 2 | Evidence |
|---|---|---|---|
| S1 wand station + district | PASS | P / P | `list shows the station row - station 1 Northside Station world 100 -61 40`; `region 1 Docks world 80 -61 110 [district]` |
| S2 dispatch from station | PASS | P / P | `DISPATCH Runner count=2 station=Northside Station eta=10s hold=0s reason=none`; radio `2 units en route from Northside Station, ETA 10 s.`; first `UNIT ... fromStation=true hidden=true`, 10 s after DISPATCH |
| S3 mixed tiers | PASS | P / P | UNIT tiers 2 and 3 over 6 units; Pointman/Assault tier 2, Commander/Defender/Marksman tier 3 |
| S4 wipe and breather | PASS | P / P | `DISPATCH ... count=5 ... hold=10s reason=wipe`; radio `Squad down. Backup inbound in N s.`; first unit 22 s after the wipe (10 s hold + 10 s ETA) |
| S5 perimeter posts | PASS + REVIEW | R / R | `PERIMETER Runner start posts=2 radius=32.0` (ring = min(zone, 0.8 x 40)); two cops PURSUING -> POSTED, `PERIMETER ... end reason=OFF`. **REVIEW:** no `Post_Up` line reached the bot's chat in either pass (the unit posts without an audible line inside the 10 s window; see Manual checks) |
| S6 outrun hand-off | **FAIL** | F / F | Run 1 (pass 1): no `HANDOFF` line at all, all `UNIT ... bias=false`. Runs 2 and 3: `HANDOFF Runner heading=east`, `DISPATCH ... bias=true`, `UNIT ... bias=true` PASS, but `UNIT ... bias=true ahead=false` on every biased unit and **no `Lost him heading east...` radio line in chat** in any run. Details under Failures |
| S7 hideout faster | PASS | P / P | `EVASION Runner speed=2.00 zone=1.00 hideout=2.00 quiet=1.00`; every earlier EVASION line `hideout=1.00` |
| S8 quiet minute | PASS | P / P | radio `Units returning to patrol` 60 s after the crime; `EVASION Runner speed=1.25 zone=1.00 hideout=1.00 quiet=1.25` |
| S9 contact phone | PASS | P / P | `Your contact made 1 star(s) disappear for $1,000.`; balance 5000 -> 4000; stars 2 -> 1 |
| S10 sign refused | PASS | P / P | `Error: [GLW] Not while a cop has eyes on you.`; balance 5000 -> 5000; stars unchanged |
| S11 self-defence | PASS | P / P | two provoked kills, stars 0 before and after |
| S12 posted bounty | PASS | P / P | two posted bounties claimed, Hunter 0 stars, paid 0 -> 1000 |
| S13 unposted kill | PASS | P / P | two unposted five-star kills: Hunter 0 -> 1 star |
| S14 downed -> hospital | PASS | P / P (pass 2 first run lost one harness step to a scenario race, fixed, re-run P) | `HOSPITAL Runner waypoint=Infirmary`; `WARD_BILL Runner amount=750.00 at=RESPAWN`; `SHIELD Runner seconds=5`; wallet 5000 -> 4250; woke at 150.5,-60,260.5 |
| S15 vanilla death -> hospital | **FAIL (diagnostic line only)** | F / F / F | Behaviour correct: `HOSPITAL ... Infirmary`, `SHIELD ... seconds=5`, chat `Ward bill: -$750` once, wallet 5000 -> 4250, woke at the Infirmary, 0 items at the body. Missing: the contract's `WARD_BILL {player} amount={x} at=DEATH` debug line (0 lines). Details under Failures |
| S16 logout restore | PASS | P (hold read 14) / P | `DISPATCH ... reason=restore hold=14s` (Rejoin_Grace_Seconds 15, logged a moment after the stamp), `EVASION Runner hold=enroute`, first unit 24 s after DISPATCH, star still there |

## Regression rows on default profiles (0.16 jars)
Verdict script: `cnc-verdict.js`. Multi-phase rows ran two server runs each (R4, N2, N4, A4, A5, A6).
| Row | Verdict | Run | Evidence |
|---|---|---|---|
| R1 | PASS (REVIEW by design) | run-all-1 | squad members seen at 1/3/5 stars 2/4/6, grows with level. Compare with `Cops.Count`/`Squad_Composition` by hand: 3 stars is 5 listed roles, 4 seen because the dispatch queue delivers in batches (`count=4` first) |
| R2 | PASS (flaky, same as baseline) | 3 runs 0.16 + 2 runs 0.15.2 | 0.16: FAIL (run-all-1: last-seen spot not reached, beeline behind wall true), then PASS, PASS (`sample 3` / `sample 2`, fanned out true, re-acquired true). Baseline 0.15.2 jars: FAIL (`sample 2`, re-acquired false), PASS. Reference 0.15.0 acceptance: PASS. `acceptance-evidence/r2.txt` |
| R3 | PASS | run-all-1 | stars kept while cuffed, intake reached, stars cleared at intake |
| R4 | PASS | run-all-1 (2 runs) | `★★` before quit and after rejoin, decay line within 60 s |
| N1 | PASS | run-all-1 | decreased line in window, 1 star left, no death |
| N2 | PASS | run-all-2 (2 runs, default + legacy-settings) | balance 5000 -> 5000 both, star fell |
| N3 | PASS | run-all-1 | 5000 -> 3750 |
| N3b | PASS | run-all-1 | 5000 -> 3500, exactly one "Money formula" warning |
| N4 | PASS | run-all-2 (0.13.0 jars then 0.16.0 jars) | posted 500 collected once (Hunter 0 -> 500), second kill 500 -> 500 |
| N5 | PASS (read) | run-all-1 | `Gunfire, he's close! Move in!` after shot 1 |
| N6 | PASS (read) | run-all-1 | cleared -> one hit on a cop -> `wanted ... level 1` |
| N7 | PASS (read) | run-all-1 | `CHARGE SHEET`, `Paid from your wallet: $300`, `Unpaid $400 served as +40s` |
| N8 | PASS (read) | run-all-1 | `Two down!...` (Regroup) then `Backup's here! All units, push together!` (Regroup_Push) |

## A1-A9 (profile auto / auto-compat)
| Row | Verdict | Run | Evidence |
|---|---|---|---|
| A1 | PASS | run-all-1 (re-judged) | three HUNKER_DOWN drops 3->2->1->0, gaps 15 s and 6 s, first drop 34 s after hide |
| A2 | PASS | run-all-2 | `AUTO drop level=2->0 ending=PETTY` |
| A3 | PASS | run-all-2 | `level=4->3 ending=STILL_HOT reason=rampage` |
| A4 | PASS | run-all-3 (2 runs) | `level=2->1 ending=STILL_HOT reason=logout quits=1`; no squad re-formed after rejoin (REVIEW by design) |
| A5 | PASS | run-all-3 (2 runs, sqlite read) | `chase_habit` `n=1.9`, delta 0.02 on the second chase, 0.03 after restart |
| A6 | PASS | run-all-1 (2 runs) | `Done (` both times; first run no `config.` line; second exactly one `config.range` naming `Step_Speed` |
| A7 A8 A9 | REVIEW, not a 0.16 regression | run-all-3 + 0.15.2 baseline | In their 30-40 s window no `AUTO drop` line is logged on 0.16 **or on the 0.15.2 jars** (`cnc016-b152a--A7/A8/A9`, 0 lines each), so the scenarios do not reach a drop; they need a longer tail to be informative. Read by a human, nothing to compare against |

## Failures and reproductions
### S6 - hand-off radio and "ahead" bias (suspect T17 HandoffController, T11 spawn)
Repro: `bash acceptance/run-all-cnc016.sh S6` with `WT16=E:/Programming/java/wt/cnc016-t21/target` (profile default-016; station at 100,-60,40; Runner at 3 stars, hops +15 blocks/s east for 40 s).
- `HANDOFF` is not deterministic: run 1 logged no line (cops went PURSUING -> RETURNING normally, so the "RETURNING cop still targets him" test at `HandoffController.java:105-111` never held; T17's own ruling warned the target may already be cleared), runs 2 and 3 logged `HANDOFF Runner heading=east`.
- When it fires, `sayFromLeader(group, "Handoff", ...)` (`HandoffController.java:91`) is never heard: no `Lost him heading east` in chat in any run.
- Every biased unit logs `bias=true ahead=false` (`CopSpawnManager.java:220`): units spawn at x=397-470 behind a Runner who is by then past x=550, from the fallback spot (`hidden=false`), not from the hidden ring ahead of the bias (`CopSpawnManager.java:206`). Flat sandbox world, 14 s ETA at 15 blocks/s, so part of this is the scenario's geometry; the missing radio line and the one non-firing run are not.
- Likely cause of the silent radio line (read from the code, not proven by a run): the hand-off fires only when `leashedOut` finds a RETURNING cop more than `Pursuit.Max_Distance` (80, `cops.yml:130`) from the Runner, and `sayFromLeader` (`CopRadio.java:147-151`) speaks from the squad leader's own body with no target, so the line reaches players within `Radio.Range` (32, `cops.yml:195`; `Target_Range` is 64). When the leader is the cop that leashed out, the Runner is out of range every time.
- Release status: **open, not accepted**. T21 only reports (PLAN Task 21: "Do not fix code; report"). Docket-ready triage line for the orchestrator (proposed id CC-HANDOFF) is in `_programme/cnc-0.16/reports/T21.md`, "Fix round 3". Routing (T17/T11 fix or accepted known issue) is an orchestrator/owner call.
### S15 - missing `WARD_BILL ... at=DEATH` debug line (suspect T8)
Repro: `run-all-cnc016.sh S15`. Vanilla death with `Hospital.Enable` true and `Death.Respawn.Enable` false: `PlayerDeathListener.onPlayerDeath` calls `handleMoney` (`PlayerDeathListener.java:98-100`) which charges through `charge()` (`:182-184`, `:204`) and never logs; only `chargePending` (`:157`) logs `WARD_BILL`. CONTRACTS C13 says `at={DOWN|RESPAWN|QUIT|DEATH}`. Player-visible behaviour is correct (one bill, 750).

## Manual checks (the bot cannot see them)
- Boss bar (any 0.16 boss bar or timer display), the wand's particle outline (logic: T12 `SetupOutlineTest`), the [WANTED] sign render, the phone's `phone_contacts` page layout, bribe-star item hover/stand rendering: look at them once on a real client.
- S5 `Post_Up` line: stand within radio range of a posted marksman for about 20 s and confirm the line shows.
- S6 hand-off: outrun a squad on foot (not by teleport hops) in a town with streets and confirm `Lost him heading ...` and units arriving from ahead.
- The two smoke rows on the live test server (above).

## Rulings
- Ruling: the tooling was fixed in place instead of reporting every false FAIL - five harness faults (below) produced FAIL rows that were not product behaviour; each was reproduced, fixed in the acceptance tooling only (no product code) and the affected rows re-run - cost if wrong: a fix hides a real problem; every fix is listed and the evidence of the unfixed first pass is kept (`pass1/`, `run-all-1.txt`).
- Ruling: `mvn clean verify` and `package` ran in this lane worktree, not in `gangland-0.16.0` - the task forbids touching integration worktrees; the code is the same commit - cost: none.
- Ruling: the baseline comparisons (R2, A7-A9) used the 0.15.2 jars with Keystone 1.14.0 on a copy of the same profile - cost: R2 is timing-sensitive, two baseline runs is a sample not a proof.
- Ruling: S14/S15 wake position and the other REVIEW-threshold rows were graded PASS where the numbers are inside the verdict script's stated tolerance - cost: none beyond the script's own thresholds.

## Tooling changes (committed in the lane; all under `acceptance/`)
1. `cnc016-verdict.js` and `cnc-verdict.js` `markAt` matched the `>>> STEP n {"console":"say CNCMARK:..."}` echo first (no timestamp, `NaN`), which broke S4 ("a refill unit spawned" FAIL), S7, S8 and A1 timing checks. Now only timestamped log lines count.
2. `cnc016-verdict.js` S16 accepts `hold=14|15` (the DISPATCH line is logged just after the grace stamp, floor-rounded).
3. `gen-cnc015.js`: the A2/A3/A4/A5 chat steps (`WANT`, `STATUS`) took the last joined bot, which is Target after `TARGET`, so Target got the stars and was cuffed instead of Runner; they now pass `bot: 'Runner'`, and A4a's final `quit` names Runner (Target had already quit).
4. `gen-cnc016.js` S14/S15: `expectLog SHIELD Runner` ran after the HOSPITAL step had already consumed the window (a race, passed or failed by timing); removed, the verdict reads the log.
5. `prep-cnc016.sh`: the final `grep ... Hospital:` aborted `set -e` for 0.13.0-based profiles (legacy-settings, n4-old); `|| true`. `WT16` is now overridable from the environment. `run-all-cnc016.sh`: the A5 sqlite path was `Gangland_Warfare/*.db`, the file is `Gangland_Warfare/database/gangland.db`.
