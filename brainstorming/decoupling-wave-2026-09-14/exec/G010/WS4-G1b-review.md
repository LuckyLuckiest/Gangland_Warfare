# Review — Gangland 0.10.0 WS4 G1b (shop admin editor → keystone-inventory) + docs sweep — 2026-09-22 (Sonnet, transcribed)
Verdict: FIX (1 Important, report-evidence only; no code change owed).

## Spec table (condensed)
§0d ✅ verified against Keystone source: `ChestMenu` never overrides `handleDepositClick`/`isInteractive`, so `MenuListener.onClick` cancels every top click on the grid slots before `dispatchClick` (no component there anyway) and `handlePlayerInventoryClick` cancels shift-clicks; the admin's `@EventHandler(priority = HIGH)` raw listeners have no `ignoreCancelled`, run after the cancel and read `getCursor()`/`getCurrentItem()`; nothing consumed — feature works, no loss · chrome fill ✅ `applyBorder` = the 26-cell ring, `ShopAdminView` 28 `INTERIOR_SLOTS` = the true interior; category views never `.slot()` the 36 grid slots and never `.fill()`, so `renderAll` leaves them empty; the 36-53 range loop is correctly scoped · flow lifetime ⚠ F1 · persistence ✅ single idempotent `onEnd`, rebuilds from live session state (a mid-edit close persists what was staged, as before); listener package under the scanned root · `ShopConfig.shopAdminFlow(InventoryService …)` (`GameplayConfig.java:106` produces it), `ShopAdminOpenerImpl` byte-identical ✅ · scope/style ✅ · CUT grep re-run: impl 19, lootchest-api 2, gadget 1 ✅ · docs ✅ (CLAUDE.md clean; only intentional historical phrasing + the 2 changelogs still say shop-api; `gangland-api/pom.xml:79-83` keystone-shop in the provided block, keystone-inventory not a gangland-api dependency).

## Findings
**F1 Important** — the report never analysed the KS-IV-06 exposure the dispatch asked for. Traced: four `AnvilGUI` detours via `flow.suspend()`; `suppressClose()` true while suspended (`MenuFlow.java:330`) → a disconnect there never reaches `end()`, leaks the `FlowCloseListener` and drops the staged `ShopEditedEvent`. Pre-existing Keystone bug, no code change here. Fix: amend the report; cross-reference KS-IV-06's docket note.

## Cannot verify
Build/818 tests, C9 audit, smoke; client-only GUI rows.

## Notes
CT-04/05/22 carried unchanged; no new CT row. KS-IV-06 docket note: "confirmed exposed by Gangland WS4 G1b's 4 anvil sites (+ G4's banker/quantity prompts)".

## Orchestrator rulings (W46)
Report amended by the orchestrator (W27 precedent); G1b committed without a fix round. KS-IV-05, KS-IV-06 and KS-IV-07 are fixed together as Keystone 1.11.1 (own phase note, red-first tests) AFTER the CUT gate, with the Gangland `keystone.version` pin bumped in one 0.10.0 commit — not mid-stream. The KS-IV-06 note update goes into the clerk's CUT-batch.
