# Cops N Crooks 0.21.0: investigation report for spawn-dup, radio-far and brandish

Read-only, nothing edited, built or committed. The graph is fresh (`graph.json` 2026-10-26 11:20, newer than the last commit 2026-10-24 12:10). I used `graphify explain EvasionClock` to orient before reading code.

**Path prefixes**
- `cnc/` = `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/`
- `res/` = `gangland-features/cops-n-crooks/src/main/resources/copsncrooks/`
- `ks/` = `E:/Programming/java/Keystone/keystone-npc/src/main/java/org/luckyraven/keystone/npc/`

**Tags**
- **[re-read]** means I opened the code myself in this pass.
- **[verifier]** means the tracer and verifiers read it, but I did not re-read it here.
- READ means seen in code; INFER means deduced.

---

## 1. spawn-dup: cops keep spawning while a wanted player runs

### Root causes that survived

| # | Cause | Class | Docket | Provenance |
|---|---|---|---|---|
| 1 | Leashed walkers are replaced but never removed | design-gap (primary) | T-131, T-149 (existing, open) | [re-read] |
| E | The evasion clock resets on every whole-squad leash, so the chase runs longer | bug (amplifier) | **NEW** | [re-read] |
| 3 | A leash refill gets no tip in some cases | bug (amplifier) | T-148 (existing, open) | [re-read] spawnDueUnits |
| 4 | Queued units spawn without re-checking how many are still needed | bug | **NEW** | [verifier] |
| 5 | `reengageReturning` pulls back walkers at any distance | bug (makes walkers live longer) | T-131 (part) | [verifier] |
| 2 | The spawn area reaches the leash line; the station spawner is tried before the bias | config-default / edge case | **NEW** | [re-read] |
| R | Every refill batch re-announces Dispatch_En_Route | config-default (makes it look worse) | **NEW** | [re-read] cops.yml:288 |
| P | Post-escape search keeps refilling for 120 s | design | **NEW** (owner ruling) | [verifier] |
| 6 | Beat-cop patrols refill instantly | design-gap (low, only near stations) | **NEW** | [verifier] |

**Cause 1 (primary).**
- The leash: `cnc/npc/police/state/behavior/PursuingBehavior.java:100-106` switches PURSUING to RETURNING once the 3D distance passes 80 (`res/cops.yml:130`). It keeps `targetPlayerId`.
- The removal pass, `cnc/npc/police/CopManager.java:719-735` [re-read], only releases cops that are marked for removal, recycled or invalid. A stranded walker therefore stays in `group.getCops()`.
- The count leaves it out anyway. `counted = cops.filter(!isStrandedReturning)` at `CopManager.java:751-753` [re-read]; `isStrandedReturning` is at 962-966.
- The cap only limits the refill target: `targetCount = min(level count + backup, Max_Per_Player)` at `CopManager.java:749-750` [re-read].
- The walker only despawns when it reaches a spawner, after the unreachable or Recycle window, or at Return.Max_Ticks (300 s cap) (`ReturningBehavior.java:92-108`, `cops.yml:140`).
- The live server already showed 4-5 cops at level 2 against a target of 3 (T-149 observation).
- INFER: walkers more than 80 blocks out are beyond entity tracking (48), so the owner sees repeated waves rather than a visible crowd.
- **Trigger:** at 2-3 stars, a sprinting or driving player outruns a Speed 1.0 squad past 80 blocks. The next 2 s spawn tick counts 0 and queues a full squad ahead of him. This repeats on every outrun.

**Cause E (missed by the tracer, high).**
- `cnc/wanted/evasion/EvasionClock.java:177-183` [re-read] calls `clear(player)` when `!hasLiveCop`.
- `hasLiveCop` (`:411-413`) needs a valid non-RETURNING cop or a unit en route (`CopGroup.java:365-368`).
- A whole-squad leash happens inside `aiTick`'s `cop.tick` (`CopManager.java:1066-1068`), and the AI hooks run right after it (`:1093`; the hook is registered at `EvasionModuleConfig.java:113`).
- Replacement units are only queued later, in `spawnTick` (every 40 ticks, against the AI tick's 10). So the evasion progress is usually thrown away, and the star-drop countdown starts over when the refill arrives.
- Outrunning the cops is the natural way to escape, and it is exactly what resets escape progress. That keeps the leash, hand-off, refill loop going.

**Cause 3 (T-148, narrowed).**
- `recycles()` tips the squad off (`CopManager.java:948-949`).
- `spawnDueUnits` also tips, when the unit carries a hand-off bias: `reportSighting(bias.lastSeen()) + markTipOff` at `CopManager.java:878-881` [re-read].
- So T-148's gap is real only in these cases (from `HandoffController.java:86-88` [re-read]):
  - a partial leash (another cop is still engaged, so no hand-off fires);
  - a second leash while a bias is still active (the early return on `biasAt != null` leaves the old `lastSeen`);
  - Handoff is disabled.
- The extra recycle churn about 12 s later (`cops.yml:446`) only applies when the lead is unreachable.

**Cause 4.**
- `dispatchMissing` only ever adds units: `missing = lacking - pendingCount` (`CopManager.java:819-821`).
- `spawnDueUnits` (`:860-883`) spawns every due unit with no check against the target.
- Correction to the tracer: the queue is also emptied by `clearPending` on wanted end, `endSquad` and the escape path (`:204, :263, :294`). Only mid-level drops (n to m>0) go unhandled. `onWantedLevelChange` ignores those (`:331-337`).
- The overshoot is at most one batch (ETA up to 40 s, `cops.yml:337`).

**Cause 5.**
- `reengageReturning` (`CopManager.java:305-311`) flips every RETURNING cop to PURSUING with no distance check.
- A far walker leashes again on its next tick, and `ReturningBehavior.onEnter` (`:112-116`) resets `despawnTicks`, so its 300 s lifetime restarts.
- Correction: this runs once per wanted start. The level-change path returns early at the `isWanted()` guard. `keepSquadOnSearch` does run twice per escape, but running it twice changes nothing.
- It adds no spawns by itself. It extends the walkers' lifetime, and the one-tick re-engage creates fresh hand-off edges (and so more Handoff radio lines).

**Cause 2.**
- `hiddenStationSpawner` (`cnc/npc/police/spawn/CopSpawnManager.java:240-255` [re-read]) tests horizontal distance only (radius 80, `cops.yml:108`) and allows up to 16 blocks of height difference (`:124`).
- The leash uses 3D distance over 80 (Keystone `AbstractNpc.distanceTo`).
- `spawnUnit` tries the station spawner before `unit.bias()` (`CopSpawnManager.java:207-213` [re-read]).
- Only `recycles()` ever calls `group.avoid` (`CopManager.java:945-947`).
- A unit spawned 60-80 blocks out can leash quickly, despawn on its own spawner, and get re-dispatched with a new radio line.
- This is an edge case (both radii are 80), not the main driver.

**Cause R.**
- `Dispatch_En_Route` has a cooldown of 0 (`res/cops.yml:288` [re-read]).
- `dispatchMissing` announces every batch that comes from a station (`CopManager.java:838-844`).
- Each leash refill therefore sounds like a brand-new dispatch.

**Cause P.**
- During the post-escape search the spawn tick runs at level 1 (`CopManager.java:696-706` [re-read]).
- Leashes during the search are refilled the same way.
- Any sighting by a new squad member raises the stars again (`PostEscapeSpotting.java:61-74`).

**Cause 6.**
- `PatrolManager.manage` (`:104-125`) refills to Cops_Per_Station every second while a player is within 64 blocks.
- There is no respawn cooldown.
- The missing null-entity guard is effectively harmless, because the first validity check comes at least 10 ticks after spawn.

**Ruled out**
- **Cause 7 (ring spawn destroyed by a stricter check).** The verifiers disagreed, so I opened the code. Keystone `ks/entity/EntitySpawner.java:378-388` `isValidGround` checks floor, feet, head and the block above head, plus clearance on two layers. `cnc/npc/police/npc/CopNpcFactory.java:271-296` `isSafeSpawnPosition` checks the same blocks against the same clearance. A cramped spot fails at the ring stage, so a repeatable loop cannot happen. Only an entity nudged during its first tick can trigger it, and that is sporadic.
- Also not a cause: Citizens teleport-on-stuck (the test server config sets it to `none`). T-126, an entity swap that can look like a duplicate, is already fixed.

### Pinned tests that would need flipping
- `CopManagerSquadTest.spawnTask_ignoresStrandedReturningCops` (L168-186) asserts `4` cops for 2 stranded plus 2 fresh. Change it to `2` once out-of-view stranded walkers are released, and keep the "nearby returners still count" half.
- `spawnTask_fillsCompositionRoles_strandedRoleRefilled` (189-203) stays green.

### Recommended fix (at the choke points)
1. **Causes 1, 3 and 5.** Do this in the `removeIf` of `spawnTick` (`CopManager.java:719-735`). When `isStrandedReturning(cop, player, maxDist)` holds and the cop is out of view (reuse the `inView` + `spawnManager.isVisibleToOtherPlayers` guard from `recycles()`, `:937-943`), call `group.release(cop, markManager)` and tip the squad.
   - The tip must go through the Keystone `reportTip` seam (radio-far fix 1), not `reportSighting`. Otherwise this fix makes the radio symptom worse.
   - This makes Max_Per_Player a real cap for out-of-view walkers. In-view walkers are still kept and not counted, as T-130 intends, so a short overlap remains by design.
2. **Cause E.** In `EvasionClock.hasLiveCop`, also count an active `group.biasAt(now) != null`, so a leash pauses the clock instead of clearing it. After fix 1 there are no walkers left to key on, so do not use `leashedOut`. Hand-off detection (AI tick) and release (spawn tick) run on different cadences, and the fix must not depend on which runs first.
3. **Cause 4.** Pass `targetCount - currentCount` into `spawnDueUnits` and stop spawning once it is met.
4. **Cause R.** Give `Dispatch_En_Route` a per-group cooldown, or skip it for leash refills.
5. **Cause 2.** In `hiddenStationSpawner`, require a 3D distance of at most `min(Spawner_Preference_Radius, 0.75 × Pursuit.Max_Distance)`, and try the bias cone first when `unit.bias() != null`.
6. **Cause 6.** Add a per-station respawn cooldown in `PatrolManager.manage`.

### Red tests to write (each must fail on today's code)
- `CopManagerSquadTest`: an out-of-view stranded RETURNING cop at (500,500) is released (`verify(cop).destroy`), and the group size never exceeds `targetCount`. Use the `CopManagerStuckTest.strandedOutOfView_recycledAndReplaced` pattern.
- `CopManagerStuckTest`, leash variant of `recycle_tipsTheSquadOffToTheSuspectsSpot`: after `spawnTick`, `lastKnownLocation` is the player's spot and `tippedOffWithin` is true.
- `EvasionClock`: a group whose only cops are RETURNING and that has an active bias keeps its Track. Today `clear()` fires.
- `CopManagerDispatchTest`: queue units at level 3, drop to level 1 before the ETA, advance the clock, run `spawnTick`. `spawnUnit` must be called no more than `target - counted` times.
- `CopManagerSquadTest`: `onWantedStart` leaves a RETURNING cop at (500,500) in RETURNING.
- `CopSpawnManagerUnitTest`: a spawner 79 blocks out horizontally and 15 up (3D > 80) is not chosen, and a biased unit gets a ring spot ahead of the runner.
- `PatrolManagerTest`: an invalid beat cop is not respawned within the cooldown.

---

## 2. radio-far: cops radio "go around him" while far away and out of sight

### Root causes that survived

| # | Line(s) the player hears | Class | Docket | Provenance |
|---|---|---|---|---|
| H | **Handoff**: "Lost him heading %direction%. Units ahead, pick him up." | bug (perception) | **NEW** | [re-read] |
| 2 | **Flank_Left/Right, Push, Engage, No_Route, Commander_Orders**, triggered by a tip with no line of sight | design-gap (shared root) | T-109 (by design), **NEW KS- row** | [re-read] NpcPursuit:245-246 |
| S | **Standoff**: "He's holed up, set the perimeter!" on the UNREACHABLE trigger | bug | **NEW** | [re-read] |
| 1 | **Route**: "Going round, finding a way to him." / "Cutting round to him!" | design-gap (wording) | follow-up to `triage/new-findings.txt:129` (fixed 0.12.0) | [verifier] |
| 3 | **Overwatch_Set** ("I have eyes on the suspect") with no sight check; `%side%` from the player's live facing | bug | **NEW** | [re-read] roleKey |
| 4 | The hunted player hears his pursuers from 64 blocks (Target_Range), but cops only see 40 (Alert_Range) | config-default | T-114 (by design; needs an owner ruling) | [verifier] |

**H. Handoff (most literal match for "when I'm far").**
- It fires exactly when every hunting cop has leashed out, past 80 blocks (`cnc/npc/police/handoff/HandoffController.java:86-88`).
- The heading comes from the player's own sampled positions (`:75-78, :90-93`), not from any cop sighting.
- `sayFromLeader(..., player)` makes the player the addressee (`:97`), and `SquadRadio.java:218` delivers a line to anyone within Range of the addressee. So the player always hears it.
- The biased units it queues then re-seed a fresh sighting on spawn (`CopManager.java:878-881`), which feeds cause 2.

**Cause 2 (shared root: a tip counts as a sighting).**
- `NpcSquad.reportSighting(Location)` stamps `lastSeenAt` (`ks/NpcSquad.java:185-190`), and `hasFreshSighting` reads only that stamp (`:251-254`, grace 1500 ms).
- `ks/NpcPursuit.java:245-246` [re-read]: `seen = hasFreshSighting(); goal = seen ? target.getLocation() : lastKnown`. A tip with no line of sight therefore makes every member chase the player's live position.
- `announce()`/`sideOrder()` then emit FLANK, PUSH or ENGAGE (`:297-322`), and the leader speaks the order (`SquadRadio.java:136, 158-176`).
- cops-n-crooks tips without line of sight in these places:
  - `CopManager.java:160` (wanted start), `:366` (attacker hit a cop), `:879` (biased spawn), `:949` (recycle, live spot)
  - `ShotNoiseListener.java:85` (radius 48 > Alert_Range 40)
  - `TrackerTags.java:125`
  - `NineOneOneListener.java:49`
- The same conflation also:
  - widens `CopPerception.pursuitRange` (`:80-88`) [verifier];
  - drives No_Route ("surround the building", `NpcPursuit.java:271-273, 815-816`) [verifier];
  - drives ENGAGE ("Suspect in sight, engaging!") and Commander_Orders ("Assault, flank %direction%!", `CopRadio.java:433-438`) [verifier].
- The 1.5 s grace after a real line-of-sight loss also tracks his live, hidden spot. An acceptance run shows the Route line firing this way: `brainstorming/cnc-overhaul-2026-10-05/acceptance/runs/final/cnc015-default--N1-evasion-drop/chat.txt:15-18`.

**S. Standoff UNREACHABLE.**
- `cnc/npc/police/standoff/StandoffController.java:186-189` [re-read] fires when more than half the hunting cops have been unreachable for 10 s or more, with no sight check. Compare `NO_ROUTE` at `:191`, which does check `hasFreshSighting`.
- `hunting()` (`:171-182`) filters by state and world only, with no distance limit.
- `start()` centres the post ring on the live `player.getLocation()` (`:225`) and radios "Standoff" (`:227`).
- A player who broke contact near an unreachable spot gets surrounded at his real, current position.

**Cause 1. Route.**
- In SEARCH, a failed walk to the stale `lastKnown` calls `requestRoute`, which enters ROUTE (`NpcPursuit.java:779-787, 863-869`).
- `SquadRadio.java:145-147` speaks Route with no sight condition, and the text says "to him" (`res/cop_radio_messages.yml:88-90` [re-read]).
- A second way in: `chase()` also routes toward the live target (`NpcPursuit.java:575-586`) during a tip-driven or grace-window "seen".

**Cause 3. Overwatch_Set and %side%.**
- `roleKey` maps MARKSMAN + ENGAGE to `Overwatch_Set` (`cnc/npc/police/radio/CopRadio.java:386-388` [re-read]). `roleLine` (`:402-418`) adds no sight check, so the line can follow a tip-driven ENGAGE.
- The `postLine` follow-up is gated only by `atPost`, a distance check (`:464-492`; Marksman about 34 blocks, `cop_roles.yml:216`).
- `%side%` comes from the hunted player's live position and facing (`SquadRadio.java:321-326`).
- Shield_Up is **not** a false-sight line: its text claims nothing about sight.

**Cause 4. Hearing range.**
- `cops.yml:79-81` (Alert_Range 40), `:193-197` (Target_Range 64), `CopConfigProvider.java:27`, `SquadRadio.java:217-221`.
- The scanner (160 blocks) and the lookout relay (`SquadRadio.java:257-274`, T-344) extend it further.
- This is a precondition for the symptom, not a cause.

### Pinned tests that would need flipping
- Keystone `NpcPursuitTest.aTeammatesSighting_isEnoughToChase` (L173) stays green for a real `reportSighting`. Add a `reportTip` twin.
- `NineOneOneListenerTest:77` (`assertTrue(hasFreshSighting)`) and `CopManagerDispatchTest:352 restoreStart_dispatchOff` flip if those callers move to `reportTip`.
- `ShotNoiseListenerTest:100` and `TrackerTagsTest:281-411` need their verify target changed. `CopManagerStuckTest.recycle_tipsTheSquadOffToTheSuspectsSpot` (L107) still holds for `lastKnownLocation`.
- `CopRadioRolesTest.marksmanFlank_overwatchFollowUp` (L280): mock line of sight as true, and add a red twin with no line of sight that verifies "OW" is not sent.

### Recommended fix
1. **Keystone upstream** (new KS- row, Keystone version bump + phase doc, then bump the Gangland pom):
   - Add `NpcSquad.reportTip(Location)`, which updates `lastKnown` but not `lastSeenAt`.
   - Expose the member-contact state the squad already tracks privately (`inContact`).
   - In `NpcPursuit.java:245-246`, use the live target only on member contact, and `lastKnown` otherwise.
   - Move these callers to `reportTip`: `CopManager:879`, `:949`, `TrackerTags:125`, `NineOneOneListener:49`, likely `CopManager:160`, and shot noise if the owner agrees.
2. **Handoff:** compute the heading from the squad's own `lastKnown` history, and do not make the player the addressee. Biased spawns tip through `reportTip`.
3. **Standoff:** require member contact or a fresh line-of-sight sighting for UNREACHABLE, and centre the ring on `squad.lastKnownLocation()`.
4. **Overwatch_Set:** require `cop.hasLineOfSight(hunted)` in both `roleLine` and `atPost`.
5. **Route:** add an optional `Route_Search` pool for when the squad has no contact (`SquadRadio.java:145`). This only adds to `gangland-api`, so no Host_Api major bump. Reword `cop_radio_messages.yml:88-90` and the `_es` file to match.

### Red tests to write
- Keystone `NpcPursuitTest`: after `squad.reportTip(spot)`, the member is in `Mode.SEARCH`, `setTarget(target)` is never called, and no FLANK or PUSH signal is sent.
- `HandoffController`: the Handoff line is not addressed to the player, and its heading comes from the squad's sightings.
- `StandoffController`: UNREACHABLE with no contact starts no standoff.
- `CopRadioRolesTest`: a Marksman ENGAGE with no line of sight sends no Overwatch_Set.
- `SquadRadioTest`: ROUTE with no fresh sighting uses `Route_Search`.

---

## 3. brandish: a cop warns once, walks away after 5 s, and doesn't warn again

The owner's exact words are explained by intended mechanics plus silent outcomes. No permanent block exists.

| # | Cause | Class | Docket | Provenance |
|---|---|---|---|---|
| a | The 30 s cooldown counts from when the warning starts, is shared by every beat cop for that player, and the cop walks off immediately | config-default | **NEW** | [re-read] |
| b | Both deadline outcomes are silent (IGNORED and out-of-view COMPLIED) | design-gap | **NEW** | [re-read] |
| c | Ignored warnings build heat silently; the third one gives star 1 and a squad | config-default / design | **NEW** | [verifier] HeatLedger |
| d | Darkness, dim light and sneaking shrink the 12-block range at both gates | design | **NEW** | [verifier] |
| e | Once wanted (or during the post-escape search), beat cops report the player instead of warning; only PATROL beat cops can warn at all | by design (document it) | **NEW** (docs) | [re-read] |
| f | A suspect description stored at 0 stars never clears; two of head/chest/in-car must change before cops stop recognising him | design-gap (low) | **NEW** | [verifier] |

**Cause (a).**
- `begin()` stamps `lastGiven` at the start of the warning (`cnc/npc/police/patrol/BrandishWarnings.java:71-73` [re-read]).
- `canWarn` blocks until `repeatSeconds` (30) have passed since then (`:55-63`; `res/cops.yml:681-690`).
- It is one shared bean keyed by player (`PatrolModuleConfig.java:45-49`), so every beat cop at every station observes the same cooldown.
- After the verdict, `finish()` sends the cop back to PATROL (`WarningBehavior.java:92-95` [re-read]). `PatrolBehavior.onEnter` resets its beat (`:130-131` [re-read]), so it walks to a new random point within Beat_Radius 24 straight away.
- By the time the 30 s cooldown ends, that cop is usually out of the 12-block range.

**Cause (b).**
- `update()` only sends a message when the weapon is put away (`BrandishWarnings.java:91-95` [re-read]).
- The out-of-view outcome (`:100-104`) and IGNORED (`:105-107`) only log at debug level.
- There is no `Patrol.Ignored` key (`WantedMessages.java:89-93`).

**Cause (c).** Corrects the tracer's "no visible effect": the effect is delayed and hidden.
- One IGNORED is worth 25 × 1.5 = 37.5 heat (`wanted.yml:41,50`), against 100 for star 1 (`:31`).
- `HeatLedger` never decays heat for a player with no stars. It is only cleared on `WantedEndEvent` (`HeatLedger.java:150-153`, `HeatListener.java:33-36`), which never fires for a player who was never wanted.
- So the third ignored warning (about 65 s of holding a weapon near beat cops) gives star 1. `HeatLedger.heatOf` has no callers, so the player never sees heat building.

**Cause (d).**
- Both the scan (`PatrolBehavior.java:58` [re-read]) and the deadline in-view check (`WarningBehavior.java:63` [re-read]) go through `perception.acquires`, which applies the SightModel factors.
- With the defaults, the 12-block range becomes 4.8 blocks in the dark, 8.4 in dim light, and ×0.7 while sneaking (`cops.yml:758-782`).

**Cause (e).**
- `PatrolBehavior.java:60-63` [re-read]: a player for whom `services.wanted()` is true is reported, not warned. That includes post-escape searching (`WantedTargetingManager.java:38-43`).
- `begin()` has a single caller, `PatrolBehavior.java:66`, so squad, IDLE, POSTED and OVERWATCH cops never warn.

**Cause (f).**
- `SuspectListener` stores a description on any crime and clears it only on `WantedEndEvent`.
- `DisguiseFilter.java:47-54` then needs 2 of 3 fields to match (`SuspectDescriptions.java:69-79`). A mismatch needs **two** changes, not one.

**Ruled out**
- **The "open warning leak" (tracer cause 4).** Both verifiers refuted it, and I agree. Keystone `AbstractNpc.destroy` runs `cleanupTransientState()` in its `finally`. `CopNpc.cleanupTransientState` (`CopNpc.java:353-359`) calls the current behaviour's `onExit`, which is `WarningBehavior.onExit` → `abandonWarned` → `BrandishWarnings.abandon` (`WarningBehavior.java:79-90` [re-read]). Its expiry fix is not needed.

### Pinned tests that would need flipping
- `BrandishWarningsTest.repeatCooldown` (L163-178) pins "30 s counted from the first warning". Flip it only if the owner moves the cooldown to start at the verdict.
- No test pins the silent outcomes or the 0-star result.

### Recommended fix (needs owner decisions first)
- In `BrandishWarnings.update`, the single choke point:
  - send a new `Patrol.Ignored` message in the IGNORED branch;
  - send a `Patrol.Lost_Sight` (or the existing Complied) message in the out-of-view branch;
  - optionally stamp `lastGiven` at the verdict instead of in `begin`.
- Make IGNORED readable: either raise `Brandish_Near_Cop` to 67 or more (so 67 × 1.5 ≥ 100 gives star 1), or decay heat that stays below star 1.
- Document in `cops.yml` that the cooldown is per player and shared across all cops, and that only beat cops warn.

### Red tests to write
- `BrandishWarningsTest.ignoredWarningCommitsSeenBrandish`: add `verify(player).sendMessage(<Patrol.Ignored>)`.
- `outOfViewAtDeadline_noCrime`: add a message assertion.
- `HeatLedgerTest`: a Brandish case under the default settings that pins the chosen outcome (stars ≥ 1, or heat decays).
- `SuspectListener`: a crime that leaves the player at 0 stars stores no description, if the owner picks that option.

---

## Docket bookkeeping
- **Existing open rows:** T-131, T-149 (no live row) and T-148 (no live row) are fixed by spawn-dup fix 1. T-130's pinned test flips. T-109, T-114 (distance-only audience, by design) and T-147 provide context. T-186's flaky line-of-sight row may be explained by tips counting as sightings (INFER). T-344 is the lookout relay. `triage/new-findings.txt:129` is the earlier Route rewording.
- **NEW rows needed** (add to `triage/<slug>.txt` and rebuild):
  - spawn-dup: evasion clear-on-leash; spawnDueUnits need re-check; Dispatch_En_Route cooldown 0; spawn-area vs leash margin; PatrolManager respawn cooldown.
  - radio-far: Handoff heading and addressee; Standoff UNREACHABLE sight gate; Overwatch_Set gates; Route-while-searching wording; **KS-** row for `reportTip` / contact-gated pursuit goal in the cross-docket.
  - brandish: items (a) through (f).
- **Local/live mismatch:** CJ-05 shows as fixed in the live db but has no fix record in the local `bugs.json`. Reconcile it.

## Owner decisions
1. **What should an ignored warning do?** Give a guaranteed star, a fine, more heat (today: hidden 37.5 heat), or nothing plus a message?
2. **When should the warning cooldown start?** At the warning (today) or at the verdict? And should darkness shrink the warning range?
3. **Should heat and suspect descriptions at zero stars decay or clear?** On a timer, or on quit?
4. **What happens to leashed walkers?** Release them when out of view (recommended), or count them toward the cap, which risks T-130's gap of up to 300 s with no cops?
5. **Should shot noise and the recycle tip (T-147) still drive a live-position chase,** or become last-known-only tips?
6. **Hearing range:** keep Target_Range 64 (hearing pursuers from far off as tension) or cap it near Alert_Range 40? And should the Handoff line still be broadcast to the player?
7. **Post-escape search:** keep refilling a 1-star squad for 120 s, or hunt only with the squad that already exists?

## Overall fix order
1. **Keystone `reportTip` + contact-gated live goal** (KS bump). This is the prerequisite: it clears radio cause 2, No_Route, the perception widening, and the tip-driven chatter after Handoff/Standoff. Any spawn-dup tip must use it.
2. **spawnTick `removeIf`:** release out-of-view stranded walkers and tip the squad through `reportTip` (spawn-dup causes 1, 3 and 5).
3. **EvasionClock:** pause on an active bias or pending units instead of clearing (cause E).
4. **Radio gates and wording:** Handoff heading from the squad and no addressee; Standoff UNREACHABLE gate; Overwatch_Set line-of-sight check; `Route_Search` pool.
5. **`spawnDueUnits` need re-check** plus a per-group Dispatch_En_Route cooldown.
6. **Brandish messages and heat,** once the owner answers decisions 1-3.
7. **Spawn-area margin inside the leash; PatrolManager respawn cooldown.**