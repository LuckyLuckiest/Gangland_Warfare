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

Repair materials are defined in `repair.yml`. Each entry maps to a vanilla item and defines its repair behavior.

```yaml
cleaning-kit:
   name: "Cleaning Kit"
   material: PAPER               # Vanilla item that acts as the repair material
   uses: 5                       # How many times this item can be used before disappearing
   restore: 15                   # Flat durability restored per use
   compatible: # What item types this can repair
      - WEAPON
      - WEARABLE

mechanical-part:
   name: "Mechanical Part"
   material: IRON_INGOT
   uses: 1
   restore-percent: 0.25         # Restores 25% of the item's max durability
   compatible:
      - WEAPON
```

### Restore vs Restore Percent

- `restore` — adds a flat amount of durability (e.g., `15` always adds exactly 15).
- `restore-percent` — adds a percentage of the item's maximum durability (e.g., `0.25` on a weapon with 100 max = 25
  durability restored).

Only one of these should be set per material. If both are present, `restore-percent` takes precedence.

### Adding a New Repair Material

```yaml
duct-tape:
   name: "Duct Tape"
   material: STRING
   uses: 3
   restore: 10
   compatible:
      - WEARABLE
```

Any key added to `repair.yml` is automatically registered on load. Players must have the corresponding vanilla item in
their inventory to use it as a repair material.

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
