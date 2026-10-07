# Review — WS7 G4 — 2026-09-17 (Opus, transcribed)
Verdict: FIX (3 Important, 2 Minor)

## Spec table (condensed)
G4.1 migration hook `JetpackService.java:144-157` (guards: not-already-jetpack → `wearable` tag → `FUEL_ID` → known jetpack id), called first in the scheduled runnable (:119), event-driven only ✅ · fuel preserved, `setChestplate` once, FUEL_MAX kept as-is ✅ · no-op for ordinary Bartizan wearables ✅ · G4.2 permission at `activate` :52-55, node = registered node, item not stripped ✅ (I3) · module-owned message via `JetpackMessages` + `messages.yml`, no NPE when missing ✅ (I1) · G4.3 `/glw jetpack give <id> [amount]` = `CarGiveCommand.java:49-97` shape, `@CommandHandler` scanned, console/permission central ✅ (I2) · `commands.json` jetpack/jetpack_give ✅, `jetpack_help` missing (M1) · beans: `jetpackMessages` FILE-phase `GadgetFileConfig.java:60-67`, ordering edges kept ✅ · K1/K2 ✅ · tests red-first ✅ (M2) · smoke rows authored not run (W17) ⚠ · docket none new ✅

## Findings
**Important 1** — `GadgetFileConfig.java:62` registers `FileHandler(plugin, "messages", "", ".yml", …)` = `plugins/Gangland_Warfare/messages.yml` at the data-folder root. Keystone's `FileManager` resolves by name only (`FileManager.java:79-85`) and `addFile` de-dupes on name+dir+type (:36-39): a second module's `messages.yml` would be silently dropped and its `getFile("messages")` would return gadget's handler. Every other module YAML is namespaced; the root is reserved for `gangland-impl`. Fix: name `gadget_messages`, resource `gadget/gadget_messages.yml` (`GadgetFileConfig.java:62`, `JetpackMessages.java:26,30`).
**Important 2** — `JetpackGiveCommand.java:100-103`: `slots = ceil(amount / maxStackSize)` with `maxStackSize == 1` → `/glw jetpack give jetpack -1` → `new ItemStack[-1]` → `NegativeArraySizeException`; huge amounts allocate huge arrays. Copied from `CarGiveCommand.java:106-109` (same hole). Fix: `amount <= 0` → `MUST_BE_NUMBERS` + return; cap at 36 × maxStackSize.
**Important 3** — the denial message fires from every `scheduleChestplateCheck` caller (join `JetpackActivateListener.java:22`, dismount/undown `JetpackSessionLifecycleListener.java:40,45`, right-click holding any chestplate `JetpackEquipListener.java:64`). Fix: silent `return` in `activate`, message only from the two `JetpackEquipListener` paths (Car precedent `CarInteractListener.java:59`).
**Minor 1** — `commands.json:22-29` lacks `jetpack_help` (`/glw jetpack help` is auto-registered, `BrigadierTabRegistrar.java:168`).
**Minor 2** — `JetpackLegacyMigrationTest.java:96-119` asserts only the positive case; add `verify(inventory, never()).setChestplate(any())` for a non-jetpack Bartizan wearable and an already-migrated item.

## Cannot verify
Smoke rows (not run); real NBT round-trip (tests use `RecordingNbtAccessor`); migrated items keep Bartizan's stamped `fuel_max` (3600) even if `Max_Fuel:` changes — a line for the migration note.

## Notes
- Docket candidate: `CarGiveCommand.java:106-109` negative/huge amount (same root as I2) → triage row covering both commands.
- Plan defect: §2's file table attributes the hook + permission to `JetpackEquipListener`; §4/§6 put them in `JetpackService` (implementation right; amend §2 at G6).
- Cross-gate: I1 sets the module-owned-messages file-naming precedent for the other five modules.
- GD-07 untouched, as §11 predicted.

## Orchestrator rulings (W22)
Fix I1–I3, M1, M2. Precedent: a module's own messages file is `<module>/<module>_messages.yml` with FileManager name `<module>_messages` (recorded for the module-owned messages migration). I2: apply the same one-line guard to `CarGiveCommand` in this round (same module, identical shape) and file one triage row covering both (found in 0.9.2 G4 review, fixed in 0.9.2). Migration note (G6) gains the `fuel_max` sentence.
