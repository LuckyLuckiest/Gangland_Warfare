# CUT report — 2026-09-22

Status: **DONE_WITH_CONCERNS** (one real, load-bearing deviation from the plan's §4/§5 text — see "Deviations" — everything
else green: full reactor build, 818 tests/0 failures, zero `org.luckyraven.gangland.inventory` references anywhere,
clean jar audit, full-regression smoke PASS)

Plan refs: `plans/WS2-inventory-keystone.md` §2 "At the shared CUT gate", §4 "CUT", §0d/§0e; `plans/WS3-lootchest-hologram.md`
§4 G3 steps 13-14; `exec/G010/BRIEF.md` batch 7; reports `WS2-G3-report.md`, `WS2-G4-report.md`, `WS4-G1b-report.md`.
Worktree `E:\Programming\java\wt\gangland-0.10.0`, branch `0.10.0`, started at HEAD `85299070` (WS4 G1b, committed).
No commits made this batch.

## Reconciliation (before starting)

A previous lead was stopped mid-edit. `git status` at start showed `menu/InventoryBuilder.java` and
`menu/SimplePagedMenu.java` modified: a partial, torn attempt to decompose the old `Fill` record (2-field
`(name, material)`) into two plain `String fillMaterial, String fillName` parameters across `createMenu`/
`createPagedMenu`/`SimplePagedMenu.open`'s signatures, with the `Fill` import already removed but every caller
still passing a `Fill` object — would not have compiled. Diffed both files against HEAD: the in-progress design
(decompose `Fill` into two Strings at every call site rather than keep a replacement record type) matched exactly
what the CUT scope calls for ("`part/Fill` is NOT moved... goes to `ChestMenuBuilder.fill()/border()/line()` +
`FillComponent`"), so **kept** both files' partial edits and finished the job by updating every caller (below)
instead of reverting.

## What changed — the 22 CUT-scope files

Exact grep re-run at start (`grep -rl "org\.luckyraven\.gangland\.inventory\." --include=*.java . | grep -v
"gangland-ui/inventory-api/"`) matched **20** files, not 22 — `menu/InventoryBuilder.java` and
`menu/SimplePagedMenu.java` had already dropped their `Fill` import in the torn partial edit, so they no longer
matched the grep even though they were mid-migration. Both are included below; the true file-by-file count for
this gate is 22, matching the brief.

| # | File | Treatment |
|---|---|---|
| 1 | `gangland-impl/menu/InventoryBuilder.java` | Finished the previous lead's `Fill`→2-String decomposition. Added `public static final String DEFAULT_FILL_ITEM/DEFAULT_FILL_NAME/DEFAULT_LINE_ITEM/DEFAULT_LINE_NAME` (literal copies of the `settings.yml` block's shipped defaults), `public static int factorOfNine(int)` (ports `InventoryHandler.factorOfNine`), widened `headItem` to `public` (reused outside the package by the lootchest wand rewrite). |
| 2 | `gangland-impl/menu/SimplePagedMenu.java` | Already finished by the previous lead; no further change needed. |
| 3 | `gangland-impl/menu/part/ButtonTags.java` | Added `public static final ButtonTags DEFAULT` — the 3 base64 head textures copied verbatim from `settings.yml`'s `Multi_Inventory` block. |
| 4 | `gangland-impl/menu/InventoryData.java` | T-43: removed the dead `perPage` field (see Docket). |
| 5 | `gangland-impl/file/configuration/inventory/InventoryParser.java` | T-43: stopped parsing `Multi.Per_Page` / calling `setPerPage`. |
| 6 | `gangland-impl/file/configuration/inventory/InventoryRuntimeContext.java` | Dropped `Fill`/`InventoryHandler` imports; `InventoryHandler.factorOfNine(size)` → `InventoryBuilder.factorOfNine(size)`; both `createMenu`/`createPagedMenu` call sites now pass `InventoryBuilder.DEFAULT_*` constants + `ButtonTags.DEFAULT` instead of building `Fill`/`ButtonTags` from `Settings.getInventoryFillItem()` etc. |
| 7 | `gangland-impl/listener/inventory/InventoryOpenByCommandListener.java` | Same `Fill`-removal pattern as #6, one `createMenu` call site. |
| 8 | `gangland-impl/command/sub/debug/DebugCommand.java` | (a) `getMultiInv()`: same `Fill`-removal pattern for its `SimplePagedMenu.open` call. (b) `getVillagerTest()`: ported `MultiPanelInventory<VillagerDebugPanel.Session>` → `MenuFlow.builder(inventoryService, getPlugin(), player, new VillagerDebugPanel.Session()).panel("main", panel).build(); flow.openAt("main")`. (c) Deleted `getSpecialInventories()` (`/glw debug inv-data special`) entirely — it listed `InventoryHandler.SPECIAL_INVENTORIES`, a static registry that no longer exists (docket UI-10, see below) and has no `keystone-inventory` equivalent; deleted its wiring + the now-unused `NamespacedKey` import. |
| 9 | `gangland-impl/command/sub/debug/VillagerDebugPanel.java` | Full port: `implements Panel<Session>` (old `org.luckyraven.gangland.inventory.flow.Panel<Session extends FlowSession>`) → Keystone's `Panel<Session extends FlowState>`; `size(session)`→`rows(session)` (27→3); `render(host, InventoryHandler, viewer, session)` → `render(MenuFlow<Session>, ChestMenuBuilder, Session)`; the emerald button's click handler `(player,inv,item)->openAction.accept(player)` → `ItemComponent.of(button).onAnyClick(ctx -> openAction.accept(ctx.player()))`; fill via `FillComponent` + `InventoryBuilder.DEFAULT_FILL_ITEM/NAME` (through `XMaterial.matchXMaterial`, not raw `Material.valueOf`, per house rule). |
| 10 | `gangland-impl/command/sub/gang/GangColorCommand.java` | Full rewrite off `InventoryHandler`(2 nested GUIs: color picker + confirm dialog) onto `ChestMenu.builder(inventoryService)`/`ChestMenuBuilder`/`ItemComponent`/`BorderComponent`/`FillComponent`. `InventoryUtil.aroundSlot`'s 8-slot ring (a 3×3 block minus the center) ported as a direct loop over the 8 explicit ring slots — no occupied-slot skip needed since a fresh `ChestMenuBuilder` has no stale prior-render state (same simplification WS2 G4 already established for `NegotiationView`'s own `aroundSlot` port). Gained an `InventoryService inventoryService` constructor param, threaded from `GangCommand`. Preserved a pre-existing quirk verbatim (not fixed, not in the LS-30/31 carve-out): the confirm dialog's border used `new Fill(Settings.getInventoryFillName(), Settings.getInventoryLineName())` — pairing the fill-name getter with the *line*-name getter (not a material id) in the material slot; both default to `" "` so the visible outcome (fallback to `BLACK_STAINED_GLASS_PANE`) is unchanged either way. |
| 11 | `gangland-impl/command/sub/gang/GangCommand.java` | Removed the `Fill`/`ButtonTags` construction at both `SimplePagedMenu.open` call sites (member list, ally list), replaced with `InventoryBuilder.DEFAULT_*`/`ButtonTags.DEFAULT`. **Found and deleted dead code**: `gangStat(User, UserManager, GangManager)` and its sole caller-free helper `itemToBalance(Gang)` — a private, ~140-line method building an entire gang-info `InventoryHandler` GUI with zero callers anywhere in the reactor or its tests (grepped before touching it). `/glw gang info` is actually served by the YAML-driven `gang_info.yml` menu through the generic dialect — this method was orphaned, pre-dating some earlier YAML migration, and never removed. Deleting it (rather than porting ~140 lines of unreachable code) also removed the need for `Material`/`ItemStack`/`Bukkit`/`Member`/`Rank`/`GangAlliance`/`ItemBuilder`/`XMaterial`/`ColorUtil`/`MaterialType`/`Currency`/`ArgumentUtil`/`GanglandApi`/`Settings`/`Gang`/`BigDecimal` imports, all now unused. **Docket GR-18** — already an existing, filed entry ("`GangCommand.gangStat` is dead code (~140 lines)"); fixed by this deletion. See "Docket" below. |
| 12 | `gangland-impl/command/sub/lootchest/LootChestWandEditCommand.java` | (subagent A) Dropped `Fill`/`Settings` imports; `wand.openConfigInventory(player, fill)` → `wand.openConfigInventory(player, "BLACK_STAINED_GLASS_PANE", " ")` (two String literals matching the deleted `Settings` getters' defaults). |
| 13 | `gangland-impl/config/KernelConfig.java` | Deleted the `@Bean InventoryRegistry inventoryRegistry()` method (called `InventoryHandler.setRegistry(...)`) and its javadoc describing the "old inventory-api framework" tracker — the legacy seam KernelConfig's own comment said would stay "until WS2 CUT retargets everything onto keystone-inventory's OpenMenuTracker." Removed the now-unused `InventoryHandler`/`InventoryRegistry` imports. |
| 14 | `gangland-impl/listener/loot/LootChestWandListener.java` | (subagent A) Dropped `Fill` import; both `openConfigInventory` call sites (left-click open, not-configured-yet auto-open) updated to the 2-string form. Also fixed **LS-31** (see Docket). |
| 15 | `gangland-impl/listener/player/RemoveAccountListener.java` | Removed the `InventoryRegistry inventoryRegistry` field/constructor param and its `inventoryRegistry.clear(user.getUuid())` call in `onPlayerQuit` — dead now that every menu opens through `keystone-inventory`'s `InventoryService`, whose own `MenuListener.onQuit` already returns held items and untracks the player. Docket UI-04 (see below). |
| 16 | `gangland-impl/lootchest/LootChestManager.java` | (subagent A) Added an `@Getter InventoryService inventoryService` field/constructor param — `LootChestWand` pulls it from here rather than gaining its own new constructor param (smaller diff; `LootChestWandCommand`'s existing `new LootChestWand(getPlugin(), lootChestManager, prefix)` call needed no change). |
| 17 | `gangland-impl/lootchest/LootChestWand.java` | (subagent A) Full 540-line rewrite off `InventoryHandler`/`Fill`/`InventoryUtil` onto `ChestMenuBuilder`/`ItemComponent`/`FillComponent`/`BorderComponent`/`PageConfig`/`PagedRegion`; `openConfigInventory(Player, Fill)` → `openConfigInventory(Player, String fillMaterial, String fillName)`; the old hand-rolled `PREVIEW_INTERIOR_SLOTS` index math replaced by `PageConfig.forSize(6, 1, 4, 1, 7, entries.size())` + `PagedRegion.render`; prev/next rebuild the target page and swap in place via `ChestMenu#adoptComponentsFrom`. Fixed **LS-30** (see Docket). |
| 18 | `gangland-impl/menu/filter/SearchButtonFactory.java` | `sortClick`/`clearClick`/`cycleEnumClick` returned `TriConsumer<Player, InventoryHandler, ItemBuilder>` — the pre-G3-era click-handler shape — but grepped and confirmed **zero real callers anywhere in the reactor** (only a comment reference in `FilterCommand.java` and the bean registration in `GameplayConfig.java`); genuinely orphaned/never-wired code from before G3 retargeted the dialect onto Keystone's `ClickHandler`. Retargeted the return type to `org.luckyraven.keystone.inventory.click.ClickHandler` (`ctx -> {...}`, `ctx.player()` replacing the unused `inv`/`b` params — the same "params never dereferenced" finding G3/G4 made elsewhere) so the file at least matches the current dialect era, even though nothing calls it yet. |
| 19 | `gangland-impl/sign/SignManager.java` | Threaded `container.getInstance(InventoryService.class)` into `new ViewSign(gangland, contributions, viewType, ...)` — the same `DependencyContainer container` field already used for `BountySign`'s `InventoryService` at G3. |
| 20 | `gangland-impl/sign/aspect/BountyAspect.java` | Removed `Fill`/`ButtonTags` construction from `Settings` getters, replaced with `InventoryBuilder.DEFAULT_*`/`ButtonTags.DEFAULT` at its one `SimplePagedMenu.open` call. |
| 21 | `gangland-impl/sign/aspect/ViewInventoryAspect.java` | Full rewrite: `new InventoryHandler(plugin, title, 9, player)` + `InventoryUtil.fillInventory` → `ChestMenu.builder(inventoryService).title(title).rows(1)` + `ItemComponent`/`FillComponent`. Gained an `InventoryService inventoryService` constructor field (Lombok `@RequiredArgsConstructor` picks it up); threaded from `ViewSign` (see #19-adjacent change to `ViewSign.java`, listed separately below since it's a 3rd file touched for this same wiring chain, not originally in the 22-file grep because it never itself imported `org.luckyraven.gangland.inventory.*`). |
| 22 | `gangland-impl/sign/type/ViewSign.java` | *(not in the original 22-file grep — needed as a wiring pass-through for #21)* Gained an `InventoryService inventoryService` field (`@RequiredArgsConstructor`), passed to `new ViewInventoryAspect(gangland, contributions, inventoryService)`. |
| — | `gangland-impl/test/.../InventoryParserRoundTripTest.java` | Removed all 6 fully-qualified `new org.luckyraven.gangland.inventory.part.Fill(...)` constructions; every `createMenu`/`createPagedMenu` call site updated to pass `InventoryBuilder.DEFAULT_*` constants directly. |
| — | `gangland-impl/test/.../SimplePagedMenuTest.java` | Same pattern, one call site. |
| 23 | `gangland-ui/lootchest-api/lootchest/LootChestService.java` | (subagent B) `Map<UUID, InventoryHandler> sharedChestInventories` → `Map<UUID, SharedLootInventory>`; dropped the `NamespacedKey key = new NamespacedKey(...)` line entirely (built only to feed `InventoryHandler`'s ctor, never read again — the map is keyed by chest UUID, not the `NamespacedKey`); `new InventoryHandler(title, size, key, owner)` → `new SharedLootInventory(title, size)`. Hologram-related code (`HologramService` import, `hologramService.clear()`) left completely untouched, per instruction (a different workstream owns it). |
| 24 | `gangland-ui/lootchest-api/lootchest/data/LootChestSession.java` | (subagent B) `InventoryHandler inventory` field/ctor param → `SharedLootInventory`; two `inventory.setItem(slot, item.clone(), true)` calls → `inventory.setItem(slot, item.clone())` (dropped the unused `draggable` boolean — nothing in the loot-chest path ever reads `InventoryHandler`'s internal draggable-slot bookkeeping; clicks go through `LootChestListener`'s raw `InventoryClickEvent` handler instead). |
| — | `gangland-ui/lootchest-api/lootchest/SharedLootInventory.java` (new) | (subagent B) ~41 lines: wraps `Bukkit.createInventory(null, size, ChatUtil.color(title))`. Kept the factor-of-9/cap-54 size normalization as a defensive guard (an admin-set NBT value isn't otherwise validated to be a multiple of 9). No click-handler maps, `NamespacedKey`, owner UUID, or registry integration — verified by grep that nothing in `lootchest-api` reads any of that. |
| — | `gangland-ui/lootchest-api/listener/LootChestListener.java` | (subagent B, verified, **not** touched) Zero `InventoryHandler` references before or after — already dispatches purely via raw `InventoryClickEvent`/`InventoryCloseEvent`/`PlayerQuitEvent` and only calls `session.getInventory().getSize()`, which resolves fine against `SharedLootInventory.getSize()`. |
| — | `gangland-impl/command/sub/lootchest/{LootChestWandCommand,LootChestRemoveCommand}.java` | (subagent A, verified, **not** touched) Grepped for `Fill`/`InventoryHandler`/`InventoryUtil` — zero hits in either. |
| 25 | `gangland-features/gangland-gadget/.../sign/CarSignViewProvider.java` | Dropped the `Fill`/`Settings` imports. Gadget cannot depend on `gangland-impl` (module-boundary rule) so it does **not** use `InventoryBuilder.DEFAULT_*` — instead carries its own literal `"BLACK_STAINED_GLASS_PANE"`/`" "` string constants inline at the one `FillComponent` call site, with a comment cross-referencing where the impl-side equivalents live. |

## Deletions

- **`gangland-ui/inventory-api/` — whole module deleted** (pom + 13 remaining files: `InventoryHandler.java`,
  `InventoryOpener.java`, `flow/{FlowSession,MultiPanelInventory,Panel}.java`,
  `listener/{InventoryClickHandler,InventoryCloseHandler,InventoryDragHandler,PlayerInventoryCleanup}.java`,
  `part/{Fill,PageConfig}.java`, `service/InventoryRegistry.java`, `util/InventoryUtil.java`,
  `module.properties`). Of the original 58, 46 moved to `menu.*` at G3a and 12 were deleted 1:1-replaced at
  G3/G4/G5 per the plan's §2 table — this gate deletes the 13th-through-58th-accounting leftover set: the 12
  planned deletions **plus `part/Fill.java`**, which the plan explicitly said would *not* move to `menu.part` and
  stayed behind as the last real consumer-blocking file (confirmed: G3/G3a/G4's own reports all flagged "`Fill.java`
  still not moved" as an open concern for whoever ran CUT — that's this gate).
- **`<module>inventory-api</module>`** removed from `gangland-ui/pom.xml`.
- **Every pom dependency on `inventory-api`** removed: root `pom.xml` (`dependencyManagement`), `gangland-api/pom.xml`,
  `gangland-impl/pom.xml`, `gangland-features/{cops-n-crooks,gangland-npc-shops,gangland-turf}/pom.xml`,
  `gangland-ui/lootchest-api/pom.xml`.
- **`net.wesjd:anvilgui` was NOT removed** (per plan — Gangland keeps its own `AnvilGUI`-driven gang/user-stat
  search locally). **Found and fixed a real transitive-dependency break**: `inventory-api`'s own pom declared
  `anvilgui` at `compile` scope (not `provided`), so `gangland-npc-shops` — which uses `AnvilGUI` directly in 6
  files (`BankerAmountView`, `BankerCreateAccountView`, `BankerRenameAccountView`, `BankerEditNameCommand`,
  `TraderEditNameCommand`, `QuantitySelectorView`) — was getting it **transitively** through its `inventory-api`
  dependency, not a direct declaration. Deleting `inventory-api` broke `gangland-npc-shops`'s compile
  (`package net.wesjd.anvilgui does not exist`). Fixed by adding a direct `net.wesjd:anvilgui` dependency to
  `gangland-features/gangland-npc-shops/pom.xml` (version already resolved from the root `dependencyManagement`,
  matching `gangland-impl`'s own direct declaration). Verified via `grep -rl "net.wesjd.anvilgui" --include=*.java .`
  that no other module needed the same fix (only `gangland-impl` and `gangland-npc-shops` use it anywhere in the
  reactor).

## Deviation from the plan — `settings.yml`'s `Inventory:` block was **NOT** deleted

The plan's §4/§5 text says the block and its `Settings` getters are "deleted together at the CUT gate." Before
deleting, grepped every `Settings.getInventoryFillItem/FillName/LineItem/LineName/NextPage/PreviousPage/HomePage()`
call site across the **whole reactor**, not just the 22-file CUT scope, and found it still genuinely load-bearing
for many already-shipped files from G4/G5/WS4 that **never imported `org.luckyraven.gangland.inventory.*` in the
first place** (so they were never going to show up in the CUT precondition grep):

- `gangland-features/gangland-npc-shops/.../integration/{BankerSettingsImpl,TraderSettingsImpl}.java` — both
  delegate their own `*Settings` interface's `getInventoryFillItem()/getInventoryFillName()` methods straight
  through to core `Settings.getInventoryFillItem()/getInventoryFillName()`.
- Every Banker/Trader view (`BankerClaimView`, `BankerCreateAccountView`, `BankerMenuView`, `BankerUpgradeView`,
  `BarterView`, `ModeSelectView`, `NegotiationView`, `SellView`, `ShopView`) calls `settings.getInventoryFillItem()`/
  `.getInventoryFillName()` on that delegating interface to build their `FillComponent`/`BorderComponent`.
- `gangland-features/gangland-turf/.../TurfModuleConfig.java` — 3 call sites, `Settings.getInventoryFillItem()`/
  `Settings.getInventoryFillName()` directly.
- `gangland-impl/.../file/configuration/shop/GanglandShopUiSettings.java` (implements `ShopUiSettings`) and its
  consumer `gangland-impl/.../shop/admin/view/ShopAdminView.java` — same delegation pattern.

Deleting the block and the `Settings` getters would have broken compilation in all of the above — well outside
this gate's 22-file boundary, and none of it was reviewed or scoped by this gate's brief. **Kept the block and
every `Settings` getter unchanged.** The 22 CUT-scope files this gate touched no longer read them (they use
`InventoryBuilder.DEFAULT_*`/`ButtonTags.DEFAULT`/gadget's own literals instead, all copies of the same shipped
default values, so no visible behavior changes for anyone), but the block stays live for every other consumer.
Documented in `documentation/developer/ui-framework.md`, `documentation/migration-0.10.0.md`'s new WS2 section, and
inline in `settings.yml` itself. **Ruling (fix round 1, W49): the deviation stands.** The block's retirement —
migrating the remaining `Settings.getInventoryFillItem()`-reading module views onto their own local YAML-backed
defaults (matching the pattern `TraderSettingsImpl`/`BankerSettingsImpl` already use for their *other* settings) —
is recorded as a **WS6 G3** (api-facade wave) migration item and a docket candidate; not scoped or attempted here.
See "Docket" below.

## T-43 — dead `Multi.Per_Page` key (docket, fixed)

Confirmed via `grep -rn "getPerPage\|setPerPage\|\.perPage"` that `InventoryData.perPage` had exactly one writer
(`InventoryParser.configureMultiInventory`) and zero readers anywhere in the reactor, before or after WS2 G3's
`PagedRegion` rebuild. Deleted: the field from `InventoryData.java`, the parse call from `InventoryParser.java`,
and the `Multi.Per_Page: 28` key from the 3 YAML files that had it (`alliance_stat.yml`, `phone_gang_search.yml`,
`user_stat.yml`). The docket entry's own cited location (`gangland-api Settings.java`) was inaccurate — the real
dead code was `InventoryData`/`InventoryParser`/3 per-menu YAML files, not a `Settings.java` getter; corrected in
`documentation/migration-0.10.0.md`'s WS2 section.

## Lootchest policy — take/deposit + cursor-on-close (for the docket/clerk)

**Policy**: loot chests are **take-only** — a player can take items out of a chest's shared opening view, but can
never place an item of their own into it, whether swapping into a generated-loot slot or dropping into an empty
one. This restores the deleted `InventoryHandler`-based framework's actual behavior: its click listener cancelled
every click/shift-click deposit into a registered top inventory unless the specific slot was in a `draggableSlots`
allowlist, and the old `LootChestSession` only ever marked the chest's *generated-loot* slots as draggable — so
every empty slot was deposit-blocked too, making loot chests take-only even before this gate. The bare
`SharedLootInventory` wrapper this gate introduced (file #23-24/the new `SharedLootInventory.java` above) carried
no such gate at all — a plain `Bukkit.createInventory(...)` wrap with nothing cancelling clicks — so the
`InventoryHandler`→`SharedLootInventory` swap **silently widened** loot chests into free, shared dumping-ground
storage. A later code review caught this regression; it is fixed as of this same report:

- `SharedLootInventory` now `implements org.bukkit.inventory.InventoryHolder`, constructed via
  `Bukkit.createInventory(this, this.size, ...)` instead of `Bukkit.createInventory(null, ...)`, giving it a real
  object identity a listener can check.
- `LootChestListener.onInventoryClick` gates on `event.getView().getTopInventory().getHolder() ==
  session.getInventory()` (object identity, never a title-string match) and cancels every deposit-shaped
  `InventoryAction` landing on the top inventory — `PLACE_ALL`, `PLACE_ONE`, `PLACE_SOME`, `SWAP_WITH_CURSOR`,
  `HOTBAR_SWAP`, `HOTBAR_MOVE_AND_READD` — plus `MOVE_TO_OTHER_INVENTORY` specifically when it originates from the
  player's own (bottom) inventory, i.e. a shift-click deposit. The reverse shift-click (out of the chest),
  `PICKUP_ALL`/`PICKUP_HALF`/`PICKUP_SOME`/`PICKUP_ONE`, and `COLLECT_TO_CURSOR` are all take-shaped and stay
  untouched; the pre-existing `markItemTaken()`/`syncInventoryToChestData()` take-tracking path is unchanged.
- A new `LootChestListener.onInventoryDrag` handler cancels any `InventoryDragEvent` whose `getRawSlots()` touches
  the top inventory at all — a drag can only ever place items across the slots it spans, so "drag out" is not a
  real take path.

Red-first covered by `gangland-ui/lootchest-api/src/test/java/org/luckyraven/gangland/lootchest/listener/
LootChestListenerTest.java` (9 cases): run against the pre-fix, unguarded click/drag handling, the 5
deposit-should-be-cancelled cases (`PLACE_ALL`, `SWAP_WITH_CURSOR`, `HOTBAR_SWAP`, bottom-originating
`MOVE_TO_OTHER_INVENTORY`, and the drag case) all genuinely failed with `Wanted but not invoked:
inventoryClickEvent/inventoryDragEvent.setCancelled(true)`; after the fix, all 9 pass (`mvn -pl
gangland-ui/lootchest-api -am test` → `Tests run: 38, Failures: 0, Errors: 0, Skipped: 0` for the module,
`BUILD SUCCESS` for the reactor slice).

**Cursor-on-close/disconnect**: neither the old `InventoryHandler` nor the new `SharedLootInventory` ever touches
`Player#getItemOnCursor()`. A cursor-held stack is native CraftBukkit `InventoryView`-close state, unrelated to
either wrapper class: on close (including disconnect) the server always returns it to the player's own inventory
or drops it at their feet if full — stock Minecraft container-close behavior. Verified by reading (subagent B):
`LootChestListener.java:94-103` (`onInventoryClose`, only calls `closeSession`, no cursor access),
`:106-108` (`onPlayerQuit`, only calls `cancelSession`), `LootChestService.java:293-338` (`closeSession`/
`cancelSession`, no cursor access), and a grep across `gangland-ui/lootchest-api/src` for
`ItemOnCursor|getCursor|setCursor` — **zero matches**. `SharedLootInventory`'s entire surface (`getSize`/
`getInventory`/`setItem`/`open`) has no close/disconnect hook to intercept in the first place. Live confirmation
booked as manual-checklist row 40.

## LS-30 / LS-31 outcome — both fixed (D5 carve-out, free side effect of the wand rewrite)

- **LS-30** ("wand writes to the currently held item, not the GUI's item"): `openConfigInventory` now captures
  `int wandSlot = player.getInventory().getHeldItemSlot()` once, at the top, and threads it through every nested
  screen (`openLootTableSelection`, `openTierSelection`, `openAnvilInput`, `handleInvSizeChange`, `setWandNBT`,
  `updateWandLore`). Every subsequent read/write targets `player.getInventory().getItem(wandSlot)`/
  `.setItem(wandSlot, ...)` instead of `getItemInMainHand()`/`setItemInMainHand()` — a hotbar-slot switch
  mid-configuration can no longer misdirect an edit onto a different item. Live regression check: manual-checklist
  row 42.
- **LS-31** ("allowed-block substring match"): `LootChestWandListener.java`'s allowed-block check changed from
  `.contains(allowed.toUpperCase())` to `.equalsIgnoreCase(allowed)` — `CHEST` no longer wrongly matches
  `TRAPPED_CHEST`/`ENDER_CHEST`. Live regression check: manual-checklist row 43.

Neither fix widened the rewrite materially — both landed inside the file pair (`LootChestWand.java`,
`LootChestWandListener.java`) that was being rewritten wholesale anyway.

## Red-first evidence

No new/flipped tests in this gate. Every existing test that touched the 22 files continued passing unmodified
after the migration (see Build below); the two genuinely new pieces of code this gate wrote — `SharedLootInventory`
(subagent B) and the LS-30/31 fixes inside `LootChestWand`/`LootChestWandListener` (subagent A) — have no unit
test, matching this wave's established precedent for real-GUI/real-Bukkit-`Inventory` code (a raw `Inventory`/a
chest-menu render isn't unit-testable without a live server; `BarterView`/`SellView`/`ShopAdminView` all carry the
same gap, covered by the manual checklist instead). This is a pure migration + two small bugfixes on already-tested
production code, not new logic needing a red-first pin — consistent with G3a/G3's own "genuinely red" notes for
mechanical/relocation work.

## Build

Final gate command: `mvn clean install` (full reactor, tests included) → **BUILD SUCCESS**, all 21 reactor modules
`SUCCESS`. Aggregate test count summed fresh from every module's own `target/surefire-reports/*.txt`:
**Tests run: 818, Failures: 0, Errors: 0, Skipped: 0** — identical to the WS4-G1b baseline (818) immediately before
this gate. **Delta: 0**, explained fully: `inventory-api` shipped **zero test files** (confirmed both by this
gate's own `find` and by the WS2-G3 report's own note that its test count "most likely" sat at zero), so its
deletion removes no tests; every other file this gate touched already had its coverage exercised by the existing
suite (`InventoryParserRoundTripTest`, `SimplePagedMenuTest`, and the npc-shops/lootchest-api test classes) and
none of those files' test counts changed.

An intermediate compile-only run (`mvn clean install -DskipTests`) caught one real break before the full build:
`gangland-npc-shops` failed with `package net.wesjd.anvilgui does not exist` (the transitive-dependency loss
described under "Deletions" above) — fixed by adding a direct pom dependency, then both the compile-only and full
test runs went green on the next attempt.

## Postcondition grep

```
grep -rl "org.luckyraven.gangland.inventory" --include=*.java .
```
→ **zero hits** (confirmed twice — once before deleting the module, once after, both clean; a stray hit in my own
explanatory comment in `GangCommand.java` during the first pass was reworded to avoid literally spelling the
package name).

Resource/doc sweep (`grep -rl "inventory-api"`/`"gangland\.inventory"` across `*.md`/`*.yml`/`*.xml`/`*.json`):
every remaining hit outside `graphify-out/` (auto-generated, untouched — orchestrator's own job) and
`brainstorming/` (historical planning docs, left alone per WS4-G1b's own precedent) is now properly contextualized
as an explicit "deleted"/"old"/"was" historical reference — see "Docs" below for the full file list. `permission:
"gangland.inventory.*"` strings in the 3 core menu YAMLs are permission-node namespaces, unrelated to the Java
package, and correctly untouched.

## Jar audit

`target/gangland_warfare-0.10.0.jar` (the shaded core jar):
```
unzip -l ... | grep -c "keystone/inventory"  → 0
unzip -l ... | grep -c "keystone/shop"       → 0
unzip -l ... | grep -c "gangland/inventory"  → 0
```
All clean — `provided` scope held for `keystone-inventory` everywhere, and no `org.luckyraven.gangland.inventory.*`
class survives (there are none left to leak; the module is deleted).

## Smoke

New scenario `cut-full-regression` added to `brainstorming/bartizan-split-2026-09-08/smoke/scenarios.json`
(module set: mail, turf, civilians, cops-n-crooks, gadget, npc-shops — matches the existing `D5` row's set —
plus `plugins: [Bartizan]`; commands: `glw`, `glw help`, `glw modules`, `glw reload`, `glw debug inv-data`).
`paths.repo_dir`/`paths.keystone_jar_dir` temporarily repointed to this worktree / `wt\keystone-1.11.0\
keystone-plugin\target`, restored to `gangland-0.9.2`/`keystone-1.10.0` immediately after the run (the new row
definition itself kept, matching every prior gate's pattern). Command:
`python smoke.py --rows cut-full-regression --deploy --keystone --restore`.

**Result: PASS.** `boot=True stop=True modules=['civilians','gadget','mail','npcshops','turf','copsncrooks']
errors=1`. `Runtime modules: 6 loaded, 0 fault(s)`, `Item vocabularies installed: [bartizan]`,
`keystone-inventory service registered` (confirms the core `InventoryService` bean construction), `/glw reload`
completed ("Reload has been completed"), `/glw debug inv-data` ran without throwing (empty output — no in-game
player in a console-only run, same "none" tracker state every prior gate's smoke row observed). The 1 "error" is
`[PlaceholderAPI] Failed to download anti malware hash check list` — a pre-existing, environment-only network
failure (the test server has no internet access), already on the harness's `no_errors_except` allow-list; the
`no_errors_except` expectation itself reports PASS. All 4 expectation checks (`loaded_modules`, `must_contain`,
`must_not_contain`, `no_errors_except`) PASS. Report:
`brainstorming/bartizan-split-2026-09-08/smoke/reports/2026-09-22-1814-cut-full-regression.md` (+`.log`,
+summary files) — main checkout, untracked, per LEAD-RULES.

**Loot-chest open/take and wand-preview rows** (console cannot drive a real click/drag) added as a new "## CUT"
section in `exec/G010/WS2-manual-checklist.md`, rows 37-44: place+open a shared chest, take-then-reopen, two
players sharing one chest, cursor-survives-close, wand open+paginate, the LS-30 hotbar-switch regression check,
the LS-31 exact-match regression check, and `/glw lootchest edit`. All unchecked pending a client session.

## Docs

- **`documentation/developer/ui-framework.md`** — full rewrite of the "Inventory System" section (was ~600 lines
  describing the deleted `InventoryHandler`/`MultiInventory`/`MultiPanelInventory` API in detail; now describes
  the two-layer `keystone-inventory` + `menu.*` architecture, `InventoryBuilder`, `SimplePagedMenu`, the
  `Multi.*`→`PagedRegion` rebuild, the item-return contract with the `DropzoneSlotComponent` pattern and the
  peek-and-clone counter-example, the 9 YAML menus' unchanged schema, `/glw debug inv-data`, and `MenuFlow`/
  `Panel`/`FlowState`). Also fixed the overview module table, `LootChestSession`'s class doc, the "Loot Chest +
  Inventory"/"Inventory + Sign" cross-module sections, and the module dependency graph at the bottom.
- **`documentation/developer/modules.md`** — the 101-line `## gangland-ui/inventory-api` class-by-class section
  replaced with a short stub pointing at `ui-framework.md`; TOC entry, module tree diagram, and LOC table row
  updated/removed.
- **`documentation/developer/README.md`** — dropped the `inventory-api/` line from the directory tree diagram.
- **`documentation/module-loader.md`** — "pulls ... inventory-api and sign-api in transitively" → drops
  `inventory-api`, notes it was deleted outright.
- **`documentation/tests/features/inventory.md`** — rewrote the overview paragraph and "Modules involved" line to
  describe the current `keystone-inventory` + `menu.*` architecture instead of the deleted `InventoryUtil` helpers.
- **`documentation/migration-0.10.0.md`** — new `## WS2` section appended: nothing to do for the 9 core menus,
  the dead `Multi.Per_Page` key removal, the `settings.yml Inventory:` block staying live (server-owner-facing
  explanation of the deviation above), the `panel-create` skill retarget, and the loot-chest LS-30/LS-31 fixes.
- **`README.md`** — dropped the `inventory-api` row from the module table.
- **`CLAUDE.md`** (worktree-local, **gitignored** — edits are local-only) — `gangland-api` row's "re-exports
  domain/item/core/inventory/sign" → drops `inventory`; dropped the `gangland-ui/inventory-api` module-table row.
- **`settings.yml`** — the `Inventory:` block itself kept (see Deviation above) but annotated in place explaining
  why; the stale `Debug.Modules` comment listing "Inventory API" as a valid log-module name removed (that specific
  Keystone-logger module identity genuinely no longer exists, independent of the block-deletion deviation).
- **`.claude/skills/panel-create/`** (main checkout, gitignored — **edited despite being in the main checkout**,
  an explicit, deliberate exception the brief itself carves out) — retargeted via a subagent (sonnet) to scaffold
  Keystone's `Panel<S extends FlowState>`/`MenuFlow<S>` instead of the deleted `org.luckyraven.gangland.inventory.
  flow.Panel<S extends FlowSession>`/`MultiPanelInventory<S>`; also fixed stale file-path references (the
  shop-admin flow moved from `gangland-ui/shop-api` — deleted in WS4 — to `gangland-impl/.../shop/admin/view/`;
  trader/banker moved to `gangland-features/gangland-npc-shops/...`), the `onEnd`-fixed-at-build-time behavior
  change, the `ItemHoldingComponent`/item-return contract for drop-zone panels (vs. the peek-and-clone
  non-item-holding case), the `SoundConfiguration`→`SoundEffect` and `ChatUtil` package renames, and the YAML
  frontmatter `description:` line. All 6 files touched (`SKILL.md` + 5 `references/*.md`); verified against real
  current source (`ModeSelectView.java`, `TraderFlow.java`, `ShopAdminView.java`, `DropzoneSlotComponent.java`)
  before writing any template, per its own instructions. Spot-checked post-hoc: remaining `MultiPanelInventory`/
  `FlowSession`/`InventoryHandler` string matches are all properly contextualized as "the old X" historical
  references or legitimate substring matches inside current class names (`TraderFlowSession` etc. — real,
  unrenamed session-record class names that correctly implement the new `FlowState`, not stale references to the
  old `FlowSession` interface).

Not touched (out of this sweep's scope, matching WS4-G1b's own established precedent): changelogs
(`documentation/v0.7.5-DEV/CHANGELOG*`), and pre-existing unrelated staleness already present in
`documentation/developer/{modules,README}.md` before this gate (mentions of the long-gone `gangland-weapon`
module, `plugin-common`) — those predate even the Bartizan split and are not `inventory-api` mentions.

## Docket

| Id | Status change | Evidence |
|---|---|---|
| **T-43** | open → **fixed** | `Multi.Per_Page` dead key removed from `InventoryData.java`/`InventoryParser.java`/3 YAML files this gate — see "T-43" section above. The docket entry's cited location (`gangland-api Settings.java`) was wrong; corrected in the migration doc. |
| **LS-30** | open (P3) → **fixed** | `LootChestWand.openConfigInventory` now captures `wandSlot` once and threads it through every nested screen instead of re-reading `getItemInMainHand()` — see "LS-30 / LS-31 outcome" above. |
| **LS-31** | open (P3) → **fixed** | `LootChestWandListener`'s allowed-block check is now `.equalsIgnoreCase` not `.contains` — see "LS-30 / LS-31 outcome" above. |
| **UI-04** | open (P1) → **fixed-by-WS2** | "`InventoryRegistry` never unregisters handlers and `findByInventory` scans everything" — the whole class is deleted; `RemoveAccountListener`/`KernelConfig` no longer construct or clear it, replaced by `keystone-inventory`'s `InventoryService`/`OpenMenuTracker`, which is `InventoryHolder`-identity-based (no linear scan) and untracks on quit automatically via its own `MenuListener`. |
| **UI-10** | open (P3, dormant) → **fixed-by-WS2** | "`SPECIAL_INVENTORIES` handlers would not cancel clicks (dormant)" — `InventoryHandler.SPECIAL_INVENTORIES` and its accessor `getSpecialInventories()` are deleted along with the class; `DebugCommand`'s `/glw debug inv-data special` argument (its only reader) deleted too — see file #8 above. |
| **T-41** | open (P2) → **fixed-by-WS2** | "`InventoryClickHandler` cancels MOVE_TO_OTHER_INVENTORY/COLLECT_TO_CURSOR only when the click lands on the bottom inventory" — the docket entry's own resolution note already said this was "superseded by the WS2 cutover to Keystone's `keystone-inventory`... which ships this exact guard natively (cross-project docket KS-IV-03... fixed in `keystone-inventory` E6.1)." `InventoryClickHandler.java` (the buggy class) is deleted this gate; the guard now lives in Keystone's `ChestMenu`/`MenuListener`, unconditionally, for every menu. |
| **GR-18** | open → **fixed** | "`GangCommand.gangStat` is dead code (~140 lines)" — already an existing docket entry (correction from fix round 1: this report previously called it an unfiled "docket candidate"; it was already filed). `gangStat()`/`itemToBalance()` deleted this gate: zero callers anywhere in the reactor, `commands.json`, `/glw` help output, or `gang_info.yml` (which serves `/glw gang info` through the YAML-driven dialect instead). See file #11 above for the full reasoning. The clerk writes the row. |

**WS6 G3 migration item / docket candidate (new, from the review's W49 ruling on the `settings.yml Inventory:` block
deviation)**: the block's retirement — migrating `TraderSettingsImpl`/`BankerSettingsImpl`/`TurfModuleConfig`/
`GanglandShopUiSettings` off `Settings.getInventoryFillItem()`/`getInventoryFillName()` onto their own
YAML-backed defaults (the same pattern `TraderSettingsImpl`/`BankerSettingsImpl` already use for their *other*
settings) — is not scoped or attempted here. Flagged as a WS6 G3 (api-facade wave) migration item, and as a
docket candidate for the clerk to file if it isn't already tracked.

## Subagents used

- **sonnet** — "Rewrite LootChestWand admin GUI onto keystone-inventory" (`LootChestWand.java`,
  `LootChestWandEditCommand.java`, `LootChestWandListener.java`, plus the `LootChestManager`/`GameplayConfig`
  wiring it needed): full rewrite, LS-30/LS-31 fixed, `mvn -pl gangland-impl -am compile -DskipTests` verified
  green. See files #12, #14, #16, #17 above.
- **sonnet** — "Rewrite loot chest opening view off InventoryHandler" (`LootChestService.java`,
  `LootChestSession.java`, new `SharedLootInventory.java`): `mvn -pl gangland-ui/lootchest-api -am compile
  -DskipTests` verified green, plus the take/deposit/cursor-on-close policy investigation. See files #23-24 above.
- **sonnet** — "Retarget panel-create skill to keystone-inventory" (`.claude/skills/panel-create/` in the main
  checkout, all 6 files): see "Docs" above.

Both loot-chest subagents ran in parallel (disjoint file sets: `gangland-impl/.../lootchest/*` vs.
`gangland-ui/lootchest-api/*`); the skill-retarget subagent ran afterward, alone, since it was a documentation-only
task with no compile dependency on the other two. All three were given the exact new signature conventions
(`InventoryBuilder.DEFAULT_*` constants, the `(fillMaterial, fillName)` decomposition) established by my own
earlier edits, and verified their work against real, already-shipped source files (`ModeSelectView.java`,
`TraderFlow.java`, etc.) rather than guessing an API shape.

## Concerns / open questions

- **The `settings.yml Inventory:` block deviation** (above) is the one item worth the orchestrator's attention —
  not a defect, but a real scope boundary the plan's text didn't anticipate. No action needed now; flagging for a
  possible future gate.
- **`SearchButtonFactory`'s `ClickHandler`-returning methods are still unused** (zero real callers, confirmed by
  grep before and after this gate's type-signature fix) — pre-existing orphaned code, not introduced by this gate,
  left in its current (now at least type-correct) state rather than deleted, since deleting a `@Bean`-constructed
  class with a real (if unused) public API felt like a bigger call than this gate's scope warranted. Flagging in
  case it's worth a dead-code pass later.
- **`GangCommand.gangStat()`/`itemToBalance()` dead-code deletion** — resolved as of fix round 1: this is docket
  GR-18, an already-filed, already-triaged entry ("`GangCommand.gangStat` is dead code, ~140 lines"), not a
  speculative call this gate made on its own. No longer a concern.
- Every "CUT" manual-checklist row (37-44) is unchecked, pending a client session — same limitation as every
  prior GUI gate this wave (console cannot drive a real click/drag).

## Deliverables

- `exec/G010/CUT-report.md` — this file.
- `exec/G010/CUT-package.diff` — `git add -N . && git diff HEAD` (then `git reset`; worktree left uncommitted).
  4680 lines, 64 tracked-file changes (45 modified, 14 deleted from `inventory-api`, 1 pom-only, plus 1 new
  untracked file `SharedLootInventory.java` which `git add -N` also picked up) — `CLAUDE.md`'s edits are real but
  gitignored, so they do not appear in this diff (see "Docs" above); the `.claude/skills/panel-create/` edits live
  in the *main checkout*, not this worktree, so they also do not appear here.
- `exec/G010/WS2-manual-checklist.md` — new "## CUT" section, rows 37-44.
- `brainstorming/bartizan-split-2026-09-08/smoke/scenarios.json` — new `cut-full-regression` scenario retained;
  `paths` were temporarily repointed for the run and restored afterward.
- `brainstorming/bartizan-split-2026-09-08/smoke/reports/2026-09-22-1814-*` — the smoke run's report files (main
  checkout, untracked).
