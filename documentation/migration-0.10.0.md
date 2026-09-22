# Migrating a server from 0.9.2 to Gangland 0.10.0

[← Back to Documentation Index](./README.md)

Gangland 0.10.0 is the decoupling wave: the plugin API bumps to a new major line (`Host_Api: 2.0`, see §2), and
Gangland-owned systems start moving out into standalone plugins or Keystone modules. This page grows one section
per gate as the wave lands; the first section (WS1) is below.

## WS1 — the scoreboard is now its own plugin, Plaque

Gangland Warfare no longer has any scoreboard code, config or command. Scoreboard rendering moved to a standalone
sibling plugin, **Plaque** (`E:\Programming\java\Plaque`), which has no dependency on Gangland at all and renders
the same board through PlaceholderAPI `%gangland_*%` tokens.

### 1. Install Plaque

Drop `Plaque-<version>.jar` into `plugins/`, alongside `Gangland_Warfare-<version>.jar` and `Keystone-<version>.jar`
(Plaque only needs Keystone — it works without Gangland installed too, showing literal `%gangland_*%` text instead
of live gang/user data). No `Host_Api` or `Depends:`/`Plugins:` wiring is involved; Plaque is not a Gangland
runtime module, it is a separate plugin.

### 2. Copy `scoreboard.yml`

Plaque's `scoreboard.yml` uses the **identical schema** Gangland's old file used
(`Board.Title.{Interval,Lines}` / `Board.Rows.<n>.{Interval,Lines}`). Copy your existing
`plugins/Gangland_Warfare/scoreboard.yml` to `plugins/Plaque/scoreboard.yml` and it loads unchanged — no key
renames, no reformatting.

### 3. Carry the two `Scoreboard:` settings values into Plaque's own `settings.yml`

Gangland's deleted `settings.yml` `Scoreboard:` block had two keys. Both move to Plaque's own `settings.yml`, at
the top level (not nested under a `Scoreboard:` section there):

| Old (`Gangland_Warfare/settings.yml`) | New (`Plaque/settings.yml`) |
|---|---|
| `Scoreboard.Enable` | `Enable` |
| `Scoreboard.Driver` | `Driver` |

Plaque ships only the `Driver_V3` renderer (Gangland's old `Driver_V1`/`Driver_V2` clustering algorithms were
retired in the split); an unrecognised `Driver:` value falls back to `Driver_V3` with a startup warning instead of
silently reproducing a deleted driver's behaviour.

### 4. Delete the leftover `Scoreboard:` block from Gangland's `settings.yml`

If you upgrade an existing `settings.yml` in place rather than regenerating it, a leftover `Scoreboard:` block is
**harmless but inert** — Gangland's settings loader reads sections by name (`Settings.populate()`'s
`section(root, "X", report)` calls), and nothing calls `section(root, "Scoreboard", report)` any more, so the
block is parsed into memory and never visited. It causes no fault and no warning; it is simply dead weight worth
deleting for a clean file.

### 5. What breaks if you skip Plaque entirely

Nothing errors. Gangland boots with no scoreboard of any kind — no fault line, no missing-dependency warning
(Plaque was never a Gangland dependency in either direction). Players simply see no scoreboard until Plaque is
installed.

## WS4 — Trader/Banker NPC settings moved out of core `settings.yml`

`gangland-ui/shop-api` is gone; the headless shop system (registry, purchase/sell/barter services, valuators) now
comes from Keystone's `keystone-shop`, and 10 NPC-specific knobs that used to live under core `settings.yml`'s
`Trader:`/`Banker:` blocks moved into two new files shipped inside `modules/gangland-npc-shops-<rev>.jar` and
extracted alongside the module's other defaults (same mechanism as `npc/trader_traits.yml`/`npc/bank_tiers.yml`).

### 1. The 10 keys and their new files

| Old (`Gangland_Warfare/settings.yml`) | New file | New key |
|---|---|---|
| `Trader.Respawn_Cooldown` | `plugins/Gangland_Warfare/npc/trader_settings.yml` | `Respawn_Cooldown` |
| `Trader.Head_Track_Radius` | same | `Head_Track_Radius` |
| `Trader.Fallback_Trait_Id` | same | `Fallback_Trait_Id` |
| `Trader.Sell.Max_Offer_Slots` | same | `Sell.Max_Offer_Slots` |
| `Trader.Sell.Mood_Per_Sale` | same | `Sell.Mood_Per_Sale` |
| `Trader.Tip_Amount` | same | `Tip_Amount` |
| `Banker.Head_Track_Radius` | `plugins/Gangland_Warfare/npc/banker_settings.yml` | `Head_Track_Radius` |
| `Banker.Max_Health` | same | `Max_Health` |
| `Banker.Invulnerable` | same | `Invulnerable` |
| `Banker.Fallback_Tier_Id` | same | `Fallback_Tier_Id` |

Both files are created automatically (with the same defaults the old `Trader:`/`Banker:` blocks shipped) the first
time the npc-shops module boots, exactly like any other module-owned YAML — nothing to install by hand.

### 2. `Trader.Max_Mode_Multiplier` is the one key that stayed core

It moved sideways instead of out: the admin price editor (`/glw shop edit`) reads it too, not just the trader
browser, so it stayed in core `settings.yml` under a new, small top-level block:

| Old | New |
|---|---|
| `Trader.Max_Mode_Multiplier` | `Shop.Max_Mode_Multiplier` |

### 3. Customised values are NOT auto-migrated — copy them by hand

If you had changed any of the 10 keys in section 1 away from their defaults, upgrading in place does **not** carry
those values into the new files — the new `npc/trader_settings.yml`/`npc/banker_settings.yml` are written fresh
from the module jar's own defaults, and core `Settings` no longer reads the old `Trader:`/`Banker:` block at all.
Open your old `settings.yml`, copy any non-default value across to the matching key in the new file, by hand,
before or after the upgrade.

### 4. The warning that tells you this needs doing

A leftover `Trader:`/`Banker:` block in `settings.yml` is not silently ignored like WS1's `Scoreboard:` block —
`Settings`' load step (`init()`, run at boot and on every `/glw reload`) fires a **targeted warning naming the new
file**, once per block, distinct from the generic "unknown key" line the individual leaf keys underneath it still
get:

```
[Gangland.Settings] settings.yml still has a legacy 'Trader:' block — those keys moved to
plugins/Gangland_Warfare/npc/trader_settings.yml (extracted by the npc-shops module); customised values are NOT
auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
```

Seeing this line (for `Trader:` and/or `Banker:`) is your signal to do step 3, then delete the leftover block —
once removed, the warning stops.

## See also

- [`documentation/features/scoreboard.md`](./features/scoreboard.md) — the short in-repo pointer to Plaque.
- [`documentation/developer/configuration.md`](./developer/configuration.md) — the retired `scoreboard.yml`
  section, with the schema comparison.
- [`documentation/migration-0.9.2.md`](./migration-0.9.2.md) — the previous migration note (jetpack ownership,
  Bartizan going soft), unaffected by WS1.

## WS2 — the custom inventory/GUI framework is gone; every menu runs on Keystone's `keystone-inventory`

`gangland-ui/inventory-api` (the `InventoryHandler`/`InventoryBuilder`/`MultiInventory`/`MultiPanelInventory`
framework) has been deleted outright. Every menu — the 9 core YAML menus, trader/banker flows, shop admin views,
turf/cops-n-crooks/gadget menus, the loot-chest admin wand preview — now builds on Keystone's `keystone-inventory`
library, either directly or through `gangland-impl`'s own thin YAML dialect
(`org.luckyraven.gangland.menu.*`, moved out of `inventory-api` at G3a). See
[`documentation/developer/ui-framework.md`](./developer/ui-framework.md) for the architecture.

### 1. Nothing to do for the 9 core YAML menus

`gangland-impl/src/main/resources/inventory/*.yml` (`phone.yml`, `gang_info.yml`, `gang_stat.yml`,
`user_stat.yml`, `alliance_stat.yml`, `phone_gang_search.yml`, `phone_banking.yml`, …) load through an
**unchanged schema** — no key renames, no reformatting, no server-owner action. They render through
`ChestMenuBuilder` now instead of the deleted `InventoryHandler`, but the YAML you already have keeps working
byte-for-byte.

### 2. One dead YAML key removed: `Configuration.Multi.Per_Page`

`alliance_stat.yml`, `phone_gang_search.yml` and `user_stat.yml` each had a `Multi.Per_Page: 28` key
(docket **T-43**). It was parsed but never actually read — even before this gate, and doubly so now that
pagination runs on Keystone's `PageConfig`/`PagedRegion`, whose own arithmetic drives page size. The key has been
removed from the 3 shipped files; if a server owner customized it in their own copy, the value is simply ignored
(was already ignored before 0.10.0 too) and can be deleted with no behavior change.

### 3. `settings.yml`'s `Inventory:` block is unchanged — still there, still read

Unlike most of this wave's deletions, the `Inventory:` block (`Fill.Item/Name`, `Line.Item/Name`,
`Multi_Inventory.{Next_Page,Previous_Page,Home_Page}` head textures) was **not** removed. It is still read by
several already-shipped module views (trader/banker/turf/shop-admin fill colors) that never went through
`inventory-api` in the first place. If you customized this block, your customization still applies to those
views. The 9 core YAML menus and `SimplePagedMenu`'s 3 call sites (`/glw debug multi`, gang member/ally lists,
the bounty sign view) no longer read it — they render with the same shipped defaults
(`BLACK_STAINED_GLASS_PANE`/`WHITE_STAINED_GLASS_PANE`, both named `" "`) as literal constants instead, so nothing
visibly changes there either way.

### 4. `.claude/skills/panel-create/`

If you use this Claude Code skill to scaffold new panels, it now generates Keystone's `Panel<S extends
FlowState>`/`MenuFlow<S>` shape, not the deleted `org.luckyraven.gangland.inventory.flow.Panel<S extends
FlowSession>`/`MultiPanelInventory<S>` shape. No action needed unless you have local, uncommitted panel scaffolds
mid-generation from before this gate.

### 5. Loot chests: the admin wand preview moved GUIs, the chest-opening view did not change shape

The chest-opening view (what a player sees when they open a placed loot chest) was already a plain shared Bukkit
inventory, not a menu — it stays that way, just on a small internal `SharedLootInventory` wrapper instead of the
deleted `InventoryHandler`. Cooldown and persistence are unchanged. The admin **wand preview** screen (`/glw
lootchest wand`, `/glw lootchest edit`) is a real menu and now renders through
`ChestMenuBuilder`/`PagedRegion` — same paginated 28-slot grid, same Back/Prev/PageInfo/Next layout. Two
longstanding bugs were fixed as a free side effect of that rewrite (both were already docket-tracked, P3):

- **LS-30**: the wand used to write edits to whatever item was currently in the admin's main hand at click time,
  not the item the wand's own GUI slot showed — a hotbar-slot switch mid-edit could silently misdirect or drop an
  edit. Fixed: edits now always target the exact inventory slot the wand was in when the config screen opened.
- **LS-31**: the "is this block type allowed for a loot chest" check used a substring match, so e.g. `CHEST`
  wrongly matched `TRAPPED_CHEST`/`ENDER_CHEST`. Fixed: exact match against the configured allow-list.

No server-owner action needed for either — both are pure bugfixes, no config shape change.

**Deposit policy: loot chests are take-only, full stop.** The old (deleted) `InventoryHandler` framework never let
a player deposit into a loot chest except onto the chest's own generated-loot slots, via a `draggableSlots`
allowlist its click listener checked on every click/shift-click — and `LootChestSession` never marked an empty
slot as draggable, so in practice nothing could ever be placed into a loot chest, full or empty. When the
opening view moved onto the bare `SharedLootInventory` wrapper this gate introduced, that gate carried over no
click guard at all, so a loot chest silently became placeable/shared storage until a later review caught it.
Fixed: `SharedLootInventory` now implements `InventoryHolder` (identity, not title matching) and
`LootChestListener` cancels every deposit-shaped click (`PLACE_ALL`/`PLACE_ONE`/`PLACE_SOME`/`SWAP_WITH_CURSOR`/
`HOTBAR_SWAP`/`HOTBAR_MOVE_AND_READD` onto the chest, plus a shift-click `MOVE_TO_OTHER_INVENTORY` originating
from the player's own inventory) and every drag that touches a chest slot, leaving every take path (`PICKUP_*`,
`COLLECT_TO_CURSOR`, shift-click out of the chest) untouched. No deposit slot exists any more — not even onto
generated loot. This is deliberate, not a missed case: a loot chest regenerates on cooldown/respawn, which would
silently void anything a player had deposited into it, and a free deposit slot would make loot chests double as
an unintended shared stash. Covered by
`gangland-ui/lootchest-api/src/test/java/org/luckyraven/gangland/lootchest/listener/LootChestListenerTest.java`.

<!-- Later 0.10.0 gates (WS3 lootchest module extraction / hologram → keystone-hologram, WS5 gang module,
     WS6 api facade) append their own sections here as they land. -->
