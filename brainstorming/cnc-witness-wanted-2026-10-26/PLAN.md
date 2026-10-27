# Witness-based wanted: Cops N Crooks 0.21.2

Owner request, 2026-10-26: a cop kill should not give stars at once. Stars come when a cop sees you kill a cop or a
civilian, or when a cop stays silent too long and dispatch sends backup to his last reported spot. Stars should rise
with the number of people who saw the crime, not at random. Killing every cop must not clear the stars; only a proper
escape does.

This plan obeys the earlier rulings:
- **R4:** a tip is a last-known spot, never the live position.
- **Civilian witnesses** are deterministic and show visible feedback. They see in a 180° cone, and a loud crime turns them toward the noise.
- **An unseen Kill_Civilian needs a witness.**
- **No randomness in outcomes.**

Read-only design. Nothing was edited or committed. The graph was fresh (graph.json 2026-10-26 11:20, newer than the
last commit, 2026-10-24); `graphify query` ran first, then the raw files.

**Base.** 0.21.2 is built on 0.21.1 with **two lanes merged into it first**:
- the spawn/radio lane `E:/Programming/java/wt/gangland-0.21.1` (HEAD a0b016f24, spawn r4);
- the civilian 911 lane `E:/Programming/java/wt/gl-0.21.1-civ`, committed through b8ca509f1 on top of eda965e2a, ff4016e13, 691175991 and 440d73c87. It does **not** contain a0b016f24.

Every line number below is from the worktree named by its prefix.

**Path prefixes**
- `cnc/` = `E:/Programming/java/wt/gangland-0.21.1/gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/`
- `CIV/` = `E:/Programming/java/wt/gl-0.21.1-civ/gangland-features/` (`CIV/cnc/...` is cops-n-crooks in that tree)
- `res/` = `.../cops-n-crooks/src/main/resources/copsncrooks/`
- `KS/` = `E:/Programming/java/wt/keystone-1.15.4/keystone-npc/src/main/java/org/luckyraven/keystone/npc/`

**No Keystone or gangland-api change is needed, and `Host_Api` is not bumped.**
- `CrimeCommittedEvent` already carries `seenByCop` and `witnesses`.
- Keystone 1.15.4 already has `NpcSquad.memberDown`/`remove`/`reportTip`/`isInContact`/`lastKnownLocation` (`KS/NpcSquad.java:139-157,189-217,271`).
- It also has `AbstractNpc.isValid`/`canSee`/`navigateTo` (`KS/AbstractNpc.java:344-345,502-505,546-549`).
- Every event touched below (`CivilianCalledInEvent`, `CopDeathEvent`) is a module event.

## 0. The chain in one picture

```
crime -> CopCrimeReports.open(killer, crime, at, victimId?)
  |- crime not in Wanted.Witnesses.Crimes ----------> commit now (admin opt-out; B1/B2/B6 stay fixed)
  |- >=1 cop EYEWITNESS -> REPORT: a named, glowing reporter; it lands at +Report_Seconds (3 s)
  |                          '- every eyewitness dead first -> "call never went out" (cop kill: falls to SILENCE)
  |- else civilian eyewitnesses -> 911: the existing CIV call, Call_Seconds 8; viewers = caller + watchers
  '- else -> "Nobody saw that." (Kill_Cop: SILENCE below)
cop death with a player killer, gated, Check_In on, no cop saw the killer:
  |- a cop saw the BODY fall -> Man_Down now (today's radio), SCENE at the body
  '- nobody saw it -> SILENT unit: no radio, his slot stays "filled", CopDeathEvent(silent)
        lastReportAt + Check_In_Seconds -> dispatch "Radio_Check"
        lastReportAt + Silent_Seconds   -> dispatch "Officer_Missing": casualty, backup biased to lastReport,
                                           exact tip there, SCENE
SCENE (Scene_Seconds): a cop sees the killer within Scene_Radius -> commit (seen); timeout -> "Scene_Clear", no stars
Pursuit_Presumed: the dead cop's group was hunting this killer -> commit Kill_Cop(false, 1) at Officer_Missing
```

**Every outcome comes from a fixed timer or a geometry test.** The module still uses random numbers in a few places, and
none of them decides a star, a report or a witness:
- radio line picks;
- cop names and weapon pools;
- the beat-cop wander;
- **the angle on the hidden spawn ring** (`KS/entity/EntitySpawner.java:260`). It decides *where* out of sight a unit appears, which the player can feel but cannot see. Tests pin the spawn side through `SpawnBias`, not the exact spot.

## 1. Report delay and witnesses

### 1.1 Where each crime is booked

**Kill_Cop moves to the cop's death event.**
- The POLICE branch at `cnc/seam/HeatWantedTracker.java:99-100` returns without committing, as `:103` already does for managed civilians.
- `CopListener.onCopDeath` (`cnc/listener/detainment/CopListener.java:246-275`) calls `reports.open(killer, KILL_COP, body, cop)`.
- The killer is `getKiller()` (`:253`). When that is null (BZ-EV-20, Bartizan kills), the code falls back to a 5 s last-attacker map, filled from `CopManager.onCopAttackedAlert` (`cnc/npc/police/CopManager.java:381`).
- **Fixes B2 by construction.** A hit that `shieldBlock` (`CopListener:183-192`) absorbs never kills, so it is never booked. Core-vs-module listener order no longer matters.

**When no player can be blamed** (the killer and the fallback are both null: fall, fire, lava, an NPC, a car), today's behaviour stays:
- `memberDown` runs;
- no Kill_Cop is booked;
- no scene opens;
- no slot is held;
- there is no addressee, so no Radio_Check.

**Assault_Cop waits one tick.**
- The hook at `cnc/config/HeatModuleConfig.java:46-51` schedules `reportAssault` for the next tick.
- It skips the call if `!entity.isValid() || entity.isDead()`.
- `HeatLedger.reportAssault` keeps its per-victim throttle, but it calls `reports.open(attacker, ASSAULT_COP, attacker loc, cop)` instead of committing.
- **Fixes B1:** a lethal blow books only Kill_Cop.
- **Fixes B3:** by the next tick the hit cop is in COMBAT with the attacker (`CopManager:423-428`), and his sighting has been reported (`:400-404`).

**`at` is the scene for every kill.**
- `CIV/gangland-civilians/.../listener/npc/CivilianDeathRewardListener.java:52` commits at the victim's location. That matches `HeatWantedTracker:106`.
- **Fixes B6.**
- The civilian sight test still targets the criminal (`CIV/.../witness/WitnessService.java` `witness`).

### 1.2 Who counts as a cop eyewitness (`CopCrimeReports.eyes`)

A cop counts only when **all three** hold:

1. **He is valid and is not the victim.**
2. **He can see the criminal.** The check is `perception.sees(cop, killer, Alert_Range)` (`cnc/npc/police/perception/CopPerception.java:53`; range 40, `res/cops.yml:79-81`).
   - It uses `sees`, not `acquires` (`:72-74`): a disguise stops a lock-on, but it does not hide a murder done in front of him.
   - **Close-range floor.** `sees` shrinks the range by the darkness, crowd and sneak factors (`CopPerception.java:45-55`). So a cop also counts when he is within `Witness_Floor` (6 blocks) and has a clear line of sight (`AbstractNpc.canSee`). A cop two blocks away at night is a witness.
3. **One of these is true:**
   - **Engaged:** his target is the killer and he is in COMBAT or PURSUING. A hit cop witnesses his own assault.
   - **Loud:** the crime is in `Cops.Loud_Crimes`, or the killer's last shot is within `Loud_Shot_Seconds`.
     - There is **one loudness rule for cops and civilians**: the CIV shot clock (`WitnessService.heardShot(shooter, weaponType)`, `CIV/gangland-civilians/.../witness/WitnessService.java:145`, filtered by `Witness.Loud_Weapon_Types`), exposed as a new public `loudWithin(player, ms)`.
     - A melee swing is silent for both. There is no `SightModel` change.
     - A loud crime first turns the cop toward `at`, as civilians turn.
   - **In view:** `WitnessService.inView(cop eye, killer loc, Cop_View_Angle)` holds (`WitnessService.java:154`, package-private today; it becomes public). cops-n-crooks already declares `Depends: [civilians]`.

**Who is checked:** the existing `CopManager.liveCops()` (`CIV/cnc/npc/police/CopManager.java:448`), plus `PatrolManager.beatCops()`. This is the list the CIV `copSees` already builds (`CIV/cnc/listener/wanted/WitnessGateListener.java:82-83`). No `allCops()` is added.

### 1.3 The witness gate (supersedes the CIV lane's bypasses)

`CIV/cnc/listener/wanted/WitnessGateListener.java:60-75`:
- **Both bypasses go:**
  - `:69` (the squad's fresh sighting);
  - `:70` (`copSees`, added in b8ca509f1).
- An unreported gated crime is cancelled. Instead of calling `witnesses.witness(...)`, the gate calls `reports.open(player, crime, at, null)`.
- **Reported crimes still pass at `:66`** (`seenByCop || witnesses > 0`).
- `open` looks for cop eyewitnesses first. **Only when no cop saw it** does it call the CIV `witness()`. Each crime gets one reporter, and a cop report beats a civilian call.

**Deviation from 0.21.1.**
- A gated crime in front of a cop used to count in the same tick (`CIV/.../documentation/migration-0.21.1.md:163`; the `wanted.yml:408` comment "a cop who sees you commit one counts it at once").
- It now lands after `Report_Seconds`.
- Both texts and the new b8ca509f1 `WitnessGateListenerTest` cases are superseded and must be rewritten.

### 1.4 A cop report (`Case`, kind COP)

**What a case holds:**
- `killer`, `crimeId`, `at` and an optional `victimId`;
- the witnesses, fixed at the moment of the crime;
- the `reporter`;
- `landsAt = now + Report_Seconds`.

**The reporter:**
- He is the nearest witness, and he glows (the civilian caller's pattern).
- If he dies, the next living witness takes over and keeps the same `landsAt`.

**When the case lands** (at `landsAt`, with at least one witness still valid, and the killer online and not restrained):
1. `ledger.commitReported(killer, ReportKind.COP, () -> crimes.commit(killer, crimeId, at, true, liveWitnesses))`.
   - The 0.21.1 `reporting` UUID (`CIV/cnc/wanted/heat/HeatLedger.java:52-53,88-93,128`) grows from a boolean into a kind: COP, CIVILIAN, DISPATCH or SCENE.
   - It is stored on `CrimeRecord` and drives the chat cause line (§5).
   - There is no second side channel.
2. `squad.reportTip(at)`, right after the commit. It overwrites the live-position tip `onWantedStart` sets (`cnc/npc/police/CopManager.java:166`) in the same tick, as `NineOneOneListener` already does (`CIV/cnc/listener/wanted/NineOneOneListener.java:61`). This is the R4 fix for late reports.
3. `copRadio.dispatchAt(at, killer, group, "Officer_Report", extras)` (§2.4).
   - `CopRadio.dispatch` (`cnc/npc/police/radio/CopRadio.java:139-147`) takes `%place%` *and* the heard-around point from `target.getLocation()`, which is the live position.
   - `dispatchAt(Location place, Player addressee, @Nullable CopGroup group, key, extras)` is the same call with `%place% = placeOf(place)`. The heard-around point stays at the addressee: he must hear the line, and hearing is not a tip. With no group it speaks through a throwaway `NpcSquad` (§2.4).
   - `Dispatch_Wanted` (`CopManager:156`) also gets `place = placeOf(at)` when the stars came from a case.

**If every witness dies first:** the action bar shows `Report_Cut`. For a Kill_Cop, the dead cop's silence (§2) still runs.

**Superseding:** a Kill_Cop for victim V replaces a pending Assault_Cop case for V and keeps that case's deadline.

**Killer state changes:**
- **Dies:** his cases are dropped (death resets wanted, T-181).
- **Cuffed, arrested or captured** (`CuffedEvent`, `WantedEnd(ARREST)`, a hunter capture): his cases are dropped, because the arrest settles him. Landing also re-checks `DetainmentService.isRestrained` and drops the case if he is.
- **Downed:** cases are kept. A downed player ends in a cuff, a death or a recovery, and each of those already resolves the case.

**Quit** (OD-5): a case whose witnesses are alive at quit is **deferred**. It goes to a `Deferred` map keyed by UUID and is committed on his next join, within `Defer_Minutes` (10). Nothing is committed during `PlayerQuitEvent`, so no group is built for an offline player and nothing races the user-data save or the `HeatListener` clear.
- The CIV lane follows the same rule. `WitnessService.onCriminalQuit` (`CIV/.../WitnessService.java:397`) only stops cancelling, so the call keeps running in gangland-civilians and its event still fires. The offline early-out at `NineOneOneListener:49` hands `(uuid, crimeId, at, viewers)` to `CopCrimeReports.defer(...)` instead of returning. The `Deferred` map lives in cops-n-crooks.
- The join commit runs **one tick after** `PlayerJoinEvent`, never inside it, so `onWantedStart`'s group and spawn work sees a fully loaded player.

**Reload and shutdown:** `CopCrimeReports` implements `BeanLifecycle`.
- `reloadBeans` restarts its 10-tick `Timer` (`start(false)`) and keeps its maps.
- `shutdownBeans` cancels it and drops every case, silence, scene and deferred entry.
- Known limit: a restart inside a report window forgives that crime. `ponytail:` persist the deferred map if owners report abuse.

### 1.5 Civilian reports (the CIV lane plus a viewer count)

- `witness()` already collects every civilian who saw the crime and passes the watchers to `startCall`.
- **New:** `CivilianCalledInEvent` gains `int viewers` (the caller plus the watchers still valid at landing). The old constructor passes 1.
- `NineOneOneListener` (`CIV/cnc/listener/wanted/NineOneOneListener.java:53-54`) commits with `witnesses = event.getViewers()` instead of 1, inside `commitReported(..., CIVILIAN, ...)`.
- Before committing it calls `reports.landCivilian(killer, crimeId, at)`. That closes the matching case and any silence (§2), so nothing is committed twice.

## 2. Radio check-ins and silent officers

### 2.1 The last reported location

`CopNpc` gains `lastReport` (a Location) and `lastReportAt` (ms). Both are stamped at spawn, and then by the `CopCrimeReports` task (every 10 ticks):
- **In a chase**, every run while his squad `isInContact()` (`KS/NpcSquad.java:217`).
- **On patrol**, once `Check_In_Seconds` have passed since the last stamp.

This is the owner's "last reported checked location", taken literally.

### 2.2 When a cop dies

The silent path runs only when **all** of these hold:
- `Cops.Check_In.Enable` is on;
- `Kill_Cop` is in `Wanted.Witnesses.Crimes`;
- a player killer is known (§1.1).

Otherwise `onCopDeath` is today's code, except that Kill_Cop is booked through `open` (which commits at once when the crime is not gated).

When the path applies, it runs in `CopListener.onCopDeath:246-275`, before any `memberDown`:
1. **A cop eyewitness saw the killer.** Today's code runs: `memberDown` on the current squad, and on the group squad when they differ (`:258-264`). That sends Man_Down/Leader_Down, the casualty and the backup (`CopRadio:112-131,672-681`).
   - `setBackupHeld(false)` (`:254-256`) stays at death: a cop saw it, so the radio is live.
   - The report lands 3 s later.
2. **No cop saw the killer, but one saw the body fall.** That means `sees(cop, body)` within `Alert_Range`, or the close-range floor, combined with the cone or loud rule.
   - `memberDown` runs as in 1, and a SCENE (§2.4) opens at the body.
3. **Nobody saw anything. He becomes a SILENT unit:**
   - `NpcSquad.remove(cop)` (`KS/NpcSquad.java:153-157`) **instead of** `memberDown`, on every squad he is in: the current squad, and the group squad when they differ (the same two targets as `:258-264`; the RETURNING case at `:261` included).
     - This sends no Man_Down, no `regroupCheck`/`recordCasualty` and no `requestBackup`.
     - The existing test `onCopDeath_inAttackerSquad_notInGroupSquad_reportedOnceToAttackerSquad` gains a silent twin that asserts the cop is gone from that squad.
   - `setBackupHeld(false)` is **not** called at death. It moves to the moment the Kill_Cop commits, or Officer_Missing, whichever comes first, before `requestBackup`.
   - `CopDeathEvent` (`:274`) gains a `silent` flag (a module event).
     - `StandoffListener.onCopDeath` (`cnc/listener/police/StandoffListener.java:30-45`) and `PursuitReportListener.onCopDeath` (`cnc/listener/wanted/PursuitReportListener.java:58-63`) ignore a silent event.
     - `CopCrimeReports` re-fires a non-silent `CopDeathEvent` at discovery (Officer_Missing, a body sighting or a 911 call), so the standoff casualty and the pursuit report's `copDown` happen when the city learns of the death.
   - `reports.silent(cop, groupOrNull, stationIdOrNull, killer, lastReport, lastReportAt)` records the silence.

**Already hunted:** if the killer is his group's target and nobody saw the kill, rule 3 still applies. While hunted, the squad's `isInContact` stamps keep `lastReport` near the death spot. `Pursuit_Presumed` decides whether the kill counts at Officer_Missing. He keeps the stars he already has; nothing drains them meanwhile (§4).

### 2.3 Keeping the dead cop's slot filled until dispatch notices

There is no stored counter. `reports.silentRoles(group)` works the held roles out from the open silences, so a logout, a 911 call or a group teardown (`CopManager:222,1175,1536`) leaves no phantom count behind.

**Changes in `CopManager` (a0b016f24 HEAD lines):**
- `liveRoles` gets the silent roles too.
- The dispatch-off loop becomes `while (currentCount + held < targetCount)` (`:818`).
- `lacking = targetCount - currentCount - held` (`:836`).
- `currentCount` itself is unchanged. The T-435 surplus release and the r4 "a new episode trims against engaged cops" block (`:859-866`, not read in this pass) must keep comparing real cops only, so a held slot never sends a real cop home. Verify this in S5 and pin it with a test.

**Beat cops.**
- `PatrolManager.aiTick` stamps `lostAt` on the first AI tick after a death (`cnc/npc/police/patrol/PatrolManager.java:183-191`). The replacement then spawns after `Respawn_Seconds` (30, `res/cops.yml:686`), whatever the silence says.
- `PatrolManager` therefore skips the replacement for a station with an open silence: `reports.silentStation(id)` holds it.
- At Officer_Missing, `respondTo` (§2.4) releases it.

### 2.4 The silence timeline and the scene

**At `lastReportAt + Check_In_Seconds`:** dispatch says `Radio_Check` ("Unit %unit%, status check.").

**At `lastReportAt + Silent_Seconds`, dispatch says `Officer_Missing`** ("Unit %unit% is not answering. Units, check his last position near %place%."; `%place%` = `lastReport`). Then the silence closes, and:
- the deferred non-silent `CopDeathEvent` fires;
- the backup hold is released.

**If the cop had a group,** re-resolve it with `groupOf(group.target)`. If the group has changed or is gone, treat the cop as group-less (below). Then:
1. `group.recordCasualty(now)` and `group.requestBackup(now, backup)` (`cnc/npc/police/CopGroup.java:214,328`).
2. If the killer is the group's target: `group.setBias(new SpawnBias(lastReport - player, lastReport, now + Handoff.Bias_Seconds, Cone_Degrees))` (`CopGroup:413`).
3. **Then `group.spendBiasSeed()`** (new). It is an explicit flag that makes `takeBiasSeed` (`:429-433`) return false.
   - Today the seed is skipped only when `lastKnownLocation()` differs from `seedLead`. That is the ponytail shortcut `:422-424` admits, and it breaks when `lastReport` equals the lead.
   - Without the seed, the R5 `bias.lead(handoffLead())` call (`CopManager:955`) cannot push the tip past the reported spot.
4. `squad.reportTip(lastReport)` and `group.markTipOff(now)`.
5. On the next spawn tick the refill queues under that bias, and `spawnUnit` places it in the hidden ring on the report's side.
   - **Limit:** the ring is player-centred (`KS/entity/EntitySpawner.java:260`). The unit appears near the player on the report's side and walks the tip. It does not appear *at* the report.

**If the cop was a beat cop (no group): the new `PatrolManager.respondTo(stationId, lastReport)`:**
- releases the held station replacement and spawns it now;
- gives it `navigateTo(lastReport)` plus an INVESTIGATE hold that lasts until it arrives or `Scene_Seconds` pass, so PATROL's wander (`PatrolBehavior:108-115`) cannot override it;
- sends **one** more unit: the nearest live beat cop of the same station, under the same hold. **Two units respond in all.**
- speaks through the new `CopRadio.dispatchAt(Location, addressee, key, extras)`, which uses a throwaway `NpcSquad`, as at `CIV/gangland-civilians/.../npc/CivilianService.java:330`.

**If the cop had neither a group nor a station,** no unit is sent. The scene still opens, and any cop that passes can close it.
- Check this against the 0.21.0 transit, rescue and jail-intake guards in S5. I have not traced whether they carry a group or a station.

**A SCENE opens at `lastReport`.** Scenes are keyed by killer, so they work for a killer with no group. Each task run:
- **Any cop passes `eyes` on the killer while `killer.distance(sceneAt) <= Scene_Radius`:** commit `(true, n)` under `ReportKind.SCENE`. The spotting cop says `Scene_Suspect`, and the scene closes.
- **The first unit within 6 blocks of `sceneAt`** says `Scene_Found`, once.
- **`Scene_Seconds` run out:** dispatch says `Scene_Clear`, the action bar shows "The trail went cold", and nothing is committed.

**Pursuit_Presumed** (default on, OD-2): if the dead cop's group was hunting this killer, commit Kill_Cop at Officer_Missing, then close the scene.
- The commit is `(at=lastReport, seenByCop=false, witnesses=1)` under `ReportKind.DISPATCH`. `witnesses=1` is needed: `0` would be cancelled by the gate at `:66`.

**If a 911 call lands first** for the same Kill_Cop, `landCivilian` closes the silence with the Officer_Missing actions, minus the radio line and the commit (Caller_911 speaks instead). Units are biased to the call's `at`.

**Limit: the pursuit leash.** A chasing cop gives up past `Pursuit.Max_Distance` 80 (`res/cops.yml:128-130`), and a spawn must be within 0.75 x 80 of the player (`cnc/npc/police/spawn/CopSpawnManager.java:46,288-296`). So once the player is more than about 80 blocks from `lastReport`, the scene runs out as Scene_Clear. That is the intended result: he left. A location-centred hidden ring is upstream follow-up S6.

**Out of scope: only a death starts a silence** (OD-10). A cop stuck on a path, or one that vanished, never stops answering. Stuck requeued units are B5 (T-450).

## 3. Stars from who saw what

Replace the `seen` factor in `HeatLedger.record` (`cnc/wanted/heat/HeatLedger.java:104` in a0b016f24; the same expression in CIV) with:

```java
boolean gated = witnessSettings.get().crimes().contains(event.getCrimeId());
double  w     = (event.isSeenByCop() || (!gated && copSight.test(player))) ? heat.seenByCopMultiplier() : 1.0;
w += heat.perExtraWitness() * Math.min(Math.max(0, event.getWitnesses() - 1), heat.maxExtraWitnesses());
mult *= w;
```

**How it scores:**
- **Gated crimes lose the hidden x1.5** from the squad's 1.5 s sighting window (`HeatModuleConfig:34-37`). That window was the main source of the "random" feel. Ungated crimes keep it.
- **By role:** a cop eyewitness gives x1.5 (the existing `Seen_By_Cop`). A civilian report, a scene sighting or a presumption gives x1.0.
- **By head count:** each eyewitness past the first, of any role, adds `Per_Extra_Witness`, up to `Max_Extra_Witnesses`. Counted at landing, so killing a witness before the report lowers the heat. That is deterministic counterplay.
- **Streaks:** `Streak_Bonus` 1.5 inside `Kill_Combo.Reset_After` 10 s (`HeatLedger:98-103`), measured at landing time. `ponytail:` `CrimeCommittedEvent` has no crime-time field; a later api minor can add one.
- **Star thresholds** do not change: 100/250/450/700/1000 (`res/wanted.yml:31-36`).

**Worked defaults** (`Per_Extra_Witness 0.5`, `Max_Extra_Witnesses 3`), from 0 stars:

| Crime (weight) | nobody | presumed / scene | 1 civ | 3 civ | 1 cop | 2 cops | 3 cops | 4+ cops | 0.21.1 |
|---|---|---|---|---|---|---|---|---|---|
| Kill_Cop (150) | 0 | 150 ★1 | 150 ★1 | 300 ★2 | 225 ★1 | 300 ★2 | 375 ★2 | 450 ★3 | 250-325 ★2 at once (B1) |
| Kill_Civilian (100) | 0 | - | 100 ★1 | 200 ★1 | 150 ★1 | 200 ★1 | 250 ★2 | 300 ★2 | ★1, 0 unseen (CIV gate) |
| Assault_Cop (100) | 0 | - | 100 ★1 | 200 ★1 | 150 ★1 | 200 ★1 | 250 ★2 | 300 ★2 | ★1 at once |

Two cop kills inside 10 s, each seen by one cop: 225 + 337.5 = 562.5, which is ★3.

**Escalation is per crime.** Stars rise only when a crime lands, scaled by who saw it. Being seen in the open without a new crime adds nothing (OD-9).

## 4. Persistence: no free clear, no stuck state

**Rule:** stars go up only through crimes. They go down only through escape (EVASION), arrest, death or a command. **Killing every cop never removes a star.**

Changes in `cnc/wanted/evasion/EvasionClock.java`:

1. **`handlesDecay`** (`:143-146`) becomes `enabled && groupOf(id) != null`. While the module hunts a player, the legacy timer DECAY (`gangland-core/.../wanted/WantedExecutor.java:56-70`) never takes his stars. **Fixes B4.** `Evasion.Enable: false` keeps the old decay.
2. **The clear in `tick`** (`:181-185`) fires only when there is no group or the player is no longer wanted. It no longer fires on `!hasLiveCop`.
3. **Holds** pause the clock and keep its progress. Each one has a time limit:
   - **Existing:** restrained, a tip-off within `Lost_Sight_Seconds` 3, or a live tracker (`:192-201`).
   - **First response:** no cop standing, `!group.hasStood()` and `unitsEnRoute`. `hasStood` is a new flag set in `CopGroup.add` (`:141`). The hold is limited by `EN_ROUTE_GRACE_MS` 10 s (`CopGroup:106,406-410`). This keeps 0.21.1's cause-E protection: nobody escapes before the first unit exists.
   - **Cause E:** no cop standing, and `biasAt != null` or `hasLeashedWalker`. Unchanged (T-400, T-412).
   - **Silence:** no cop standing and `reports.silentRoles(group)` not empty, because dispatch still believes its unit is on him. Limited by `Silent_Seconds`.
4. **Otherwise, with no cop standing, the clock runs SEARCHING**, centred on `squad.lastKnownLocation()` (`:247-249`, null-guarded there).
   - After a wipe it runs through the breather and the ETA. **Deviation:** today it freezes there (`hold=enroute`, `:202-206`).
   - `speed()` (`:402-409`) gets `No_Cop_Inside_Speed` (0.25) while he is inside the zone and no cop stands. Outside the zone the existing `Outside_Zone_Speed` 2.0 applies.
   - At ★1 he needs 40 s inside the 40-block zone, against a 15 s breather plus the ETA (`res/cops.yml:338-356`). Standing on the bodies does not pay; leaving the zone does.
   - Speed is floored at 0.01 (`:403,:408`), so the clock never freezes and nothing gets stuck, even on a map smaller than the ★5 zone.
5. **The escape** is `stars.drop(..., EVASION)` (`:279`), so `PostEscapeSearch` and the post-escape spotting run as normal. A refill that sees him puts the clock back to SEEN.
6. **B5** (requeued units never re-dispatched, `CopManager:878,927-934`) drops to P3 (T-450): nothing waits on those units any more. Not fixed here.
7. **Stand_Down** goes through `copRadio.dispatch` instead of `sayFromLeader`, which is silent when the group has no living speaker (`CopRadio:166-185`; T-453).

## 5. What the player sees

Per R1, the subtitle stays short and the cause goes in chat, through `WantedHudListener` (440d73c87; b8ca509f1 names the witness on the chat card only). The killer is the addressee of every radio line.

| Step | Radio | Action bar / boss bar | Subtitle + chat cause |
|---|---|---|---|
| Nobody saw it | - | `Witness.No_Witness` "&7Nobody saw that." (`""` turns it off) | - |
| Cop report pending | the witnesses' Man_Down, for a kill | the reporter glows; "&9%reporter% &7is radioing it in - &e%s%s" | - |
| Report cut | - | "&aThe radio call never went out." | - |
| Cop report lands | `Officer_Report` "%reporter% reports %crime% near %place%. All units respond." | - | stars; chat "%crime% - seen by %n% officer(s)" |
| 911 | CIV lines + `Caller_911` | CIV countdown | "%crime% - reported by %n% witness(es)" |
| Silent unit | `Radio_Check`, then `Officer_Missing` | if `Show_Timer`: "&7%unit% &8misses check-in in &e%s%s" | presumed: "%crime% - %unit% stopped answering" |
| Scene | `Scene_Found`, then `Scene_Suspect` or `Scene_Clear` | "&eUnits heading to %place%" / "&7The trail went cold." | "%crime% - spotted near the scene" |
| No cop standing | the existing `Wipe_Refill`, spoken at discovery | the SEARCHING boss bar + "No units on you - leave the area" | - |
| Deferred on quit | - | on join: "&7A report on you came in while you were away." | as the kind that landed |

The six new radio keys go into `Cops.Radio.Priority` (`res/cops.yml:207-219`). Each pool has one line.

## 6. Config, files, migration, tests, risks

### 6.1 Config keys

All keys are block style and `Capitalized_Underscore`. When a key is absent, the bundled value shown is used.

`res/wanted.yml`:

```yaml
Wanted:
   Heat:
      # 0.21.2: added to the witness multiplier per eyewitness past the first (any role); 0 = head count ignored
      Per_Extra_Witness: 0.5
      Max_Extra_Witnesses: 3
   Evasion:
      # 0.21.2: hide-timer speed inside the search zone while no cop stands (floored at 0.01, never a freeze)
      No_Cop_Inside_Speed: 0.25
   Witnesses:
      # a report whose witness is alive when you log out lands when you next join, within this many minutes
      Defer_Minutes: 10
      Crimes:
         - Car_Theft
         - Assault_Civilian
         - Kill_Civilian
         # 0.21.2: remove these two to restore instant cop-crime stars
         - Assault_Cop
         - Kill_Cop
      Cops:
         # seconds a cop eyewitness takes to radio it in
         Report_Seconds: 3
         # full width of a cop's view cone; 360 = all round. Engaged cops and loud crimes ignore it
         View_Angle: 180.0
         # a cop this close with line of sight sees you whatever the light or sneaking
         Witness_Floor: 6.0
         # crimes that turn every cop in Alert_Range toward the noise; a shot of a Witness.Loud_Weapon_Types weapon
         # within Witness.Loud_Shot_Seconds (npc/civilians.yml) is always loud
         Loud_Crimes: []
         Reporter_Glow: true
```

`WitnessSettings` is `record(boolean enabled, List<String> crimes)` with a two-crime `DEFAULT` (`cnc/wanted/config/WitnessSettings.java:15`). It gains `deferMinutes` and a nested `CopWitnessSettings`, and `DEFAULT` changes with it. `Bundled020YamlTest`, the dead-key guard, is extended for every new key.

`res/cops.yml`, next to `Backup:` (`:308`):

```yaml
   Check_In:
      # false = a dead cop's squad radios at once (pre-0.21.2); the silent path also needs Kill_Cop in Witnesses.Crimes
      Enable: true
      Check_In_Seconds: 10
      Silent_Seconds: 20
      Scene_Radius: 32.0
      Scene_Seconds: 60
      # the dead cop's group was hunting the killer: committed at Officer_Missing
      Pursuit_Presumed: true
      Show_Timer: true
```

**Message keys:**
- `cop_radio_messages(.yml/_es)`: `Officer_Report`, `Radio_Check`, `Officer_Missing`, `Scene_Found`, `Scene_Suspect`, `Scene_Clear`.
- `wanted_messages(.yml/_es)`: `Witness.No_Witness`, `Witness.Cop_Calling`, `Witness.Report_Cut`, `Witness.Silent`, `Witness.Scene_Heading`, `Witness.Scene_Cold`, `Witness.Deferred`, `Hud.No_Cop_Search`, and the four cause lines.
- Parsed in `WitnessSettings`, `HeatSettings` and `EvasionSettings` (through `ChaseConfigLoader`), plus a new `CheckInSettings` beside `BackupSettings`.

### 6.2 Files

**cops-n-crooks, new:** `wanted/report/CopCrimeReports.java` (a `BeanLifecycle` built in `HeatModuleConfig`). It holds:
- `eyes`, `open`, `landCivilian`, `silent`, `silentRoles`, `silentStation`, `sceneFor`;
- the deferred map and its join listener (plain Bukkit);
- the 10-tick task, which also stamps check-ins.

**cops-n-crooks, edits:**
- `seam/HeatWantedTracker.java:99-100`
- `config/HeatModuleConfig.java:34-51`
- `wanted/heat/HeatLedger.java` (`seen` factor; `reporting` becomes `ReportKind`; `CrimeRecord.kind`; `reportAssault`)
- `listener/wanted/WitnessGateListener.java:66-75`
- `listener/wanted/NineOneOneListener.java:49-61`
- `listener/detainment/CopListener.java:246-275`
- `events/npc/CopDeathEvent.java` (`silent`)
- `listener/police/StandoffListener.java`, `listener/wanted/PursuitReportListener.java` (ignore silent)
- `npc/police/radio/CopRadio.java` (`dispatchAt`)
- `npc/police/npc/CopNpc.java` (two fields)
- `npc/police/CopGroup.java` (`hasStood`, `spendBiasSeed`)
- `npc/police/CopManager.java` (`:156` place, `:818,836` held, `:859-866` check)
- `npc/police/patrol/PatrolManager.java` (`respondTo`, station hold)
- `wanted/evasion/EvasionClock.java`, `WantedHudListener`, `wanted/config/WitnessSettings.java`
- the yml files above

**gangland-civilians:**
- `witness/WitnessService.java`: `inView` becomes public; a new `loudWithin`; watcher validity carried to landing; `onCriminalQuit` defers instead of cancelling.
- `events/CivilianCalledInEvent.java`: `viewers`.
- `listener/npc/CivilianDeathRewardListener.java:52`: commits at the victim.

**Docs:** `documentation/features/wanted-bounty.md`, `documentation/features/cops-n-crooks.md`, `migration-0.21.1.md:163` (superseded note), the CHANGELOG, and a new `documentation/migration-0.21.2.md`.

### 6.3 Migration

Config edits are manual (owner ruling, as in T-424).

**What happens with no edits:**
- A deployed `wanted.yml` keeps its old `Witnesses.Crimes`, so cop crimes stay **instant** until the admin adds `Assault_Cop` and `Kill_Cop`. The silent path stays off, because it needs Kill_Cop gated (§2.2).
- B1, B2 and B6 are fixed either way.
- The evasion changes apply at once: no DECAY while hunted, the new holds, `No_Cop_Inside_Speed`.
- Deferred-on-quit applies to civilian calls at once.

**`migration-0.21.2.md` lists:**
- the two `Crimes` lines;
- every new key and its value when absent;
- the six radio keys for `Cops.Radio.Priority`;
- the message keys (absent = built-in English);
- the behaviour changes above.

### 6.4 Tests

JUnit 5 and Mockito, per `documentation/TESTING.md`. Each new test must be shown red on the pre-fix code first.

**New tests:**
- `HeatModuleConfigTest`:
  - `lethalHitOnCop_commitsOnlyKillCop` (B1)
  - `assaultHook_runsAfterSquadSighting` (B3)
- `CopListenerShieldTest.shieldAbsorbedLethalHit_noKillCop` (B2)
- `CopListenerDeathTest`:
  - `unseenDeath_removesFromEverySquad_noManDown_silentEvent_recordsSilence`
  - `bodySeen_keepsMemberDown_opensScene`
  - `nullKiller_keepsMemberDown_noCase`
  - `bartizanKill_nullKiller_usesLastAttacker`
  - `silent_doesNotReleaseBackupHold`
  - `checkInOff_orKillCopUngated_keepsTodaysPath`
- `CopCrimeReportsTest`, reports:
  - `copKill_seenByOneCop_landsAtReportSeconds_seenByCop_count1`
  - `victimNeverWitnesses`
  - `hitCop_engaged_witnessesOwnAssault`
  - `beatCopSeesUnwantedPlayer_reports`
  - `copBehind_outsideCone_meleeKill_noWitness`
  - `gunshot_turnsCop`
  - `meleeSwing_notLoud_forCopsOrCivilians`
  - `nightSneak_withinFloor_stillWitnessed`
  - `disguisedKiller_stillWitnessed`
  - `reporterKilled_nextWitnessKeepsDeadline`
  - `allWitnessesKilled_reportCut`
  - `notInCrimes_commitsAtOnce`
  - `quit_defersPending_commitsOnJoin_expiresAfterDeferMinutes`
  - `killerDeath_dropsCases`
  - `killerCuffed_dropsCases`
  - `restrainedAtLanding_drops`
  - `copEyes_suppressCivilianCall`
  - `landing_tipsAtCrimeSpot_notLivePosition`
  - `landing_radioPlaceIsCrimeSpot`
  - `shutdown_cancelsTimer_reload_keepsCases`
- `CopCrimeReportsTest`, silences and scenes:
  - `checkIn_inContactStampsEveryRun_patrolEveryTenSeconds`
  - `silence_radioCheckThenMissing_atLastReport`
  - `missing_groupCop_biasToLastReport_seedSpent_tipsExactSpot_casualty_backup`
  - `missing_firesDeferredCopDeathEvent`
  - `missing_beatCop_releasesHeldReplacement_twoUnitsInvestigate`
  - `missing_noGroupNoStation_sendsNobody_sceneOpens`
  - `scene_killerSeenInRadius_commitsSeen`
  - `scene_expires_noStars_sceneClear`
  - `pursuitPresumed_commitsWitnesses1_passesGate`
  - `nineOneOneFirst_closesSilence_noDoubleCommit`
  - `silentRoles_emptyAfterMissing_logout_911_teardown`
- `CopManagerSquadTest`:
  - `silentCop_holdsRefill_bothDispatchBranches`
  - `silentCop_neverCountsTowardRelease_orEpisodeTrim`
  - `wipeRefill_spokenAtDiscovery`
- `PatrolManagerTest.openSilence_holdsStationReplacement`
- `CopGroupTest.spendBiasSeed_blocksSeedEvenWhenLeadEqualsReport`
- `EvasionClockTest`:
  - `handlesDecay_trueWhileGroupExists` (B4)
  - `noUnitsEverSpawn_searchesLastKnown_dropsAsEvasion`
  - `firstResponseEnRoute_stillHolds`
  - `openSilence_holds`
  - `wipeAfterStood_searchRunsThroughBreather_insideSpeedQuarter`
  - `noCopInsideSpeedZero_isFlooredNotFrozen`
- `HeatLedgerTest`:
  - `witnessTableRows` (parameterised over §3)
  - `oneWitness_unchanged`
  - `gatedCrime_ignoresSquadSight`
  - `reportKind_storedOnRecord`
- Other suites:
  - `NineOneOneListenerTest.commitsViewerCount`
  - CIV `WitnessServiceTest.calledInEvent_carriesCallerPlusWatchers`
  - CIV `WitnessServiceTest.criminalQuit_defersCall`
  - `CivilianDeathRewardListenerTest.commitsAtVictim` (B6)
  - `StandoffListenerTest.silentDeath_noCasualty`
  - `PursuitReportListenerTest.silentDeath_notCounted`
  - `Bundled020YamlTest` and the ChaseConfig tests, extended for every key

**Existing tests that pin today's behaviour and must flip:**
- `HeatWantedTrackerTest.heatOn_copKill_commitsKillCop` (`:237-240`)
- `HeatLedgerTest.assaultCop_once_givesTheFirstStar_andIsTheLastCrime` (`:101`) and `reportAssault_sameVictimInsideTheWindow_...` (`:246`)
- `WitnessGateListenerTest.copSightPassesThrough`, and the b8ca509f1 cases for the `copSees` bypass
- `CopListenerDeathTest`:
  - `onCopDeath_findsDyingCopWhileInvalid_callsMemberDownThenMarksForRemoval` (`:65`; stays green for a seen or null-killer death, and gains a silent twin)
  - `onCopDeath_inAttackerSquad_notInGroupSquad_reportedOnceToAttackerSquad` (`:115`; the same treatment)
  - `suspectKill_releasesTheBackupHold_beforeManDown` (`:227`; flips for the silent path)
- `EvasionClockTest`:
  - `onlyReturningCops_doesNotHandleDecay_andTurnsOff` (`:345`)
  - `emptyGroup_whileSearching_turnsOff` (`:427`)
  - `unitsEnRouteOnly_ownDecay_butMakeNoProgress` (`:1119`; stays green for the first response, flips after a wipe)
- `NineOneOneListenerTest` (the `witnesses=1` asserts)
- CIV `WitnessServiceTest` cases that assert a call is cancelled on the criminal's quit

**Live acceptance rows** (mineflayer, `live/run.sh` on a `servers/cop-ai-*` clone; judged from `chat.txt` plus new `REPORT`, `CHECKIN` and `SCENE` debug lines):

| Id | Setup / action | Pass |
|---|---|---|
| WK-1 | not wanted, one beat cop; melee one-shot from behind | no star, "Nobody saw that."; Radio_Check, then Officer_Missing 10-20 s after the kill; 2 beat cops walk to the last check-in; no replacement before Officer_Missing |
| WK-2 | as WK-1, and the bot stays within 32 blocks | Scene_Suspect, ★1 (225), "spotted near the scene" |
| WK-3 | as WK-1, and the bot leaves the radius | Scene_Clear, no star |
| WK-4 | 2 beat cops facing the bot; kill one | reporter glows; ★1 at +3 s; exactly one Kill_Cop; "seen by 1 officer"; radio %place% is the body's spot |
| WK-5 | 3 cops facing; kill one | ★2 (300) |
| WK-6 | as WK-4, but kill the reporter within 3 s | "call never went out", then the silence path |
| WK-7 | one-shot a cop from 0 stars, with a witness | one crime, ★1 |
| WK-8 | lethal hit absorbed by a Defender shield | no Kill_Cop |
| WK-9 | ★2, the whole squad killed, spawns blocked | no DECAY; SEARCHING at last-known; leaving the zone drops a star by EVASION; post-escape search runs |
| WK-10 | ★1, the squad silently killed, bot stays on the bodies | no drop before Officer_Missing; Wipe_Refill then; the inside drop takes >= 40 s |
| WK-11 | gun kill behind a cop's back | the cop turns and reports |
| WK-12 | civilian kill seen by 1 vs 4 civilians | ★1 vs ★2 |
| WK-13 | bot quits with a cop report pending, rejoins | the heat lands on join, with "came in while you were away" |
| WK-14 | Bartizan raytrace kill of a cop | exactly one Kill_Cop, killer resolved |
| WK-15 | cop dies to lava, no player damage | today's Man_Down, no Kill_Cop |
| WK-16 | bot cuffed while a report is pending | no stars land after the cuff |

### 6.5 Risks

1. **The leash limits the scene response** (§2.4). Upstream S6 removes the limit.
2. **`respondTo` relies on an INVESTIGATE hold over PATROL.** `PatrolBehavior:108-115` was not read in full; check the hold in S5.
3. **The view cone makes a beat cop's witnessing depend on where he faces.** That is visible and positional, not chance.
4. **The streak is measured at landing**, not at the crime (§3, ponytail note).
5. **Task cost** is cops x (open cases + scenes) every 10 ticks. The range check runs first, so the cost is small.
6. **A hit cop fighting an unwanted player has no group** during the 3 s report window. Only `PatrolManager.aiTick` runs him; WK-4 checks that he keeps fighting.
7. **A restart inside a report window forgives the crime** (§1.4).
8. **Transit, rescue and intake cops** may have neither a group nor a station. Verify in S5 (§2.4).

Bounty hunters are a separate plan: [BOUNTY-HUNTERS.md](BOUNTY-HUNTERS.md).
Docket rows proposed by this trace: [docket-rows.txt](docket-rows.txt), T-446..T-462.

## Owner decisions

Each decision has a recommended default; the plan is written to that default.

1. **An unseen kill whose killer is never spotted at the scene.** Default: 0 stars.
2. **`Pursuit_Presumed`.** Default: on. A cop who stops answering while hunting you counts as a kill at x1.0.
3. **`No_Cop_Inside_Speed`.** Default: 0.25. The options are 0.25, 1.0 (no penalty for staying) or near 0 (strict; floored at 0.01).
4. **`Cops.View_Angle`.** Default: 180, to match civilians. The alternative is 360.
5. **Logout with a report pending.** Default: defer for both lanes; the report lands on the next join within `Defer_Minutes` 10.
   - This replaces the CIV lane's cancel-on-quit, which makes a logout a free escape.
   - The alternatives are cancel for both lanes, or commit at quit. Commit at quit is rejected: it builds a cop group for an offline player.
6. **Timers.** Defaults: `Report_Seconds` 3, `Check_In_Seconds` 10, `Silent_Seconds` 20, `Scene_Seconds` 60, `Scene_Radius` 32, `Witness_Floor` 6.
7. **`Per_Extra_Witness` and `Max_Extra_Witnesses`.** Defaults: 0.5 and 3. Four cops watching a cop kill then give ★3 from 0 stars.
8. **The bundled `Check_In.Enable` and `Show_Timer`.** Default: both on. Deployed servers are unaffected until Kill_Cop is gated (§6.3).
9. **"Wanted level based on viewers".** Default: per crime. Stars rise only when a crime lands, scaled by who saw it.
   - The alternative is ongoing escalation, where being seen in the open by more cops raises the level with no new crime. That is a new heat source; it is not designed here.
10. **Only a death starts a silence.** Default: yes. A stuck or vanished cop never "stops answering".
11. **The behaviour change on upgrade.** Default: accept. A gated crime in front of a cop now lands after 3 s, not at once, and that supersedes the 0.21.1 migration note.

## Staging / release slot

The slot is a new branch, **`0.21.2`** (a patch bump), cut after the CIV lane (b8ca509f1) and spawn r4 (a0b016f24) are both merged into 0.21.1.
- No Keystone or api change is needed.
- Each stage below ships on its own.
- New docket rows: T-446..T-457, T-461, T-462.

- **S1 (no config):**
  - B1 and B3: the one-tick Assault_Cop delay.
  - B2: Kill_Cop booked in `onCopDeath`, with the killer fallback and the null-killer rule.
  - B6: Kill_Civilian committed at the victim.
- **S2 (independent):** §4 persistence.
  - B4.
  - The holds and `No_Cop_Inside_Speed`.
  - Stand_Down through dispatch.
- **S3:** §3 heat.
  - The witness count.
  - No squad bonus for gated crimes.
  - The CIV viewer count.
  - `reporting` becomes `ReportKind`.
- **S4:** §1 `CopCrimeReports` cop reports.
  - The gate rewiring, which removes both bypasses.
  - The shared loudness rule and the close-range floor.
  - The `:166` tip fix and `dispatchAt` / `%place%`.
  - Quit deferral, and dropping cases on a cuff.
- **S5 (needs S4):** §2.
  - Check-ins, silences and scenes.
  - `CopDeathEvent.silent`.
  - The slot and station holds, `respondTo` and `spendBiasSeed`.
- **S6 (optional, upstream):** a location-centred hidden-spawn ring in Keystone, so units can spawn at the scene.
