# Review — 0.10.0 WS2 G3 — 2026-09-21 (Opus, transcribed)
Verdict: FIX (6 Important, 4 Minor)

## Spec table (condensed)
Parser/DefinitionStore/RuntimeContext/ConditionalSlotParser onto ChestMenuBuilder ✅ · 9 shipped YAMLs byte-unchanged, 9/9 parse+build ✅ (decoration drift F2) · handler/condition dispatch on `slot()`/`ClickHandler`, ClickKind LEFT/SHIFT_LEFT chain ✅ · `menu.filter` still imports InventoryHandler via `SearchButtonFactory.java:6` (dead bean, UI-34) ⚠ CUT blocker · Multi.* on PageConfig/PagedRegion — arithmetic re-derived identical ✅ · nav slots size-1/-5/-9 ✅ YAML path / ❌ SimplePagedMenu (F1) · GangItemSourceProvider page-agnostic ✅ · legacy views (FilterCommand, GangColorCommand, ViewInventoryAspect, LootChestWand*, ReloadPlugin) still on InventoryHandler — before CUT ⚠ · inv-data ✅ · round-trip tests vs SHIPPED files ✅ · dedicated multi-page arithmetic test ❌ (F4) · reactor 21/21, 906 tests ✅ (G3a's 1800 was a double count — 856 test methods reactor-wide; impl 245→254; no tests lost) · smoke ❌ console only (F6)

## Findings
**F1** `menu/SimplePagedMenu.java:59,63,74` — the four ported views lose the configured `ButtonTags` head textures (settings.yml `Multi_Inventory` next/previous/home) and the Home button (old `size-5` on pages > 0). Fix: `ButtonTags` param, reuse `InventoryBuilder.headItem`, add the home slot.
**F2** `menu/InventoryBuilder.java:149-152` — paged path applies `isBorder() ? border : isFill() ? fill`; the old paged path always drew the border and never filled → `phone_gang_search.yml` (Fill true, Border false) flips to a fully filled grid. Fix: unconditional border on the paged path.
**F3** `menu/InventoryBuilder.java:85` binds only `ClickKind.LEFT`; the old `InventoryClickHandler.java:48-62` fell through to the left action for every click type; no shipped YAML declares `OnRightClick` → every core button stops answering right/middle/drop/number-key clicks. Fix: `onAnyClick(left)` when no right handler, else left+right.
**F4** `InventoryParserRoundTripTest` paginated cases run with 0/0/5 entries (pageCount 1 always); F1/F2 would have been caught by one `perPage+1` case asserting page count, last-page remainder slots, nav buttons per page.
**F5** no red-before-green run for the nine new tests (a wrong-value probe was available, as at G3a).
**F6** smoke: plan §4 G3 names an interactive row; only console S1 ran (inv-data "none", no menu opened).
**M7** nav buttons lost the `&7(n/total)` lore and the click sound (use Keystone `SoundEffect`, not raw XSound). **M8** `resolveItemStack` reads `head` only, old `processItemStack` also honoured `data` (latent). **M9** `InventoryBuilder.java:137` no bounds check on a `Static_Items` slot key (old code skipped out-of-range/occupied). **M10** `SignManager.java:160` fully-qualified `InventoryService` inline.

## Cannot verify
Client-only: nav rendering, adoptComponentsFrom swap, the anvil branch (`phone_banking.yml:48-54`).

## Notes
- `MenuOpener` over `MenuRegistry`: correct (a `Supplier<Menu>` cannot see the player); `MenuRegistry` stays empty for Gangland → `ClickContext.openMenu(name)` inert; note in CUT docs.
- Reopen semantics acceptable (`ChestMenu.open` tracks before `openInventory`, so the outgoing close fails the identity check); a trap for any future core menu with an `onClose`.
- Item safety: zero `Draggable` hits in the nine YAMLs; `interactive(` gated on `draggable()` never fires.
- Deleted tests pinned the shim only; `RemoveAccountListener` back to pre-G2 shape.
- Fill (W41) import sites listed; CUT/R3 blockers: `SearchButtonFactory`, `InventoryRuntimeContext.java:170` (`InventoryHandler.factorOfNine`), `ViewInventoryAspect`, `VillagerDebugPanel`, `GangColorCommand`, `LootChestWand*` ×3, `ReloadPlugin`, `KernelConfig`.
- Docket candidates: `Multi.Per_Page` parsed but consumed by nothing (pre-existing dead key); positive: YAML `Permission:` now enforced by `InventoryOpenByCommandListener`.

## Orchestrator rulings (W42)
Fix F1–F5, M7 (lore + `SoundEffect`), M8, M9, M10 in one round. F6: the harness cannot drive a player — the G3 interactive smoke (`/glw phone`, gang info, bank, bounty, gang search+filter, inv-data showing the current menu) goes on a client checklist `exec/G010/WS2-manual-checklist.md` (started now, extended by G4/G5); console S1 + inv-data stands as the boot proof. The CUT/R3 blocker list is carried into the G5/CUT dispatch. `Multi.Per_Page` dead key → docket batch 3.
