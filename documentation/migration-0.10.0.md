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

<!-- Later 0.10.0 gates (WS2 inventory-api → keystone-inventory, WS3 lootchest/hologram, WS5 gang module,
     WS6 api facade) append their own sections here as they land. -->
