# Review — 0.10.0 WS2 G1+G2 — 2026-09-20 (Opus, transcribed)
Verdict: FIX (2 Important, 2 Minor)

## Spec table (condensed)
root dependencyManagement + 6 consumer poms provided (impl, shop-api, npc-shops, turf, cops, gadget; api excluded R6, lootchest excluded R11) ✅ · inventory-api alive everywhere but gangland-domain (R3) ✅ · plugin.yml unchanged ✅ · InventoryService: one instance, CONFIG phase `GameplayConfig.java:106-112`, `registerListeners` once (:109), BeanLifecycle sweep automatic ✅ · User 3-arg ctor, UserFactory + call sites ✅ · gangland-domain drops inventory-api (its only UI dep) ✅ · adapters moved, still wired via `GangFilterRegistration.java:37,42` ✅ · InventoryRuntimeContext re-pointed to the OLD registry (superset) ⚠ (F1) · RemoveAccountListener only clears (parity with before; Keystone's onQuit returns items once G3 opens through the service) ⚠ note · inv-data reads `tracker().currentMenuOf` live ✅ · PlayerInventoryCleanup/registry/SPECIAL_INVENTORIES/MultiInventory.ID still compile and are wired ✅ · GangItemSourceProvider untouched — lead right, plan residue ✅ · three domain tests red-first ✅ · §7 adapter tests ❌ (F2) · smoke S1 PASS with one registration line ✅

## Findings
**F1 Important** — `InventoryRuntimeContext.java:225-227` filters `inventoryRegistry.getInventories(uuid)`, a superset of the deleted per-user set (`InventoryHandler` self-registers every owner-bound handler on construction/open, `InventoryHandler.java:82-84,201-203`, incl. sign views, wand views, PaperworkView/HandcuffBribeView, CarSignViewProvider, MultiPanelInventory panels): a foreign handler whose title key equals a YAML menu name is reopened instead of built, and `findFirst()` over a ConcurrentHashMap key set is nondeterministic on duplicate keys. Fix: a private `Map<UUID, Map<String, InventoryHandler>>` in `InventoryRuntimeContext` for the lookup/register pair (what `User.inventories` was); registry calls unchanged for the other listeners. Dies at G3.
**F2 Important** — plan §7's `GangFilterAdapterTest`/`MemberFilterAdapterTest` (+ `GangItemSourceProviderTest`) missing; nothing pins the moved classes' projections.
**M3** — moved files written LF into a CRLF tree (autocrlf normalises on commit). **M4** — `DebugCommand.java:481-489` console branch messages tracked players instead of the sender (pre-existing).

## Cannot verify
`/glw filter gangs search` (needs a player + gang); `/glw reload` double-registration only from `BeanFactory.java:333-353` reading.

## Notes (plan defects for §0d)
(a) §4 G2's `GangItemSourceProvider` sentence is pre-R10 residue and the class already lives in gangland-impl; (b) "adapters drop their `inventory.filter.*` imports at G2" is impossible before G3a — the move's real reason is the domain pom; (c) the plan never names the impl-side callers of `User.getInventory/addInventory/clearInventories/getInventories` that need an interim substitute; (d) §2/§3 never name the config class/phase for `InventoryService` (`GameplayConfig`, CONFIG, one instance).

## Orchestrator rulings (W38)
F1 fixed now (parity until G3 throws the shim away; test: a foreign handler with a colliding key is not reopened). F2 → G3a (the classes move again there; write the two projection tests then). M3 handled by git; M4 → docket candidate (pre-existing). Report gains the RemoveAccountListener parity sentence. Plan §0d appended by the orchestrator. Then: commit, merge 0.9.2 (WS8) into 0.10.0, dispatch G3a+G3.
