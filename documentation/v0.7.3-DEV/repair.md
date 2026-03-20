# Repair System

[← Wearables](./wearables.md) | [Back to Index](../README.md) | [Next: Trade Signs →](../features/trade-signs.md)

---

## Overview

Both weapons and wearables have durability that depletes with use. Rather than discarding them, players can repair their
gear using configurable repair materials. Each material has a limited number of uses, restores a set amount of
durability, and may only work on certain item categories.

---

## How It Works

1. A player holds a weapon or wearable that has lost durability.
2. They use a repair material from their inventory — either by right-clicking the item or through a crafting-style
   interaction (exact input method depends on your server's configuration).
3. The material consumes one use and restores the configured amount of durability.
4. If the material has no uses left, it is removed from the player's inventory.

Durability cannot exceed the item's base maximum. Over-repairing simply brings it to the cap — no excess is carried.

---

## Built-in Repair Materials

| Name              | Vanilla Item | Uses | Restores               | Works On            |
|-------------------|--------------|------|------------------------|---------------------|
| Cleaning Kit      | Paper        | 5    | 15 durability          | Weapons & Wearables |
| Mechanical Part   | Iron Ingot   | 1    | 25% of max durability  | Weapons only        |
| Weapon Repair Kit | Diamond      | 3    | 100% of max durability | Weapons only        |
| Field Kit         | Stick        | 10   | 5 durability           | Weapons only        |

> **Mechanical Part** and **Weapon Repair Kit** restore percentage-based or full durability respectively — they are your
> heavy repair tools. **Cleaning Kit** and **Field Kit** are low-value maintenance items for keeping gear topped up in
> the
> field.

---

## Configuration

Repair materials are defined under `Repair_Materials` in `repair.yml`. Each key becomes the material's internal ID.

```yaml
Repair_Materials:
  Cleaning_Kit:
    Display_Name: "&aCleaning Kit"      # Item display name (supports & color codes)
    Material: PAPER                     # Vanilla Bukkit material used as the physical item
    Uses: 5                             # Uses remaining before the item is removed from the player's inventory
    Restore_Amount: 15                  # Flat durability restored per use (0 = disabled)
    Restore_Percent: 0.0                # Percentage of max durability restored per use (0.0 = disabled)
                                        # When both are set, the higher resulting value is used.
    Sound:
      Default_Sound:                    # Vanilla Bukkit sound played on repair
        Sound: ENTITY_PLAYER_SPLASH
        Volume: 1.0                     # Volume (0.0–1.0)
        Pitch: 1.0                      # Pitch (0.5–2.0)
      Custom_Sound:                     # Resource pack sound played on repair (used if resource pack is loaded)
        Sound: ""
        Volume: 1.0
        Pitch: 1.0
    Compatible_Types:                   # Item categories this material can repair
      - WEAPON
      - WEARABLE                        # Valid values: WEAPON, WEARABLE, GADGET
    Lore:
      - "&7A basic cleaning kit."
      - "&7Restores &a15 &7condition."
    Custom_Model_Data: 0                # Custom model data integer for resource pack texture overrides (0 = none)
```

### Restore_Amount vs Restore_Percent

- `Restore_Amount` — adds a flat number of durability points (e.g., `15` always restores exactly 15).
- `Restore_Percent` — adds a percentage of the item's max durability (e.g., `25.0` on a weapon with 100 max = 25
  restored).

When both are non-zero, **the higher resulting value is used**. Set the unused field to `0` / `0.0` to avoid ambiguity.

### Adding a New Repair Material

```yaml
Repair_Materials:
  Duct_Tape:
    Display_Name: "&7Duct Tape"
    Material: STRING
    Uses: 3
    Restore_Amount: 10
    Restore_Percent: 0.0
    Compatible_Types:
      - WEARABLE
    Lore:
      - "&7Quick field patch."
    Custom_Model_Data: 0
```

Any key added under `Repair_Materials` is automatically registered on load.

---

## API

```java
RepairManager repairManager = gangland.getInitializer().getRepairManager();

// Check if an item is a registered repair material
boolean isMaterial = repairManager.isRepairMaterial(itemStack);

// Get the repair material definition
Optional<RepairMaterial> material = repairManager.getRepairMaterial(itemStack);

material.

ifPresent(m ->{
int uses = m.getUses();
int restore = m.getRestoreAmount();             // flat
double restorePercent = m.getRestorePercent();  // percent (0.0 if not set)
List<ItemCategory> compatible = m.getCompatible();
});

// Manually apply a repair
		repairManager.

repair(player, targetItem, repairMaterialItem);
```

---

[← Wearables](./wearables.md) | [Back to Index](../README.md) | [Next: Trade Signs →](../features/trade-signs.md)
