# Review: contracts and parallel safety, second pass (lens contracts-parallel-2)

Checked the revised PLAN.md / CONTRACTS.md / CONSTRAINTS.md against Gangland master 629af929, the 0.15.2 worktree
`E:/Programming/java/wt/gangland-0.15.2` (branch 0.15.2, read 2026-10-07 ~12:50) and Keystone master b818483. graphify
oriented on master classes (CopState and its neighbours); 0.15.2-only classes were read in the worktree.

The file x task matrix per wave is disjoint (W1: T3/T4/T5 and the two Keystone lanes; W2: T6-T10; W3: T11-T15; W4:
T16-T18). Every construction site of a changed constructor is owned (`new CopManager(`, `new CopRadio(`, `new EvasionClock(`,
`new SignManager(`, `new GanglandPlaceholder(`, `new WantedAspect(` all sit in the owning lane's files). Enum appends
(CopState.POSTED, WantedCause.CONTACT, WaypointType.HOSPITAL) break no switch: every switch over them has a `default`.
No subclass already declares `getPost/holdPost/releasePost/tickPost/isOutOfSight/findHiddenSpawnLocation`. The defects
below remain.

## Critical
1. **CopConfigProvider is not a bean, but two seams take it as one.** PLAN T17 fixes the bean as
   `handoffController(CopManager, CopRadio, CopConfigProvider)`, and C8 gives `Dispatcher(StationRegistry, CopConfigProvider)`.
   No configuration produces a CopConfigProvider: no config class names it, and nothing calls registerInstance for one. Every
   holder reads `copLoader.getLoadedProvider()`: CopRadio.java:67, CopManager.java:88 and :388 (re-read on reload),
   CopSpawnManager.java:42 and :161, ShotNoiseListener.java:52. CopLoader also swaps in a new instance on every load
   (CopLoader.java:44, :71). Keystone `BeanFactory.resolveParameter` (keystone-bean BeanFactory.java:533-538) throws
   `IllegalStateException("Cannot resolve parameter ... no bean of that type is registered")`, so cops-n-crooks fails to
   bootstrap. HandoffControllerTest and DispatcherTest build these classes by hand, so no lane test catches the failure; it
   first shows up when T21 boots the merged build. If a lane passes `copLoader.getLoadedProvider()` at bean time instead, the
   Dispatch/Handoff knobs stop following `/glw reload`.
   Fix: C8 `Dispatcher(StationRegistry, Supplier<CopConfigProvider>)`; T17 bean `handoffController(CopManager, CopRadio,
   CopLoader)`. Both read `copLoader.getLoadedProvider()` per call with `requireNonNullElse(..., X.DEFAULT)`, the
   CopRadio/CopFieldCare pattern. State the same rule for PerimeterController (C9) and the Station_Radius reads in T7 and T12.

## Important
2. **The 0.15.2 base is still moving, and C7 is written against an older ChaseArcs.view.** The worktree reflog shows five
   commits at 12:47 (6dc9bf7a..63ef767c), then `reset: moving to aa2cd39c` at 12:48. ChaseArcListener(+Test) has uncommitted
   edits. Commit 6d85250e adds `ChaseArc.begunAt`, an `onChase` prefilter inside `view` (it drops ledger crimes older than
   chase start - Opening_Seconds) and a new `hadCrime(UUID, List<CrimeRecord>, AutoSettings)`. C7 computes the 4-arg view's
   cutoff over the raw `crimes`. If that fix lands, a T5 lane that follows C7 literally re-opens it (the prefilter goes) or
   conflicts with it. The cites are already stale: `arcs.view` in EvasionClock.views is now :295 (C7/T5 say :290), and the
   `verify(arcs, never()).view` line in EvasionClockTest is :754 (C7/T5 say :666).
   Fix: before W1, freeze and merge 0.15.2, re-read ChaseArcs.view, and write into C7 "heavy-weight rule applied after
   `onChase`", with fresh line numbers.
3. **The cold-trail anchor can be a crime from before the chase (C10 QuietTrail).** `quietMs = now - ledger.lastCrime(id).at()`.
   `HeatLedger.lastCrime` (HeatLedger.java:160) returns the last entry of the player's ledger with no chase bound. 0.15.2's own
   6d85250e message says "HeatLedger keeps sub-threshold crimes with no decay, so a crime from hours before a chase counted".
   A sign-, admin- or kill-combo-raised chase over an old ledger entry therefore gets Quiet_Speed Max and backup held as soon
   as the first sighting goes stale. That contradicts R12 ("a sign-raised chase gets cold-trail speed from its first minute").
   No QuietTrailTest case covers it.
   Fix: anchor = the later of lastCrime.at() and the arc start (begunAt / offline-shifted startedAt, minus Opening_Seconds,
   matching onChase). Add the test `staleLedgerCrimeBeforeTheChase_quietRunsFromChaseStart`.
4. **T12's setup.yml never reaches the data folder.** Module YAMLs are copied out of the module jar only when listed in
   `CopsNCrooksYamlConfig.FILES` (CopsNCrooksYamlConfig.java:36-37, KERNEL phase). The list is pinned by
   `CopsNCrooksYamlConfigTest.shippedDefaultsLiveInModuleFolder`. T12 owns only CopsNCrooksFileConfig and
   RegistryModuleConfig, and no W3 lane owns CopsNCrooksYamlConfig. The lane must either stop ("touch only files you own") or
   ship a file no admin ever sees; unit tests with a mocked FileManager do not notice.
   Fix: add `CNC/config/CopsNCrooksYamlConfig.java` (FILES += "setup") to T12's W3 row.
5. **T17's end-to-end test cannot reach T11's fixture.** `CopManagerFixture` is a package-private `final class` with a
   package-private constructor in test package `npc.police` (CopManagerFixture.java:55, :72). HandoffControllerTest mirrors
   `npc.police.handoff` (CONSTRAINTS: mirrored package). T17's Done-when still demands "the bias reaches Dispatcher/spawnUnit
   in a test built on T11's fixture". The sequenced list says "CopManagerFixture T11 only (T16/T17 read it)".
   Fix: give T17 a new `npc/police/CopManagerHandoffTest.java` (the fixture's package), or have T11 make the fixture public.

## Minor
6. T15 "Depends on: T4, T6" leaves out T7. T15 calls `CopRadio.placeOf` (C5, T7) for the Post_Up/Eyes_On places. Wave order
   covers it; make the edge explicit, as was done for T16/T17 -> T15.
7. T11 step 3 `getSquadTiers(level).get(slot)`: the fixture leaves `getSquadComposition` unstubbed (a Mockito empty list), so
   `nextSlot` is -1 and the tier list is empty. Taken literally, this throws IndexOutOfBounds in every legacy squad test. Say
   `slot >= 0 && slot < tiers.size() && tiers.get(slot) > 0`.
8. T6 `KeystoneFloor.satisfied`: "unparsable -> true with a warning", but `PluginVersion.parse` never throws (it coerces to
   0.0.0, keystone-common PluginVersion.java:12-27). Garbage therefore reads as 0.0.0 and is refused. Tell the lane to detect
   unparsable input itself (a digits-and-dots check) before parsing. KeystoneFloorTest would catch it.
9. T9: the test helper `hit()` deals 1.0 damage (EntityDamageListenerTest.java:537), below Min_Damage 2.0. Two tests go red:
   `victimStruckFirst_killIsSelfDefence_noStarNoNotoriety` and the unlisted `selfDefence_alsoHoldsWithoutTheTracker` (:404).
   Name both, and say the fix is the helper's damage, not the rule.
10. T21 runs `mvn clean install` in the integration worktree, against CONSTRAINTS ("never install; only the orchestrator /
    Task 6") and R42. T19 (same wave) may also still change `commands.json` after T21's build, so the accepted build would not
    be the shipped one. Use `clean verify`/`package` in T21, and land T19's commands.json fixes before T21 builds (or rerun).
11. Risk 2 ("T12-T14 can still start from the 1.14.0 pin") contradicts the per-wave flow. W3 lanes branch from the integration
    branch after the W2 merge, which already carries T6's `<keystone.version>` 1.15.0, so all of W3 waits for the install.

## Verified OK (this pass)
Settings code defaults reach every impl test through SettingsFixture (BountySetCommandTest posts 1000, above
Takedown_Minimum). Every existing PlayerDeathListenerTest case calls `handleMoney` directly. CopListener tests never verify
`onWantedStart`. EvasionModuleConfigTest is sequenced T15 -> T16. CopSpawner station_id lands at index 7 (the table has 7
columns). Attribute supports `canBeNull`. WantedEvasionStateEvent carries level, zoneCentre and zoneRadius. HeatLedger.lastCrime,
GangMembership.gangIdOf, ChaseArc.startedAt, NpcSquad.lastKnownLocation/millisSinceSighting/hasFreshSighting,
CopGroup.markTipOff, AbstractNpc.canSee/navigateTo/stopNavigation and SpawnConfigProvider getters all exist. Keystone
`<revision>` is at pom.xml:62. Resource filtering is on for every module (root pom.xml:515-520), and Gangland.onDisable already
returns on a null context.
