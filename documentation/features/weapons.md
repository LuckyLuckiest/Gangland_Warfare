# Weapons

[← Wanted & Bounty](./wanted-bounty.md) | [Back to Index](../README.md) | [Next: Wearables →](../v0.7.3-DEV/wearables.md)

---

## Overview

Gangland uses a fully custom weapon system. Weapons are distinct from vanilla items — they carry their own damage
values, reload mechanics, fire modes, durability, ammo types, and special properties. Cops can also carry and fire
weapons, pulling from their own configured weapon pools.

---

## Weapon Configuration

Each weapon is defined in its own `.yml` file under the `weapon/` resources folder. The rifle is the reference example.

### Basic Structure

```yaml
name: "Rifle"
material: BOW                   # Vanilla item used as the display item
durability:
  base: 100                     # Starting durability
  consumed-on-shot: 1           # Durability lost per shot

shoot:
  type: AUTO                    # AUTO, BURST, or SINGLE
  speed: 4                      # Projectile speed units
  cooldown: 0.5                 # Seconds between shots
  spread:
    start: 0.05                 # Initial accuracy spread
    max: 1.5                    # Maximum spread under sustained fire

damage:
  base: 7                       # Base hit damage
  head-bonus: 5                 # Additional damage on headshot
  critical-chance: 0.02         # 2% chance to trigger a critical
  critical-bonus: 4             # Extra damage on critical
  flat-damage: 0.0              # Damage added after all reductions (bypasses armor)

range: 10                       # Maximum effective distance in blocks

reload:
  capacity: 45                  # Rounds per magazine
  cooldown: 4                   # Seconds to reload
  type: instant                 # instant, one (per bullet), or num (per batch)
```

### Advanced Properties

```yaml
modifiers:
  armor-piercing: 0.40          # Percentage of armor bypassed (0.40 = 40%)
  penetration:
    blocks: 2                   # Passes through up to 2 blocks
    entities: 3                 # Hits up to 3 entities in a line
    damage-reduction: 0.25      # 25% damage lost per entity penetrated
  block-breaking:               # Blocks this weapon can break and how many hits
    GLASS: 3
    ICE: 5
    TERRACOTTA: 10
  tracer:
    color: "FF5500"             # Hex color of tracer rounds
    glowing: true
    size: 0.8
```

### Flat Damage

`flat-damage` is a damage value added **after** all other damage reductions have been applied — armor, wearable traits,
and armor piercing all happen first, then flat damage is added on top. It is always dealt in full and cannot be reduced.
This makes it useful for weapons that should always deal a guaranteed minimum injury.

---

## Ammunition

Ammo types are defined in `ammunition.yml`. Each weapon specifies which ammo type it uses, and players must have that
ammo in their inventory to fire.

| Ammo Name | Item        | Notes                    |
|-----------|-------------|--------------------------|
| 9mm       | Gold Nugget | Pistol rounds            |
| 7.62 NATO | Flint       | Rifle cartridge          |
| 5.56mm    | Gunpowder   | Assault rifle ammunition |
| Flare     | Sugar       | Pyrotechnic              |
| Rocket    | Feather     | Anti-tank                |
| .50 BMG   | Slime Ball  | Long-range sniper        |
| Slugs     | Red Dye     | Shotgun rounds           |

Ammo is consumed when loaded into the weapon's magazine, not on each shot. One ammo item = one magazine load.

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
    Player player = event.getPlayer();
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
player.getInventory().addItem(weaponItem);

// Check if an item is a Gangland weapon
boolean isWeapon = weaponManager.isWeapon(itemStack);
```

---

[← Wanted & Bounty](./wanted-bounty.md) | [Back to Index](../README.md) | [Next: Wearables →](../v0.7.3-DEV/wearables.md)
