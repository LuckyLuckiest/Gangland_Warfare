# Item System

[Back to Developer Docs](./README.md)

---

## Overview

The item system spans the `gangland-item` module and parts of `gangland-impl`. It provides custom item
parsing, unique items with controlled inventory behavior, a fuel system for gadgets, and wearable armor
with trait-based damage reduction.

**Module:** `gangland-item`  
**Package:** `me.luckyraven.item.*`

---

## Item Parsing

### ItemParser

Parses item definition strings into `ItemStack` objects using the format:

```
TYPE:modifier{key=value,key2=value2}
```

- Uses regex patterns `ATTRIBUTE_PATTERN` and `KEY_VALUE_PATTERN` for extraction
- Returns `null` on parse failure
- Delegates to registered converters via `ItemConverterRegistry`

### ItemConverter (Interface)

```java
ItemStack convert(String type, String modifier, Map<String, String> attributes);
```

Implementations handle different item categories (weapons, ammo, unique items, etc.).

### ItemConverterRegistry

- `register(String type, ItemConverter)` -- case-insensitive registration
- `getConverter(String type)` -- lookup by type name
- `hasConverter(String type)` -- existence check
- Converters registered at startup for each item category

---

## Unique Items

Unique items are special inventory items with controlled behavior -- they can be pinned to specific
slots, auto-given on join/respawn, prevented from dropping, and more.

### UniqueItem (`@Builder`)

| Field             | Type             | Description                                 |
|-------------------|------------------|---------------------------------------------|
| `permission`      | `String`         | Permission required to receive the item     |
| `uniqueItem`      | `String`         | Unique identifier (stored in NBT)           |
| `material`        | `XMaterial`      | Item material type                          |
| `name`            | `String`         | Display name                                |
| `lore`            | `List<String>`   | Item lore lines                             |
| `addOnJoin`       | `boolean`        | Automatically give on player join           |
| `addOnRespawn`    | `boolean`        | Automatically give on player respawn        |
| `dropOnDeath`     | `boolean`        | Whether the item drops on death             |
| `allowDuplicates` | `boolean`        | Allow multiple copies in inventory          |
| `inventorySlot`   | `int`            | Pinned inventory slot (-1 for any)          |
| `overridesSlot`   | `boolean`        | Force placement in pinned slot              |
| `movable`         | `boolean`        | Whether the player can move it in inventory |
| `droppable`       | `boolean`        | Whether the player can drop it              |
| `lootKey`         | `String`         | Loot table key for loot chest integration   |
| `fuel`            | `Optional<Fuel>` | Optional fuel component                     |

**Key Methods:**

- `buildItem()` -- creates ItemStack and stamps NBT tags (`UNIQUE_ITEM_KEY`)
- `addItemToInventory(Player)` -- adds to player inventory respecting slot rules
- `addItem()` -- recursive slot-finding for placement
- `createItem()` -- creates the raw ItemStack
- Implements `Comparable<ItemStack>` for sorting by name/material

### UniqueItemKeys

```java
public static final String UNIQUE_ITEM_KEY = "uniqueItem";
```

NBT tag used to identify unique items in any ItemStack.

### UniqueItemUtil

Utility methods for unique item detection and extraction from ItemStack instances.

### Built-in Unique Items (configured in `unique_items.yml`)

| Item          | Slot | On Join | On Respawn | Droppable | Description                   |
|---------------|------|---------|------------|-----------|-------------------------------|
| Phone         | Last | Yes     | Yes        | No        | Permanent hotbar item         |
| Lockpick      | Any  | No      | No         | Yes       | Consumable for Rare chests    |
| Epic Key      | Any  | No      | No         | Yes       | Required for Epic chests      |
| Legendary Key | Any  | No      | No         | Yes       | Required for Legendary chests |

---

## Fuel System

The fuel system provides consumable fuel for cars and jetpacks, tracked via NBT tags on items.

### Fuel (`@Builder`)

| Field          | Type        | Description                       |
|----------------|-------------|-----------------------------------|
| `fuelKey`      | `String`    | Unique fuel identifier            |
| `maxFuel`      | `int`       | Maximum fuel capacity             |
| `displayName`  | `String`    | Display name for fuel items       |
| `fuelMaterial` | `XMaterial` | Material type for fuel items      |
| `fuelPerItem`  | `int`       | Fuel units restored per item used |

**NBT Tags (FuelKey enum):**

| Tag            | Key              | Description           |
|----------------|------------------|-----------------------|
| `FUEL_ID`      | `"fuel"`         | Fuel type identifier  |
| `FUEL_CURRENT` | `"fuel_current"` | Current fuel level    |
| `FUEL_MAX`     | `"fuel_max"`     | Maximum fuel capacity |

**Static Helpers:**

```java
Fuel.isFuelItem(ItemStack)        // checks for FUEL_ID NBT tag
Fuel.getFuelKey(ItemStack)        // reads fuel type from NBT
Fuel.getCurrentFuel(ItemStack)    // reads current fuel from NBT
Fuel.getMaxFuel(ItemStack)        // reads max fuel from NBT
Fuel.setCurrentFuel(ItemStack, int) // writes current fuel to NBT
```

**Raw NBT Methods:**

```java
Fuel.hasFuelCapacity(ItemStack)   // checks for fuel tags
Fuel.readFuelCurrent(ItemStack)   // raw NBT read
Fuel.readFuelMax(ItemStack)       // raw NBT read
Fuel.writeFuelCurrent(ItemStack, int) // raw NBT write
```

**Instance Method:**

```java
fuel.stampNBT(ItemBuilder)        // applies all fuel tags to an item
```

### FuelBar

Renders an action bar fuel gauge for the player:

```
⛽ Fuel ||||||||||||          42/100
         ^green^    ^gray^
```

- Fixed 20-segment bar
- Green segments = filled, gray = empty
- Static render function: `FuelBar.render(current, max)`

---

## Wearable System

Wearables are custom armor pieces that integrate with the weapon damage pipeline, providing
trait-based damage reduction on top of vanilla armor.

### Wearable (`@Builder`, immutable)

| Field                 | Type                          | Description                    |
|-----------------------|-------------------------------|--------------------------------|
| `permission`          | `String`                      | Permission to equip            |
| `material`            | `XMaterial`                   | Armor material                 |
| `name`                | `String`                      | Display name                   |
| `lore`                | `List<String>`                | Item lore                      |
| `baseDamageReduction` | `double`                      | Base % damage reduction        |
| `traits`              | `Map<WearableTrait, Integer>` | Trait type to level mapping    |
| `leatherColor`        | `Color`                       | Leather armor color (nullable) |
| `temporary`           | `boolean`                     | Temporary wearable flag        |

**Jetpack-specific fields (when wearable is a jetpack):**

| Field                 | Type     | Description                 |
|-----------------------|----------|-----------------------------|
| `fuelKey`             | `String` | Fuel type for jetpack       |
| `fuelConsumptionRate` | `double` | Fuel consumed per tick      |
| `ascendPower`         | `double` | Upward thrust strength      |
| `glideDescentRate`    | `double` | Descent speed while gliding |
| `maxSpeedY`           | `double` | Maximum vertical speed      |
| `maxFuel`             | `int`    | Fuel tank capacity          |
| `sounds`              | `Map`    | Sound effects               |

**NBT Constants:**

```java
NBT_KEY          = "wearable"      // identifies as wearable
NBT_TRAIT_PREFIX = "wt_"           // prefix for trait tags
NBT_BASE_REDUCE  = "wr_base"      // base reduction tag
```

**Static Methods:**

```java
Wearable.fromItemStack(ItemStack)            // deserialize from NBT
Wearable.getEnchantmentGenericBonus(ItemStack) // enchant bonus calc
Wearable.isArmorItem(ItemStack)              // check if wearable
```

### WearableTrait (Enum)

| Trait            | Effect                         |
|------------------|--------------------------------|
| `REINFORCED`     | Extra flat damage reduction    |
| `BULLETPROOF`    | Reduced projectile damage      |
| `PADDED`         | Reduced melee/blunt damage     |
| `TOUGHENED`      | General toughness bonus        |
| `FIRE_RESISTANT` | Reduced fire/incendiary damage |
| `REACTIVE`       | Damage reflection on attacker  |
| `LIGHTWEIGHT`    | Movement speed bonus           |

Traits from multiple wearable pieces **stack** for combined protection.

---

## Repair Interface

The `Repairable` interface provides the contract for items that can be repaired.

```java
public interface Repairable {
    String getRepairableId();
    int getCurrentRepairDurability();
    void setCurrentRepairDurability(int durability);
    int getMaxRepairDurability();
    RepairableType getRepairableType();
    ItemStack buildItem();
    boolean isFullyRepaired();
    boolean canBeRepaired();
}
```

### RepairableType (Enum)

Categorizes repairable items for material compatibility:

- `WEAPON` -- weapon items only
- `WEARABLE` -- wearable armor only
- `BOTH` -- either type

---

## ItemBuilder (gangland-core)

Fluent builder for creating ItemStacks with NBT support.

```java
ItemBuilder item = new ItemBuilder(Material.DIAMOND_SWORD)
    .setDisplayName("&cFlame Sword")
    .setLore(List.of("&7A legendary weapon"))
    .addEnchantment(Enchantment.FIRE_ASPECT, 2)
    .addItemFlags(ItemFlag.HIDE_ENCHANTS)
    .modifyNBT(nbt -> nbt.setString("weapon_id", "flame_sword"));

ItemStack stack = item.build();
```

**Key Methods:**

- `setDisplayName(String)` -- set colored display name
- `setLore(List<String>)` -- set lore lines
- `addEnchantment(Enchantment, int)` -- add enchantment
- `addItemFlags(ItemFlag...)` -- add item flags
- `modifyNBT(Consumer<NBTCompound>)` -- direct NBT manipulation
- `hasNBTTag(String)` / `getStringTagData(String)` -- NBT reads
- `build()` -- create final ItemStack
