# Review: plan-quality lens (AUTO Drop_Mode PLAN.md)

Reviewer scope: is the plan executable (validation and fallbacks, red-first tests, DAG, docs/changelog/docket steps, decisions).
Every item below was checked against the source on 2026-10-07. Worked-example arithmetic (E1-E8, W1-W5, A5 n=1.9 / delta 0.027) was
re-computed and is correct; no finding there. Counts: 0 blocker, 6 major, 9 minor.

## Major

1. **major - Section 4.2 validation describes behaviour the config framework does not have.**
   Evidence: PLAN.md 4.2 says `.min(0)` "clamped" and `>= 1` / `0..1` / `(0,1]` give "WARNING `config.range`, default / clamped".
   Keystone `NodeReader.java:446-458` (IntAccess) and `:496-508` (DoubleAccess): `.min()/.max()` add **ERROR** `config.range` and set
   the state INVALID, so `.orDefault(def)` returns the **default**; there is no clamp and no WARNING. `ChaseConfig.heat()` already
   works this way (`.min(0).orDefault(...)`). Also `evasion()` (`ChaseConfig.java:66-78`) never sees `HeatSettings`, so the INFO row
   "AUTO with Heat.Enable false" cannot be emitted from there; the per-entry list rules (`Typical_Seconds >= 5`, `Escape_Rate 0..1`)
   and the cross-key rules (Narrow > Step, `Min <= 1 <= Max`, Petty vs Rampage) have no framework call at all.
   Fix: split 4.2 into (a) rows done with `.min/.max().orDefault()` (ERROR + default, state that plainly) and (b) rows that need
   hand-written code in `ChaseConfig.auto(...)` emitting `report.add(Severity.WARNING, ...)` and clamping, naming the helper. Move the
   Heat/Evasion INFO rows into `parse()` where both blocks are known. Write the `ChaseConfigTest` expectations (value, severity, code)
   from the corrected table so the tests are red against the old code for the right reason.

2. **major - DAG: T4 and T5 cannot start after T2; they need T3's types.**
   Evidence: PLAN.md section 5 puts `DropPlan`, `ChaseView`, `Learned`, `enum Ending` in `AutoDropPlanner.java` (T3), while `ChaseArc.pending`
   is a `DropPlan`, `ChaseArcs.view(...)` returns `ChaseView`, `stashPending(DropPlan)` (all T4), and `ChaseRecord`/`learner.record`
   need the last drop's `Ending` (3.5, T5). Section 8 gives T4 and T5 only `Depends on T2` and runs `{T3, T4, T5}` in parallel.
   Fix: move the shared value types (`Ending`, `DropPlan`, `ChaseView`, `SpellView`, `Learned`) into T2 (or a new T2b, haiku, S) so
   T3/T4/T5 really are independent; otherwise make T4 and T5 depend on T3.

3. **major - DAG: T7 (clock + bean wiring) needs T6 (repositories); the plan runs them in parallel and allows a hidden merge after T7.**
   Evidence: PLAN.md 5 `EvasionModuleConfig` bean `chaseLearner(ChaseConfigLoader, RepositoryRegistry)` "getRepository as at
   `CopsNCrooksModuleConfig.java:171-174`". Keystone `RepositoryRegistry.java:169-175`: `getRepository` **throws
   IllegalStateException** when no repository is registered. Without T6's `@Repository` classes the learner bean fails during bootstrap
   for every server, including ONE_STAR ones. Section 8 line "Steps up to T7 can merge as a hidden feature" makes that reachable.
   Fix: `T7 depends on T6`, lane order `{T6, T8}` then T7; or have the bean method tolerate a missing repository (not recommended).

4. **major - The "natural getaway" learning rule has no data source for the final drop.**
   Evidence: 3.5 learns `S.typical` only "if the last drop's ending was HUNKER_DOWN or STILL_HOT". 5 says the clock records `lastEnding`
   "after a drop with dropped > 0", but `EvasionClock.java:150-153` returns early when `!wanted.isWanted()`, and the last star's
   `WantedEndEvent` fires **inside** `stars.drop` (`Wanted.java:68-110`: change event, level mutates, end event), where `ChaseArcListener`
   (HIGH) already runs `arcs.end(...)` and removes the arc. `WantedHudListener.onLevelChange` (inside the same call, before the end
   event) has already consumed `pending` via `takePending`. So at chase end neither `pending` nor `lastEnding` holds the final plan;
   a getaway is exactly the case where the final drop is the one that matters. The unit test "only HUNKER_DOWN/STILL_HOT getaways move
   `typical_s`" cannot be implemented as written.
   Fix: have `stashPending` also set `ChaseArc.lastEnding` (before `stars.drop`), keep `pending` as the HUD's separate one-shot, and
   have the HIGH end handler read `lastEnding`. Add a `ChaseArcListenerTest` case: stash -> level change (HUD takes pending) -> end event
   -> record sees the ending.

5. **major - `peak` and the arc are not seeded for the first raise.**
   Evidence: `Wanted.java:68-110` fires `WantedLevelChangeEvent` (0 -> n) **before** `WantedStartEvent`, but `ChaseArcListener`
   (PLAN.md 5) creates the arc only in the start handler and records `peak` in the level-change handler. The first level change has no
   arc to write to, so `peak` stays 0 (or a later raise only) for a chase opened by `/glw wanted add 4` or one big crime.
   Acceptance row A3 (`/glw wanted add 4`, expects `reason=rampage` via `peak >= Rampage_Peak_Level`) and E5-style chases would then
   see `peak = 0` and could fall into PETTY.
   Fix: `ChaseArcs.start(id, cause, level, now)` seeds `peak = event.getWantedLevel()` and `lastHotAt = now`; add a `ChaseArcsTest` row
   "start at level 4 -> peak 4 -> rampage".

6. **major - Release plumbing is missing from the DAG and the base branch is not on master.**
   Evidence: PLAN.md header and 9 ("the 0.15.2 jar ships the Auto block") need revision 0.15.2, but no task changes `pom.xml:57`
   (`<revision>0.15.1</revision>`; `module.yml` takes `${project.version}`). `git merge-base --is-ancestor 046887e1 master` fails: 0.15.1
   (and api 2.2) are not in master, so "own branch cut from 0.15.1" ships only after 0.15.1 does; D1 does not say so.
   Fix: add T-1 (haiku, S): create branch `0.15.2` from `0.15.1`, bump `<revision>` to 0.15.2 (patch-bump convention), no api bump.
   Add to D1: "0.15.1 must be merged to master first, or 0.15.2 is merged together with it".

## Minor

7. **minor - `ChaseRecord` is undefined.** PLAN.md 5 says "records (section 6)", but section 6 only lists the two tables. T5 (opus) must
   invent its fields. Fix: list them: `UUID player, WantedCause startCause, WantedCause endCause, int peak, long chaseMs, @Nullable Ending
   lastEnding, long endedAt`.

8. **minor - T9 (haiku, S) is too vague to execute; existing docs that now lie are not listed.** Evidence: `documentation/developer/configuration.md:464`
   ("`ONE_STAR` or `ALL_STARS` per completed evasion"), `documentation/features/wanted-bounty.md:44`, `documentation/tests/features/wanted-bounty.md:58`
   (manual checklist; the plan adds a manual checklist only to the acceptance README), `documentation/README.md:13` (0.15.0 marked "Current"),
   `documentation/features/cops-n-crooks.md` and `developer/cops-n-crooks.md` (mention evasion). The changelog location is not given;
   the precedent is `documentation/v0.15.0/CHANGELOG.md` + `CHANGELOG.bbcode.txt`. An upgrading 0.15.1 server keeps its old `wanted.yml`
   (no `Auto` block, old Drop_Mode comment), which the docs should say.
   Fix: T9 lists those files, creates `documentation/v0.15.2/CHANGELOG.md` + `.bbcode.txt`, adds the README index row, and adds the four
   AUTO card checks to `documentation/tests/features/wanted-bounty.md`. `migration-0.15.0.md:113` is historical; leave it.

9. **minor - D9 contradicts the repo's docket rule.** CLAUDE.md: a bug that is not in the docket "gets added to `triage/<slug>.txt` and the
   docket rebuilt with `build_docket.py`". PLAN.md D9 offers "accept as designed" as an alternative, and T11 says only "docket check (D9)".
   (The "no evasion entries in the docket" claim is true: no hits for evasion/EvasionClock/HeatLedger/Drop_Mode in either docket folder.)
   Fix: drop the alternative; T11 gets the concrete steps: add the P3 row to `bug-docket-2026-09-06/triage/new-findings.txt`
   (next id after 178), run `python build_docket.py`, republish to the docket artifact, and mirror it in `cross-docket-2026-09-10`.

10. **minor - The planned event-order test has no infrastructure.** PLAN.md 9 `ChaseArcListenerTest`: "both listeners registered, one
    WantedEndEvent". The cops-n-crooks tests have no dispatcher (no `SimplePluginManager`/MockBukkit hits); the repo pattern is
    `CopListenerDeathTest.java:144` asserting `EventHandler.priority()` by reflection. Fix: pin priorities by reflection
    (HIGH vs HeatListener MONITOR) plus a direct-call test that invokes the end handler while a real `HeatLedger` still holds the crimes.

11. **minor - `ChaseLearner` reload/shutdown behaviour is unspecified.** Evidence: `BeanLifecycle.onInitialize(boolean firstLoad)` runs again
    on reload; `JailExitService` reloads from the repository every time. If the learner does the same, every `/glw reload` discards
    updates not yet autosaved. Fix: say "load only when `firstLoad`; `onPreClear` flushes the caches", and test it; also say what a
    reload that flips `Learning.Enable` does to the caches.

12. **minor - Red-first evidence is only a T11 gate and some listed tests cannot be red.** CLAUDE.md requires every new test to be shown
    red before green. T1's "update the unknown-mode test" (`ChaseConfigTest.java:132-139`, value `SOME_STARS`) and 9's "ONE_STAR / ALL_STARS
    tests unchanged" are green before and after; they are regression pins. Fix: in section 9 mark each test "red" (new type or new
    behaviour; names the failing assertion) or "pin" (stays green), and make each task record its red run, not only T11.

13. **minor - The `Min_Time_Factor` comment contradicts the plan's own numbers.** PLAN.md 4.1 says the habit "never shortens a timer below this
    share of Seconds_To_Drop" (0.6), and Risk 4 says "at most 40 %". But 3.4 clamps only the habit, then `factor = clamp(step * habit, Floor, ...)`;
    W3 gives 4.4 s at level 1 (Seconds_To_Drop 10) = 0.44. Shipped YAML text would mislead admins. Fix: "the habit alone never shortens a
    timer below this share; Momentum.Floor is the overall limit", and fix Risk 4.

14. **minor - Acceptance rows A5/A6 name inputs the harness does not have.** A6 needs a "0.15.1-shaped wanted.yml": no step says where it
    comes from (extract from the 0.15.1 jar, like `prep-cnc015.sh:63-64`). A5 says `sqlite3` on the sandbox db without the file path or the
    query; `sqlite3` exists on this machine (`/c/msys64/ucrt64/bin/sqlite3`) but no harness file uses it. T10 also depends on T0 for the
    `copsncrooks/wanted.yml` path used by `yset.js`. Fix: add the fixture source, db path and exact `SELECT` to T10.

15. **minor - "32 keys" (4.1) is wrong.** The block has 29 leaf keys (5 + Petty 2 + Cold_Trail 3 + Clean_Break 2 + Momentum 4 + Repeat 2 +
    Learning 11) and 35 nodes with the six sub-blocks. T9's dead/missing-key lint compares against `AutoSettings.DEFAULT`; give it the right number.

## Checked, no finding

- Open decisions D1-D10 each carry a recommendation; commands.json needs no entry (D7 deferred); no gangland-api change, so no api doc bump.
- The existing `ChaseConfigTest` pin "shipped wanted.yml parses to exactly the in-code DEFAULT without issues" (`ChaseConfigTest.java:44-48`)
  will cover `AutoSettings.DEFAULT` vs the shipped block automatically; the plan's "block round-trips" row matches it.
- `EvasionModuleConfigTest` only tests `installEvasion`, so the new `EvasionClock` constructor does not break it; `EvasionClockTest.setUp`
  (`:112`) and the `new EvasionSettings(...)` calls in `ChaseConfigTest`/`EvasionClockTest` are the ones the plan already lists.
