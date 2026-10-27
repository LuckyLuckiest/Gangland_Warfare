# cop-ai live reproduction environments (Gangland 0.21.0, pre-fix)

Sandbox only. Clones of `E:/Programming/java/wt/_programme/servers/base`; the owner's live Test Server is never touched.

## Jars (prep.sh)
- Gangland core + 10 modules: `E:/Programming/java/wt/cnc-0.21.0/target/` (worktree HEAD ea59ffaee, tree-equal to master
  a14bcfbcb for gangland-features, gangland-impl, gangland-api, gangland-core, gangland-infra, gangland-ui, pom.xml; jars
  built 2026-10-24 15:51, md5 identical to `_programme/cnc-tdd/deploy-0.21.0/deployed.md5` = what the live server runs).
- Keystone 1.15.3: `E:/Programming/java/wt/keystone-1.15.3/keystone-plugin/target/Keystone-1.15.3.jar` (4945a1788), md5 = live.
- Bartizan 0.6.0: `E:/Programming/java/wt/bartizan-0.6.0/bartizan-plugin/target/Bartizan-0.6.0.jar`, as the newest
  cnc-integ prep (cnc21f2 acc-r3 `prep-r3.sh`) used. The live server runs Bartizan 0.6.1: the one deliberate divergence.
- Everything else (Paper 1.21.11, Citizens 2.0.43, ViaVersion, PlaceholderAPI, ...) from `servers/base`.

## Servers
| key | server | port |
|---|---|---|
| spawn-dup | `servers/cop-ai-spawn-dup` | 25611 |
| radio-far | `servers/cop-ai-radio-far` | 25612 |
| brandish | `servers/cop-ai-brandish` | 25613 |

## Config overrides (all others are shipped defaults; no cops.yml / wanted.yml / cop_roles.yml edits)
- `server.properties`: `level-type=flat`, `generate-structures=false` (every CnC acceptance prep);
  `view-distance=16` (the owner's live value; base has 6, which would unload the chunks stranded walkers stand in and
  release them, masking spawn-dup cause 1). `simulation-distance=10` unchanged (= live).
- `settings.yml`: `Debug.Enabled: true`, `Debug.Modules: ["Cops N Crooks", "Gangland"]`; `User.Death.Respawn.Enable: true`,
  `Delay: 3`.
- Scenario-side world rules: `spawn_mobs false`, `spawn_monsters false`, `advance_time false`, `time set 6000`, clear weather;
  bots get Resistance V + Saturation (no deaths during a chase).

## Files
- `clone.sh <name> <port>`, `prep.sh <name> <port>`: copies of cnc21f2 acc-r3 `laneclone-r3.sh` / `prep-r3.sh` (refuse anything
  outside `servers/cop-ai-*`).
- `mk-runner.js` -> `run-copai.js`: additive copy of `harness/run-cnc020.js` (same steps). chat.txt lines carry the local
  wall clock `[HH:MM:SS.mmm]` (server.log uses the same clock) and boss bars / titles are recorded (`[bossbar+]`,
  `[bossbar]`, `[bossbar-]`, `/title`): the wanted HUD's evasion countdown is a boss bar.
- `gen-scen.js` -> `scen/*.json` (boot, dry, spawn-dup, radio-far, brandish). Scenarios are recorders: hard expects only on
  setup preconditions, so rc=1 means setup broke, never "bug present".
- `run.sh <key> [scenario] [tag]`: fresh clone + prep + boot + scenario + stop. Output `_programme/runs/cop-ai-<key>--<tag>/`
  (server.log with `>>> STEP n` markers, chat.txt, summary.txt, steps.json).

## Reading a run
- `say CNCMARK:<TAG>:<label>` lines are in both server.log (timestamped) and chat.txt (`[Server] CNCMARK:...`).
- Probe after each marker: `glw cop list Runner` (CopGroup members, stranded RETURNING walkers included), one
  `<npc name> has the following entity data: [x, y, z]` line per Citizens NPC (all cops and beat cops in the world), the
  bot's own `Pos`.
- Debug lines: `DISPATCH`, `UNIT ... at=`, `Transitioning cop X from A state to B state`, `HANDOFF`, `STANDOFF`, `EVASION`,
  `PERIMETER`, `SIGHT`, `WARNING <p> result=`, `SUSPECT`, `PATROL station=`.
- Chat: `[DISPATCH] ... units en route to Runner.` (Dispatch_En_Route), `[RADIO] <callsign>: <line>`, `Beat cop: Put that away!`,
  `Thanks, carry on.`, `/glw wanted` replies.

## Env checks done (2026-10-26, all on the jars above)
- `run.sh <key> boot` x3: Paper 1.21.11 `Done (`, `Runtime modules: 10 loaded, 0 fault(s)`, all 10 modules enabled, Keystone
  1.15.3 / Gangland_Warfare 0.21.0 / Bartizan 0.6.0 / Citizens 2.0.43, 0 exceptions. Only ERROR = Paper's
  `No key layers in MapLike[{}]` (3x at world load, base-server noise, also in earlier acceptance runs).
- `run.sh brandish dry`: bot joins via ViaVersion (client 26.1), probes print NPC positions (`Nick #1000 has the following
  entity data: [...]`), the pull-next-to-beat-cop command works. Citizens' console `npc list` prints only its header, so
  it is not used.
- One full pass of each scenario (`_programme/runs/cop-ai-<key>--envcheck2`, brandish `--envcheck3`), rc=0 all three:
  spawn-dup squad (`glw cop list`) peaked at 6 against a level-3 target of 4, 16 Dispatch_En_Route lines, 7 HANDOFF;
  radio-far HANDOFF 0.6 s after the >80 hop, STANDOFF trigger=UNREACHABLE while sealed, flank lines at 3 stars sealed;
  brandish 3 ignored warnings 30-31 s apart, star 1 at the 3rd, silent IGNORED (no chat line), variant B complied then
  re-warned 37 s later. These are previews for the judge, not verdicts.
- Known run-to-run variance: cops cuff a bot that stands still in the open (spawn-dup's aftermath was cut to ~16 s and
  radio-far's open-far phase to 8 s for that reason; brandish's Runner is cuffed after star 1, which is in-scope behaviour).
  A `CUFFING`/`GUARDING` debug line or the `Break free` actionbar marks it.
- Boot time 16-30 s per fresh clone; one boot took 110 s while another process was scanning the whole wt tree (disk
  contention). run-copai.js's boot timeout is 600 s.

## 0.21.1 acceptance (2026-10-26, owner rulings R1-R6)
- **Run dirs are never overwritten any more**: `run.sh` (and `run-acc.sh`) refuse a tag whose
  `_programme/runs/cop-ai-<key>--<tag>` exists (exit 4). Pick a new tag per run/round (`acc2-...`, `acc3-...`).
- `run-acc.sh <key> <scenario> <tag>`: run.sh with the FIXED build (env `GL`/`REV`/`KS` read by `prep.sh`, whose
  defaults stay the 0.21.0 repro build): Gangland `wt/gangland-0.21.1/target` (branch 0.21.1 @ 440d73c87, core + 10
  modules), Keystone `wt/keystone-1.15.4` (08c8453) 1.15.4, Bartizan 0.6.0, Citizens/base unchanged. Writes
  `<out>/deployed.md5`. `prep.sh` regenerates `copsncrooks/*.yml` from the module jar, so the shipped 0.21.1 defaults
  apply (`Brandish_Near_Cop: 67`, gain `Subtitle: "%stars%"`); no migration step is needed on these sandboxes.
- `gen-acc.js` -> `scen/acc-*.json`: `acc-spawn-dup`, `acc-spawn-dup-b`, `acc-radio-route` (repro scenarios + a
  `data get entity <bot> Rotation` after each Pos probe), `acc-spawn-view` (R6: corridor hops with explicit yaw, LA =
  looks where he runs, LB = looks back, SV-S/SV-N = facing toward/away from the station spawner), `acc-radio-far`
  (radio-far + a ~10-block walk west while seen before the hop south: R5 discriminator), `acc-brandish` (Drawer
  complied, Oov R2 Lost_Sight verdict then pulls from verdict+20 s, Nighter/Sneaker shrink regression, Igner R1).
- `judge-acc.py <spawn|radio|brandish> <run dir>` (python -I): PASS/FAIL per ruling; exit 1 on any FAIL. Against the
  0.21.0 repro runs it FAILs R3/R/2/5 (`cop-ai-spawn-dup--r1`), R4 (`cop-ai-radio-far--r1-orig`, `--r1`) and R1/R2
  (`cop-ai-brandish--r1`, `--r2b`), so its checks are red on the unfixed build.
- Boot checks `cop-ai-<key>--acc1-boot` (x3): Keystone 1.15.4, Gangland_Warfare 0.21.1, Bartizan 0.6.0, Citizens
  2.0.43, `Runtime modules: 10 loaded, 0 fault(s)`, 0 exceptions, only ERROR = `No key layers in MapLike[{}]`.
- Env checks (previews on the fixed jars, rc=0, all steps ok, 0 exceptions; judge OVERALL PASS):
  `cop-ai-spawn-dup--acc1-envcheck-spawn-view` (38 UNIT spawns, 0 in view, 0 past 61 b; no stranded walker listed >=5 s;
  8 en-route lines >=30 s apart; SV-S spawner 0/3, SV-N 3/3), `cop-ai-radio-far--acc1-envcheck-radio-far` (sealed 141 s:
  0 Flank/Push/Engage/Overwatch/Standoff/"to him", 4 Route_Search; Handoff "heading west" +1.3 s after the hop south),
  `cop-ai-brandish--acc1-envcheck-brandish` (every 1st ignore: cause line + `[subtitle] ★☆☆☆☆` + card + DISPATCH;
  Oov Lost_Sight, next warning 31 s after the verdict; Igner 31 s; shrink kept). Not previewed: acc-spawn-dup,
  acc-spawn-dup-b, acc-radio-route (repro scenarios + one Rotation probe per Pos probe).
- Two scenarios of the same key share one server dir and port: run them one after the other (keys in parallel are fine).

## ACC7 (2026-10-27): merged 0.21.1 build, handoff-lead, two new judge checks
- Jars: Gangland `wt/gangland-0.21.1/target` (branch 0.21.1 @ cdd447d7d, civ merged at f52cda0cd; jars built 16:08-16:09,
  no source newer than the core jar), Keystone 1.15.4. `run-civ.sh` now deploys the same target (env `GL` overrides).
  The run list with pass criteria per check is `ACC7.md`.
- New key `handoff-lead` in `run.sh`: server `servers/cop-ai-handoff-lead`, port 25615 (`run-acc.sh handoff-lead ...`).
- `gen-handoff.js` -> `scen/handoff-lead.json` (R5 lead, 248 steps): 2 stars, ring spawns; contact at (0,0) facing
  west, 2-block hops west every 0.5 s to x=-20 (yaw on every tp), then tp into a sealed stone box at (0,200) yaw 180
  (due south of the last-known spot, off the heading line, every cop > 80 b), 28 probes ~1 s apart, then out at
  (0,185) facing west, `/glw wanted clear` there, WATCH (west) and AWAY (south). The last step expects
  `HANDOFF_LEAD Runner` since the row start: rc=1 with only that step failed = the hand-off never seeded a lead (a
  judge FAIL), not setup.
- `judge-acc.py handoff <run>`: HANDOFF_LEAD logged, heading west, the Handoff line heard, biased units' 15 s tracks go
  toward the logged lead, none away from it or toward the box (ACC7.md lists the four classes), R6 cone on every
  open-ground UNIT (the spawner's own test is cone AND clear line, so the boxed phase is skipped).
- New checks on every kind: `search-clear` (from each `/glw wanted clear` + 1 s to his next `wanted add`, new crime
  or quit: no `HANDOFF`/`HANDOFF_LEAD` line, no order-pool radio line); on spawn/radio/handoff `R3-in-view` (an NPC
  that no later probe lists, last state RETURNING, his pose at both probes around the removal < 64 b and < 70 deg:
  FAIL; sealed phases skipped; the code's own cone is 60 deg). Red on old runs: `cop-ai-radio-far--acc6-radio-far`
  fails search-clear (HANDOFF heading=south 4 s after the clear).
- Boots `cop-ai-<key>--acc7-boot` for spawn-dup, radio-far, brandish, civ (civ-boot) and handoff-lead: all clean.
  Previews `cop-ai-handoff-lead--hl-env1..3` (env3 = final layout): HANDOFF_LEAD at=-44,-60,0 from=-20,-60,0 heading
  west (lead 24 b), line heard; R5-lead-go FAIL in all three (biased units spawn around the boxed live spot, Keystone
  `route walk failed` toward the lead: docket T-423, open). R6, R3-in-view and search-clear PASS. Details: ACC7.md.
