# WS2 G5 report — 2026-09-21

Status: DONE

Plan refs: `plans/WS2-inventory-keystone.md` §4 row G5, R11 (lootchest/shop-api out of scope). Coordinator's G5 brief
(turf 6 files + cops-n-crooks 2 + gadget 1, §0d rule, CUT reference inventory).
Worktree `E:\Programming\java\wt\gangland-0.10.0`, branch `0.10.0`, started at HEAD `3014843c` (WS2 G4, committed).
Verified clean via `git status --short` before starting.

**Session-gap note**: this batch was interrupted mid-execution by a session-limit reset after `HandcuffBribeView.java`
was written. Reconciled on resume: `git status` showed the 9 files already listed by the coordinator's resume
message, `HandcuffBribeView.java`/`PaperworkView.java` were read back whole and confirmed complete (not torn) —
`mvn clean install -pl gangland-features/gangland-turf,gangland-features/cops-n-crooks -am -DskipTests` immediately
after resume failed only on the two bean-config files (`CopsNCrooksModuleConfig`'s `HandcuffBribeView`/`PaperworkView`
constructor calls not yet updated for the new `InventoryService` parameter) — exactly the next step, not a torn
edit. No work was redone.

## What changed

### turf (7 files: the plan's 6 + `TurfModuleConfig.java`)

Same generic `Panel`→`ChestMenuBuilder`/`MenuFlow` swap as G4, applied to the Quartermaster panel chain:
- **`TurfPowerupFlowSession.java`**: `implements FlowSession` → `implements FlowState` (marker-interface swap,
  no method changes — the session already had zero flow-framework methods to retarget).
- **`TurfPowerupFlow.java`**: `MultiPanelInventory<S>` → `MenuFlow<S>`; gained an `InventoryService` constructor
  param (`MenuFlow.builder(...)` requires one, unlike the old `MultiPanelInventory` constructor — the same G4
  finding, since `TurfPowerupFlow` mirrors `TraderFlow`/`BankerFlow`'s shape exactly).
- **`TurfPowerupMenuView.java`**, **`TurfPowerupBuffCatalogueView.java`**, **`TurfPowerupGarrisonView.java`**:
  `size(session)`→`rows(session)`; `render(MultiPanelInventory, InventoryHandler, Player, S)` →
  `render(MenuFlow, ChestMenuBuilder, S)`; every `handler.setItem(slot, ItemBuilder, draggable=false, TriConsumer)`
  → `builder.slot(slot, ItemComponent.of(itemBuilder).onAnyClick(ctx -> ...))` (the `TriConsumer`'s
  `InventoryHandler`/`ItemBuilder` params were never dereferenced here either, same finding as G4's dialect and
  npc-shops ports); `InventoryUtil.fillInventory` → `builder.fill(FillComponent...)`;
  `host.switchTo/back/end/rerender` → the identical method names on `MenuFlow`. Buff-catalogue and garrison views'
  `attemptBuy` methods call `flow.rerender()` after a successful purchase — the coordinator's named
  `MenuFlow.rerender()` call sites.
- **`TurfPowerupOpenContract.java`**: javadoc-only change (its `{@link MultiPanelInventory}` reference retargeted
  to `{@link MenuFlow}`); also corrected a stale claim in the same doc comment ("the implementation lives in
  gangland-impl") — the real implementation, `TurfPowerupOpenContractImpl`, has always lived in
  `gangland-turf/npc/config/`, confirmed by grep before editing.
- **`TurfModuleConfig.java`**: `turfPowerupFlow` bean method gained an `InventoryService` parameter, threaded to
  the new `TurfPowerupFlow` constructor.

### cops-n-crooks (3 files: the plan's 2 + `CopsNCrooksModuleConfig.java`)

`PaperworkView.java`/`HandcuffBribeView.java` are **not** `Panel`/flow-based — both are standalone
`open(Player, ...)` methods that build one single-shot `InventoryHandler` and open it directly (no panel
navigation). Re-pointed the same way G3's `SimplePagedMenu` and G4's `PaperworkView`-shaped standalone views were:
`InventoryHandler` → `ChestMenu.builder(inventoryService).title(...).rows(...)`; each `handler.setItem(...)` →
`builder.slot(...)` with `ItemComponent`; `InventoryUtil.fillInventory` → `builder.fill(FillComponent.of(...))`
(both views hardcode `Fill(" ", "BLACK_STAINED_GLASS_PANE")`, not a `Settings`-driven value, so the fill component
uses the literal `Material.BLACK_STAINED_GLASS_PANE` directly rather than an `XMaterial` string-match — same net
effect, no material-resolution ambiguity since the input was never configurable to begin with);
`handler.open(player)` → `builder.build().open(player)`. Both gained an `InventoryService` constructor parameter.
`DetainmentGuiAccess.authorize(...)` (the UUID allowlist `DetainmentListener` consults to let these two GUIs'
`InventoryOpenEvent` through while every other inventory-open attempt for a restrained player is blocked) is called
in the exact same place, before `.open(player)` — unaffected by the menu-system swap, since it's a plain
UUID-keyed static set with no `InventoryHandler` type dependency (confirmed by reading `DetainmentGuiAccess.java`
and every call site before touching anything).
- **`CopsNCrooksModuleConfig.java`**: `handcuffBribeView`/`paperworkView` bean methods gained an `InventoryService`
  parameter, threaded to both constructors.

### gadget (2 files: the plan's 1 + `GadgetModuleConfig.java`)

`CarSignViewProvider.java`'s `openCarView` (also standalone, single-shot, display-only — no click handler on its
one slot at all) — same retarget: `InventoryHandler`→`ChestMenuBuilder`, `InventoryUtil.fillInventory`→
`builder.fill(...)`. **`Fill` itself is unchanged** — still imported from `org.luckyraven.gangland.inventory.part.Fill`
per the standing ruling (W41, carried from G3a/G3/G4): the class isn't moved until CUT, so this file (like every
other G3/G4/G5 file that needs a `Fill`) keeps importing it from its current package. `Settings.getInventoryFillName()`/
`getInventoryFillItem()` (Gangland's own static config class) are still read the same way; only the material string
→ `Material` resolution now goes through `XMaterial.matchXMaterial(...)` (matching the pattern every other ported
view in G3/G4/G5 uses) instead of `InventoryUtil.getFillItem(...)`.
- **`GadgetModuleConfig.java`**: `carSignViewProvider` bean method gained an `InventoryService` parameter.

## §0d (item-return contract) — not applicable this gate

Checked every `Panel`/standalone view touched in G5 for a player-placeable slot (the coordinator's specific
suspicion was `HandcuffBribeView`) **before** porting any of them: grepped every `handler.setItem(...)` call across
all 9 original files for a `draggable=true` (or old-API equivalent) argument. **Zero hits** — every slot in
`TurfPowerupMenuView`/`TurfPowerupBuffCatalogueView`/`TurfPowerupGarrisonView`/`PaperworkView`/`HandcuffBribeView`/
`CarSignViewProvider` passes `draggable=false` (or, for `CarSignViewProvider`'s one slot, has no click handler and
was never draggable). Every "bribe"/"bail"/"buy" action in this gate charges the player's **economy balance**
(a service call), never accepts a **physical item** into a slot. Confirmed the same in the new code: zero
`.interactive(true)` calls anywhere in the 12 changed files.

**Conclusion**: no `DropzoneSlotComponent` need — not copied into cops-n-crooks, not proposed as an additive
`gangland-api` class. Nothing to rule on this gate.

## CUT preparation — reference inventory (no code changes)

`grep -rl "org\.luckyraven\.gangland\.inventory\." --include=*.java .` across the whole reactor, excluding
`gangland-ui/inventory-api/` itself (the definitions), **after** this gate's changes:

### gangland-impl (18 files — CUT's own job, not touched this gate)
`bootstrap/ReloadPlugin.java` (`InventoryHandler`), `command/sub/debug/DebugCommand.java` (`InventoryHandler`,
`MultiPanelInventory`, `part.Fill`), `command/sub/debug/VillagerDebugPanel.java` (`InventoryHandler`, `FlowSession`,
`MultiPanelInventory`, `Panel`, `part.Fill`, `util.InventoryUtil`), `command/sub/gang/GangColorCommand.java`
(`InventoryHandler`, `part.Fill`, `util.InventoryUtil`), `command/sub/gang/GangCommand.java` (`InventoryHandler`,
`part.Fill`, `util.InventoryUtil`), `command/sub/lootchest/LootChestWandEditCommand.java` (`part.Fill`),
`config/KernelConfig.java` (`InventoryHandler`, `service.InventoryRegistry`),
`file/configuration/inventory/InventoryRuntimeContext.java` (`InventoryHandler`, `part.Fill` — the coordinator's
named `:170` is `InventoryHandler.factorOfNine(size)`, a **static arithmetic call**, not an instance — CUT needs
this rounding logic moved somewhere that isn't the deleted class before the class itself can go),
`listener/inventory/InventoryOpenByCommandListener.java` (`part.Fill`), `listener/loot/LootChestWandListener.java`
(`part.Fill`), `listener/player/RemoveAccountListener.java` (`service.InventoryRegistry`),
`lootchest/LootChestWand.java` (`InventoryHandler`, `part.Fill`, `util.InventoryUtil`), `menu/InventoryBuilder.java`
(`part.Fill` — G3's own retargeted class, still imports `Fill` from its old package per W41),
`menu/SimplePagedMenu.java` (`part.Fill`, same reason), `menu/filter/SearchButtonFactory.java` (`InventoryHandler`),
`sign/aspect/BountyAspect.java` (`part.Fill`), `sign/aspect/ViewInventoryAspect.java` (`InventoryHandler`,
`part.Fill`, `util.InventoryUtil`) + 2 test files (`InventoryParserRoundTripTest.java`,
`SimplePagedMenuTest.java` — both `part.Fill` only, fully-qualified inline construction, not an import in the
round-trip test's case).

### gangland-ui/lootchest-api (2 files — WS3's scope, R2/R11)
`LootChestService.java`, `data/LootChestSession.java` — both `InventoryHandler` only.

### gangland-ui/shop-api (9 files — WS4 G1b's scope)
`listener/{BarterCategoryAdminListener,SellCategoryAdminListener,ShopAdminListener}.java` (`flow.MultiPanelInventory`
only), `view/{BarterCategoryItemsAdminView,PriceEditorView,SellCategoryItemsAdminView}.java` (`InventoryHandler`,
`flow.MultiPanelInventory`, `flow.Panel`), `view/ShopAdminFlow.java` (`flow.MultiPanelInventory`),
`view/ShopAdminFlowSession.java` (`flow.FlowSession`), `view/ShopAdminView.java` (`InventoryHandler`,
`flow.MultiPanelInventory`, `flow.Panel`, `part.Fill`, `util.InventoryUtil`) — this is the "shop-api's 9 admin
views" the plan's §4 G1b already names; confirmed the count matches (9 files) and none of them were touched by
G3/G4/G5.

### gangland-features/gangland-gadget (1 file — this gate's own, intentional)
`sign/CarSignViewProvider.java` — `part.Fill` only, per the standing W41 ruling (Fill doesn't move until CUT).

**Not found anywhere** (confirmed absent, not just unchecked): any remaining reference in `gangland-turf`,
`cops-n-crooks`, `gangland-npc-shops`, `gangland-civilians`, `gangland-mail` — all fully off `org.luckyraven.gangland.inventory.*`
after G3/G3a/G4/G5.

**CUT gate precondition** (`grep -rl "org.luckyraven.gangland.inventory" --include=*.java . | grep -v "gangland-ui/inventory-api/"`)
currently returns 18+2+9+1 = 30 files, all inside `gangland-impl` (CUT's own job), `lootchest-api` (WS3), `shop-api`
(WS4 G1b) or this gate's one intentional `Fill`-only import — **zero** files in any already-migrated module
(turf/cops-n-crooks/gadget/npc-shops/civilians/mail). CUT can proceed once WS3 G3 and WS4 G1b both land, per R11 —
this gate adds nothing new to that blocker list beyond confirming gadget's one `Fill` import is expected, not a
leftover.

## Build

**Module gate**: `mvn clean install -pl gangland-features/gangland-turf,gangland-features/cops-n-crooks,gangland-features/gangland-gadget -am`
→ **BUILD SUCCESS**. Test count, summed fresh from each module's own `target/surefire-reports/*.txt`:
**Tests run: 280, Failures: 0, Errors: 0, Skipped: 0**.

**Whole reactor**: `mvn clean install` (full reactor, exit code 0) → **BUILD SUCCESS**. Aggregate test count,
summed fresh from every module's own `target/surefire-reports/*.txt`: **Tests run: 875, Failures: 0, Errors: 0,
Skipped: 0** — **unchanged** from G4's own reactor total. No test was added or removed this gate: §0d doesn't apply
(no deposit slots found), so no new item-survival tests were needed, and none of the 12 changed files had existing
test coverage to update (confirmed by grep — no test file references `TurfPowerupFlow`, `PaperworkView`,
`HandcuffBribeView` or `CarSignViewProvider` by name anywhere in the reactor).

**Cross-module check** (before touching anything, and re-confirmed after):
`grep -rln "TurfPowerupFlow\b|PaperworkView\b|HandcuffBribeView\b|CarSignViewProvider\b" --include=*.java` across
`gangland-impl`, `gangland-ui`, `gangland-mail`, `gangland-civilians`, `gangland-npc-shops` → zero hits. The
constructor-signature changes in this gate (all four classes gaining `InventoryService`) cannot have broken
anything outside the three modules the `-pl ... -am` gate already rebuilt.

## Smoke

**Console-drivable (harness)**: new scenario `ws2-g5-boot` added to
`brainstorming/bartizan-split-2026-09-08/smoke/scenarios.json` (`paths.repo_dir`/`keystone_jar_dir` temporarily
repointed at this worktree / `wt\keystone-1.11.0\keystone-plugin\target`, restored to their prior values —
`wt\gangland-0.9.2` / `wt\keystone-1.10.0` — immediately after the run, per W17/W35; the new scenario definition
itself is kept). Deploys `civilians` + `turf` + `cops` + `gadget` (civilians alongside because
`turf: Depends:[civilians]` and `cops: Depends:[turf, civilians]`; Bartizan alongside because
`cops: Plugins:[Bartizan]`) — civilians is not this gate's own scope but is required for turf/cops-n-crooks to
actually **load** (not merely fault) so their real bean graphs — including this gate's own
`InventoryService`-consuming beans — get exercised. Command:
`python smoke.py --rows ws2-g5-boot --deploy --keystone --restore`.
**Verdict: PASS** — boot detected, clean stop, `loaded_modules=['civilians', 'copsncrooks', 'gadget', 'turf']`
(all four, 0 faults), 0 ERROR-signature mismatches. `glw turf` resolves cleanly from console (help page renders);
`glw car`/`glw debug inv-data` also ran without exception. Report:
`brainstorming/bartizan-split-2026-09-08/smoke/reports/2026-09-21-2321-ws2-g5-boot.md` (+`.log`,
+`-summary.md/.json`) — main checkout, untracked, per LEAD-RULES. Parked jars (`gangland-gadget-0.9.1.jar`,
`Bartizan-0.3.0.jar`, 2× Citizens jars) restored via `--restore`; core/Keystone jars left deployed at their new
version, matching the harness's established steady-state (not parked/restored) and G4's own precedent.

**Client-only rows**: 8 new rows (20-27) appended to `exec/G010/WS2-manual-checklist.md` under a new "## G5"
section — turf's Quartermaster root/buff-catalogue/garrison/navigation (rows 20-23), cops-n-crooks' detainment
paperwork/handcuff-bribe/restrained-click-composition (rows 24-26), gadget's car-sign hover view (row 27). All
unchecked pending a client session.

## Docket

No entries fixed this gate. No new findings filed — the CUT reference inventory above is planning input for the
shared CUT gate, not a bug; the `InventoryRuntimeContext.java:170` `factorOfNine` static-arithmetic dependency
(flagged by the coordinator as a "known blocker to confirm") is confirmed exactly as named, not a surprise finding.

## Subagents used

None. All 12 files follow the identical mechanical pattern established across G3/G4 (`Panel`/`MenuFlow` swap for
turf's flow-based views; the standalone-`ChestMenuBuilder` pattern G3's `SimplePagedMenu` and G4's non-flow views
already used for cops-n-crooks/gadget's single-shot views) — reading each file once and applying the established
substitution was faster than transferring that already-loaded context to a fresh subagent.

## Concerns / open questions

- **None new this gate.** The concerns already on record from G3/G4 (`Fill.java` not yet moved; `MenuRegistry`
  unused; the Keystone-vs-Gangland `bukkit.version` test-environment gap) are unaffected — this gate introduced no
  new item-return-contract surface (§0d didn't apply) and no new test infrastructure to carry the gap into.
- The CUT reference inventory above is now current as of this gate; WS3 (lootchest-api, 2 files) and WS4 G1b
  (shop-api, 9 files) are the only two blockers left before the CUT gate's own precondition grep returns zero
  outside `gangland-impl` — worth flagging to whoever runs those two next, since this gate's grep is the freshest
  count available.

## Files touched

**Modified** (12): `gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/{TurfModuleConfig,
npc/TurfPowerupOpenContract,npc/view/TurfPowerupBuffCatalogueView,npc/view/TurfPowerupFlow,
npc/view/TurfPowerupFlowSession,npc/view/TurfPowerupGarrisonView,npc/view/TurfPowerupMenuView}.java` (7);
`gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/{config/CopsNCrooksModuleConfig,
detainment/paperwork/HandcuffBribeView,detainment/paperwork/PaperworkView}.java` (3);
`gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/{config/GadgetModuleConfig,
sign/CarSignViewProvider}.java` (2).

No files added or deleted this gate (no new test infrastructure — §0d didn't apply).

**Also touched (main checkout, untracked)**: `exec/G010/WS2-manual-checklist.md` (new "## G5" section, rows 20-27),
`brainstorming/bartizan-split-2026-09-08/smoke/scenarios.json` (new `ws2-g5-boot` scenario retained; `paths.
repo_dir`/`keystone_jar_dir` restored to their pre-gate values).

Review package (real hunks, `git add -N . && git diff HEAD -M -U5`, intent-to-add reset afterward so the worktree
stays clean/uncommitted): `exec/G010/WS2-G5-package.diff` (862 lines).
