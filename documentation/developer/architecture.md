# Architecture Overview

[Back to Developer Docs](./README.md)

---

## Overview

Gangland Warfare is a multi-module Spigot plugin built on Java 21. It follows a two-phase
initialization lifecycle, uses a custom lightweight DI container for wiring, and relies on
an event-driven architecture with a generic repository persistence layer.

**Entry Point:** `me.luckyraven.Gangland` (extends `JavaPlugin`)  
**Primary Package:** `me.luckyraven`  
**Build Output:** `gangland-build` (shaded JAR)

---

## Plugin Lifecycle

### Gangland.java

The main plugin class is intentionally thin -- it delegates all work to `Initializer`.

```
┌─────────────────────────────────────────────────────────┐
│                     Gangland.java                        │
│                                                         │
│  Static:                                                │
│    FULL_PREFIX  = "gangland"                             │
│    SHORT_PREFIX = "glw"                                  │
│                                                         │
│  Fields:                                                │
│    Initializer initializer                               │
│    ReloadPlugin reloadPlugin                             │
│    PeriodicalUpdates periodicalUpdates                    │
│    UpdateChecker updateChecker                            │
│    PlaceholderAPIExpansion placeholderAPIExpansion        │
│    ViaAPI<?> viaAPI                                      │
│                                                         │
│  onLoad()    → Disable HikariCP logs, create Initializer │
│  onEnable()  → initializer.postInitialize()              │
│  onDisable() → Nullify Vault, deactivate gadgets,        │
│                initializer.shutdown()                     │
└─────────────────────────────────────────────────────────┘
```

### Lifecycle Sequence

```
Server Start
    │
    ├── onLoad()
    │     ├── Suppress HikariCP debug logging
    │     └── new Initializer(this)
    │           ├── InformationManager (plugin metadata)
    │           ├── VersionSetup (MC version detection)
    │           ├── CompatibilitySetup (NMS adapter selection)
    │           └── PlaceholderService (placeholder registration)
    │
    ├── onEnable()
    │     └── initializer.postInitialize()
    │           ├── Phase 1: Configuration
    │           ├── Phase 2: Database
    │           ├── Phase 3: Core Managers
    │           ├── Phase 4: Gadget Services
    │           ├── Phase 5: UI & Data
    │           ├── Phase 6: Feature Services
    │           ├── Phase 7: Weapon System
    │           ├── Phase 8: DI Registration & Listener Scan
    │           └── Phase 9: Command Registration
    │
    └── onDisable()
          ├── Nullify Vault economy reference
          ├── Deactivate jetpacks for all players
          └── initializer.shutdown()
                ├── Save all repositories
                ├── Cancel scheduled tasks
                └── Disconnect database
```

---

## Initialization Flow (Initializer.java)

The `Initializer` class orchestrates all plugin setup in a deterministic order. Dependencies
must be registered before the classes that consume them.

### Phase 1 -- Configuration

| Component    | Class         | Purpose                    |
|--------------|---------------|----------------------------|
| File Manager | `FileManager` | YAML file loading/saving   |
| Settings     | `Settings`    | Main runtime configuration |
| Messages     | `Messages`    | i18n message strings       |

### Phase 2 -- Database

| Component           | Class                | Purpose                         |
|---------------------|----------------------|---------------------------------|
| Database            | `GanglandDatabase`   | HikariCP wrapper (MySQL/SQLite) |
| Repository Registry | `RepositoryRegistry` | Auto-scan `@Repository` classes |

The registry scans `me.luckyraven.database.repositories` and creates tables in
dependency-sorted order.

### Phase 3 -- Core Managers

| Manager             | Purpose                                |
|---------------------|----------------------------------------|
| `UserManager`       | Online/offline player profile caching  |
| `GangManager`       | Gang lifecycle and metadata            |
| `MemberManager`     | Gang membership tracking               |
| `RankManager`       | Hierarchical rank/permission system    |
| `PermissionManager` | Runtime permission evaluation          |
| `WaypointManager`   | Teleportation waypoint registry        |
| `BountyManager`     | Player bounty tracking                 |
| `SignManager`       | Custom sign type registry and handling |

### Phase 4 -- Gadget Services

| Service             | Purpose                             |
|---------------------|-------------------------------------|
| `CarService`        | Vehicle spawning, movement, parking |
| `JetpackService`    | Jetpack flight mechanics            |
| `FuelManager`       | Fuel tracking for cars/jetpacks     |
| `AmmunitionManager` | Ammo type validation and stacking   |

### Phase 5 -- UI & Data

| Component           | Purpose                          |
|---------------------|----------------------------------|
| `LootChestManager`  | Loot chest lifecycle             |
| `ScoreboardManager` | Per-player FastBoard scoreboard  |
| `HologramService`   | Floating text display management |

### Phase 6 -- Feature Services

| Service             | Purpose                         |
|---------------------|---------------------------------|
| `CopService`        | Police NPC spawning and pursuit |
| `CopSpawnManager`   | Cop spawn point management      |
| `DetainmentService` | Handcuff and jail mechanics     |
| `JailManager`       | Jail location registry          |
| `CivilianService`   | Civilian NPC lifecycle          |

### Phase 7 -- Weapon System

| Component       | Purpose                            |
|-----------------|------------------------------------|
| `WeaponManager` | Weapon config loading and lookup   |
| `WeaponService` | Weapon equip/damage/event dispatch |

### Phase 8 -- DI Registration & Listener Scan

```java
// Register the plugin itself for injection
dependencyContainer.registerInstance(JavaPlugin.class, gangland);

// Auto-discover and instantiate all @ListenerHandler classes
listenerManager.scanAndRegisterListeners("me.luckyraven", plugin);
```

The listener manager:

1. Scans all classes in `me.luckyraven` package
2. Filters for `@ListenerHandler` annotation + `Listener` interface
3. Uses `DependencyContainer.createInstance()` to resolve constructor dependencies
4. Registers each listener with `Bukkit.getPluginManager().registerEvents()`

### Phase 9 -- Command Registration

```java
CommandManager commandManager = new CommandManager(gangland);
// Sub-commands registered programmatically
commandManager.register(new GangCommand(gangland, tree));
commandManager.register(new BankCommand(gangland, tree));
// ... all 17 command groups
```

---

## Dependency Injection

See [Dependency Injection](./dependency-injection.md) for full details.

**Summary:**

- `DependencyContainer` stores instances by type in `ConcurrentHashMap`
- Constructor injection via reflection
- `@AutowireTarget` / `@Autowired` annotations
- Type hierarchy auto-registration (superclasses + interfaces)
- Used primarily for wiring event listeners

---

## Event System

### Bukkit Events

Listeners are discovered via `@ListenerHandler` annotation scanning. Each listener class
declares `@EventHandler` methods for Bukkit events it handles.

### Custom Events (30+)

The plugin fires custom events for game-specific actions:

| Category   | Events                                                              |
|------------|---------------------------------------------------------------------|
| Weapon     | ShootEvent, ProjectileLaunchEvent, ProjectileHitEvent, ReloadEvent, |
|            | ReloadCompleteEvent, EntityDamageEvent, KillEntityEvent,            |
|            | ChangeSelectiveFireEvent                                            |
| Cops       | WantedStartEvent, WantedLevelChangeEvent, WantedEndEvent            |
| NPC        | CopDeathEvent, CivilianDeathEvent, NpcEvent                         |
| Bounty     | BountyEvent, KillComboEvent                                         |
| Detainment | CuffedEvent, DuringCuffingEvent                                     |
| Repair     | RepairStartEvent, RepairCompleteEvent                               |
| Loot Chest | CrackingStartEvent, CrackingTickEvent, CrackingSuccessEvent,        |
|            | CrackingFailedEvent, CooldownTickEvent, CooldownCompleteEvent       |

---

## Service / Manager Pattern

The codebase distinguishes between two types of domain components:

### Managers (Stateful Cache + Persistence)

Managers hold in-memory data caches and coordinate with repositories for persistence:

```
Player joins → UserManager.loadUser(uuid) → UserRepository.load(uuid) → cache
Player quits → UserManager.saveUser(uuid) → UserRepository.save(user) → database
Auto-save   → UserManager.saveAll() → UserRepository.saveAllFromMemory() → batch
```

### Services (Business Logic & Operations)

Services perform operations and dispatch events without holding persistent state:

```
Player shoots → WeaponService.handleShoot() → create projectile → fire event
Cop spawns   → CopService.spawnCopsFor(player) → create NPC → start AI loop
```

### Naming Convention

| Suffix      | Role                        | Examples                                  |
|-------------|-----------------------------|-------------------------------------------|
| `*Manager`  | Cache + persistence         | UserManager, GangManager, WeaponManager   |
| `*Service`  | Business logic              | CopService, DetainmentService, CarService |
| `*Registry` | Type/instance lookup        | RepositoryRegistry, SignTypeRegistry      |
| `*Handler`  | Event/request processing    | InventoryHandler, DatabaseHandler         |
| `*Executor` | Scheduled/triggered actions | WantedExecutor, BountyExecutor            |

---

## Scheduled Tasks (PeriodicalUpdates.java)

The `PeriodicalUpdates` class manages periodic maintenance using a `RepeatingTimer`
(Bukkit scheduler wrapper).

### Main Task Loop

```
PeriodicalUpdates.task() [runs every N minutes]
    │
    ├── PluginDataCleanupService.run()
    │     └── Clean up expired weapon data
    │
    ├── resetCache()
    │     └── Clear stale in-memory caches
    │
    └── updatingDatabase() [async]
          └── RepositoryRegistry.saveAllRepositories()
                ├── UserRepository.saveAllFromMemory()
                ├── GangRepository.saveAllFromMemory()
                ├── BankRepository.saveAllFromMemory()
                └── ... (all repositories)
```

### Configuration

```yaml
# settings.yml
Database:
  Auto_Save:
    Enable: true
    Time: 10       # minutes between saves
    Debug: true    # log performance timing
  Clean_Up:
    Time: 30       # days before old data cleanup
```

### Lifecycle

- `start()` -- initializes cleanup service and starts repeating timer
- `stop()` -- cancels timer
- Timer restarts on plugin reload

---

## Module Dependency Graph

```
                         ┌──────────────┐
                         │ gangland-build│  (shade assembly → final JAR)
                         └──────┬───────┘
                                │ depends on ALL modules
                                │
                    ┌───────────┴───────────┐
                    │                       │
             ┌──────┴──────┐         ┌──────┴──────┐
             │gangland-impl│         │gangland-comp │
             │  (main code)│         │  (version-*) │
             └──┬──┬──┬──┬┘         └──────┬───────┘
                │  │  │  │                 │
    ┌───────────┘  │  │  └────────┐       │
    │              │  │           │       │
┌───┴──────┐  ┌───┴──┴───┐  ┌───┴───┐  ┌┴──────────┐
│gangland- │  │gangland-  │  │gangland│  │version-impl│
│features/ │  │ui/        │  │-item   │  │(interfaces)│
│  cops    │  │ inventory │  └───┬───┘  └────────────┘
│  weapon  │  │ scoreboard│      │
│  gadget  │  │ sign      │      │
└───┬──────┘  │ lootchest │  ┌───┴────────┐
    │         │ hologram  │  │gangland-core│
    │         └───┬───────┘  │  (DI, utils)│
    │             │          └───┬─────────┘
    │             │              │
    └─────────┬──┴──────────────┘
              │
        ┌─────┴──────────┐
        │plugin-persistence│
        │ (repos, DB)      │
        └─────┬────────────┘
              │
        ┌─────┴──────┐
        │plugin-common│
        │ (logger)    │
        └─────────────┘
```

### Dependency Rules

1. **Feature modules** (`cops-n-crooks`, `gangland-weapon`, `gangland-gadget`) depend on
   `gangland-core`, `gangland-item`, `plugin-persistence`, `plugin-common`
2. **UI modules** depend on `gangland-core`, `plugin-common`
3. **`gangland-impl`** depends on everything (it wires it all together)
4. **`gangland-build`** shades everything into the final JAR
5. Feature modules must **never** import `Settings` or `Messages` directly -- they use
   contract interfaces implemented in `gangland-impl`

---

## Key Design Decisions

### Why Spigot, Not Paper?

The plugin targets Spigot exclusively. Paper-specific APIs (`io.papermc.paper.*`) are
never used. This maximizes server compatibility at the cost of Paper-only optimizations.

### Why Custom DI, Not Guice/Spring?

The `DependencyContainer` is ~200 lines of code and provides exactly what's needed:
constructor injection for listeners. A full DI framework would add unnecessary complexity
and JAR size for a Spigot plugin.

### Why Repository Pattern, Not Raw SQL?

The generic repository pattern (`IRepository<T>`, `AbstractRepository<T>`) provides:

- Consistent CRUD operations across all entity types
- Auto-discovery via `@Repository` annotation scanning
- Table dependency sorting for safe DDL execution
- Batch async saves via `DatabaseHelper`
- Easy swapping between MySQL and SQLite

### Why Single `/glw` Command?

All commands dispatch through a single registered command (`/glw`) with a tree-based
argument routing system. This:

- Avoids polluting the global command namespace
- Provides consistent permission structure (`gangland.command.*`)
- Enables built-in suggestion/spell-check for invalid arguments
- Simplifies tab completion

---

*See individual guide pages for deeper dives into each subsystem.*
