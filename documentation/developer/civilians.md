# Civilian NPC System

[Back to Developer Docs](./README.md)

---

## Overview

The civilian NPC system provides AI-driven non-player characters that populate the game world,
creating an immersive urban environment. Civilians wander, react to threats, and can serve as
traders with configurable inventories.

**Module:** `gangland-features/cops-n-crooks`  
**Package:** `org.luckyraven.gangland.copsncrooks.npc.civilian.*`

---

## Architecture

### Class Hierarchy

```
AbstractNpc
  ╰── CivilianNpc         Per-instance civilian with behavior state machine
          │
CivilianService           Lifecycle management, spawn/despawn coordination
CivilianSpawner           Spawn point management, wave-based spawning
CivilianTypeConfig        Per-type configuration (wander, flee, combat)
```

---

## CivilianService

Central service for civilian NPC lifecycle management.

**Key Responsibilities:**

- Maintain active civilian registry (UUID -> CivilianNpc)
- Coordinate spawning/despawning based on player proximity
- Provide NPC lookup for event handlers
- Hold the combat squads: one Keystone `NpcSquad` per (faction, target) — see
  [Factions and combat squads](#factions-and-combat-squads)

**Key Methods:**

| Method                     | Description                         |
|----------------------------|-------------------------------------|
| `getNpc(UUID)`             | Look up civilian by entity UUID     |
| `spawnCivilian(type, loc)` | Spawn a new civilian of given type  |
| `despawnCivilian(UUID)`    | Remove a specific civilian          |
| `despawnAll()`             | Remove all active civilians         |
| `getActiveCivilians()`     | Get all currently spawned civilians |
| `alertFaction(victim, attacker, playerAttacker)` | Squad a hit civilian and alert its faction (one hop) |
| `dropSquads(UUID)`         | Forget every squad hunting a target |

---

## Civilian Behavior State Machine

Each `CivilianNpc` operates as a finite state machine with 5 states:

```
                    ╭─────────────────────────╮
                    │                         │
                    v                         │
              ╭──────────╮                    │
              │   IDLE   │◄───────────╮       │
              ╰────┬─────╯            │       │
                   │                  │       │
         ╭─────────┼─────────╮        │       │
         v         v         v        │       │
    ╭─────────╮ ╭──────╮ ╭──────╮     │       │
    │ WANDER  │ │ LOOK │ │ FLEE │─────╯       │
    ╰────┬────╯ ╰──────╯ ╰──────╯             │
         │                                    │
         v                                    │
    ╭─────────╮                               │
    │ COMBAT  │───────────────────────────────╯
    ╰─────────╯
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
- **COMBAT -> IDLE:** Target dies, goes down or offline, or nobody in the civilian's squad has seen it for
  `AI.Combat.Search_Seconds`
- **WANDER -> IDLE:** Wander destination reached or timeout

---

## Factions and combat squads

Hostile civilians fight as squads (Keystone `NpcSquad`, one per faction and target, held by `CivilianService`):

1. **Hit.** `CivilianDamageListener` puts the victim in `COMBAT` against its attacker — a player, or an NPC/mob keyed
   by its entity UUID — and calls `CivilianService.alertFaction`: the victim joins its faction's squad against that
   attacker and reports a sighting at the attacker's position.
2. **One-hop alert.** Every other active, combat-enabled hostile civilian of the same `Faction` within **its own**
   `AI.Combat.Alert_Range` of the victim, and not already fighting someone else, takes the attacker as its target,
   enters `COMBAT` and joins the squad. Joining does not alert anyone further; a later hit on any member alerts that
   member's neighbours.
3. **Hunt.** `CivilianCombatBehavior` moves through `npc.pursue(target, squad, Alert_Range)`: members chase while
   anyone in the squad sees the target (within `Alert_Range`, line of sight), otherwise they search from the
   last-known position.
4. **Give up.** When nobody in the squad has seen the target for `AI.Combat.Search_Seconds`, a member clears its target,
   leaves the squad and goes `IDLE`. A player who dies or goes down drops every squad hunting him.

Squads are created on the first hit and removed once empty. A civilian that enters `COMBAT` without a hit (turf
defenders, the Quartermaster, the idle re-engage) hunts in a squad of its own, seeded with the target's position.
Pedestrians (combat disabled) never join a squad.

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

## Events

| Event                | When Fired             | Key Data                    |
|----------------------|------------------------|-----------------------------|
| `CivilianDeathEvent` | Civilian NPC is killed | NPC reference, killer, type |
| `NpcEvent`           | Generic NPC event      | NPC reference               |

---

## Listeners

| Listener                | Events Handled     | Purpose                       |
|-------------------------|--------------------|-------------------------------|
| `CivilianDeathListener` | `EntityDeathEvent` | Drop handling, event dispatch |
| `CivilianDamageListener` | `EntityDamageByEntityEvent`, `PlayerDownedEvent`, `PlayerDeathEvent` | Combat/flee reaction, faction alert, squad drop on death or downed |

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
- **Loot System:** Civilians can drop items on death (configurable per type). Each entry may append `@<chance>` (
  0.0–1.0) to roll independently per death — e.g. `"material:GOLD_INGOT@0.25"` drops ~25% of the time. Bare entries
  always drop.
- **Experience System:** Killing civilians may award experience
