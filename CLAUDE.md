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

Tests use JUnit 5 and Mockito.

### Windows / HikariCP / SQLite file-locking in tests

HikariCP's `minimumIdle=5` eagerly opens 5 native SQLite file handles at pool startup. On Windows, these handles are
not released synchronously after `dataSource.close()`, so JUnit's `@TempDir` auto-cleanup fails with
`Failed to delete temp directory ... tbl_test.db: The process cannot access the file`.

**Rules for any test class that touches a real database:**

1. Use `@TempDir(cleanup = CleanupMode.NEVER)` — never plain `@TempDir`. This stops JUnit from attempting deletion;
   the OS cleans up temp files eventually.
2. Call `MockPluginFactory.releaseDbFiles(tempDir)` at the end of every `@AfterEach` teardown. This flushes WAL/SHM
   files and makes a best-effort delete so the temp directory doesn't grow unboundedly during a single test run.
3. Always disconnect the database in `@AfterEach` before calling `releaseDbFiles`. Track every `DatabaseHandler`
   created in a test class (use a `List<TestDatabaseHandler> toClose` field + a `tracked()` helper) so none are
   leaked.

**Do not** use retry loops as the primary cleanup strategy — Windows file locks can persist for several seconds and
retries alone are insufficient.

### HikariCP MySQL failure behaviour

`enforceType(MYSQL)` only catches `SQLException`. HikariCP throws `PoolInitializationException` (a `RuntimeException`)
when it cannot connect, so the `catch (SQLException)` block is skipped and `this.database` is left as a non-null but
uninitialized `MySQL` instance (no live connection). Assertions about MySQL failure must check
`db == null || db.getConnection() == null`, not `assertNull(db)`.

### RepositoryRegistry key semantics

`RepositoryRegistry` is a `Map<Class<?>, IRepository<?>>` keyed by entity type. Registering two repositories under the
same entity class **overwrites** the first. Tests that register multiple repos for the same type will only see the last
one in `getAllRepositories()`.

> **Important:** always include `-am` when targeting a single module. The multi-module
> build uses `${revision}` as the parent POM version; without `-am`, Maven cannot resolve
> local sibling dependencies (`logger-api`, `plugin-exception`, `gangland-util`, etc.)
> and will fail trying to download `${revision}` from remote repositories.

```bash
# Run all tests in a single module (builds sibling deps first)
mvn test -pl plugin-persistence -am

# Run a single test class
mvn test -pl plugin-persistence -am -Dtest=SQLiteIntegrationTest

# Run a single test method
mvn test -pl plugin-persistence -am -Dtest="SQLiteIntegrationTest#insert_andSelect_returnsCorrectValues"
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
