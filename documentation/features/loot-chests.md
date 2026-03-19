# Loot Chests

[← Waypoints](./waypoints.md) | [Back to Index](../README.md) | [Next: Levels →](./levels.md)

---

## Overview

Loot chests are in-world containers that hold randomized rewards. They unlock on a countdown timer and award players
with a mix of money, XP, weapons, ammo, keys, and other items based on configurable loot tables and rarity tiers. Some
tiers are locked behind special items that players must obtain before they can open them.

---

## Tiers

Every item in a loot table belongs to a rarity tier. Higher tiers have lower spawn weights and often require a key or
minimum player level to unlock.

| Tier      | Color  | Spawn Chance | Unlock Requirement |
|-----------|--------|--------------|--------------------|
| Common    | Gray   | 100%         | None               |
| Uncommon  | Green  | 70%          | None               |
| Rare      | Blue   | 40%          | Lockpick           |
| Epic      | Purple | 15%          | Epic Key           |
| Legendary | Gold   | 5%           | Legendary Key      |

- **Common and Uncommon** items drop freely from any chest.
- **Rare** items require the player to have a **Lockpick** in their inventory. The lockpick is consumed on use.
- **Epic** items require an **Epic Key**.
- **Legendary** items require a **Legendary Key**.

See the [Unique Items guide](./unique-items.md) for how to obtain keys.

---

## Built-in Loot Tables

Three loot tables ship with the plugin:

| Table           | Display Name  | Items Generated | Contents                          |
|-----------------|---------------|-----------------|-----------------------------------|
| `street_loot`   | Street Loot   | 2–5             | Common and uncommon items         |
| `military_loot` | Military Loot | 3–7             | Rare, epic, and legendary weapons |
| `supply_cache`  | Supply Cache  | 4–8             | Mixed common-to-rare items        |

Loot tables are configured in `loot-chests.yml`. Item categories that can appear include:

- **AMMO** — Any configured ammo type from `ammunition.yml`.
- **WEAPON** — Any configured weapon.
- **REPAIR** — Repair materials from `repair.yml`.
- **UNIQUE** — Keys, lockpicks, and other unique items.

---

## How It Works

1. An admin designates a container block as a loot chest using the wand tool.
2. Once designated, a **countdown timer** begins (default **5 minutes**).
3. When the timer expires, the chest opens with a sound effect.
4. Players interact with the chest to claim their randomized rewards.
5. Rewards include a random money amount, a random XP amount, and items drawn from the assigned loot table.

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

Tier rarity weights and unlock requirements are configured in `loot/tiers.yml`:

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
      Unlock_Item: "lockpick"     # Key from unique_items.yml
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

---

[← Waypoints](./waypoints.md) | [Back to Index](../README.md) | [Next: Levels →](./levels.md)
