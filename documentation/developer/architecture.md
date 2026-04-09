# Architecture Overview

[Back to Developer Docs](./README.md)

---

## Overview

Gangland Warfare is a multi-module Spigot plugin built on Java 21. It uses a Spring-style bean framework
(`BeanFactory` + `@Configuration` classes) for phased bootstrap and dependency injection, and relies on
an event-driven architecture with a generic repository persistence layer.

**Entry Point:** `me.luckyraven.Gangland` (extends `JavaPlugin`)  
**Primary Package:** `me.luckyraven`  
**Build Output:** `gangland-build` (shaded JAR)

---

## Plugin Lifecycle

### Gangland.java

The main plugin class is intentionally thin -- it delegates all work to `GanglandContext`.

```
╭─────────────────────────────────────────────────────────╮
│                     Gangland.java                       │
│                                                         │
│  Static:                                                │
│    FULL_PREFIX  = "gangland"                            │
│    SHORT_PREFIX = "glw"                                 │
│                                                         │
│  Fields:                                                │
│    GanglandContext context                              │
│    ReloadPlugin reloadPlugin                            │
│    UpdateChecker updateChecker                          │
│    PlaceholderAPIExpansion placeholderAPIExpansion      │
│    ViaAPI<?> viaAPI                                     │
│                                                         │
│  onLoad()    → Disable HikariCP logs                    │
│  onEnable()  → new GanglandContext(this)                │
│               → context.bootstrap()                     │
│  onDisable() → Nullify Vault, context.shutdownBeans(),  │
│                force save, close database               │
╰─────────────────────────────────────────────────────────╯
```

### Lifecycle Sequence

```
Server Start
    │
    ├── onLoad()
    │     ╰── Suppress HikariCP debug logging
    │
    ├── onEnable()
    │     ├── new GanglandContext(this)
    │     │     ├── Creates DependencyContainer
    │     │     ├── Creates BeanFactory
    │     │     ╰── Self-registers context + container + Gangland
    │     │
    │     ╰── context.bootstrap()
    │           ├── Install FILE phase hook (FileManager.initializeAll() per bean)
    │           ├── Install DATABASE phase hook (publish repos to container)
    │           ├── Scan me.luckyraven.config for @Configuration classes
    │           ├── BeanFactory.instantiate()
    │           │     ├── KERNEL phase   → version, compatibility, permissions, files, DB, scoreboard
    │           │     ├── FILE phase     → Settings, addons, file initializers (staged loading)
    │           │     ├── DATABASE phase → GanglandDatabase, RepositoryRegistry
    │           │     ├── CONFIG phase   → all managers, services, gadgets, cops, weapons
    │           │     ├── LIFECYCLE      → @PostConstruct + convention-based initialize()
    │           │     ╰── (per-bean BeanLifecycle.onInitialize(true) runs in topo order)
    │           │
    │           ├── LISTENER phase → scan @ListenerHandler, constructor-inject, register
    │           ╰── COMMAND phase  → scan @CommandHandler, constructor-inject, bind to /glw
    │
    ╰── onDisable()
          ├── Nullify Vault economy reference
          ├── context.shutdownBeans() (reverse topological order)
          │     ╰── BeanLifecycle.onShutdown() on each bean
          ├── PeriodicalUpdates.forceUpdate() (flush pending data)
          ╰── DatabaseManager.closeConnections()
```

---

## Bean Bootstrap Pipeline

The `GanglandContext.bootstrap()` method drives the entire plugin setup. `BeanFactory` discovers
`@Configuration` classes in `me.luckyraven.config`, collects their `@Bean` methods, topologically
sorts them per phase, and invokes each exactly once. The result is registered as a singleton in
the shared `DependencyContainer`.

### Configuration Classes

| Class                  | Phase    | Beans produced                                                           |
|------------------------|----------|--------------------------------------------------------------------------|
| `KernelConfig`         | KERNEL   | InformationManager, VersionSetup, CompatibilitySetup, PermissionManager, |
|                        |          | FileManager, DatabaseManager, ScoreboardManager, PlaceholderService      |
| `FileConfig`           | FILE     | Settings, LanguageLoader, 13+ FileInitializer addons                     |
| `DatabaseConfig`       | DATABASE | GanglandDatabase, RepositoryRegistry                                     |
| `DataConfig`           | CONFIG   | UserManager (online/offline), RankManager, GangManager, MemberManager,   |
|                        |          | WaypointManager, PluginManager                                           |
| `GameplayConfig`       | CONFIG   | WeaponManager, SignManager, ItemParserManager, LootChestManager,         |
|                        |          | HologramService, RepairService, MoneyDepositService, BlockDamageManager  |
| `SchedulingConfig`     | CONFIG   | PeriodicalUpdates, PlayerBootstrapService, ScoreboardLifecycleService    |
| `WiringConfig`         | CONFIG   | ListenerManager, CommandManager, GanglandPlaceholder                     |
| `CopsAndGadgetsConfig` | CONFIG   | CopService, CivilianService, JailService, DetainmentService,             |
|                        |          | CarService, JetpackService, MoneyDropClassifier                          |

### Phase Hooks

Two hooks are installed before `BeanFactory.instantiate()` runs:

- **FILE hook:** After every file-initializer bean, calls `FileManager.initializeAll()` so YAML
  is loaded before the next FILE bean reads it at construction time
- **DATABASE hook:** After each database bean, walks `RepositoryRegistry.getAllRepositories()`
  and publishes every `IRepository` into the container by its concrete class

### Listener & Command Phases

After all bean phases complete, `GanglandContext` runs two final phases:

1. **Listeners:** Pulls `ListenerManager` from the container, scans `me.luckyraven` for
   `@ListenerHandler` classes, instantiates each via constructor injection, and registers
   with Bukkit's event system
2. **Commands:** Pulls `CommandManager` from the container, scans `me.luckyraven.command.sub`
   for `@CommandHandler` classes, instantiates each via constructor injection, and binds
   the executor to the `/glw` `PluginCommand`

---

## Dependency Injection (Beans System)

See [Beans System & Dependency Injection](./dependency-injection.md) for the full developer guide.

**Summary:**

- `BeanFactory` discovers `@Configuration` classes and invokes `@Bean` factory methods
- `DependencyContainer` stores singletons by type in `ConcurrentHashMap`
- Constructor injection via reflection for beans, listeners, and commands
- `@Qualifier` disambiguates when multiple beans share a raw type (e.g. generic `UserManager`)
- `BeanLifecycle` interface provides managed reload (`onPreClear` / `onClear` / `onInitialize`)
  and shutdown (`onShutdown`) in topological order
- `@ConditionalOnSetting` / `@ConditionalOnBean` for optional feature trees
- Type hierarchy auto-registration (superclasses + interfaces)

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
    │     ╰── Clean up expired weapon data
    │
    ├── resetCache()
    │     ╰── Clear stale in-memory caches
    │
    ╰── updatingDatabase() [async]
          ╰── RepositoryRegistry.saveAllRepositories()
                ├── UserRepository.saveAllFromMemory()
                ├── GangRepository.saveAllFromMemory()
                ├── BankRepository.saveAllFromMemory()
                ╰── ... (all repositories)
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
                         ╭────────────────╮
                         │ gangland-build │  (shade assembly → final JAR)
                         ╰──────┬─────────╯
                                │ depends on ALL modules
                                │
                    ╭───────────┴───────────╮
                    │                       │
             ╭──────┴──────╮         ╭──────┴───────╮
             │gangland-impl│         │gangland-comp │
             │  (main code)│         │  (version-*) │
             ╰──┬──┬──┬──┬─╯         ╰──────┬───────╯
                │  │  │  │                  │
    ╭───────────╯  │  │  ╰────────╮         │
    │              │  │           │         │
╭───┴──────╮  ╭────┴──┴───╮   ╭───┴────╮  ╭─┴──────────╮
│gangland- │  │gangland-  │   │gangland│  │version-impl│
│features/ │  │ui/        │   │-item   │  │(interfaces)│
│  cops    │  │ inventory │   ╰───┬────╯  ╰────────────╯
│  weapon  │  │ scoreboard│       │
│  gadget  │  │ sign      │       │
╰───┬──────╯  │ lootchest │  ╭────┴────────╮
    │         │ hologram  │  │gangland-core│
    │         ╰───┬───────╯  │  (DI, utils)│
    │             │          ╰───┬─────────╯
    │             │              │
    ╰─────────┬───┴──────────────╯
              │
        ╭─────┴────────────╮
        │plugin-persistence│
        │ (repos, DB)      │
        ╰─────┬────────────╯
              │
        ╭─────┴───────╮
        │plugin-common│
        │ (logger)    │
        ╰─────────────╯
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

### Why Custom Bean Framework, Not Guice/Spring?

The bean framework (`BeanFactory` + `DependencyContainer`) is purpose-built for a Spigot plugin:
phased bootstrap with hooks (FILE loading, repository publishing), topological ordering with
cycle detection, and managed reload/shutdown via `BeanLifecycle`. A full Spring or Guice
deployment would add unnecessary complexity, JAR size, and classpath conflicts for a Spigot plugin.

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
