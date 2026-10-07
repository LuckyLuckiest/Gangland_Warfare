# Cops N Crooks 0.15 "Lose them" - acceptance harness (authored by Task 10, run by Task 18)

Everything here is untracked and lives in the main checkout. Absolute root of this folder:
`E:/Programming/java/Gangland Warfare [Cubed-GTA recoded]/brainstorming/cnc-overhaul-2026-10-05/acceptance/` (ACC below).

## Paths (all absolute)
| What | Path |
|---|---|
| Scenario JSONs (generated, harness step format) | `ACC/scenarios/*.json`; regenerate with `node ACC/gen-cnc015.js` |
| Sandbox prep (clone + stage jars + per-row settings) | `ACC/prep-cnc015.sh <profile> [name] [port]` |
| Run one scenario on a prepared server | `ACC/run-row.sh <server-name> <scenario.json> [client-version]` |
| Run + judge everything unattended | `ACC/run-all-cnc015.sh [rows...]` |
| Verdict per row | `node ACC/cnc-verdict.js <row> <run-dir> [<run-dir-2>]` |
| YAML key setter used by the prep script | `node ACC/yset.js <file.yml> <A.B.C> <value>` |
| Mineflayer harness (own copy, outside the temp dir) | `E:/Programming/java/wt/_programme/harness/` |
| Sandbox servers / run outputs | `E:/Programming/java/wt/_programme/servers/<name>/`, `.../runs/<server>--<scenario>/` |
| Jars staged | Gangland `E:/Programming/java/wt/gangland-0.15.0/target/gangland_warfare-0.15.0.jar` + `target/modules/*-0.15.0.jar` (nine), Keystone `E:/Programming/java/wt/keystone-1.14.0/keystone-plugin/target/Keystone-1.14.0.jar`, Bartizan `E:/Programming/java/wt/bartizan-0.6.1/bartizan-plugin/target/Bartizan-0.6.1.jar` (0.6.1 is api-compatible with the 0.6.0 pin). N4 phase A and N2's legacy settings use `E:/Programming/java/wt/gangland-0.13.0/target/`. |

Harness copy: source `C:\Users\Hashim\AppData\Local\Temp\claude\E--Programming-java-Keystone\testserver-work\harness\`, copied 2026-10-05
to `E:/Programming/java/wt/_programme/harness/` (6,852 files: 331 scripts/scenarios/package files + `node_modules`, 466 MB). Harness docs:
its own `README.md`. First use builds `E:/Programming/java/wt/_programme/servers/base` with `make-base.sh` from the live server
`E:/Documents/Minecraft/Test Server` (read-only source; the live server is never touched or started).

## Before you run (Task 18)
1. The integration worktree `E:/Programming/java/wt/gangland-0.15.0` must be built: `mvn clean package` (PowerShell, not Bash) so
   `target/gangland_warfare-0.15.0.jar` and `target/modules/*-0.15.0.jar` (nine modules incl. `gangland-healthbars`) exist.
2. `node` and `python` on PATH; Git Bash for the `.sh` files; JDK 26 at the harness default (`--java` flag otherwise, see harness README).
3. Run from anywhere: `bash "E:/Programming/java/Gangland Warfare [Cubed-GTA recoded]/brainstorming/cnc-overhaul-2026-10-05/acceptance/run-all-cnc015.sh"`
   (about 45-60 min for all rows; each server boots, plays one scenario, stops). Rows may be named: `run-all-cnc015.sh R1 N1 N3`.
4. Boot proof first (no bot): in `E:/Programming/java/Gangland Warfare [Cubed-GTA recoded]/brainstorming/bartizan-split-2026-09-08/smoke/`
   run `python smoke.py --rows cnc-015-boot --deploy --keystone` (row `cnc-015-boot`: nine modules + Bartizan, no ERROR, `Done (`).
   `--dry-run --rows cnc-015-boot` only prints the plan. Note this smoke harness drives `E:/Documents/Minecraft/Test Server` (the live
   test server, its `ServerStartDebug.bat`), unlike the sandbox harness above; do that with the owner's say-so.

## Profiles (prep-cnc015.sh)
`default` shipped 0.15 defaults - `legacy-settings` the 0.13.0 `settings.yml` (no `Take_Money.Enable`) on 0.15 jars - `r3`/`r4`
`Repeating_Timer.Time: 10` (r4 also `Evasion.Enable: false` in `copsncrooks/wanted.yml`, extracted from the cops-n-crooks jar) - `n3`
`Take_Money.Enable: true` - `n3-broken` plus `Formula: "amount * ("` - `n4-old` Gangland 0.13.0 jars - `n4-new` 0.15.0 jars swapped
onto the n4-old data. Every profile: flat fresh world, Keystone 1.14.0, Bartizan 0.6.1, live Citizens `config.yml`, Debug on for
"Cops N Crooks". If a 0.15 key name differs from the one the prep script sets, `yset.js` inserts the key; check the printed `grep` of
the `Wanted:` block in `runs/prep-<profile>.log`.

## Rows (pass lines; the verdict prints PASS / FAIL / REVIEW per check)
Pass criterion for 0.15: **R1, R2, R3, R4, N1, N2, N3, N4 PASS** (N5-N8 best effort, M manual).

| Row | Scenario file(s) | Profile | Pass line | Verdict |
|---|---|---|---|---|
| R1 reg-ladder-1-3-5 | `R1-reg-ladder-1-3-5.json` | default | `/glw wanted add 1\|3\|5` -> squad sizes from `Cops.Count` (settings.yml) and the `Squad_Composition` roles (cop_roles.yml), no ERROR. The scenario counts cop NPCs near Runner (`CNCCOP:R1-<n>` markers); the verdict checks counts > 0 and non-decreasing, and leaves the exact comparison with the config to a human (REVIEW). | `cnc-verdict.js R1` |
| R2 reg-los-break | `R2-reg-los-break.json` (= H11 `scen-h11-losbreak.json`) | default | the H11 line-of-sight break still loses the cops' contact | `h11-verdict.js` "Line of sight broken" row (the run-all script copies the run to `runs/_r2/h11-losbreak-1` and sets `H11_RUNS`) |
| R3 reg-cuffed | `R3-reg-cuffed.json` | r3 | cuffed at 2 stars, stars kept 0/15/30 s into the cuff (a 10 s decay timer would show), cleared at intake, no star drop while cuffed. Cuff/intake chat wording is matched loosely (`cuff\|detain\|handcuff\|arrest`, `jail\|booked\|intake\|...`): adjust in `gen-cnc015.js` if the lines differ. 1 star behaves the same; re-run with `/glw wanted add 1` by editing the scenario. | `cnc-verdict.js R3` |
| R4 reg-logout | `R4a-reg-logout-quit.json` then `R4b-reg-logout-rejoin.json` (same server, no re-prep) | r4 | 2 stars, quit, rejoin -> still wanted and the decay clock runs (`decreased` within 60 s at `Time: 10`, evasion off) | `cnc-verdict.js R4 <R4a run> <R4b run>` |
| N1 evasion-drop | `N1-evasion-drop.json` | default | 2 stars, Runner seals himself in a stone room (`fill 96 -61 126 104 -56 134 stone hollow`, tp inside), within 3 + 20 + 5 s the `decreased` chat line, 1 star left, no death | `cnc-verdict.js N1` |
| N2 no-money-on-drop | `N2-no-money-on-drop.json`, run on `default` AND `legacy-settings` | both | balance 5000 unchanged after a drop (shipped config and a 0.13.0 settings.yml without `Enable`) | `cnc-verdict.js N2 <default run> <legacy run>` |
| N3 charge-switched-on | `N3-charge-switched-on.json` | n3 | `Enable: true`: a drop at 2 stars takes 1,250 (5000 -> 3750) | `cnc-verdict.js N3` |
| N3b charge-broken-formula | `N3b-charge-broken-formula.json` | n3-broken | broken `Formula`: exactly one "Money formula" warning (server.log) over two drops, fallback charged 1250 + 250 (5000 -> 3500), both stars still fall | `cnc-verdict.js N3b` |
| N4 bounty-upgrade | `N4a-bounty-post-on-0.13.0.json` (profile n4-old), then `prep-cnc015.sh n4-new`, then `N4b-bounty-kill-on-0.15.0.json` on the same server `cnc015-n4-old` | n4-old, n4-new | 500 posted on 0.13.0; after the 0.15.0 deploy Hunter's kill collects the 500 once, a second kill collects no posted money. Kills are `damage Target 1000 minecraft:player_attack by Hunter` from the console; extra kill rewards may show, so the first-kill check is REVIEW unless the delta is exactly 500. | `cnc-verdict.js N4 <A run> <B run>` |
| N5 shots-fired | `N5-shots-fired.json` | default | Bartizan `pistol`, two shots near a cop -> a Shots_Fired radio line (best effort) | `cnc-verdict.js N5` (transcript, REVIEW) |
| N6 assault-cop-star | `N6-assault-cop-star.json` | default | at 0 stars one hit on a cop -> 1 star | `cnc-verdict.js N6` (REVIEW) |
| N7 charge-sheet | `N7-charge-sheet.json` | default | $300 at 2 stars, cuffed and booked -> sheet 700, paid 300, +40 s | `cnc-verdict.js N7` (REVIEW) |
| N8 regroup | `N8-regroup.json` | default | two cop kills inside 20 s -> Regroup then Regroup_Push radio lines (regexes `regroup`, `push\|move in\|go go` are placeholders: set them from `npc/cop_radio_messages.yml` once Task 5/9 wording is final) | `cnc-verdict.js N8` (REVIEW) |

Verdict exit codes: 0 all PASS, 1 a FAIL, 2 REVIEW only. A row is not green until its run dir (`runs/<server>--<scenario>/`:
`server.log`, `chat.txt`, `summary.txt`) has been read for the REVIEW lines. Each scenario drops `say CNCMARK:<tag>` / `CNCCOP:<tag>`
markers so `chat.txt` reads as a timeline.

## 0.15.2 AUTO rows (profiles `auto`, `auto-compat`; jars from `E:/Programming/java/wt/gangland-0.15.2/target/`)
`prep-cnc015.sh auto` = `Wanted.Evasion.Drop_Mode AUTO` + `Auto.Learning.Min_Chase_Seconds 5` + `Min_Seconds_Between_Outcomes 5` on the
shipped `copsncrooks/wanted.yml`; every other number stays shipped, so the 180 s lock and cold-trail line cannot fire inside a row.
`auto-compat` = the 0.15.1 `wanted.yml` (`git show 970dfa96:...`, no `Auto` block). Verdicts read the `AUTO drop ... ending=` debug line
in `server.log` (Debug on for "Cops N Crooks"). Run: `run-all-cnc015.sh A1 A2 ...` (also part of the default list, after N8).

| Row | Scenario(s) | Pass line | Verdict |
|---|---|---|---|
| A1 auto-hunker | `A1-auto-hunker.json` | 3 stars sealed: three single drops, `ending=HUNKER_DOWN` x3, gaps about 15 / 6 s after a first drop about 30 s into the search | `cnc-verdict.js A1` |
| A2 auto-petty | `A2-auto-petty.json` | one kill (80 heat, no star) + `wanted add 2`, sealed: one drop 2 -> 0, `ending=PETTY` | `A2` |
| A3 auto-rampage-lock | `A3-auto-rampage-lock.json` | `wanted add 4` + a kill: first drop 4 -> 3, `STILL_HOT reason=rampage` | `A3` |
| A4 auto-logout-lock | `A4a-auto-logout-quit.json` then `A4b-auto-logout-rejoin.json` (same server) | first drop 2 -> 1, `reason=logout`; REVIEW when no squad re-forms after the rejoin | `A4 <A4a run> <A4b run>` |
| A5 auto-learn-persist | `A5a-auto-learn-two-chases.json`, sqlite read of `chase_habit` (run-all writes `chase_habit.txt` into the A5a run dir), `A5b-auto-learn-after-restart.json` | `n` = 1.9 for Runner after A5a; A5b's debug line shows `delta>0` | `A5 <A5a run> <A5b run>` |
| A6 auto-config-compat | `A6-auto-config-compat.json` twice (second after `yset.js ... Auto.Momentum.Step_Speed 1.5`) | `Done (` both times; no `config.` line the first; exactly one `config.range` naming `Step_Speed` the second | `A6 <first run> <second run>` |
| A7 / A8 / A9 (REVIEW) | `A7-auto-clean-break.json` (15 blocks/s hops), `A8-auto-flee-on-foot.json` (5 blocks/s), `A9-auto-teleport.json` (one 150-block tp) | A7 `CLEAN_BREAK` 3 -> 1; A8 `HUNKER_DOWN` with `outside` about 0.2-0.3; A9 `teleported=true`, no `CLEAN_BREAK` (a CLEAN_BREAK is a FAIL) | `A7`, `A8`, `A9` |

Release pass: A1-A3, A5, A6 PASS, A4 PASS or REVIEW, A7-A9 read, R1-R4 and N1-N8 on the default profile unchanged. Manual checklist gains: a
petty card, a still-hot card, a clean-break card, the `Evaded_Many` flash (bot cannot read titles or boss bars).

## M manual checklist (the bot cannot see boss bars, titles, particles or the compass)
Run on a real client against a prepared sandbox (`prep-cnc015.sh default`, start it, join, `/glw wanted add 3`):
- [ ] Boss bar: red while cops can see you; flashing yellow with a countdown while evading; green once lost.
- [ ] Title + siren when the evasion clock starts / when you escape.
- [ ] Zone ring: particle ring at the last-seen spot, radius per star (40/60/90/130/180), re-centred after a gunshot.
- [ ] Compass needle points at the last-seen spot / nearest pursuer while wanted; resets when stars clear.
- [ ] Action bar untouched (Gang & Turf owns it).

## Known limits
Boss bars, titles and compass state are not capturable by the bot (harness README "Known limits"). Radio/handcuff/jail wording is
matched with loose regexes because the wording lands in other lanes; R3, N5, N7, N8 are the rows most likely to need a regex tweak in
`gen-cnc015.js` (then `node gen-cnc015.js`). `damage ... by Runner` needs Minecraft 1.20.4+ (the sandbox is Paper 1.21.11).
