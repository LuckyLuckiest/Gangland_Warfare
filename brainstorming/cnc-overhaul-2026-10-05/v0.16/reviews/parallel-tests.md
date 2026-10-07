# Review: parallel safety and tests (lens parallel-tests)

Checked against the 0.15.2 worktree (E:/Programming/java/wt/gangland-0.15.2, 276cbbcb) and Keystone b818483.

## Critical
1. T11 changes the CopSpawnManager constructor (C8, StationRegistry) but CopSpawnManagerStationTest, created by T7 in W2 and calling the
   0.15 constructor, is not in T11's file list (only CopSpawnManagerFallbackTest). T11's lane stops compiling its test tree.
   Fix: add CopSpawnManagerStationTest to T11's OWN list (call sites), or keep the old constructor as an overload.

## Important
2. T7 adds PlaceNames to CopRadio's constructor; `new CopRadio(` is also called in CopRadioRolesTest (:107), not owned by T7. Say which
   constructor gains the param (public 3-arg at CopRadio.java:66 vs the package-private lambda test constructor) and add CopRadioRolesTest.
3. T7 appends RegistryModuleConfig to CopsNCrooksModule, but CopsNCrooksModuleTest asserts the exact configuration list (:41-43).
   Existing test goes red; not owned by T7. Add it.
4. EvasionModuleConfigTest does `verify(manager).addAiTickHook(...)` (exactly once) on a container holding only clock/stars/manager.
   T15 (PerimeterController hook, C9) and T16 (QuietTrail hook) each add a hook / bean in the same @PostConstruct, so the test fails by
   TooManyActualInvocations / missing bean. Neither task owns it. Add it to T15 and T16, with a test that every hook is installed.
5. T11 changes `Dispatch_Wanted` to `dispatch(..., Map.of("place", copRadio.placeOf(...)))`. Two consequences:
   a) the fixture radio is a Mockito mock, `placeOf` returns null, `Map.of` throws NPE in every legacy test; use a null-safe map.
   b) CopManagerSquadTest:150 verifies the 5-arg dispatch with times(1); it goes red. T11 says squad tests stay green unchanged and its
      Done-when says "0.15 squad tests pass untouched" - false for this line. State the flip.
6. T5 / C7: the rampage fix needs the crime weights, but `ChaseArcs.view(UUID, List<CrimeRecord>, AutoSettings)` (ChaseArcs.java:~123) has
   no HeatSettings and AutoSettings carries none. C7 never gives the new signature. ChaseArcListenerTest:92 calls the 3-arg view and is not
   owned by T5. Specify: add an overload taking HeatSettings (3-arg delegating with a null/DEFAULT), list ChaseArcListenerTest. Also say
   whether `cutoff` (first crime + Opening_Seconds) keys off the first crime or the first heavy one (cheap-first changes the window).
7. T19, T20, T21 share W5 but T20 "Depends on: T19" (copies configuration.md and migration-0.16.0.md) and T21 depends on T19. The
   plan's own rule (no consuming a same-wave contract) is broken; split W5 or drop the dependency. T21 does not need T19; T20 does.

## Minor
8. T16 and T17 consume `CopState.POSTED` (T15) but list Depends without T15. Wave order saves it; make the edge explicit.
9. T16 pins `postedCop_countsAsLive` and `postedCop_isNeverRecycled` are green at once: hasLiveCop counts every non-RETURNING cop and
   `recycles` only acts on PURSUING/COMBAT already. CONSTRAINTS demands red first; label them characterization pins.
10. Keystone T1 `findHiddenSpawnLocation_*` tests run on ThreadLocalRandom angles; require a deterministic ray stub (blocked by
    coordinate, not call order) or the test flakes.
11. C8 does not say how CopManager gets the Dispatcher (constructor vs setter); T11 owns all sites, so safe, but name it for T15/T17 fixtures.
12. Sequenced-shared list omits ChaseArcs (T5 -> T16) and CopManagerStuckTest/CopManagerFixture (T11 -> T16).
13. R2 widens api 2.3 beyond the owner ruling "exactly three additions" without an OWNER CHECK tag (only R5 has one).
14. T14 snippet has no imports and the turf types live in `turf.data` (Turf, CuboidRegion), not `turf.place`; a haiku lane needs them listed.
15. CONSTRAINTS mandates a "Claude Opus 5.5" Co-Authored-By trailer for sonnet/haiku lanes; wrong attribution.
16. PLAN Risk 1 is stale: the 0.15.2 worktree already holds AutoDrop, ChaseArcs, tables, ChaseLearner and HUD.

## Verified OK
Per-wave file ownership is otherwise disjoint (W1-W4 matrices); T6 pin lands before W3; Keystone T1/T2 are disjoint; mvn paths and -am
usage are correct; PlayerUndownedEvent, WantedEvasionStateEvent, CuboidRegion getters, TurfManager.findAt, Wanted.setLevel(int,cause),
phone slot 31 are present/free; isFreeToRespond excludes POSTED.
