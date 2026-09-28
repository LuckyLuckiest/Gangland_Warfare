# Civilian NPC System

[Back to Developer Docs](./README.md)

---

## Overview

The civilian NPC system provides AI-driven non-player characters that populate the game world,
creating an immersive urban environment. Civilians wander, react to threats, and can serve as
traders with configurable inventories.

**Module:** `gangland-features/gangland-civilians`  
**Package:** `org.luckyraven.gangland.civilians.npc.*`

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
   leaves the squad and goes `IDLE`. A player who dies or goes down drops every squad hunting them.

Squads are created on the first hit and pruned once every member has left. Since 0.12.0 a civilian that enters
`COMBAT` without a hit (turf defenders, the Quartermaster, the idle re-engage) joins the same shared squad through
`FactionSquads` (implemented by `CivilianService`, injected into every `CivilianNpc`): one squad per faction and
target, whatever the entry path. Pedestrians (combat disabled) never join a squad.

### Tactics, melee and retreat (0.12.0)

`CivilianNpcFactory.applyTuning` gives each civilian its type's `AI.Combat.Tactics` engagement, `AI.Combat.Melee`
profile (cooldown = `Attack_Interval_Ticks`) and a fire-rate scale from `NpcFireRate.scale(Fire_Rate_Multiplier,
rangedAttack)`. A new squad takes the type's formation arc. The shipped arcs are narrow (gang_member 140,
turf_defender 100, quartermaster 120) because same-faction damage is not cancelled. `CivilianCombatBehavior` fires a
ranged attack at a target seen within `Alert_Range`; `Attack_Range` is the melee engage distance only. A member at
or below `AI.Combat.Retreat.Health_Fraction` calls Keystone's `takeCover` and stays in its squad. With no cover in
reach it fights on and asks again after 5 s.

### Shouts and recruitment (0.12.0)

Each faction squad's listener (`CivilianService.squadListener`) does two things:

1. **Shouts.** It speaks the squad's `NpcSquadSignal`s as faction shouts in chat through a gangland-api `SquadRadio`,
   the same engine as the police radio. Delivery tuning is the top-level `Shouts` block of `civilians.yml`, and the
   lines are the `Shouts` block of `npc/civilian_messages(_es).yml`, with `%faction%` added. Gangs ship several kinds
   muted (`Reposition`, `Route`, `Ack` and `Responding` are `[]`).
2. **Recruitment.** A `CONTACT` edge queues its spotter (never recruiting from inside the listener, which must not
   mutate its own squad). `tickAll` drains the queue after the NPC loop: `drainPendingRecruits` pulls every active,
   combat-enabled hostile civilian of the spotter's faction within `max(its own Alert_Range, Shouts.Range)` that
   isn't already fighting someone else onto the target, in the same squad. `Shouts.Range` (24) is above
   `Alert_Range` (16), so a shout reaches allies that haven't seen the fight. When at least one new ally was
   recruited, a `Rally` line reports the count (`%count%`). This runs with no player in range. `Shouts.Enabled:
   false` silences the chat lines but keeps recruitment.

`CivilianDeathListener` calls `memberDown` on the dying civilian's squad before its drops, so `Man_Down` /
`Leader_Down` are shouted. Shutdown clears the squad reverse index and any queued recruits.

---

## Civilian Configuration

### Per-Type Config (civilians.yml)

Each civilian type is defined with its own behavior parameters:

```yaml
Types:
   gang_member:
      Display_Name: "&c&lGang Member"
      Entity_Type: PLAYER
      Hostile: true
      Faction: gang_member          # Side it fights for (default: the type id)
      AI:
         Combat:
            Enabled: true
            Attack_Damage: 4.0
            Attack_Range: 12.0       # Melee engage distance; ranged types fire within Alert_Range (0.12.0)
            Attack_Interval_Ticks: 20
            Alert_Range: 16.0        # Sight range + how far it hears a faction member being hit (default 16.0)
            Search_Seconds: 20       # Squad unseen this long -> give up (default 20)
            Fire_Rate_Multiplier: 0.05   # Gun cadence; default 1 / Civilians.Behaviour.AI_Tick_Rate (0.12.0)
            Retreat:                 # (0.12.0)
               Enabled: true
               Health_Fraction: 0.3
               Radius: 12.0
            Tactics:                 # (0.12.0) same keys as Cops.Tactics
               Formation_Arc: 140.0
               Strafe_Degrees: 12.0
               Reposition_Ticks: 60
               Moving_Aim_Error: 0.15
            Melee:                   # (0.12.0) same keys as Cops.Melee
               Reach: 3.0
               Approach: 2.0
               Damage_Spread: 0.20
               Edge_Damage: 0.7
```

The top-level `Shouts` block (`Enabled`,
`Range`, `Target_Range`, gap, cooldown and priority keys) tunes shout delivery. See
[Migrating to 0.12.0](../migration-0.12.0.md), section 3, for every key and default.

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
