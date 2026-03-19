# Wearables

[← Weapons](../features/weapons.md) | [Back to Index](../README.md) | [Next: Repair System →](./repair.md)

---

## Overview

Wearables are custom armor pieces that sit on top of the vanilla armor system. Unlike vanilla armor, wearables carry
their own configurable **base damage reduction** and can be given one or more **protective traits** that provide
additional specialized protection. Wearables integrate directly into Gangland's damage pipeline, so every hit against a
wearing player is processed through their equipped wearables before final damage is dealt.

Four pieces ship with the plugin out of the box. You can add, modify, or remove any piece in `wearables.yml`.

---

## How Damage Reduction Works

When a player wearing a wearable takes damage, the following order applies:

1. **Vanilla armor** absorbs its share first (standard Minecraft calculation).
2. **Base damage reduction** from the wearable is applied — a flat percentage of the remaining damage is absorbed.
3. Each **trait** on the wearable applies its own reduction to what's left.
4. **Flat damage** from the weapon (if any) is added back on top after all reductions. See
   the [Weapons guide](./weapons.md#flat-damage).

Traits from multiple pieces stack. If you wear both a vest with `REINFORCED 2` and a helmet with `REINFORCED 1`, the
total effect is `REINFORCED 3`.

---

## Traits

| Trait            | Effect Per Level                 | Max Levels | Notes                                                    |
|------------------|----------------------------------|------------|----------------------------------------------------------|
| `REINFORCED`     | 5% general damage reduction      | 4          | Applies to all damage types.                             |
| `BULLETPROOF`    | 4% reduction vs. firearm damage  | 3          | Only activates on projectile hits from Gangland weapons. |
| `PADDED`         | 8% broad physical reduction      | 2          | Applies to melee and fall damage.                        |
| `TOUGHENED`      | 10% critical hit bonus reduction | 3          | Reduces the extra damage from critical strikes.          |
| `FIRE_RESISTANT` | 25% fire damage reduction        | 2          | Applies to burn damage.                                  |
| `REACTIVE`       | 2% chance to fully nullify a hit | 3          | Each level adds an independent 2% nullify roll.          |
| `LIGHTWEIGHT`    | No combat effect                 | 2          | Cosmetic trait. Useful for role-play items.              |

---

## Built-in Pieces

| Name          | Base Reduction | Traits                                                  |
|---------------|----------------|---------------------------------------------------------|
| Police Vest   | 10%            | REINFORCED 2, BULLETPROOF 1                             |
| Police Helmet | 7%             | REINFORCED 1, TOUGHENED 1                               |
| Gang Jacket   | 5%             | REACTIVE 1                                              |
| Heavy Vest    | 15%            | REINFORCED 3, BULLETPROOF 2, TOUGHENED 1, LIGHTWEIGHT 1 |

---

## Configuration

Wearables are defined in `wearables.yml`. Each entry follows this structure:

```yaml
police-vest:
  name: "Police Vest"
  material: LEATHER_CHESTPLATE    # Vanilla item used as the display item
  base-damage-reduction: 0.10     # 10% of damage absorbed before traits apply
  traits:
    REINFORCED: 2                 # Trait name: level
    BULLETPROOF: 1
```

### Adding a New Wearable

```yaml
military-armor:
  name: "Military Chestplate"
  material: IRON_CHESTPLATE
  base-damage-reduction: 0.20
  traits:
    REINFORCED: 4
    BULLETPROOF: 3
    TOUGHENED: 2
```

Any key added under the root of `wearables.yml` is automatically picked up on load. The key (e.g., `military-armor`) is
used as the item's internal ID for giving and referencing.

### Trait Caps

Traits cannot exceed their defined maximum level. Configuring `REINFORCED: 10` on a single item will silently clamp it
to the maximum of 4. The cap is enforced at load time.

---

## Repairing Wearables

Wearables have durability that depletes as damage is absorbed. They can be repaired using materials configured in
`repair.yml`. The **Cleaning Kit** is the only built-in repair material that works on both weapons and wearables. See
the [Repair System guide](./repair.md).

---

## API

```java
// Check if a player is wearing a Gangland wearable in a given slot
boolean wearing = wearableManager.isWearable(player.getInventory().getChestplate());

// Get the wearable data from an item
Optional<Wearable> wearable = wearableManager.getWearable(itemStack);

wearable.ifPresent(w -> {
    double reduction = w.getBaseDamageReduction();         // e.g. 0.10
    Map<WearableTrait, Integer> traits = w.getTraits();    // trait -> level map
    int reinforcedLevel = traits.getOrDefault(WearableTrait.REINFORCED, 0);
});

// Listen for the damage event to intercept wearable processing
@EventHandler
public void onDamage(EntityDamageByEntityEvent event) {
    // Wearable reduction is applied automatically in the pipeline.
    // You do not need to handle it manually.
}
```

---

[← Weapons](../features/weapons.md) | [Back to Index](../README.md) | [Next: Repair System →](./repair.md)
