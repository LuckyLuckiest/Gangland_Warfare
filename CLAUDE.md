# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Full build (produces final shaded JAR in gangland-build/target/)
mvn clean package

# Skip tests
mvn clean package -DskipTests

# Build only the final deployable JAR
mvn clean package -pl gangland-build -am
```

Tests use JUnit 5 and Mockito. Run a single test class:

```bash
mvn test -pl <module> -Dtest=MyTestClass
```

## Module Structure

| Module                             | Purpose                                                                                |
|------------------------------------|----------------------------------------------------------------------------------------|
| `gangland-impl`                    | Main plugin entry point, commands, listeners, data managers                            |
| `gangland-build`                   | Shade plugin assembly — produces the final deployable JAR                              |
| `gangland-util`                    | Shared utilities: `DependencyContainer`, `ReflectionUtil`, `Tree`                      |
| `gangland-weapon`                  | Weapon, ammunition, and projectile system                                              |
| `plugin-persistence`               | Generic repository pattern (`IRepository`, `AbstractRepository`, `RepositoryRegistry`) |
| `plugin-exception`                 | Custom exception hierarchy                                                             |
| `scoreboard-api`                   | FastBoard-based scoreboard rendering                                                   |
| `inventory-api`                    | Custom inventory/GUI framework                                                         |
| `sign-api`                         | Sign interaction system                                                                |
| `lootchest-api`                    | Loot chest system with hologram support                                                |
| `cops-n-crooks`                    | Cop/crook gameplay: detainment, jails, NPC cop spawning                                |
| `gangland-compatibility/version-*` | NMS adapters for Minecraft 1.10–1.21                                                   |

The final shaded JAR is assembled by `gangland-build`. The primary codebase lives in `gangland-impl`; the primary
package is `me.luckyraven`.

## Architecture

### Lifecycle

`Gangland.java` (extends `JavaPlugin`) is the plugin entry point. It delegates all initialization to `Initializer.java`,
which runs in two phases:

1. **`onLoad` / constructor** — version detection, compatibility setup, placeholder service.
2. **`postInitialize` / `onEnable`** — file configs, database, all managers, listeners, commands.

`PeriodicalUpdates.java` handles scheduled auto-save and cache cleanup.

### Persistence Layer

`plugin-persistence` provides a generic repository pattern:

- `IRepository<T>` — CRUD interface.
- `AbstractRepository<T>` — lifecycle management, async upsert via `DatabaseHelper`.
- `RepositoryRegistry` — scans a package for `@Repository`-annotated classes and auto-registers them using constructor
  reflection.

Concrete repositories live in `gangland-impl` under `me.luckyraven.database.repositories.*`. They are discovered at
startup:

```java
registry.scanAndRegisterRepositories("me.luckyraven.database.repositories");
```

Database tables are defined as constants in `me.luckyraven.database.tables.*`.

`GanglandDatabase` in `gangland-impl` wraps HikariCP and supports both MySQL and SQLite, selected via `SettingAddon`.

### Dependency Injection

`DependencyContainer` (`gangland-util`) is a lightweight reflection-based DI container. Instances are registered by type
and resolved via constructor injection. It is used primarily to wire event listeners:

```java
container.registerInstance(UserManager .class, userManager);
// ...
listenerManager.

scanAndRegisterListeners("me.luckyraven",plugin);
```

### Service / Manager Layer

Each domain has a `*Manager` or `*Service`:

- `UserManager` — online/offline player caching.
- `GangManager`, `MemberManager` — gang and membership.
- `RankManager` — hierarchical rank/permission system.
- `WaypointManager` — teleportation waypoints.
- `WeaponManager` — weapon configurations.
- `LootChestManager` — loot chests.
- `CopService`, `CopSpawnManager`, `DetainmentService`, `JailManager` — cops-n-crooks.

### Commands

All commands dispatch through a single `CommandManager` registered to `/glw` (alias `/gangland`). Sub-commands are
classes under `me.luckyraven.command.sub.*` and registered programmatically in `Initializer`.

### Version Compatibility

`gangland-compatibility/version-*` modules provide NMS implementations per Minecraft version. `VersionSetup` detects the
running server version at load time and selects the correct adapter. Recoil effects and other NMS-specific code are
abstracted behind interfaces in `gangland-compatibility/version-impl`.

### cops-n-crooks Module

Depends on `gangland-impl` (shared managers) and the Citizens API (required) for NPC cops. Key classes:

- `CopService` / `CopSpawnManager` / `CopSpawner` — NPC lifecycle and spawn points.
- `DetainmentService` / `DetainmentManager` / `DetainedPlayer` — arrest mechanics.
- `JailManager` / `JailService` / `Jail` — jail locations.
- `CopBehaviorFactory` — AI state machine for cop NPCs.

## Key Configuration Files

- `gangland-impl/src/main/resources/plugin.yml` — Spigot metadata; required deps: `NBTAPI`, `Citizens`; soft deps:
  `PlaceholderAPI`, `Vault`, `ViaVersion`.
- `gangland-impl/src/main/resources/settings.yml` — Main runtime config (database type, auto-save interval, etc.).
- `gangland-impl/src/main/resources/cops.yml` — Cop NPC and spawn configuration.
