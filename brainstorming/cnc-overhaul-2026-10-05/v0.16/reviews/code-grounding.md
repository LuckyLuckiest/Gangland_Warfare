# Code-grounding review - CNC 0.16 (CONTRACTS.md + PLAN.md)

Checked against Gangland master 629af929, worktree wt/gangland-0.15.2 (branch 0.15.2), Keystone master b818483.
Most paths/classes/lines ground correctly (api Waypoint/WantedCause/GanglandApi, Settings blocks, DataConfig.bankTiers, TurfManager.findAt, Turf/CuboidRegion getters,
WantedAspect/AspectBasedSignHandler/SignManager:157,257/WantedSign:38, CopManager spawnTick/recycles/addAiTickHook, CopGroup backup/tip-off/casualty members, EntityDamageListener
:54/:181/:229/:241, EvasionClock hasLiveCop, EntitySpawner :193/:246, module tiers). Defects below.

## Important
1. C7/T5 `opening` fix cannot be built where the plan says. The 0.15.2 builder is `ChaseArcs.view(UUID, List<CrimeRecord>, AutoSettings)` (ChaseArcs.java:128-141); it has no
   HeatSettings, and `CrimeRecord(crimeId, heat, at, location)` carries after-multiplier heat, not the raw weight. Needs a signature change (HeatSettings or weight function) whose
   callers are outside T5's file list: ChaseArcsTest (3 sites), ChaseArcListenerTest:92, and the EvasionClock call 0.15.2 adds. Specify a 3-arg overload/new param + owner.
2. C7 "ChaseConfig gains bribeStars()" is a 5th record component; `new ChaseConfig(4 args)` is used in 9 test files not owned by T5 (JailIntakeServiceTest:140, ChaseArcListenerTest:201,
   WantedHudListenerTest:183, HeatWantedTrackerTest:101, EvasionClockTest:77 with nulls, HeatLedgerTest:95, HudFixtures:51, ChaseLearnerLifecycleTest:66). Contract only promises kept
   constructors for EvasionSettings/AutoSettings. Require a 4-arg overload defaulting BribeStarSettings.DEFAULT.
3. C6/T15 type mismatch: contract `compassWord(Location, Location)`, T15 calls `copRadio.compassWord(post, player)` (CopNpc, Player). Does not compile; say `post.getEntity().getLocation()`.
4. New radio lines would be silently dropped. `SquadRadio.speak` (gangland-api npc/radio/SquadRadio.java:176-240): non-priority lines obey the 20-tick player gap, and the priority line
   Dispatch_Wanted stamps `lastHeard`. Dispatch_En_Route/Wipe_Refill (non-priority, Dispatch origin) sent within a second of Dispatch_Wanted are suppressed; acceptance S2/S4 greps them.
   C6 says add them to "the Dispatch-origin key list (:214-216)" - that list is `Cops.Radio.Priority` (cops.yml:207-221) and C6 in the same paragraph says "no Priority entries". Contradictory;
   decide: put the dispatch-origin keys in Priority (note an old server's list replaces it, so also bypass in code) and add a test that En_Route after Dispatch_Wanted is delivered.
5. Keystone C3 observer set includes Citizens NPCs. `world.getPlayers()` returns player-type NPCs (cops, civilians); `SquadRadio` filters `hasMetadata("NPC")` for exactly this reason and the
   existing `EntitySpawner.isVisibleToOtherPlayers` (:119) does not. A 4-cop squad would "see" most ring spots and push every spawn to step 4. Specify `!NpcSupport.isNpc(p)` and test it.
6. CopSpawnManager overrides `isOutdoor` with the `anyRoof` toggle and `findRingLocation` (CopSpawnManager.java:~95-115) so an indoor suspect still gets ring spots. C8 steps 2-3 call
   `findHiddenSpawnLocation` directly, bypassing that toggle: indoor suspects always fall to step 4 (not hidden). Wrap the hidden search the same way and add a test.
7. Keystone floor: cops-n-crooks 0.16 calls Keystone 1.15.0 members; plugin.yml/module.yml state no Keystone minimum. A 1.14 server gets NoSuchMethodError in a bean body, which aborts
   onEnable (docket KS-MO-07, still open). Needs a guard or an explicit migration/preflight line, not only the pom bump.

## Minor
8. T2 "reuse the yaml/pitch maths of faceTarget (:656)": `AbstractNpc.faceTarget` is a one-line delegate to `NpcCombatDelegate.faceTarget` (:169, with applyAimError, Player-only); a
   Location-taking helper is new code, not reuse. `destroy` clears via `cleanupTransientState()` (AbstractNpc.java:~381) - clear the post there.
9. `CopManager.fightResisting` (:795-805) moves every non-GUARDING/RETURNING cop to COMBAT, including POSTED; the post stays set. Release the post in `PostedBehavior.onExit`.
10. `SignInteraction.java:38` does not guarantee "no money moves on refusal"; `AspectBasedSignHandler.canHandle` (handler/AspectBasedSignHandler.java:41) does (aspects are [money, wanted],
    WantedSign:43). Re-cite.
11. Stale citations: `CopSpawnerRepository:275` (file is 72 lines); T19 lists `documentation/tests/features/{wanted-bounty,waypoints}.md` which do not exist (mark new); test is named
    `victimStruckFirst_killIsSelfDefence_noStarNoNotoriety` (EntityDamageListenerTest:333); `Messages` constants need `Type.PREFIX`/`Type.OTHER`, not bare `PREFIX`; C3 names
    `Phase1_Attempts`/`Max_Distance` are yml keys, the Java getters are `getSpawnPhase1Attempts()`/`getMaxSpawnDistance()`.
12. Rampage opening: cutoff is `crimes.get(0).at() + openingSeconds`; once cheap crimes are filtered the window should start at the first counted crime or a cheap first crime shortens it.
