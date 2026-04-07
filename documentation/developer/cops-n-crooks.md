# Cops N Crooks System

## Overview

The Cops N Crooks module (`gangland-features/cops-n-crooks`) is the AI-driven law enforcement and civilian NPC
subsystem for Gangland Warfare. It provides:

- **Police NPCs** -- AI-driven cop NPCs that pursue, cuff, and engage wanted players
- **Civilian NPCs** -- ambient and hostile NPCs with independent behavior (wander, flee, combat)
- **Wanted system** -- GTA-style 1-5 star wanted level tracking with escalating police response
- **Bounty system** -- player bounty tracking with configurable scaling and kill rewards
- **Kill combo system** -- consecutive kill tracking that feeds into wanted level escalation
- **Detainment and jail** -- handcuffing, jail cells, visual restraint effects

The module contains 91 classes organized across these packages:

```
me.luckyraven.copsncrooks
  bounty/            Bounty tracking and executor
  combo/             Kill combo system
  detainment/        Detainment states, registry, service
  entity/            Abstract NPC base, spawner framework, entity marks
  events/            Custom Bukkit events (11 total)
  jail/              Jail cells, registry, service
  listener/          Bukkit event listeners
  npc/civilian/      Civilian NPC system
  npc/police/        Cop NPC system
  wanted/            Wanted level tracking and executor
```

All NPC entities are backed by the [Citizens](https://citizensnpcs.com/) plugin API.


---

## NPC Architecture

### Inheritance Hierarchy

```
NpcBehavior<T>                   (interface -- tick/onEnter/onExit contract)
  CopBehavior                    (marker interface, T = CopNpc)
  CivilianBehavior               (marker interface, T = CivilianNpc)

AbstractNpc                      (base class -- weapon pipeline, navigation, combat)
  CopNpc                         (cop state machine, tier config, cuff lock)
  CivilianNpc                    (civilian state machine, group membership, entity target queue)
```

### AbstractNpc -- Base NPC Layer

**Package:** `me.luckyraven.copsncrooks.entity.npc`

`AbstractNpc` is the shared base class for all Citizens-backed NPC types. It owns:

| Responsibility                  | Key Methods                                                                                    |
|---------------------------------|------------------------------------------------------------------------------------------------|
| **Citizens NPC lifecycle**      | `isValid()`, `destroy(EntityMarkManager)`, `markForRemoval()`, `getEntity()`                   |
| **Three-tier attack pipeline**  | `attack(Player)`, `attackEntity(LivingEntity)` -- gangland weapon > vanilla ranged > melee     |
| **Gangland weapon integration** | `setHeldWeapon(Weapon, JavaPlugin)`, `performGanglandWeaponAttack()`, `triggerReload()`        |
| **Vanilla ranged attacks**      | `performVanillaRangedAttack(Player)` -- ray-traced ranged damage with `BOW`/`CROSSBOW`         |
| **Melee fallback**              | `performMeleeAttack(Player)`, `performMeleeAttackOnEntity(LivingEntity)` -- knockback + damage |
| **Citizens navigation**         | `navigateTo(Location)`, `stopNavigation()`, stuck detection, hopeless fallback                 |
| **Ranged hold**                 | `shouldHoldPursuitPosition(target)` -- ranged NPCs hold position within firing window          |
| **Wander pathfinding**          | `findForwardWanderDestination(min, max)` -- forward-biased cone scan for walkable destinations |

#### Attack Pipeline

When `attack(Player)` is called, the NPC selects the highest-priority available attack method:

```
1. Gangland weapon attack (GunWeapon projectile with reload management)
   - Consumes ammo, fires WeaponProjectile, plays shot sound
   - Auto-reloads when magazine empty or weapon broken
   - Cooldown = gun's projectile cooldown (minimum 5 ticks)

2. Vanilla ranged attack (BOW/CROSSBOW in main hand)
   - Ray-trace 35 blocks, particle + sound effects
   - Cooldown = 15 ticks

3. Melee fallback
   - Direct damage + knockback vector
   - Cooldown = 5 ticks
```

#### Navigation and Stuck Detection

`AbstractNpc` integrates Citizens pathfinding with a custom stuck-detection layer:

```
navigateTo(location)
  |
  +-- shouldRecalculateNavigation(location)
  |     Throttles by recalculation interval, checks distance delta >= 2.25 blocks
  |
  +-- updateNavigationProgress()    (called each tick by subclasses)
        Samples position every stuckCheckIntervalTicks
        If progress < minProgressDistance:
          consecutiveStuckChecks++
          If >= maxStuckChecks:        isNavigationStuck() = true
          If >= maxHopelessStuckChecks: isNavigationHopeless() = true
```

When navigation is hopeless, subclass behaviors call `resolveHopelessFallbackLocation(target)` which uses a
three-strategy fallback chain:

1. **Gap walk** -- `findLastReachableGroundBeforeGap()` walks block-by-block toward the target, returning the last
   standable position before an impassable gap
2. **Line approach** -- `findLineApproachLocation()` probes along the line from the target toward the NPC
3. **Ring approach** -- `findBestRingApproachLocation()` scans concentric rings around the target, scoring candidates
   by distance, ideal-radius deviation, and clear-shot availability for ranged NPCs

All location candidates are validated with `normalizeToStandableLocation()` which searches +2/-4 Y offsets for a
position with solid ground, passable feet/head, and no hazards (lava, water, cactus, magma, portals).

#### Abstract Contract

Subclasses must implement:

```java
boolean canUseWeapons();           // Whether this NPC type uses the weapon pipeline

double getAttackDamage();         // Base melee damage

void equip();                   // Apply armor + weapon to EntityEquipment

void cleanupTransientState();   // Release behavior resources on destroy
```

---

## Cop NPC System

### Package Structure

```
npc/police/
  CopService           Service facade -- initializes all cop components
  CopManager           Central manager -- spawn tasks, AI tasks, target resolution
  CopGroup             Collection of cops pursuing the same target player
  config/
    CopConfigProvider   Interface -- all cop configuration values
    YamlCopConfigProvider   YAML-backed implementation
    CopConfig           Raw parsed YAML data
    CopLoader           YAML loader
    CopTierConfig       Per-tier record (health, damage, speed, armor, weapons)
    CopSettings         Settings.yml cop section contract
  npc/
    CopNpc              Individual cop NPC instance
    CopNpcFactory       Creates and equips CopNpc instances
  spawn/
    CopSpawnManager     Extends EntitySpawner -- location finding + factory calls
    CopSpawner          EntitySpawnerPoint for cops
  state/
    CopState            Enum: IDLE, PURSUING, CUFFING, COMBAT, RETURNING
    CopBehavior         Interface extending NpcBehavior<CopNpc>
    CopBehaviorFactory  Creates the full state-to-behavior map
    CuffLockRegistry    Global one-cop-per-target cuffing lock
    behavior/
      IdleBehavior
      PursuingBehavior
      CuffingBehavior
      CombatBehavior
      ReturningBehavior
  targeting/
    TargetingManager    Interface
    WantedTargetingManager   Tracks wanted players, finds nearest target
```

### CopService

Entry point for the cop subsystem. Called once during plugin enable:

```java
CopService copService = new CopService();
CopManager copManager = copService.initialize(
		plugin, copConfigProvider, entityMarkManager,
		weaponService, spawnerRepository, detainmentService
);
```

Internally wires:

1. `WantedTargetingManager` -- tracks which players are wanted
2. `CopBehaviorFactory` -- creates behavior maps (shared `CuffLockRegistry`)
3. `CopNpcFactory` -- creates individual cop NPCs with equipment
4. `CopSpawnManager` -- finds spawn locations and delegates to factory
5. `CopManager` -- orchestrates spawn tasks and AI tasks per player

### CopManager

The central orchestrator with ~20 methods managing cop lifecycle:

| Method                                          | Purpose                                                              |
|-------------------------------------------------|----------------------------------------------------------------------|
| `onWantedStart(Player, Wanted)`                 | Registers wanted player, starts spawn + AI tasks                     |
| `onWantedEnd(Player)`                           | Unregisters target, lets cops organically find new targets or return |
| `onWantedLevelChange(Player, Wanted, old, new)` | Routes to start/end based on level transitions                       |
| `onCopAttackedAlert(CopNpc, Player)`            | Alerts ALL cops in group -- forces combat mode                       |
| `onCopAttacked(CopNpc, Player)`                 | Forces single cop into combat with attacker                          |
| `removeCopAttacker(UUID)`                       | Clears attacker from registry (death/leave), clears cop targets      |
| `getCopsForPlayer(UUID)`                        | Returns all cops assigned to a player                                |
| `isCopNpc(Entity)`                              | Checks if entity is a managed cop                                    |
| `findCopByEntity(Entity)`                       | Looks up CopNpc by Bukkit entity                                     |
| `shutdown()`                                    | Stops all tasks, despawns all cops                                   |

#### Spawn Task

A per-player `BukkitTask` running at `configProvider.getSpawnCheckRate()` interval:

```
Every spawn check interval:
  1. Skip if player is offline or detained
  2. Remove invalid/marked cops from group
  3. Calculate target count = getTargetCopCount(wantedLevel)
  4. Calculate tier = getTierForWantedLevel(wantedLevel)
  5. While currentCount < targetCount AND < maxCopsPerPlayer:
       Spawn cop near player at tier
       Set target, set combatForced if alert active
       Transition to PURSUING
```

Cops spawned during an active combat alert are immediately set to `combatForced = true`.

#### AI Task

A per-player `BukkitTask` running at `configProvider.getAiTickRate()` interval:

```
Every AI tick:
  1. Skip if player is offline
  2. Self-cleanup if group empty and player no longer wanted
  3. For each cop in group:
     a. Remove if marked or invalid
     b. resolveTarget(cop, defaultPlayer) -> LivingEntity
     c. cop.tick(target)
```

#### Target Resolution Priority

`resolveTarget()` determines what each cop should pursue, in order:

```
1. Current entity target (hostile civilian NPC) -- keep if still alive
2. Current player target -- keep if still wanted OR is a cop-attacker with combatForced
3. Nearest wanted player in same world (skip downed players)
4. Nearest player who previously attacked a cop (engage in COMBAT)
5. Nearest wanted hostile civilian NPC (in COMBAT state)
6. Pending entity attacker queue (self-defense, lowest priority)
7. No target found -> transition to RETURNING
```

### CopNpc

Individual cop NPC extending `AbstractNpc`. Key additions:

| Field/Method                                 | Purpose                                                     |
|----------------------------------------------|-------------------------------------------------------------|
| `CopTierConfig tierConfig`                   | Tier-specific stats (health, damage, speed, armor, weapons) |
| `Map<CopState, CopBehavior> behaviors`       | State machine behavior map                                  |
| `CopState currentState`                      | Current AI state                                            |
| `UUID targetPlayerId`                        | Current player target UUID                                  |
| `LivingEntity targetEntity`                  | Current non-player target (hostile civilian)                |
| `boolean combatForced`                       | If true, skip cuffing and go straight to combat             |
| `Deque<LivingEntity> pendingEntityAttackers` | Queue of entities that attacked this cop                    |
| `transitionTo(CopState)`                     | State machine transition with exit/enter callbacks          |
| `tick(LivingEntity)`                         | Runs one AI tick with current behavior                      |
| `attemptCuff(Player)`                        | Returns true if within cuff radius and has line of sight    |

### CopTierConfig

Record holding per-tier configuration:

```java
record CopTierConfig(
		int tier,                    // Tier number (1-5)
		String displayName,          // e.g. "Officer", "SWAT"
		double health,               // Max health in half-hearts
		double damage,               // Base melee damage
		double speed,                // Movement speed
		double cuffRadius,           // Blocks within which cuffing is attempted
		boolean canUseWeapons,       // Whether this tier uses the weapon pipeline
		boolean skipCuffing,         // Whether this tier skips cuffing (goes straight to combat)
		List<String> weaponNamePool, // Gangland weapon names for random selection
		List<ItemStack> weaponPool,  // Vanilla weapon fallbacks
		ItemStack helmet, chestplate, leggings, boots  // Armor
)
```

Higher tiers (e.g., SWAT, Military) have `skipCuffing = true` -- they engage in combat immediately.

### Cop AI State Machine

```
                                    +---------------------------+
                                    |          IDLE             |
                                    | Scans for targets within  |
                                    | alertRange. Stands still. |
                                    +---------------------------+
                                                |
                                    target detected within alertRange
                                    and has line of sight
                                                |
                                                v
+-------------------+              +---------------------------+
|    RETURNING      |<-------------|        PURSUING           |
| Navigates to      |  target lost | Navigates toward target.  |
| nearest station.  |  or detained | Ranged cops shoot while   |
| Waits for no      |              | closing distance.         |
| observers, then   |              +---------------------------+
| despawns.         |                   |                |
| Re-engages if     |      within cuffRadius       combatForced
| target freed.     |      + has LOS               OR skipCuffing
+-------------------+           |                        |
       ^                        v                        v
       |               +------------------+    +------------------+
       |               |     CUFFING      |    |     COMBAT       |
       +<--------------| Wind-up timer,   |    | Attacks target   |
       |  cuff success | then attemptCuff.|    | within range.    |
       |               | One cop per      |    | Ranged cops hold |
       +<--------------| target via       |    | firing position. |
          target out   | CuffLockRegistry.|    | Melee cops close |
          of range     +------------------+    | distance.        |
                              |                +------------------+
                              |                        |
                         target escapes          target detained
                         -> PURSUING             or killed
                                                       |
                                                       v
                                               +------------------+
                                               |    RETURNING     |
                                               +------------------+
```

#### State Details

**IDLE** (`IdleBehavior`)

- Stands at spawn position with navigation stopped
- Each tick: checks if resolved target is within `alertRange` and has line of sight
- Transition: `IDLE -> PURSUING` when target detected

**PURSUING** (`PursuingBehavior`)

- Navigates toward target using `resolvePursuitLocation()`
- Falls back to `resolveHopelessFallbackLocation()` when navigation is permanently stuck
- Ranged cops fire while closing distance (`cop.attack()` called when LOS + canAttack)
- Transition conditions when within `cuffRadius` + has LOS:
    - `skipCuffing` or `combatForced` -> `COMBAT`
    - Otherwise -> `CUFFING`
- Transition: `PURSUING -> RETURNING` when target goes offline/dies/is detained

**CUFFING** (`CuffingBehavior`)

- Only one cop per target at a time, enforced by `CuffLockRegistry`
- On enter: attempts to acquire cuff lock; if another cop holds it -> `PURSUING`
- Wind-up timer counts down `cuffingCooldown` AI ticks, firing `DuringCuffingEvent` each tick
- On wind-up complete: calls `cop.attemptCuff(player)`
    - Success -> fires `CuffedEvent`, transitions to `RETURNING`
    - Failure (target moved out of range) -> releases lock, transitions to `PURSUING`
- Entity targets (hostile civilians) bypass cuffing entirely -> `COMBAT`
- On exit: always releases the cuff lock

**COMBAT** (`CombatBehavior`)

- Attacks target within `combatRange` (melee) or `combatRange * 3` (ranged)
- Ranged cops hold firing position via `shouldHoldPursuitPosition()`; melee cops close distance
- Uses hopeless fallback when navigation fails
- Transition: `COMBAT -> RETURNING` when target goes offline/dies/is detained

**RETURNING** (`ReturningBehavior`)

- Finds nearest registered spawner location (or falls back to NPC's spawn location)
- Navigates to station; considers itself arrived at `stationArrivalDistance`
- Before despawning, checks if other players are watching (60-degree cone check)
    - If observed: delays despawn up to `maxReturnTicks * 2`
    - If not observed or timeout exceeded: `markForRemoval()`
- Re-engagement: if target player is freed (no longer restrained) before reaching station,
  transitions back to `COMBAT` (if combatForced) or `PURSUING`

### CuffLockRegistry

Global mutex ensuring only one cop across ALL groups can cuff a given target at any time:

```java
boolean tryAcquire(UUID targetId, UUID copId)  // Atomic putIfAbsent

boolean isOwner(UUID targetId, UUID copId)      // Check ownership

void release(UUID targetId, UUID copId)      // Release only if owner

void forceRelease(UUID targetId)             // Admin force-release

void releaseByCop(UUID copId)                // Release all held by cop (on cop death)
```

### CopSpawnManager

Extends `EntitySpawner<CopSpawner>`. Spawn location strategy:

```
spawnNearPlayer(target, tier):
  1. findClosestSpawnerLocation(target) -- check registered spawner within preference radius
     Success -> copNpcFactory.createCop(location, tier)

  2. findSpawnLocation(target) -- two-phase random search
     Phase 1: preferred ring behind player (p1MinDistance to maxDistance)
     Phase 2: shrinking radius fallback (maxDistance down to minDistance)
     Success -> copNpcFactory.createCop(location, tier, behindPlayer=true)

  3. Return null -- no valid location this interval
```

Tier selection: `tier = min(wantedLevel, maxTier)`

Cop count: configurable per wanted level via `getCopsPerWantedLevel()` map, capped at `maxCopsPerPlayer`.


---

## Civilian NPC System

### Package Structure

```
npc/civilian/
  CivilianService       Lifecycle manager -- spawning, AI ticking, proximity spawners
  CivilianGroup         Spawn-group with stay-together behavior
  CivilianState         Enum: IDLE, WANDERING, FLEEING, COMBAT
  config/
    CiviliansConfig       Top-level config (types + groups)
    CiviliansLoader       YAML loader for civilians.yml
    YamlCiviliansConfigProvider   YAML-backed provider
    CivilianTypeConfig       Per-type record (full NPC config)
    CivilianGroupConfig      Group definition (member types + counts)
    CivilianAIBehaviorConfig Per-type AI behavior settings
    CivilianNavigationConfig Navigation settings (implements NpcNavigationConfig)
    CivilianWearableConfig   Armor slot strings
    CivilianDropConfig       Death drop configuration
    CivilianInventoryConfig  Trader inventory configuration
    CivilianSettings         Settings.yml civilian section contract
  npc/
    CivilianNpc              Individual civilian NPC instance
    CivilianNpcFactory       Creates and equips CivilianNpc instances
  spawn/
    CivilianSpawnManager     Extends EntitySpawner -- proximity + manual spawning
    CivilianSpawner          EntitySpawnerPoint for civilians
  state/
    CivilianBehavior         Interface extending NpcBehavior<CivilianNpc>
    CivilianBehaviorFactory  Creates the state-to-behavior map
    behavior/
      CivilianIdleBehavior
      CivilianWanderBehavior
      CivilianFleeBehavior
      CivilianCombatBehavior
      CivilianLookController   Shared ambient look-around logic
```

### CivilianService

Central manager for all civilian NPCs. Initialized once:

```java
CivilianService civilianService = new CivilianService();
civilianService.

initialize(plugin, markerConfig, entityMarkManager,
           spawnerRepository, civilianSettings, spawnConfigProvider,
           itemParser, weaponService);
```

Two scheduled tasks run after initialization:

1. **AI tick timer** (`civilianAiTickRate`) -- calls `tickAll()` which iterates all active NPCs, calling `npc.tick()`
   and removing dead/marked NPCs
2. **Proximity spawner timer** (`civilianSpawnerCheckInterval`) -- checks registered spawner locations against
   online player positions:
    - Player within activation radius -> spawn NPCs (individual or group)
    - All players outside despawn radius -> mark spawner's NPCs for removal

Key methods:

| Method                          | Purpose                                           |
|---------------------------------|---------------------------------------------------|
| `register(CivilianNpc)`         | Registers an already-spawned NPC for AI ticking   |
| `registerGroup(CivilianGroup)`  | Registers a civilian group                        |
| `getNpc(UUID)`                  | Looks up NPC by entity UUID                       |
| `getActiveNpcs()`               | Returns all active civilian NPCs                  |
| `spawnGroup(Location, groupId)` | Spawns a complete group from civilians.yml config |
| `shutdown()`                    | Destroys all NPCs and clears registries           |

### CivilianNpc

Individual civilian NPC extending `AbstractNpc`. Key additions:

| Field/Method                            | Purpose                                                |
|-----------------------------------------|--------------------------------------------------------|
| `CivilianTypeConfig typeConfig`         | Full type configuration                                |
| `String groupId`                        | Group key (nullable)                                   |
| `CivilianGroup group`                   | Parent group reference (nullable)                      |
| `Integer spawnerId`                     | Spawner that created this NPC (nullable)               |
| `CivilianState currentState`            | Current AI state                                       |
| `UUID targetPlayerId`                   | Player combat target                                   |
| `Deque<LivingEntity> entityTargetQueue` | NPC-to-NPC combat targets (priority queue)             |
| `boolean wantedByPolice`                | True when hostile civilian enters COMBAT (cops pursue) |
| `Location lastAttackerLocation`         | Used by flee behavior                                  |
| `transitionTo(CivilianState)`           | State transition with exit/enter callbacks             |
| `tick()`                                | Runs one AI tick                                       |
| `isHostile()`                           | Whether this NPC type is configured as hostile         |
| `addEntityTargetToFront(LivingEntity)`  | Bumps attacker to front of entity target queue         |

The `wantedByPolice` flag is automatically set when a hostile civilian transitions to `COMBAT` and cleared when
leaving `COMBAT`. Cops use `CivilianNpc.isWantedByPolice()` to identify civilians to pursue.

### CivilianTypeConfig

Record holding per-type configuration loaded from `civilians.yml`:

```java
record CivilianTypeConfig(
		String typeId,                         // e.g. "pedestrian", "gang_member"
		String displayName,                    // Color-coded name
		EntityType entityType,                 // Citizens body type
		double health,                         // Max health
		boolean hostile,                       // Whether NPC attacks players
		CivilianWearableConfig wearables,      // Armor (raw ItemParser strings)
		List<String> itemPool,                 // Random items given on spawn
		List<String> weaponNamePool,           // Gangland weapon names (hostile types)
		List<ItemStack> weaponPool,            // Vanilla weapon fallback
		CivilianDropConfig drops,              // Death drop config
		CivilianAIBehaviorConfig ai,           // Per-type AI settings
		CivilianInventoryConfig inventory      // Trader inventory (nullable)
)
```

### CivilianAIBehaviorConfig

Per-type AI behavior settings:

```java
record CivilianAIBehaviorConfig(
		boolean wanderEnabled,       // Whether NPC wanders when idle
		int wanderRange,         // Blocks radius for wander targets
		boolean fleeEnabled,         // Whether NPC flees when damaged
		int fleeRange,           // How far (blocks) the NPC runs
		boolean combatEnabled,       // Whether NPC engages in combat
		double attackDamage,        // Base damage per attack
		double attackRange,         // Detection/attack range (blocks)
		int attackIntervalTicks  // Server ticks between attacks
)
```

### Civilian AI State Machine

```
                    +------------------------+
                    |         IDLE           |
                    | Stands still, looks    |
                    | around at nearby       |
                    | entities. Re-engages   |
                    | remembered targets     |
                    | within 2x attackRange. |
                    +------------------------+
                       |               |
           70% chance after        remembered target
           idle countdown          returns in range
                       |               |
                       v               v
           +-----------------+   +-----------------+
           |   WANDERING     |   |     COMBAT      |
           | Random forward- |   | Pursues and     |
           | biased movement.|   | attacks target  |
           | Group members   |   | (player or NPC).|
           | return to group |   | Entity targets  |
           | center if       |   | take priority   |
           | straying.       |   | (self-defense). |
           +-----------------+   | Gives up at 4x  |
                  |              | attackRange.     |
           arrival or           +-----------------+
           stuck x3                    |
                  |              target lost or
                  v              out of range
                IDLE                   |
                                       v
                                     IDLE

           (from any state when damaged and flee enabled)
                       |
                       v
           +-----------------+
           |    FLEEING      |
           | Runs away from  |
           | attacker until  |
           | fleeRange       |
           | exceeded.       |
           +-----------------+
                  |
           arrived or
           navigation hopeless
                  |
                  v
                IDLE
```

#### State Details

**IDLE** (`CivilianIdleBehavior`)

- Random idle countdown: 40-100 AI ticks
- Ambient look-around every 15-35 ticks at entities within 12 blocks
- Re-engage check: if a remembered player or entity target returns within `2 * attackRange`, transition to `COMBAT`
- When countdown expires and `wanderEnabled`: 70% chance -> `WANDERING`, 30% chance -> reset idle timer
- When `wanderEnabled` is false: always resets idle timer

**WANDERING** (`CivilianWanderBehavior`)

- Navigates to random forward-biased destinations using `findForwardWanderDestination(min, max)`
- Periodically redirects mid-path (every 60-120 ticks) to appear natural
- Looks at nearby entities while walking (every 10-20 ticks)
- Group coherence: if member has strayed beyond `stayTogetherRange`, navigates to group center instead
- Max 3 stuck reverts before giving up -> `IDLE`
- Reverts to `IDLE` on arrival or stuck

**FLEEING** (`CivilianFleeBehavior`)

- Triggered externally by listeners when a non-hostile NPC takes damage
- Calculates direction vector away from `lastAttackerLocation`
- Navigates to a point `fleeRange` blocks away in the opposite direction
- Checks arrival every 20 ticks: if no longer navigating or navigation hopeless -> `IDLE`
- Also reverts when distance from flee origin exceeds `fleeRange`
- On exit: clears `lastAttackerLocation`

**COMBAT** (`CivilianCombatBehavior`)

- Only available for hostile civilian types
- Target resolution: entity target queue (self-defense priority) > player target (fallback)
- Navigates toward target; ranged NPCs hold firing position via `shouldHoldPursuitPosition()`
- Attacks when within `attackRange` and cooldown elapsed
- Gives up at `4 * attackRange` distance
- On exit: preserves targets so `IDLE` can re-engage if target returns

### CivilianGroup

Manages a spawn-group of civilians that stay together:

```java
// Spawned from civilians.yml group definitions
CivilianGroup group = civilianService.spawnGroup(location, "street_gang");

// Group tracks:
group.

getGroupCenter()          // Average location of alive members
group.

isMemberStraying(npc)     // Distance to center > stayTogetherRange
group.

pruneDeadMembers()        // Remove invalid members
group.

isEmpty()                 // All members dead
```

Group coherence is enforced during `WANDERING`: if a member's distance from the group center exceeds
`stayTogetherRange`, it navigates back to the center instead of picking a random destination.


---

## Entity Spawning Framework

### EntitySpawner<S>

**Package:** `me.luckyraven.copsncrooks.entity`

Abstract base providing the shared spawn-location algorithm and persistent spawner registry.
Extended by `CopSpawnManager` and `CivilianSpawnManager`.

#### Spawner Registry

Spawner points are persisted via `IRepository<S>`:

```java
setSpawnerLocation(Location)    // Register new spawner point (auto-incrementing ID)

removeSpawner(int id)           // Delete spawner

getSpawnerLocations()           // List all registered locations

getSpawnerIds()                 // List all IDs

reloadSpawners()                // Reload from database
```

#### Two-Phase Spawn Location Algorithm

```
findSpawnLocation(player):

  PHASE 1 -- Preferred Ring (behind player)
  +--------------------------------------------------+
  | For each of spawnPhase1Attempts attempts:         |
  |   Random angle, random distance [p1Min, maxDist]  |
  |   Check chunk loaded                              |
  |   findGroundNearY (±verticalSearchRange)          |
  |   Require: behind player (>90 deg from facing)    |
  |   Require: indoor/outdoor matches player          |
  +--------------------------------------------------+

  PHASE 2 -- Shrinking Ring (any direction)
  +--------------------------------------------------+
  | For max = maxDist down to minDist (step shrink):  |
  |   For each of spawnPhase2Attempts attempts:       |
  |     Random angle, random distance [min, max]      |
  |     Same ground/chunk/indoor validation           |
  |     No behind-player requirement                  |
  +--------------------------------------------------+
```

Ground validation (`isValidGround`):

- Ground block must be solid
- Feet, head, and above-head blocks must be empty (passable)
- At least `minOpenHorizontalSides` open sides at feet and head level

Additional checks:

- `isVisibleToOtherPlayers(location, exclude)` -- 60-degree cone visibility check used during cop despawn
- `findClosestSpawnerLocation(player)` -- prefers registered spawner within `spawnerPreferenceRadius`

### EntitySpawnerPoint

Base class for spawner point data:

```java
int getId()

Location getLocation()

void setLocation(Location)
```

Concrete types: `CopSpawner`, `CivilianSpawner` (adds `typeId` and `groupId` for type-specific spawning).

### EntityMark

Enum classifying NPC entities:

```java
enum EntityMark {
	CIVILIAN,    // Civilian NPC
	POLICE,      // Cop NPC
	UNSET;       // Unknown / not an NPC

	boolean isCivilian()       // true for CIVILIAN and POLICE

	boolean countForWanted()   // true for CIVILIAN and POLICE
}
```

`EntityMarkManager` maintains a mapping from entity UUID to `EntityMark`, used by listeners and the wanted system
to determine if a killed entity should increment the player's wanted level.


---

## Wanted System

**Package:** `me.luckyraven.copsncrooks.wanted`

### Wanted

Core data class tracking a player's wanted status:

```java
// Fields:
int level         // Current wanted level (0 = not wanted, capped at maxLevel)
int maxLevel      // Maximum wanted level (configurable)
int increments    // How many levels to add per incrementLevel() call
boolean wanted        // Derived: level > 0
Player owner         // The owning player

// Key methods:
void setLevel(int level)     // Clamps [0, maxLevel], fires events

void incrementLevel()        // setLevel(level + increments)

void decrementLevel()        // setLevel(level - 1)

String getLevelStars()         // e.g. "★★★☆☆" for 3/5

void reset()                 // Sets level to 0 and stops timer
```

When `setLevel()` changes the level:

1. Fires `WantedLevelChangeEvent` (cancellable)
2. If level went from 0 -> positive: fires `WantedStartEvent`
3. If level went from positive -> 0: fires `WantedEndEvent`

Events are always fired synchronously on the main thread. If called from an async thread, the level change is
deferred via `Bukkit.getScheduler().runTask()`.

### WantedExecutor

Drives the periodic wanted-level decrease timer. Configuration comes via `WantedSettings`:

```
Timer interval = timerTime * (timerMultiplierAmount ^ currentLevel)
```

Each timer tick:

1. Optionally withdraw money: `takeMoneyAmount * (takeMoneyMultiplier ^ level)`
2. Fire `WantedEvent` (cancellable)
3. Decrement wanted level by 1
4. Send level-decrease message with star display
5. Stop timer when level reaches 0

### WantedSettings Contract

```java
// Timer configuration:
double getTimerTime()                // Base interval in seconds

boolean isTimerMultiplierEnabled()   // Whether to scale interval by level

double getTimerMultiplierAmount()    // Multiplier base (e.g. 1.5)

// Money loss:
double getTakeMoneyAmount()          // Base money taken per tick

double getTakeMoneyMultiplier()      // Money scaling multiplier per level
```

---

## Kill Combo System

**Package:** `me.luckyraven.copsncrooks.combo`

### KillCombo

Tracks consecutive kills per player and triggers wanted level increments at configurable thresholds:

```java
KillCombo killCombo = new KillCombo(plugin, wantedKillCounter);

// Configurable callbacks:
killCombo.

setOnWantedLevelTrigger(event ->{ /* increment wanted */ });
		killCombo.

setOnComboIncrement(event ->{ /* display combo UI */ });
		killCombo.

setOnComboReset(event ->{ /* clear combo UI */ });
		killCombo.

setOnPlayerDeath(playerId ->{ /* handle death reset */ });
```

#### Flow

```
Player kills entity
  |
  recordKill(killer, wantedKiller, killed, resetAfterSeconds)
  |
  +-- Get or create KillComboTracker for player
  +-- tracker.addKill(killed, points=1)
  +-- Fire onComboIncrement callback
  +-- checkWantedLevelTrigger:
  |     Compare pointKillCount against wantedKillCounter thresholds
  |     Thresholds auto-scale linearly if fewer than maxLevel entries
  |     If threshold met -> fire onWantedLevelTrigger callback
  +-- tracker.restartTimer() (resets inactivity countdown)
```

### KillComboTracker

Per-player tracker that holds:

- Kill count and point kill count
- List of killed entities
- Inactivity timer (resets combo after `wantedKillComboResetAfter` seconds of no kills)

When the timer expires, the tracker fires the combo reset callback and removes itself.


---

## Bounty System

**Package:** `me.luckyraven.copsncrooks.bounty`

### Bounty

Core data class tracking a player's bounty:

```java
// Fields:
double amount                        // Current total bounty
double baseAmount                    // Base bounty per wanted level
double levelMultiplier               // User level scaling factor
Map<CommandSender, Double> userSetBounty  // Per-setter bounty contributions

// Key methods:
void addBounty(CommandSender, amount, userLevel)  // Level-scaled add

void addBounty(CommandSender, amount)              // Direct add

void removeBounty(CommandSender)                   // Remove sender's contribution

double calculateLevelScaledBounty(base, level)       // base * (1 + level * multiplier / 10)

double getAutoBountyIncrease(userLevel, wantedLevel) // baseAmount * wantedLevel, then scaled

void resetBounty()                                  // Clear all
```

### BountyExecutor

Drives the periodic bounty-increase timer:

```
Timer interval = settings.getTimeInterval() seconds

Each tick:
  1. Calculate increase: currentBounty * timerMultiple, then level-scale
  2. Cap at timerMax
  3. Fire BountyEvent (cancellable)
  4. Apply increase to bounty amount
```

---

## Detainment and Jail System

### DetainmentState

```java
enum DetainmentState {
	NORMAL,       // Free player
	HANDCUFFED,   // Restrained, cannot interact, visual effects
	JAILED        // Teleported to jail cell, fully restrained
}
```

### DetainedPlayer

Data class:

```java
class DetainedPlayer {
	UUID            playerId;
	Integer         jailId;      // Nullable -- only set when JAILED
	DetainmentState state;
}
```

### DetainmentService

Central service for all detainment operations:

| Method                    | Purpose                                                           |
|---------------------------|-------------------------------------------------------------------|
| `getState(Player)`        | Returns current `DetainmentState`                                 |
| `isHandcuffed(Player)`    | State == `HANDCUFFED`                                             |
| `isJailed(Player)`        | State == `JAILED`                                                 |
| `isRestrained(Player)`    | `HANDCUFFED` or `JAILED`                                          |
| `handcuff(Player)`        | Apply handcuffs: set state, apply visuals, send title/action bar  |
| `jail(Player, jailId)`    | Jail player: register in jail, set state, teleport, apply visuals |
| `release(Player)`         | Full release: unregister from jail, clear state, clear visuals    |
| `setState(Player, state)` | Low-level state change with visual sync                           |
| `handleJoin(Player)`      | Re-apply visuals on login; teleport jailed players back to cell   |
| `handleQuit(Player)`      | Handcuffed players auto-escalate to JAILED on quit                |
| `handleRespawn(Player)`   | Jailed players are re-teleported to cell on respawn               |
| `tickVisuals(Player)`     | Periodic visual refresh (called from scheduled task)              |

#### Visual Effects

Restrained players receive:

- **Slowness IV** (infinite duration)
- **Blindness I** (infinite duration)
- Forced inventory close on initial restraint
- Action bar messages: `"Handcuffed - You cannot interact"` / `"Jailed - You cannot interact"`
- Title messages on state transitions

Effects are skipped for spectator mode, dead players, and downed players.

#### Quit-to-Jail Escalation

When a handcuffed player logs out, their state automatically escalates to `JAILED`. On rejoin, they are teleported
to their assigned jail cell. This prevents escape-by-disconnect.

### JailRegistry

In-memory registry of jail cells:

```java
Jail getJail(int id)                      // Get jail by ID

Location getJailLocation(int id)          // Get jail location by ID

Location getJailLocation(UUID playerId)   // Get jail location for a jailed player

Jail setJailLocation(int id, Location, maxCapacity)  // Create or update

void detainPlayer(int jailId, UUID)       // Add player to jail (releases from previous)

void releasePlayer(UUID)                  // Remove player from all jails

Integer getJailIdForPlayer(UUID)          // Which jail is a player in?

Integer findAvailableJailId()             // First available jail cell
```

### JailService

Persistence layer wrapping `JailRegistry` with `IRepository<Jail>`:

```java
Jail setJailLocation(Location, maxCapacity)   // Create + persist

void detainPlayer(int jailId, UUID playerId)  // Detain + persist

void removeJail(int jailId)                   // Delete + persist

void releasePlayer(UUID playerId)             // Release + persist all

void saveAll()                                // Flush to database

void reload()                                 // Clear + reload from database
```

---

## Events

The module fires 11 custom Bukkit events:

### Wanted Events (`events.wanted`)

| Event                    | When Fired                                    | Key Fields                                 | Cancellable |
|--------------------------|-----------------------------------------------|--------------------------------------------|-------------|
| `WantedStartEvent`       | Player's wanted level goes from 0 to positive | `player`, `wanted`, `wantedLevel`          | No          |
| `WantedLevelChangeEvent` | Any wanted level change                       | `player`, `wanted`, `oldLevel`, `newLevel` | Yes         |
| `WantedEndEvent`         | Wanted level reaches 0                        | `player`, `wanted`                         | No          |
| `WantedEvent`            | Periodic timer tick (before decrement)        | Inherits from base event                   | Yes         |

### Bounty Events (`events.bounty`)

| Event         | When Fired                    | Key Fields      | Cancellable |
|---------------|-------------------------------|-----------------|-------------|
| `BountyEvent` | Periodic bounty increase tick | `amountApplied` | Yes         |

### Kill Combo Events (`events.combo`)

| Event            | When Fired                        | Key Fields          | Cancellable |
|------------------|-----------------------------------|---------------------|-------------|
| `KillComboEvent` | Combo increment or wanted trigger | `player`, `tracker` | No          |

### Police Events (`events.police`)

| Event                | When Fired                       | Key Fields                                                                 | Cancellable |
|----------------------|----------------------------------|----------------------------------------------------------------------------|-------------|
| `CuffedEvent`        | Cop successfully cuffs a player  | `cop`, `target`, `cuffRadius`, `maxAttempts`                               | No          |
| `DuringCuffingEvent` | Each tick during cuffing wind-up | `cop`, `target`, `cuffRadius`, `maxAttempts`, `cooldown`, `remainingTicks` | No          |

### NPC Events (`events.npc`)

| Event                | When Fired                | Key Fields                                       | Cancellable |
|----------------------|---------------------------|--------------------------------------------------|-------------|
| `NpcEvent`           | Base class for NPC events | `npc` (AbstractNpc)                              | No          |
| `CopDeathEvent`      | Cop NPC dies              | `npc`, `killer` (nullable)                       | No          |
| `CivilianDeathEvent` | Civilian NPC dies         | `civilianNpc`, `killer` (nullable), `experience` | No          |

### Listening to Events

```java

@EventHandler
public void onWantedStart(WantedStartEvent event) {
	Player player = event.getPlayer();
	int    level  = event.getWantedLevel();
	// Spawn cops, update scoreboard, etc.
}

@EventHandler
public void onCopDeath(CopDeathEvent event) {
	Player killer = event.getKiller();
	if (killer != null) {
		// Award experience, increment wanted level, etc.
	}
}

@EventHandler
public void onCivilianDeath(CivilianDeathEvent event) {
	CivilianNpc npc = event.getCivilianNpc();
	double      xp  = event.getExperience();
	// Award XP, check if NPC counts for wanted level, etc.
}

@EventHandler
public void onCuffed(CuffedEvent event) {
	Player target = event.getTarget();
	// Trigger detainment, jail the player, etc.
}
```

---

## Listeners

**Package:** `me.luckyraven.copsncrooks.listener`

| Listener                   | Package               | Handles                                                        |
|----------------------------|-----------------------|----------------------------------------------------------------|
| `CopListener`              | `listener.detainment` | Cop NPC damage events -- alerts group, tracks attackers        |
| `DetainmentListener`       | `listener.police`     | Detainment state changes, visual effects, movement restriction |
| `CuffingListener`          | `listener.police`     | Cuffing process events, UI feedback during wind-up             |
| `CivilianDeathListener`    | `listener.civilian`   | Civilian death -- drops, experience, wanted level tracking     |
| `CivilianDamageListener`   | `listener.civilian`   | Civilian damaged -- triggers flee/combat, cop-civilian combat  |
| `CivilianInteractListener` | `listener.civilian`   | Right-click interaction -- opens trader inventory              |

---

## Configuration

### settings.yml (Cop/Wanted/Detainment sections)

The following sections in `gangland-impl/src/main/resources/settings.yml` control the system. Feature modules access
these via contract interfaces (`CopSettings`, `WantedSettings`, `BountySettings`, `CivilianSettings`).

#### Wanted Section

```yaml
Wanted:
   enable: true
   repeating_timer: 60          # Base interval (seconds) for wanted decay
   timer_multiplier_enabled: true
   timer_multiplier: 1.5        # Timer interval scales by multiplier^level
   take_money: true
   take_money_amount: 100.0     # Base money lost per decay tick
   take_money_multiplier: 1.2   # Money scales by multiplier^level
   max_level: 5                 # Maximum wanted stars
   increments: 1                # Stars added per incrementLevel()
   kill_combo:
      kill_counter: [3, 5, 7, 10, 15]   # Kills needed per level threshold
      reset_after: 30                     # Seconds of inactivity before combo resets
```

#### Cop Behavior Section

```yaml
Cops:
   max_cops_per_player: 8
   cops_per_wanted_level: # Map of wanted level -> target cop count
      1: 2
      2: 3
      3: 5
      4: 6
      5: 8
   behaviour:
      ai_tick_rate: 4            # Ticks between AI evaluations
      spawn_check_rate: 60       # Ticks between spawn checks
      cuff_radius: 3.5           # Blocks
      max_cuff_attempts: 3
      cuff_cooldown_ticks: 40    # Wind-up duration
      alert_range: 32.0          # Blocks
      combat_range: 5.0          # Melee range (ranged = 3x)
      attack_cooldown_ticks: 10
   spawn:
      min_distance: 20.0
      max_distance: 45.0
      phase1_min_distance: 30.0
      radius_shrink_step: 5.0
      vertical_search_range: 10
      spawn_y_offset: 0
      min_open_horizontal_sides: 2
      spawner_preference_radius: 40.0
      visibility_check_distance: 30.0
      phase1_attempts: 10
      phase2_attempts: 5
   return:
      max_return_ticks: 200
      station_arrival_distance: 3.0
   navigation:
      recalculation_ticks: 10
      stuck_check_interval_ticks: 5
      max_stuck_checks: 4
      max_hopeless_stuck_checks: 8
      hopeless_close_threshold: 5.0
      min_progress_distance: 0.5
      ranged_min_distance: 8.0
      ranged_max_distance: 25.0
      min_repath_after_loss_ticks: 2
   weapon:
      starting_ammo_magazines: 3
```

#### Detainment Section

```yaml
Detainment:
   jail_max_capacity: 10        # Max players per jail cell
```

#### Civilian Section

```yaml
Civilians:
   behaviour:
      ai_enabled: true
      ai_tick_rate: 4
      spawner_check_interval: 40
   spawn:
      activation_radius: 48.0
      despawn_radius: 64.0
      max_npcs_per_spawner: 3
      default_type_id: "pedestrian"
```

### cops.yml

Per-tier cop configuration loaded by `CopLoader`:

```yaml
tiers:
   1:
      display_name: "&9Officer"
      health: 20.0
      damage: 3.0
      speed: 1.0
      cuff_radius: 3.5
      can_use_weapons: false
      skip_cuffing: false
      weapon_pool:
         - WOODEN_SWORD
      armor:
         helmet: CHAINMAIL_HELMET
         chestplate: CHAINMAIL_CHESTPLATE
         leggings: CHAINMAIL_LEGGINGS
         boots: CHAINMAIL_BOOTS
   2:
      display_name: "&1Sergeant"
      # ... higher stats
   3:
      display_name: "&5Lieutenant"
      can_use_weapons: true
      weapon_name_pool:
         - "Pistol"
      # ...
   4:
      display_name: "&4SWAT"
      skip_cuffing: true
      weapon_name_pool:
         - "Rifle"
         - "SMG"
      # ...
   5:
      display_name: "&cMilitary"
      skip_cuffing: true
      # ... highest stats and weapons
```

### civilians.yml

Civilian type and group definitions loaded by `CiviliansLoader`:

```yaml
types:
   pedestrian:
      display_name: "&7Pedestrian"
      entity_type: PLAYER
      health: 20.0
      hostile: false
      ai:
         wander_enabled: true
         wander_range: 8
         flee_enabled: true
         flee_range: 20
         combat_enabled: false
      wearables:
         helmet: LEATHER_HELMET
      drops:
         money: 5.0
         experience: 10.0
   gang_member:
      display_name: "&cGang Member"
      hostile: true
      ai:
         wander_enabled: true
         wander_range: 6
         flee_enabled: false
         combat_enabled: true
         attack_damage: 4.0
         attack_range: 6.0
      weapon_name_pool:
         - "Pistol"
   trader:
      display_name: "&6Trader"
      hostile: false
      ai:
         wander_enabled: false
      inventory:
         title: "&6Trader"
         items:
            - "DIAMOND_SWORD:1:5000"

groups:
   street_gang:
      members:
         gang_member: 3
      stay_together_range: 15.0
```

---

## Integration Points

### With gangland-impl

The cops-n-crooks module is a feature module with no direct dependency on `gangland-impl`. Integration flows
through:

1. **Contract interfaces** -- `WantedSettings`, `BountySettings`, `CopSettings`, `CivilianSettings` are implemented
   in `gangland-impl` and passed during initialization
2. **Event bus** -- Bukkit events fired by this module are handled by listeners in `gangland-impl`
3. **DependencyContainer** -- `CopManager`, `CivilianService`, etc. are registered in the DI container by
   `Initializer.java`

### With gangland-weapon

- `WeaponService` resolves gangland weapon names from `weaponNamePool` configs
- `AbstractNpc` fires `WeaponShootEvent` through the standard weapon pipeline
- `GunWeapon` projectile and reload systems are used as-is for NPC combat

### With plugin-persistence

- `IRepository<CopSpawner>`, `IRepository<CivilianSpawner>`, `IRepository<Jail>` provide persistence
- Spawner points and jail cells are loaded on startup and saved on change

### With Citizens API

- All NPCs are created via `CitizensAPI.createNPC()` (or equivalent)
- Navigation uses Citizens' built-in pathfinder (`npc.getNavigator().setTarget()`)
- NPC entity type is configurable (PLAYER, ZOMBIE, etc.)
- Entity lifecycle managed through Citizens `NPC.spawn()`, `NPC.despawn()`, `NPC.destroy()`
