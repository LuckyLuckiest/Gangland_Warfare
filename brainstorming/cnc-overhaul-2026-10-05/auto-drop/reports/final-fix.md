# Final fix: Gangland 0.15.2 (branch 0.15.2, worktree E:/Programming/java/wt/gangland-0.15.2)

Base: `aa2cd39c`. Five commits on top, all local and unpushed. The graphify graph in the main checkout has no
`ChaseArcs` nodes, because these are new 0.15.2 classes that are not on master. I therefore read the files directly.

Verification: `mvn -q -pl gangland-features/cops-n-crooks -am test` passes on `604e72de` (exit 0). cops-n-crooks
surefire ran 755 tests with 0 failures and 0 errors. Each code commit's stage ran ChaseArcListenerTest,
ChaseArcsTest and EvasionClockTest green before it was committed. Every new behavioural test failed against the
pre-fix code first; the failure output is quoted below.

## Confirmed findings

| # | Finding | Result | Commit | Test (red before the fix) |
|---|---------|--------|--------|---------------------------|
| 1 | ChaseArcListener: no prune on the RESTORE (join) path (decision-rules lens) | fixed | `26535f2e` | `ChaseArcListenerTest.restoreStart_afterLongOffline_prunesFirst`: was `expected <RESTORE> but was <CRIME>` |
| 2 | Same defect as #1, from the events-order lens | fixed by the same change | `26535f2e` | same test |
| 3 | EvasionClock: `arcs.lost` stamped on a null-track search | fixed | `6d658055` | `EvasionClockTest.replacementSquad_neverSeesHim_keepsLastLostAt`: was `expected <1002000> but was <1004000>` |
| 4 | ChaseArcs.view: stale sub-threshold ledger crimes counted on a later chase | fixed | `e441a06c` | `ChaseArcsTest.view_ignoresStalePreChaseCrimes` (was 4 crimes, expected 3); `ChaseArcListenerTest.chaseEnd_withOnlyAStalePreChaseCrime_leavesNoRecent` (was 1, expected 0); guard `ChaseArcsTest.view_afterRestore_keepsTheChasesCrimes` (green before and after) |
| 5 | configuration.md: no reference list of the 29 Auto keys | fixed (it is really a minor) | `604e72de` | docs only |

### 1 and 2: prune on join
`ChaseArcListener.onStart` now calls `arcs.prune()` before the `RESTORE && has(id)` check. A player who was offline
for more than 30 minutes now always comes back with a fresh RESTORE arc: startCause RESTORE, quits 1, quiet 0, and
the chase is never learned from. Before, that only happened if another player's chase start had pruned the arc in
the meantime.

### 3: lastLostAt only from SEEN
`arcs.lost(id)` moved inside the existing `if (track != null)` block, next to the `narrow` computation. A null track
cannot be a SEEN track, because `clear()` removes the track and SEEN always creates one.
`arcs.searchStarted(id)` still runs on both paths, so respots keep counting. The existing
`returningSquad_comesBackAndSees_countsOneRespot` test still passes. If a chase never had the player in sight, its
contact time is now 0 instead of the time of the first search, which matches PLAN 3.5 ("stamped at the SEEN to
SEARCHING switch").

### 4: stale pre-chase crimes
Two fixes from the finding would have dropped crimes that belong to the chase:
- "Clear the ledger at WantedStartEvent", or "count only crimes after the start": both drop the crime that
  triggered the chase. `HeatLedger.record` appends the crime before the star trigger fires the start, and the
  build-up crimes come before it too. The PLAN's own E1 fixture has crimes at -25 s and -15 s before the arc's
  start.

What I did instead: `ChaseArc` keeps a `begunAt` stamp that `restore()` never shifts. The crime stamps in the ledger
are never shifted either. A ledger crime counts as on the chase only if it is no older than `Opening_Seconds` (30)
before `begunAt`. That keeps the build-up, and a crime from hours earlier drops out. The filter is applied in
`ChaseArcs.view()` (crimes, the opening anchor and copKilled). `ChaseArcListener.onChaseEnd` now asks
`arcs.hadCrime(...)` instead of checking that the ledger is non-empty, so a stale crime can no longer turn an admin
chase into a crime chase for `Repeat_Chases`.

Knock-on effect: when an arc was pruned (offline more than 30 min), the RESTORE arc no longer sees the old chase's
crimes. That is the same as after a restart, where PLAN line 513 already says the ledger is empty.

Not changed: the ledger keeping sub-threshold heat with no decay. That is 0.15.0 heat design, and the stale crime
still adds its heat to the star count. Ledger decay belongs in the 0.16 plan, not this fix.

### 5: Auto keys in the docs
The finding is real but overstated. T9 asks for "the `Auto` keys" and does not ask for a table, which one skeptic
also pointed out. I added the table anyway, because it is cheap and the section listed no key names at all. It
covers all 29 keys, their defaults (`AutoSettings.DEFAULT`, which matches `wanted.yml`) and their ranges (from
`ChaseConfig.auto/learning`).

The finding suggested "clamped". That is wrong for scalars: Keystone `NodeReader.min/max` reports a `config.range`
error and uses the default. The docs say that, plus how the two lists are handled: a `Typical_Seconds` entry below 5
uses the previous entry, and an `Escape_Rate` entry is clamped to 0 to 1.

Two existing claims in that section did not match the code, so I corrected them:
- "Learning sub-block (7 keys)" is now 11 keys.
- "seconds since the first crime" is now "seconds since the chase began". `chaseMs` is measured from `startedAt`.

## Minors

| Minor | Result | Commit | Test |
|-------|--------|--------|------|
| Stale DropPlan after a cancelled drop (`dropped == 0`) | applied: `if (auto && dropped == 0) arcs.takePending(id)` | `7eeb2863` | `EvasionClockTest.auto_cancelledDrop_clearsThePendingPlan` (red: `expected <null> but was <DropPlan[stars=2, ending=PETTY...]>`) |
| `recent` map keeps empty rows / `autoFailed` never cleared | half applied: `recentEnds` removes a row once its window is empty. `autoFailed` left as is, because "logged once each" is the PLAN line 615 contract and clearing it on `clear(player)` would log again on every tick | `7eeb2863` | trivial, no test |
| `restoreLevelChange_isIgnored` asserted only `quietMs >= 0` | applied: asserts `quietMs == 1500`, DisplayName updated | `7eeb2863` | test change |
| `quit_withAnArc_counts` DisplayName overstated what it checks | applied: DisplayName reworded, plus a reflection guard that no `ChaseArcListener` constructor takes a `UserManager` | `7eeb2863` | test change |
| BBCode `[URL]../features/wanted-bounty.md[/URL]` | applied: `[URL='https://github.com/LuckyLuckiest/Gangland_Warfare/blob/master/documentation/features/wanted-bounty.md'][B]Wanted & Bounty[/B][/URL]`, the same form as FRONT-PAGE.bbcode.txt. A relative path is dead on a forum either way | `604e72de` | docs |

## Commits (branch 0.15.2)
- `26535f2e` cops-n-crooks: prune long-offline chase arcs on join, not only on another player's start
- `6d658055` cops-n-crooks: stamp lastLostAt only at the SEEN to SEARCHING switch
- `e441a06c` cops-n-crooks: ignore stale pre-chase ledger crimes in the AUTO view
- `7eeb2863` cops-n-crooks: drop a cancelled drop's AUTO plan, forget empty recent rows, tighten arc tests
- `604e72de` cops-n-crooks: document the 29 Wanted.Evasion.Auto keys, fix the changelog BBCode link

## Left for the orchestrator
- Bug docket: these are review findings on unmerged 0.15.2 code and have no docket rows. Record them when the wave
  lands, if the wave wants them on the docket.
- Ledger decay for sub-threshold heat: a 0.16 candidate (see #4).

## Round 2

Three confirmed findings, which come down to two root causes, and two minors. All of them are fixed in `8214424b` on
branch 0.15.2. `mvn -q -pl gangland-features/cops-n-crooks -am test` is green (759 tests, 0 failures, 0 errors).
Each new test was run against the pre-fix code first and failed.

| Finding | Root cause and fix | Test (red output before the fix) |
|---------|--------------------|----------------------------------|
| C1 / minor 2: a SEEN track dropped by `clear()` never stamps `lastLostAt` | Since `6d658055`, the only place that stamps a loss is the SEEN to SEARCHING switch in `tick()`. When `clear()` removes a SEEN track (quit, every cop dead, every cop RETURNING), that also ends contact. Fix: `EvasionClock.clear()` now calls `arcs.lost(id)` when the removed track's state is SEEN. On a quit, `restore()` shifts the stamp together with `offlineAt`. `clear()` from `WantedEndEvent` has no effect, because the arc ended at HIGH before that. | `EvasionClockTest.squadDiesWhileSeen_stampsLastLostAtAtTheWipe` (`expected <1006000> but was <0>`), `clearWhileSeen_stampsLost_clearWhileSearching_doesNot` (`expected <1003000> but was <0>`) |
| C2 / C3: a getaway with `contactMs == 0` pins the typical median at 0 | A chase the squad never sighted measures no contact. The first sample was set to `typical = 0`, and the multiplicative step cannot leave 0. Fix: `ChaseLearner.record()` updates typical/count only when `contactMs > 0`. The n/escaped update is unchanged. | `ChaseLearnerTest.zeroContactGetaway_teachesNoTypical` (`expected <0.0> but was <1.0>` on typicalCount). The test checks that a following 60 s getaway seeds typical at 60. |
| Minor 1: with `Opening_Seconds: 0`, the crime that triggered the chase falls out of the look-back | `begunAt` is set by the MONITOR start listener, which runs after the ledger has stamped the crime. Fix: `ChaseArcs.onChase()` subtracts a fixed `START_SLACK_MS = 1000` that does not depend on `Opening_Seconds`. | `ChaseArcsTest.view_openingZero_keepsTheTriggeringCrime` (`expected <1> but was <0>`). The test also asserts `copKilled` and `hadCrime`. |

Rejected: none.

Not done: the `typical == 0` reseed guard that C3 also proposes. Now that a 0 sample can no longer be written, the only
way to reach 0 is a row persisted by an earlier 0.15.2 build. That code is unreleased, so no deployed database has such
a row.

Commit: `8214424b` cops-n-crooks: stamp lost sight when a SEEN track is cleared, skip zero-contact typical samples,
slack the opening look-back

## Round 3

No confirmed findings. Applied all four minors, each red first: the five new tests failed against the pre-fix main
sources and pass after the fix. The module suite is 764 tests, 0 failures.

- **RESTORE seed sighting counted as contact** (CopManager.java:121 -> EvasionClock). Option 2 was chosen. Option 1
  would have left the respawned squad without a last-known location. `ChaseArc.seededAt` is stamped by `restore()`
  and by a RESTORE `start()`. `ChaseArcs.restoreSeed(id, sightingAt)` is true while the squad's latest sighting is no
  later than `seededAt + 1 s`. A SEEN track opened by the seed alone calls neither `arcs.seen` nor, at the switch,
  `arcs.lost`/narrow, so the pre-quit loss and respots stay as they were. A real sighting after the seed upgrades the
  track: it calls `arcs.seen` and moves `seenSince` to that sighting. Tests:
  `EvasionClockTest.restoreSeed_isNoRespot_andKeepsThePreQuitLoss` and `restoreSeed_thenARealSighting_countsAsContact`.
- **narrow counted the Lost_Sight_Seconds grace** (EvasionClock.java:183). The stretch now runs to
  `now - millisSinceSighting()`. Added test `auto_lostSightGrace_isNotTimeInSight` (17.9 s in sight + 3 s grace is not
  narrow). `auto_narrowEscape_notLocked_stepsByHalf` now ages the sighting realistically through the new `loseSight()`
  helper, with 24 s in sight.
- **Repeat_Chases > 8 silently disabled the known-face rule** (ChaseArcs.java:25). Fix (a): `.max(8)` in
  `ChaseConfig.auto`, which raises a config.range ERROR and falls back to the default. `configuration.md` and the
  `wanted.yml` comment now say 0 to 8, and `RECENT_CAP` notes the coupling. The test is the
  `ChaseConfigTest.auto_topLevelRange` row `Repeat_Chases,9`.
- **recent deques kept outside AUTO** (ChaseArcs end/recentEnds). `ChaseArcListener.onChaseEnd` passes
  `hadCrime = false` unless `Drop_Mode` is AUTO. The learner still records the chase. Added test
  `ChaseArcListenerTest.chaseEnd_notAuto_leavesNoRecent`. The existing crime-chase test now runs under AUTO. Trade-off:
  a reload into AUTO starts with an empty 30-minute memory.

Commit: `0acc0834` cops-n-crooks: a rejoin's seeded sighting is no contact, narrow ends at the last sighting, cap
Repeat_Chases at 8, recent ends only under AUTO
