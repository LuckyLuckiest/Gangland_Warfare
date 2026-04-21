# Loot Chests

[← Waypoints](./waypoints.md) | [Back to Index](../README.md) | [Next: Levels →](./levels.md)

---

## Overview

Loot chests are in-world containers that hold randomized rewards. They unlock on a countdown timer and award players
with a mix of money, XP, weapons, ammo, keys, and other items based on configurable loot tables and drop-chance
buckets. Chest **tiers** gate which tables a chest can draw from and which unlock item the player needs.

> **Changed in 0.7.5-DEV:** tier gating no longer filters individual items inside a table — per-item `Drop_Chance`
> buckets do that job. A chest's tier only decides which tables it's allowed to draw from (`Allowed_Tiers`).

---

## Drop Chance Buckets

Every item in a loot table is tagged with one of five **drop-chance buckets**. The bucket decides how often a roll
lands on that item.

| Bucket      | Base Chance | Typical Use                        |
|-------------|-------------|------------------------------------|
| `COMMON`    | 1.0         | Bulk ammo, consumables.            |
| `UNCOMMON`  | 0.7         | Side-grade ammo, utility items.    |
| `RARE`      | 0.4         | Mid-tier weapons, lockpicks.       |
| `EPIC`      | 0.15        | High-tier weapons, epic keys.      |
| `LEGENDARY` | 0.05        | Signature weapons, legendary keys. |

Per-table **`Rarity_Overrides`** can adjust any bucket just for that table — e.g. a legendary-vault table might
set `legendary: 0.12` to roughly triple the legendary chance compared to the base.

---

## Chest Tiers & Unlock Items

Chest **tiers** control two things:

1. Which **loot tables** a chest of that tier can draw from (`Allowed_Tiers` on each table).
2. Which **unlock item** the player needs in inventory to open the chest.

| Tier      | Color  | Unlock Requirement | Unlock Item     |
|-----------|--------|--------------------|-----------------|
| Common    | Gray   | None               | —               |
| Uncommon  | Green  | None               | —               |
| Rare      | Blue   | Lockpick           | `lockpick`      |
| Epic      | Purple | Key                | `epic_key`      |
| Legendary | Gold   | Key                | `legendary_key` |

The unlock item (if any) **floats above the chest as a plain in-game item** — no more armor-stand placeholder —
so resource packs and custom models render exactly as the player expects. The item is consumed when the chest
opens.

See the [Unique Items guide](./unique-items.md) for how to obtain keys and lockpicks.

---

## Built-in Loot Tables

Three loot tables ship with the plugin:

| Table           | Display Name  | Items Generated | Contents                          |
|-----------------|---------------|-----------------|-----------------------------------|
| `street_loot`   | Street Loot   | 2–5             | Common and uncommon items         |
| `military_loot` | Military Loot | 3–7             | Rare, epic, and legendary weapons |
| `supply_cache`  | Supply Cache  | 4–8             | Mixed common-to-rare items        |

Loot tables are configured in `loot_chests.yml`. Item categories that can appear include:

- **AMMO** — Any configured ammo type from `ammunition.yml`.
- **WEAPON** — Any configured weapon.
- **UNIQUE** — Keys, lockpicks, and other unique items.

---

## How It Works

1. An admin designates a container block as a loot chest using the wand tool.
2. Once designated, a **countdown timer** begins (default **5 minutes**).
3. While the timer ticks down, a **preview** above the chest advertises what it's holding — no more blind clicks.
   If the tier requires an unlock item, that item hovers over the chest too.
4. When the timer expires, the chest opens with a sound effect.
5. Players interact with the chest to claim their randomized rewards. Weapon rolls drop as real weapon items
   with a floating **name hologram** so players can identify loot at a glance.
6. Rewards include a random money amount, a random XP amount, and items drawn from the assigned loot table
   according to the drop-chance buckets above.

---

## Commands

| Command                      | Description                                                                      |
|------------------------------|----------------------------------------------------------------------------------|
| `/glw lootchest wand`        | Gives you the selection wand for designating containers as loot chests.          |
| `/glw lootchest wand edit`   | With a chest selected using the wand, opens the loot chest configuration editor. |
| `/glw lootchest remove <id>` | Removes the loot chest designation from a container (does not break the block).  |

### Supported Container Types

The following block types can be designated as loot chests:

- `CHEST`
- `TRAPPED_CHEST`
- `BARREL`
- `SHULKER_BOX`
- `ENDER_CHEST`

---

## Configuration

In `settings.yml`:

```yaml
Loot_Chest:
   Countdown_Timer: 300          # Seconds before the chest opens (default: 5 minutes)

   Sound:
      Opening: "BLOCK_CHEST_OPEN"
      Locked: "BLOCK_CHEST_LOCKED"
      Closing: "BLOCK_CHEST_CLOSE"

   Allowed_Blocks:
      - "CHEST"
      - "TRAPPED_CHEST"
      - "BARREL"
      - "SHULKER_BOX"
      - "ENDER_CHEST"

   Rewards:
      Money:
         Minimum: 10
         Maximum: 1_000            # Random amount drawn between min and max
      Experience:
         Minimum: 5
         Maximum: 100
      Commands:
         - ""                      # Optional: server commands executed on open
```

Tier definitions and base rarity weights live in `loot/tiers.yml`:

```yaml
Rarity:
   common: 1.0
   uncommon: 0.7
   rare: 0.4
   epic: 0.15
   legendary: 0.05

Tiers:
   rare:
      Display_Name: "&9Rare"
      Level: 10                   # Minimum player level to access this tier
      Unlock_Requirement: LOCKPICK
      Unlock_Item: "lockpick"     # Id from unique_items.yml
   epic:
      Display_Name: "&5Epic"
      Level: 15
      Unlock_Requirement: KEY
      Unlock_Item: "epic_key"
   legendary:
      Display_Name: "&6Legendary"
      Level: 25
      Unlock_Requirement: PERMISSION
      Unlock_Item: "legendary_key"
```

Per-table item drops and per-table rarity overrides live in `loot/loot_chests.yml`:

```yaml
Loot_Tables:
   military_loot:
      Display_Name: "&2Military Loot"
      Min_Items: 3
      Max_Items: 7
      Allowed_Tiers:                 # Tiers of chest this table can appear in
         - "rare"
         - "epic"
      Rarity_Overrides:              # Table-scoped tweaks to the base Rarity weights
         epic: 0.25
      Items:
         assault_rifle:
            Item: "weapon:rifle"     # Parsed by the global ItemParser
            Drop_Chance: RARE        # Bucket: COMMON | UNCOMMON | RARE | EPIC | LEGENDARY
            Weight: 3.0              # Relative weight within the bucket
         556_ammo:
            Item: "ammo:5,56"
            Drop_Chance: COMMON
            Min_Amount: 20
            Max_Amount: 60
            Weight: 8.0
```

> **Changed in 0.7.5-DEV:** individual items no longer carry a `Required_Tier` field — use the `Drop_Chance`
> bucket for item-level rarity and `Allowed_Tiers` at the table level for chest-tier gating. The loot-chest
> loader rejects unknown keys, so stale `Required_Tier:` lines will be reported on first boot.

---

[← Waypoints](./waypoints.md) | [Back to Index](../README.md) | [Next: Levels →](./levels.md)
