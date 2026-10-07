# WS2 Plan — inventory-api → keystone-inventory

> Re-target (R10, 2026-09-17): WS2 moves off Oriel entirely. The basic inventory mechanism —
> holder-identified menus, click routing, panel flow, page arithmetic — ships as a new Keystone module,
> `keystone-inventory` (Phase E6, Keystone 1.11.0), instead of living on Oriel. Gangland gains **no** Oriel
> dependency. This supersedes `plans/WS2-inventory-oriel.md` (superseded banner added there).

Sources: `plans/WS2-inventory-oriel.md` (the superseded Oriel-targeted plan — carries forward B1/R3 cutover
timing, B2's three domain tests, R2's lootchest ownership), `exec/WS2/RETARGET-2026-09-17.md` (the board's WS2
section as re-targeted), `exec/WS2/E6-plan-review.md` (Opus review of the E6/R10 pairing, verdict PASS WITH
FIXES, orchestrator rulings W23), `E:\Programming\java\wt\keystone-1.11.0\docs\extraction-roadmap.md` Phase E6
section (amended 2026-09-17 alongside this plan), and a direct listing of
`gangland-ui/inventory-api/src/main/java/org/luckyraven/gangland/inventory/**` (58 files) in the
`gangland-0.9.2` worktree.

---

## 0. Review response (E6-plan-review.md, W23 rulings)

| Id | Finding | Resolution | Where |
|---|---|---|---|
| R10 | Board re-target: WS2 moves onto `keystone-inventory` (Phase E6, 1.11.0) instead of onto Oriel | This plan replaces the Oriel-targeted one wholesale; `plugin.yml` drops the Oriel ask entirely | whole document |
| R11 | Ownership clash (F5): the old plan's consumer list/G5 pulled in `lootchest-api` and shop-api's admin views, contradicting R2 (lootchest = WS3) and `PLAN.md`'s WS4 G1b (shop views) | WS2 G5 = turf · cops-n-crooks · gadget **only**; the shared CUT gate waits on WS2 G5 **and** WS3 G3 **and** WS4 G1b, not WS2 alone | §1, §4 G5, §4 CUT |
| F1 | `ChestMenuClickListener`'s ~130-line deposit/placeholder/swap logic doesn't compile against three finer `MenuTarget` defaults | Collapsed to one `handleDepositClick` default — Keystone-side fix, landed in `docs/extraction-roadmap.md`; doesn't change anything WS2 itself writes | (Keystone repo) |
| F2 | "`InventoryParser`/`handler.*` stay in gangland-impl" was false — the ~40-file Gangland dialect actually lives in the doomed `inventory-api` module and no gate moved it | New gate **G3a**: mechanical move into `gangland-impl` `org.luckyraven.gangland.menu.*`, no behaviour change, before G3's real retarget | §4 G3a |
| F3 | `Multi.Item_Source`/`Multi.Per_Page` drive `MultiInventory` pagination; no G3 step rebuilt them on Keystone's `PagedRegion` | G3 explicitly rebuilds the `Multi.*` stack on `PagedRegion` (+1 d) | §4 G3, §12 |
| F4/F8/F9 | Keystone-side: `ChestMenu` extension points, `ItemComponent` is a new class not a strip, `PageConfig` sourced from Oriel's copy diffed against Gangland's | Landed in `docs/extraction-roadmap.md`'s E6 section — nothing this plan writes changes as a result, but G3's `PageConfig`/`PagedRegion` consumption depends on that diff landing correctly | (Keystone repo); §4 G3 |
| F5 | Same clash as R11, filed as a Critical finding | See R11 | — |
| F6 | Estimate correction | Keystone 5–6 d (Keystone repo); Gangland (this plan) 7–9 d + 0.5 d cutover | §12 |
| F7 | `/glw debug` `User.getInventories()` lists several handlers; under `keystone-inventory` the tracker is one menu per player | `/glw debug inv-data` **stays**, reduced to the tracker's current-menu-per-player shape — not deleted, unlike the old Oriel plan (whose tracker was unpublished, B4) | §5, §9 |
| Docket | `InventoryHandler.SPECIAL_INVENTORIES` static map and `static InventoryRegistry registry` leak across reloads | Recorded fixed-by-WS2 | §11 |

**What R10 removes from the old plan's problem list, not just relabels.** `keystone-inventory` is a plain
`provided`-scope library module (the `keystone-item`/`keystone-hologram` shape) that Gangland constructs its own
bean from — not a second running plugin reached through `ServicesManager`. That dissolves the old plan's biggest
pain points outright: no `MenuConfigService` 17-arg/4-unpublished-args constructor (C5), no `forConsumer` factory
ask, no foreign YAML schema to port the 9 menus onto (`InventoryParser` keeps Gangland's own schema and just
retargets what it builds), no `unique_items.yml` side-file (C7 — `InventoryRuntimeContext.registerUniqueItemHandler`
keeps working unchanged), no `Enchanted:` rename (D5/N1 already dissolved this pre-retarget), and — per
`docs/extraction-roadmap.md`'s own E6.1 note that Gangland contributes `SlotView.interactive(boolean)` — the
`BarterView`/`SellView` deposit-slot **redesign** risk (C3, the old plan's largest single risk, two ~500-line
files with item-loss exposure) collapses to a mechanical re-point, because the interactive/draggable slot concept
*is* Gangland's own, not a foreign primitive to bridge onto.

---

## 1. Scope

**In:**
- Add `keystone-inventory` (`org.luckyraven:keystone-inventory`, `provided` scope — exact artifactId to verify at
  G1 against Keystone's `keystone-item`/`keystone-hologram` naming convention) to the poms that actually build a
  menu: `gangland-impl`, `gangland-ui/shop-api`, `gangland-features/gangland-npc-shops`,
  `gangland-features/gangland-turf`, `gangland-features/cops-n-crooks`, `gangland-features/gangland-gadget`.
  **Not** `gangland-api` — R6 (reworded 2026-09-17): the api never re-exports `keystone-inventory`; a
  menu-building module declares it directly (the `bartizan-api` rule).
- Sever `gangland-infra/gangland-domain`'s dependency on `inventory-api` — the one dependency inversion in the
  wave, unchanged from the old plan (`User`/`UserFactory`'s `Set<InventoryHandler>` + `InventoryRegistry` deleted,
  replaced by `keystone-inventory`'s `OpenMenuTracker`, owned directly by Gangland's own `InventoryService` bean).
- Move the ~40 Gangland-dialect files out of the doomed `inventory-api` module into `gangland-impl`
  (`org.luckyraven.gangland.menu.*`) at G3a, then retarget `InventoryParser` and the `handler.*` dialect onto
  `ChestMenuBuilder` at G3. The 9 core YAML menus under `plugins/Gangland_Warfare/menus/` load **unchanged** — no
  migration, because Gangland keeps its own schema (unlike the old Oriel plan's `MIGRATION-FROM-GLW.md` port).
- Migrate every WS2-scoped consumer file (npc-shops 18, turf/cops-n-crooks/gadget per R11) onto
  `keystone-inventory` primitives: `ChestMenu`/`ChestMenuBuilder`, `MenuFlow`/`Panel`/`FlowState`, `PagedRegion`,
  `ItemComponent`/`FillComponent`/`BorderComponent`/`LineComponent`.
- `plugin.yml` keeps `depend: [Keystone]` only — no `Oriel` entry, WS2-D1 dissolved.
- Retire `.claude/skills/panel-create/` and rewrite `documentation/developer/ui-framework.md` — **at the shared
  CUT gate**, not inside WS2's own sequence (unchanged reasoning from R3/B1).

**Out (belongs to other workstreams, R11):**
- `LootChestService.java`/`LootChestSession.java` (lootchest-api) — **entirely WS3's scope**. They wrap the loot
  chest's own raw Bukkit `Inventory` (`SharedLootInventory`, per the Keystone E6 roadmap's WS3 note); WS2 makes
  zero changes to `lootchest-api`. WS2 still touches the wand's `UniqueItemHandler` config source only because that
  lives in `gangland-impl`, not `lootchest-api` (moved to `menu.unique` at G3a).
- shop-api's 9 `MultiPanelInventory`-based admin-view files — **WS4 G1b's job**. WS2 only adds
  `keystone-inventory` as an additional pom dependency to `shop-api` (additive, doesn't require deleting
  `inventory-api`) so WS4 can start porting once its own gate opens.
- The gang module extraction (WS5) and the api facade (WS6) — untouched, except that severing `User`/`UserFactory`
  from `inventory-api` is a prerequisite WS5 benefits from (unchanged from the old plan).
- **Not carried from the old plan at all** (R10 dissolves the question): Oriel's `MenuConfigService`, `TraderMenu`,
  `AnvilMenu`, `FilterRegistry`/`SortRegistry`/`SearchMatcherRegistry`, `DepositSlotComponent`, the
  `MIGRATION-FROM-GLW.md` schema port, `Enchanted:` YAML rename, `unique_items.yml`, `HeadMaterialSources`
  consolidation. None of these exist as a WS2 concern under `keystone-inventory`.

---

## 2. Target layout

### Dependency table

| Consumer pom | `keystone-inventory` (`provided`) | Why |
|---|---|---|
| `gangland-impl/pom.xml` | yes | core menus, `InventoryParser`/`handler.*` dialect targets `ChestMenuBuilder`, `/glw debug villager` (Gangland's own `villager.*`, moved to `menu.villager`, unchanged — no Oriel `TraderMenu` swap) |
| `gangland-ui/shop-api/pom.xml` | yes (additive; WS4 G1b executes the view rewrite) | replaces its `inventory-api` `provided` dep |
| `gangland-features/gangland-npc-shops/pom.xml` | yes | trader/banker flows |
| `gangland-features/gangland-turf/pom.xml` | yes | powerup flow |
| `gangland-features/cops-n-crooks/pom.xml` | yes | paperwork/bribe views |
| `gangland-features/gangland-gadget/pom.xml` | yes | car sign preview list |

`gangland-api/pom.xml` is **not** in this table (R6). `gangland-ui/lootchest-api` is **not** in this table (R2/R11
— WS3 owns any Keystone dependency it needs when it converts the module).

### `plugin.yml`

`depend: [Keystone]`, `softdepend: [PlaceholderAPI, Vault, ViaVersion, Citizens, Bartizan]` — **no `Oriel` entry**
(WS2-D1 dissolved by R10; contrast the old plan's hard `depend: [Keystone, Oriel]`).

### Package layout — G3a target: `org.luckyraven.gangland.menu.*`

The ~40 (exactly 46 of 58) Gangland-dialect files move verbatim (package rename + import fixes, no behaviour
change — F2) from `gangland-ui/inventory-api/.../inventory/**` into `gangland-impl/.../menu/**`:

- **`org.luckyraven.gangland.menu`** (root): `State.java`, `InventoryData.java`, `InventoryBuilder.java`,
  `OpenInventory.java`
- **`menu.condition`**: `BooleanExpressionEvaluator.java`, `ConditionEvaluator.java`, `ConditionalSlotData.java`,
  `SlotCondition.java`
- **`menu.filter`**: `FilterAdapter.java`, `FilterApplier.java`, `FilterBinding.java`, `FilterField.java`,
  `FilterRegistry.java`, `FilterStore.java`, `FilterValue.java`, `SearchButtonFactory.java`, `SearchFilter.java`,
  `SortDescriptor.java`, `StandardFilterField.java`
- **`menu.handler`**: `AbstractCommandSlotHandler.java`, `ClickSlotHandler.java`, `CloseSlotHandler.java`,
  `DropSlotHandler.java`, `JoinSlotHandler.java`, `PlayerInteractSlotHandler.java`, `QuitSlotHandler.java`,
  `SlotContext.java`, `SlotEventHandler.java`, `SlotItemFactory.java`, `SwapHandSlotHandler.java`
- **`menu.multi`**: `ItemSourceEntry.java`, `ItemSourceProvider.java`, `ListEntry.java`, `MultiInventory.java`,
  `MultiInventoryCreation.java`, `MultiInventoryNavigation.java`, `StaticSlotEntry.java`
- **`menu.part`**: `ButtonTags.java`, `ConditionalSlotResult.java`, `Fill.java`, `Slot.java` (**not** `PageConfig.java`
  — that one folds into Keystone's, F9, see below)
- **`menu.unique`**: `UniqueItemHandler.java`
- **`menu.villager`**: `VillagerInventory.java`, `VillagerInventoryListener.java`, `VillagerInventoryRegistry.java`,
  `VillagerTrade.java`

**(To verify at G3a):** whether any of these 46 files reference `InventoryHandler`/`InventoryData`'s *concrete*
implementation directly (as opposed to being called *by* it) — if so, G3a and G3 may need to land as one combined
commit rather than G3a alone compiling green; the E6 review's "no behaviour change; one commit" ruling assumes
they don't, but neither the review nor this pass read every file body to confirm it.

### Deletions — 12 files stay in `inventory-api`, replaced 1:1 by `keystone-inventory`, never moved

| Deleted file | Replaced by |
|---|---|
| `InventoryHandler.java` | `MenuHolder` + `ChestMenu` |
| `InventoryOpener.java` | `MenuRegistry`/`MenuOpener` |
| `flow/FlowSession.java` | Keystone's `FlowState` |
| `flow/MultiPanelInventory.java` | `MenuFlow` |
| `flow/Panel.java` | Keystone's `Panel<S>` |
| `listener/InventoryClickHandler.java` | `MenuListener` |
| `listener/InventoryCloseHandler.java` | `MenuListener` |
| `listener/InventoryDragHandler.java` | `MenuListener` |
| `listener/PlayerInventoryCleanup.java` | deleted outright — `InventoryHolder` identity removes the linear registry scan this class cleaned up after |
| `part/PageConfig.java` | Keystone's `PageConfig` (diffed against this one, Gangland's extra arithmetic folded in — F9, Keystone repo) |
| `service/InventoryRegistry.java` | `InventoryService`'s `OpenMenuTracker` |
| `util/InventoryUtil.java` | `ChestMenuBuilder`'s fill/border/line + `ItemComponent` |

### At the shared CUT gate (after WS2 G5, WS3 G3 **and** WS4 G1b — R11)

`gangland-ui/inventory-api/` itself (pom + all 58 files, both the moved-and-superseded and the moved-to-`menu`
copies now live in `gangland-impl`), `.claude/skills/panel-create/` (retargeted to scaffold Keystone's
`Panel<S extends FlowState>`, not Oriel's), `documentation/developer/ui-framework.md` rewrite, the `settings.yml`
`Inventory:` block + its `Settings` getters. **Not** removed, unlike the old Oriel-targeted plan:
`net.wesjd:anvilgui` — Gangland keeps its own `AnvilGUI`-driven gang/user-stat search locally under R10 (no
Keystone ask for it), so the shade dependency stays.

---

## 3. Seams

| Cross-boundary call | Mechanism | Publisher | Puller | Default when absent |
|---|---|---|---|---|
| Gangland's own menu construction | `InventoryService` — a plain `@Bean` in `gangland-impl`'s CONFIG phase, own instance (not resolved via `ServicesManager`: `keystone-inventory` is a `provided`-scope library dependency, not a second running plugin — none of the old plan's `MenuConfigService` 17-arg/`forConsumer` pain applies) | `keystone-inventory` (E6.1) | every `@Configuration` class that opens/registers a menu | n/a — compile-time dependency, always present |
| Menu open-tracking | `InventoryService`'s `OpenMenuTracker`, instance-scoped, owned directly by Gangland's own bean — no publish/lookup step (contrast Oriel's internal-only tracker, old plan's B4) | `keystone-inventory` | `DebugCommand`'s `inv-data` argument — **kept, reduced to current-menu-per-player** (F7), not deleted | tracker always constructed with the bean |
| Panel re-render in place | `MenuFlow.rerender()` (E6.2 deliverable — RETARGET's requirements table, was Oriel ask R5) | `keystone-inventory` | ~20 Gangland call sites (npc-shops, turf, shop-api — same sites the old plan counted) | none — this is now a Keystone E6.2 deliverable, not an ask that can be declined |
| Interactive/draggable slot | `SlotView.interactive(boolean)` — sourced **from Gangland** at E6.1 (`docs/extraction-roadmap.md`: "Gangland contributes the interactive-slot flag") | `keystone-inventory` | `BarterView`/`SellView` (npc-shops) — a re-point, not a redesign (contrast old plan C3) | slot is non-interactive by default, click cancelled |
| Gangland's own menu YAML | `InventoryParser` + `menu.handler`/`menu.condition`/`menu.filter`/`menu.unique`/`menu.villager` (moved at G3a, retargeted at G3 to build `ChestMenuBuilder`/`MenuHolder` instead of the deleted `InventoryBuilder`/`InventoryHandler`) | Gangland (unchanged schema, no migration) | the 9 core YAML menus under `plugins/Gangland_Warfare/menus/`, files unchanged | n/a |
| Paginated lists (gang search, user stats, multi-page menus) | `PageConfig` + `PagedRegion` (E6.2) | `keystone-inventory` | `menu.multi.*` (moved, retargeted at G3, F3) | G3 explicitly rebuilds `Multi.Item_Source`/`Multi.Per_Page` on `PagedRegion` |
| Anvil-driven paginated search | Gangland's own `AnvilGUI` prompt, unchanged | Gangland | `phone_gang_search.yml`/`user_stat.yml` | n/a — no Keystone ask (RETARGET requirements table) |
| Sign → menu open | `MenuRegistry`/`MenuOpener` (replaces `InventoryOpener`) | `keystone-inventory` | sign-api's existing `ViewInventoryAspect` seam, unchanged shape | n/a |
| `CarSignViewProvider` (gadget) | Existing `SignViewProvider` seam, body swapped `InventoryHandler`→`ChestMenuBuilder` | gadget module | core `SignManager` | n/a |
| Villager trade UI | Gangland's own `villager.*` (moved to `menu.villager` at G3a), unchanged product — no Oriel `TraderMenu` swap under R10 | Gangland | `/glw debug villager` only | n/a |

---

## 4. Gates

Each gate ends with a green `mvn clean install` and a named smoke row. K2 (Keystone 1.11.0 E6.1 → E6.2) is a
prerequisite that runs as its own Keystone-side phase with its own review — not one of WS2's own G-gates.

### K2 — Keystone 1.11.0 (prerequisite, Keystone repo, own review)
`keystone-inventory` E6.1 core → E6.2 flow + pages. Tracked in `docs/extraction-roadmap.md`; not scheduled here.

### G1 — Gangland reactor plumbing
Add the 6-pom `keystone-inventory` dependency table (§2) at `provided` scope — compile side by side with
`inventory-api`, no behaviour change yet. `plugin.yml` unchanged (`Keystone` already there; no `Oriel` entry to
add). **Gate:** `mvn clean install` full reactor; smoke — server boots with Keystone + `keystone-inventory` on the
classpath, no menu opened yet.

### G2 — Sever the domain inversion (gangland-domain)
`GangFilterAdapter.java`/`MemberFilterAdapter.java` move `gangland-domain` → `gangland-impl` (drop their
`inventory.filter.*` imports once G3a's target package exists). `GangItemSourceProvider.java` rewritten to build
`ItemStack`s directly and register against Gangland's own (moved) `FilterRegistry`, not an Oriel one. `User.java`
deletes `Set<InventoryHandler> inventories`, `InventoryRegistry inventoryRegistry`, and
`addInventory/removeInventory(InventoryHandler)/removeInventory(String)/getInventory/getInventories/
clearInventories()`; `UserFactory.java` drops its `InventoryRegistry` param. `gangland-domain/pom.xml` drops the
`inventory-api` dependency. **In the same commit (B2, unchanged from the old plan):** fix the three domain tests
this signature change breaks — the wave's actual regression guard, not net-new coverage:
- `gangland-infra/gangland-domain/src/test/.../user/UserTest.java`
- `.../user/UserManagerTest.java`
- `.../GangMembershipTest.java`

All construct `new User<>(plugin, handle, placeholder, new InventoryRegistry())` — drop the fourth argument to
match `User`'s new 3-arg constructor. **Gate:** `mvn clean install -pl gangland-infra/gangland-domain,gangland-impl
-am`; smoke — `/glw filter gangs search <name>`.

### G3a — Move the Gangland dialect (new gate, F2)
Move the 46 files listed in §2's package layout from `gangland-ui/inventory-api/.../inventory/**` into
`gangland-impl/.../menu/**`, fix importers. **No behaviour change; one commit** — this is relocation only, not the
`ChestMenuBuilder` retarget (that's G3). Precondition/acceptance: every moved file still compiles and every
consumer that imported `org.luckyraven.gangland.inventory.{handler,condition,filter,unique,multi,part,villager}.*`
now imports the `menu.*` equivalent instead. **Gate:** `mvn clean install -pl gangland-impl -am`.

### G3 — Core menus (gangland-impl)
Retarget `InventoryParser`/`InventoryDefinitionStore`/`InventoryLoader`/`InventoryRuntimeContext`/
`ConditionalSlotParser` (`gangland-impl`'s own `file/configuration/inventory/` package, separate from the 58-file
census) to build `ChestMenuBuilder`/`MenuHolder`-based menus instead of the now-deleted
`InventoryBuilder`/`InventoryHandler`/`InventoryData`/`OpenInventory`/`State` stack. The 9 core YAML menus under
`plugins/Gangland_Warfare/menus/` load **unchanged** — no schema migration, unlike the old plan's
`MIGRATION-FROM-GLW.md` port. `menu.handler`/`menu.condition`/`menu.filter`/`menu.unique`/`menu.villager` (moved at
G3a) retarget their dispatch to hook `ChestMenuBuilder`'s `slot()`/`ClickHandler` API instead of the deleted
`InventoryHandler`'s. **`menu.multi.*`'s `Multi.Item_Source`/`Multi.Per_Page`-driven pagination is explicitly
rebuilt on `PagedRegion`** (F3, +1 d — `MultiInventory`/`MultiInventoryCreation`/`MultiInventoryNavigation`'s
ad hoc paging logic is replaced, not ported verbatim). Real command/sign/reload consumers (carried from the old
plan's C6 correction, unaffected by the re-target): `command/sub/filter/FilterCommand.java`,
`command/sub/lootchest/LootChestWandEditCommand.java`, `command/sub/gang/{GangCommand,GangColorCommand}.java`,
`sign/aspect/{ViewInventoryAspect,BountyAspect}.java`, `bootstrap/ReloadPlugin.java` (`inventoryReload()`),
`lootchest/LootChestWand.java`, `listener/loot/LootChestWandListener.java`. `PlayerInventoryCleanup` is **not**
ported (deleted, §2) — `InventoryHolder` identity needs no cleanup pass. `DebugCommand`'s villager tool keeps
Gangland's own `villager.*` (moved), no Oriel `TraderMenu` swap. `/glw debug inv-data` is **kept**, reduced to the
tracker's current-menu-per-player shape (F7) — not deleted, unlike the old plan. **Gate:** `mvn clean install -pl
gangland-impl -am`; smoke — `/glw phone`, gang info, bank menu, bounty view, gang search+filter, `inv-data` shows
the current menu.

### G4 — npc-shops (18 files)
Generic `Panel`→`Panel` swap across the trader/banker view+flow files (file list unchanged from the old plan's
C6-corrected census: `trader/view/{NegotiationView,QuantitySelectorView,ShopView,ModeSelectView,TraderFlow,
TraderFlowSession}`, `banker/view/{BankerAmountView,BankerClaimView,BankerCreateAccountView,BankerMenuView,
BankerUpgradeView,BankerFlow,BankerFlowSession,BankerRenameAccountView}`, the two `listener/trader/*SessionListener`s
— 16 files), plus **`BarterView.java`/`SellView.java`** (npc-shops, ~500 lines each). Under R10 these are
**re-points, not redesigns** (§0, §3): `SlotView.interactive(boolean)` is Gangland's own draggable-slot concept
landing in `keystone-inventory`, so the deposit-zone read/restore logic keeps its shape and only swaps
`InventoryHandler.getInventory()` for `ChestMenu.bukkitInventory()` — no `DepositSlotComponent` design, no
item-survival redesign risk the old plan's C3 carried. The ~20 `MenuFlow.rerender()` call sites across these files
resolve directly against the Keystone E6.2 deliverable (§3) — no per-view fallback decision needed, unlike the old
plan (which had to choose between two lossy Oriel substitutes because Oriel had no in-place rerender at all).
**Gate:** `mvn clean install -pl gangland-features/gangland-npc-shops -am`; smoke — trader buy/sell/barter/
negotiate, banker create/deposit/claim/upgrade.

### G5 — turf · cops-n-crooks · gadget (R11 — lootchest and shop views excluded)
turf: 6 files, same generic swap (`TurfPowerupBuffCatalogueView`/`TurfPowerupGarrisonView` among the `rerender()`
call sites, same as G4). cops-n-crooks: `detainment/paperwork/{PaperworkView,HandcuffBribeView}.java`. gadget:
`CarSignViewProvider.java` off `InventoryHandler`/`Fill`/`InventoryUtil`. **Not this gate's job** (R11):
`lootchest-api` (WS3's), shop-api's 9 admin views (WS4 G1b's). **Gate:** `mvn clean install -pl
gangland-features/gangland-turf,gangland-features/cops-n-crooks,gangland-features/gangland-gadget -am`; smoke —
turf powerup menu, detainment paperwork/bribe, car sign hover.

### CUT — shared cutover gate (after WS2 G5, WS3 G3 **and** WS4 G1b — R11)
Precondition, verified in the gate, not assumed:
```
grep -rl "org.luckyraven.gangland.inventory" --include=*.java . | grep -v "gangland-ui/inventory-api/"
```
must return zero hits. Steps: delete `gangland-ui/inventory-api/` (pom + 58 files) and its `<module>` line;
retire `.claude/skills/panel-create/`; rewrite `documentation/developer/ui-framework.md`; delete the `settings.yml`
`Inventory:` block and its matching `Settings` getters in the same commit; `graphify update . --force`; docket
record (§11); memory note. `net.wesjd:anvilgui` is **not** touched (§2 — stays, Gangland keeps its own AnvilGUI
search). **Gate:** `mvn clean install` full reactor, zero `org.luckyraven.gangland.inventory.*` references
anywhere; full regression smoke.

---

## 5. Config, messages, permissions

| Item | Today | Destination |
|---|---|---|
| `settings.yml` `Inventory:` block | `Fill.Item/Name`, `Line.Item/Name`, `Multi_Inventory` base64 heads | Unchanged shape, bakes into `ChestMenuBuilder` calls the same way it fed `InventoryUtil` today. Block + its `Settings` getters deleted together at the **CUT gate**, not earlier — the YAML files still reference the old block's values until every consumer is off `inventory-api`. |
| `commands.json` | No `/glw debug inventory`/`inv-data` entry exists today (confirmed by the old plan's C9 grep) | Nothing to add — `/glw debug inv-data` is an existing `DebugCommand` argument, not a separate command; it stays wired, reduced to current-menu-per-player (F7). |
| Unique-item config | `Information.Event.UniqueItem` block inside each menu YAML, parsed by `InventoryRuntimeContext.registerUniqueItemHandler` (moved to `menu.unique`/impl's own loader at G3a/G3) | **Unchanged** — no side-file, no schema migration (R10 dissolves the old plan's C7). |
| `Enchanted: true` (`gang_info.yml`, `phone_banking.yml`) | Static YAML item flag | **Unchanged** — Gangland keeps its own `ItemBuilder` path; no Oriel NBT gap to sidestep (D5/N1 already dissolved this before the retarget). |
| `Messages` enum | Zero inventory-specific constants | No change. |
| `.claude/skills/panel-create/` | Scaffolds `Panel<S extends FlowSession>` | Retargeted to scaffold Keystone's `Panel<S extends FlowState>` (not Oriel's) **at the shared CUT gate**, not earlier — shop-api (WS4) still uses the old pattern until then. |
| Permission nodes | None found under `gangland.inventory.*` | Per-menu `permission:` keys carry forward unchanged. |

---

## 6. Persistence

None. No tables move. `User.inventories` (deleted) and `keystone-inventory`'s `OpenMenuTracker` are both
in-memory.

---

## 7. Tests

| Test | Change | Why |
|---|---|---|
| `UserTest.java` | Drop the `InventoryRegistry` constructor arg | `User`'s constructor shrinks to 3 args (G2) |
| `UserManagerTest.java` | Same | Same |
| `GangMembershipTest.java` | Same | Same |
| `GangFilterAdapterTest`/`MemberFilterAdapterTest` (new) | Filtering/sorting still matches `GangFilterRegistration`'s declared fields after the `gangland-domain`→`gangland-impl` move | G2 |
| `GangItemSourceProviderTest` (new) | Built `ItemStack`s round-trip through Gangland's own (moved) `FilterRegistry`/paginated source registration | G2/G3 |
| `InventoryParser` round-trip on the 9 YAML menus (new) | Every menu loads to the same `ChestMenuBuilder` shape it loaded to under the old `InventoryBuilder`, files byte-unchanged | G3 |
| `Multi.*`-on-`PagedRegion` paginated-stack test (new) | Page count/remainder/first-last-page arithmetic matches the old `MultiInventoryNavigation` behaviour after the F3 rebuild | G3 |
| Interactive-slot barter/sell test (new) | `BarterView`/`SellView` items placed in the interactive slot round-trip through `ChestMenu.bukkitInventory()` on close/reopen — the re-point's regression guard, smaller in scope than the old plan's item-survival-under-crash test because there is no deposit-slot redesign to guard | G4 |

### Smoke rows

| Row | Setup | Steps | Expect |
|---|---|---|---|
| S1 | Keystone + `keystone-inventory` + Gangland, no menu opened | boot | Zero `inventory.*`/`menu.*` faults |
| S2 | S1 + a gang | `/glw filter gangs search <name>` | Filtered gang list renders |
| S3 | S1 | `/glw phone`, gang info, bank deposit/withdraw, bounty view, `phone_gang_search`, `inv-data` | Every core menu opens and clicks; `inv-data` shows the current menu |
| S4 | S1 + a trader/banker NPC | buy, sell, barter, negotiate, create account, deposit, claim, upgrade | Every flow works; barter/sell items intact across menu interactions |
| S5 | S1 + turf/cops | turf powerup menu, detainment paperwork, bribe offer, car sign hover | Renders and clicks |
| S6 | full reactor after CUT | boot | No `org.luckyraven.gangland.inventory.*` references anywhere |

---

## 8. Risks

| # | Risk | Mitigation |
|---|---|---|
| 1 | G3a's "no behaviour change" claim is unverified at the file-body level — some of the 46 moved files may reference `InventoryHandler`/`InventoryData` concretely | Verify at G3a execution time (§2); fall back to landing G3a+G3 as one combined commit if so |
| 2 | `Multi.*`→`PagedRegion` rebuild (F3) is new work, not a port — page-arithmetic edge cases (remainder, first/last page) could drift from today's behaviour | New paginated-stack test (§7) pins the arithmetic before/after |
| 3 | `MenuFlow.rerender()` and `SlotView.interactive` are both Keystone E6.2/E6.1 deliverables this plan depends on but does not build — a slip in K2 (Keystone 1.11.0) blocks G4/G5 | K2 is tracked as its own reviewed Keystone phase; no WS2 gate starts ahead of the E6.1/E6.2 pieces it needs |
| 4 | `keystone-inventory`'s exact artifactId/coordinates are unconfirmed | To verify at G1 |
| 5 | Whether shop-api's own admin views (WS4's, not this plan's) turn out to have their own dropzone code that WS4 didn't anticipate | Flagged for WS4's own planning, not WS2's — carried from the old plan's equivalent note |

**Rollback story per gate:** G1 additive, revert by dropping the branch. G2–G5 each touch a bounded file set and
leave `inventory-api` compiling until the shared CUT gate — revert any single gate by reverting its commits. CUT
is the only irreversible gate (module deletion) and explicitly waits on WS3/WS4 too (R11).

---

## 9. Decisions for the user

| Id | Status |
|---|---|
| ~~WS2-D1~~ (Oriel dependency hardness) | **Dissolved by R10** — no Oriel dependency exists to size |
| ~~WS2-D2~~ (menu YAML home) | **Dissolved by R10** — stays `plugins/Gangland_Warfare/menus/`, Gangland's own schema, no `MenuConfigService.forConsumer` question |
| ~~WS2-D3~~ (filters/search/sort: Oriel's registries or GLW's ported) | **Dissolved by R10** — stays Gangland's own `filter.*` (moved to `menu.filter` at G3a), no Oriel-registry option ever existed under this target |
| ~~WS2-D5~~ (`Enchanted: true` on 4 slots) | **Dissolved by R10** — already superseded pre-retarget (re-review N1); no Oriel/`keystone-inventory` schema question left, the YAML key is unchanged |
| New — `/glw debug inv-data` | **Not a decision, a ruling (F7):** stays, reduced to current-menu-per-player. No user call needed. |

No open decisions remain for WS2 beyond what §1/§2 already state as settled by R10/R11.

---

## 10. Keystone E6 requirements this migration depends on

Carried verbatim from `exec/WS2/RETARGET-2026-09-17.md`'s board section — the primitives WS2's gates above assume
`keystone-inventory` ships:

| Primitive | Why | Where in E6 | Blocking? |
|---|---|---|---|
| In-place `Menu.rerender()` / `MenuFlow.rerender()` | ~20 Gangland call sites (was Oriel ask R5) | E6.1 / E6.2 | yes — G4/G5 |
| Per-consumer `InventoryService` owning an `OpenMenuTracker` | replaces `User.inventories` (was Oriel ask R5) | E6.1 | yes — G2 |
| `SlotView.interactive(boolean)` + the drag policy | barter, sell and loot views take and place items | E6.1 | yes — G4/G5 |
| `MenuFlow` in-place switch, suspend/resume, `back()`/`hasBack()` | panel flows in shop-api, npc-shops, turf | E6.2 | yes — G4/G5 |
| `PageConfig` + `PagedRegion` | gang search, user stats, multi-page YAML inventories | E6.2 | G3 |
| `MenuRegistry` (name → menu, `openPrevious`) | replaces `InventoryOpener` and the command-open lookup | E6.1 | G3 |
| `ItemBuilder` overloads on `ItemComponent` and the slot setters | Gangland's call sites pass `ItemBuilder`, not `ItemStack` | E6.1 | no — one adapter line otherwise |
| Price-entry grid component (mirrored ± rows) — from WS4 | the shop price editor | consumer-side, over `ChestMenuBuilder` | no Keystone ask |
| Anvil-driven paginated search | gang search and user stats menus | stays Gangland (`AnvilGUI` in impl) | no Keystone ask |

---

## 11. Docket

Two triage rows recorded **fixed-by-WS2** at the docket step (per the E6 review's W23 ruling): `InventoryHandler`'s
static `SPECIAL_INVENTORIES` map and the static `InventoryRegistry registry` seam — both leak across plugin
reloads; the `InventoryHolder`-identity model in `keystone-inventory` removes the registry scan that needed them.

`brainstorming/bug-docket-2026-09-06/findings/` — grepped for inventory/menu/GUI/panel keywords: zero matches
(carried from the old plan's check, not re-verified this pass). The old plan's flagged Oriel cross-docket check
(`cross-docket-2026-09-10/oriel/findings/`, 9 files) **no longer applies** — WS2 has no Oriel dependency under R10,
so those findings are Oriel's own concern, not this plan's.

---

## 12. Estimate

| Gate | Wall-clock | Why |
|---|---|---|
| G1 | 0.5–1 d | 6-pom dependency table, no Oriel property/version juggling — simpler than the old plan's 7-pom/`<oriel.version>` table |
| G2 | 1–1.5 d | domain inversion, 3 test fixes |
| G3a | 0.5 d | mechanical move, one commit, no behaviour change (F2) |
| G3 | 2.5–3 d | `InventoryParser`/`handler.*` retarget to `ChestMenuBuilder`, 9 YAML menus unchanged, `Multi.*`→`PagedRegion` rebuild (+1 d, F3), the real command/sign/reload consumer list |
| G4 | 1.5–2 d | npc-shops 18 files — mechanical `Panel` swap + barter/sell **re-points** (not redesigns — no deposit-slot risk, unlike the old plan's C3) |
| G5 | 1 d | turf · cops-n-crooks · gadget only (R11) |
| **WS2 own total** | **7–9 d** | matches F6's corrected estimate |
| CUT | 0.5 d, **not scheduled by WS2** | shared gate; triggers once WS3 G3 **and** WS4 G1b also finish (R11) |

---

## 13. Not verified

- Whether the 46 G3a-moved files compile standalone in `gangland-impl` before G3's retarget touches them, or
  whether some (`menu.handler`/`menu.multi` in particular) reference `InventoryHandler`/`InventoryData` concretely
  and need G3a+G3 landed together (§2, §8 risk 1) — not read at the file-body level by the E6 review or this pass.
- 1.16.5-floor cleanliness of Gangland's own moved files — the E6 review's "Cannot verify" section covers
  Keystone/Oriel's side only, not Gangland's `menu.*` dialect.
- Whether `MenuFlow.switchTo` already switches in place (E6 review "Cannot verify" — affects whether G4/G5's
  `rerender()` call sites need any per-view fallback at all).
- npc-shops file count of 18 — E6 review flagged this as unverified (grep page limit); this plan carries the old
  plan's C6-corrected list at face value.
- `keystone-inventory`'s exact Maven `artifactId` (§8 risk 4).
- No compile was attempted in either repo for this plan or the E6 review — every signature/wiring claim is static
  analysis (grep + graphify + source reads, per this document's own directory listing). The per-module `mvn clean
  install` gates remain the actual verification.
- Whether shop-api's own admin views have dropzone code WS4 needs to solve independently (§8 risk 5) — not checked
  this session, carried as a note for WS4's own plan.

## §0d Execution notes (2026-09-17, orchestrator, from the Keystone E6.1 review)

- **G4 consumer responsibility:** a player-placed item in an `interactive` slot that is NOT an `ItemHoldingComponent` is discarded when the menu closes (Keystone only returns `ItemHoldingComponent` contents, as Gangland does today). The barter and sell views must either model their input slots as `ItemHoldingComponent`s or return the slot contents themselves in `onClose`; an item-survival test per view (Escape, disconnect, panel switch) is part of G4.
- **Fixed-by-WS2 candidate:** Gangland `InventoryClickHandler.java:34-43` lets `COLLECT_TO_CURSOR` on a draggable top slot pull display items out of non-draggable slots; Keystone E6.1 cancels it (review F4), so the cutover fixes it — record at the docket step.
- Keystone `InventoryService` returns held items on the tracked close branch because CraftBukkit closes the inventory before `PlayerQuitEvent` (SPIGOT-5799); a console smoke item verifies the order at G-final.

## §0e Execution corrections (2026-09-20, orchestrator, from the G1+G2 review)

- §4 G2's `GangItemSourceProvider` sentence is pre-R10 residue: the class already lives in `gangland-impl` and needs no G2 work; its `PagedRegion` rebuild is G3's.
- The two filter adapters move `gangland-domain` → `gangland-impl` at G2 because the domain pom drops `inventory-api`; their `inventory.filter.*` imports change at G3a, not G2. Their projection tests (`GangFilterAdapterTest`, `MemberFilterAdapterTest`) are written at G3a.
- The impl-side callers of the deleted `User.getInventory/addInventory/clearInventories/getInventories` (`InventoryRuntimeContext`, `RemoveAccountListener`, `DebugCommand`) get an interim per-user map in `InventoryRuntimeContext` (parity with the old set — the global `InventoryRegistry` is a superset); G3 throws that shim away when menus open through the Keystone service.
- `InventoryService` is one CONFIG-phase bean in `GameplayConfig` (`registerListeners` once; `BeanLifecycle` sweep automatic).
- `/glw debug inv-data` reads the Keystone tracker live and shows "none" until G3 opens menus through it.

