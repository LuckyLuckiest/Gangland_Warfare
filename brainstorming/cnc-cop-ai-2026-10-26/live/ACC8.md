# ACC8: 0.21.1 acceptance round 8 (2026-10-27)

ACC8 is ACC7 with the owner rulings of 2026-10-27 and a revised judge. Each run uses the tag `acc8-<key>`.

What changed since ACC7:
- **T-423:** hand-off units spawn between the player's live spot and the R5 lead. Their goal is still the lead.
- **R3 range:** the code's 48 b (`Visibility_Check_Distance`, the server's player tracking range) is accepted as is.
- **Watched cap:** a RETURNING walker that a player keeps watching from within 24 b is removed anyway after
  `Cops.Return.Watched_Cap_Seconds`.
- **Judge:** four checks were changed or added. See "Judge changes in this round".

## Build under test

Built 2026-10-27. Every acc8 run's `deployed.md5` matches this table (all 9 runs checked).

| What | Where | md5 |
|---|---|---|
| Gangland core | `E:/Programming/java/wt/gangland-0.21.1/target/gangland_warfare-0.21.1.jar` (built 21:01 from the branch 0.21.1 working tree, just before 3603399c3 was committed at 21:01:26; 28ddc73c3..3603399c3 hold the T-423 spawn-between and watched-cap work) | `77e7d53f106486d4e84bfc12a03ff005` |
| cops-n-crooks module | `.../target/modules/cops-n-crooks-0.21.1.jar` | `3ea60b5b3ad72dbb2e444de1e636f7f4` |
| gangland-civilians module | `.../target/modules/gangland-civilians-0.21.1.jar` | `a9450406c50731e5640c13b6eb2fbb2d` |
| Keystone 1.15.4 | `E:/Programming/java/wt/keystone-1.15.4/keystone-plugin/target/Keystone-1.15.4.jar` (built 19:15; HEAD e06da82, a docs-only commit at 19:17) | `9eb26046c3d86b798c7a3a1ec47e82a6` |

`run-acc.sh` deploys whatever is in `wt/gangland-0.21.1/target`. If you rebuild there, fill this table in again.

The build must ship these config keys. The judge reads them, and `documentation/migration-0.21.1.md` must list them
for the manual edit of a deployed config:
- `Cops.Return.Watched_Cap_Seconds` (default about 60). The judge reads it from the run's `cops.yml` and uses 60 when
  the key is missing.
- The spawn-between fraction key under `Cops.Handoff` (default 0.5, the midpoint). Its name is up to the build. The
  judge does not read it: R5-spawn-between checks only that a unit spawns on the lead's side.
- The fallback debug line (contract). When no valid spot exists between him and the lead, the spawner falls back to
  today's ring. It must then log a `Cops N Crooks` debug line that contains `Runner` and `fallback`, within 1 s of
  that unit's `UNIT` line, for example `HANDOFF_RING Runner center=x,y,z fraction=0.5 clamped=true fallback=true`.
  A line with `fallback=false` does not count.

## Re-prep and boot check (to do)

Each server boots once with the new jars. The boot tags are:
- `cop-ai-spawn-dup--acc8-boot`;
- `cop-ai-radio-far--acc8-boot`;
- `cop-ai-brandish--acc8-boot`;
- `cop-ai-civ--acc8-boot` (`civ-boot`);
- `cop-ai-handoff-lead--acc8-boot`.

Every boot must show the same items as in ACC7:
- Keystone 1.15.4, Gangland_Warfare 0.21.1, Bartizan 0.6.0 and Citizens 2.0.43-SNAPSHOT;
- `Runtime modules: 10 loaded, 0 fault(s)`;
- `EXCEPTIONS (0 distinct)`;
- the only ERROR is `No key layers in MapLike[{}]` (3x);
- `deployed.md5` equals the build table.

The civ boot must also pass the four civ-README boot items.

## How to run

Run everything from `E:/Programming/java/Gangland Warfare [Cubed-GTA recoded]/brainstorming/cnc-cop-ai-2026-10-26/live`
in Git Bash.

- **Output:** `E:/Programming/java/wt/_programme/runs/cop-ai-<server key>--acc8-<key>/`. It holds `server.log`,
  `chat.txt`, `summary.txt`, `steps.json` and `deployed.md5`.
- **Tags:** a tag that already exists is refused (exit 4). For a rerun, use `acc8-<key>-r2`.
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


### Judge changes in this round

The `search-clear` check is unchanged from ACC7. ACC7.md describes it.

**`cause2-hut`** (spawn-dup-b), revised:
- 0 UNIT at the pillar-hut spawner `0,-46,74`. This part is unchanged.
- 0 cops go from PURSUING to RETURNING within 3 s of their UNIT line. This part now counts only units born in a `P2-`
  phase, and it skips a leash when a `tp Runner` step falls between the UNIT line and the transition.
- Phase PP hops him 180 b every few seconds, so a leash there comes from the hop, not from the spawn spot. The skipped
  leashes are printed as INFO (`cause2-hut-skip`).

**`R3-in-view`** (spawn, radio and handoff), revised:
- **Range:** the smaller of two values. Both sources are printed on the INFO line `R3-in-view-cfg`.
  - `entity-tracking-range.players`, read from `servers/cop-ai-<key>/spigot.yml`, or from `servers/base` when that
    file is missing. It is 48 today.
  - `Cops.Spawn.Visibility_Check_Distance`, read from the server's deployed `cops.yml`, then from the module jar, then
    48.
- **Cone:** 60 degrees, the same as the code (`CopSightlines.inView`).
- **Vanish:** an NPC listed in probe i that no later probe lists. The removal happened between probe i and probe i+1.
- **Walker position:** the walker is judged at its probe-i spot, and at its spot projected to probe i+1's time. The
  projection follows its last two listings, capped at 6 b/s.
- **Player pose:** his pose while each probe ran. ACC7 used his pose at the next marker, which in hop phases was
  already the pose after the next hop.
- **Moved:** if he teleported or walked between the two probes, that walker is INCONCLUSIVE. These walkers are counted
  in the summary and listed on the INFO line `R3-in-view-tp`.
  - A move counts when it falls in any probe from probe i up to probe i+1, including a hop marker's own empty probe
    (`P2-HOP-EAST`, `GO-BOX`, `GO-ROOM`, `GO-AWAY`).
- **Age rules** (from `ReturningBehavior`):
  - RETURNING age at or past `Return.Max_Ticks` (600 x AI_Tick_Rate 10 / 20 = 300 s): only 24 b holds the walker, so
    the range is 24.
  - Age at or past Max_Ticks plus `Watched_Cap_Seconds`: the walker is exempt, because the cap removes it.
- **Verdict:** FAIL if the walker is in view at both ends of the window. In view at one end only is INFO (`-amb`).
  Line of sight is not traced, and the sealed phases are skipped, as in ACC7.

**`R3-cap`** (spawn, radio and handoff), new:
- The bound is RETURNING age `Max_Ticks` + `Watched_Cap_Seconds` + 15 s. The 15 s is slack for AI ticks that lag
  behind wall time. With the defaults the bound is 300 + 60 + 15 = 375 s.
  - The cap may count from Max_Ticks (after which only a view within 24 b keeps a walker) or from the start of the
    watch. Either way, every removal lands before the bound.
- **FAIL:** a cop is still listed RETURNING past the bound, whether or not it is in view.
- **PASS:** at least one walker was in his view (less than 24 b, less than 60 deg) past Max_Ticks, and no walker
  outlived the bound.
- **INCONCLUSIVE:** no walker was watched that long. That is the result on every existing run, because no scenario
  keeps a walker in view for 5 minutes. See the r3-cap row spec below.

**`R5-spawn-between`** (handoff), new. This is T-423.
- **Units:** every `UNIT ... bias=true` after the HANDOFF line.
- **Pass per unit:** the unit's spawn is closer to the logged lead than his live spot at spawn time, with 4 b of
  tolerance.
- **Exempt:** a unit with a fallback line within 1 s of its UNIT line (see the contract above).
- **INFO** (`R5-spawn-rows`): the centroid of the spawns and how much closer it is to the lead than he is, plus one
  line per unit.
- **Limit:** this check is geometry only. On the acc7 build (today's ring around the live spot), hl-env1 and hl-env3
  already pass it because their units happened to land on the lead's side. `R5-lead-go` stays the behaviour verdict and
  is unchanged.
- **Expected values:** on a T-423 build with the box about 204 b from the lead, the ring center is clamped to the
  spawn range. The units then spawn within 61 b of him (`cause2-margin`), and their lead distance is about 145-180 b
  against his 204 b.

## Rows

### spawn-view (R6 view cone, station spawner facing)

- **Command:** `bash run-acc.sh spawn-dup acc-spawn-view acc8-spawn-view`
- **Server:** `servers/cop-ai-spawn-dup`, port 25611. The scenario is `scen/acc-spawn-view.json` (591 steps).
- **Judge:** `python -I judge-acc.py spawn E:/Programming/java/wt/_programme/runs/cop-ai-spawn-dup--acc8-spawn-view`

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
- **`R3-in-view`:** 0 FAIL (ACC8 criteria above). The hop windows are INCONCLUSIVE per walker. The HOLD-a to HOLD-b
  windows and the clear windows are judged.
- **`R3-cap`:** not FAIL. INCONCLUSIVE is expected.
- **`search-clear`:** 3 windows (CLEAR, SV-S-CLEAR, SV-N-CLEAR), each with 0 HANDOFF lines and 0 order lines.

### spawn-dup (repro corridor, level 3)

- **Command:** `bash run-acc.sh spawn-dup acc-spawn-dup acc8-spawn-dup`
- **Server:** `servers/cop-ai-spawn-dup`, port 25611. The scenario is `scen/acc-spawn-dup.json` (509 steps). Run it
  after spawn-view finishes, not in parallel.
- **Judge:** `python -I judge-acc.py spawn E:/Programming/java/wt/_programme/runs/cop-ai-spawn-dup--acc8-spawn-dup`

Pass:
- `R6`, `cause2-margin`, `R3`, `R3-count`, `causeR`, `R3-in-view` and `R3-cap`, with the criteria from
  spawn-view.
- `search-clear` is INCONCLUSIVE because this scenario has no clear step. That is expected.

### spawn-dup-b (cause 2 hut spawner, cause 5, post-escape refill)

- **Command:** `bash run-acc.sh spawn-dup acc-spawn-dup-b acc8-spawn-dup-b`
- **Server:** `servers/cop-ai-spawn-dup`, port 25611. The scenario is `scen/acc-spawn-dup-b.json` (614 steps). Run it
  after spawn-dup.
- **Judge:** `python -I judge-acc.py spawn E:/Programming/java/wt/_programme/runs/cop-ai-spawn-dup--acc8-spawn-dup-b`

Pass:
- **`R6`:** 0 in-cone spawns outside the sealed PP phases.
- `cause2-margin`, `R3`, `R3-count`, `causeR`, `R3-in-view` and `R3-cap`, with the criteria from spawn-view.
  - `R3-in-view` judges the P2-A and P2-B windows, where he stands still at (-28,0). acc5 and acc6 FAIL there on the
    old build.
- **`cause2-hut`:** 0 UNIT at the pillar-hut spawner `0,-46,74`, and 0 units born in a `P2-` phase going PURSUING to
  RETURNING within 3 s of their UNIT line with no hop of his in between. The PP-phase leashes are INFO.
- **`cause5`:** 0 RETURNING to PURSUING transitions within 3 s of `P5-ADD1` for cops last probed more than 80 b out.
- **`search-clear`:** every window has 0 HANDOFF lines and 0 order lines.
- Expect step #39 (`UNIT Runner.*hidden=true.*at=0,-46,74`) failed in acc4, acc5 and acc6 (613/614 steps). On the
  fixed build no unit uses the hut spawner, which is exactly what `cause2-hut` asserts. So `rc=1` from that step
  alone is expected.

### radio-far (R4 sealed-room radio, R5 heading after a walk west)

- **Command:** `bash run-acc.sh radio-far acc-radio-far acc8-radio-far`
- **Server:** `servers/cop-ai-radio-far`, port 25612. The scenario is `scen/acc-radio-far.json` (232 steps).
- **Judge:** `python -I judge-acc.py radio E:/Programming/java/wt/_programme/runs/cop-ai-radio-far--acc8-radio-far`

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
- **`R3-cap`:** not FAIL. INCONCLUSIVE is expected.
- **`search-clear`:** the CLEAR window runs about 4 s, until `wanted add 3`. It must have 0 HANDOFF lines and 0 order
  lines. acc6 failed this check on the older build.

### radio-route (R4 with a tunnel the cops can walk in by)

- **Command:** `bash run-acc.sh radio-far acc-radio-route acc8-radio-route`
- **Server:** `servers/cop-ai-radio-far`, port 25612. The scenario is `scen/acc-radio-route.json` (103 steps). Run it
  after radio-far finishes.
- **Judge:** `python -I judge-acc.py radio E:/Programming/java/wt/_programme/runs/cop-ai-radio-far--acc8-radio-route`

Pass:
- **`R4`:** as radio-far, except the window ends at the first real sighting through the tunnel. Sight claims are not
  counted in this variant. The time of that first sighting is INFO.
- **`R5`:** INFO only (this row has no walk): a Handoff line is still heard after the hop.
- `R6`, `cause2-margin`, `R3-in-view` and `R3-cap`, as radio-far.
- `search-clear` is INCONCLUSIVE because this scenario has no clear step.
- Docket T-432 (open, P3): a second Handoff during the 0-star bounty search. That row may show up as an extra INFO
  line here. It is not a check.

### brandish (R1 / R2 / Lost_Sight / range shrink)

- **Command:** `bash run-acc.sh brandish acc-brandish acc8-brandish`
- **Server:** `servers/cop-ai-brandish`, port 25613. The scenario is `scen/acc-brandish.json` (495 steps).
- **Judge:** `python -I judge-acc.py brandish E:/Programming/java/wt/_programme/runs/cop-ai-brandish--acc8-brandish`

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

- **Command:** `bash run-acc.sh brandish brandish acc8-brandish-orig`
- **Server:** `servers/cop-ai-brandish`, port 25613. The scenario is `scen/brandish.json` (281 steps, as
  `acc2-brandish-orig-c`). Run it after brandish.
- **Judge:** `python -I judge-acc.py brandish E:/Programming/java/wt/_programme/runs/cop-ai-brandish--acc8-brandish-orig`

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

- **Command:** `bash run-civ.sh civ-911 acc8-civ`
- **Server:** `servers/cop-ai-civ`, port 25614. The scenario is `scen/civ-911.json` (406 steps, about 6 minutes after
  boot). `run-civ.sh` now deploys `wt/gangland-0.21.1/target`, which is the merged build. Env `GL` overrides it.
- **Judge:** there is no script. Use `civ-README.md` "Pass criteria", which lists every check:
  - rows A (silent crime in front), B (behind), C (loud shot behind), D (intimidate + scared cooldown) and E (no
    witness, then a witness);
  - the all-run checks: no cop exists before each `-CRIME` marker, 10 modules, 0 exceptions.
- Yaws are compared modulo 360.
- `rc=1` means a spawn reply was missing, or W_D was still fleeing after 30 s. Either is a setup failure.

### handoff-lead (R5 lead, T-423 spawn between, R4 no live-position tip, R6)

- **Command:** `bash run-acc.sh handoff-lead handoff-lead acc8-handoff-lead`
- **Server:** `servers/cop-ai-handoff-lead`, port 25615. This is the new `run.sh` key. The scenario is
  `scen/handoff-lead.json` (248 steps, from `node gen-handoff.js`).
- **Judge:** `python -I judge-acc.py handoff E:/Programming/java/wt/_programme/runs/cop-ai-handoff-lead--acc8-handoff-lead`

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
- **`R5-spawn-between`** (new, T-423): every biased unit spawns closer to the lead than his live spot (4 b
  tolerance), or its fallback line is logged. See "Judge changes in this round".
  - On the fixed build, expect spawns within 61 b of the box on its lead side (north-west), not around the box.
  - Keystone's `route walk failed ... toward world -44,-60,0` must not come back from that closer start. If it does,
    record it as a Keystone finding (see the docket rows).
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
  - In every acc7 run the hand-off units were removed while he was still boxed (none were left at `GO-OUT`). The
    contact squad vanished between `HOPS-END` and `BOX-1`, the window with his hop into the box.
  - So on the acc7 runs the check is INCONCLUSIVE (0 judged). It judges a walker only if one vanishes during OUT,
    WATCH or AWAY.
  - R3-in-view's real coverage is spawn-view, spawn-dup and spawn-dup-b.
- **`R3-cap`:** not FAIL. INCONCLUSIVE is expected.
- **`search-clear`:** the CLEAR window runs from the clear to END. It must have 0 HANDOFF or HANDOFF_LEAD lines and 0
  order lines.

### r3-cap (spec only, not generated): the watched cap

No current scenario keeps a walker in view long enough to reach the cap, so `R3-cap` is INCONCLUSIVE on every row
above. This row covers it. It is not built yet.

- **Server:** `servers/cop-ai-handoff-lead`, port 25615. The scenario would be `scen/r3-cap.json`.
- **Shipped config:** with Max_Ticks 600 and a 60 s cap, the bound is 375 s, so the WATCH phase must last at least
  400 s.
- **Steps:**
  1. Level 1, no station, Runner at (0,0).
  2. Probe about every 0.5 s until a probe lists a cop within 10 b of him.
  3. `/glw wanted clear` on that first probe. Waiting longer races the cuff (`Cuff_Radius` 3).
  4. WATCH: 400 probes about 1 s apart. Each probe has `glw cop list`, the NPC positions, his Pos and his Rotation, so
     the judge has his facing for every probe.
  5. AWAY: he turns away for 10 probes.
- **Facing:** he must keep facing the walker without being teleported. Teleporting him would make each window
  INCONCLUSIVE for `R3-in-view`.
  - The runner has no "look at an NPC" step. A 3-line `lookAtEntity: {near, type}` step in `run-copai.js` (the
    `nearestEntity` helper already exists) would do it. Run it once per probe.
- **Pass:** `R3-cap` PASS, which means a walker held in view past Max_Ticks is gone by 375 s. Also `R3-in-view` PASS:
  a removal by the cap is exempt.
- **On the acc7 build:** this row should FAIL, because a walker within 24 b in view is kept forever. That makes the
  row a real discriminator.

## Validation of the ACC8 judge on existing runs (2026-10-27)

Each run was judged with the ACC7 judge (before) and the ACC8 judge (after), with `python -I`. The run dirs are under
`E:/Programming/java/wt/_programme/runs/`. Every check not in the table gave the same output line before and after on
all 13 runs (diffed).

| Run | Check | Before (ACC7 judge) | After (ACC8 judge) | Why |
|---|---|---|---|---|
| acc7-spawn-view | R3-in-view | FAIL 1 (Steve#1008, LA-T2F-HOLD-b to LA-T2B-x60-h19, 57 b, 22/43 deg) | PASS (6 judged, 27 INCONCLUSIVE) | A hop to x60 falls in that window, so Steve#1008 is INCONCLUSIVE. He was also 57 b away, past 48. |
| acc7-spawn-dup | R3-in-view | PASS (27 judged) | PASS (6 judged, 21 INCONCLUSIVE) | Hop windows are now INCONCLUSIVE. |
| acc7-spawn-dup | R3 | FAIL (Nick#1015 18 s at 105 b) | FAIL, unchanged | Not touched. |
| acc7-spawn-dup-b | cause2-hut | FAIL (10 leashed, all PP-H5..H25) | PASS (10 PP leashes INFO) | Intended: the PP hops are not the spawn spot. |
| acc7-spawn-dup-b | R3-in-view | FAIL 5 | PASS (4 judged, 20 INCONCLUSIVE) | Sue#1008 (63 b) had a tp in its window. Marty#1002 (50 b) and Carl#1005 (56 b) were past 48 b. Tina#1003 (46 to 59 b) and Maria#1004 (47 to 53 b) leave the 48 b range during the window, so they are INFO `-amb`. |
| acc6-spawn-dup-b | cause2-hut | FAIL (10, all PP-H) | PASS | Intended, as acc7. |
| acc6-spawn-dup-b | R3-in-view | FAIL 4 | **FAIL 3** (4 judged) (Pete#1003 39 to 43 b, Hank#1004 33 to 39 b, Mike#1005 33 to 40 b, ages 15-16 s) | The hand-verified FAIL is kept (Pete#1003, cited in ACC7). Bob#1002 (49 b) dropped: past 48 b. |
| acc5-spawn-dup-b | cause2-hut | FAIL (10, all PP-H) | PASS | Intended, as acc7. |
| acc5-spawn-dup-b | R3-in-view | FAIL 4 | **FAIL 2** (4 judged) (Tony#1003 35 to 42 b, Kate#1004 39 to 45 b) | Still FAIL. Tony#1005 (45 to 50 b) and Walt#1002 (46 to 51 b) leave 48 b during the window: INFO `-amb`. |
| acc7-radio-far | R3-in-view | PASS (3 judged) | PASS (3 judged) | Same. |
| acc7-radio-route | R3-in-view | PASS | PASS | Same. |
| acc7-handoff-lead, hl-env1..3 | R3-in-view | PASS (3 judged) | INCONCLUSIVE (0 judged) | The 3 contact walkers vanished between HOPS-END and BOX-1. That window holds his tp into the box, so a pose from before the hop and one from after it were mixed. |
| acc7-handoff-lead | R5-lead-go | FAIL (0 toward the lead) | FAIL, unchanged | T-423 behaviour FAIL, kept. |
| acc7-handoff-lead | R5-spawn-between | (new) | **FAIL** (Tony#1003 -35 b, Tina#1005 -19 b; centroid -9 b) | Today's ring around the live spot. |
| hl-env1 | R5-lead-go | FAIL | FAIL, unchanged | Kept. |
| hl-env1 | R5-spawn-between | (new) | PASS (gains -1/+41/+8, centroid +16 b) | Luck-pass on the unfixed build: the ring units landed on the lead's side. |
| hl-env2 | R5-spawn-between | (new) | **FAIL** (Kate#1003 -10 b, Kate#1005 -28 b) | Today's ring. |
| hl-env3 | R5-spawn-between | (new) | PASS (gains +20/+7/+17, centroid +15 b) | Luck-pass, as env1. |
| all spawn/radio/handoff runs | R3-cap | (new) | INCONCLUSIVE | No walker was watched past Max_Ticks (300 s). The oldest RETURNING listing in any run is 16 s. |
| all runs | OVERALL | - | - | acc7-spawn-view and acc7-spawn-dup-b go from FAIL to PASS (see their rows above). acc5 and acc6 spawn-dup-b, acc7-spawn-dup and all handoff runs stay FAIL. Brandish and radio are unchanged. |

No hand-verified behaviour FAIL turned into PASS:
- acc5 and acc6 spawn-dup-b `R3-in-view` stay FAIL.
- acc7-spawn-dup `R3` stays FAIL.
- The handoff `R3-in-view` PASS turned into INCONCLUSIVE, not into FAIL. That PASS came from mixing his pose before the
  hop with his pose after it.
- T-423 `R5-lead-go` stays FAIL on all four handoff runs.

No check went from PASS to FAIL. The FAIL-to-PASS flips are all one of three things:
- the intended `cause2-hut` scope;
- windows where he hopped, now INCONCLUSIVE per walker;
- walkers that were past the 48 b range the owner accepted.

R3-cap's FAIL and PASS paths were also exercised synthetically on acc7-spawn-dup:
- Overriding Max_Ticks so the bound is 1 s gives FAIL: Nick#1015 is listed at 3-15 s.
- Overriding Max_Ticks to 2 s and the cap to 1000 s gives PASS: Nick#1015 is held in view within 24 b past
  "Max_Ticks".

## Docket rows

No harness row. The ACC7 judge took the probe pose at the next marker. That was a judge bug, not a plugin bug, and
this judge fixes it.

The watched cap (owner ruling 2026-10-27) is docket row T-501 (fixed in 28ddc73c3; filed 2026-10-27 from the NEW row reported here). T-423
keeps its row and gets the ruling plus the acc7 and hl-env1..3 evidence. Do not add a Keystone row for
`route walk failed 2 legs` unless it comes back from a spawn-between start, about 150 b from the lead.
