# Civilian NPC System

[Back to Developer Docs](./README.md)

---

## Overview

The civilian NPC system provides AI-driven non-player characters that populate the game world,
creating an immersive urban environment. Civilians wander, react to threats, and can serve as
traders with configurable inventories.

**Module:** `gangland-features/cops-n-crooks`  
**Package:** `me.luckyraven.copsncrooks.npc.civilian.*`

---

## Architecture

### Class Hierarchy

```
AbstractNpc
  └── CivilianNpc         Per-instance civilian with behavior state machine
          │
CivilianService           Lifecycle management, spawn/despawn coordination
CivilianSpawner           Spawn point management, wave-based spawning
CivilianTypeConfig        Per-type configuration (wander, flee, combat, inventory)
```

---

## CivilianService

Central service for civilian NPC lifecycle management.

**Key Responsibilities:**

- Maintain active civilian registry (UUID -> CivilianNpc)
- Coordinate spawning/despawning based on player proximity
- Provide NPC lookup for event handlers

**Key Methods:**

| Method                     | Description                         |
|----------------------------|-------------------------------------|
| `getNpc(UUID)`             | Look up civilian by entity UUID     |
| `spawnCivilian(type, loc)` | Spawn a new civilian of given type  |
| `despawnCivilian(UUID)`    | Remove a specific civilian          |
| `despawnAll()`             | Remove all active civilians         |
| `getActiveCivilians()`     | Get all currently spawned civilians |

---

## Civilian Behavior State Machine

Each `CivilianNpc` operates as a finite state machine with 5 states:

```
                    ┌─────────────────────────┐
                    │                         │
                    v                         │
              ┌──────────┐                    │
              │   IDLE   │◄───────────┐       │
              └────┬─────┘            │       │
                   │                  │       │
         ┌─────────┼─────────┐       │       │
         v         v         v       │       │
    ┌─────────┐ ┌──────┐ ┌──────┐   │       │
    │ WANDER  │ │ LOOK │ │ FLEE │───┘       │
    └────┬────┘ └──────┘ └──────┘           │
         │                                   │
         v                                   │
    ┌─────────┐                              │
    │ COMBAT  │──────────────────────────────┘
    └─────────┘
```

### State Descriptions

| State    | Trigger                  | Behavior                                 |
|----------|--------------------------|------------------------------------------|
| `IDLE`   | Default / no stimulus    | Stationary, may look around              |
| `WANDER` | Periodic timer           | Random movement within configured radius |
| `LOOK`   | Nearby player detected   | Turns to face the player                 |
| `FLEE`   | Nearby gunfire or threat | Runs away from threat source             |
| `COMBAT` | Attacked while armed     | Fights back against attacker             |

### State Transitions

- **IDLE -> WANDER:** Periodic wander timer triggers
- **IDLE -> LOOK:** Player enters detection radius
- **IDLE/WANDER -> FLEE:** Gunfire or wanted player nearby
- **IDLE/WANDER -> COMBAT:** Attacked and civilian is armed
- **FLEE -> IDLE:** Threat leaves area, cooldown expires
- **COMBAT -> IDLE:** Attacker dies, leaves range, or combat timeout
- **WANDER -> IDLE:** Wander destination reached or timeout

---

## Civilian Configuration

### Per-Type Config (civilians.yml)

Each civilian type is defined with its own behavior parameters:

```yaml
civilian_types:
  street_vendor:
    display_name: "&eStreet Vendor"
    skin: "vendor_skin_data"
    behaviour:
      wander_range: 10.0
      flee_range: 20.0
      combat_enabled: false
      look_range: 8.0
    inventory:
      title: "&6Street Vendor"
      size: 27
      items:
        0: "weapon:pistol{amount=1}"
        1: "ammo:9mm{amount=32}"
```

### CivilianNavigationConfig (12 methods)

| Setting               | Type     | Description                                |
|-----------------------|----------|--------------------------------------------|
| `wanderRange`         | `double` | Max distance from spawn for wandering      |
| `fleeRange`           | `double` | Distance to run when fleeing               |
| `speed`               | `double` | Movement speed multiplier                  |
| `pathRecalcInterval`  | `int`    | Ticks between path recalculation           |
| `stuckCheckInterval`  | `int`    | Ticks between stuck detection samples      |
| `maxStuckChecks`      | `int`    | Samples before considered stuck            |
| `minProgressDistance` | `double` | Min distance per sample to count as moving |

### CivilianInventoryConfig

Configuration for trader-type civilians:

| Field       | Type                   | Description                       |
|-------------|------------------------|-----------------------------------|
| `title`     | `String`               | Inventory display title           |
| `size`      | `int`                  | Inventory size (9-54, normalized) |
| `slotItems` | `Map<Integer, String>` | Slot -> item definition string    |

---

## Spawning System

### Proximity-Based Spawning (settings.yml)

Civilians spawn automatically when players enter the activation radius of registered spawner
points, similar to Minecraft village mechanics.

```
Player enters activation radius (60 blocks)
    → Spawner activates
    → Civilians spawn up to max_per_spawner (5)
    → Civilians persist while any player is within despawn radius (80 blocks)

All players leave despawn radius
    → All civilians from that spawner despawn
```

**Configuration:**

| Setting                | Default | Description                             |
|------------------------|---------|-----------------------------------------|
| `Activation_Radius`    | 60.0    | Player distance to activate spawner     |
| `Despawn_Radius`       | 80.0    | Distance beyond which civilians despawn |
| `Max_Npcs_Per_Spawner` | 5       | Max civilians per active spawner        |
| `Check_Interval`       | 100     | Ticks between proximity checks          |
| `Default_Type_Id`      | `""`    | Default type for untyped spawners       |

### Spawn Location Algorithm

Uses the same `EntitySpawner` as cops, with a two-phase algorithm:

**Phase 1 -- Preferred Ring:**

1. Attempt `Phase1_Attempts` (20) random positions at `Phase1_Min_Distance` (30 blocks)
2. Prefer positions behind the player
3. Validate: solid ground, sufficient open sides, not in blocks

**Phase 2 -- Shrinking Radius:**

1. Starting from phase-1 distance, shrink by `Radius_Shrink_Step` (5 blocks) per iteration
2. Try `Phase2_Attempts` (15) positions per shrink step
3. Continue until `Min_Distance` (10 blocks) reached

**Validation Checks:**

- Block below must be solid (not air/liquid)
- Position must have `Min_Open_Sides` (2) clear horizontal neighbors
- Position must be within `Vertical_Search_Range` (10 blocks) of target Y level

---

## Trader Interaction

When a player right-clicks a civilian NPC that has an inventory config, the
`CivilianInteractListener` opens a custom inventory using the `InventoryHandler` framework.

### Flow

```
Player right-clicks civilian NPC
    → NPCRightClickEvent fired
    → CivilianInteractListener.onNpcRightClick()
    → Look up CivilianNpc by entity UUID
    → Check if type has CivilianInventoryConfig
    → Create InventoryHandler with title, size
    → Populate slots using ItemParser
    → Open inventory for player
```

### Item Resolution

Items in the inventory config are parsed using the `ItemParser`:

```
"weapon:pistol{amount=1}"  →  Parsed by ItemConverterRegistry → ItemStack
"ammo:9mm{amount=32}"     →  Parsed by ItemConverterRegistry → ItemStack
```

---

## Events

| Event                | When Fired             | Key Data                    |
|----------------------|------------------------|-----------------------------|
| `CivilianDeathEvent` | Civilian NPC is killed | NPC reference, killer, type |
| `NpcEvent`           | Generic NPC event      | NPC reference               |

---

## Listeners

| Listener                   | Events Handled       | Purpose                       |
|----------------------------|----------------------|-------------------------------|
| `CivilianDeathListener`    | `EntityDeathEvent`   | Drop handling, event dispatch |
| `CivilianInteractListener` | `NPCRightClickEvent` | Trader inventory opening      |

---

## AI Tick Configuration (settings.yml)

```yaml
Civilians:
  Behaviour:
    Enabled: true           # Master toggle for civilian AI
    AI_Tick_Rate: 20        # Ticks between AI evaluations
```

The AI tick rate controls how frequently each civilian evaluates its state machine.
Lower values = faster reactions but higher CPU cost.

---

## Integration Points

- **Wanted System:** Armed civilians may increment wanted level when attacked
- **Weapon System:** Combat civilians use configured weapon pools
- **Loot System:** Civilians can drop items on death (configurable per type)
- **Experience System:** Killing civilians may award experience
