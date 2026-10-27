# Cops N Crooks 0.21.0: live reproduction of spawn-dup, radio-far and brandish

This is the live companion to `INVESTIGATION.md`, and it keeps that file's cause ids. Every run used sandbox Paper clones driven by mineflayer bots. Each run was recorded, then a separate judge pass assigned verdicts. I re-opened a sample of the cited lines in the on-disk files, and they match (list at the end). Nothing in this pass was built, edited in tracked source or committed. The owner's live server (`E:/Documents/Minecraft/Test Server`) was never started or touched.

**Verdict words**
- **reproduced**: the live log shows the mechanism the cause describes.
- **not reproduced**: the scenario exercised the cause and the bug did not appear. This happened for no cause.
- **inconclusive**: the scenario never reached the cause's precondition.

## Run directories and how they are cited

All run directories live under `E:/Programming/java/wt/_programme/runs/`. A citation reads `<tag> file:line`.

| Tag | Run dir | What it holds |
|---|---|---|
| SD-R1 | (lost) `cop-ai-spawn-dup--r1`, run 12:59-13:03 | Round 1 of the `spawn-dup` scenario. **Overwritten by round 2.** Its quoted lines survive only in the judge record below and cannot be re-checked. |
| SD-R2 | `cop-ai-spawn-dup--r1` (13:33-13:39) | Round 2, scenario `spawn-dup-b`: phases 2, 5 and P. Despite the name, this dir now holds round 2. |
| SD-ENV2 | `cop-ai-spawn-dup--envcheck2` | The env builder's preview run of `spawn-dup`, on the same jars. Used only as corroboration. |
| RF-ORIG | `cop-ai-radio-far--r1-orig` | Round 1 of the full `radio-far` scenario, copied aside before round 2. |
| RF-ROUTE | `cop-ai-radio-far--r1` (13:27-13:28) | Round 2, scenario `radio-far-route-r2`: tunnel and doorway variant. |
| RF-ENV | `cop-ai-radio-far--envcheck` | The env builder's preview run. It holds the only Route-line instance. |
| BR-R1 | `cop-ai-brandish--r1` | Round 1 of the full `brandish` scenario (Drawer = variant B, Runner = variant A). |
| BR-R2 | `cop-ai-brandish--r2` | Round 2, bots Nighter, Sneaker, Faller, Oov and Natural. Use only F-PERSIST and A-NATURAL; the night and sneak rows here are confounded. |
| BR-R2B | `cop-ai-brandish--r2b` | Round 2 rerun of D-NIGHT and D-SNEAK, with bots starting away from the cops. These rows are clean. The B-OOV row failed mechanically (rc=1). |
| BR-R2C | `cop-ai-brandish--r2c` | B-OOV only. It failed mechanically (rc=1, the expectLog missed the given). It holds no evidence. |

---

## 1. spawn-dup: cops keep spawning while a wanted player runs

**Symptom reproduced: yes.**

| Cause | Live verdict | Basis |
|---|---|---|
| 1 Leashed walkers are replaced but never removed | **reproduced** | SD-R1 and SD-R2 |
| E The evasion clock clears on a whole-squad leash | **reproduced once** (SD-R1 only; raw log overwritten). Inconclusive in SD-R2 | SD-R1 |
| 3 A leash refill gets no tip | **reproduced** | SD-R1 and SD-R2 (an A/B pair inside one chase) |
| 4 Queued units spawn with no re-check | **reproduced** (SD-R1 only; raw log overwritten). Inconclusive in SD-R2; SD-ENV2 shows the same order of events | SD-R1 |
| 5 `reengageReturning` pulls back walkers at any distance | **reproduced** | SD-R2 phase 5 |
| 2 The spawn area reaches the leash line; the station spawner is tried before the bias | **reproduced** (geometry built on purpose) | SD-R2 phase 2 |
| R Dispatch_En_Route on every batch | **reproduced** | SD-R1 and SD-R2 |
| P Post-escape search refills | **inconclusive** | not exercised |
| 6 Patrol refills instantly | **inconclusive** | not exercised |

### Cause 1: reproduced
- **SD-R2 phase 5, level 3 (target 4).**
  - `server.log:1128` [13:36:21]: Linda #1024 goes PURSUING to RETURNING. `:1151` DISPATCH count=1, then `:1217` UNIT Ann #1028 (her replacement).
  - `:1158`: Kate #1025 goes RETURNING. `:1173` DISPATCH, then `:1259` UNIT Frank #1029.
  - Probe P5-HOLD-5, `server.log:1293-1310` [13:36:40]: **6 rows against a target of 4**. Linda, Kate, Rosa and Marty are RETURNING at 101-144 blocks from Runner at (90.5,0.5).
  - `:1291` [13:36:39]: DISPATCH count=2 is queued while 4 walkers are still in the group. That is 6 listed plus 2 pending, against 4.
  - `:1324-1338` P5-CLEARED: 6 rows, all RETURNING.
- **SD-R2 phase P, level 1 (target 2).**
  - `server.log:1615-1632` [13:37:58]: 4 rows. Jim #1030 and Ann #1031 are RETURNING (since `:1529-1530`), next to the fresh Tina #1032 and Tina #1033.
  - `:1714` [13:38:12]: Carl #1034 RETU at 200 blocks and Pete #1035 RETU at 208 are listed next to the new pair.
- **SD-R1:** 7 rows against a target of 4 at T1F-HOLD-b (`server.log:711-717`). Hank was 108 blocks out and Eddie 171 (`:720, :727, :729`). The judge's walker table counted 27 RETURNING callsigns still listed 5-27 s after their leash.
- **Ceiling.** The group peaked at 7. It never exceeded Max_Per_Player 8, so the half of T-131 that says "Max_Per_Player stops being a cap" was not shown. On the flat map, walkers left the group only by reaching the spawner or through the Recycle_Seconds 12 no-route rule (11 `route walk failed` lines in SD-R1). That ends a walker after about 15-30 s instead of the 300 s Return cap. A map where walkers can walk home would show a larger pile-up.
- SD-R2 phase 2 is **not** cause-1 evidence. The hut spawner sits about 4 blocks from the station anchor, so a leashed unit despawns at once.

### Cause E: reproduced once
- **SD-R1** (judge-quoted; the raw file is overwritten):
  - `chat.txt:260` [13:02:08.976]: bossbar `★★☆☆☆ SEARCHING 11s`.
  - `server.log:1862-1864` [13:02:09]: Tina, Nick and Mike, the whole squad, go PURSUING to RETURNING. `:1865` HANDOFF.
  - `chat.txt:263` [13:02:09.476]: bossbar `★★☆☆☆ IN SIGHT`. The nearest cop was 90 blocks away (`:1874-1879`).
  - Between 13:01:49 and 13:02:11 there is no DISPATCH, so nothing was pending. `:1881` DISPATCH count=3, then `:1882` `EVASION Runner hold=enroute`.
  - The 11 s left of the SEARCHING countdown were thrown away.
- **Scope.** This was 1 of 8 HANDOFFs. The other 7 were held because a refill was already pending or a POSTED or GUARDING cop was still standing. So the live data does not support INVESTIGATION's "usually".
- **SD-R2.** No whole-squad leash happened while a SEARCHING countdown was running. In phase 2 the leash came in the same second as the spawn, before any Track existed. In phase P every leash came 0-3 s after a real sighting. So SD-R2 is inconclusive.

### Cause 3: reproduced
- **SD-R2, an A/B pair inside one chase:**
  - Linda leashed while Kate was still PURSUING (`server.log:1128`), so no HANDOFF fired. `:1151` DISPATCH bias=false, then `:1217` UNIT Ann #1028 `bias=false`. The evasion clock kept ticking after Ann arrived (`:1220-1242` EVASION speed lines), so she brought no tip-off.
  - Contrast: Kate leashed, a HANDOFF fired (`:1158-1159`), then `:1173` DISPATCH bias=true and `:1259` UNIT Frank `bias=true`. After Frank arrived the clock froze: no EVASION line, and `chat.txt:172` stayed at `SEARCHING 5s` until `:178`.
- **SD-R1, the harm instance:**
  - `server.log:806` [13:00:09]: DISPATCH bias=false. `:840` [13:00:13]: HANDOFF. `:885` [13:00:17]: UNIT Eddie #1010 `bias=false`.
  - `chat.txt:92`: Eddie radios "No visual from overwatch, check east!" while Runner was to his **west**. He had inherited the dead lead.

### Cause 4: reproduced once (raw log overwritten)
- **SD-R1:**
  - `server.log:1467` [13:01:23]: DISPATCH count=2 is queued at level 3.
  - `chat.txt:210` [13:01:32.477]: the level drops 3 to 2 (target 3).
  - `server.log:1560, :1563` [13:01:35]: UNIT Ann #1021 and Joe #1022 spawn with no re-check. That leaves 4-5 non-stranded cops against a target of 3.
- **SD-ENV2 corroborates the order of events** (I checked these lines myself; the overshoot was not measured there):
  - `server.log:1428` [12:46:37]: DISPATCH count=2 at level 3.
  - `chat.txt:194` [12:46:43.956]: "decreased ★★☆☆☆".
  - `server.log:1495, :1498` [12:46:47]: UNIT Ray #1019 and Tina #1020.
- **SD-R2:** the level never dropped from n to m>0. Both queues that were pending at a `wanted clear` were emptied, as INVESTIGATION's correction says.

### Cause 5: reproduced
- **SD-R2:**
  - `server.log:1334-1335` [13:36:46]: Marty is about 109 blocks out and Rosa about 144, both RETURNING.
  - `:1345`: `/glw wanted add 1`.
  - `:1346-1347`: Rosa and Marty go RETURNING to PURSUING.
  - `:1352-1353`, the same second: both go PURSUING to RETURNING again. Rosa stays put at (-52.13,-21.22) through `:1438`.
- The reset of `despawnTicks` is read from code; no log line shows it.

### Cause 2: reproduced (both halves)
- **Geometry, built on purpose (SD-R2 phase 2).**
  - The spawner sits on a pillar hut at (0,-46,74). Runner stands at (-27.5,-60,0.5).
  - Horizontal distance is 79.1, inside Spawner_Preference_Radius 80. Height difference is 14, inside Spawner_Max_Y_Diff 16. The 3D distance is 80.35, past Max_Distance 80.
- **Every unit leashed in the second it spawned.**
  - `server.log:513` UNIT Linda #1002 `hidden=true ... at=0,-46,74`, then `:524` PURSUING to RETURNING.
  - This repeated for 20 units, about every 10 s: `:513-527, 587-601, 659-673, 726-740`.
  - Each batch was re-dispatched with a fresh `[DISPATCH] 4 units en route` (`chat.txt:35,41,47,53`). No cop ever saw the bot.
- **Control.** At x=2.5 (3D distance 75.3) units from the same hut stayed PURSUING or POSTED (`server.log:809-822`).
- **Station tried before the bias.**
  - `:834` HANDOFF, then `:854` DISPATCH bias=true.
  - `:938` UNIT Lou #1022 `hidden=true bias=true ahead=false at=0,-46,74`: a biased unit placed at the station spawner, not ahead of the runner.
- The geometry is constructed. This proves the mechanism, not how often natural terrain hits it.

### Cause R: reproduced
- `cops.yml:288` sets `Dispatch_En_Route: 0`.
- **SD-R2:** 18 "units en route" lines over the whole run. Phase 2 alone had 7 in 70 s against a bot that no cop ever saw (`chat.txt:31,35,41,47,53,71,88`). Phase P had 6 in 82 s (`chat.txt:284,299,318,333,351,369`), each 1-2 s after a whole-squad leash.
- **SD-R1:** 20 lines in one continuous chase. Each pairs to the second with a `DISPATCH Runner ... station=Central Station` line.

### Cause P: inconclusive
- **SD-R1:** the stars never reached 0.
- **SD-R2** (`chat.txt:270`, 13:37:39.929): the 0-star window lasted only 2 s. It ended with a real sighting, when Jim was 23.7 blocks away in the open (`chat.txt:276`; `server.log:1518`). No DISPATCH or UNIT appeared at 0 stars.

### Cause 6: inconclusive
- After the beat cops disappeared (`PATROL ... cops=0`, `server.log:1054`), the bot never came within 64 blocks of the station.

---

## 2. radio-far: cops radio "go around him" while far away and out of sight

**Symptom reproduced: yes.**

| Cause | Live verdict | Basis |
|---|---|---|
| H Handoff (heading and addressee taken from the player) | **reproduced** | RF-ORIG and RF-ROUTE |
| 2 A tip counts as a sighting (flank, push and engage orders) | **reproduced** | RF-ORIG and RF-ROUTE |
| S Standoff starts on UNREACHABLE with no sight gate | **reproduced (trigger half)**; the ring centre on the live position was not observable | RF-ORIG and RF-ENV |
| 1 Route wording says "to him" while searching | **reproduced in the env-check run only** (RF-ENV). Inconclusive in RF-ORIG; RF-ROUTE reached ROUTE but no line was heard | RF-ENV |
| 3 Overwatch_Set with no sight check | **reproduced** | RF-ORIG |
| 4 Hearing range 64 against sight 40 | **reproduced** (config default and precondition, T-114) | RF-ENV and RF-ROUTE |

### Cause H: reproduced
- **RF-ORIG:**
  - `server.log:515` [12:59:27]: teleport to (0.5,110.5).
  - `:516-518`: all three cops go PURSUING to RETURNING.
  - `:519`: `HANDOFF Runner heading=south`.
  - `chat.txt:24` [12:59:28.312]: "Lost him heading south. Units ahead, pick him up.", 0.4 s after the hop.
  - The adjacent probe (`server.log:528-530`) puts every cop 114.6-123.0 blocks away. That is past Range 32 and Target_Range 64, and the bot carried no scanner. The only delivery route left is the addressee clause (`HandoffController.java:97`).
- **RF-ROUTE:** the same pattern. `server.log:525-528` HANDOFF, `chat.txt:27` [13:27:27.169]. The cops were 114.9-132.3 blocks away (`:537-539`).

### Cause 2: reproduced
- **RF-ORIG, sealed 9x9x6 stone shell** (`server.log:455`, light=0 from `:602`). Five times, a dispatch that replaced the previous pair was followed within 0.3 s by a flank or push order:
  - `:687` DISPATCH, then `chat.txt:71` "take his front!"
  - `:738`, then `chat.txt:79`
  - `:780`, then `chat.txt:86`
  - `:823`, then `chat.txt:95`
  - `:902`, then `chat.txt:118` "move in on him!"
- **Control:** `chat.txt:37` "Sweep the area!" comes when no tip lies inside the 1.5 s window.
- **RF-ROUTE:** `server.log:589` DISPATCH bias=true, then `chat.txt:35` [13:27:36.178] "move in on him!" and `chat.txt:37` "Moving to flank, north-east!". No line of sight to (3.5,203.5) was possible.
- **Inferred, not logged.** The recycle tip at `CopManager:949` writes no debug line. That attribution comes from pairs vanishing from the probes on the 12 s cadence. The biased-spawn path (`:879`) alone is enough for the verdict.

### Cause S: reproduced (trigger half)
- **RF-ORIG:**
  - `server.log:669` [12:59:53] `STANDOFF Runner start trigger=UNREACHABLE`, then `chat.txt:53-54` "Barricade! Perimeter up..." and "Step out in 45 seconds...". The last tip was 16 s old.
  - `server.log:958` [13:01:07] another UNREACHABLE start, then `chat.txt:136` "He's holed up, set the perimeter!". The last tip was 12 s old.
- **RF-ENV:** `server.log:737` [12:38:02] UNREACHABLE start, then `chat.txt:104`, with the nearest cop 42.9-43.1 blocks away.
- **The ring half was not observable:**
  - The 12:59:53 standoff posted nobody (no POSTED transition near `:669`).
  - At 13:01:07 a PerimeterController was already active, so `start()` skipped `post()`.
  - The other standoffs ended GONE within 3 s.

### Cause 1: reproduced (env-check run only)
- **RF-ENV:**
  - `chat.txt:81` [12:37:47.453]: the bot is in the sealed box.
  - `chat.txt:83` [12:37:49.637]: Linda #1008 says "Cutting round to him!".
  - `server.log:693`: `route walk failed 2 legs toward world 0,-60,110`. That is ROUTE aimed at the stale FAR point, about 90 blocks from the bot.
  - Every cop was 43-114 blocks away, and no sighting was fresh (`chat.txt:84`: SEARCHING had already started).
- **RF-ORIG:** the sealed box gave the planner no route (`server.log:597`), so ROUTE was never entered. Inconclusive.
- **RF-ROUTE:** ROUTE was entered (`server.log:612`), but no Route line was heard. Likely causes (inferred): radio gap and cooldown after three lines in 36-38 s, and a stale goal (see Unexpected).
- RF-ENV is a preview run on the same jars and scenario, so treat this verdict as single-instance.

### Cause 3: reproduced
- **RF-ORIG:** `chat.txt:122` [13:00:59.055]: Lieutenant Marksman Dave #1019 says "In the nest, 38 blocks south-east. Clear line on the suspect." This is from the Overwatch_Set pool.
- The bot was sealed in stone at light 0, so line of sight was impossible.
- The line came exactly three ack delays (3 x 1.25 s) after the 13:00:55.308 order burst (`chat.txt:118`). That is the `atPost` follow-up, gated by distance only (Dave at 36.8 blocks, then 16.0: `server.log:919, :941`).
- The "38" is his distance at spawn (`:907`), not a sight measurement.

### Cause 4: reproduced (config default)
- **RF-ENV:** `chat.txt:83, :86, :89` were received while every cop was 43-50 blocks away (`server.log:679, :704`). That is past Alert_Range 40 and inside Target_Range 64.
- **RF-ROUTE:** `chat.txt:35`. The speaker was 45.2 blocks away and the addressee 38.9 (`server.log:602, :604`), both past Range 32, so only the Target_Range clause delivers it.

---

## 3. brandish: a cop warns once, walks away after 5 s, and doesn't warn again

**Symptom reproduced: yes.** All six causes reproduced live.

| Cause | Live verdict | Basis |
|---|---|---|
| a 30 s cooldown counted from the warning start, shared across cops, cop walks off | **reproduced** | BR-R1, BR-R2 and BR-R2B |
| b Silent deadline outcomes (IGNORED, and COMPLIED out of view) | **reproduced** (both halves) | BR-R1, BR-R2 and BR-R2B |
| c Silent heat; star 1 at the third ignore | **reproduced** | BR-R1 and BR-R2 |
| d Darkness or sneaking shrinks the warning range at both gates | **reproduced** (dim-light band not exercised) | BR-R2B and BR-R2 |
| e Once wanted, beat cops report instead of warning | **reproduced** (first clause). The "only PATROL beat cops warn" clause is code-read only | BR-R1 and BR-R2 |
| f The 0-star description persists and needs 2 of 3 fields to change | **reproduced** (bounded at 170 s; quit and rejoin not tested) | BR-R2 |

### Cause a: reproduced
- **The cooldown counts from the start of the warning, not the verdict.**
  - BR-R1: `server.log:794` given [13:01:00], `:809` ignored [13:01:06], `:872` given [13:01:30]. The next warning came 30 s after the start but only 24 s after the verdict.
  - Same pattern in BR-R2: `:1357` given, `:1383` ignored, `:1484` given (30 s and 25 s).
  - Also BR-R2 `:690` to `:705` to `:770`, and BR-R2B `:811` to `:832` to `:930`.
  - Given-to-given gaps per player were never under 30 s: BR-R1 38, 31 and 30; BR-R2 34, 30, 36 and 30; BR-R2B 30.
- **The cooldown is shared across beat cops.** BR-R1 `chat.txt:113` [13:01:20.331]: Runner is pulled 2.1 blocks from Carl, who is in PATROL in daylight with his own warning 51 s old. Carl stayed 8.9-9.9 blocks away through 13:01:24 (`server.log:830-858`) and never warned. Sam warned the moment Sam's cooldown ended (`:872`).
- **The cooldown is per player, not global.** BR-R2: Nighter was warned at `:622` [13:24:54] and Sneaker at `:690` [13:25:19], 25 s apart.
- **The cop walks off.**
  - BR-R2: Jim went from 2.1 blocks to 37.6 within 18 s (`server.log:1226, :1247, :1271, :1303`). Nobody was in range when the cooldown ended (`:1327, :1335`), so the next warning slipped to 36 s.
  - The walk-off is not deterministic. In BR-R1 Carl drifted back to 3.7-5.2 blocks inside his cooldown (`:756, :778`).

### Cause b: reproduced
- **IGNORED is silent every time** (9 instances).
  - BR-R1, Runner: `server.log:726`, after `chat.txt:96` "Put that away!". Only CNCMARK lines follow up to `:102`.
  - BR-R1, Runner again: `:809`, with nothing in `chat.txt:105-109`.
  - BR-R1, Drawer: `:648`, with nothing in `chat.txt:76-82`.
  - The same holds for Nighter, Sneaker, Faller and Natural in BR-R2 and BR-R2B.
- **Out-of-view COMPLIED is silent, without needing the B-OOV row.** In three cases a pull left the still-armed bot outside the shrunken range of a stopped WARNING cop. The verdict was `complied` and no "Thanks, carry on." followed:
  - BR-R2 `:552`: night, 8.5 blocks against a range of 4.8.
  - BR-R2 `:705`: sneaking, 9.9 blocks against 8.4.
  - BR-R2B `:832`: sneaking, 9.9 blocks.
- "Thanks, carry on." has 0 hits in BR-R2 and BR-R2B chat. Its only occurrence anywhere is BR-R1 `chat.txt:47`, after a real put-away.

### Cause c: reproduced
- **No visible effect after two ignores.** BR-R1 `chat.txt:110-111` [13:01:15] shows `☆☆☆☆☆`. Faller in BR-R2 stays at `☆☆☆☆☆` seven times over 170 s after one ignore.
- **The third ignore gives star 1.**
  - BR-R1: `server.log:888` [13:01:35], then `chat.txt:122` subtitle "★☆☆☆☆ Brandishing a weapon near an officer", `:123` DISPATCH level 1, and `:125` bossbar "IN SIGHT". `server.log:891` DISPATCH count=2.
  - Repeated in BR-R2: `server.log:1511`, `chat.txt:389-392`, `server.log:1514`.
- **The arithmetic matches the config.** `wanted.yml:41,50` gives 25 x 1.5 = 37.5 heat per ignore. Two ignores are 75 (under 100); three are 112.5 (at least 100).
- **Heat is per player.** Drawer's earlier ignore did not count toward Runner.

### Cause d: reproduced
- **Night.**
  - BR-R2B `server.log:581` shows `SIGHT Nighter factor=0.40 light=4`, so the range is 4.8 blocks.
  - Eight pulls put the cooldown-free bot 6-10.6 blocks from the cops over 39 s. No warning.
  - The warning `:731` [13:35:07] fired only once Marty #1001 stopped about 3.35 blocks away (`:740` against `:729`).
  - BR-R2 `:539` likewise fired with the cop at about 3.5 blocks.
- **Daylight control.** BR-R2 `:1357` fired at exactly 12.0 blocks (`:1362`).
- **Sneak.**
  - BR-R2B `:802` shows `sneak=0.70`, so the range is 8.4 blocks.
  - At 9.9 blocks there was no warning (`:807, :809`). The warning `:811` came when the cop was at 8.1.
- **The deadline gate shrinks too.** The out-of-view COMPLIED verdicts under cause b are the deadline-side effect.
- The dim-light band (factor 0.7) was not exercised.

### Cause e: reproduced (first clause)
- **A wanted player is reported, not warned.** BR-R1 `chat.txt:129` [13:01:40.557]: "[RADIO] Officer Sam #1000: Patrol here, I see the suspect at the area." Patrol_Sighting is reachable only from the wanted branch, which runs before `canWarn`. A second one came at `chat.txt:163` after Sam's cooldown had lapsed.
- **No further warnings while wanted.** After `server.log:872` there is no `result=given`, although Runner was pulled beside cops six more times.
- **Caveat.** Runner was cuffed and released between 13:01:56 and 13:02:16, and his held item after release was not verified.
- **Not discriminated.** "Only PATROL beat cops ever warn" rests on code: `begin()` has a single caller.

### Cause f: reproduced
- BR-R2 `server.log:845` [13:26:19]: `SUSPECT Faller` at 0 stars. The stars stayed at 0 throughout (`chat.txt:170..264`).
- After about 170 s, an iron helmet (one field changed) produced no DISGUISE change.
- A leather chestplate (the second field) followed at once by `server.log:1081` [13:29:09] `DISGUISE Faller state=mismatch`.
- A mismatch can only be logged against a stored, non-null description. So the description survived at least 170 s, and recognition needed two of three fields to change.

---

## Unexpected (not predicted by INVESTIGATION.md)

Candidates for new `triage/` rows are marked **[triage]**. I only list them here. Per this task's scope, no docket file was edited.

### spawn-dup
1. **[triage] A sibling spawn path that fix 5 does not cover.**
   - In open terrain every refill came from `spawnNearPlayer`, which calls Keystone `EntitySpawner.findClosestSpawnerLocation`. That fallback uses the same 80-block radius, has no sight test and ignores the bias.
   - SD-R1 had 29 of 29 UNIT lines with `hidden=false`.
   - Nick and Mike spawned at the station spawner 79.85 blocks out. They leashed 8 s later, and that triggered a 3-unit re-dispatch. Biased units Rosa and Tina were also placed at the spawner.
   - In SD-R2 phases 5 and P, the fallback placed cops 22-49 blocks from the player in plain view, so refills visibly pop in (e.g. `server.log:1038, 1217, 1601, 1702`).
2. **[triage] The HUD shows "IN SIGHT" for the OFF state.** `StarCard.barTitle`'s default branch renders an evasion `clear()` as `IN SIGHT`. Examples: SD-R1 `chat.txt:263` with the nearest cop 90 blocks away; SD-R2 `chat.txt:29` and `:61` while no cop ever stood; RF-ROUTE `chat.txt:76` while sealed. This also hides cause E from the player.
3. **[triage] PendingUnit captures the hand-off bias when it is queued.** A unit queued before a HANDOFF spawns untipped even while the bias is active. SD-R1: queued at `:806`, HANDOFF at `:840`, UNIT `bias=false` at `:885`.
4. **[triage] POSTED perimeter cops are exempt from the 80-block leash and stay in the count.** SD-R2 shows them at 101-144 blocks (`:1181-1267`).
   - When the perimeter ended on CONTACT they went PURSUING and leashed one second later (`:1285-1290`), which triggered a count=2 refill (`:1291`).
   - HANDOFF also fires when only POSTED cops remain (`:832-834`, `:1158-1159`).
5. **Returning walkers fail to route home on open flat ground.** SD-R2 has 14 `route walk failed 2 legs` lines. Rosa stood still at (-52,-21) for 23 s (`:1209-1438`). This cuts both ways: it keeps walkers alive (amplifying cause 1), and the no-route Recycle rule caps them at about 15 s (understating cause 1).
6. **The refill loop starves the evasion clock.** In SD-R2 phase P, a 1-star bot teleporting 180 blocks every 3 s kept his star for 90 s. Each fresh biased pair sighted him, leashed, and re-armed the 12 s en-route hold (`:1539, 1634, 1760, 1840, 1931, 2032`).
7. **[triage] Beat cops vanish with no log line.** Sue #1000 and Linda #1001 were last probed at `server.log:1025-1026`. Then `:1054` shows `PATROL ... cops=0` with no transition line.
8. **Callsign names are reused with new ids** (Linda #1001/#1002/#1024/#1039). To a player this reads as the same cop respawning.
9. **The sandbox has no jail.** `JailIntakeService No non-full jail available ... releasing` caused cuff, release and re-dispatch cycles in SD-R1 and BR-R1. This is environmental.
10. **Harness counting is off by one.** The runner summary said 21 DISPATCH and 30 UNIT; the raw logs have 20 and 29.

### radio-far
11. **[triage] The hunt continues at 0 stars and after a full clear.**
    - RF-ORIG: after "Escaped" (`chat.txt:60-69`), dispatches `server.log:687, :738, :780` still produced flank orders at 0 stars.
    - After `/glw wanted clear` (`:810`), `glw cop list` still listed Joe and Tina (`:815-817`). DISPATCH `:823` spawned Pete #1014 and Mike #1015, who radioed "take his right!" (`chat.txt:95`).
    - The 0-star part may be the intended bounty search (cause P). The post-clear part should not happen.
12. **[triage] Stale bias reuse sets a stale search centre.**
    - At GO-ROOM, `HandoffController.java:86` suppressed a second HANDOFF because a bias was still active. The biased dispatch (RF-ROUTE `server.log:589`) then tipped the squad with the FAR spot (0.5,110.5), about 93 blocks from the bot.
    - The only route search went there (`:612`), and the "ahead" units spawned about 130 blocks past their own goal.
    - So `CopManager:879` both fakes a sighting and seeds a stale `lastKnown`. INVESTIGATION fix 2 (`reportTip`) covers the first, not the second.
13. **[triage] The 2-star standoff posted nobody.** RF-ORIG `server.log:669` shows no POSTED transitions, and the cops stood at the same coordinates before and after (`:660-662`, `:684-686`). Yet the leader radioed "Perimeter up".
14. **[triage] A PerimeterController ring formed on the sealed live position** (RF-ORIG `server.log:855-857`; posts at 31.4-31.9 blocks around (0.5,200.5)) with no sighting. This is a sibling of cause S in a controller INVESTIGATION does not list.
15. **[triage] The radio text reads "Suspect is suspect, on foot."** (RF-ORIG `chat.txt:19, 84, 215`). An unknown description renders as `suspect`, inside a template that already starts with "Suspect is".
16. **The peak values disagree.** RF-ORIG `server.log:673-674` has GETAWAY peak=0 against PURSUIT_REPORT peak=2, and `:1149-1150` has 0 against 3. This is either a different metric or a bug; it needs a look.
17. **Retreat and cover lines against a sealed, unarmed suspect** (RF-ORIG `chat.txt:138-140, 196-197`).
18. **Removals and recycles are not logged.** The `CopManager` recycle path (`:918-950`) writes no debug line, so recycle attributions can only be inferred.

### brandish
19. **[triage] Inferred from code, not exercised with a drawn weapon.** `DisguiseFilter` sits inside `CopPerception.acquires`, which both PatrolBehavior and WarningBehavior use. A 0-star player in `state=mismatch` (BR-R2 `:1081`) therefore cannot be acquired by any beat cop, so he is never warned again until a WantedEndEvent that never fires at 0 stars. This compounds cause f and the owner's "doesn't warn again".
20. **The night warnings fired at about 3.4 blocks, not 8.5.** The runner summary credited the BR-R2B night warning to the control pull. It actually fired before that pull (13:35:08), with the cop walking in to about 3.35 blocks. Both are consistent with the 4.8 range.
21. **The warning cop does not alternate.** In BR-R1 Sam warned twice in a row, and in BR-R2 Jim warned three times. The warner is whichever cop is in range when the per-player cooldown ends.
22. **Environmental, BR-R2B boot only:** SQLite `Failed to delete old native lib` NoSuchFileException (`server.log:259-311`). It had no effect on the run.

---

## Environment

### Jars
These are md5 values recomputed on 2026-10-26. The env builder states the Gangland and Keystone jars match `_programme/cnc-tdd/deploy-0.21.0/deployed.md5`, which is what the live server runs.

| Jar | Path (under `E:/Programming/java/wt/`) | md5 |
|---|---|---|
| Gangland core 0.21.0 | `cnc-0.21.0/target/gangland_warfare-0.21.0.jar` | `1f8343965ef4a5f9459b71786bba4be4` |
| cops-n-crooks | `cnc-0.21.0/target/modules/cops-n-crooks-0.21.0.jar` | `f4e3dcf9e0c8080dc0cf421fb86f563a` |
| civilians | `.../gangland-civilians-0.21.0.jar` | `d8ee12a3f4b2b902318854c20aee2143` |
| gadget | `.../gangland-gadget-0.21.0.jar` | `8d45a80cf14be55c94c5c4e7490cca61` |
| gang | `.../gangland-gang-0.21.0.jar` | `c7b6479d4c3fc844c7ec5875fa219ad9` |
| gang-fights | `.../gangland-gang-fights-0.21.0.jar` | `c2624b345d5b0d5aab5873ec9aaaed1c` |
| healthbars | `.../gangland-healthbars-0.21.0.jar` | `ce8eb08401ee3f4fe0395e4c24742f19` |
| lootchest | `.../gangland-lootchest-0.21.0.jar` | `7dedfdb3fe0b168e9758e0073846d01c` |
| mail | `.../gangland-mail-0.21.0.jar` | `b217055e175c93246689ad6b5c21fe57` |
| npc-shops | `.../gangland-npc-shops-0.21.0.jar` | `b87f82d6fc261c0237afa5025962de09` |
| turf | `.../gangland-turf-0.21.0.jar` | `79634debd40f1de42d16de78b209071a` |
| Keystone 1.15.3 (source 4945a1788) | `keystone-1.15.3/keystone-plugin/target/Keystone-1.15.3.jar` | `4daaa76d4b3d4052d4beebec3b90737e` |
| Bartizan 0.6.0 (worktree 695ef355a) | `bartizan-0.6.0/bartizan-plugin/target/Bartizan-0.6.0.jar` | `ca8e9b2df5ed2cb45081164c79f8ebae` |

- **Source.** The worktree `cnc-0.21.0` HEAD is ea59ffaee. It is tree-equal to master a14bcfbcb for every code module and `pom.xml`, the tree is clean, and the jars were built 2026-10-24 15:51.
- **Bartizan.** The sandboxes run 0.6.0; the live server runs 0.6.1. This is the one deliberate divergence, and it matches the newest cnc-integ prep (cnc21f2 acc-r3).
- **From `servers/base`:** `paper-1.21.11.jar` (Paper 1.21.11-129), Citizens-2.0.43-b4246, ViaVersion and ViaBackwards 5.12.0-SNAPSHOT, PlaceholderAPI 2.12.3. JDK 26.0.2.1, bot client 26.1 through ViaVersion.

### Servers
Each is a fresh clone of `servers/base` on every `run.sh`. `clone.sh` and `prep.sh` refuse any path outside `servers/cop-ai-*`.

| Key | Server | Port |
|---|---|---|
| spawn-dup | `E:/Programming/java/wt/_programme/servers/cop-ai-spawn-dup` | 25611 |
| radio-far | `.../servers/cop-ai-radio-far` | 25612 |
| brandish | `.../servers/cop-ai-brandish` | 25613 |

### Config overrides
All CnC YAML is shipped as-is: `cops.yml`, `wanted.yml`, `cop_roles.yml` and `cop_radio_messages.yml` have no edits.
- **`server.properties`:** `level-type=flat`, `generate-structures=false`, `view-distance=16` (the live value; base has 6, which would release stranded walkers through chunk unload), `simulation-distance=10`.
- **`settings.yml`:** `Debug.Enabled: true`, `Debug.Modules: ["Cops N Crooks","Gangland"]`, respawn after 3 s.
- **World rules set by each scenario:** `spawn_mobs` and `spawn_monsters` false, `advance_time` false, `time set 6000` (brandish D-NIGHT uses 18000), clear weather. Bots get Resistance V and Saturation.
- **Built structures:**
  - spawn-dup: station at (0,-61,70) and spawner at (0,-60,74). spawn-dup-b adds a pillar hut at (0,-46,74) and a box at x 86..94.
  - radio-far: a 9x9x6 hollow stone box at x -4..4, z 196..204. radio-far-route-r2 adds a doorway at (0,196) and a tunnel x -1..1, z 166..195.
  - brandish: Northside Station at (100,-61,40) and district Docks.

### Exact commands to re-run
Run from Git Bash. The third argument is the run tag, and reusing a tag **overwrites** that run dir: that is how SD-R1 was lost. Use new tags, e.g. `o1`.

```bash
L="E:/Programming/java/Gangland Warfare [Cubed-GTA recoded]/brainstorming/cnc-cop-ai-2026-10-26/live"
cd "$L" && node mk-runner.js && node gen-scen.js && node gen-route.js && node gen-r2.js && node gen-r2b.js && node gen-r2c.js   # regenerate run-copai.js and scen/*.json (already present)
bash "$L/run.sh" spawn-dup spawn-dup          o1   # SD-R1 equivalent (corridor at 3 stars)
bash "$L/run.sh" spawn-dup spawn-dup-b        o2   # SD-R2: phases 2 (pillar hut), 5 (re-engage), P (post-escape)
bash "$L/run.sh" radio-far radio-far          o1   # RF-ORIG equivalent (sealed box, WANTED3 block)
bash "$L/run.sh" radio-far radio-far-route-r2 o2   # RF-ROUTE (tunnel and doorway)
bash "$L/run.sh" brandish  brandish           o1   # BR-R1 (Drawer variant B, Runner variant A)
bash "$L/run.sh" brandish  brandish-r2        o2   # BR-R2 (use F-PERSIST and A-NATURAL)
bash "$L/run.sh" brandish  brandish-r2b       o3   # BR-R2B (D-NIGHT and D-SNEAK clean; B-OOV fails, see below)
# outputs: E:/Programming/java/wt/_programme/runs/cop-ai-<key>--<tag>/{server.log,chat.txt,steps.json,summary.txt}
python -I "$L/judge-spawn-probe.py"  <run>/server.log   # per-probe squad and distance table (SPAWN markers)
python -I "$L/judge-spawnb-probe.py" <run>/server.log   # same, for SPAWNB markers
python -I "$L/judge-spawn-walkers.py" <run>/server.log  # RETURNING walker lifetimes
```

- The three keys can run in parallel: separate dirs and ports, harness slot limit 5, `-Xmx2G` each. Fresh-clone boots take 16-30 s.
- `scen/brandish-r2c.json` (B-OOV) fails because its `expectLog` step searches only from its own start. Set `"since":"start"` on that step before re-running it.
- For BR-R2B's B-OOV row, the pull filter `distance=..60` is too short from z=90.
- rc=1 means a setup or harness expect failed, never "bug present".

---

## Fix-readiness

### Confirmed live, ready to fix
- **spawn-dup:** 1, 3, 5, 2 (mechanism, built geometry) and R. Fix 1 and fix 5 will be verifiable with `spawn-dup-b` phases 5 and 2.
- **radio-far:** H, 2, S (trigger half), 3, and 4 (needs an owner ruling, T-114).
- **brandish:** a, b, c, d, e (first clause) and f. All fixes still wait on owner decisions 1-3 in INVESTIGATION.md.

### Confirmed live once, raw evidence needs re-capture before or with the fix
- **spawn-dup E.** One clean instance in SD-R1, whose raw log is overwritten; it was 1 of 8 HANDOFFs. Re-run `spawn-dup` with a new tag to re-attach evidence. A red unit test (`EvasionClock` RETURNING-only plus active bias keeps its Track) is the better gate anyway.
- **spawn-dup 4.** One instance in SD-R1 (overwritten). SD-ENV2 `server.log:1428` / `chat.txt:194` / `server.log:1495-1498` repeats the event order, but the overshoot was not re-measured there.
- **radio-far 1.** Only RF-ENV, a preview run. The two judged rounds did not produce a Route line.

### Still only code-read
- **spawn-dup P and 6.** Not exercised. The 0-star window in SD-R2 lasted 2 s, and the bot never came within 64 blocks of a station with missing beat cops.
- **radio-far S, ring-centring half.** Not observable: no standoff posted, or a perimeter was already active.
- **brandish e, second clause** ("only PATROL beat cops can warn"), and **f beyond 170 s** (quit and rejoin, longer expiry).
- **brandish d, dim-light band** (factor 0.7).
- **The Max_Per_Player cap half of T-131.** The group peaked at 7 against a cap of 8; it needs a map with walkable routes home.

### Docket follow-up (not done here)
- T-131 and T-149 (cause 1) and T-148 (cause 3) now have live confirmation and can carry these run refs.
- The NEW rows INVESTIGATION lists, plus the **[triage]** items above (1, 2, 3, 4, 7, 11, 12, 13, 14, 15, 19), should go to `triage/<slug>.txt`, followed by a docket rebuild.

## Verification of this report
I re-opened these cited lines in the on-disk files, and each matches the quoted text:
- RF-ORIG `server.log:519, :669, :958`; RF-ORIG `chat.txt:122`
- RF-ENV `chat.txt:83`
- RF-ROUTE `server.log:612`
- SD-R2 `server.log:513, :524, :1034, :1291, :1346-1353`
- SD-ENV2 `server.log:1428, :1495, :1498`; SD-ENV2 `chat.txt:194`
- BR-R1 `server.log:794, :872, :888`
- BR-R2 `server.log:1081`
- BR-R2B `server.log:581, :731, :811`

The jar md5s were recomputed. SD-R1's lines could not be re-checked because that run dir was overwritten by round 2. All other line refs are from the judge passes.
