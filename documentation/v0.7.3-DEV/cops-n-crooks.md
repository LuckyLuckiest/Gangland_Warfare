# Cops N Crooks

[← Back to Index](../README.md) | [Next: Jail & Detainment →](./jail-detainment.md)

---

## Overview

Cops N Crooks brings fully AI-driven police NPCs to your server. When a player accumulates a wanted level, police
officers spawn in the world, track the player down, and attempt to arrest them. The system is powered by the Citizens
plugin and requires it to function.

The number and strength of cops that respond scales with the player's wanted level — a low-level offender draws a couple
of rookie officers, while a five-star fugitive faces a military response.

---

## How It Works

1. A player earns **wanted stars** through kills, crimes, or admin commands.
2. The system periodically checks for wanted players and spawns cops near them.
3. Cops pick up the player's trail, pursue them, and attempt a cuff-and-arrest.
4. If the arrest is successful, the player is jailed. If the player escapes or kills the cops, their wanted level decays
   over time.
5. **Dying clears your wanted level** — but so does evading the cops long enough.

---

## Cop Tiers

Cop strength is determined by the player's wanted level. Higher tiers have more health, deal more damage, move faster,
and carry better equipment.

| Tier | Name       | Health | Damage | Speed | Can Use Firearms | Skips Cuffing |
|------|------------|--------|--------|-------|------------------|---------------|
| 1    | Officer    | 20     | 2.0    | 1.0×  | No               | No            |
| 2    | Sergeant   | 25     | 3.0    | 1.1×  | No               | No            |
| 3    | Lieutenant | 30     | 4.0    | 1.2×  | Yes              | No            |
| 4    | SWAT       | 40     | 5.0    | 1.3×  | Yes              | Yes           |
| 5    | Military   | 60     | 7.0    | 1.4×  | Yes              | Yes           |

> **Skip Cuffing**: Tier 4 and 5 cops do not attempt to cuff the player first — they go straight to lethal engagement.

---

## Cop AI Behavior

Cops follow a state machine with three primary states:

### Pursuit

The cop has spotted a wanted player and is actively navigating toward them. Navigation recalculates every 10 ticks. If a
cop gets stuck — determined by detecting no meaningful movement across several consecutive checks — it retries
pathfinding and eventually uses a fallback position.

### Combat

Within 12 blocks (ranged) or 4 blocks (melee), the cop switches to combat mode. Armed cops fire their configured weapon
with proper reload cycles. When one cop enters combat range and alerts the squad, all nearby cops in the group become
aware of the player's location.

### Cuffing

Lower-tier cops that reach the player attempt to cuff rather than kill. A cop makes up to 3 cuffing attempts with a
cooldown between each. If all attempts fail, the cop falls back to combat mode. Only one cop can attempt to cuff a
player at a time — the others stand by.

---

## Spawner System

Cops spawn from **spawner locations** you place in the world. When the system needs to spawn cops for a wanted player,
it looks for the nearest spawner within 80 blocks and spawns from there.

If no configured spawner is nearby, it falls back through a series of phases:

1. **Phase 1** — Attempts to find a valid location in a ring approximately 30 blocks behind the player.
2. **Phase 2** — Shrinks the search radius progressively until a valid spot is found.
3. **Global fallback** — Spawns at any valid location if all else fails.

Each spawner candidate is validated: the location must have at least two open sides and solid ground beneath it, and
must not be indoors in a way that would trap the NPC.

---

## Commands

All commands require appropriate permissions.

### Spawner Management

| Command                          | Description                                                 |
|----------------------------------|-------------------------------------------------------------|
| `/glw cop spawner set`           | Places a cop spawner at your current location.              |
| `/glw cop spawner remove <id>`   | Removes the spawner with the given ID.                      |
| `/glw cop spawner list`          | Lists all configured spawners with their IDs and locations. |
| `/glw cop spawner info <id>`     | Shows details about a specific spawner.                     |
| `/glw cop spawner teleport <id>` | Teleports you to a spawner's location.                      |

### Active Cops

| Command         | Description                          |
|-----------------|--------------------------------------|
| `/glw cop list` | Lists all currently active cop NPCs. |

---

## Configuration

Cop behavior is controlled through `cops.yml`. The file is generated automatically on first run.

### Tier Configuration

Each tier (`tier-1` through `tier-5`) supports the following fields:

```yaml
tier-1:
   name: "Officer"
   health: 20.0
   damage: 2.0
   speed: 1.0
   cuff-radius: 3.0          # How close the cop must be to attempt a cuff
   can-use-weapons: false    # Whether this tier fires ranged weapons
   skip-cuffing: false       # If true, goes straight to lethal combat
   equipment:
      - WOODEN_SWORD          # Items given to the NPC on spawn
```

### AI Settings

Found under the `behavior` section of `cops.yml`:

```yaml
behavior:
   ai-tick-rate: 10          # How often (in ticks) cops recalculate decisions
   spawn-check-ticks: 40     # How often the spawn manager checks for wanted players
   attack-cooldown: 20       # Ticks between melee attacks
   cuff-attempts: 3          # Max cuffing attempts before switching to combat
   cuff-cooldown: 100        # Ticks between cuffing attempts
   navigation-recalc: 10     # Ticks between pathfinding recalculations
   stuck-threshold: 6        # Consecutive stuck checks before retrying navigation
   return-timeout: 600       # Ticks before a cop with no target despawns
   arrival-distance: 3.0     # Blocks from target before the cop considers itself "arrived"
   starting-ammo: 3          # Magazine reloads each cop spawns with
```

### Spawn Settings

Found under the `spawn` section:

```yaml
spawn:
   min-distance: 10          # Minimum spawn distance from the player
   max-distance: 50          # Maximum spawn distance from the player
   phase-1-radius: 30.0      # Preferred spawn ring radius
   visibility-check: 48.0    # Distance within which the system checks for spawners
```

### Global Settings

These live in `settings.yml` under the `cops` key:

```yaml
cops:
   count:
      base: 2                 # Cops sent at 1 wanted star
      per-level: 1            # Additional cops per additional wanted star
      max: 8                  # Hard cap on cops per player
   alert-range: 40.0         # Blocks within which a cop alerts its squad
   cuff-radius: 3.0          # Default cuff attempt radius (overridden per tier)
```

---

## API

The main entry point for the cops system is `CopService`, accessible from the plugin initializer.

```java
CopService copService = gangland.getInitializer().getCopService();

// Check if a player is being pursued
boolean pursued = copService.isBeingPursued(player);

// Manually trigger a cop spawn for a player
copService.

spawnCopsFor(player);

// Despawn all cops currently targeting a player
copService.

despawnCopsFor(player);
```

The `CopSpawnManager` handles spawner persistence:

```java
CopSpawnManager spawnerManager = gangland.getInitializer().getCopSpawnManager();

// Get all registered spawner locations
List<CopSpawner> spawners = spawnerManager.getSpawners();
```

---

[← Back to Index](../README.md) | [Next: Jail & Detainment →](./jail-detainment.md)
