# Cops N Crooks 0.16 + Keystone 1.15.0 - final fix round 1

Lanes:
- Gangland: `E:/Programming/java/wt/cnc016-fix1`, branch `cnc-0.16-fix1` (from `0.16.0` @ 2b9a7221)
- Keystone: `E:/Programming/java/wt/ks115-fix1`, branch `ks-1.15-fix1` (from `phase-h14-dispatch-posts` @ 1f818e7)

Nothing installed, merged or pushed. Bug docket rows are not written from this lane; the orchestrator records them.

## Confirmed findings

| # | Finding | Result | Commit | Red-first test |
|---|---------|--------|--------|----------------|
| 1 | Wipe breather skips units already queued; `missing <= 0` returns before `Wipe_Refill` (CopManager.dispatchMissing) | fixed: new `CopGroup.holdPendingUntil(at)` pushes every queued unit to `now + breather + ETA` on a wipe; the early return no longer skips a wipe, so `Wipe_Refill` always goes out | 43e39053 | `CopManagerDispatchTest.wipe_unitQueuedBeforeIt_waitsOutTheBreather` (red: 22000 vs 36000), `wipe_everySlotAlreadyQueued_stillRadiosAndHolds` (red: no radio) |
| 2 | `Rejoin_Grace_Seconds` documented as "pending units survive logout" (inverse of the code) | fixed (doc only): cops.yml comment, `DispatchSettings` @param, `documentation/features/cops-n-crooks.md`, `documentation/developer/configuration.md` reworded to "delay before the first units after a rejoin; a logout drops the queue; clock holds". migration-0.16.0.md does not mention the key | a88dffa4 | n/a (doc) |
| 3 | `spawnDueUnits` runs the full search chain for every due unit after a failure | fixed: the first null from `spawnUnit` requeues that unit and the rest of the due list untried (the 0.15 "stop trying this interval" rule) | 18d81c82 | `CopManagerDispatchTest.failedSpawn_isRequeued` now asserts 1 call on the failing tick and 3 total (red: 2 calls) |
| 4 | Cold-trail backup hold read stale when the suspect kills a cop (critical) | fixed: `CopListener.onCopDeath` clears `backupHeld` before `memberDown` when the killer is the group's target (killing a cop is a crime); another killer leaves it | fb0f7cf1 | `CopListenerDeathTest.suspectKill_releasesTheBackupHold_beforeManDown` (red: hold still true at MAN_DOWN), plus `otherKiller_keepsTheBackupHold` |
| 5 | Waypoint row skipped at load: id reused by the next waypoint; `refactorIds` empties the table then NPEs | fixed: `WaypointRepository.getHighestStoredId()` (recorded before the skip, as `CopSpawnerRepository`), `WaypointManager.initialize` floors on it; `refactorIds` deletes and reinserts loaded rows one by one and renumbers around unloaded ids (no delete-all) | a671b163 | `WaypointRepositoryTypeTest.unknownTypeRow_countsTowardTheHighestStoredId`, `WaypointManagerIdsTest.initialize_floorsTheNextIdOnTheHighestStoredRow` (red: 3 vs 6), `refactorIds_keepsUnloadedRow_andRenumbersAroundIt` (red) |
| 6 | `EntitySpawnerTest` ray stub never checks NEVER / ignorePassable / +1.6 (Keystone) | fixed: `isOutOfSight_tracesFromTheEyeToTheSpotPlus16_ignoringFluidsAndPassableBlocks` captures the call. Mutation-checked: ALWAYS/false in EntitySpawner -> red; dropping the +1.6 -> red (65.6 vs 64.0); source restored | e787efa (Keystone) | the new test itself (red under both mutations) |

## Minors

Applied:
- M3: `ChaseConfig` warns `config.conflict` when `Outside_Zone_Speed > Max_Speed` - 258c801d, red-first `ChaseConfigTest.outsideZoneSpeedAboveMaxSpeed_warns`.
- M4: wanted.yml `Quiet_Speed` comment reworded (crime-anchored; signs/commands do not count; never while seen) - 31f00bd0.
- M5: same as confirmed #2 - a88dffa4.
- M10: `BribeStarsTest.neverSightedBySquad_takesTheStar` (Long.MAX_VALUE sighting age) - eac3c6ce. Characterization pin, passes on the current code. The null-group half is already covered by `wantedPlayerInRange_losesOneStarWithContactCause_andTheItemGoes` (groupOf returns null there, so flipping the null check would turn it red).
- M11/M19: `BribeStars.tick` drops `told` entries older than `TOLD_EVERY_MS` (same behaviour, no growth) - 83dba2ea.
- M14: `PlaceNames` catches `RuntimeException | LinkageError` - 0c1301c0, red-first `PlaceNamesTest.linkageErrorProviderIsSkipped` (NoClassDefFoundError escaped).

Rejected:
- M1 (reset breatherUntil/bias in onWantedEnd): the suggested fix is wrong as written. With breatherUntil reset to 0, the old casualty still inside the wipe window satisfies `lastCasualtyAt > breatherUntil` on the new crime's first tick, so the new episode gets a fresh full breather (13 s) instead of the ~12 s remainder. A correct fix also has to clear the casualty history. Not cheap; left as a follow-up.
- M7/M18 (remove deathLocations on quit): it would break the hospital redirect. A player who quits on the death screen rejoins still dead, and his respawn then fires PlayerRespawnEvent, which needs the saved death location. The entry is bounded at one per UUID and overwritten on the next death, as the finding notes.

Not applied (valid, but not cheap or they need a design call): M2 (EvasionClock en-route hold leaves SEEN through the hold), M6 (pending bill past the 500 ms window), M8 (pending bills on disable: charge or forgive), M9 (AspectBasedSignHandler money guarantee test), M12 (station id reuse), M13 (admin region Y extent), M15 (NpcPost ARRIVE_RADIUS 1.5 vs NpcPursuit ARRIVE_DISTANCE 2.0: needs the real pursuit path traced), M16 (hidden-ring attempt budget and chunk loads; #3 cuts the per-tick multiplier on the Gangland side), M17 (glass/fences count as cover).

## Tests

- Gangland `mvn -pl gangland-impl,gangland-features/cops-n-crooks -am test`: BUILD SUCCESS. core 124, item 48, sign-api 66, gangland-api 154, gangland-impl 453, gang 142, civilians 91, turf 117, cops-n-crooks 992. 0 failures.
- Keystone `mvn -pl keystone-npc -am test`: BUILD SUCCESS. keystone-npc 324 (common 272, bean 66, item 143, persistence 443, testkit 5). 0 failures.

## Round 2

Lane: `E:/Programming/java/wt/cnc016-fix1`, branch `cnc-0.16-fix1`, headBefore `0c1301c0`. Findings: `review-round-2.json`.
Nothing installed, merged or pushed; docket rows are left to the orchestrator. graphify was silent on the new 0.16 classes (the graph is built from the main checkout), so the files the findings named were read directly.

### Confirmed findings

| # | Finding | Result | Commit | Red-first test |
|---|---------|--------|--------|----------------|
| 1 | `EntityDamageListener.onPlayerQuit` wiped the pair cooldowns, takedown tally and `lastHit` stamps, so a relog reset every exploit guard | fixed: a quit clears only the fight state (`lastExchange`, `firstHit`, `damage`, and `provoked`, the first strike's flag, which is recomputed at the next first strike). `lastHit`, `cooldowns` and `takedowns` survive and expire by time in `pruneStale` | 28c52dca | `pruneAndQuit_dropTheNewMemories` flipped (1 memory survives a quit, not 0); new `relog_keepsTheTakedownTally`, `relog_keepsThePairCooldown`, `relog_keepsTheProvocation` (all red: wanted 0, expected 1) |
| 2 | Ward bill dodged by emptying the wallet while downed | fixed with **escrow at the down**: `onPlayerDowned` withdraws the quote at once, clamped to the wallet at that moment, and stores the amount taken. `PlayerUndownedEvent` only sends the Ward bill line. A real death after the down counts that payment and is not charged a fresh quote. Why not block commands while downed: `/glw respawn` must still work, an economy-command list is fragile (shops, `/pay`, other plugins), and escrow also ends the round-1 M8 question (bills pending at disable). Docs: features/waypoints.md, migration-0.16.0.md, developer/configuration.md, tests/features/waypoints.md | c926f198 | `downed_depositEverything_undowned_billStillPaid` (red), `downed_withHospital_takesTheBillSilentlyAtTheDown` (flipped from "charges nothing at the down") |
| 3 | Hoppers and hopper minecarts farm bribe stars; a vanished star re-dropped on the next tick | fixed: new `@ListenerHandler BribeStarListener` cancels `InventoryPickupItemEvent` for a live star (`BribeStars.isStar`). A star that vanishes in a loaded chunk without a take now waits `Respawn_Seconds`; one unloaded with its chunk still returns at once | 022857cb | `BribeStarListenerTest` (2 tests), `vanishedItem_waitsForTheRespawnTimer` (red: 2 items vs 1), `isStar_onlyTheLiveItem` (red); `unloadedWithItsChunk_returnsAtOnce` guards the chunk case |
| 4 | `SetupOutline.points` built the whole edge set before thinning | fixed: the stride comes from the edge lengths (`ceil(4(nx+ny+nz) / (256-12))`) and each of the 12 edges is walked with it. At most 256 Locations are built, whatever the selection size | 9335632d | `hugeSelection_isBoundedByTheCap_notTheEdgeSet` (2M x 256 x 2M; red: timed out after 2000 ms) |

### Minors

Applied:
- M5/M7 (BribeStars stale slots, id reuse): each tick drops and removes the slot of any pickup point that no longer exists, and re-drops a star that is off its point (id reused elsewhere, or pushed by water). In 022857cb; tests `removedPoint_removesItsStar` and `reusedPointId_dropsAFreshStarAtTheNewSpot` (both red).
- M3 (WaypointManager): `refactorIds` floors `Waypoint.setID` on the in-memory ids as well, so a waypoint not yet autosaved keeps its id. 34a07159; test `refactorIds_keepsTheNextIdAboveAnUnsavedWaypoint` (red: 3 vs 6).
- M6 (cancelled star drop): `ContactCommand` refunds the price and starts no cooldown when the level did not drop (b506057e, `cancelledDrop_refunds_andStartsNoCooldown`, red: 4000 vs 5000). `BribeStars` leaves the star at its point (1289d7c9, `cancelledDrop_keepsTheStar`, red). The BribeStarsTest wanted mock now keeps its level.
- M9 (HandoffController null provider): the tick is skipped, with its state kept, while no cop config is loaded. 0632afd3; test `noLoadedProvider_skipsTheTick` (red: NPE).
- M4 (downed hospital pick): the location is recorded at the cancelled lethal hit (`downedAt`) and `performRespawn` picks the hospital nearest it. The entry is removed at the respawn and in cleanup. 9e439109; test `downedPath_usesWhereHeWentDown_notTheSpectatorPosition` (red).
- M2 (stop-at-first-failure): the failing unit is requeued behind the untried ones, so a unit whose spawn is always refused no longer blocks those behind it. cadd45e8; test `unitRefusedEveryTime_rotatesToTheBack` (red: 0 cops vs 1).

Not applied:
- M1 (a stranded RETURNING cop's death re-triggers the wipe): fixing it needs a decision on which casualties count toward a wipe (`recordCasualty` comes from CopListener/CopRadio and does not know whether the cop was counted). Not a cheap fix.
- M8 (HandoffController/QuietTrail per-player maps on quit): QuietTrail's anchors track offline time (`offlineTotalThen`), so clearing them on quit would change the quiet clock across a relog. HandoffController's leak is one small entry per quitter who never returns. Left for a design call.

### Tests

`mvn -pl gangland-impl,gangland-features/cops-n-crooks -am test`: BUILD SUCCESS. item 48, sign-api 66, gangland-api 154, gangland-impl 460, gang 142, civilians 91, turf 117, cops-n-crooks 1002. 0 failures, 0 errors.

## Round 3

Input: `review-round-3.json` (1 confirmed, 4 minors). Gangland lane `cnc016-fix1` (branch `cnc-0.16-fix1`), headBefore `cadd45e8`.
Keystone lane `E:/Programming/java/wt/ks115-fix2` (branch `ks-1.15-fix2` off `phase-h14-dispatch-posts` @ 96372ab).

| Finding | Fix | Commit | Test (red before the fix) |
|---|---|---|---|
| CONFIRMED bribe-star re-drop loop | `BribeStars.tick` drops and measures at `corner + (0.5, 1.0, 0.5)` (free air above the wand block) and zeroes the star's velocity on every pass | Gangland `7a6ba84d` | `BribeStarsTest.wandPoint_starFloatsAboveTheBlock_andIsNotRedropped` (item settles at 10.5/65.4/10.5; red: 6 drops in 6 passes) |
| minor: PerimeterController order | `transitionTo(POSTED)` before `holdPost`, so the chase state's `onExit` `stopNavigation` no longer cancels the post walk | Gangland `7a8e718e` | `PerimeterControllerTest.searchingAtThreeStars_postsMarksmanAndDefender` (InOrder; red: in-order failure) |
| minor: rejoin seeded SEEN | `EvasionClock` fires SEEN only for a real sighting: not for a seeded track, and at the seed -> real flip. All four consumers (ContactDesk, WantedHud, PerimeterListener, api) see one consistent "a cop has eyes on you" signal; the clock still holds during the seed | Gangland `7a8e718e` | `EvasionClockTest.restoreSeed_firesNoSeen_untilACopReallySeesHim` (red: SEEN fired on the seed) |
| minor: partial contact wipe vs arc.peak | Rule: a CONTACT decrease (contact payment or bribe star) marks the arc `boughtDown`; `onChaseEnd` skips `learner.record` for it. Chosen over lowering `arc.peak` because live AUTO reads the peak (rampage/petty), so lowering it would let a buy-down earn petty-speed drops | Gangland `7a8e718e` | `ChaseArcListenerTest.contactBuyDown_thenEvasion_isNotLearned` (red: recorded) |
| minor: Keystone CLAUDE.md H14 | H14 (v1.15.0) sentence appended to the phase list | Keystone `b6f6ff9` | docs only |

Rejected: none. Known ceiling: a chase bought down and then re-escalated by crime is still not learned (conservative; no live-drop effect).
Tests: `mvn -q -pl gangland-features/cops-n-crooks,gangland-impl -am test` green: cops-n-crooks 1005, gangland-impl 460, 0 failures.
