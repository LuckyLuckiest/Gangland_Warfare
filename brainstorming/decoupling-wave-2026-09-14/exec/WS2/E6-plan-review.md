# Plan review — WS2 re-target (R10) + Keystone Phase E6 — 2026-09-17 (Opus, transcribed)
Verdict: PASS WITH FIXES (3 Critical, 4 Important, 2 Minor). Direction right; E6.1/E6.2 is a faithful minimal extraction (every named class exists; every "Keystone E6 requirement" already exists in the Oriel 0.8.0 worktree). Blockers are boundary bookkeeping.

## Step table
E6.1 core ⚠ (F1) · MenuHolder/MenuListener identity ✅ (`service()` additive; two-service filter correct) · ChestMenu "~200-line core" ⚠ rewrite not port; base must be non-final with render hooks (F4) · components ⚠ `ItemComponent` is `AnimatedComponent` + `ItemSource`/`LoreSource`/`RequirementHook` (ItemComponent.java:12-19) → new class (F8) · E6.2 ✅ (`MenuFlow.suspend/resume/back/hasBack/rerender/suppressClose` present, MenuFlow.java:129-199; Oriel already has a `PageConfig`, F9) · G1 ✅ · G2 ✅ (`User(plugin,user,placeholder,InventoryRegistry)` User.java:60 → 3 args; UserTest/UserManagerTest/GangMembershipTest) · G3 ❌ under-scoped (F2, F3) · G4 ✅ (`Draggable` per-slot key InventoryParser.java:50,98 → `SlotView.interactive` 1:1; barter/sell are re-points) · G5 ❌ ownership clash (F5) · CUT ✅ census complete: 125 importers = 58 internal + 67 consumers (impl 22 · npc-shops 18 · shop-api 9 · domain 7 · turf 6 · cops 2 · lootchest 2 · gadget 1), satisfiable only after F5

## Findings
**F1 (Critical)** — `ChestMenuClickListener.java:122-123,176,182,188,215` reaches into `DepositSlotComponent` (`hasDeposit`, `accepts`), ~130 lines of placeholder/swap/shift-transfer logic; the three declared `MenuTarget` defaults cover none of it — the class does not compile without `DepositSlotComponent`.
**F2 (Critical)** — "`InventoryParser` and the `handler.*` dialect stay in gangland-impl" is false: `handler.*` (11), `condition.*` (4), `filter.*` (11), `unique/UniqueItemHandler`, `multi.*` (7), `villager.*` (4), `part/{Slot,Fill,ButtonTags,ConditionalSlotResult}`, `State`, `InventoryData`, `InventoryBuilder`, `OpenInventory` live under `gangland-ui/inventory-api/.../inventory/**` (~40 of 58 files); no gate moves them.
**F5 (Critical)** — WS2's consumer list and G5 include `lootchest-api` and the shop views; R2 assigns LootChestService/Session to WS3, PLAN.md:172 assigns the 9 shop admin views to WS4 G1b.
**F3** — `Multi.Item_Source`/`Multi.Per_Page` (InventoryParser.java:72-73) drive `MultiInventory` + Creation/Navigation/ListEntry/StaticSlotEntry on `InventoryHandler`; no G3 step rebuilds them on `PagedRegion`.
**F4** — Oriel's rebase needs extension points in 1.11.0 (today's `ChestMenu` is `public final`, private fields ChestMenu.java:50-77): non-final class, protected accessors (components/inventory/holder/viewer), overridable `renderAll`/`rerenderSlot`/`applyFillIfEmpty`/`ensureTitleFor`/`resolveTitle`, settable title source, extensible `ChestMenuBuilder`, public `MenuHolder.service()`, overridable `MenuListener`, `MenuTarget` defaults — adding later is a breaking change (keystone CLAUDE.md:109).
**F6** — estimates: Keystone 5–6 d; Gangland 7–9 d + 0.5 cutover.
**F9** — two `PageConfig`s (Oriel `chest/internal/PageConfig` + test); diff before choosing.
**F7 (Minor)** — `/glw debug` `User.getInventories()` (DebugCommand.java:466,474) lists several handlers; the tracker is one menu per player.
**F8 (Minor)** — E6 does not say which Oriel `chest/component` classes die (Fill/Border/Line would exist twice).

## Cannot verify
1.16.5 floor clean (`createInventory(holder,size,String)`, `getOpenInventory().getTopInventory()`); deleted Gangland classes use `event.getView().getTopInventory()` (InventoryView became an interface in 1.21 — moving to `event.getInventory()` is a silent win); whether `MenuFlow.switchTo` already switches in place; npc-shops = 18 (grep page limit).

## Notes (exact amendments)
1. `plans/WS2-inventory-keystone.md` new; `plans/WS2-inventory-oriel.md` SUPERSEDED at its head. Carry: B1/R3 cutover, B2 three domain tests, R2. Drop: B4, B5/D5 `Enchanted:`, C7 `unique_items.yml`, S4, D1/D2/D3.
2. PLAN.md: §1 WS2 row (:16), Oriel row (:29), diagram (:44); §2 R4/R5 obsolete → R10, R6 → "never re-exports keystone-inventory" (:57); §5 replaced; §7 WS3 G3 (:151) "GUI on Oriel" → keystone-inventory; §8 WS4 (:172) drop the Oriel `rerender()`/R5 clause; §9 WS5 (:186 "5 Oriel menus", :204 "G3 menus on Oriel") — forgotten by the retarget; §10 (:224); §12 WS2-D1/D2/D3/D5 → dissolved by R10; §13/§14 order.
3. R11 per-file ownership for lootchest-api (2) and shop-api (9).
4. Keystone: fold F1/F4 into E6.1; "Oriel extension points" an explicit E6.1 deliverable.
5. Docket candidates: `InventoryHandler.SPECIAL_INVENTORIES` static map (:30) and `static InventoryRegistry registry` (:41) leak across reloads — record as fixed-by-WS2.

## Orchestrator rulings (W23)
- **R11 (ownership):** `lootchest-api`'s two importers are WS3's (R2); the nine shop admin views are WS4 G1b's; WS2 G5 = turf · cops-n-crooks · gadget only; CUT is a shared gate after WS2 G5 **and** WS3 G3 **and** WS4 G1b (R3 grep unchanged).
- **F1:** one `MenuTarget` default — `default boolean handleDepositClick(InventoryClickEvent event) { return false; }` — the whole deposit branch stays in Oriel's `MenuTarget` implementation; the three finer defaults are dropped.
- **F2:** new gate **G3a** before G3: move the ~40 Gangland-dialect files from `inventory-api` into `gangland-impl` under the package `org.luckyraven.gangland.menu.*` (so R3's grep on `org.luckyraven.gangland.inventory` stays meaningful), fix importers; no behaviour change; one commit.
- **F3:** G3 explicitly rebuilds the `Multi.*` paginated stack on `PagedRegion` (cost +1 d).
- **F4/F8/F9:** E6.1 gains the deliverable "Oriel extension points" (the list in F4); the E6 text names Oriel's `chest/component` Fill/Border/Line as deleted in Oriel's rebase (they become Keystone's); E6.2 diffs Oriel's `PageConfig` against Gangland's and keeps Oriel's shape with Gangland's extra arithmetic folded in.
- **F6:** estimates as corrected (Keystone 5–6 d; Gangland 7–9 d + 0.5).
- **F7:** `/glw debug inv-data` stays, reduced to the tracker's current menu per player.
- Docket: two triage rows (SPECIAL_INVENTORIES static, static registry) recorded as fixed-by-WS2 at the docket step.
- Execution: a Sonnet planner writes the amendments (Keystone `docs/extraction-roadmap.md` E6 section in the wt/keystone-1.11.0 worktree; `plans/WS2-inventory-keystone.md`; PLAN.md edits; SUPERSEDED banner) first; K2 (Keystone 1.11.0 E6.1 → E6.2) starts from the amended text and is reviewed as a Keystone phase.
