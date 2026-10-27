# ACC7: 0.21.1 acceptance round 7 (2026-10-27)

## Build under test

| What | Where | md5 |
|---|---|---|
| Gangland core | `E:/Programming/java/wt/gangland-0.21.1/target/gangland_warfare-0.21.1.jar` (branch 0.21.1 @ cdd447d7d, civ merged at f52cda0cd) | `12d239fb4a707373ab54e5cc06a792ff` |
| cops-n-crooks module | `.../target/modules/cops-n-crooks-0.21.1.jar` | `8445cb05ee45ab99577659155dfeb1a7` |
| gangland-civilians module | `.../target/modules/gangland-civilians-0.21.1.jar` | `ab4cda9567ef6e8666b28fa28ab4658f` |
| Keystone 1.15.4 | `E:/Programming/java/wt/keystone-1.15.4/keystone-plugin/target/Keystone-1.15.4.jar` | `b00235037c270c02afe3e2ca764c7e5f` |

- The jars were built at 16:08-16:09. No `.java`, `.yml`, `.json` or `pom.xml` file is newer than the core jar.
- Bartizan 0.6.0, Citizens 2.0.43 and Paper 1.21.11 are as in every cop-ai row.
- Every run writes `<run>/deployed.md5`. Check it against this table.

## Re-prep and boot check (done 2026-10-27, all PASS)

`run-acc.sh` and `run-civ.sh` make a fresh clone and run `prep.sh` on every run, so each server below was re-prepped
with these jars.

| Server dir (`E:/Programming/java/wt/_programme/servers/`) | Port | Boot run (`E:/Programming/java/wt/_programme/runs/`) |
|---|---|---|
| `cop-ai-spawn-dup` | 25611 | `cop-ai-spawn-dup--acc7-boot` |
| `cop-ai-radio-far` | 25612 | `cop-ai-radio-far--acc7-boot` |
| `cop-ai-brandish` | 25613 | `cop-ai-brandish--acc7-boot` |
| `cop-ai-civ` | 25614 | `cop-ai-civ--acc7-boot` (`civ-boot`, 71/71 steps) |
| `cop-ai-handoff-lead` (new) | 25615 | `cop-ai-handoff-lead--acc7-boot` |

Every boot showed all of the following:
- Keystone 1.15.4, Gangland_Warfare 0.21.1, Bartizan 0.6.0 and Citizens 2.0.43-SNAPSHOT;
- `Runtime modules: 10 loaded, 0 fault(s)`;
- `EXCEPTIONS (0 distinct)`;
- the only ERROR is `No key layers in MapLike[{}]` (3x).

The deployed md5s equal the table above.

The civ boot passes all four civ-README boot items:
- `CivilianService initialized` at DEBUG;
- P_F and P_B keep their position and their yaw (180.0 / 0.0) at both probes;
- `ammo-left` goes from 12 to 11.

The boot times were 300-395 s because three servers booted in parallel. One at a time they take about 20 s.

## How to run

Run everything from `E:/Programming/java/Gangland Warfare [Cubed-GTA recoded]/brainstorming/cnc-cop-ai-2026-10-26/live`
in Git Bash.

- **Output:** `E:/Programming/java/wt/_programme/runs/cop-ai-<server key>--acc7-<key>/`. It holds `server.log`,
  `chat.txt`, `summary.txt`, `steps.json` and `deployed.md5`.
- **Tags:** a tag that already exists is refused (exit 4). For a rerun, use `acc7-<key>-r2`.
- **Same server:** rows that share a server dir share its port, so run them one after the other. Different server dirs
  can run in parallel. Run at most 3 at once: three parallel boots took 5-6 minutes each.
- **Judge:** `python -I judge-acc.py <kind> <run dir>`. It prints PASS, FAIL, INCONCLUSIVE or INFO for each check. It
  exits 1 on any FAIL.
- **What rc means:** `rc=1` from a run means a setup expect failed. It is not a behaviour verdict. Read
  `summary.txt` `FAILED STEPS`. The one exception is handoff-lead's last step.

These checks apply to every row:
- `Runtime modules: 10 loaded, 0 fault(s)`;
- `EXCEPTIONS (0 distinct)`;
- the only ERROR is the MapLike noise;
- `deployed.md5` equals the build table.

### New judge checks in this round

The judge runs these on every kind they apply to.

**`search-clear`** (all kinds):
- The window runs from each `/glw wanted clear` step plus 1 s to the bot's next `/glw wanted add`. It ends early at a
  new crime of his (`WARNING <bot> result=ignored`, or a `[subtitle]` with ★), at his quit, or at the end of the log.
- Inside the window there must be 0 `HANDOFF <bot>` and 0 `HANDOFF_LEAD <bot>` debug lines.
- Inside the window there must be 0 radio lines from an order pool:
  - Engage, Push, Flank_Left, Flank_Right, Reposition, Search, Route, Route_High, Route_Search, Climb;
  - Covering_Fire, Overwatch_Set, Shield_Up, Flanking, Flanking_Undirected;
  - Commander_Orders, Commander_Orders_Undirected, Commander_Orders_Basic;
  - Regroup, Regroup_Push, Post_Up, Handoff, Standoff, Negotiate, Stack_Up, Breach, Flashbang;
  - Backup, Dispatch_En_Route, Wipe_Refill, Escalate;
  - Contact, Eyes_On, Marksman_Spotted, Overwatch_Spotted;
  - any line containing `Lost him heading`.
- Stand_Down, Returning_To_Patrol, Suspect_Description and other lines are printed as INFO only.
- With no clear step the check is INCONCLUSIVE. That is expected for spawn-dup, radio-route and brandish-orig.

**`R3-in-view`** (spawn, radio and handoff):
- A vanish is an NPC listed in probe i's positions that no later probe lists. If its state at that point was
  `RETURNING`, the judge checks the bot's pose where probe i closes and where probe i+1 closes.
- FAIL if the walker was in view at both poses: less than 64 blocks from the bot and less than 70 degrees off his look.
- In view at only one of the two poses is reported as INFO (`-amb`).
- Line of sight is not traced. The world is flat and open, and the sealed phases are skipped: spawn `PP-SEALED`,
  `PP-DROP` and `PP-WANTED1`; radio `GO-ROOM`, `ROOM*`, `CLEAR`, `WANTED3` and `END`; handoff `GO-BOX` and `BOX-*`.
- The code's own view test (`CopSightlines.inView`) uses 60 degrees. A FAIL at 60-70 degrees therefore means the code
  is narrower than the ruling's FOV.

Both checks were sanity-run on older runs, which predate the r5 commits:
- `cop-ai-radio-far--acc6-radio-far` FAILs `search-clear`: `HANDOFF Runner heading=south` came 4 s after the clear.
- `cop-ai-spawn-dup--acc6-spawn-dup-b` FAILs `R3-in-view` with 4 walkers that vanished at 33-49 b, 6-26 degrees off
  his look, in phase P2. For example, Pete#1003 was at (-11.5,36) while Runner stood at (-27.5,0.5) with yaw 0.
- `acc2-spawn-view-r4`, `acc6-spawn-dup` and `acc2-brandish` PASS both checks.

## Rows

### spawn-view (R6 view cone, station spawner facing)

- **Command:** `bash run-acc.sh spawn-dup acc-spawn-view acc7-spawn-view`
- **Server:** `servers/cop-ai-spawn-dup`, port 25611. The scenario is `scen/acc-spawn-view.json` (591 steps).
- **Judge:** `python -I judge-acc.py spawn E:/Programming/java/wt/_programme/runs/cop-ai-spawn-dup--acc7-spawn-view`

Pass, for each check:
- **`R6`:** 0 UNIT spawns less than 56 degrees off his look on open ground.
  - The look is set by the tp yaw (LA looks where he runs, LB looks back) or by the latest Rotation probe.
  - INFO lists the 56-64 degree rounding band and ahead=true counts per phase.
  - INCONCLUSIVE if no UNIT has a known pose.
- **`cause2-margin`:** 0 UNIT spawns more than 61 blocks (3D) from him.
- **`R3`:** no RETURNING walker more than 80 b from him stays listed in `glw cop list Runner` across 5 s or more.
- **`R3-count`:** the live squad is never over its target in two consecutive probes. The live squad is the rows minus
  RETURNING walkers within 80 b; the target is `2 + level - 1`, capped at 8.
- **`causeR`:** no two `[DISPATCH] N units en route` lines less than 29 s apart within one wanted episode.
- **`R6-station`:**
  - At SV-S, facing the station spawner (0,-60,74) from 54 b: the spawner is used 0 times.
  - At SV-N, facing away, the spawner use count is INFO.
- **`R3-in-view`:** 0 FAIL (see above).
- **`search-clear`:** 3 windows (CLEAR, SV-S-CLEAR, SV-N-CLEAR), each with 0 HANDOFF lines and 0 order lines.

### spawn-dup (repro corridor, level 3)

- **Command:** `bash run-acc.sh spawn-dup acc-spawn-dup acc7-spawn-dup`
- **Server:** `servers/cop-ai-spawn-dup`, port 25611. The scenario is `scen/acc-spawn-dup.json` (509 steps). Run it
  after spawn-view finishes, not in parallel.
- **Judge:** `python -I judge-acc.py spawn E:/Programming/java/wt/_programme/runs/cop-ai-spawn-dup--acc7-spawn-dup`

Pass:
- `R6`, `cause2-margin`, `R3`, `R3-count`, `causeR` and `R3-in-view`, with the criteria from spawn-view.
- `search-clear` is INCONCLUSIVE because this scenario has no clear step. That is expected.

### spawn-dup-b (cause 2 hut spawner, cause 5, post-escape refill)

- **Command:** `bash run-acc.sh spawn-dup acc-spawn-dup-b acc7-spawn-dup-b`
- **Server:** `servers/cop-ai-spawn-dup`, port 25611. The scenario is `scen/acc-spawn-dup-b.json` (614 steps). Run it
  after spawn-dup.
- **Judge:** `python -I judge-acc.py spawn E:/Programming/java/wt/_programme/runs/cop-ai-spawn-dup--acc7-spawn-dup-b`

Pass:
- **`R6`:** 0 in-cone spawns outside the sealed PP phases.
- `cause2-margin`, `R3`, `R3-count`, `causeR` and `R3-in-view`, with the criteria from spawn-view.
- **`cause2-hut`:** 0 UNIT at the pillar-hut spawner `0,-46,74`, and 0 cops going PURSUING to RETURNING within 3 s of
  their UNIT line.
- **`cause5`:** 0 RETURNING to PURSUING transitions within 3 s of `P5-ADD1` for cops last probed more than 80 b out.
- **`search-clear`:** every window has 0 HANDOFF lines and 0 order lines.
- Expect step #39 (`UNIT Runner.*hidden=true.*at=0,-46,74`) failed in acc4, acc5 and acc6 (613/614 steps). On the
  fixed build no unit uses the hut spawner, which is exactly what `cause2-hut` asserts. So `rc=1` from that step
  alone is expected.

### radio-far (R4 sealed-room radio, R5 heading after a walk west)

- **Command:** `bash run-acc.sh radio-far acc-radio-far acc7-radio-far`
- **Server:** `servers/cop-ai-radio-far`, port 25612. The scenario is `scen/acc-radio-far.json` (232 steps).
- **Judge:** `python -I judge-acc.py radio E:/Programming/java/wt/_programme/runs/cop-ai-radio-far--acc7-radio-far`

Pass:
- **`R4`:** in the sealed window, from `GO-ROOM` + 5 s to `END`:
  - 0 radio lines from Engage, Push, Flank_Left/Right, Overwatch_Set, Standoff, Route or Route_High;
  - 0 sight claims (Contact, Marksman_Spotted, Eyes_On, Overwatch_Spotted);
  - 0 `STANDOFF Runner start trigger=UNREACHABLE`.
  - Route_Search and Commander/Flanking/Shield lines are INFO.
- **`R5`:**
  - The walk west happened: Runner x at CONTACT-b is below -3. If not, the check is INCONCLUSIVE.
  - A `Lost him heading ...` line reaches Runner within 12 s of `GO-FAR`.
  - The first such line says `west`. The squad saw him walk west; the live hop was south.
- **`R6`:** 0 in-cone UNIT spawns on open ground. The phases GO-ROOM, ROOM, CLEAR, WANTED3 and END are skipped.
- **`cause2-margin`:** 0 spawns more than 61 b from him.
- **`R3-in-view`:** 0 FAIL.
- **`search-clear`:** the CLEAR window runs about 4 s, until `wanted add 3`. It must have 0 HANDOFF lines and 0 order
  lines. acc6 failed this check on the older build.

### radio-route (R4 with a tunnel the cops can walk in by)

- **Command:** `bash run-acc.sh radio-far acc-radio-route acc7-radio-route`
- **Server:** `servers/cop-ai-radio-far`, port 25612. The scenario is `scen/acc-radio-route.json` (103 steps). Run it
  after radio-far finishes.
- **Judge:** `python -I judge-acc.py radio E:/Programming/java/wt/_programme/runs/cop-ai-radio-far--acc7-radio-route`

Pass:
- **`R4`:** as radio-far, except the window ends at the first real sighting through the tunnel. Sight claims are not
  counted in this variant. The time of that first sighting is INFO.
- **`R5`:** INFO only (this row has no walk): a Handoff line is still heard after the hop.
- `R6`, `cause2-margin` and `R3-in-view`, as radio-far.
- `search-clear` is INCONCLUSIVE because this scenario has no clear step.
- Docket T-432 (open, P3): a second Handoff during the 0-star bounty search. That row may show up as an extra INFO
  line here. It is not a check.

### brandish (R1 / R2 / Lost_Sight / range shrink)

- **Command:** `bash run-acc.sh brandish acc-brandish acc7-brandish`
- **Server:** `servers/cop-ai-brandish`, port 25613. The scenario is `scen/acc-brandish.json` (495 steps).
- **Judge:** `python -I judge-acc.py brandish E:/Programming/java/wt/_programme/runs/cop-ai-brandish--acc7-brandish`

Pass:
- **`R1-<bot>`:** for each bot whose first verdict is `ignored` (Igner), all of the following within 3 s:
  - chat `Beat cop: You were warned. Cause: Brandishing a weapon near an officer`;
  - a `[subtitle]` that is only the 5-star row with at least one ★;
  - the card line `Brandishing a weapon near an officer: ...` in chat;
  - a `DISPATCH <bot>` debug line;
  - this is his first ignore.
- **`R2-<bot>`:** after every verdict (complied or ignored), the same player's next `WARNING <bot> result=given` comes
  29 s or more later. The logs have whole seconds, so 29 s stands for 30 s.
- **`verdict-msg-<bot>`:** every complied verdict has `Thanks, carry on` or `Lost sight of you` within 2 s.
- **`Lost_Sight`:** Oov's out-of-view verdict tells him `Lost sight of you`.
- **`R2-repeat`:** Oov's second warning comes 30-45 s after that verdict.
- **`shrink-Nighter`:** every warning in the N- phases fires with the nearest cop at 6.5 b or less (darkness shrink
  kept).
- **`shrink-Sneaker`:** every warning in the S-SNEAK phases fires with the nearest cop at 9.4 b or less (sneak shrink
  kept).
- **`search-clear`:** Igner's I-CLEAR window has 0 HANDOFF lines and 0 order lines. The window ends at his next
  `WARNING Igner result=ignored`, because he stays armed next to cops and is re-wanted on purpose.

### brandish-orig (the 0.21.0 repro scenario on the fixed build)

- **Command:** `bash run-acc.sh brandish brandish acc7-brandish-orig`
- **Server:** `servers/cop-ai-brandish`, port 25613. The scenario is `scen/brandish.json` (281 steps, as
  `acc2-brandish-orig-c`). Run it after brandish.
- **Judge:** `python -I judge-acc.py brandish E:/Programming/java/wt/_programme/runs/cop-ai-brandish--acc7-brandish-orig`

Pass:
- **`R1-Drawer` and `R1-Runner`:** as brandish.
- **`R2-<bot>`:** one line for each verdict that has a later `result=given` from the same player. Each must show the
  next warning 29 s or more after the verdict. Variant B: Drawer complies, then re-draws about 35 s later.
  - `R2-Runner` may be absent: in `acc2-brandish-orig-c`, Runner's ignored verdict had no later warning. A missing
    `R2-Runner` line is not a FAIL.
- **`verdict-msg-Drawer`:** `Thanks, carry on.`.
- `shrink-*` and `search-clear` are INCONCLUSIVE because this scenario has no Nighter, Sneaker or clear step. That is
  expected.

### civ (civilian witness / 911, rows A-E)

- **Command:** `bash run-civ.sh civ-911 acc7-civ`
- **Server:** `servers/cop-ai-civ`, port 25614. The scenario is `scen/civ-911.json` (406 steps, about 6 minutes after
  boot). `run-civ.sh` now deploys `wt/gangland-0.21.1/target`, which is the merged build. Env `GL` overrides it.
- **Judge:** there is no script. Use `civ-README.md` "Pass criteria", which lists every check:
  - rows A (silent crime in front), B (behind), C (loud shot behind), D (intimidate + scared cooldown) and E (no
    witness, then a witness);
  - the all-run checks: no cop exists before each `-CRIME` marker, 10 modules, 0 exceptions.
- Yaws are compared modulo 360.
- `rc=1` means a spawn reply was missing, or W_D was still fleeing after 30 s. Either is a setup failure.

### handoff-lead (new: R5 lead, R4 no live-position tip, R6)

- **Command:** `bash run-acc.sh handoff-lead handoff-lead acc7-handoff-lead`
- **Server:** `servers/cop-ai-handoff-lead`, port 25615. This is the new `run.sh` key. The scenario is
  `scen/handoff-lead.json` (248 steps, from `node gen-handoff.js`).
- **Judge:** `python -I judge-acc.py handoff E:/Programming/java/wt/_programme/runs/cop-ai-handoff-lead--acc7-handoff-lead`

What the scenario does:
1. Sets 2 stars. With no station, cops spawn on a ring.
2. Contact: Runner stands at (0,0) with yaw 90 (west) for about 6 s.
3. Hops: 2-block hops west every 0.5 s to x=-20, yaw 90 on every tp, then about 1 s at (-20,0).
4. `GO-BOX`: a tp into a sealed 9x9x6 stone box at (0,-60,200) with yaw 180. The box is due south of the last-known
   spot, off the heading line, about 200 b away and more than 80 b from every cop.
5. 28 probes about 1 s apart (`BOX-n`: every NPC position and his Pos; every 5th also has `glw cop list` and his
   Rotation). He does not move.
6. `GO-OUT`: out at (0,-60,185) facing west, for 2 probes.
7. `/glw wanted clear` there.
8. `WATCH`: the same spot facing west, for 10 probes.
9. `AWAY`: the same spot facing south, for 8 probes.
10. The last step expects `HANDOFF_LEAD Runner` since the row start. If only that step failed, `rc=1` means the
    hand-off never seeded a lead. That is a behaviour FAIL, not a setup failure.

Pass, for each check:
- **`HANDOFF_LEAD`:** a `HANDOFF_LEAD Runner at=... heading=... dir=... from=...` line after `GO-BOX`.
- **`R5-heading`:** its heading is `west`, and the normalized dir has x below -0.7 (dir is logged unnormalized, e.g.
  -8.00,0.00). The squad saw him hop west; the box is due south.
- **`R5-lead-len` (INFO):** the distance from `from=` to `at=`. It should be 24 (Lead_Blocks), and shorter only at a
  wall.
- **`R5-heard`:** `Lost him heading west. ...` reaches Runner within 15 s of `GO-BOX`. The hand-off line reaches the
  player (R5).
- **`R5-lead-go`:** covers every unit with `UNIT ... bias=true` after the HANDOFF, over the probes in the 15 s after
  HANDOFF_LEAD. There must be 12 or more probes; fewer is INCONCLUSIVE. Every unit falls into exactly one class, and
  the check passes only when "toward the lead" has at least one unit and the next three classes are empty:
  - **toward the lead:** its distance to the logged `at=` falls by more than 3 b, or it ends within 8 b of it;
  - **toward the box:**
    - it comes within 7 b of the box center after starting 10 b or more away; or
    - its net move is less than 45 degrees off the box line and closer to the box line than to the lead line; or
    - its lead distance grows by more than 3 b while its box distance shrinks by more than 3 b;
  - **moving but not toward the lead:** it moved 3 b or more and is in neither class above;
  - **still:** it moved less than 3 b. Still is allowed, but it does not count toward a pass.
  - Per-unit tracks are printed as INFO (`R5-lead-rows`).
- **`R6`:** 0 UNIT spawns less than 56 degrees off his look on open ground. Open ground here means contact, hops, OUT,
  WATCH and AWAY. Each pose is the tp yaw.
  - The boxed phases are skipped. The spawner's own test (`CopSpawnManager.inViewOf`) is the cone AND a clear line,
    so a spot the walls hide is allowed.
  - R6 is mainly covered by spawn-view; here it guards contact, hops and OUT.
- **`cause2-margin`:** 0 spawns more than 61 b from him.
- **`R3-in-view`:** 0 FAIL. The boxed phases are skipped.
  - In every preview the hand-off units were removed while he was still boxed (none were left at `GO-OUT`), so here
    the check judges only the open-ground contact squad.
  - R3-in-view's real coverage is spawn-view, spawn-dup and spawn-dup-b.
- **`search-clear`:** the CLEAR window runs from the clear to END. It must have 0 HANDOFF or HANDOFF_LEAD lines and 0
  order lines.

## Previews of handoff-lead (non-acc7 tags, 0 exceptions each)

Each preview ran a different layout of the tail after the box:

| Preview | Layout | Steps, rc |
|---|---|---|
| `runs/cop-ai-handoff-lead--hl-env1` | clear in the box, WATCH facing north | 245/245, rc=0 |
| `runs/cop-ai-handoff-lead--hl-env2` | clear in the box, WATCH facing west | 245/245, rc=0 |
| `runs/cop-ai-handoff-lead--hl-env3` | the final layout: out, then clear | 248/248, rc=0 |

What was the same in all three:
- **Mechanics:** `HANDOFF Runner heading=west` came at +0 s and `HANDOFF_LEAD Runner at=-44,-60,0 heading=west
  from=-20,-60,0` at +0-1 s. The lead is exactly 24 b. `Lost him heading west. Units ahead, pick him up.` was heard.
- **Passing checks:** `R6`, `cause2-margin`, `R3-in-view` and `search-clear` PASS in all three.
- **`R5-lead-go`:** FAIL in all three, with the current judge.
  - The 3 biased units always spawned ahead=true around the boxed live spot, 30-45 b from the box and about 185-230 b
    from the lead.
  - Keystone logged `route walk failed 2 legs toward world -44,-60,0 from -38,-60,205` (env1) and `from -37,-60,214`
    (env2).
  - The units then held, or shuffled toward their pointman near the box's latitude. None closed on the lead.
  - env1: Linda walked 40 b to (-37.7,202.8), lead distance 163 to 202 and box distance 45 to 38.
  - env2: Linda's lead distance went 184 to 212.
  - env3: Sam moved 5 b, 33 degrees off the box line.

This is docket **T-423** (open, "needs owner decision"), reproduced on the r5 build. R5 is now that decision. The
code paths:
- `CopSpawnManager.spawnUnit` places every biased unit with `hiddenRing(target, ...)`. `bias.ahead(at, ...)` is
  measured from `at = target.getLocation()`, his live spot, and the leash margin from `leashAnchor`.
- `CopManager.leashAnchor` returns the squad's last-known spot only while `isSearching`, and his live location
  otherwise.

So a hand-off unit is born near the live spot and routed to a lead that can be about 200 b away.

## Docket rows

None NEW this round. The handoff-lead FAIL is existing row T-423; record the hl-env1..3 previews as its evidence.
