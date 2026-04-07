# Weapon System -- Developer Documentation

> **Module:** `gangland-features/gangland-weapon`
> **Package root:** `me.luckyraven.weapon`
> **Class count:** 83

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture Diagram](#architecture-diagram)
3. [Core Classes](#core-classes)
4. [Weapon Types](#weapon-types)
5. [Projectile System](#projectile-system)
6. [Ammunition System](#ammunition-system)
7. [Modifier System](#modifier-system)
8. [Reload System](#reload-system)
9. [Shooting Mechanics](#shooting-mechanics)
10. [Weapon Interaction](#weapon-interaction)
11. [Configuration and Parsing](#configuration-and-parsing)
12. [Events](#events)
13. [Listeners](#listeners)
14. [Damage Pipeline](#damage-pipeline)
15. [Wearable / Armor Reduction](#wearable--armor-reduction)
16. [NBT Tag System](#nbt-tag-system)
17. [Durability System](#durability-system)

---

## Overview

The weapon system is a fully custom combat engine that replaces vanilla Minecraft combat.
It is self-contained in the `gangland-weapon` module and exposes an event-driven API for
other modules to hook into.

**Key facts:**

- 6 weapon types: `GUN`, `MELEE`, `THROWABLE`, `INCENDIARY`, `BIOLOGICAL`, `OTHER`
- 4 projectile types: `BULLET`, `SPREAD`, `FLARE`, `ROCKET`
- 6 modifier types: Armor Piercing, Block Break, Penetration, Ricochet, Tracer, Flat Damage
- 3 selective fire modes: `AUTO`, `BURST`, `SINGLE`
- 2 reload strategies: `InstantReload`, `NumberedReload`
- 10 custom Bukkit events
- 7 listeners handling all player/projectile interactions
- 15 DTO classes for weapon property groups
- 5 type-specific YAML parsers

All vanilla damage from weapon items is cancelled. Damage is calculated and applied
programmatically through the weapon pipeline.

---

## Architecture Diagram

```
                          +---------------------+
                          |    WeaponAddon       |  <-- YAML config loader
                          |  (registers weapons) |
                          +---------+-----------+
                                    |
                                    v
+-------------------+     +-------------------+     +---------------------+
|  AmmunitionAddon  |---->| AmmunitionManager |<----| AmmunitionSection   |
| (ammunition.yml)  |     | (ammo registry)   |     |     Parser          |
+-------------------+     +-------------------+     +---------------------+
                                    |
                                    v
                          +-------------------+
                          |  WeaponService     |  <-- Runtime weapon registry
                          |  (UUID -> Weapon)  |      Validates held items
                          +---------+---------+      Provides weapon lookups
                                    |
                    +---------------+----------------+
                    |               |                 |
                    v               v                 v
          +-------------+  +--------------+  +----------------+
          | WeaponInteract| | ProjectileDamage| | WeaponReload   |
          |  (clicks)    | |   Listener    | |   Listener     |
          +------+------+  +------+-------+  +-------+-------+
                 |                 |                   |
                 v                 v                   v
          +-----------+    +-------------+    +--------------+
          |  *Action   |    | ModifierHandler|    |   Reload     |
          | (per type) |    | (damage calc) |    | (Instant/Num)|
          +-----------+    +-------------+    +--------------+
                 |
                 v
          +-----------+
          | *Weapon    |
          | (per type) |
          +-----------+
```

---

## Core Classes

### `Weapon` (abstract, 479 lines)

**Path:** `me.luckyraven.weapon.Weapon`

The base data class for all weapons. Extends no Bukkit class; implements `Repairable`,
`Cloneable`, and `Comparable<Weapon>`.

**Key fields:**

| Field                  | Type                     | Description                                         |
|------------------------|--------------------------|-----------------------------------------------------|
| `name`                 | `String`                 | Internal config ID (filename without ext)           |
| `displayName`          | `String`                 | Color-coded display name                            |
| `category`             | `WeaponType`             | GUN, MELEE, THROWABLE, etc.                         |
| `material`             | `Material`               | Bukkit item material                                |
| `durability`           | `short`                  | Max durability (weapon-level, not item-level)       |
| `currentMagCapacity`   | `int`                    | Rounds currently in the magazine                    |
| `currentSelectiveFire` | `SelectiveFire`          | AUTO, BURST, or SINGLE                              |
| `uuid`                 | `UUID`                   | Unique runtime instance ID                          |
| `tags`                 | `Map<WeaponTag, Object>` | NBT tag values (UUID, weapon name, ammo, fire mode) |

**Key configuration DTOs (set post-construction by `WeaponAddon`):**

| DTO                   | Purpose                                                     |
|-----------------------|-------------------------------------------------------------|
| `DurabilityData`      | On-shot loss, on-repair gain, consume-on-time               |
| `SoundData`           | Shot, empty-mag, reload, scope, flyby, impact               |
| `ReloadActionBarData` | Messages shown during reload ("Reloading...", "Opening...") |
| `ModifiersData`       | All modifier instances (piercing, penetration, etc.)        |
| `RecoilData`          | Recoil amount, push velocity, recoil pattern                |
| `ScopeData`           | Slowness level, scoped state                                |
| `SpreadData`          | Starting spread, change rate, bounds, reset                 |

**Key runtime managers (non-serialized, recreated on clone):**

| Manager                | Purpose                                       |
|------------------------|-----------------------------------------------|
| `DurabilityCalculator` | Maps weapon durability to item durability bar |
| `Reload`               | Manages reload state machine                  |
| `RecoilManager`        | Tracks recoil pattern index per weapon        |
| `SpreadManager`        | Tracks spread accumulation per weapon         |

**Key methods:**

```java
// Scope toggle
void scope(Player player, boolean bypass)
void unScope(Player player, boolean bypass)

// Magazine management
boolean consumeShot()          // Deducts 1 round; overridden by GunWeapon/IncendiaryWeapon
boolean isMagazineEmpty()
boolean isMagazineFull()
void    addAmmunition(int amount)
boolean requiresReload()

// Reload delegation
void    reload(JavaPlugin plugin, Player player, boolean removeAmmunition)
void    stopReloading()
boolean isReloading()

// Item building
ItemStack buildItem()          // Creates the ItemStack with NBT tags
void updateWeaponData(ItemBuilder itemBuilder)  // Syncs ammo/fire mode to NBT
void updateWeapon(Player player, ItemBuilder itemBuilder, int slot)
void removeWeapon(Player player, int slot)

// Durability
void    increaseDurability(ItemBuilder, int amount)
void    decreaseDurability(ItemBuilder, int amount)
boolean isBroken()
void    applyOnHitDurability(Player player, int slot)

// Push (recoil knockback on shooter)
void applyPush(Player player)  // Only when grounded, reduced when sneaking/scoped

// Cloning
abstract Weapon copyWithUUID(UUID newUuid)
Weapon clone()                 // Deep-copies all mutable DTOs
```

**Display name format with ammo counter:**

```
&6AK-47&r &8<<&6 24&7/&6 30 &8>>&r
```

### `WeaponService` (abstract, 244 lines)

**Path:** `me.luckyraven.weapon.WeaponService`

Central runtime registry mapping `UUID -> Weapon`. Every active weapon instance lives here.

**Key methods:**

```java
// Weapon identification
static UUID getWeaponUUID(ItemStack item)  // Reads UUID from NBT
String getHeldWeaponName(ItemStack item)   // Reads "weapon" tag from NBT
boolean isWeapon(ItemStack item)           // Checks UUID exists in registry

// Weapon retrieval
Weapon getWeapon(String type)                     // By config name
Weapon getWeapon(Player, UUID, String, boolean)   // Full lookup with new-instance flag
Weapon validateAndGetWeapon(Player, ItemStack)     // Complete validation from held item

// Ammunition check
boolean hasAmmunition(Player player, Weapon weapon)

// Utility
ItemBuilder getHeldWeaponItem(Player player)       // Checks main hand, then off hand
boolean isHeadPosition(Location l1, Location l2)   // Y difference > 1.4 = headshot
void clear()
```

**Weapon resolution flow:**

```
1. Read UUID from item NBT
2. Look up UUID in weapons map
   a. Found -> return existing, sync NBT data (ammo, durability, fire mode)
   b. Not found -> look up config name in WeaponAddon
      -> Clone weapon template
      -> Assign UUID (deterministic for throwables, random for others)
      -> Register in weapons map
      -> Sync NBT data from item
```

**Throwable UUID determinism:**

Throwable weapons share a single UUID per weapon type (generated via
`UUID.nameUUIDFromBytes("throwable:<type>")`) so identical throwable items stack
in the player's inventory.

### `WeaponTag` (enum)

**Path:** `me.luckyraven.weapon.WeaponTag`

Defines the NBT keys written to weapon ItemStacks:

| Tag              | Type    | NBT Key          | Description                  |
|------------------|---------|------------------|------------------------------|
| `UUID`           | STATIC  | `uuid`           | Weapon instance identifier   |
| `WEAPON`         | STATIC  | `weapon`         | Config name (e.g., "ak47")   |
| `SELECTIVE_FIRE` | DYNAMIC | `selective-fire` | Current fire mode            |
| `AMMO_LEFT`      | DYNAMIC | `ammo-left`      | Rounds remaining in magazine |

STATIC tags are set once at creation. DYNAMIC tags are updated after every shot/reload/mode change.

### `SelectiveFire` (enum)

**Path:** `me.luckyraven.weapon.SelectiveFire`

Three fire modes with cyclic state transition:

```
AUTO --> BURST --> SINGLE --> AUTO --> ...
```

```java
SelectiveFire.getType("auto")       // parse from config string
selectiveFire.getNextState()        // cycle to next mode
```

---

## Weapon Types

### Type Hierarchy

```
                     Weapon (abstract)
                        |
         +--------------+---------------+------------------+------------------+
         |              |               |                  |                  |
      GunWeapon    MeleeWeapon   ThrowableWeapon    IncendiaryWeapon   BiologicalWeapon
         |              |               |                  |                  |
      GunAction    MeleeAction   ThrowableAction    IncendiaryAction   BiologicalAction
```

Each weapon type has:

- A **Weapon subclass** holding type-specific configuration data
- An **Action class** containing the activation/combat logic

### `WeaponType` (enum)

```java
GUN, MELEE, THROWABLE, INCENDIARY, BIOLOGICAL, OTHER
```

Parsed from config strings with aliases:

- `"throwable"`, `"throw"`, `"grenade"`, `"projectile"`, `"proj"` -> `THROWABLE`
- `"incendiary"`, `"fire"` -> `INCENDIARY`
- `"biological"`, `"biology"`, `"bio"` -> `BIOLOGICAL`

---

### 1. GunWeapon / GunAction

**The standard firearm.** Fires projectile entities with configurable ballistics.

**GunWeapon** adds these fields to Weapon:

| Field                  | Type             | Description                                |
|------------------------|------------------|--------------------------------------------|
| `projectileData`       | `ProjectileData` | Speed, type, damage, cooldown, distance    |
| `weaponConsumedOnShot` | `int`            | Remove weapon item after N shots (0=never) |
| `damageData`           | `DamageData`     | Explosion, fire, head, critical hit        |

**ProjectileData fields:**

| Field      | Type             | Description                                        |
|------------|------------------|----------------------------------------------------|
| `speed`    | `double`         | Projectile velocity multiplier                     |
| `type`     | `ProjectileType` | BULLET, SPREAD, FLARE, or ROCKET                   |
| `damage`   | `double`         | Base projectile damage                             |
| `consumed` | `int`            | Ammo consumed per shot                             |
| `perShot`  | `int`            | Projectiles fired per shot (burst count for BURST) |
| `cooldown` | `int`            | Ticks between shots                                |
| `distance` | `int`            | Max projectile travel distance (blocks)            |
| `particle` | `boolean`        | Whether to show a dust particle trail              |

**DamageData fields:**

| Field               | Type     | Description                               |
|---------------------|----------|-------------------------------------------|
| `explosionDamage`   | `double` | AoE damage for explosive projectiles      |
| `fireTicks`         | `int`    | Fire duration applied to hit entity       |
| `headDamage`        | `double` | Bonus damage for headshots (Y diff > 1.4) |
| `criticalHitChance` | `int`    | Percentage chance (0-100)                 |
| `criticalHitDamage` | `double` | Bonus damage on critical hit              |

**GunAction.weaponShoot(Player) flow:**

```
1. Get held weapon item
2. Check if weapon is broken -> play empty sound, show "Broken" action bar
3. consumeShot() -> deducts projectileData.consumed from magazine
4. Create WeaponProjectile via ProjectileType.createInstance()
5. Fire WeaponShootEvent (cancellable)
6. If not cancelled -> launchProjectile()
7. Update weapon NBT (ammo counter in display name)
8. Apply durability loss (durabilityData.onShot)
9. Update item in player inventory
10. Apply recoil via RecoilManager
11. Apply push (knockback on shooter)
12. Play shot sound at shooter's location
```

**GunWeapon overrides `consumeShot()`** to deduct `projectileData.consumed` per shot
instead of the default 1.

---

### 2. MeleeWeapon / MeleeAction

**Instant-hit melee weapons** with swing arc detection and no projectiles.

**MeleeData fields:**

| Field       | Type     | Description                                   |
|-------------|----------|-----------------------------------------------|
| `damage`    | `double` | Base swing damage                             |
| `range`     | `double` | Detection radius for nearby entities (blocks) |
| `cooldown`  | `int`    | Minimum ticks between swings                  |
| `knockback` | `double` | Knockback velocity applied to hit targets     |

**MeleeAction.activate(Player) flow:**

```
1. Check ammo (if configured) -> play empty sound if empty
2. Check cooldown (ms-based per weapon UUID)
3. Calculate look direction
4. For each nearby entity within range:
   a. Check if within ~60-degree arc (dot product > 0.5)
   b. Apply damage:
      - If armor piercing: split into armored + unarmored portions
      - Otherwise: full damage with attacker attribution
   c. Apply knockback along look direction
5. Spawn slash arc particle effect (always, even on miss)
6. Play swing sound
7. Apply recoil
```

**Entity damage bypass:** MeleeAction uses a static `pendingDamage` set (`ConcurrentHashMap.newKeySet()`)
to whitelist entities about to receive programmatic damage. `WeaponInteract.onEntityDamage` checks
this set and allows the damage through instead of cancelling it.

---

### 3. ThrowableWeapon / ThrowableAction

**Grenades and throwable explosives** with physics simulation.

**ThrowableData fields:**

| Field             | Type      | Description                                |
|-------------------|-----------|--------------------------------------------|
| `fuseTime`        | `int`     | Ticks before detonation                    |
| `explosionRadius` | `double`  | Blast radius in blocks                     |
| `explosionDamage` | `int`     | Damage at epicenter                        |
| `fireTicks`       | `int`     | Fire duration applied to entities in blast |
| `bounces`         | `boolean` | Whether grenade bounces on landing         |
| `maxBounces`      | `int`     | Maximum number of bounces                  |
| `sticky`          | `boolean` | Whether grenade sticks to surfaces         |
| `entityType`      | `String`  | Reserved for future entity-type grenades   |

**ThrowableAction.activate(Player) flow:**

```
1. Decrement held item stack (remove 1 from hand)
2. Drop a Material item entity at eye location
3. Apply throw velocity (direction * 1.2 + upward 0.2)
4. Play throw sound, apply recoil
5. Start physics timer (1 tick interval):
   a. Track ground contact, bouncing, sticky collision
   b. Sticky: freeze on any surface contact (floor, wall, ceiling)
   c. Bouncing: reduce velocity by 0.65^bounceCount per bounce
   d. Spawn smoke trail particles
6. Start fuse countdown timer
7. On fuse expiry:
   a. Remove grenade entity
   b. Detonate at grenade location
```

**Detonation logic:**

```
1. Spawn explosion burst particles
2. Calculate total damage = explosionDamage + flatDamage modifier
3. Pre-register vehicle entities for CarDamageListener
4. Create vanilla explosion (for block damage and sound)
5. Damage all LivingEntities within radius (with attacker attribution)
6. Apply fire ticks to entities in blast
7. Self-damage: player takes damage + knockback if within radius
   - Knockback scales with distance (closer = stronger)
   - Direct center hit: straight upward velocity of 2.0
```

**Static maps for cross-listener communication:**

| Map                             | Purpose                                           |
|---------------------------------|---------------------------------------------------|
| `pendingDamage`                 | Bypass WeaponInteract's cancel guard              |
| `pendingKillerWeapon`           | Map victim UUID to weapon name for death messages |
| `pendingVehicleExplosionDamage` | Override vanilla explosion damage for vehicles    |

---

### 4. IncendiaryWeapon / IncendiaryAction

**Flamethrower-style weapons** with continuous fire spray using ray-trace hit detection.

**IncendiaryData fields:**

| Field          | Type     | Description                        |
|----------------|----------|------------------------------------|
| `coneAngle`    | `double` | Spray cone angle in degrees        |
| `range`        | `double` | Maximum fire range in blocks       |
| `fireDuration` | `int`    | Fire ticks applied to hit entities |
| `tickRate`     | `int`    | Ticks between spray pulses         |
| `consumeRate`  | `int`    | Fuel consumed per spray tick       |

**IncendiaryAction behavior:**

- **Toggle on/off:** Right-click starts spraying, left-click stops (or right-click again toggles off)
- **Continuous spray:** A `RepeatingTimer` runs at `tickRate` intervals
- **Hit detection:** Multiple randomized rays within a cone, using `World.rayTraceEntities()`
- **Fuel tracking:** Uses `weapon.getCurrentMagCapacity()` with `consumeShot()` per tick
- **Vehicle damage:** Fires `WeaponEntityDamageEvent` for non-living entities in the cone

**Per-tick spray flow:**

```
1. Get held weapon item (stop if weapon switched away)
2. Consume fuel (ammo)
3. Update display name ammo counter
4. Apply durability loss
5. Update item in inventory
6. Apply recoil and push
7. Compute muzzle position (offset right of eye, slightly down)
8. Spawn flame cone particles
9. Ray-trace: cast multiple random rays within cone angle
10. Apply fire ticks and attributed damage to hit entities
11. Fire WeaponEntityDamageEvent for vehicles in cone
```

**IncendiaryWeapon overrides `consumeShot()`** to deduct `incendiaryData.consumeRate` instead
of the default 1.

---

### 5. BiologicalWeapon / BiologicalAction

**Charge-and-release area-effect weapons** that apply potion effects scaled by charge level.

**BiologicalData fields:**

| Field                | Type           | Description                                |
|----------------------|----------------|--------------------------------------------|
| `chargeTimePerLevel` | `int`          | Ticks per charge level                     |
| `maxChargeLevel`     | `int`          | Maximum achievable charge                  |
| `effectsPerLevel`    | `List<String>` | Potion effects per level (comma-separated) |
| `areaRadius`         | `double`       | Effect radius in blocks                    |

**Effect format per level:**

```
"POISON-100-2,SLOWNESS-60-1"
  ^effect  ^dur ^amp
```

**BiologicalAction behavior:**

- **Right-click:** Start charging (or fire if already charging)
- **Left-click:** Fire at current charge level
- **Charge display:** Action bar shows `Charging... [####____]` with growing ring particles
- **Release:** Applies potion effects + flat damage to all entities in radius
- **Beam effect:** Particle beam drawn from player to each affected entity

**Charge-and-fire flow:**

```
1. Right-click: start charging timer (1 tick interval)
   - Increment charge level every chargeTimePerLevel ticks
   - Show action bar progress and charge ring particles
2. Right-click again (or left-click): fire at current level
   - Stop charge timer
   - Parse potion effects for current level
   - consumeShot()
   - Play release sound, apply recoil
   - For each entity in areaRadius:
     a. Apply parsed potion effects
     b. Apply flat damage bonus (if configured)
     c. Draw beam particle to target
   - Fire WeaponEntityDamageEvent for vehicles
   - Spawn area pulse particle effect
   - Show "Released at charge level X" on action bar
```

---

## Projectile System

### `WProjectile` (abstract base)

**Path:** `me.luckyraven.weapon.projectile.WProjectile`

Low-level projectile tracking with location, velocity, distance, and environment awareness.

```java
abstract void   launchProjectile()
abstract double getSpeed()
double getGravity()              // 0.05 default
double getDomainDrag()           // 0.96 in water, 0.98 in storm, 0.99 normal
Block  getCurrentBlock()
int    getMaxAliveTicks()        // 600 (30 seconds)
```

### `WeaponProjectile<T extends Projectile>` (abstract)

**Path:** `me.luckyraven.weapon.projectile.WeaponProjectile`

Wraps a Bukkit `Projectile` entity. `T` is the entity type (Snowball, Fireball, Firework).

**`launchProjectile()` flow:**

```
1. Calculate weapon position:
   - Eye location + right offset (0.3 blocks) + down offset (0.2 blocks)
   - This places the projectile at the player's weapon hand, not center
2. Spawn projectile entity at weapon position
   - silent=true, gravity=false, shooter=player
3. Apply spread via SpreadManager
4. Set velocity = spread-adjusted direction * speed
5. If particle mode: spawn dust particle line from muzzle to max distance
6. Fire WeaponProjectileLaunchEvent
7. Start flyby sound check (RepeatingTimer, 1 tick):
   - For each nearby player within flybyRange
   - Play flyby sound once per player (tracked via Set<UUID>)
```

### Projectile Types

#### `Bullet` (extends `WeaponProjectile<Snowball>`)

Standard fast projectile. Uses Snowball entity for minimal visual footprint.
No gravity, straight-line travel.

#### `Spread` (extends `WeaponProjectile<Snowball>`)

Multi-projectile shotgun pattern. Overrides `launchProjectile()` to fire
`pelletsCount` (default 8) individual projectiles, each with independent spread.

```java
@Override
public void launchProjectile() {
    for (int i = 0; i < pelletsCount; i++) {
        super.launchProjectile();  // Each gets its own spread offset
    }
}
```

#### `Flare` (extends `WeaponProjectile<Firework>`)

Visible tracer projectile using a Firework entity for bright visibility.

#### `Rocket` (extends `WeaponProjectile<Fireball>`)

Explosive projectile using a Fireball entity. Adds:

- **Smoke trail:** Repeating particle effect (smoke + flame + lava sparks)
- **Explosion on impact:** Creates visual/sound explosion with configurable power
- **Homing capability:** Optional target-tracking with configurable strength

```java
void startSmokeTrail(Fireball rocket)        // Continuous particle trail
void createExplosion(Location, float power)   // Explosion with particles + sound
void startHoming(LivingEntity target, double homingStrength)  // Lock-on guidance
```

### `ProjectileType` (enum)

Factory enum that creates the correct `WeaponProjectile` subclass:

```java
BULLET  -> new Bullet(plugin, shooter, weapon)
SPREAD  -> new Spread(plugin, shooter, weapon)
FLARE   -> new Flare(plugin, shooter, weapon)
ROCKET  -> new Rocket(plugin, shooter, weapon)
```

### `ProjectileState`

**Path:** `me.luckyraven.weapon.projectile.ProjectileState`

Tracks the runtime state of a single active projectile for modifier calculations:

| Field                     | Type     | Description                                  |
|---------------------------|----------|----------------------------------------------|
| `baseDamage`              | `double` | Original damage from ProjectileData          |
| `blocksPenetrated`        | `int`    | Number of blocks passed through              |
| `entitiesPenetrated`      | `int`    | Number of entities passed through            |
| `bounceCount`             | `int`    | Number of ricochets performed                |
| `currentDamageMultiplier` | `double` | Cumulative damage multiplier (starts at 1.0) |

**Key methods:**

```java
double getCurrentDamage()                       // baseDamage * currentDamageMultiplier
void   applyPenetrationReduction(double reduction) // multiplier *= (1.0 - reduction)
void   applyRicochetReduction(double retention)    // multiplier *= retention
boolean canPenetrateBlock()    // Check against modifiers limit
boolean canPenetrateEntity()   // Check against modifiers limit
boolean canRicochet()          // Check against max bounces
```

### `BlockDamageManager`

**Path:** `me.luckyraven.weapon.projectile.BlockDamageManager`

Manages progressive block crack animations from projectile impacts.

**Constants:**

| Constant                   | Value | Description                             |
|----------------------------|-------|-----------------------------------------|
| `REGENERATION_DELAY_TICKS` | 100   | Ticks before crack starts healing       |
| `REGENERATION_STEP_TICKS`  | 4     | Ticks between each crack-heal step      |
| `MAX_DAMAGE_STAGE`         | 9     | Maximum Minecraft crack animation stage |

**Flow:**

```
1. Projectile hits block with BlockBreakModifier
2. BlockDamageManager.applyDamage(block, modifier):
   a. Get or create BlockDamageState for this location
   b. Cancel any ongoing regeneration
   c. Increment hit count
   d. Calculate crack stage = (hits * 9) / hitsRequired
   e. Send block damage animation to nearby players (sendBlockDamage)
   f. If hits >= hitsRequired:
      - Breakable material (glass, ice, etc.) -> break block, play effects
      - Non-breakable -> stay at max crack, schedule regen
   g. Schedule smooth regeneration (delay + step-by-step crack reduction)
```

**Breakable materials:** Glass, Glass Pane, Ice variants, Glowstone, Sea Lantern,
Redstone Lamp, Melon, Pumpkin, Terracotta variants.

---

## Ammunition System

### `Ammunition`

**Path:** `me.luckyraven.weapon.ammo.Ammunition`

Represents an ammo type (e.g., "9mm", "Shotgun Shells").

| Field         | Type           | Description                       |
|---------------|----------------|-----------------------------------|
| `name`        | `String`       | Internal config key               |
| `displayName` | `String`       | Color-coded display name          |
| `material`    | `Material`     | Bukkit material for the ammo item |
| `lore`        | `List<String>` | Item lore lines                   |

**Key methods:**

```java
static boolean isAmmunition(ItemStack item)  // Checks for "ammo" NBT tag
ItemStack buildItem()                        // Creates ammo ItemStack with NBT
ItemStack buildItem(int amount)              // Creates stack of given size
```

### `AmmunitionData` (DTO)

Links an `Ammunition` type to a weapon's magazine configuration:

| Field            | Type         | Description                                   |
|------------------|--------------|-----------------------------------------------|
| `ammoType`       | `Ammunition` | The ammo type this weapon uses                |
| `maxMagCapacity` | `int`        | Maximum rounds in magazine                    |
| `consumeRate`    | `int`        | Ammo items consumed from inventory per reload |
| `restore`        | `int`        | Rounds added to magazine per reload cycle     |

### `AmmunitionManager`

Simple registry mapping config keys to `Ammunition` instances. Loaded by
`AmmunitionAddon` from `ammunition.yml`.

```java
void register(String key, Ammunition ammo)
Ammunition getAmmunition(String key)
Set<String> getAmmunitionKeys()
```

---

## Modifier System

All modifiers are stored in `ModifiersData` and applied by `ModifierHandler` (static utility class).

### `ModifiersData` (DTO)

Container for all modifier instances on a weapon:

```java
List<BlockBreakModifier> breakBlocks    // Multiple block-type entries
PenetrationModifier      penetration    // Single instance
List<RicochetModifier>   ricochets      // Multiple surface-type entries
TracerModifier           tracer         // Single instance
ArmorPiercingModifier    armorPiercing  // Single instance
FlatDamageModifier       flatDamage     // Single instance
```

### Individual Modifiers

#### 1. `ArmorPiercingModifier` (record)

Bypasses a percentage of the target's armor.

```java
record ArmorPiercingModifier(double armorBypass)
// armorBypass: 0.0 (no bypass) to 1.0 (full bypass)

double calculateEffectiveArmor(double armor)
// Returns: armor * (1.0 - armorBypass)
```

**Applied in `ModifierHandler.calculateArmorPiercingDamage()`:**
Uses Minecraft's armor reduction formula `damage * (1 - min(20, armor) / 25)` but
with effective armor reduced by the bypass percentage.

#### 2. `BlockBreakModifier` (record)

Destroys blocks after repeated projectile hits.

```java
record BlockBreakModifier(
    Set<Material> targetMaterials,  // Materials this modifier affects
    int hitsRequired,               // Hits to reach max damage / break
    boolean actuallyBreaks          // Whether block breaks or just shows max crack
)
```

Config format: `"GLASS-3"` (material group - hits required).
Supports `BlockGroupResolver` for material groups (e.g., `GLASS` expands to all glass variants).

#### 3. `PenetrationModifier` (record)

Allows projectiles to pass through blocks and entities with damage falloff.

```java
record PenetrationModifier(
    int penetrateBlocks,      // Max blocks to pass through
    int penetrateEntities,    // Max entities to damage and continue
    double damageReduction    // Damage lost per penetration (0.0 - 1.0)
)
```

Config format: `"2-3-0.25"` (blocks-entities-reduction).

**Penetrable blocks:** Glass, panes, leaves, fences, bars, chains, carpet, banners, signs,
candles, flowers, plants, grass, vines, moss, cobweb, snow, sugar cane, bamboo, scaffolding, ladder.

#### 4. `RicochetModifier` (record)

Allows projectiles to bounce off surfaces.

```java
record RicochetModifier(
    int maxBounces,                 // Maximum number of ricochets
    Set<Material> bounceOffBlocks,  // Valid surface materials (empty = all)
    double damageRetention          // Damage kept per bounce (0.0 - 1.0)
)
```

Config format: `"3-STONE,IRON_BLOCK-0.8"` (bounces-materials-retention).

**Reflection formula:** `R = V - 2(V . N)N` where N is the block face normal.

**Ricochet effects:** CRIT particles + anvil sound at impact point.

#### 5. `TracerModifier` (record)

Adds colored particle trails to projectiles.

```java
record TracerModifier(
    Color color,          // RGB particle color
    boolean glowing,      // Whether projectile glows
    float particleSize    // Size of dust particles
)
```

Config format: `"FF0000-true-1.0"` (hex color-glowing-size).

Spawns `DUST` particles along the projectile trajectory (2 particles per block).

#### 6. `FlatDamageModifier` (record)

Adds a flat damage bonus after all other modifiers.

```java
record FlatDamageModifier(double bonus)
```

Config format: `"5.0"` (bonus damage).

Applied last in the damage pipeline, after armor reduction, so armor cannot absorb this bonus.

### `ModifierHandler` (static utility)

Central class for applying modifier effects. All methods are static.

**Key methods:**

```java
// Damage calculation
static double calculateArmorPiercingDamage(double baseDamage, LivingEntity target, Weapon weapon)
static double applyFlatDamage(double baseDamage, Weapon weapon)

// Penetration
static boolean handleEntityPenetration(ProjectileState state, Projectile projectile)
static boolean handleBlockPenetration(ProjectileState state, Projectile projectile, Block hitBlock)

// Ricochet
static boolean handleRicochet(ProjectileState state, Projectile projectile, Block hitBlock, BlockFace face)

// Visual
static void spawnTracerParticles(Weapon weapon, Location from, Location to, Player player)
```

---

## Reload System

### `Reload` (abstract)

**Path:** `me.luckyraven.weapon.reload.Reload`

Base class for all reload strategies. Manages reload state, player tracking, and
sound/scope effects.

**Lifecycle:**

```
reload(plugin, player, removeAmmunition)
  |
  +-> Show action bar ("Reloading...")
  +-> executeReload(plugin, player, removeAmmunition)  [subclass]
        |
        +-> startReloading(player)
        |     +-> Set reloading = true
        |     +-> Show "Opening" action bar
        |     +-> Play reload start sound
        |     +-> Apply scope (slowdown)
        |     +-> Fire WeaponReloadStartEvent
        |
        +-> [Type-specific reload logic]
        |
        +-> endReloading(player)
              +-> Set reloading = false
              +-> Play reload end sound
              +-> Remove scope
              +-> Fire WeaponReloadCompleteEvent
```

### `ReloadType` (enum)

```java
INSTANT  -> InstantReload     // Full magazine in one step
ONE      -> NumberedReload    // One round at a time
NUM      -> NumberedReload    // Multiple rounds at a time (amount from config)
```

### `InstantReload`

Single-step reload using a `SequenceTimer` with three phases:

```
Time 0:              startReloading() -- scope, sounds, event
Time cooldown/2:     Mid-reload sound
Time cooldown:       Check ammo -> remove from inventory -> restore full magazine
                     Update weapon NBT -> endReloading()
```

Aborts if player dies or is downed during any phase.

### `NumberedReload`

Multi-step reload that inserts rounds one at a time (or in groups).

```
Time 0:                     startReloading()
For each insertion (up to magazine capacity):
  Time += cooldown:         Check ammo -> remove from inventory -> add restore amount
                            Play mid-reload sound -> update weapon NBT
After all insertions:
  Time += 1:                endReloading()
```

**Insertion count calculation:**

```java
int leftToInsert       = maxMagCapacity - currentMagCapacity;
int numberOfInsertions = leftToInsert / restore;
// Limited by actual ammo in player inventory
// NPCs (null inventory) get unlimited insertions
```

### `ReloadData` (DTO)

| Field      | Type         | Description           |
|------------|--------------|-----------------------|
| `cooldown` | `int`        | Ticks per reload step |
| `type`     | `ReloadType` | INSTANT, ONE, or NUM  |

---

## Shooting Mechanics

### `SpreadManager`

**Path:** `me.luckyraven.weapon.projectile.spread.SpreadManager`

Calculates bullet spread (accuracy degradation) with accumulation and time-based reset.

**SpreadData fields:**

| Field          | Type      | Description                                     |
|----------------|-----------|-------------------------------------------------|
| `start`        | `double`  | Initial spread value                            |
| `resetTime`    | `int`     | Milliseconds of inactivity before spread resets |
| `changeBase`   | `double`  | Spread increase per shot                        |
| `resetOnBound` | `boolean` | Reset to start when hitting bound, or clamp     |
| `boundMinimum` | `double`  | Minimum spread value                            |
| `boundMaximum` | `double`  | Maximum spread value                            |

**`applySpread(Vector)` flow:**

```
1. Check if time since last shot > resetTime -> reset spread to start
2. Generate random offsets: (random - 0.5) * currentSpread for X, Y, Z
3. Add offsets to original direction vector, normalize
4. Update spread: currentSpread += changeBase
   - If exceeds boundMaximum: clamp or reset (based on resetOnBound)
   - If below boundMinimum: clamp or reset
5. Return modified vector
```

### `RecoilManager`

**Path:** `me.luckyraven.weapon.projectile.recoil.RecoilManager`

Applies camera rotation (recoil) to the player after each shot, using NMS compatibility.

**RecoilData fields:**

| Field          | Type             | Description                         |
|----------------|------------------|-------------------------------------|
| `amount`       | `double`         | Default recoil magnitude            |
| `pushVelocity` | `double`         | Backward push on shooter            |
| `pushPowerUp`  | `double`         | Upward push on shooter              |
| `pattern`      | `List<String[]>` | Recoil pattern (yaw;pitch per shot) |

**Recoil application:**

```
1. If recoil pattern exists and is non-empty:
   a. Get current pattern entry [yaw, pitch]
   b. Apply modifiers:
      - Sneaking + scoped: divide by 2
      - Sneaking + not scoped: divide by 4
      - Standing: full values
   c. Apply via RecoilCompatibility NMS call
   d. Advance pattern index (loops back to 0)
2. Else (no pattern):
   a. Use default recoil amount for both yaw and pitch
   b. Apply same sneaking/scoped modifiers
```

**Pattern format in YAML:**

```yaml
Pattern:
  - "0.5;-1.2"    # Shot 1: yaw=0.5, pitch=-1.2
  - "0.3;-0.8"    # Shot 2: yaw=0.3, pitch=-0.8
  - "-0.2;-1.0"   # Shot 3: etc.
```

The pattern repeats cyclically. Reset occurs when:

- Player switches weapon slot
- Watchdog timer detects the player stopped shooting

### `FullAutoTask`

**Path:** `me.luckyraven.weapon.types.gun.FullAutoTask`

Handles fully automatic fire rate timing. Uses a pre-computed 20-tick boolean table
(inspired by WeaponMechanics by CJCrafter) to distribute shots evenly across ticks.

```
For 10 shots/second: [T,_,T,_,T,_,T,_,T,_,T,_,T,_,T,_,T,_,T,_]
For 5 shots/second:  [T,_,_,_,T,_,_,_,T,_,_,_,T,_,_,_,T,_,_,_]
```

The task runs at 1-tick intervals and fires `GunAction.weaponShoot()` on ticks
where `AUTO[shotsPerSecond][tickIndex]` is `true`.

**Shots per second calculation:** `20 / max(1, projectileData.cooldown)`

---

## Weapon Interaction

### `WeaponInteract` (557 lines)

**Path:** `me.luckyraven.weapon.listener.WeaponInteract`

The primary listener handling all player input for weapons. Autowired with
`WeaponService` and `RecoilCompatibility`.

**Internal state maps:**

| Map                 | Key    | Value                         | Purpose                        |
|---------------------|--------|-------------------------------|--------------------------------|
| `continuousFire`    | `UUID` | `AtomicReference<WeaponData>` | Track active burst/single fire |
| `singleShotLock`    | `UUID` | `Boolean`                     | Prevent repeated single shots  |
| `autoTasks`         | `UUID` | `FullAutoTask`                | Active full-auto tasks         |
| `activeTasks`       | `UUID` | `RepeatingTimer`              | Active incendiary/bio tasks    |
| `meleeCooldowns`    | `UUID` | `Long`                        | Per-weapon melee cooldown      |
| `activeMeleeSwings` | `UUID` | (Set)                         | 1-tick dedup for melee         |

**Event handlers:**

#### `onPlayerInteract(PlayerInteractEvent)`

Main input dispatcher:

```
1. Validate weapon from held item
2. Skip if player is dead or downed
3. Determine click type (left/right)
4. SCOPE TOGGLE (left-click + not sneaking + scope configured + not reloading):
   - Toggle scope on/off
   - Play scope sound
   - Return (no further processing)
5. NON-GUN dispatch:
   - ThrowableWeapon + right-click -> ThrowableAction.activate()
   - MeleeWeapon + left-click -> MeleeAction.activate()
   - IncendiaryWeapon + right-click -> start, + left-click -> stop
   - BiologicalWeapon + right-click -> start/fire, + left-click -> fire
6. GUN dispatch (right-click only):
   - Block if reloading
   - AUTO mode -> shootFullAuto() (FullAutoTask)
   - BURST/SINGLE mode -> shootOtherModes()
```

#### `onEntityDamage(EntityDamageByEntityEvent)` -- priority LOWEST

```
1. Check if damager is Player
2. Check pending damage sets (MeleeAction, ThrowableAction, IncendiaryAction)
   - If whitelisted: allow through (remove from set, return)
3. Check if held item is weapon
4. If MeleeWeapon: cancel vanilla damage, trigger MeleeAction
5. Otherwise: cancel vanilla damage (weapons never deal vanilla damage)
```

#### `onWeaponHeld(PlayerItemHeldEvent)`

Cleanup when switching away from a weapon:

```
1. Unscope player
2. Reset recoil pattern
3. Cancel single-shot lock
4. Cancel active auto fire task
5. Cancel active incendiary/biological tasks
```

#### Other handlers:

- `onBlockPlace` / `onBlockBreak` -- Cancel if holding weapon
- `onPlayerInteractWithEntity` -- Right-click on entity dispatches gun fire

---

## Configuration and Parsing

### YAML Structure

Each weapon is defined in its own YAML file. The `WeaponAddon` class orchestrates parsing.

**Shared sections (all weapon types):**

```yaml
Information:
  Name: "&6AK-47"
  Category: "gun"            # gun, melee, throwable, incendiary, biological
  Material: "WOODEN_HOE"
  Durability:
    Base: 500
    Change:
      On_Shot: 1
      On_Repair: 10
  Lore:
    - "&7A reliable assault rifle"
  Drop_Hologram: true

Death_Messages:
  - "&c{victim} was gunned down by {killer}"

Scope:
  Level: 2
  Sound:
    Default_Sound: { Sound: "ITEM_SPYGLASS_USE", Volume: 1.0, Pitch: 1.0 }

Modifiers:
  Break_Blocks:
    - "GLASS-3"              # Material group - hits required
  Penetration: "2-3-0.25"   # blocks-entities-damageReduction
  Ricochet:
    - "3-STONE,IRON_BLOCK-0.8"  # bounces-materials-retention
  Tracer: "FF0000-true-1.0" # hexColor-glowing-particleSize
  Armor_Piercing: "0.5"     # bypass percentage (0.0-1.0)
  Flat_Damage: "5.0"        # bonus damage
```

**Gun-specific section:**

```yaml
Shoot:
  Selective_Fire: "auto"     # auto, burst, single
  Projectile:
    Speed: 5
    Type: "bullet"           # bullet, spread, flare, rocket
    Damage:
      Base: 8
      Explosion_Damage: 0
      Fire_Ticks: 0
      Head: 4
      Critical_Hit:
        Chance: 15
        Amount: 3
    Consumed_Amount: 1
    Per_Shot: 1              # Burst: shots per burst
    Cooldown: 3              # Ticks between shots
    Distance: 100
    Particle: true
  Weapon_Consumed:
    Consume_On_Shot: 0       # Remove weapon after N shots (0=never)
  Recoil:
    Amount: 1.5
    Push: 0.1
    Power_Up: 0.05
    Pattern:
      - "0.5;-1.2"
      - "0.3;-0.8"
  Spread:
    Starting_Spread: 0.01
    Time: 500                # ms before spread resets
    Change:
      Base: 0.005
      Bounds:
        Reset_On_Bound: false
        Min: 0.01
        Max: 0.1
  Sound:
    Default_Sound: { Sound: "ENTITY_GENERIC_EXPLODE", Volume: 0.5, Pitch: 2.0 }
    Custom_Sound: { Sound: "custom.ak47.shot", Volume: 1.0, Pitch: 1.0 }
    Empty_Default_Sound: { Sound: "BLOCK_COMPARATOR_CLICK", Volume: 1.0, Pitch: 1.0 }
    Flyby_Range: 10.0
    Flyby_Default_Sound: { Sound: "ENTITY_ARROW_SHOOT", Volume: 0.3, Pitch: 2.0 }
    Impact_Default_Sound: { Sound: "ENTITY_ARMOR_STAND_HIT", Volume: 0.5, Pitch: 1.5 }

Ammunition:
  Ammo_Type: "762mm"         # Key from ammunition.yml
  Capacity: 30
  Consume: 1                 # Ammo items consumed from inventory per reload
  Restore: 30                # Rounds added to magazine per reload

Reload:
  Cooldown: 40               # Ticks for reload
  Type: "instant"            # instant, one, num-N
  Sound:
    Default_Sound_Before: { Sound: "BLOCK_IRON_DOOR_OPEN", Volume: 1.0, Pitch: 2.0 }
    Default_Sound_After: { Sound: "BLOCK_IRON_DOOR_CLOSE", Volume: 1.0, Pitch: 2.0 }
    Custom_Sound:
      Start: { Sound: "custom.reload.start", Volume: 1.0, Pitch: 1.0 }
      Mid: { Sound: "custom.reload.mid", Volume: 1.0, Pitch: 1.0 }
      End: { Sound: "custom.reload.end", Volume: 1.0, Pitch: 1.0 }
  Action_Bar:
    Reloading: "&eReloading..."
    Opening: "&7Opening chamber..."
```

**Melee-specific section (under `Shoot:` / `Attack:` / `Melee:`):**

```yaml
Attack:
  Damage: 6.0
  Range: 3.0
  Cooldown: 10       # Ticks between swings
  Knockback: 0.5
```

**Throwable-specific section (under `Shoot:` / `Throw:` / `Throwable:`):**

```yaml
Throw:
  Fuse_Time: 60           # Ticks before detonation
  Explosion_Radius: 4.0
  Explosion_Damage: 12
  Fire_Ticks: 40
  Bounces: true
  Max_Bounces: 3
  Sticky: false
```

**Incendiary-specific section:**

```yaml
Shoot:
  Cone_Angle: 30.0
  Range: 8.0
  Fire_Duration: 60
  Tick_Rate: 2
  Consume_Rate: 1
```

**Biological-specific section:**

```yaml
Shoot:
  Charge_Time_Per_Level: 20
  Max_Charge_Level: 5
  Effects_Per_Level:
    - "POISON-100-1"
    - "POISON-100-2,SLOWNESS-60-1"
    - "POISON-200-2,SLOWNESS-100-2"
    - "POISON-200-3,SLOWNESS-100-2,WITHER-60-1"
    - "POISON-300-3,SLOWNESS-200-3,WITHER-100-2"
  Area_Radius: 6.0
```

### Parser Classes

| Parser                    | Parses                  | Output                |
|---------------------------|-------------------------|-----------------------|
| `GunWeaponParser`         | `Shoot:` section        | `GunWeapon`           |
| `MeleeWeaponParser`       | `Attack:`/`Melee:`      | `MeleeWeapon`         |
| `ThrowableWeaponParser`   | `Throw:`/`Throwable:`   | `ThrowableWeapon`     |
| `IncendiaryWeaponParser`  | `Shoot:` section        | `IncendiaryWeapon`    |
| `BiologicalWeaponParser`  | `Shoot:` section        | `BiologicalWeapon`    |
| `AmmunitionSectionParser` | `Ammunition:`+`Reload:` | `ParsedAmmo` (record) |

**Shoot section resolution order:** `Shoot:` -> `Attack:` -> `Throw:` -> `Melee:` -> `Throwable:`

### `WeaponBaseData` (record)

Common fields shared across all parsers:

```java
record WeaponBaseData(
    String fileName,
    String displayName,
    WeaponType category,
    Material material,
    short durability,
    List<String> lore,
    boolean dropHologram,
    @Nullable List<String> deathMessages
)
```

### `AmmunitionAddon`

Parses `ammunition.yml` and registers entries into `AmmunitionManager`:

```yaml
# ammunition.yml
762mm:
  Name: "&e7.62mm"
  Material: "IRON_NUGGET"
  Lore:
    - "&7Standard rifle round"
```

---

## Events

All weapon events extend `WeaponEvent`, which provides `getWeapon()`.

### Event Hierarchy

```
WeaponEvent (abstract)
  |
  +-- WeaponShootEvent            [Cancellable] - Gun fires
  +-- WeaponProjectileLaunchEvent [Cancellable] - Projectile entity spawned
  +-- WeaponProjectileHitEvent    [Cancellable] - Projectile hits target/block
  +-- WeaponReloadEvent           [Cancellable] - Reload requested (drop listener)
  +-- WeaponReloadStartEvent                    - Reload begins
  +-- WeaponReloadCompleteEvent                 - Reload finishes
  +-- WeaponChangeSelectiveFireEvent [Cancellable] - Fire mode toggled
  +-- WeaponEntityDamageEvent                   - Non-projectile damage to entity
  +-- WeaponKillEntityEvent       [Cancellable] - Entity killed by weapon
```

### Event Details

| Event                            | Cancellable | Fired By                               | Key Data                                      |
|----------------------------------|-------------|----------------------------------------|-----------------------------------------------|
| `WeaponShootEvent`               | Yes         | `GunAction.weaponShoot()`              | weapon, weaponProjectile                      |
| `WeaponProjectileLaunchEvent`    | Yes         | `WeaponProjectile.launchProjectile()`  | weapon, projectile (Bukkit), weaponProjectile |
| `WeaponProjectileHitEvent`       | Yes         | `ProjectileDamageListener`             | weapon                                        |
| `WeaponReloadEvent`              | Yes         | `WeaponDroppedListener`                | weapon                                        |
| `WeaponReloadStartEvent`         | No          | `Reload.startReloading()`              | weapon, player                                |
| `WeaponReloadCompleteEvent`      | No          | `Reload.endReloading()`                | weapon, player                                |
| `WeaponChangeSelectiveFireEvent` | Yes         | `WeaponSelectiveFireChangeListener`    | weapon                                        |
| `WeaponEntityDamageEvent`        | No          | `IncendiaryAction`, `BiologicalAction` | weapon, entity, damage, shooter               |
| `WeaponKillEntityEvent`          | Yes         | External (gangland-impl)               | weapon, killer, killed                        |

---

## Listeners

### 1. `WeaponInteract` (557 lines)

**Events:** `PlayerInteractEvent`, `BlockPlaceEvent`, `BlockBreakEvent`,
`PlayerInteractEntityEvent`, `EntityDamageByEntityEvent`, `PlayerItemHeldEvent`

Primary input handler. See [Weapon Interaction](#weapon-interaction) section for full details.

### 2. `ProjectileDamageListener` (410 lines)

**Events:** `WeaponProjectileLaunchEvent`, `EntityDamageByEntityEvent`, `ProjectileHitEvent`

Manages the complete projectile lifecycle from launch to impact.

**Internal state:**

| Static Map         | Key        | Value             | Purpose                        |
|--------------------|------------|-------------------|--------------------------------|
| `weaponInstance`   | `entityId` | `GunWeapon`       | Links projectile to its weapon |
| `projectileStates` | `entityId` | `ProjectileState` | Tracks penetration/ricochet    |

**Event queue system:** Uses `ProjectileEventQueue` to synchronize
`EntityDamageByEntityEvent` and `ProjectileHitEvent`, which may arrive in any order:

```
1. On ProjectileLaunchEvent (LOW priority):
   - Register weapon and state by entity ID
   - Spawn tracer particles if configured

2. On EntityDamageByEntityEvent (LOWEST priority):
   - For non-living entities: set configured damage directly
   - For living entities: add to queue, try process

3. On ProjectileHitEvent (HIGHEST priority):
   - Reset noDamageTicks on hit entity (prevents invulnerability skip)
   - Add to queue, try process

4. Queue processing (when both events received):
   a. Fire WeaponProjectileHitEvent
   b. Process damage events (in order):
      - Calculate critical hit (chance + bonus)
      - Reduce crit bonus via TOUGHENED wearable trait
      - Apply armor piercing modifier
      - Apply wearable damage reduction
      - Apply flat damage bonus (post-reduction)
      - Apply headshot bonus
      - Set final damage on event
      - Handle entity penetration
   c. Process hit event:
      - Try ricochet -> if bounced, reset queue for next hit
      - Try block penetration -> if penetrated, reset queue
      - Handle block break modifiers
      - Remove projectile if no ricochet/penetration
      - Handle explosive projectile (Fireball) AoE
   d. Cleanup state maps
```

### 3. `WeaponReloadListener`

**Events:** `WeaponReloadStartEvent`, `WeaponReloadCompleteEvent`, `PlayerItemHeldEvent`, `PlayerQuitEvent`

Tracks which players are reloading. Cancels reload if player switches weapon slot or disconnects.

### 4. `WeaponDroppedListener`

**Events:** `PlayerDropItemEvent`

**Drop key (Q):**

- While reloading: cancel drop
- Sneaking + has ammo + magazine not full: cancel drop, trigger reload
- Not sneaking: allow drop, show hologram if configured

### 5. `ScopeJumpListener`

**Events:** `PlayerMoveEvent` (HIGHEST priority)

Prevents jumping while scoped by snapping Y position back to original.
Does NOT block movement during reload (allows external knockback).

### 6. `WeaponSelectiveFireChangeListener`

**Events:** `PlayerSwapHandItemsEvent`

Sneaking + swap-hand key (F) cycles selective fire mode:
`AUTO -> BURST -> SINGLE -> AUTO`

Fires `WeaponChangeSelectiveFireEvent` (cancellable), then updates weapon NBT and shows
fire mode on action bar.

---

## Damage Pipeline

Complete flow from player click to damage application:

```
                    Player Input
                        |
                        v
              +-------------------+
              | WeaponInteract    |
              | onPlayerInteract  |
              +--------+----------+
                       |
            +----------+-----------+
            |                      |
         GunWeapon              Non-Gun
            |                      |
            v                      v
     +-------------+     +------------------+
     | GunAction   |     | MeleeAction /    |
     | weaponShoot |     | ThrowableAction /|
     +------+------+     | IncendiaryAction/|
            |             | BiologicalAction |
            v             +--------+---------+
     +--------------+              |
     | consumeShot  |              v
     +--------------+     [Direct damage or
            |              AoE calculation]
            v
     +-------------------+
     | WeaponShootEvent  |  <-- Cancellable
     +--------+----------+
              |
              v
     +------------------------+
     | ProjectileType         |
     | .createInstance()      |
     +--------+---------------+
              |
              v
     +------------------------+
     | WeaponProjectile       |
     | .launchProjectile()    |
     +--------+---------------+
              |
              v
     +------------------------------+
     | WeaponProjectileLaunchEvent  |  <-- Cancellable
     +--------+---------------------+
              |
              v
     [Projectile travels through world]
     [SpreadManager applied to direction]
     [Flyby sounds for nearby players]
              |
              v
     +------------------------+
     | ProjectileHitEvent     |  (Bukkit)
     +--------+---------------+
              |
              v
     +------------------------------+
     | ProjectileDamageListener     |
     | (event queue synchronization)|
     +--------+---------------------+
              |
              v
     +----------------------------+
     | WeaponProjectileHitEvent   |  <-- Cancellable
     +--------+-------------------+
              |
              v
     +----------------------------------+
     | Damage Calculation               |
     |                                  |
     | 1. Base damage from ProjectileData|
     |    (or ProjectileState if         |
     |     penetrated/ricocheted)        |
     |                                  |
     | 2. Critical hit roll             |
     |    (chance % -> bonus damage)    |
     |    Reduced by TOUGHENED trait     |
     |                                  |
     | 3. Armor Piercing modifier       |
     |    (reduces effective armor)     |
     |                                  |
     | 4. Wearable damage reduction     |
     |    (multiplicative per slot)     |
     |    REACTIVE: chance to nullify   |
     |                                  |
     | 5. Flat Damage bonus             |
     |    (added after reductions)      |
     |                                  |
     | 6. Headshot bonus                |
     |    (Y diff > 1.4 blocks)         |
     |                                  |
     | 7. Fire ticks                    |
     |    (reduced by FIRE_RESISTANT +  |
     |     FIRE_PROTECTION enchant)     |
     +--------+-------------------------+
              |
              v
     +----------------------------------+
     | event.setDamage(finalDamage)     |
     +--------+-------------------------+
              |
              v
     +----------------------------------+
     | Modifier post-processing:        |
     |                                  |
     | - Entity penetration:            |
     |   Increment count, reduce mult,  |
     |   continue projectile if allowed |
     |                                  |
     | - Ricochet:                      |
     |   Reflect velocity, reduce mult, |
     |   reset queue for next hit       |
     |                                  |
     | - Block penetration:             |
     |   Check if penetrable material,  |
     |   increment count, reduce mult   |
     |                                  |
     | - Block break:                   |
     |   Apply crack damage via         |
     |   BlockDamageManager             |
     +--------+-------------------------+
              |
              v
     +----------------------------------+
     | If lethal:                       |
     | WeaponKillEntityEvent            |
     | (fired by gangland-impl)         |
     +----------------------------------+
```

### Damage Formula Summary

```
damage = baseDamage                           // From ProjectileData or ProjectileState
       + critBonus * (1 - toughenedReduction) // Random chance, reduced by armor trait

// Armor piercing: reduces effective armor for this calculation
effectiveArmor = armor * (1 - armorBypass)
// Minecraft formula: normalReduction = min(20, armor) / 25
// Piercing compensates: damage += piercingDamage - normalDamage

damage = applyWearableReduction(damage)       // Per-slot multiplicative
                                               // REACTIVE can nullify entirely

damage += flatDamageBonus                     // After all reductions

if headshot:
    damage += headDamage                      // Bypasses armor

fireTicks = reduceFireTicks(configuredTicks)   // FIRE_RESISTANT + FIRE_PROTECTION
```

---

## Wearable / Armor Reduction

**Path:** `me.luckyraven.weapon.wearable.WearableService`

Applies custom armor reduction from the plugin's wearable system alongside vanilla armor.

**Resolution order per armor slot:**

1. Item has `"wearable"` NBT key matching registry -> use registered values
2. Item has `"wearable"` NBT key but not in registry -> create from material
3. Vanilla armor piece -> create from material
4. Not armor -> null (no reduction)

**Reduction methods:**

| Method                     | Description                                             |
|----------------------------|---------------------------------------------------------|
| `applyWearableReduction()` | Per-slot multiplicative damage reduction                |
| `reduceCritBonus()`        | Reduces critical hit bonus via TOUGHENED trait          |
| `reduceFireTicks()`        | Reduces fire duration via FIRE_RESISTANT + enchantments |

**Stacking model:** Multiplicative per slot. Each slot's reduction is capped at 90%.
Four slots cannot fully negate damage.

**Special traits:**

- `REACTIVE` -- chance to nullify entire hit (checked first)
- `TOUGHENED` -- reduces critical hit bonus damage
- `FIRE_RESISTANT` -- reduces fire tick duration
- `BULLETPROOF` -- additional projectile-specific reduction

---

## NBT Tag System

Weapons store runtime state as NBT tags on the `ItemStack`. This allows weapons to
persist across inventory actions, drops, and server restarts.

**Tags written to every weapon item:**

```
uuid           = "550e8400-e29b-41d4-a716-446655440000"  (String)
weapon         = "ak47"                                   (String)
selective-fire = "AUTO"                                   (String)
ammo-left      = 24                                       (Integer)
```

**Tag lifecycle:**

```
buildItem()        -> Writes all 4 tags to new ItemStack
updateWeaponData() -> Updates SELECTIVE_FIRE and AMMO_LEFT on existing item
setWeaponData()    -> Reads AMMO_LEFT, SELECTIVE_FIRE from item NBT into Weapon fields
```

**Ammunition items use a separate tag:**

```
ammo = "762mm"   (String - key into AmmunitionManager)
```

---

## Durability System

**Path:** `me.luckyraven.weapon.durability.DurabilityCalculator`

The weapon has its own durability system independent of Minecraft's item durability.
The weapon durability is mapped to the item's visual durability bar for display.

**DurabilityData fields:**

| Field           | Type    | Description                                           |
|-----------------|---------|-------------------------------------------------------|
| `onShot`        | `short` | Durability lost per shot                              |
| `onRepair`      | `short` | Durability restored per repair                        |
| `consumeOnTime` | `int`   | Ticks after shot before weapon is consumed (-1=never) |

**Mapping formula (weapon durability -> item damage bar):**

```java
// Weapon -> Item display
scale = itemMaxDurability / weaponMaxDurability
itemDamageValue = floor((weaponMaxDurability - currentDurability) * scale)

// Item display -> Weapon (loading from existing item)
weaponCurrentDurability = weaponMaxDurability - (itemCurrentDamage / scale)
```

This allows weapons to use any Minecraft material (even those with low vanilla durability)
while supporting arbitrary weapon durability values.

**Broken weapon behavior:** When `currentDurability <= 0`, the weapon is "broken":

- Guns: play empty-mag sound, show "Broken" on action bar, cannot shoot
- General: `isBroken()` returns true, checked before activation

**Repair integration:** Weapons implement the `Repairable` interface from `gangland-item`:

```java
String getRepairableId()           // weapon name
int    getCurrentRepairDurability() // currentDurability
void   setCurrentRepairDurability() // updates currentDurability
int    getMaxRepairDurability()    // max durability
RepairableType getRepairableType() // WEAPON
```
