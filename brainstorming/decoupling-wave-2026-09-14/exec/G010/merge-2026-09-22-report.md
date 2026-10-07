# Merge verification report — 2026-09-22

Status: **DONE**. HEAD `5b915c17` ("0.10.0: merge WS3 G1+G2 (gangland-lootchest module, keystone-hologram) after
CUT and WS5 G0", parents `f999ab72` WS5 G0 + `61fb6a6a` WS3 G1+G2 rebased) — tree was already clean on arrival,
confirmed. No commits made this session.

## 1. Whole reactor build

`mvn clean install` (one build) → **BUILD SUCCESS**, 01:02 min, all 18 reactor modules `SUCCESS` including the new
`Gangland Loot Chests` module.

**No merge-caused compile break found — nothing to fix.** The coordinator flagged `GameplayConfig.java`'s import
block as the only hand-resolved conflict (per WS3's own `rebase-report.md`: CUT's side re-added the whole
hologram/lootchest `@Bean` block since CUT was built before WS3's G2 deleted it; resolved by taking WS3's side —
block stays deleted, moved to `LootChestModuleConfig`). Verified directly: `GameplayConfig.java`'s imports contain
zero `hologram`/`lootchest` references, only the core's own `inventory.*`/`keystone.inventory.InventoryService`
imports (unrelated, core menu dialect). The resolution landed correctly; no residue, no further edit needed.

## 2. Test count reconciliation

**Authoritative total (Maven's own per-module Surefire rollup lines, captured live during the fresh build):
878, 0 Failures, 0 Errors, 0 Skipped** — exactly matching the coordinator's expectation (872 WS3-rebased + 6
WS5-net = 878).

| Module | Tests run |
|---|---|
| Gangland Core | 67 |
| Gangland Item | 43 |
| Sign API | 63 |
| Gangland Domain | 63 |
| Gangland (impl) | 258 |
| Gangland Mail | 25 |
| Gangland Civilians | 20 |
| Gangland Turf | 91 |
| Cops N Crooks | 76 |
| Gangland Gadgets | 113 |
| Gangland NPC Shops | 17 |
| Gangland Loot Chests | 42 |
| **Total** | **878** |

(`Gangland UI` and `Gangland Infrastructure` and `Gangland API` are parent/no-test aggregator modules — 0 each,
not listed. `Lootchest API`, WS3's now-empty pre-G5-deletion husk module, also 0.)

**A file-based recount bug found and diagnosed, not a real discrepancy.** My first pass summed every module's
`target/surefire-reports/*.txt` "Tests run:" line directly (the pattern used throughout this gate's earlier
rounds) and got **837** — 41 short. Root cause, confirmed by direct inspection: Surefire's plain-text reporter
overwrites a report file when a test class contains multiple JUnit 5 `@Nested` inner classes that each get their
own "Test set" — e.g. `RankManagerTest.txt` shows only `Tests run: 0` (the *outer* class has zero direct test
methods; all its tests live in `@Nested` classes like `addPermission / removePermission`, `permissionExists /
findPermission`, etc.), because each nested class's own report write **overwrote** the previous one on disk under
the same filename, leaving only the *last* nested class's count (`RankManager - permission bookkeeping and tree
reload semantics: 0`) surviving as a file. Domain, Core, and Mail all have this shape post-WS5 (User/Bounty/Wanted/
Rank all use `@Nested` extensively). The live Maven console output does **not** have this problem — each nested
class's rollup line prints and gets summed before the next one's write can clobber the file — so the console-based
878 is authoritative; the file-based 837 undercounts by exactly the sum of every "first N-1 of N nested classes"
whose file got overwritten. Flagging this as a real harness-adjacent gotcha for future gates that use the
file-sum method: **prefer the live console's per-module rollup lines over summing `.txt` files whenever any test
class in the reactor uses `@Nested`** (increasingly common since WS5's `User`/`Bounty`/`Wanted`/`RankManager`
tests all do).

## 3. Merge smoke

Harness: `brainstorming/bartizan-split-2026-09-08/smoke/smoke.py`. `scenarios.json` needed one real fix before the
7-module run could deploy at all: `module_jar_prefix` (the primary id→jar-prefix map `smoke.py`'s `deploy_scenario`
consults) had no entry for `"lootchest"` — added `"lootchest": "gangland-lootchest"` (matches the other 6 modules'
convention, and the actual built jar `target/modules/gangland-lootchest-0.10.0.jar`). This is a permanent harness
fix, kept (not reverted) — every future gate that deploys the lootchest module needs it too.

`paths.repo_dir`/`paths.keystone_jar_dir` repointed to this worktree / `wt\keystone-1.11.0\keystone-plugin\target`
(→ `Keystone-1.11.1.jar`) for both runs below, restored to `gangland-0.9.2`/`keystone-1.10.0` immediately after the
second run.

### (a) `cut-full-regression` re-run, lootchest as the 7th module

Updated the existing scenario in place (kept the id, matching every prior gate's "re-run the same row" pattern):
`modules` gained `"lootchest"`; `expect.loaded_modules` gained `"lootchest"`; `expect.must_contain`'s module-count
string updated to `"Runtime modules: 7 loaded, 0 fault(s)"`; `expect.must_not_contain` gained an
`org.luckyraven.gangland.hologram` guard (the deleted package); `commands` gained `"glw lootchest"`.

Command: `python smoke.py --rows cut-full-regression --deploy --keystone --restore`.

**Result: PASS.** `boot=True stop=True modules=['civilians','gadget','lootchest','mail','npcshops','turf',
'copsncrooks'] errors=0`. All 4 expectation checks PASS. Confirmed in the raw log:
```
[Keystone Module.ModuleLoader] Loaded module lootchest 0.10.0 from gangland-lootchest-0.10.0.jar
[Gangland.GanglandContext] Runtime modules: 7 loaded, 0 fault(s)
```
`/glw lootchest` → `"You need to be a player to use this!"` (clean resolution, not an exception — matches the
root `@CommandHandler` shape `LootChestWandCommand` already had before the move). `/glw reload` → `"Reload has
been completed."`, zero faults. `/glw debug inv-data` ran without throwing (empty, no player — expected).
`plugins/Gangland_Warfare/lootchests/{loot_chests.yml,tiers.yml}` confirmed extracted onto disk from the module
jar (filesystem check, both files present after this boot). Report:
`brainstorming/bartizan-split-2026-09-08/smoke/reports/2026-09-22-1923-cut-full-regression.{md,log}`.

### (b) WS3's 6 deferred smoke rows

From `exec/WS3/G1-G2-report.md`'s "Deferred smoke rows" section. Console-drivable pieces already covered by (a)
above (module load, YAML extraction, `/glw reload` mechanism); everything requiring a placed chest or a real
player added to the manual checklist as a new **"## WS3 merge"** section
(`exec/G010/WS2-manual-checklist.md`, rows 45-50):

| Deferred row | Console-drivable part (done, see (a)) | Remainder (checklist row) |
|---|---|---|
| 1. Boot with module absent, no fault | Implicitly proven by every prior 6-module smoke row in this wave (none ever deployed lootchest and all boot clean) | — none |
| 2. Boot present: wand/place/open/take/close, cooldown hologram | Module loads 0 faults (proves `HologramService`+`LootChestManager`+`LootChestWandTag` bean construction succeeds); `/glw lootchest` resolves | Row 45 (wand/place/open/take), row 46 (cooldown hologram text) — both need a real player |
| 3. Cracking chest always fails (LS-02) | — | Row 47 |
| 4. `/glw reload`: registry+hologram survive | Reload *mechanism* confirmed clean (both DB states) | Row 48 — needs a placed chest to confirm the *data* survives, not just the mechanism |
| 5. Restart: chests persist | — | Row 49 |
| 6. `onDisable`: no armor-stand leak (UI-13/UI-15) | — | Row 50 |

### (c) WS5's step-1c live proof

The plan requires: *"members still attach gangId correctly across first load, `/glw reload`, and a fresh empty
DB."* Gang creation is confirmed player-only (WS5's own `G0-report.md` states this explicitly). Drove every
console-reachable piece:

**A genuinely fresh, empty database** — the shared test server's `plugins/Gangland_Warfare/database/gangland.db`
was parked aside (`mv` to `gangland.db.parked-by-G010-lead`, no WAL/SHM sidecars existed) and the scenario re-run
against a database that did not exist yet:
```
[Keystone Persistence.FileHandler] Created file: E:\Documents\Minecraft\Test Server\plugins\Gangland_Warfare\database\gangland.db
[Gangland.GanglandContext] Runtime modules: 7 loaded, 0 fault(s)
```
— confirming the gang/member/rank/user table creation and the whole `BeanFactory`/`BeanGraph` construction order
(including `MemberManager`'s deleted `orderingDep` parameter) completed with **zero faults, zero exceptions**
against a completely empty schema. `/glw reload` also completed cleanly (`"Reload has been completed."`) against
that same fresh database — covering "first load" and "`/glw reload`" both, on a fresh DB, in one run. The original
`gangland.db` (630784 bytes) was restored immediately after (`rm` the fresh one, `mv` the parked one back);
confirmed byte-identical size on restore. Report:
`brainstorming/bartizan-split-2026-09-08/smoke/reports/2026-09-22-1924-cut-full-regression.{md,log}`.

**What's left, added to the checklist** as a new **"## WS5 merge"** section (row 51): the actual *gangId-is-correct*
observation for a real member, since that needs a live gang + a live player — the "fresh empty DB" third of the
plan's three-part ask is already fully covered above; only the "does the number itself come out right" half needs
a human.

**0 errors, 0 faults across every run** (the bar the coordinator set) — met on all three smoke invocations (the
standard 7-module run, and both halves of the fresh-DB investigation).

## Deliverables

- `exec/G010/merge-2026-09-22-report.md` — this file.
- `exec/G010/WS2-manual-checklist.md` — two new sections: `## WS3 merge` (rows 45-50) and `## WS5 merge` (row 51).
- `brainstorming/bartizan-split-2026-09-08/smoke/scenarios.json` — `cut-full-regression` row updated in place
  (7 modules); `module_jar_prefix` gained the `"lootchest"` entry (permanent fix, kept); `paths` temporarily
  repointed for all three runs, restored to `gangland-0.9.2`/`keystone-1.10.0` afterward.
- `brainstorming/bartizan-split-2026-09-08/smoke/reports/2026-09-22-1923-cut-full-regression.*` (existing-DB run)
  and `2026-09-22-1924-cut-full-regression.*` (fresh-DB run) — both main checkout, untracked.

## Anything not verified

- Everything requiring a real client/player: WS3 checklist rows 45-50 (wand-give, chest place/open/take, cooldown
  hologram, cracking-minigame shape, reload-survives-a-placed-chest, restart persistence, armor-stand-leak count)
  and WS5 checklist row 51 (the live gangId-correctness observation). All added to
  `exec/G010/WS2-manual-checklist.md`, none silently assumed.
- No commits made anywhere; no reviewers invoked; no subagents used this round (build/smoke verification and
  report-writing only — no code changes were needed, so there was nothing to delegate).
