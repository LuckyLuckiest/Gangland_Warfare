# Unique Items

[← Levels](./levels.md) | [Back to Index](../README.md) | [Next: Scoreboard →](./scoreboard.md)

---

## Overview

Unique items are special items with defined behaviors separate from the normal weapon and armor systems. They have fixed
vanilla item representations, configurable inventory rules (auto-give on join, slot pinning, drop protection), and some
act as required keys for unlocking loot chest tiers.

---

## Built-in Unique Items

### Phone

The phone is a permanent personal item that every player receives automatically. It sits in the last hotbar slot and
cannot be dropped or duplicated.

| Property           | Value                |
|--------------------|----------------------|
| Item               | Compass              |
| Slot               | 8 (last hotbar slot) |
| Given on join      | Yes                  |
| Given on respawn   | Yes                  |
| Droppable          | No                   |
| Duplicates allowed | No                   |

### Lockpick

A consumable item required to open **Rare** tier loot chests. It is droppable and can be looted from enemies or found in
lower-tier chests. Players can carry multiple.

| Property         | Value         |
|------------------|---------------|
| Item             | Tripwire Hook |
| Given on join    | No            |
| Droppable        | Yes           |
| Dropped on death | Yes           |
| Loot tier unlock | Rare          |

### Epic Key

Required to open **Epic** tier loot chests. Droppable and tradeable.

| Property         | Value       |
|------------------|-------------|
| Item             | Gold Nugget |
| Given on join    | No          |
| Droppable        | Yes         |
| Dropped on death | Yes         |
| Loot tier unlock | Epic        |

### Legendary Key

Required to open **Legendary** tier loot chests. The rarest key.

| Property         | Value        |
|------------------|--------------|
| Item             | Golden Apple |
| Given on join    | No           |
| Droppable        | Yes          |
| Dropped on death | Yes          |
| Loot tier unlock | Legendary    |

---

## Inventory Rules

Each unique item supports the following inventory behavior settings:

| Property           | Description                                                                    |
|--------------------|--------------------------------------------------------------------------------|
| `Add_On_Join`      | Automatically added to the player's inventory when they first join the server. |
| `Add_On_Respawn`   | Re-added to inventory every time the player respawns.                          |
| `Drop_On_Death`    | Whether the item is dropped at death location (if `false`, it disappears).     |
| `Allow_Duplicates` | Whether the player can carry more than one of this item.                       |
| `Slot`             | Forces the item into a specific inventory slot. Use `-1` for no fixed slot.    |
| `Droppable`        | Whether the player can manually drop the item from their inventory.            |
| `Movable`          | Whether the player can move the item between inventory slots.                  |

---

## Configuration

Unique items are defined in `unique_items.yml`. Each entry is identified by a key (used as the internal ID):

```yaml
phone:
   Permission: "gangland.uniqueitem.phone"   # Permission node to receive this item
   Material: COMPASS
   Name: "&6Phone"
   Lore:
      - "&7Your personal communication device"
   Inventory:
      Add_On_Join: true
      Add_On_Respawn: true
      Drop_On_Death: false
      Allow_Duplicates: false
      Slot: 8
      Movable: true
      Droppable: false

lockpick:
   Permission: "gangland.uniqueitem.lockpick"
   Material: TRIPWIRE_HOOK
   Name: "&7Lockpick"
   Lore:
      - "&7Used to open &9Rare &7tier chests"
   Loot_Key: "lockpick"                      # Links this item to a loot tier unlock
   Inventory:
      Add_On_Join: false
      Add_On_Respawn: false
      Drop_On_Death: true
      Allow_Duplicates: true
      Slot: -1
      Movable: true
      Droppable: true
```

The `Loot_Key` field links the item to a tier defined in `loot/tiers.yml`. When a player attempts to open a loot chest
tier that has an `Unlock_Item` configured, the system checks for an item whose `Loot_Key` matches and consumes one if
found.

### Adding a New Unique Item

```yaml
medkit:
   Permission: "gangland.uniqueitem.medkit"
   Material: RED_DYE
   Name: "&cMedkit"
   Lore:
      - "&7Restores health when used"
   Inventory:
      Add_On_Join: false
      Add_On_Respawn: false
      Drop_On_Death: true
      Allow_Duplicates: true
      Slot: -1
      Movable: true
      Droppable: true
```

Any key added to `unique_items.yml` is automatically registered on load.

---

[← Levels](./levels.md) | [Back to Index](../README.md) | [Next: Scoreboard →](./scoreboard.md)
