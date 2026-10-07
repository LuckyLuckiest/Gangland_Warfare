# Review — Gangland 0.10.0 CUT gate — 2026-09-22 (Sonnet, transcribed)
Verdict: FIX (3 Important, no Critical).

## Spec table (condensed)
Postcondition grep zero ✅ (module deleted, 6 consumer poms + root + api drop the dep) · inventory-api deleted + `<module>` ✅ · anvilgui untouched, still unrelocated in the core jar per T-12; npc-shops' direct compile dep is fine (module jars are unshaded) ✅ · panel-create skill: only ever existed in the main checkout; retargeted to `Panel<S extends FlowState>` ✅ · settings `Inventory:` block ❌ kept — declared, justified (Banker/Trader/Turf/ShopAdminView still call `Settings.getInventoryFillItem/Name()`), needs an orchestrator ruling · `SharedLootInventory` on raw `Bukkit.createInventory`, same shared instance per chest, no InventoryService/MenuListener/tracker involvement ✅ · LS-30/LS-31 code-correct ✅ (evidence gap F2) · Keystone 1.11.0 signatures (ChestMenuBuilder/ItemComponent/FillComponent/PageConfig.forSize/adoptComponentsFrom) ✅ · `InventoryBuilder.DEFAULT_*` literals byte-identical to settings.yml defaults ✅.

## Findings
**F1 Important** — deposit policy silently widened. The deleted `InventoryClickHandler` (LOWEST) cancelled every click and shift-click deposit on a registered top inventory unless the slot was in `draggableSlots`; `LootChestSession` marked only generated-loot slots draggable, so empty slots were deposit-blocked. `SharedLootInventory` has no gate → click-place, shift-click and drag deposit everywhere. Report and migration doc say "unchanged". Fix: ruling below.
**F2 Important** — LS-30/LS-31 have no automated pin (report admits it; manual rows 42-43 only). LS-31 (`contains(allowed.toUpperCase())` → `equalsIgnoreCase`) and LS-30 (`wandSlot` captured once and threaded) are unit-testable with mocks. W48 second clause.
**F3 Important** — GR-18 ("GangCommand.gangStat is dead code, ~140 lines", P3) already exists; the report calls the deletion a "candidate". Needs the fixed row, not a new entry.

## Cannot verify
Reactor 818/0, smoke cut-full-regression PASS, jar audit; manual rows 37-44.

## For the docket
GR-18 fixed row · LS-30/31 fixed only with the pins · T-43/UI-04/UI-10/T-41 evidence consistent · new: loot-chest deposit policy change · `Inventory:` block follow-up (migrate TraderSettingsImpl/BankerSettingsImpl/TurfModuleConfig/GanglandShopUiSettings onto their own YAML defaults).

## Orchestrator rulings (W49)
Loot chests are TAKE-ONLY: the service cancels every inbound action into the top inventory (place/swap/hotbar/shift-in/drag-in), take paths untouched, holder identified by identity; red-first unit test per blocked action; report + migration doc corrected to state old rule → new rule and why (stash prevention; regeneration would void deposits). LS-30/31 get unit pins with genuine reds. GR-18 → fixed row via the clerk. The `Inventory:` block stays live; its retirement is a WS6 G3 migration item + docket candidate. The Keystone pin bump to 1.11.1 rides in this fix round with a reactor rebuild and a smoke re-run against Keystone-1.11.1.jar.
