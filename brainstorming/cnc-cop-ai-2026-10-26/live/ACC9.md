# ACC9: 0.21.1 acceptance round 9 (2026-10-27)

ACC9 is ACC8 with a fixed spawn-view scenario and three judge fixes. The build is the ACC8 build. Each run uses the tag
`acc9-<key>`.

What changed since ACC8 (harness only, no plugin change):
- **spawn-view scenario:** before SV-S and before SV-N he waits at (-90,0) until the last squad is gone, then he
  stands facing the station spawner (SV-S) or away from it (SV-N) and gets 2 stars. In acc8 the SV-S half never ran.
  See "Judge and scenario changes in ACC9".
- **`R6-station`:** INCONCLUSIVE when no unit spawned during SV-S. acc8 printed PASS for "0x of 0".
- **`R3-count`:** a probe at wanted level 0 has no target and is skipped. The post-escape search keeps its cops by
  design (T-187).
- **`R5-lead-go`:** each unit's track ends at its first PURSUING to RETURNING line, so its walk home is not judged as
  part of its walk to the lead.
- The ACC8 rules (owner rulings of 2026-10-27, T-423 spawn-between, the watched cap, the ACC8 judge) all stay. ACC8.md
  has the ACC8 history.

## Build under test

Unchanged from ACC8. Built 2026-10-27. Every acc8 run's `deployed.md5` matches this table (all 9 runs checked).

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
- `Cops.Handoff.Spawn_Between` (default 0.5, the midpoint; 0 keeps the 0.16 ahead ring). The judge does not read
  it: R5-spawn-between checks only that a unit spawns on the lead's side.
- The fallback debug line (contract). When no valid spot exists between him and the lead, the spawner falls back to
  today's ring. It must then log a `Cops N Crooks` debug line that contains `Runner` and `fallback`, within 1 s of
  that unit's `UNIT` line, for example `HANDOFF_RING Runner center=x,y,z fraction=0.5 clamped=true fallback=true`.
  A line with `fallback=false` does not count.
  - The ACC8 build logs no fallback line yet (docket T-509, open). Until it does, a correct fallback scores as a
    R5-spawn-between FAIL, so read the UNIT spots before calling such a FAIL a T-423 regression.

## Re-prep and boot check

Each server boots once with the new jars. The boot tags are:
- `cop-ai-spawn-dup--acc9-boot`;
- `cop-ai-radio-far--acc9-boot`;
- `cop-ai-brandish--acc9-boot`;
- `cop-ai-civ--acc9-boot` (`civ-boot`);
- `cop-ai-handoff-lead--acc9-boot`.

Every boot must show the same items as in ACC7 and ACC8:
- Keystone 1.15.4, Gangland_Warfare 0.21.1, Bartizan 0.6.0 and Citizens 2.0.43-SNAPSHOT;
- `Runtime modules: 10 loaded, 0 fault(s)`;
- `EXCEPTIONS (0 distinct)`;
- the only ERROR is `No key layers in MapLike[{}]` (3x);
- `deployed.md5` equals the build table.

The civ boot must also pass the four civ-README boot items.

## How to run

Run everything from `E:/Programming/java/Gangland Warfare [Cubed-GTA recoded]/brainstorming/cnc-cop-ai-2026-10-26/live`
in Git Bash.

- **Output:** `E:/Programming/java/wt/_programme/runs/cop-ai-<server key>--acc9-<key>/`. It holds `server.log`,
  `chat.txt`, `summary.txt`, `steps.json` and `deployed.md5`.
- **Tags:** a tag that already exists is refused (exit 4). For a rerun, use `acc9-<key>-r2`.
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


### Judge and scenario changes in ACC9

**spawn-view scenario** (`gen-acc.js`, `scen/acc-spawn-view.json`, now 593 steps):
- **The acc8 failure:** step #500 (`expectLog DISPATCH Runner`, 20 s) failed. After CLEAR he waited 15 s at (-90,0),
  but the 4 RETURNING walkers of the last squad were still listed. `/glw wanted add 2` at SV-S called 3 of them back
  (RETURNING to PURSUING), so the level-2 squad (target 3) was already full and no unit was dispatched.
  - Ann#1032 cuffed him 12 s later. The only SV-S DISPATCH came after the jail release, at 21:25:43, 1 s before
    SV-S-CLEAR, and it spawned nothing before the clear.
  - So SV-S had 0 UNIT lines, and the acc8 judge printed `R6-station PASS ... 0x of 0`.
- **The fix:** a new `poll` step runs before SV-S and before SV-N. He waits at (-90,0) facing east, more than 100 b
  from the station. `glw cop list Runner` is issued every 3 s until the server logs `Runner is not being chased`.
  - The timeout is 330 s (Return.Max_Ticks 300 s plus slack). A timeout is a setup failure (rc=1), not a verdict.
  - In acc7 (the older build) the squad was gone within 18 s of the clear. In acc8 it was still listed after 19 s.
  - The `DISPATCH Runner` expect after `/glw wanted add 2` now waits 30 s (was 20 s).
- **Runner:** `run-copai.js` is regenerated by `mk-runner.js`, where the `poll` step `{poll, until, every, timeout}`
  is a new additive patch. `harness/run-cnc020.js` is not touched. Before the patch, `node mk-runner.js` reproduced
  the acc8 runner byte for byte. The other five `acc-*.json` scenarios regenerate byte-identical.
- **Judge:** the cop-list rows that a `poll` step prints are not probe rows. Without this, they would be appended to
  the CLEARED probe. A synthetic copy of acc8-spawn-view with a 3-try poll step inserted gives the same judge output;
  without the guard, its CLEARED probe lists 13 rows instead of 4.

**`R6-station`** (spawn):
- FAIL when the station spawner `0,-60,74` is used in an SV-S phase.
- PASS when SV-S has at least one UNIT and none of them is at the spawner.
- INCONCLUSIVE when SV-S has no UNIT at all. This is the acc8 case.
- INCONCLUSIVE too when the SV phases ran and neither SV-S nor SV-N has a UNIT. The ACC8 judge printed nothing then.

**`R3-count`** (spawn):
- A probe at wanted level 0 has no target. The level is 0 after a 0-star row (`☆☆☆☆☆`) or after `Your wanted level has
  been cleared`. The post-escape search keeps every cop it called back by design (T-187), so those probes are not
  judged. A level-0 probe also breaks a run of over-target probes.
- The ACC8 judge used target 2 at level 0 (`max(level, 1)`). The count of skipped probes is printed on the line.
- The level timeline is sorted by time only, so star rows logged in the same millisecond keep their chat order. A sort
  by (time, level) puts the 0-star row before the `Peak: ★★★☆☆` row logged with it, and AFTER-4 then reads level 3.

**`R5-lead-go`** (handoff):
- Each biased unit's track ends at its first PURSUING to RETURNING line after the HANDOFF. Probes after that line are
  dropped for that unit.
- A unit left with fewer than 3 probes is listed as `under 3 probes before returning`. It is in no class, so it does
  not count toward a pass.
- `R5-lead-rows` shows `returning +Ns` (seconds after HANDOFF_LEAD) or `no return` for each unit.
- The classes and the pass rule are unchanged from ACC8.

### Judge changes in ACC8 (kept)

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

- **Command:** `bash run-acc.sh spawn-dup acc-spawn-view acc9-spawn-view`
- **Server:** `servers/cop-ai-spawn-dup`, port 25611. The scenario is `scen/acc-spawn-view.json` (593 steps, from `node gen-acc.js`).
- **Judge:** `python -I judge-acc.py spawn E:/Programming/java/wt/_programme/runs/cop-ai-spawn-dup--acc9-spawn-view`

Pass, for each check:
- **`R6`:** 0 UNIT spawns less than 56 degrees off his look on open ground.
  - The look is set by the tp yaw (LA looks where he runs, LB looks back) or by the latest Rotation probe.
  - INFO lists the 56-64 degree rounding band and ahead=true counts per phase.
  - INCONCLUSIVE if no UNIT has a known pose.
- **`cause2-margin`:** 0 UNIT spawns more than 61 blocks (3D) from him.
- **`R3`:** no RETURNING walker more than 80 b from him stays listed in `glw cop list Runner` across 5 s or more.
- **`R3-count`:** the live squad is never over its target in two consecutive probes. The live squad is the rows minus
  RETURNING walkers within 80 b; the target is `2 + level - 1`, capped at 8. A probe at level 0 is not judged (ACC9).
- **`causeR`:** no two `[DISPATCH] N units en route` lines less than 29 s apart within one wanted episode.
- **`R6-station`:**
  - At SV-S, facing the station spawner (0,-60,74) from 54 b: the spawner is used 0 times, with at least one SV-S
    UNIT. With no SV-S UNIT the check is INCONCLUSIVE, which is a setup failure: read `summary.txt` `FAILED STEPS`.
  - At SV-N, facing away, the spawner use count is INFO.
- **`R3-in-view`:** 0 FAIL (ACC8 criteria above). The hop windows are INCONCLUSIVE per walker. The HOLD-a to HOLD-b
  windows and the clear windows are judged.
- **`R3-cap`:** not FAIL. INCONCLUSIVE is expected.
- **`search-clear`:** 3 windows (CLEAR, SV-S-CLEAR, SV-N-CLEAR), each with 0 HANDOFF lines and 0 order lines.
- **Setup:** the two `poll` steps (before SV-S and SV-N) and the two `DISPATCH Runner` expects after them pass. A
  failed `poll` means the last squad was still listed after 330 s.

### spawn-dup (repro corridor, level 3)

- **Command:** `bash run-acc.sh spawn-dup acc-spawn-dup acc9-spawn-dup`
- **Server:** `servers/cop-ai-spawn-dup`, port 25611. The scenario is `scen/acc-spawn-dup.json` (509 steps). Run it
  after spawn-view finishes, not in parallel.
- **Judge:** `python -I judge-acc.py spawn E:/Programming/java/wt/_programme/runs/cop-ai-spawn-dup--acc9-spawn-dup`

Pass:
- `R6`, `cause2-margin`, `R3`, `R3-count`, `causeR`, `R3-in-view` and `R3-cap`, with the criteria from
  spawn-view.
- `search-clear` is INCONCLUSIVE because this scenario has no clear step. That is expected.
- **Known FAIL on the ACC8 build:** `R3-count` FAILs at AFTER-3 (4 cops against target 3 at level 2, after AFTER-2
  was 4 > 3 as well). That is docket T-510 (open): a level drop never sends a POSTED or COMBAT surplus cop home. The
  ACC9 judge no longer flags AFTER-4, which is at level 0.

### spawn-dup-b (cause 2 hut spawner, cause 5, post-escape refill)

- **Command:** `bash run-acc.sh spawn-dup acc-spawn-dup-b acc9-spawn-dup-b`
- **Server:** `servers/cop-ai-spawn-dup`, port 25611. The scenario is `scen/acc-spawn-dup-b.json` (614 steps). Run it
  after spawn-dup.
- **Judge:** `python -I judge-acc.py spawn E:/Programming/java/wt/_programme/runs/cop-ai-spawn-dup--acc9-spawn-dup-b`

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

- **Command:** `bash run-acc.sh radio-far acc-radio-far acc9-radio-far`
- **Server:** `servers/cop-ai-radio-far`, port 25612. The scenario is `scen/acc-radio-far.json` (232 steps).
- **Judge:** `python -I judge-acc.py radio E:/Programming/java/wt/_programme/runs/cop-ai-radio-far--acc9-radio-far`

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

- **Command:** `bash run-acc.sh radio-far acc-radio-route acc9-radio-route`
- **Server:** `servers/cop-ai-radio-far`, port 25612. The scenario is `scen/acc-radio-route.json` (103 steps). Run it
  after radio-far finishes.
- **Judge:** `python -I judge-acc.py radio E:/Programming/java/wt/_programme/runs/cop-ai-radio-far--acc9-radio-route`

Pass:
- **`R4`:** as radio-far, except the window ends at the first real sighting through the tunnel. Sight claims are not
  counted in this variant. The time of that first sighting is INFO.
- **`R5`:** INFO only (this row has no walk): a Handoff line is still heard after the hop.
- `R6`, `cause2-margin`, `R3-in-view` and `R3-cap`, as radio-far.
- `search-clear` is INCONCLUSIVE because this scenario has no clear step.
- Docket T-432 (open, P3): a second Handoff during the 0-star bounty search. That row may show up as an extra INFO
  line here. It is not a check.

### brandish (R1 / R2 / Lost_Sight / range shrink)

- **Command:** `bash run-acc.sh brandish acc-brandish acc9-brandish`
- **Server:** `servers/cop-ai-brandish`, port 25613. The scenario is `scen/acc-brandish.json` (495 steps).
- **Judge:** `python -I judge-acc.py brandish E:/Programming/java/wt/_programme/runs/cop-ai-brandish--acc9-brandish`

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

- **Command:** `bash run-acc.sh brandish brandish acc9-brandish-orig`
- **Server:** `servers/cop-ai-brandish`, port 25613. The scenario is `scen/brandish.json` (281 steps, as
  `acc2-brandish-orig-c`). Run it after brandish.
- **Judge:** `python -I judge-acc.py brandish E:/Programming/java/wt/_programme/runs/cop-ai-brandish--acc9-brandish-orig`

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

- **Command:** `bash run-civ.sh civ-911 acc9-civ`
- **Server:** `servers/cop-ai-civ`, port 25614. The scenario is `scen/civ-911.json` (406 steps, about 6 minutes after
  boot). `run-civ.sh` now deploys `wt/gangland-0.21.1/target`, which is the merged build. Env `GL` overrides it.
- **Judge:** there is no script. Use `civ-README.md` "Pass criteria", which lists every check:
  - rows A (silent crime in front), B (behind), C (loud shot behind), D (intimidate + scared cooldown) and E (no
    witness, then a witness);
  - the all-run checks: no cop exists before each `-CRIME` marker, 10 modules, 0 exceptions.
- Yaws are compared modulo 360.
- `rc=1` means a spawn reply was missing, or W_D was still fleeing after 30 s. Either is a setup failure.

### handoff-lead (R5 lead, T-423 spawn between, R4 no live-position tip, R6)

- **Command:** `bash run-acc.sh handoff-lead handoff-lead acc9-handoff-lead`
- **Server:** `servers/cop-ai-handoff-lead`, port 25615. This is the new `run.sh` key. The scenario is
  `scen/handoff-lead.json` (248 steps, from `node gen-handoff.js`).
- **Judge:** `python -I judge-acc.py handoff E:/Programming/java/wt/_programme/runs/cop-ai-handoff-lead--acc9-handoff-lead`

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
  HANDOFF_LEAD. Each unit's track ends at its first PURSUING to RETURNING line (ACC9). There must be 12 or more probes; fewer is INCONCLUSIVE. Every unit falls into exactly one class, and
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

## Validation of the ACC9 judge on existing runs (2026-10-27)

Each run was judged with the ACC8 judge (before) and the ACC9 judge (after), with `python -I`. The run dirs are under
`E:/Programming/java/wt/_programme/runs/`. There are 17 runs: the 8 judged acc8 runs (civ has no judge script), acc5,
acc6 and acc7 spawn-dup-b, acc7 spawn-view, acc7 spawn-dup, acc7-handoff-lead and hl-env1..3. Every check not in the
table gave the same verdict before and after on all 17 runs (diffed). The other text changes are the new
`(N probes at level 0 not judged)` suffix on every `R3-count` line and the new fields on `R5-lead-go` and
`R5-lead-rows`.

The after pass used `--jar E:/Programming/java/wt/_programme/servers/cop-ai-spawn-dup/plugins/Gangland_Warfare/modules/cops-n-crooks-0.21.1.jar`
(md5 `3ea60b5b...`, the build-table jar). `wt/gangland-0.21.1/target` was being rebuilt at the time, and the judge reads
the radio pools from that jar by default.

| Run | Check | Before (ACC8 judge) | After (ACC9 judge) | Why |
|---|---|---|---|---|
| acc8-spawn-view | R6-station | PASS (0x of 0) | **INCONCLUSIVE** (0x of 0) | No SV-S unit (setup step #500 failed): nothing was judged. |
| acc8-spawn-view | OVERALL | PASS (10 PASS, 1 INCONCLUSIVE) | PASS (9 PASS, 2 INCONCLUSIVE) | R6-station only. |
| acc8-spawn-dup | R3-count | FAIL 2 (AFTER-3 4>3; AFTER-4 4>2) | **FAIL 1** (AFTER-3 4>3; 1 probe at level 0 not judged) | The hand-verified AFTER-2/3 FAIL stays (T-510). AFTER-4 is at level 0 after the escape, and the search keeps its cops (T-187). |
| acc5, acc6, acc7, acc8 spawn-dup-b | R3-count | PASS | PASS (3 probes at level 0 not judged) | Same verdict. |
| acc7-spawn-view, acc7-spawn-dup | R3-count | PASS | PASS (0 at level 0) | Same verdict. |
| acc8-handoff-lead | R5-lead-go | PASS (toward the lead 3; Tony#1004 157->154, Vince#1003 157->154) | PASS (toward the lead 3; Tony#1004 157->125, Vince#1003 157->124, Carl#1005 159->123) | Tony and Vince went RETURNING at +8 s. The old window also judged their walk home, which hid a 33 b approach behind a 3 b net change. |
| acc7-handoff-lead | R5-lead-go | FAIL (toward the lead 0; away 2) | FAIL (toward the lead 0; away 2: Lou#1004 178->237, Tina#1005 224->234) | T-423 behaviour FAIL on the old build, kept. |
| hl-env1 | R5-lead-go | FAIL (toward the box 1, still 2) | FAIL (toward the box 1: Linda#1004; away 1: Ann#1005 196->203; still 1) | Kept. Ann's window now ends at +14 s. |
| hl-env2 | R5-lead-go | FAIL (toward the lead 1, away 1) | FAIL (toward the lead 1, away 1: Linda#1004 184->212) | Kept. |
| hl-env3 | R5-lead-go | FAIL (toward the box 1: Sam#1005 33 deg) | FAIL (toward the box 1: Sam#1005 41 deg) | Kept. Sam's window now ends at +14 s. |

No hand-verified FAIL turned into PASS:
- acc8-spawn-dup `R3-count` stays FAIL on AFTER-3, which follows AFTER-2 (both 4 > 3 at level 2).
- acc5 and acc6 spawn-dup-b `R3-in-view` and acc7-spawn-dup `R3` are unchanged (still FAIL).
- `R5-lead-go` stays FAIL on acc7-handoff-lead and hl-env1..3.

No check went from PASS to FAIL. The one PASS that changed became INCONCLUSIVE: acc8-spawn-view `R6-station`, which
had judged nothing.

The spawn-view `poll` fix has not run live yet. Its first run is `acc9-spawn-view`.

## Docket rows

All ids are final. This round adds no plugin row. These rows are cited above:
- T-187: the post-escape search keeps its cops. This is why R3-count skips level 0.
- T-423: hand-off units head to the R5 lead and spawn between him and it. R5-lead-go and R5-spawn-between check it.
- T-509 (open): the spawn-between fallback logs no line.
- T-510 (open): a level drop never sends a POSTED or COMBAT surplus cop home. It is the acc8-spawn-dup R3-count FAIL.

The three ACC9 judge fixes and the spawn-view setup fix are harness rows, like T-490, T-494 and T-495. They were
reported to the orchestrator as NEW rows, to be filed.
