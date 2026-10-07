# CUT fix round 1 report — 2026-09-22

Status: **DONE**. Review verdict FIX (3 Important, no Critical, ruling W49) — all 3 findings fixed, docket wording
corrected, Keystone bumped to 1.11.1, whole reactor rebuilt, smoke re-run. Worktree `E:\Programming\java\wt\gangland-0.10.0`,
branch `0.10.0`, still uncommitted on HEAD `85299070`. No commits made this round either.

## F1 (Important) — loot-chest deposit policy silently widened → fixed, take-only restored

**Finding**: the deleted `inventory-api`'s `InventoryClickHandler` (`LOWEST` priority) cancelled every click/
shift-click deposit onto a registered top inventory unless the slot was in a `draggableSlots` allowlist, and the
old `LootChestSession` only ever marked generated-loot slots draggable — so empty slots were deposit-blocked too.
`SharedLootInventory` (the CUT gate's replacement) shipped with **no gate at all**: every slot accepted
click-place, shift-click-in and drag-in from any player. Loot chests had silently become usable as free shared
storage.

**Fix** (`gangland-ui/lootchest-api`):
- `SharedLootInventory.java` — now `implements org.bukkit.inventory.InventoryHolder`;
  `Bukkit.createInventory(null, size, title)` → `Bukkit.createInventory(this, size, title)` (identity, not title,
  is how a click/drag handler now proves "this top inventory belongs to a loot chest").
- `listener/LootChestListener.java` — `onInventoryClick` gained a holder-identity check
  (`event.getView().getTopInventory().getHolder() != session.getInventory()` → return) right after the existing
  `LOOTING`-state gate, then a new `isDepositAction(InventoryClickEvent)` private helper that cancels and returns
  *before* the existing take-tracking/sync logic (which is otherwise untouched): cancels
  `PLACE_ALL`/`PLACE_ONE`/`PLACE_SOME`/`SWAP_WITH_CURSOR`/`HOTBAR_SWAP`/`HOTBAR_MOVE_AND_READD` when the clicked
  slot is in the top inventory, and cancels `MOVE_TO_OTHER_INVENTORY` only when it originates from the *bottom*
  inventory (a shift-click deposit) — the reverse direction (shift-click OUT of the chest) stays a legitimate take.
  A new `onInventoryDrag(InventoryDragEvent)` handler (same session/state/holder gate) cancels whenever any
  `event.getRawSlots()` entry falls in the top-inventory slot range — dragging can only ever place, never take, so
  this blocks every drag-deposit path with no take-path risk.
- `pom.xml` — added `org.luckyraven:gangland-core` as a `test`-scope `test-jar` dependency (needed for
  `BukkitRegistryFixture`, since the new test's production code path is now reachable through `Material.isAir()`).

**Red-first evidence**: new `gangland-ui/lootchest-api/src/test/java/org/luckyraven/gangland/lootchest/listener/LootChestListenerTest.java`
(9 cases). `InventoryHolder` identity plumbing was landed first (the test cannot compile without it — that's
identity infrastructure, not the guard behavior itself), then run against the still-unguarded
`onInventoryClick`/a no-op `onInventoryDrag` stub:
```
mvn -pl gangland-ui/lootchest-api -am test -Dtest=LootChestListenerTest -Dsurefire.failIfNoSpecifiedTests=false
→ Tests run: 9, Failures: 5, Errors: 0, Skipped: 0
```
Exactly the 5 deposit-should-cancel cases failed:
- `placeAll_ontoTopSlot_isCancelled:106` — `Wanted but not invoked: inventoryClickEvent.setCancelled(true);`
- `swapWithCursor_ontoTopSlot_isCancelled:116` — same shape
- `hotbarSwap_ontoTopSlot_isCancelled:126` — same shape
- `moveToOtherInventory_fromBottomInventory_isCancelled:137` — same shape
- `drag_touchingTopSlot_isCancelled:186` — `Wanted but not invoked: inventoryDragEvent.setCancelled(true)`

The other 4 cases (2 take-not-cancelled, 1 no-active-session, 1 drag-confined-to-bottom-only) correctly passed
even pre-fix, confirming the test doesn't over-cancel. After implementing the real fix:
```
mvn -pl gangland-ui/lootchest-api -am test → BUILD SUCCESS
lootchest-api: Tests run: 38, Failures: 0, Errors: 0, Skipped: 0  (9 new + 29 pre-existing)
gangland-core (built via -am):  Tests run: 43, Failures: 0, Errors: 0, Skipped: 0
```

**Docs corrected** (both wordings the review flagged as now-false "unchanged"):
- `exec/G010/CUT-report.md`'s "Lootchest policy — take/deposit + cursor-on-close" section: the `**Policy**:`
  paragraph rewritten to state take-only, name the old `draggableSlots`-allowlist mechanism, name the exact
  regression (`SharedLootInventory` shipped with zero click guard), list the fix by the exact `InventoryAction`s
  cancelled, and cite `LootChestListenerTest`. The cursor-on-close paragraph was left as-is (still accurate — the
  cursor-return behavior itself never changed).
- `documentation/migration-0.10.0.md`'s `## WS2` → `### 5. Loot chests` section: removed the "take/deposit
  behavior unchanged" claim from the intro; added a paragraph stating the old rule (deposit worked only onto
  generated-loot slots via the deleted framework's allowlist), the new rule (take-only, no deposit slot at all),
  and why (prevents free shared-storage/stash use; a cooldown/respawn regeneration would silently void anything a
  player had deposited).

## F2 (Important) — LS-30/LS-31 needed automated pins → both added

### LS-30 pin

`gangland-impl/src/test/java/org/luckyraven/gangland/lootchest/LootChestWandTest.java`,
`updateWandLore_resolvesTargetFromCapturedWandSlot_notFromCurrentMainHand`.

**Design note (deviation from the brief's suggested approach, forced by environment, not a design choice)**: the
brief suggested driving `setWandNBT`/`updateWandLore` end-to-end and asserting `setItem(eq(wandSlot), any())`. Two
attempts failed for confirmed environmental reasons: letting the real `de.tr7zw.nbtapi.NBT.modify(...)` execute
throws `ExceptionInInitializerError` (needs a live NMS/CraftBukkit server this unit-test JVM doesn't have), and
`Mockito.mockStatic(NBT.class)` also fails (Byte Buddy can't retransform the `NBT` class hierarchy without
`com.mojang.authlib.GameProfile` on the classpath, which isn't and should not become a `gangland-impl` test
dependency). So every NBT-*writing* path in `LootChestWand` is genuinely unreachable in-process — the pin instead
targets `updateWandLore`'s target-*resolution* guard, the exact mechanism the write path sits behind: with the
config-opening slot (`wandSlot=3`) empty and the player's *current* main hand (slot 7) holding a valid wand, the
fixed code must resolve its target from `getItem(wandSlot)` alone and never even call `getItemInMainHand()`.

**Red**: temporarily reverted `LootChestWand.java:394` (`player.getInventory().getItem(wandSlot)` →
`player.getInventory().getItemInMainHand()`), then:
```
mvn -pl gangland-impl -am test -Dtest=LootChestWandTest
→ LootChestWandTest.java:97 — verify(inventory, never()).getItemInMainHand(); → NeverWantedButInvoked
```
Restored `LootChestWand.java` to byte-identical (confirmed via `grep`, see "Postcondition" below).
**Green**: `mvn -pl gangland-impl -am test -Dtest=LootChestWandTest` → `Tests run: 1, Failures: 0, Errors: 0`.

### LS-31 pin

`gangland-impl/src/test/java/org/luckyraven/gangland/listener/loot/LootChestWandListenerTest.java`,
`allowListOfChest_rejectsTrappedChest_exactMatchNotSubstring`.

Chose the full mocked-event-chain approach over extracting a standalone matcher method, since the task's
constraint ("do not modify production code — test-only") ruled out the extraction alternative. Drives the real
`onPlayerInteract` with a mocked `PlayerInteractEvent`/`Player`/`Block`/`PlayerInventory`, `mockStatic(Settings
.class)` returning an allow-list of `["CHEST"]`, and a right-clicked `TRAPPED_CHEST`. Asserts the observable
effect: `LootChestManager.registerChest(...)` must never be reached for a disallowed block.

**Red**: temporarily reverted `LootChestWandListener.java:71` (`.equalsIgnoreCase(allowed)` →
`.toUpperCase().contains(allowed.toUpperCase())`), then:
```
mvn -pl gangland-impl -am test -Dtest=LootChestWandListenerTest
→ LootChestWandListenerTest.java:94 — verify(manager, never()).registerChest(any()); → NeverWantedButInvoked
```
(substring match let `TRAPPED_CHEST` wrongly pass the `["CHEST"]` allow-list and a chest got registered). Restored
`LootChestWandListener.java` to byte-identical.
**Green**: `mvn -pl gangland-impl -am test -Dtest=LootChestWandListenerTest` → `Tests run: 1, Failures: 0, Errors: 0`.

### Postcondition — both production files confirmed unmodified by this round

```
grep -n "getItemInMainHand\|getItem(wandSlot)" gangland-impl/.../lootchest/LootChestWand.java
  → only the correct getItem(wandSlot) call sites, zero getItemInMainHand() reintroduced
grep -n "equalsIgnoreCase\|\.contains(" gangland-impl/.../listener/loot/LootChestWandListener.java
  → only .equalsIgnoreCase(allowed), zero substring .contains( reintroduced
```

### Final module gate for both pins

`mvn -pl gangland-impl -am test` → **BUILD SUCCESS**, `Tests run: 260, Failures: 0, Errors: 0, Skipped: 0` (258
pre-existing + 2 new).

## F3 (Important) — GR-18 docket wording corrected

`exec/G010/CUT-report.md` previously called `GangCommand.gangStat()`/`itemToBalance()`'s deletion an unfiled
"docket candidate." It was already a filed, triaged entry: **GR-18**, "`GangCommand.gangStat` is dead code (~140
lines)" (confirmed via the docket's `bugs.json`). Corrected in three places in `CUT-report.md`: the file #11
table row, the "Docket" table (added a `GR-18 | open → fixed` row alongside T-43/LS-30/LS-31/UI-04/UI-10/T-41),
and the "Concerns" section (removed the "happy to be second-guessed" hedge, since this is a known, already-triaged
entry, not a speculative call this gate made unilaterally). The clerk writes the actual status-database row.

## Settings `Inventory:` block deviation — ruling stands (W49)

No code change requested or made. `CUT-report.md` updated per the ruling: the block's eventual retirement
(migrating `TraderSettingsImpl`/`BankerSettingsImpl`/`TurfModuleConfig`/`GanglandShopUiSettings` off
`Settings.getInventoryFillItem()`/`getInventoryFillName()` onto their own YAML-backed defaults) is now recorded
as a **WS6 G3** (api-facade wave) migration item and a docket candidate, in both the "Deviation" section and a new
row-adjacent note in "Docket."

## Keystone version bump — 1.11.0 → 1.11.1

`pom.xml:70` — `<keystone.version>1.11.0</keystone.version>` → `<keystone.version>1.11.1</keystone.version>`.
Verified every `keystone-*` artifact this reactor depends on is present at `1.11.1` in `~/.m2/repository/org/
luckyraven/` before bumping (`keystone`, `keystone-bean`, `keystone-command`, `keystone-common`,
`keystone-hologram`, `keystone-hooks`, `keystone-inventory`, `keystone-item`, `keystone-module`, `keystone-npc`,
`keystone-persistence`, `keystone-plugin`, `keystone-shop`, `keystone-testkit` — all present). No API removals per
the coordinator's note (KS-IV-05/06/07 fixed: `MenuFlow` quit-during-suspend + ended guards on
`switchTo`/`rerender`/`back`); confirmed no compile break anywhere in the reactor from the bump alone.

## Build (whole reactor, after all of the above)

`mvn clean install` → **BUILD SUCCESS**. Aggregate test count summed fresh from every module's own
`target/surefire-reports/*.txt`: **Tests run: 829, Failures: 0, Errors: 0, Skipped: 0** — exactly **818 + 11**
(818 = the CUT gate's own baseline before this fix round; +9 = `LootChestListenerTest`'s new cases; +2 =
`LootChestWandTest` + `LootChestWandListenerTest`'s one case each), matching the coordinator's expected "818 +
the new tests" precisely. Per-module breakdown for the touched modules: `gangland-impl` 260 (258+2),
`gangland-ui/lootchest-api` 38 (29+9), `gangland-core` 5 (unchanged, rebuilt only via `-am`).

Jar audit re-run clean: `unzip -l target/gangland_warfare-0.10.0.jar | grep -c "keystone/inventory"` → 0,
`"keystone/shop"` → 0, `"gangland/inventory"` → 0. Postcondition grep re-run clean:
`grep -rl "org.luckyraven.gangland.inventory" --include=*.java .` → 0 hits.

## Smoke — re-run against Keystone 1.11.1

`paths.repo_dir`/`paths.keystone_jar_dir` repointed to this worktree / `E:\Programming\java\wt\keystone-1.11.0\
keystone-plugin\target` (which now holds `Keystone-1.11.1.jar` — the directory name is stale, the jar inside it
is current), restored to `gangland-0.9.2`/`keystone-1.10.0` immediately after. Command:
`python smoke.py --rows cut-full-regression --deploy --keystone --restore`.

**Result: PASS.** `boot=True stop=True modules=['civilians','gadget','mail','npcshops','turf','copsncrooks']
errors=0` — **cleaner than the original CUT-gate run** (which had `errors=1`, an unrelated PlaceholderAPI
network-timeout line already on the harness's `no_errors_except` allow-list; this run hit zero network-flake
errors at all). Confirmed via the raw log: `[Keystone] Enabling Keystone v1.11.1`, `[Keystone] Keystone 1.11.1
loaded`, `Runtime modules: 6 loaded, 0 fault(s)`. Report:
`brainstorming/bartizan-split-2026-09-08/smoke/reports/2026-09-22-1857-cut-full-regression.md` (+`.log`,
+summary files) — main checkout, untracked, per LEAD-RULES.

## Subagents used

- **sonnet** — F1 (loot-chest take-only fix + `LootChestListenerTest` + doc corrections). Verified against real
  `InventoryAction` semantics and this repo's own `documentation/TESTING.md` conventions; genuinely red-first as
  detailed above.
- **sonnet** — F2 (LS-30/LS-31 automated pins). Hit two real environment constraints (NBT-API needs a live
  server; `mockStatic` needs a classpath dependency this repo won't add) and adapted the LS-30 pin's exact target
  accordingly rather than forcing the originally-suggested approach — flagged clearly in its own report and
  reproduced above, not silently substituted.

Both ran in parallel (disjoint file sets: `gangland-ui/lootchest-api/*` vs. `gangland-impl/*`).

## Concerns / open questions

- **LS-30's pin covers the target-resolution guard, not every write path** (`setWandNBT` itself is unreachable in
  a unit test without a live NMS server, per F2's investigation above). This is a real, honestly-reported coverage
  gap, not a silent shortfall — the guard being pinned is the exact mechanism LS-30's fix lives behind, so a
  regression that reintroduces `getItemInMainHand()` anywhere in the resolution path would still be caught; a
  regression that broke only the NBT-write call itself (unlikely, since that code didn't change) would not be.
- Everything else from the original `CUT-report.md`'s "Concerns" section still applies unchanged (manual-checklist
  rows 37-44 unchecked, `SearchButtonFactory` still orphaned) — not re-litigated here.

## Deliverables

- `exec/G010/CUT-fix1-report.md` — this file.
- `exec/G010/CUT-fix1-package.diff` — cumulative, `git add -N . && git diff HEAD` (then `git reset`; worktree left
  uncommitted). 5319 lines (up from the original CUT gate's 4680 — the delta is this round's production fix + 3
  new test files + doc corrections + the pom bump).
- `exec/G010/CUT-report.md` — corrected in place (F3 wording, Settings-block deviation note, lootchest policy
  section) rather than superseded; still the primary narrative document for the CUT gate as a whole.
- `documentation/migration-0.10.0.md` — loot-chest take-only paragraph added to the WS2 section (this worktree).
- `brainstorming/bartizan-split-2026-09-08/smoke/reports/2026-09-22-1857-*` — the re-run's report files (main
  checkout, untracked).
