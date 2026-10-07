# WS2 G3a report — 2026-09-21

Status: DONE

Plan refs: `plans/WS2-inventory-keystone.md` §4 row G3a, §2 (46-file package layout, 12-file deletion/stay list),
§7 (`GangFilterAdapterTest`/`MemberFilterAdapterTest`), §8 risk 1, §0e; review `exec/G010/WS2-G1-G2-review.md`
ruling W38 (F2 → G3a). Worktree `E:\Programming\java\wt\gangland-0.10.0`, branch `0.10.0`, started at HEAD
`9d0d8878` (WS2 G1+G2 merged with 0.9.2's WS8). Baseline `mvn clean install -DskipTests` confirmed green before
any edit. `graphify update . --force` run in the worktree first (15699 nodes, 24312 edges) per the mandatory
orientation rule.

## What changed

- **Verified the 46/12 split against the real tree** (plan's own "to verify at G3a" note, §2): listed
  `gangland-ui/inventory-api/src/main/java/org/luckyraven/gangland/inventory/**` — exactly 58 files, matching the
  plan's move list (46) + stay/delete list (12) precisely.
- **Moved 45 of the 46 planned files** (see Deviations — `part/Fill.java` did not move) via `git mv` into
  `gangland-impl/src/main/java/org/luckyraven/gangland/menu/**`, preserving sub-package shape: root (`State`,
  `InventoryData`, `InventoryBuilder`, `OpenInventory`), `condition/` (4), `filter/` (11), `handler/` (11),
  `multi/` (7), `part/` (`ButtonTags`, `ConditionalSlotResult`, `Slot` — 3 of the planned 4), `unique/` (1),
  `villager/` (4).
- **Fixed package declarations and cross-package imports** in all 45 moved files: a scripted `sed` pass rewrote
  `package org.luckyraven.gangland.inventory[...]` → `org.luckyraven.gangland.menu[...]` and every cross-reference
  among the moved classes (`condition.*`, `filter.*`, `handler.*`, `multi.*`, `unique.*`, `villager.*`,
  `part.{ButtonTags,ConditionalSlotResult,Slot}`, and the four root classes) to the new `menu.*` package. Imports of
  the 12 stay-behind classes (`InventoryHandler`, `InventoryOpener`, `flow.*`, `listener.*`, `part.PageConfig`,
  `service.InventoryRegistry`, `util.InventoryUtil`, and — per the deviation — `part.Fill`) were left pointed at
  `org.luckyraven.gangland.inventory.*`, unchanged, since those classes did not move.
- **Fixed 3 static imports** the sed pass's pattern (`import org.` prefix) didn't match:
  `menu/multi/MultiInventory.java`, `menu/multi/MultiInventoryCreation.java`, `sign/aspect/BountyAspect.java` each
  had `import static org.luckyraven.gangland.inventory.multi.X.method;` — corrected to `menu.multi`.
- **Fixed one implicit same-package reference that broke on the move**: `InventoryBuilder.java` used
  `InventoryHandler`/`InventoryOpener` with no explicit import (both were in the same root package before the
  move). Added the two explicit imports pointing at the still-`inventory-api` classes.
- **Fixed one wildcard import**: `InventoryRuntimeContext.java` had `import org.luckyraven.gangland.inventory.*;`,
  which covered all 6 root-package classes (2 staying, 4 moving). Replaced with explicit imports: kept
  `InventoryHandler`/`InventoryOpener` from `org.luckyraven.gangland.inventory`, added `InventoryBuilder`/
  `InventoryData`/`OpenInventory`/`State` from `org.luckyraven.gangland.menu`.
- **Fixed the remaining 17 gangland-impl importers** (found by reactor-wide grep, all confined to `gangland-impl`):
  `command/sub/debug/DebugCommand.java`, `command/sub/filter/FilterCommand.java`, `command/sub/gang/GangCommand.java`,
  `config/FileConfig.java`, `config/GameplayConfig.java`, `config/GangFilterRegistration.java`,
  `config/KernelConfig.java`, `file/configuration/inventory/{ConditionalSlotParser,InventoryDefinitionStore,
  InventoryParser}.java`, `file/configuration/inventory/itemsource/GangItemSourceProvider.java`,
  `gang/GangFilterAdapter.java`, `gang/member/MemberFilterAdapter.java`,
  `item/contract/GanglandUniqueItemInteractionService.java`,
  `listener/inventory/InventoryOpenByCommandListener.java`, `sign/aspect/BountyAspect.java` (import fix, counted
  above with the static-import fix) — same mechanical import-path rewrite, no other change.
- **Wrote the two deferred projection tests** (plan §7, review F2, ruling W38): `GangFilterAdapterTest` (9 cases:
  name lower-casing + null-name fallback, description, color, member count incl. empty, created epoch, unsupported
  field → null, null gang/null field → null) and `MemberFilterAdapterTest` (8 cases: name lower-casing off
  `Bukkit.getOfflinePlayer` via `MockedStatic<Bukkit>` + null-name fallback, rank category incl. null rank,
  contribution, join-date epoch, unsupported field → null, null member/null field → null). Both new files under
  `gangland-impl/src/test/java/org/luckyraven/gangland/gang/`.

## Deviations from the plan

**`part/Fill.java` stays in `inventory-api`; it does not move to `menu.part` as §2 lists it.** Before moving,
I grepped every consumer of the 46-file move list across the whole reactor (not just `gangland-impl`, which is all
the plan's own G3a acceptance criterion — `-pl gangland-impl -am` — would have caught). Result: `cops-n-crooks`
(`PaperworkView`, `HandcuffBribeView`), `gangland-gadget` (`CarSignViewProvider`), `gangland-npc-shops` (9 trader/
banker views), `gangland-turf` (3 turf-NPC views) and `shop-api` (`ShopAdminView`) — 16 files outside
`gangland-impl` — all import `org.luckyraven.gangland.inventory.part.Fill` directly (alongside `InventoryHandler`,
`flow.MultiPanelInventory`, `flow.Panel`, `util.InventoryUtil` — all four correctly staying per §2's own deletion
table). These are exactly the not-yet-migrated `Panel`/`InventoryHandler`-based views G4/G5 re-point onto
`ChestMenuBuilder` later; until then they still construct `Fill` records directly. Since runtime modules and
`shop-api` may never depend on `gangland-impl` (the module-loader contract this repo enforces at compile time), and
since `Fill` is a two-field record with zero dependencies of its own (verified — it references nothing, and
nothing among the moved files' same-package neighbours (`ButtonTags`/`ConditionalSlotResult`/`Slot`) references it
either), moving it would have broken 16 files' compiles in 5 modules that `-pl gangland-impl -am` cannot see, for a
class that has no coupling to justify forcing the move now. I left `Fill.java` in `gangland-ui/inventory-api/
.../inventory/part/Fill.java` untouched (13 files now remain in `inventory-api`'s `inventory` package, not the
planned 12) and kept the 3 impl-side moved files that reference it (`InventoryBuilder.java`, `menu/multi/
MultiInventory.java`, `menu/multi/MultiInventoryCreation.java`) importing it from the old package — a one-line,
zero-behaviour-change accommodation. **Follow-up for G4/G5/CUT:** once the 16 external files' `Panel`→`ChestMenuBuilder`
re-point removes their `Fill` usage, `Fill.java`'s only remaining consumer will be `gangland-impl`, and it can move
into `menu.part` at that point (or simply fold away if `ChestMenuBuilder`'s own fill/border primitives make a
standalone `Fill` record redundant — worth checking at G3 rather than porting it forward mechanically).

No other file-body cross-reference surprises turned up beyond what's noted above; the plan's own §8 risk 1 ("some
of the 46 may reference `InventoryHandler`/`InventoryData` concretely") was real (`InventoryBuilder`, `Slot`,
`ConditionalSlotResult`, `SearchButtonFactory`, `AbstractCommandSlotHandler`, `ClickSlotHandler`,
`CloseSlotHandler`, `SlotEventHandler`, `ListEntry`, `MultiInventory`, `MultiInventoryCreation`,
`MultiInventoryNavigation`, `StaticSlotEntry` all reference `InventoryHandler`/`InventoryOpener`/`PageConfig`/
`InventoryUtil` concretely) but did **not** require combining G3a with G3 — every one of those references is to a
class staying behind in `inventory-api`, which `gangland-impl` already depends on at ordinary (non-`provided`)
compile scope, so the reference just needed to keep pointing at the old package, not disappear.

## Red-first evidence

Both new test classes assert current (unchanged) behaviour rather than pin a bug fix, so "red" was demonstrated by
temporarily substituting a wrong expected value into one assertion per class, confirming the test framework
actually catches it, then restoring the correct value:

- `GangFilterAdapterTest.name_isLowercasedDisplayName` — temporarily asserted `"WRONG_RED_FIRST_PROBE"`. Red:
  `mvn -pl gangland-impl -am test -Dtest=GangFilterAdapterTest -Dsurefire.failIfNoSpecifiedTests=false` →
  `Tests run: 9, Failures: 1` — `expected: <WRONG_RED_FIRST_PROBE> but was: <zebra gang>`. Reverted to
  `"zebra gang"`; green: `Tests run: 9, Failures: 0, Errors: 0`.
- `MemberFilterAdapterTest.category_projectsRankName` — temporarily asserted `"WRONG_RED_FIRST_PROBE"`. Red (same
  command, `-Dtest=MemberFilterAdapterTest`): `Tests run: 8, Failures: 1` — `expected: <WRONG_RED_FIRST_PROBE> but
  was: <Boss>`. Reverted to `"Boss"`; green: `Tests run: 8, Failures: 0, Errors: 0`.
- Combined run after both reverts: `mvn -pl gangland-impl -am test -Dtest=GangFilterAdapterTest,MemberFilterAdapterTest
  -Dsurefire.failIfNoSpecifiedTests=false` → `Tests run: 17, Failures: 0, Errors: 0, Skipped: 0`.
- Incidental finding during setup: `Gang`'s constructor reads `GangSettings` (a process-wide static bound to
  `Settings`) for its `Bounty` field, so `GangFilterAdapterTest` needed the same `SettingsFixture.initializeMinimal`
  + `GangSettings.bind(new GanglandGangSettings())` `@BeforeEach` setup `GangAllianceRepositorySpiTest` already
  uses — without it every `new Gang(id)` call threw `IllegalStateException: GangSettings accessed before
  GangModuleConfig bound the contract`.

## Build

Final gate command: `mvn clean install` (full reactor, tests included) → **BUILD SUCCESS**, all 21 reactor modules
`SUCCESS`. Aggregate test summary across every module's surefire output: **Tests run: 1800, Failures: 0, Errors: 0,
Skipped: 0** (includes the 17 new/moved-package-touching tests: 9 `GangFilterAdapterTest` + 8
`MemberFilterAdapterTest`, plus the pre-existing `InventoryRuntimeContextTest` (2) and `RemoveAccountListenerTest`
(1) whose imports were repointed but whose assertions are unchanged from G1+G2's fix rounds).

Full-reactor compile-only check (`-DskipTests`) was also run standalone before the test run and separately after
each fix (wildcard import, implicit-reference, static imports) while iterating — three intermediate `BUILD FAILURE`
rounds along the way, each fixed before moving on (see Deviations/what-changed for what each one was: implicit
`InventoryHandler`/`InventoryOpener` reference in `InventoryBuilder.java`; the `InventoryRuntimeContext.java`
wildcard import; the 3 missed `import static` lines).

## Docket ids touched

None. G3a is a pure mechanical relocation with no behaviour change; the plan's two docket-candidate items
(`InventoryHandler.SPECIAL_INVENTORIES`, static `InventoryRegistry registry` leaking across reloads — §11) are
recorded "fixed-by-WS2" against G3/CUT, not this gate, and neither class moved.

Docket candidates (new): none found this gate.

## Subagents used

None. Given the volume of cross-reference verification needed to safely script the move (confirming the 46/12
split against the real tree, the full-reactor consumer grep that surfaced the `Fill.java` exception, the wildcard/
implicit-import traps), doing it directly with a scripted `sed` pass plus targeted manual fixes kept the full
reasoning chain — and the ability to catch the `Fill.java` cross-module conflict `-pl gangland-impl -am` alone
would have missed — in one place. The transformation itself was mechanical enough (a fixed, verified substitution
table applied file-by-file) that a subagent handoff would not have reduced risk, only added a verification pass I'd
have had to do anyway.

## Concerns / open questions

- **`Fill.java` deviation** (see above) is the one substantive open item — flagged for whoever picks up G4/G5 or
  CUT to fold `Fill.java` into `menu.part` (or retire it in favour of `ChestMenuBuilder`'s own fill primitive) once
  its last five external-module consumers are re-pointed.
- `gangland-ui/inventory-api` now holds 13 files, not the plan's stated 12 (`Fill.java` staying is the delta) —
  worth a one-line correction to plan §2's deletion-table count when this lands, so a later reader doesn't treat
  13 as a fresh discrepancy.
- The implicit-same-package and wildcard-import traps found here (`InventoryBuilder.java`'s bare
  `InventoryHandler`/`InventoryOpener` references, `InventoryRuntimeContext.java`'s `import
  org.luckyraven.gangland.inventory.*;`) were specific to files that happened to share root package/wildcard
  imports with the moved classes; G3's own retarget of `InventoryHandler`/`InventoryOpener`/`PageConfig`/
  `InventoryUtil`/`InventoryRegistry` onto `ChestMenuBuilder` should re-grep for wildcard imports of
  `org.luckyraven.gangland.inventory.*` and `org.luckyraven.gangland.inventory.part.*` before deleting those 12
  (now 13, per above) stay-behind files — this gate's grep found exactly one wildcard import in the whole reactor,
  but G3 changes the reference set those greps need to run against.
- No smoke row added/run this gate — the plan's own G3a acceptance criterion is `mvn clean install -pl
  gangland-impl -am` (a compile+test gate only; the batch's smoke ask is attached to G3, not G3a), and no menu is
  opened differently by a pure package move. Full reactor test suite (1800 tests) is the coverage for this gate.

## Files touched

**Renamed** (45, `git mv`, package + import fixes only — `gangland-ui/inventory-api/.../inventory/**` →
`gangland-impl/.../menu/**`): all of `condition/` (4), `filter/` (11), `handler/` (11), `multi/` (7), `unique/`
(1), `villager/` (4), `part/{ButtonTags,ConditionalSlotResult,Slot}.java` (3), and the 4 root files
(`State`, `InventoryData`, `InventoryBuilder`, `OpenInventory`).

**Modified** (18, import-path fixes only, all under `gangland-impl/`): `command/sub/debug/DebugCommand.java`,
`command/sub/filter/FilterCommand.java`, `command/sub/gang/GangCommand.java`, `config/FileConfig.java`,
`config/GameplayConfig.java`, `config/GangFilterRegistration.java`, `config/KernelConfig.java`,
`file/configuration/inventory/ConditionalSlotParser.java`,
`file/configuration/inventory/InventoryDefinitionStore.java`,
`file/configuration/inventory/InventoryParser.java`, `file/configuration/inventory/InventoryRuntimeContext.java`
(also the wildcard-import fix), `file/configuration/inventory/itemsource/GangItemSourceProvider.java`,
`gang/GangFilterAdapter.java`, `gang/member/MemberFilterAdapter.java`,
`item/contract/GanglandUniqueItemInteractionService.java`,
`listener/inventory/InventoryOpenByCommandListener.java`, `sign/aspect/BountyAspect.java` (also a static-import
fix), `src/test/.../file/configuration/inventory/InventoryRuntimeContextTest.java`.

**New** (2): `gangland-impl/src/test/java/org/luckyraven/gangland/gang/GangFilterAdapterTest.java`,
`gangland-impl/src/test/java/org/luckyraven/gangland/gang/member/MemberFilterAdapterTest.java`.

**Unchanged, confirmed by grep+build, not by assumption:** `gangland-ui/inventory-api/.../inventory/` still holds
`InventoryHandler.java`, `InventoryOpener.java`, `flow/{FlowSession,MultiPanelInventory,Panel}.java`,
`listener/{InventoryClickHandler,InventoryCloseHandler,InventoryDragHandler,PlayerInventoryCleanup}.java`,
`part/{Fill,PageConfig}.java`, `service/InventoryRegistry.java`, `util/InventoryUtil.java` — 13 files, the 12 the
plan named plus `Fill.java` (see Deviations). Zero importers anywhere in the reactor still reference
`org.luckyraven.gangland.inventory.{condition,filter,handler,multi,unique,villager}.*` or the four moved root
classes (verified by a whole-reactor grep after the fix, and by the full `mvn clean install` compiling and testing
green).

Review package (status + diffstat): `exec/G010/WS2-G3a-package.diff`.
