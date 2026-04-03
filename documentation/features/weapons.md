# Weapons

[← Scoreboard](./scoreboard.md) | [Back to Index](../README.md) | [Next: Wanted & Bounty →](./wanted-bounty.md)

---

## Overview

Gangland uses a fully custom weapon system. Weapons are distinct from vanilla items — they carry their own damage
values, reload mechanics, fire modes, durability, ammo types, and special properties. Cops can also carry and fire
weapons, pulling from their own configured weapon pools.

---

## Weapon Configuration

Each weapon is defined in its own `.yml` file inside the `weapon/` folder in the plugin's resources directory. The file
name becomes the weapon's internal ID. The rifle (`rifle.yml`) is the reference example.

---

### `Information`

```yaml
Information:
   Name: "&7M4 Carbine&r"          # Display name shown in-game (supports & color codes)
   Category: gun                   # gun, melee, projectile, or incendiary
   Material: IRON_PICKAXE          # Vanilla Bukkit material used as the item base
   Durability:
      Base: 100                     # Starting durability of the weapon
      Change:
         On_Shot: 0                  # Durability lost per shot fired
         On_Repair: 0                # Durability lost per repair action (usually 0)
   Lore:
      - "&7Description line 1"      # Item lore lines (supports & color codes)
   Drop_Hologram: true             # Whether a hologram appears when the weapon is on the ground
```

---

### `Shoot`

```yaml
Shoot:
   Selective_Fire: auto            # Fire mode: auto, burst, or single
```

#### `Shoot.Projectile`

```yaml
  Projectile:
     Speed: 4                      # Projectile travel speed (units per tick)
     Type: BULLET                  # Projectile type: BULLET, FLARE, ROCKET, or SPREAD
     Damage:
        Base: 7                     # Base damage on hit
        Explosion_Damage: 0         # Explosion radius damage (used by ROCKET type)
        Fire_Ticks: 0               # Ticks the target is set on fire (20 ticks = 1 second)
        Head: 5                     # Bonus damage added on headshot
        Critical_Hit:
           Chance: 2                 # Percentage chance (2 = 2%) to land a critical hit
           Amount: 4                 # Extra damage added on a critical hit
     Consumed_Amount: 1            # How many ammo items are consumed per shot
     Per_Shot: 1                   # How many projectiles are fired per trigger pull (e.g. shotgun = 8)
     Cooldown: 0.5                 # Seconds between individual shots
     Distance: 10                  # Maximum travel distance in blocks before the projectile disappears
     Particle: true                # Whether a particle trail follows the projectile
```

#### `Shoot.Weapon_Consumed`

```yaml
  Weapon_Consumed:
     Consume_On_Shot: 0            # Destroy the weapon after this many shots (0 = never)
     Time: -1                      # Destroy the weapon this many seconds after the first shot (-1 = never)
```

Both fields can be combined. The weapon is removed as soon as either condition triggers first.

#### `Shoot.Spread`

```yaml
  Spread:
     Starting_Spread: 0.05         # Accuracy spread when firing begins
     Time: 5                       # Seconds before spread resets back to Starting_Spread
     Change:
        Base: 0.05                  # Amount added to spread per shot
        Bounds:
           Reset_On_Bound: true      # When Max is reached, reset spread to Min instead of capping
           Min: 0.05                 # Floor value spread resets to
           Max: 1.5                  # Ceiling value spread will not exceed
```

#### `Shoot.Recoil`

```yaml
  Recoil:
     Amount: 0.05                  # How much the camera is pushed per shot
     Push: 0.05                    # Horizontal velocity applied to the player per shot
     Power_Up: 0.0002              # Vertical velocity applied upward per shot
     Pattern: # Sequence of recoil vectors (yaw;pitch per shot), loops when exhausted
        - 2.5;1
        - 0;0
        - 0.5;1
        - 1;1
```

#### `Shoot.Sound`

Each sound block has `Sound` (Bukkit sound name), `Volume`, and `Pitch`. Custom sounds override default sounds when
available through the resource pack.

```yaml
  Sound:
     Default_Sound: # Played on every shot (vanilla sound fallback)
        Sound: ENTITY_GENERIC_EXPLODE
        Volume: 1.3
        Pitch: 2.2
     Custom_Sound: # Resource pack sound played on every shot
        Sound: sound.gta.m4_shoot
        Volume: 1
        Pitch: 2.2
     Empty_Default_Sound: # Played when the player fires with an empty magazine (vanilla fallback)
        Sound: ENTITY_BLAZE_BURN
        Volume: 1
        Pitch: 1
     Empty_Custom_Sound: # Resource pack version of the empty-magazine click
        Sound: sound.gta.m4_empty
        Volume: 1
        Pitch: 1
     Flyby_Range: 3.0              # Blocks from the projectile path within which other players hear the flyby
     Flyby_Default_Sound: # Sound heard by nearby players as the bullet passes (vanilla fallback)
        Sound: ENTITY_ARROW_SHOOT
        Volume: 0.6
        Pitch: 2.0
     Flyby_Custom_Sound: # Resource pack flyby whoosh
        Sound: sound.gta.bullet_flyby
        Volume: 0.8
        Pitch: 1.0
     Impact_Default_Sound: # Played when the projectile hits a target (vanilla fallback)
        Sound: BLOCK_STONE_HIT
        Volume: 1.0
        Pitch: 1.5
     Impact_Custom_Sound: # Resource pack impact sound
        Sound: sound.gta.bullet_impact
        Volume: 1.0
        Pitch: 1.0
```

---

### `Reload`

```yaml
Reload:
   Capacity: 45                    # Maximum rounds the magazine holds
   Cooldown: 4                     # Seconds the reload animation takes
   Ammo_Type: "5,56"              # Key from ammunition.yml (dots replaced with commas — see Ammunition below)
   Consume: 1                      # Number of ammo items consumed to load one full magazine
   Restore: 45                     # Number of rounds added to the magazine per reload
   Type: instant                   # Reload mode: instant, one, or num
     #   instant — loads a full magazine in one action
   #   one — loads rounds one at a time
   #   num — loads in batches (requires two numbers separated by '-')
   Action_Bar:
      Reloading: "&cReloading...&r" # Action bar text shown while reload is in progress
      Opening: "&cOpening...&r"     # Action bar text shown at the start of the reload sequence
   Sound:
      Default_Sound_Before: # Played at the start of the reload (vanilla fallback)
         Sound: ENTITY_WITHER_DEATH
         Volume: 1
         Pitch: 1
      Default_Sound_After: # Played at the end of the reload (vanilla fallback)
         Sound: ENTITY_WITHER_DEATH
         Volume: 1
         Pitch: 1
      Custom_Sound: # Resource pack three-stage reload sounds
         Start:
            Sound: sound.gta.m4_reload_start
            Volume: 1
            Pitch: 1
         Mid:
            Sound: sound.gta.m4_reload_mid
            Volume: 1
            Pitch: 1
         End:
            Sound: sound.gta.m4_reload_end
            Volume: 1
            Pitch: 1
```

---

### `Scope`

```yaml
Scope:
   Level: 2                        # Zoom level when scoping in (higher = more zoom)
   Sound:
      Default_Sound: # Vanilla fallback played on scope toggle
         Sound: ENTITY_GENERIC_EXPLODE
         Volume: 1
         Pitch: 3
      Custom_Sound: # Resource pack scope sound
         Sound: sound.gta.m4_scope
         Volume: 1
         Pitch: 1
```

---

### `Modifiers`

```yaml
Modifiers:
   Flat_Damage: 0.0                # Damage added after all reductions (armor, wearables, armor piercing).
   # Always dealt in full — cannot be reduced.
   Armor_Piercing: 0.4             # Fraction of the target's armor to bypass (0.4 = 40%)

   Break_Blocks: # Blocks this weapon can destroy and how many hits they require
      - GLASS-3                     # Format: MATERIAL-hitsRequired
      - ICE-5
      - TERRACOTTA-10

   Penetration: 2-3-0.25          # Format: blocksPierced-entitiesPierced-damageReductionPerEntity
   # 2 blocks, up to 3 entities, 25% less damage per entity penetrated

   Ricochet: # List of ricochet rules. Each rule applies to listed materials.
      - 3-STONE,COBBLESTONE,IRON_BLOCK-0.8  # Format: maxBounces-MATERIAL1,MATERIAL2-damageRetention
      - 2-CONCRETE-0.7                       # 0.8 = 80% damage kept after each bounce

   Tracer: FF5500-true-0.8         # Format: RRGGBB-glowing-particleSize
   # Hex color, whether the tracer glows, and particle size
```

---

## Ammunition

Ammo types are defined in `ammunition.yml`. Each entry has a key, a vanilla `Material`, a `Name`, and `Lore` lines.

### Key Naming — Dot Restriction

YAML keys cannot contain `.`, so any ammo name with a period must use a comma instead in `ammunition.yml`. The weapon's
`Ammo_Type` field must use the same comma-substituted key.

```yaml
# In ammunition.yml:
5,56: # Key uses comma — represents 5.56mm
   Material: "GUNPOWDER"
   Name: "&c5.56mm&r"
   Lore:
      - "..."

# In rifle.yml:
Reload:
   Ammo_Type: "5,56"          # Must match the key in ammunition.yml exactly
```

### Built-in Ammo Types

| Key in ammunition.yml | Display Name | Vanilla Item |
|-----------------------|--------------|--------------|
| `9mm`                 | 9mm          | Gold Nugget  |
| `7,62`                | 7.62 NATO    | Flint        |
| `5,56`                | 5.56mm       | Gunpowder    |
| `flare`               | Flare        | Sugar        |
| `rocket`              | Rocket       | Feather      |
| `50_bmg`              | .50 BMG      | Slime Ball   |
| `slugs`               | Slugs        | Red Dye      |

Ammo is consumed when the player reloads, not on each individual shot. `Consume: 1` in the weapon config means one ammo
item fills the magazine.

---

## Fire Modes

| Mode     | Behavior                                                               |
|----------|------------------------------------------------------------------------|
| `SINGLE` | One shot per click.                                                    |
| `BURST`  | Fires a fixed number of rounds per click with a short delay between.   |
| `AUTO`   | Fires continuously while the trigger is held, respecting the cooldown. |

---

## Commands

### Weapons

| Command                              | Description                             |
|--------------------------------------|-----------------------------------------|
| `/glw weapon give <player> <weapon>` | Gives the specified weapon to a player. |
| `/glw weapon list`                   | Lists all configured weapons.           |
| `/glw weapon info <weapon>`          | Shows full stats for a weapon.          |

### Ammo

| Command                                   | Description                           |
|-------------------------------------------|---------------------------------------|
| `/glw ammo give <player> <ammo> <amount>` | Gives the specified ammo to a player. |
| `/glw ammo list`                          | Lists all configured ammo types.      |
| `/glw ammo info <ammo>`                   | Shows details about an ammo type.     |

---

## API

### Weapon Events

Listen for weapon events by registering Bukkit listeners for the relevant Gangland events:

```java

@EventHandler
public void onWeaponReloadStart(WeaponReloadStartEvent event) {
	Player         player = event.getPlayer();
	GanglandWeapon weapon = event.getWeapon();
	// ...
}

@EventHandler
public void onWeaponReloadStop(WeaponReloadStopEvent event) {
	// Fired when reload is cancelled or completed
}

@EventHandler
public void onWeaponShoot(WeaponShootEvent event) {
	event.setCancelled(true); // Can cancel the shot
}
```

### Weapon Manager

```java
WeaponManager weaponManager = gangland.getInitializer().getWeaponManager();

// Get a weapon template by name
GanglandWeapon weapon = weaponManager.getWeapon("Rifle");

// Give a weapon to a player
ItemStack weaponItem = weaponManager.createWeaponItem("Rifle");
player.

getInventory().

addItem(weaponItem);

// Check if an item is a Gangland weapon
boolean isWeapon = weaponManager.isWeapon(itemStack);
```

---

[← Scoreboard](./scoreboard.md) | [Back to Index](../README.md) | [Next: Wanted & Bounty →](./wanted-bounty.md)
