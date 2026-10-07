# WS4 G1b report — shop admin editor rebuilt onto keystone-inventory + hygiene docs sweep

Batch 6b of the 0.10.0 decoupling-wave execution. Spec: `brainstorming/decoupling-wave-2026-09-14/plans/WS4-shop.md`
§4 G1b steps 13-15 + Hygiene step 16, with the coordinator's corrections applied verbatim (Oriel = keystone-inventory;
`MenuFlow.rerender()` exists in Keystone 1.11.0, no per-site fallback needed; rebuild onto `ChestMenuBuilder`
panels driven by `MenuFlow`, exact G4/G5 pattern; `ShopAdminOpenerImpl` keeps its interface; §0d item-slot ruling).
Worktree: `E:\Programming\java\wt\gangland-0.10.0`, branch `0.10.0`, reconciled clean at 720557fe before starting.
No commits made.

## Summary

The 9 admin-editor files (`ShopAdminFlow`, `ShopAdminFlowSession`, `ShopAdminView`, `PriceEditorView`,
`SellCategoryItemsAdminView`, `BarterCategoryItemsAdminView`, and their 3 listeners) are rebuilt off
`MultiPanelInventory`/inventory-api onto Keystone's `MenuFlow`/`ChestMenuBuilder`/`ItemComponent`, following the
exact pattern already shipped for npc-shops' trader/banker views (G4) and turf/cops-n-crooks/gadget (G5).
`ShopConfig.shopAdminFlow` now also takes `InventoryService` (already a core `GameplayConfig` bean, no new
registration needed). `ShopAdminOpenerImpl` needed **zero code changes** — its interface and the
`shopAdminFlow.start(admin, definition)` call are both unchanged (step 14).

**§0d finding, the headline result of this batch: none of the 9 files hold a player item in a menu slot.** Read
every click handler in all 9 files before porting — the BUY-tab / SELL-category / BARTER-category "drop an item to
add a template" mechanic `event.setCancelled(true)`s the click *before* reading the source item and cloning it; the
original item never leaves the player's cursor or bottom inventory. There is nothing for Keystone's item-holding
contract (`ItemHoldingComponent`, `builder.interactive(slot)`) to apply to — see the slot-by-slot table below. This
is the "if no admin view takes a player item, say so slot-by-slot" branch of the ruling, not the "add
`ItemHoldingComponent`" branch — confirmed by direct code reading, not assumed.

**Gate: PASS.** `gangland-impl -am clean install -DskipTests` green on the first build. Whole-reactor
`mvn clean install` green: **818 tests, 0 failures/errors/skipped** — unchanged from the fix-round-1 count, since
this batch added no new unit tests (see "Tests" below for why) and removed none. C9 audit clean for both
`keystone/shop` and `keystone/inventory` (0 matches each — `provided` scope held for both). CUT precondition grep:
22 files total (19 gangland-impl, 2 lootchest-api, 1 gadget), listed exactly below. Smoke reused
`ws4-g1a-shop-boot`: PASS, 0 errors, `npcshops` loads with 0 faults.

## File-by-file

### Rebuilt (9 files, `gangland-impl/.../shop/admin/{view,listener}`)

| File | Old → new framework | Notable changes |
|---|---|---|
| `ShopAdminFlowSession.java` | `implements FlowSession` → `implements FlowState` | One-line import + interface swap; pure state holder, no other changes |
| `ShopAdminFlow.java` | `MultiPanelInventory<S>` → `MenuFlow<S>` via `MenuFlow.builder(inventoryService, plugin, admin, session).panel(id, view)...build()` | New `InventoryService` constructor param; the old per-render `host.onEnd(...)` moved to a single flow-wide `.onEnd(...)` at construction (`MenuFlow`'s `onEnd` is fixed at build time) — now also calls `adminPanel.onFlowEnd(admin)` / `sellCategoryPanel.onFlowEnd(admin)` / `barterCategoryPanel.onFlowEnd(admin)` to clear the 3 raw-listener views' per-admin tracking maps |
| `ShopAdminView.java` | `Panel<S>.size()`→`.rows()` (54→6), `InventoryHandler.setItem(slot, item, bool, left, right)` → `builder.slot(slot, ItemComponent.of(item).onLeftClick(...).onRightClick(...))`, `InventoryUtil.createBoarder` → `builder.border(BorderComponent.of(...).name(...))` | `active: Map<Player, ActiveContext>` simplified to `Map<Player, MenuFlow<S>>` (no separate `InventoryHandler` reference needed — `flow.currentMenu().bukkitInventory()` covers it); `host.rerender()`→`flow.rerender()` throughout; the raw-listener `handleClick` bridge kept, same shape as `BarterView`/`BarterSessionListener`'s already-shipped pattern — see §0d below |
| `PriceEditorView.java` | same size→rows, `InventoryHandler.setItem`→`builder.slot(...)`, whole-inventory glass fill → `builder.fill(FillComponent.of(...).name(" "))` | No item-holding at all (read-only preview); `host.suspend()`/`.resume()`/`.switchTo()` → `flow.suspend()`/`.resume()`/`.switchTo()` unchanged shape (matches `QuantitySelectorView`'s anvil pattern) |
| `SellCategoryItemsAdminView.java` | same, plus the chrome-fill range loop (see below) | `active: Map<Player, ActiveContext>` now holds `(MenuFlow<S>, SellCategory)`; `renderItemsDirect(ctx)` (the old raw-listener direct-update path) dropped — `appendItem`/`removeItem` now just call `flow.rerender()`, letting `MenuFlow`'s `adoptComponentsFrom` do the in-place update instead of a hand-rolled partial refresh |
| `BarterCategoryItemsAdminView.java` | mirror of `SellCategoryItemsAdminView` | same shape, `BarterCategory` instead of `SellCategory` |
| `ShopAdminListener.java` / `SellCategoryAdminListener.java` / `BarterCategoryAdminListener.java` | unchanged `@ListenerHandler` + `@EventHandler(priority = HIGH)` shape | Only the stale `MultiPanelInventory#onEnd` javadoc reference updated to describe `MenuFlow`'s flow-wide `onEnd`; the dispatch-to-`view.handleClick(event)` body is byte-identical |

**A correctness fix made during the port, not present in the old code:** `SellCategoryItemsAdminView`/
`BarterCategoryItemsAdminView`'s chrome (rows 4-5, slots 36-53) is rendered via an **explicit range loop**
(`for (slot = 36; slot < ROWS*9; slot++) if not BACK/INFO/PRICE then glass`), not `builder.fill(...)`/
`builder.border(...)`. A whole-inventory `.fill()` or `.border()` call would incorrectly glass-fill the *empty*
item-grid slots (0-35) that the original code deliberately leaves visually blank so the admin can see how much room
is left — `.fill()`/`.border()` only skip slots already explicitly `.slot()`-assigned, and the empty item slots are
never explicitly assigned when empty (same reasoning does *not* apply to `ShopAdminView`, whose `INTERIOR_SLOTS`
are the true interior excluding the border ring — there, `.border(...)` is correct and used as-is).

### `ShopConfig.java` (rewired, not relocated)
- Added `import org.luckyraven.keystone.inventory.InventoryService;`.
- `shopAdminFlow(...)` bean gained an `InventoryService inventoryService` parameter (first position), threaded
  into `new ShopAdminFlow(gangland, inventoryService, refresherRegistry, ...)`.
- The 5 admin-view beans (`priceEditorView`, `sellCategoryItemsAdminView`, `barterCategoryItemsAdminView`,
  `shopAdminView`, `shopAdminOpener`) are otherwise **unchanged** — none of those 4 views' own constructors gained
  or lost a parameter in this port.
- Class javadoc updated to drop the stale "still on inventory-api, unchanged this gate" sentence.

### `ShopAdminOpenerImpl.java` — zero changes
Per step 14: the interface boundary doesn't change, only what's behind it. `shopAdminFlow.start(admin,
definition)` is exactly the same call whether `ShopAdminFlow` is `MultiPanelInventory`- or `MenuFlow`-based.
Confirmed unchanged (not touched this batch).

## §0d — item-slot accounting, slot-by-slot (all 9 files)

| File | Slots that ever receive a player item | Verdict |
|---|---|---|
| `ShopAdminFlowSession.java` | none (pure state holder, no rendering) | N/A |
| `ShopAdminFlow.java` | none (orchestration only, no rendering) | N/A |
| `ShopAdminView.java` | BUY tab's 28 `INTERIOR_SLOTS` are the drop target region for the raw-listener bridge (`handleClick`); SELL/BARTER tabs never process drops at all (`if (session.currentKind != EntryKind.BUY) return;`) | **Not item-holding.** `event.setCancelled(true)` fires before the drop physically applies (both the cursor-drop-on-top-inventory branch and the shift-click-from-bottom-inventory branch); the item is read via `event.getCursor()`/`event.getCurrentItem()`, cloned via `refresherRegistry.refresh(...).clone()`, and the original stays wherever it was on the player |
| `PriceEditorView.java` | none — the item preview slot (`SLOT_ITEM`) only ever displays `session.priceEditItem`, a value already set by a *different* panel; nothing is ever dropped onto this panel | **Not item-holding** |
| `SellCategoryItemsAdminView.java` | 36 `ITEM_SLOTS` are the drop target region for its own raw-listener bridge | **Not item-holding** — identical `event.setCancelled(true)`-before-clone shape as `ShopAdminView`, confirmed by reading `handleClick`/`appendItem` line by line |
| `BarterCategoryItemsAdminView.java` | 36 `ITEM_SLOTS`, mirror of `SellCategoryItemsAdminView` | **Not item-holding**, same shape |
| `ShopAdminListener.java` / `SellCategoryAdminListener.java` / `BarterCategoryAdminListener.java` | n/a (pure listener bridges, no rendering) | N/A |

**Net result: zero `ItemHoldingComponent` implementations added, zero `builder.interactive(slot)` calls anywhere in
this gate's 9 files.** No item-survival tests (Escape/disconnect/panel-switch/rerender) were written for the same
reason `BarterViewItemSurvivalTest`-style tests exist for `BarterView` but not for, say, `ShopView` (display-only,
nothing to lose) — there is no item-holding behavior here to pin. The peek-and-clone contract itself (item read,
cloned, original preserved) is re-verified live via `exec/G010/WS2-manual-checklist.md`'s new WS4 G1b row 34 (no
console harness can drive a real drag-and-drop click, so this is a client-only row, same limitation as every other
GUI row in that file).

## Build

Per the gate's own ordering: `gangland-impl -am clean install -DskipTests` first (isolates a compile failure to
the module actually touched), then the whole reactor.

**`mvn clean install -pl gangland-impl -am -DskipTests` → BUILD SUCCESS**, clean on the first attempt (no
iteration needed — the API surface confirmed via `javap` against the real `keystone-inventory-1.11.0.jar` before
writing any of the 9 files matched every call site exactly).

**`mvn clean install` (whole reactor) → BUILD SUCCESS.**
**Tests run: 818, Failures: 0, Errors: 0, Skipped: 0** (152 surefire-report files — same count as the WS4 G1a fix
round 1 gate, since this batch neither added nor removed a test file; see the §0d section above for why no new
item-survival tests were warranted).

## C9 shading audit (re-run, both keystone systems)

- `unzip -l target/gangland_warfare-0.10.0.jar | grep keystone/shop` → **0 matches**.
- `unzip -l target/gangland_warfare-0.10.0.jar | grep keystone/inventory` → **0 matches**.

Both `provided`-scope disciplines held through the rebuild — no Keystone shop or menu code leaked into the shaded
core jar.

## CUT precondition grep

`grep -rl "org\.luckyraven\.gangland\.inventory\." --include=*.java .` (excluding `gangland-ui/inventory-api`
itself, which obviously imports its own package) — **22 files, listed exactly:**

**`gangland-impl` (19 — the coordinator's dispatch estimated "the known 18"; my exact count is 19, listed in full
since the instruction asked for the exact list rather than a count match):**
```
gangland-impl/src/main/java/org/luckyraven/gangland/bootstrap/ReloadPlugin.java
gangland-impl/src/main/java/org/luckyraven/gangland/command/sub/debug/DebugCommand.java
gangland-impl/src/main/java/org/luckyraven/gangland/command/sub/debug/VillagerDebugPanel.java
gangland-impl/src/main/java/org/luckyraven/gangland/command/sub/gang/GangColorCommand.java
gangland-impl/src/main/java/org/luckyraven/gangland/command/sub/gang/GangCommand.java
gangland-impl/src/main/java/org/luckyraven/gangland/command/sub/lootchest/LootChestWandEditCommand.java
gangland-impl/src/main/java/org/luckyraven/gangland/config/KernelConfig.java
gangland-impl/src/main/java/org/luckyraven/gangland/file/configuration/inventory/InventoryRuntimeContext.java
gangland-impl/src/main/java/org/luckyraven/gangland/listener/inventory/InventoryOpenByCommandListener.java
gangland-impl/src/main/java/org/luckyraven/gangland/listener/loot/LootChestWandListener.java
gangland-impl/src/main/java/org/luckyraven/gangland/listener/player/RemoveAccountListener.java
gangland-impl/src/main/java/org/luckyraven/gangland/lootchest/LootChestWand.java
gangland-impl/src/main/java/org/luckyraven/gangland/menu/InventoryBuilder.java
gangland-impl/src/main/java/org/luckyraven/gangland/menu/SimplePagedMenu.java
gangland-impl/src/main/java/org/luckyraven/gangland/menu/filter/SearchButtonFactory.java
gangland-impl/src/main/java/org/luckyraven/gangland/sign/aspect/BountyAspect.java
gangland-impl/src/main/java/org/luckyraven/gangland/sign/aspect/ViewInventoryAspect.java
gangland-impl/src/test/java/org/luckyraven/gangland/file/configuration/inventory/InventoryParserRoundTripTest.java
gangland-impl/src/test/java/org/luckyraven/gangland/menu/SimplePagedMenuTest.java
```

**`lootchest-api` (2, exactly as named):**
```
gangland-ui/lootchest-api/src/main/java/org/luckyraven/gangland/lootchest/LootChestService.java
gangland-ui/lootchest-api/src/main/java/org/luckyraven/gangland/lootchest/data/LootChestSession.java
```
(both import `InventoryHandler` only)

**gadget's `Fill` import (1, exactly as named):**
```
gangland-features/gangland-gadget/src/main/java/org/luckyraven/gangland/gadget/sign/CarSignViewProvider.java
```
(imports `org.luckyraven.gangland.inventory.part.Fill` only — already otherwise on `keystone-inventory`, a
transitional file from an earlier gate)

**Confirmed: none of the 9 shop/admin files appear in this list any more** — the CUT precondition (every remaining
`gangland.inventory.*` consumer confined to gangland-impl/lootchest-api/gadget's one `Fill` import) holds.

## Smoke

M-SHOP-1 is player-only end to end (confirmed in the WS4 G1a fix round — every `/glw shop`/`/glw trader`/
`/glw banker` command needs a real player), so per the coordinator's instruction the harness row is boot + bean
graph only. **Reused `ws4-g1a-shop-boot`** rather than adding a new row (its title updated to note it now also
covers G1b) — same npcshops deploy, same command probes, same `expect` block (already asserted
`at org.luckyraven.gangland.shop`/`at org.luckyraven.keystone.shop` never appear, which would catch a
`NoSuchMethodError`/`ClassCastException` from a bad `InventoryService` wiring just as well as it did for G1a).

Temporarily repointed `scenarios.json`'s `paths.repo_dir`/`paths.keystone_jar_dir` to `gangland-0.10.0`/
`keystone-1.11.0` for the run, restored to `gangland-0.9.2`/`keystone-1.10.0` afterward (same pattern as every
prior smoke run this wave).

**Result: PASS.** `boot=True stop=True modules=['npcshops'] errors=0`. `Loaded module npcshops 0.10.0`,
`Runtime modules: 1 loaded, 0 fault(s)`, **0 ERROR lines in the whole log** — confirming `ShopConfig`'s
`InventoryService`-consuming `shopAdminFlow` bean (and everything downstream: `ShopAdminView`,
`SellCategoryItemsAdminView`, `BarterCategoryItemsAdminView`, `PriceEditorView`, `ShopAdminOpener`) all constructed
without a fault during `GanglandContext.bootstrap()`, which runs before module loading — the module wouldn't have
loaded to 0 faults at all if the core CONFIG-phase bean graph had thrown.

**Not verifiable from console** (unchanged limitation, same as every prior GUI gate this wave): `/glw shop edit`'s
actual chest GUI, drop-to-add, price editing, category creation, save-on-close. All tracked in
`exec/G010/WS2-manual-checklist.md`'s new "WS4 G1b" section (rows 34-36), plus a note that rows 28-31 from the
WS4 G1a section now exercise this rebuilt (not the old inventory-api) implementation.

## Hygiene docs sweep (step 16)

`grep -rl "shop-api" documentation CLAUDE.md README.md` found 9 files; **7 fixed, 2 intentionally left alone**:

| File | Change |
|---|---|
| `CLAUDE.md` (worktree-local, **gitignored** — edits are local-only per the repo's own documented convention, so this file does **not** appear in the package diff below) | Module table: dropped the `gangland-ui/shop-api` row entirely; `gangland-api` row's "re-exports domain/item/core/inventory/sign/shop-api at compile scope" → drops `shop-api`, adds a note that `keystone-shop`/`keystone-inventory` are provided, never re-exported (B2, WS4/WS2) |
| `documentation/module-loader.md` | "pulls … inventory-api, sign-api and shop-api in transitively" → drops `shop-api`; adds a sentence that `keystone-shop`/`keystone-inventory` are never re-exported by `gangland-api`, so a module declares them directly |
| `documentation/features/traders.md` | "the shop-api framework in gangland-ui/shop-api" → "the shop framework, which as of 0.10.0 lives in Keystone's keystone-shop" |
| `documentation/features/bank.md` | "shop transaction totals handled by shop-api" → "...by Keystone's keystone-shop (0.10.0 — moved out of the deleted gangland-ui/shop-api)" |
| `documentation/tests/features/trader-shop.md` | 3 mentions fixed: the intro paragraph, the "Modules involved" line, and the "Regression Risks" bullet — all repointed at `keystone-shop` + `gangland-impl`'s admin views. (Left the pre-existing, unrelated `cops-n-crooks`-for-traders inaccuracy alone — that's a 0.9.0-era staleness, not a shop-api mention, out of this sweep's scope) |
| `documentation/FRONT-PAGE.md` | "Shop API — the UI, persistence, and transaction pipeline live in a shared gangland-ui/shop-api module" → "the persistence and transaction pipeline live in Keystone's keystone-shop (0.10.0), reusable by any Keystone-based plugin" |
| `documentation/FRONT-PAGE.bbcode.txt` | same change, BBCode form |

**Intentionally not touched** (both are historical changelog entries describing what shipped in v0.7.5-DEV, not
living documentation — rewriting a changelog to erase what a past version actually said would be historically
inaccurate, not a fix):
- `documentation/v0.7.5-DEV/CHANGELOG.md`
- `documentation/v0.7.5-DEV/CHANGELOG.bbcode.txt`

Step 17 (`graphify update . --force` in both repos) is explicitly the orchestrator's own job at batch end per the
dispatch ("Do not run graphify (END batch, orchestrator)") — not run by me.

## Docket notes for the clerk

- Nothing new to add — this batch is a pure framework port (peek-and-clone semantics, all button/anvil/pagination
  behavior) with no functional change to any docket-tracked bug's status. CT-04/CT-05/CT-22 (still open, in these
  relocated files, per the WS4 G1a report) are unaffected by this port — their bugs live in the price-edit/
  category-item logic this batch preserved byte-for-byte, not in the click-routing translation.
- Step 18's docket record (relocated file paths, commit refs) is the clerk's job once this batch is committed,
  per the established pattern from every prior batch this wave.

## Concerns / left out

- No new automated tests this batch — justified at length in the §0d section above (zero item-holding slots to
  pin, and the plan's own text for G1b already said "no prior unit tests to pin against"). The one regression risk
  a test *could* have caught — the click-routing translation from a raw `InventoryClickEvent` listener to
  `ItemComponent`'s left/right/any-click handlers plus a raw-listener bridge for the drop path — is covered by the
  build (a wrong API call is a compile error here, not a silent bug) and by manual-checklist row 34, not by a unit
  test; flagging this honestly rather than manufacturing test coverage that wouldn't actually pin anything new.
- `documentation/tests/features/trader-shop.md`'s pre-existing "cops-n-crooks" staleness (traders actually live in
  npc-shops since 0.9.0) was left alone — out of this sweep's named scope (shop-api mentions only).
- Row 35 in the new manual-checklist section is deliberately a "confirm nothing breaks" row rather than a
  meaningful behavior check, since there's genuinely nothing held across a panel switch to lose — kept it anyway
  so a future regression (if someone adds a real drop-zone here later) has a row already waiting for it.

## Deliverables

- `exec/G010/WS4-G1b-report.md` — this file.
- `exec/G010/WS4-G1b-package.diff` — `git add -N . && git diff HEAD` (then `git reset`; worktree left uncommitted,
  clean of intent-to-add). 1675 lines, 16 tracked files (9 rebuilt shop files + `ShopConfig.java` + 6 docs files;
  `CLAUDE.md`'s edits are real but gitignored, so they do not appear in this diff — see the hygiene section above).
- `exec/G010/WS2-manual-checklist.md` — new "WS4 G1b" section (rows 34-36) added after the WS4 G1a section, with a
  note that G1a's rows 28-31 now exercise this rebuilt implementation.
- `brainstorming/bartizan-split-2026-09-08/smoke/scenarios.json` — `ws4-g1a-shop-boot`'s title updated to note it
  now covers G1b too; `paths` were temporarily repointed for the run and restored afterward.

## Amendment (orchestrator, 2026-09-22, from the gate review — Finding 1)
KS-IV-06 exposure, confirmed: `ShopAdminView`/`PriceEditorView` open four `AnvilGUI` detours through `flow.suspend()` (`openAddSellCategoryAnvil`, `openAddBarterCategoryAnvil`, `openPriceAnvil`, `openModeAnvil`). While suspended, `MenuFlow.suppressClose()` returns true (`keystone-inventory/.../flow/MenuFlow.java:330`), so a disconnect inside that window never reaches `end()`: the flow's `FlowCloseListener` leaks and the admin's staged edits never fire `ShopEditedEvent`. Pre-existing Keystone bug (docket KS-IV-06, P1), not introduced by this port; the same exposure exists in G4's banker amount/create/rename prompts and `QuantitySelectorView`. No Gangland-side code change: the fix is a quit handler in Keystone's `MenuFlow` (ruling W46 — KS-IV-05/06/07 land together as Keystone 1.11.1 after the CUT gate, with the Gangland pin bumped in one commit).
