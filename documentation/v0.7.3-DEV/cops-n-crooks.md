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

Cop behavior is split across two files: `cops.yml` (tier definitions and AI tuning) and `settings.yml` (cop count
scaling).

---

### Tier Configuration (`cops.yml`)

Tiers are numbered `1` through `5` under `Cops.Tiers`. Each tier defines the stats and equipment for that cop rank.

```yaml
Cops:
   Tiers:
      1:
         Display_Name: "&9Officer"   # Name shown above the NPC (supports & color codes)
         Health: 20.0                # Max health points
         Damage: 2.0                 # Melee damage per attack
         Speed: 1.0                  # Movement speed multiplier (1.0 = normal player speed)
         Cuff_Radius: 3.0            # Blocks from target at which this tier can attempt a cuff
         Can_Use_Weapons: false      # Whether this tier fires Gangland ranged weapons
         Skip_Cuffing: false         # If true, skips cuffing entirely and goes straight to lethal combat
         Weapon_Pool: # Items the cop can carry. One is selected randomly on spawn.
            - "WOODEN_SWORD"          # Vanilla Bukkit material name
            - "weapon:rifle"          # Custom Gangland weapon — prefix with "weapon:" then the weapon name
         Helmet: ""                  # Vanilla armor material for the helmet slot (empty = none)
         Chestplate: ""              # Vanilla armor material for the chestplate slot
         Leggings: ""                # Vanilla armor material for the leggings slot
         Boots: ""                   # Vanilla armor material for the boots slot
```

`Weapon_Pool` accepts two formats:

- Plain vanilla material (e.g., `IRON_SWORD`, `CROSSBOW`) — gives the NPC that vanilla item.
- `weapon:<name>` (e.g., `weapon:rifle`) — gives the NPC a configured Gangland weapon from the `weapon/` folder.

---

### AI Settings (`settings.yml` → `Cops.Behaviour`)

```yaml
Cops:
   Behaviour:
      Max_Per_Player: 8             # Hard cap on active cop NPCs per wanted player at any time
      AI_Tick_Rate: 10              # Ticks between each AI decision cycle. Lower = faster reactions, more CPU.
      Spawn_Check_Rate: 40          # Ticks between checks that decide whether to spawn more cops
      Cuff_Radius: 3.0              # Default cuff radius in blocks (individual tiers override this)
      Max_Cuff_Attempts: 3          # Cuff attempts before the cop gives up and switches to combat
      Cuff_Cooldown_Ticks: 100      # Ticks between consecutive cuffing attempts
      Alert_Range: 40.0             # Blocks within which an idle cop detects a wanted player
      Combat_Range: 4.0             # Melee attack range in blocks (ranged range is derived from this)
      Attack_Cooldown_Ticks: 20     # Ticks between melee attacks
```

---

### Spawn Settings (`settings.yml` → `Cops.Spawn`)

```yaml
Cops:
   Spawn:
      Min_Distance: 10.0            # Minimum spawn distance from the player (blocks)
      Max_Distance: 50.0            # Maximum spawn distance from the player (blocks)
      Phase1_Min_Distance: 30.0     # Target ring radius for Phase 1 (preferred, behind-player) spawn attempts
      Radius_Shrink_Step: 5.0       # How much the ring radius shrinks per Phase 2 iteration
      Vertical_Search_Range: 10     # Blocks searched above and below the player's Y to find valid ground
      Y_Offset: 0                   # Vertical offset from the player's Y when searching (0 = same level)
      Min_Open_Sides: 2             # Minimum open horizontal sides required at a spawn position
      Spawner_Preference_Radius: 80.0  # Blocks within which a placed cop spawner is preferred over a random position
      Visibility_Check_Distance: 48.0  # Distance within which nearby players trigger despawn visibility checks
      Phase1_Attempts: 20           # Number of spawn attempts in Phase 1 (preferred ring)
      Phase2_Attempts: 15           # Number of attempts per shrink step in Phase 2
```

---

### Navigation Settings (`settings.yml` → `Cops.Navigation`)

```yaml
Cops:
   Navigation:
      Recalculation_Ticks: 10       # Ticks between pathfinding path recalculations
      Stuck_Check_Interval: 5       # AI ticks between movement-progress samples for stuck detection
      Max_Stuck_Checks: 3           # Consecutive stuck samples before the cop retries pathfinding
      Max_Hopeless_Stuck_Checks: 6  # Consecutive stuck samples before navigation is considered permanently failed
      Hopeless_Close_Threshold: 8.0 # If the target is within this many blocks, a hopeless cop still tries to navigate directly
      Min_Progress_Distance: 0.75   # Minimum blocks moved between samples to count as progress (not stuck)
      Ranged_Min_Distance: 7.0      # Ranged cops hold their firing position when target is closer than this
      Ranged_Max_Distance: 12.0     # Ranged cops hold position when target is farther than this
      Min_Repath_After_Loss_Ticks: 2.0  # Minimum AI ticks before the cop re-paths after losing combat
```

---

### Return Settings (`settings.yml` → `Cops.Return`)

```yaml
Cops:
   Return:
      Max_Ticks: 600                # AI ticks before a cop with no target is force-despawned (600 ≈ 30 s)
      Station_Arrival_Distance: 3.0 # Blocks from the spawn station at which the cop considers itself arrived and despawns
```

---

### Cop Count Scaling (`settings.yml` → `Cops.Count`)

```yaml
Cops:
   Count:
      Formula_Enabled: false        # If true, evaluates the Formula string instead of the linear calculation
      Formula: "base + (level - 1) * perLevel"
      # Custom expression when Formula_Enabled is true.
      # Available variables: level, base, perLevel, max
      Base: 2                       # Cops spawned at 1 wanted star (also the 'base' variable in the formula)
      Per_Level: 1                  # Additional cops per additional wanted star (also 'perLevel' in the formula)
      Max: 8                        # Hard cap — result is always clamped to this value

   Starting_Ammo_Magazines: 3      # Full magazine reloads worth of ammo given to each cop NPC on spawn
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
