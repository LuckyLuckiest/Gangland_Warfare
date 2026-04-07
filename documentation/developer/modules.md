# Gangland Warfare - Developer Module Reference

> **Version:** 0.7.4-DEV | **Platform:** Spigot 1.21 | **Java:** 21  
> **Primary Package:** `me.luckyraven` | **Command Prefix:** `/glw` (alias `/gangland`)

This document provides a comprehensive reference for every module in the Gangland Warfare plugin. Each section details
the module's purpose, package structure, key classes, and how it integrates with the rest of the system.

---

## Table of Contents

1. [gangland-impl (Main Plugin)](#gangland-impl-main-plugin)
2. [gangland-build (Assembly)](#gangland-build-assembly)
3. [gangland-core (Shared Utilities)](#gangland-core-shared-utilities)
4. [gangland-item (Item System)](#gangland-item-item-system)
5. [plugin-persistence (Database & Repository)](#plugin-persistence-database--repository)
6. [plugin-common (Logger & Exceptions)](#plugin-common-logger--exceptions)
7. [gangland-features/cops-n-crooks](#gangland-featurescops-n-crooks)
8. [gangland-features/gangland-weapon](#gangland-featuresgangland-weapon)
9. [gangland-features/gangland-gadget](#gangland-featuresgangland-gadget)
10. [gangland-ui/scoreboard-api](#gangland-uiscoreboard-api)
11. [gangland-ui/inventory-api](#gangland-uiinventory-api)
12. [gangland-ui/sign-api](#gangland-uisign-api)
13. [gangland-ui/lootchest-api](#gangland-uilootchest-api)
14. [gangland-ui/hologram-api](#gangland-uihologram-api)
15. [gangland-compatibility](#gangland-compatibility)

---

## gangland-impl (Main Plugin)

**Purpose:** The central module of Gangland Warfare. Contains the plugin entry point, all commands, listeners,
managers/services, database repositories, table definitions, configuration loaders, and concrete implementations of
interfaces defined in feature modules.

**Java Files:** 319 | **Primary Package:** `me.luckyraven`

### Entry Point Classes

| Class                    | Description                                                                                                                                                        |
|--------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Gangland.java`          | Extends `JavaPlugin`. The Spigot plugin entry point. Delegates all initialization to `Initializer`.                                                                |
| `Initializer.java`       | Two-phase bootstrap: `onLoad` (version detection, compatibility, placeholders) and `postInitialize`/`onEnable` (configs, database, managers, listeners, commands). |
| `PeriodicalUpdates.java` | Scheduled tasks for auto-save and cache cleanup on configurable intervals.                                                                                         |
| `ReloadPlugin.java`      | Handles plugin hot-reload logic, re-reading configuration files and resetting managers.                                                                            |

### Package: `command/`

The command system dispatches all player commands through a single `/glw` root command.

**Framework Classes:**

| Class                                  | Description                                                                                      |
|----------------------------------------|--------------------------------------------------------------------------------------------------|
| `Command.java`                         | Base command abstraction.                                                                        |
| `CommandManager.java`                  | Central command registry and dispatcher for `/glw`. Registers all sub-commands programmatically. |
| `CommandTabCompleter.java`             | Tab-completion provider for all registered sub-commands.                                         |
| `argument/Argument.java`               | Argument parsing base class.                                                                     |
| `argument/ArgumentLock.java`           | Prevents concurrent argument resolution for a player.                                            |
| `argument/ArgumentResult.java`         | Encapsulates parsed argument result with success/failure state.                                  |
| `argument/ArgumentSpecifier.java`      | Defines argument type constraints and validation.                                                |
| `argument/ArgumentUtil.java`           | Static helpers for argument parsing.                                                             |
| `argument/SubArgument.java`            | Nested argument within a parent argument chain.                                                  |
| `argument/types/ConfirmArgument.java`  | Yes/no confirmation prompt argument.                                                             |
| `argument/types/DoubleArgument.java`   | Numeric double-precision argument with range validation.                                         |
| `argument/types/OptionalArgument.java` | Argument that may be omitted by the player.                                                      |
| `data/CommandInformation.java`         | Metadata DTO for a command (name, description, usage, permission).                               |
| `data/InformationManager.java`         | Loads command metadata from `commands.json` resource file.                                       |

**Sub-Command Groups (16 groups + 8 standalone):**

| Group            | Commands                                                                                                                                                                                                                                                                                                                                      | Description                                                         |
|------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------|
| `sub/bank/`      | `BankCommand`, `BankBalanceCommand`, `BankCreateCommand`, `BankDeleteCommand`, `BankDepositCommand`, `BankWithdrawCommand`                                                                                                                                                                                                                    | Player bank account management.                                     |
| `sub/bounty/`    | `BountyCommand`, `BountyClearCommand`, `BountySetCommand`                                                                                                                                                                                                                                                                                     | Bounty placement and clearing on players.                           |
| `sub/car/`       | `CarCommand`, `CarGiveCommand`, `CarInfoCommand`, `CarListCommand`                                                                                                                                                                                                                                                                            | Vehicle spawning and management.                                    |
| `sub/civilians/` | `CivilianCommand`, `CivilianGroupsCommand`, `CivilianListCommand`, `CivilianSpawnCommand`, `CivilianSpawnerCommand`, `CivilianSpawnerInfoCommand`, `CivilianSpawnerListCommand`, `CivilianSpawnerRemoveCommand`, `CivilianSpawnerSetCommand`, `CivilianSpawnerSetGroupCommand`, `CivilianSpawnerTeleportCommand`, `CivilianSpawnGroupCommand` | Civilian NPC spawner administration.                                |
| `sub/cops/`      | `CopCommand`, `CopListCommand`, `CopSpawnerCommand`, `CopSpawnerInfoCommand`, `CopSpawnerListCommand`, `CopSpawnerRemoveCommand`, `CopSpawnerSetCommand`, `CopSpawnerTeleportCommand`                                                                                                                                                         | Cop NPC spawner administration.                                     |
| `sub/cuff/`      | `CuffCommand`, `UncuffCommand`                                                                                                                                                                                                                                                                                                                | Handcuff/uncuff players as a cop.                                   |
| `sub/debug/`     | Debug commands                                                                                                                                                                                                                                                                                                                                | Developer diagnostics and testing utilities.                        |
| `sub/fuel/`      | Fuel commands                                                                                                                                                                                                                                                                                                                                 | Fuel management for vehicles and jetpacks.                          |
| `sub/gang/`      | Gang commands                                                                                                                                                                                                                                                                                                                                 | Gang creation, invitation, kicking, promotion, alliance management. |
| `sub/item/`      | Item commands                                                                                                                                                                                                                                                                                                                                 | Custom item giving and manipulation.                                |
| `sub/jail/`      | Jail commands                                                                                                                                                                                                                                                                                                                                 | Jail location setup and prisoner management.                        |
| `sub/lootchest/` | Loot chest commands                                                                                                                                                                                                                                                                                                                           | Loot chest placement and configuration.                             |
| `sub/rank/`      | Rank commands                                                                                                                                                                                                                                                                                                                                 | Rank creation, permission assignment, hierarchy management.         |
| `sub/wanted/`    | Wanted commands                                                                                                                                                                                                                                                                                                                               | Wanted level manipulation for players.                              |
| `sub/waypoint/`  | Waypoint commands                                                                                                                                                                                                                                                                                                                             | Teleportation waypoint creation and management.                     |
| `sub/weapon/`    | Weapon commands                                                                                                                                                                                                                                                                                                                               | Weapon giving and configuration.                                    |

**Standalone Commands:**

| Class                          | Description                                       |
|--------------------------------|---------------------------------------------------|
| `BalanceCommand.java`          | Check player wallet balance.                      |
| `DownloadPluginCommand.java`   | Download plugin updates from remote.              |
| `DownloadResourceCommand.java` | Download resource pack files.                     |
| `EconomyCommand.java`          | Admin economy manipulation (give/take/set money). |
| `HelpCommand.java`             | Display command help pages.                       |
| `LevelCommand.java`            | Check or set player level.                        |
| `ReloadCommand.java`           | Reload plugin configuration.                      |
| `RespawnCommand.java`          | Force respawn a player.                           |

### Package: `data/`

Domain models, managers, and services for core gameplay entities.

| Subpackage             | Key Classes                                                                                       | Description                                                                                                                                       |
|------------------------|---------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| `account/`             | `Bank.java`                                                                                       | Player bank account with balance, interest, and transaction history.                                                                              |
| `account/gang/`        | `Gang.java`, `GangAlliance.java`, `GangManager.java`                                              | Gang entity (name, tag, level, XP, bank), alliance tracking, and gang lifecycle management.                                                       |
| `account/gang/member/` | `Member.java`, `MemberManager.java`                                                               | Gang membership model and cache with join/leave/promote operations.                                                                               |
| `account/user/`        | `User.java`, `UserManager.java`                                                                   | Player profile (level, XP, balance, bounty, kills, deaths, wanted level). `UserManager` handles online/offline caching with async DB persistence. |
| `economy/`             | `EconomyHandler.java`, `EconomyException.java`                                                    | Vault-compatible economy interface with balance checks and transfer validation.                                                                   |
| `permission/`          | `PermissionHandler.java`, `PermissionManager.java`, `PermissionWorker.java`                       | Runtime permission attachment and rank-based permission resolution.                                                                               |
| `placeholder/`         | `PlaceholderService.java`, `GanglandPlaceholder.java`, `PlaceholderAPIExpansion.java`             | PlaceholderAPI integration. Exposes player stats, gang info, and economy data as `%gangland_*%` placeholders.                                     |
| `plugin/`              | `PluginData.java`, `PluginDataCleanupService.java`, `PluginManager.java`                          | Global plugin state tracking and periodic cleanup of stale data.                                                                                  |
| `rank/`                | `Rank.java`, `RankManager.java`, `RankParent.java`, `RankPermission.java`, `Permission.java`      | Hierarchical rank system with inheritance. Ranks have parents, permissions, and display prefixes.                                                 |
| `teleportation/`       | `Waypoint.java`, `WaypointManager.java`, `WaypointTeleport.java`, `IllegalTeleportException.java` | Named teleportation waypoints with warmup timers and movement cancellation.                                                                       |
| `HelpInfo.java`        | —                                                                                                 | Help page data structure for the `/glw help` command.                                                                                             |

### Package: `database/`

Concrete database connection and all repository/table implementations.

| Class                           | Description                                                                                                        |
|---------------------------------|--------------------------------------------------------------------------------------------------------------------|
| `GanglandDatabase.java`         | Wraps HikariCP. Supports MySQL and SQLite, selected via `Settings`. Manages connection pooling and table creation. |
| `GanglandDatabaseSettings.java` | Implements `DatabaseSettingsProvider` contract from `plugin-persistence`, pulling values from `Settings`.          |

**Repositories (`database/repositories/`):**

All repositories implement `AbstractRepository<T>` from `plugin-persistence` and are annotated with `@Repository` for
auto-discovery.

| Subpackage     | Repository                                                                                    | Entity Type                                      |
|----------------|-----------------------------------------------------------------------------------------------|--------------------------------------------------|
| `car/`         | `ParkedCarRepository`                                                                         | `ParkedCar`                                      |
| `copsncrooks/` | `CivilianSpawnerRepository`, `CopSpawnerRepository`, `DetainmentRepository`, `JailRepository` | Spawner points, detained players, jail locations |
| `gang/`        | `GangRepository`, `GangAllianceRepository`                                                    | `Gang`, `GangAlliance`                           |
| `lootchest/`   | `LootChestRepository`                                                                         | `LootChestData`                                  |
| `player/`      | `UserRepository`, `MemberRepository`, `BankRepository`                                        | `User`, `Member`, `Bank`                         |
| `plugin/`      | `PluginDataRepository`, `PermissionRepository`                                                | `PluginData`, `Permission`                       |
| `rank/`        | `RankRepository`, `RankParentRepository`, `RankPermissionRepository`                          | `Rank`, `RankParent`, `RankPermission`           |
| `waypoint/`    | `WaypointRepository`                                                                          | `Waypoint`                                       |
| `weapon/`      | `WeaponRepository`                                                                            | `Weapon`                                         |

**Tables (`database/tables/`):**

Each table class extends `Table<T>` and defines the SQL schema, search criteria, and column mappings.

| Subpackage     | Tables                                                                    |
|----------------|---------------------------------------------------------------------------|
| `car/`         | `ParkedCarTable`                                                          |
| `copsncrooks/` | `CivilianSpawnerTable`, `CopSpawnerTable`, `DetainmentTable`, `JailTable` |
| `gang/`        | `GangTable`, `GangAllianceTable`                                          |
| `lootchest/`   | `LootChestTable`                                                          |
| `player/`      | `UserTable`, `MemberTable`, `BankTable`                                   |
| `plugin/`      | `PluginDataTable`, `PermissionTable`                                      |
| `rank/`        | `RankTable`, `RankParentTable`, `RankPermissionTable`                     |
| `waypoint/`    | `WaypointTable`                                                           |
| `weapon/`      | `WeaponTable`                                                             |

### Package: `listener/`

Event listeners registered via `ListenerManager` and the DI container.

| Subpackage   | Listeners                                                                                                                                                                                                                                       | Description                                                                                                                                                             |
|--------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `listener/`  | `ListenerManager.java`                                                                                                                                                                                                                          | Scans packages for `@ListenerHandler`-annotated classes and registers them via `DependencyContainer`.                                                                   |
| `gang/`      | `GangMembersDamage.java`                                                                                                                                                                                                                        | Prevents friendly fire between gang members and allies.                                                                                                                 |
| `inventory/` | `InventoryOpenByCommand.java`, `LoadUniqueItem.java`, `UniqueItemInteract.java`, `UniqueItemInventoryRestrict.java`                                                                                                                             | Handles unique item loading on join/respawn, interaction events, and inventory restrictions.                                                                            |
| `loot/`      | `LootChestEarnGoods.java`, `LootChestWandHandler.java`                                                                                                                                                                                          | Loot chest interaction rewards and admin wand tool.                                                                                                                     |
| `npc/`       | `CivilianDeathRewardListener.java`                                                                                                                                                                                                              | Grants XP/money rewards when civilians are killed.                                                                                                                      |
| `player/`    | `CreateAccount.java`, `RemoveAccount.java`, `CustomPlayerDeath.java`, `PlayerDeath.java`, `EntityDamage.java`, `BountyIncrease.java`, `LevelUp.java`, `LoadResourcePack.java`, `PlayerScoreboard.java`, `WantedChange.java`, `WantedLevel.java` | Core player lifecycle: account creation/removal on join/quit, death handling, damage processing, bounty/wanted/level events, scoreboard updates, resource pack loading. |

### Package: `events/`

Custom Bukkit events fired by the plugin for inter-system communication.

| Event                              | Description                                                   |
|------------------------------------|---------------------------------------------------------------|
| `gang/GangBountyEvent.java`        | Fired when a gang collectively earns bounty.                  |
| `gang/GangLevelUpEvent.java`       | Fired when a gang levels up.                                  |
| `level/LevelUpEvent.java`          | Base level-up event.                                          |
| `teleportation/TeleportEvent.java` | Fired when a player uses a waypoint.                          |
| `user/UserBountyEvent.java`        | Fired when a player's bounty changes.                         |
| `user/UserDataInitEvent.java`      | Fired when a player's data is fully loaded from the database. |
| `user/UserLevelUpEvent.java`       | Fired when a player levels up.                                |

### Package: `features/`

Self-contained gameplay features.

| Class              | Description                                                               |
|--------------------|---------------------------------------------------------------------------|
| `level/Level.java` | Level progression system with XP thresholds and reward tiers.             |
| `phone/Phone.java` | In-game phone GUI providing access to player stats, gang info, and menus. |

### Package: `file/`

Configuration loading and contract implementations.

| Class                                                                | Description                                                                                   |
|----------------------------------------------------------------------|-----------------------------------------------------------------------------------------------|
| `FileInitializer.java`                                               | Bootstraps all YAML configuration files on plugin startup.                                    |
| `LanguageLoader.java`                                                | Loads localized message files (`message_en.yml`, `message_es.yml`).                           |
| `configuration/Settings.java`                                        | Static accessor for `settings.yml` values (database type, auto-save interval, economy, etc.). |
| `configuration/Messages.java`                                        | Static accessor for localized message strings.                                                |
| `configuration/GadgetPhysicsConfigImpl.java`                         | Implements `GadgetPhysicsConfig` for the gadget module.                                       |
| `configuration/copsncrooks/GanglandBountySettings.java`              | Implements `BountySettings` contract.                                                         |
| `configuration/copsncrooks/GanglandCivilianSettings.java`            | Implements `CivilianSettings` contract.                                                       |
| `configuration/copsncrooks/GanglandCivilianSpawnConfigProvider.java` | Implements `SpawnConfigProvider` for civilian spawners.                                       |
| `configuration/copsncrooks/GanglandCopSettings.java`                 | Implements `CopSettings` contract.                                                            |
| `configuration/copsncrooks/GanglandWantedSettings.java`              | Implements `WantedSettings` contract.                                                         |
| `configuration/inventory/ConditionalSlotParser.java`                 | Parses conditional slot definitions from inventory YAML.                                      |
| `configuration/inventory/InventoryAddon.java`                        | Configuration addon for inventory YAML loading.                                               |
| `configuration/inventory/InventoryLoader.java`                       | Loads inventory layout definitions from YAML files.                                           |
| `configuration/inventory/InventoryParser.java`                       | Parses inventory YAML into `InventoryHandler` instances.                                      |
| `configuration/inventory/itemsource/GangItemSourceProvider.java`     | Provides gang-specific items for paginated inventory GUIs.                                    |
| `configuration/lootchest/GanglandLootChestMessages.java`             | Implements `LootChestMessagesProvider` contract.                                              |
| `configuration/lootchest/LootChestSettings.java`                     | Implements `LootChestSettingsProvider` contract.                                              |
| `configuration/weapon/GanglandRepairMessages.java`                   | Implements `RepairMessages` contract.                                                         |
| `configuration/weapon/WeaponLoader.java`                             | Loads weapon definitions from per-weapon YAML files in `weapon/`.                             |

### Package: `item/`

Item conversion and parsing implementations.

| Class                                | Description                                                          |
|--------------------------------------|----------------------------------------------------------------------|
| `ItemAttributes.java`                | Constants for custom item NBT attribute keys.                        |
| `ItemParserManager.java`             | Registers all `ItemConverter` implementations and manages parsing.   |
| `configuration/UniqueItemAddon.java` | Loads unique item definitions from `unique_items.yml`.               |
| `converter/AmmunitionConverter.java` | Converts `"ammo:ak47"` format strings to ammunition `ItemStack`.     |
| `converter/CarConverter.java`        | Converts `"car:sports_car"` format strings to car spawn items.       |
| `converter/MaterialConverter.java`   | Converts standard `"MATERIAL_NAME"` strings to vanilla `ItemStack`.  |
| `converter/WeaponConverter.java`     | Converts `"weapon:rifle"` format strings to weapon `ItemStack`.      |
| `converter/WearableConverter.java`   | Converts `"wearable:kevlar"` format strings to wearable `ItemStack`. |

### Package: `sign/`

Concrete sign type implementations for the sign-api framework.

| Subpackage             | Key Classes                                                                                                                        | Description                                                                                                     |
|------------------------|------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|
| Root                   | `SignManager.java`, `GanglandSignInformation.java`                                                                                 | Registers all sign types and provides sign metadata.                                                            |
| `aspect/`              | `BountyAspect`, `MoneyAspect`, `ItemTransferAspect`, `ViewInventoryAspect`, `WantedAspect`                                         | Composable sign behaviors: bounty check, money transfer, item trading, inventory viewing, wanted level display. |
| `model/`               | `BountyParsedSign`, `ViewParsedSign`, `WantedParsedSign`, `WeaponParsedSign`                                                       | Parsed sign data models extending `BaseParsedSign`.                                                             |
| `parser/`              | `BountyParser`, `TradeSignParser`, `ViewSignParser`, `WantedParser`                                                                | Sign text parsers extending `AbstractSignParser`.                                                               |
| `type/`                | `Sign`, `BountySign`, `ViewSign`, `WantedSign`                                                                                     | Core sign type implementations.                                                                                 |
| `type/trade/`          | `BaseTradeSign`, `BuySign`, `SellSign`                                                                                             | Base buy/sell sign logic.                                                                                       |
| `type/trade/ammo/`     | `AmmoBuySign`, `AmmoSellSign`                                                                                                      | Ammunition trading signs.                                                                                       |
| `type/trade/car/`      | `CarBuySign`, `CarSellSign`                                                                                                        | Vehicle trading signs.                                                                                          |
| `type/trade/weapon/`   | `WeaponBuySign`, `WeaponSellSign`                                                                                                  | Weapon trading signs.                                                                                           |
| `type/trade/wearable/` | `WearableBuySign`, `WearableSellSign`                                                                                              | Wearable armor trading signs.                                                                                   |
| `validation/`          | `BountySignValidator`, `ViewSignValidator`, `WantedSignValidator`                                                                  | Input validation for sign creation.                                                                             |
| `validation/trade/`    | `TradeSignValidator`, `ItemSignValidator`, `AmmoSignValidator`, `CarSignValidator`, `WeaponSignValidator`, `WearableSignValidator` | Trade sign input validation.                                                                                    |

### Package: `lootchest/`

| Class                           | Description                                                                             |
|---------------------------------|-----------------------------------------------------------------------------------------|
| `LootChestManager.java`         | Extends `LootChestService`, wiring gangland-specific item providers and configuration.  |
| `GanglandLootItemProvider.java` | Implements `LootItemProvider` to resolve weapons, ammo, and wearables from loot tables. |
| `LootChestWand.java`            | Admin tool for placing and configuring loot chests.                                     |
| `LootChestWandTag.java`         | NBT tag constants for the loot chest wand item.                                         |

### Package: `scoreboard/`

| Class                    | Description                                                                                       |
|--------------------------|---------------------------------------------------------------------------------------------------|
| `ScoreboardManager.java` | Creates and manages per-player scoreboards using the scoreboard-api. Registers placeholder lines. |

### Package: `weapon/`

| Class                | Description                                                                                      |
|----------------------|--------------------------------------------------------------------------------------------------|
| `WeaponManager.java` | Loads weapon configurations from YAML, manages the weapon registry, and provides lookup by name. |

### Package: `updater/`

| Class                | Description                                                         |
|----------------------|---------------------------------------------------------------------|
| `UpdateChecker.java` | Checks for plugin updates from a remote source and notifies admins. |

### Package: `util/`

| Class                   | Description                                                                |
|-------------------------|----------------------------------------------------------------------------|
| `GanglandChatUtil.java` | Plugin-specific chat formatting extending core `ChatUtil`.                 |
| `TimeMessages.java`     | Implements `TimeMessagesProvider` for localized time duration strings.     |
| `ray/RayTrace.java`     | Ray-tracing utility for projectile hit detection and line-of-sight checks. |

### Resource Files

| File                     | Description                                                                                                                      |
|--------------------------|----------------------------------------------------------------------------------------------------------------------------------|
| `plugin.yml`             | Spigot plugin metadata. Required dependencies: `NBTAPI`, `Citizens`. Soft dependencies: `PlaceholderAPI`, `Vault`, `ViaVersion`. |
| `settings.yml`           | Main runtime configuration (database type, auto-save interval, economy settings, world settings).                                |
| `commands.json`          | Command metadata (descriptions, usage, permissions) for the help system.                                                         |
| `cops.yml`               | Cop NPC tiers, equipment, and spawn configuration.                                                                               |
| `entity_marker.yml`      | Civilian NPC types, groups, behaviors, and equipment configuration.                                                              |
| `ammunition.yml`         | Ammunition type definitions (name, material, max stack, price).                                                                  |
| `cars.yml`               | Vehicle type definitions (speed, fuel capacity, model).                                                                          |
| `scoreboard.yml`         | Scoreboard layout and line definitions with placeholders.                                                                        |
| `unique_items.yml`       | Unique item definitions (phone, tools, etc.) with slot, permission, and behavior.                                                |
| `wearables.yml`          | Wearable armor definitions (material, traits, damage reduction, leather color).                                                  |
| `repair.yml`             | Repair material definitions and costs.                                                                                           |
| `weapon/*.yml`           | Per-weapon-type YAML files: `rifle.yml`, `knife.yml`, `grenade.yml`, `flamethrower.yml`, `syringe_gun.yml`.                      |
| `loot/loot-chests.yml`   | Loot chest definitions (location, tier, cooldown).                                                                               |
| `loot/tiers.yml`         | Loot tier definitions (rarity weights, item pools).                                                                              |
| `inventory/*.yml`        | GUI layout definitions: `phone.yml`, `phone_gang.yml`, `gang_info.yml`, `gang_stat.yml`, `alliance_stat.yml`, `user_stat.yml`.   |
| `message/message_en.yml` | English message strings.                                                                                                         |
| `message/message_es.yml` | Spanish message strings.                                                                                                         |

---

## gangland-build (Assembly)

**Purpose:** Shade plugin assembly module that produces the final deployable JAR. Contains no Java source code.

**Contents:**

- `pom.xml` -- Configures `maven-shade-plugin` to bundle all module dependencies into a single uber-JAR.

**How it works:**

1. Declares all Gangland modules as dependencies.
2. The `maven-shade-plugin` merges all classes and resources into one JAR.
3. Relocates third-party packages (e.g., HikariCP, FastBoard) to avoid classpath conflicts with other plugins.
4. The output JAR is the artifact deployed to a Spigot server's `plugins/` directory.

**Build command (reference only -- never run automatically):**

```
mvn clean package -DskipTests
```

---

## gangland-core (Shared Utilities)

**Purpose:** Foundation module providing dependency injection, reflection utilities, command/listener frameworks, data
structures, color utilities, timers, and common helpers used by all other modules.

**Java Files:** 47 | **Package:** `me.luckyraven.util.*`

### Subpackage: `autowire/`

Lightweight dependency injection container.

| Class                      | Description                                                                                                                                                                        |
|----------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `DependencyContainer.java` | Core DI container using `ConcurrentHashMap<Class<?>, Object>`. Supports `registerInstance()`, `resolve()`, and constructor injection. Scans classes for `@Autowired` constructors. |
| `Autowired.java`           | Annotation marking a constructor for automatic dependency injection.                                                                                                               |
| `AutowireTarget.java`      | Annotation marking a class as eligible for autowiring during package scans.                                                                                                        |

### Subpackage: `color/`

Material and color utilities for version-safe item coloring.

| Class               | Description                                                                        |
|---------------------|------------------------------------------------------------------------------------|
| `Color.java`        | Color constants and conversion utilities.                                          |
| `ColorUtil.java`    | Translates color codes (`&a`, `&#hex`) to Bukkit `ChatColor`/component format.     |
| `MaterialType.java` | Version-safe material type mapping (handles 1.12- to 1.13+ material name changes). |

### Subpackage: `command/`

Command framework annotations and interfaces.

| Class                  | Description                                                                                                |
|------------------------|------------------------------------------------------------------------------------------------------------|
| `CommandHandler.java`  | Annotation marking a class as a command handler. Includes command name, aliases, and description metadata. |
| `CommandService.java`  | Interface for command registration and dispatch services.                                                  |
| `CommandPriority.java` | Enum defining command execution priority ordering (e.g., `NORMAL`, `HIGH`).                                |

### Subpackage: `configuration/`

Configuration POJOs and trackers.

| Class                      | Description                                                                                   |
|----------------------------|-----------------------------------------------------------------------------------------------|
| `ResourcePackTracker.java` | Tracks per-player resource pack download status (`ACCEPTED`, `LOADED`, `DECLINED`, `FAILED`). |
| `SoundConfiguration.java`  | POJO representing a configured sound (Bukkit `Sound`, volume, pitch).                         |

### Subpackage: `datastructure/`

General-purpose data structures.

| Class                       | Description                                                                   |
|-----------------------------|-------------------------------------------------------------------------------|
| `LinkedList.java`           | Custom doubly-linked list implementation.                                     |
| `Tree.java`                 | Generic tree structure with parent-child traversal (used by rank hierarchy).  |
| `ScientificCalculator.java` | Expression evaluator supporting arithmetic operations.                        |
| `SpellChecker.java`         | Levenshtein distance-based spell checker for command/argument fuzzy matching. |
| `JsonFormatter.java`        | JSON pretty-printing and serialization utility.                               |

### Subpackage: `downed/`

Downed player state tracking.

| Class                       | Description                                                                        |
|-----------------------------|------------------------------------------------------------------------------------|
| `DownedPlayerRegistry.java` | Tracks players in a "downed" (incapacitated) state. Prevents actions while downed. |
| `PlayerDownedEvent.java`    | Custom Bukkit event fired when a player enters or exits the downed state.          |

### Subpackage: `feature/`

| Class           | Description                                                                                          |
|-----------------|------------------------------------------------------------------------------------------------------|
| `Executor.java` | Base interface for feature executors (bounty, wanted, etc.). Provides standard `execute()` contract. |

### Subpackage: `listener/`

Listener framework annotations and services.

| Class                   | Description                                                                            |
|-------------------------|----------------------------------------------------------------------------------------|
| `ListenerHandler.java`  | Annotation marking a class as an event listener. Includes priority and feature gating. |
| `ListenerPriority.java` | Enum for listener registration priority ordering.                                      |
| `ListenerService.java`  | Interface for listener scanning and registration via the DI container.                 |

### Subpackage: `placeholder/`

Advanced placeholder system with effects.

| Class                                 | Description                                                                    |
|---------------------------------------|--------------------------------------------------------------------------------|
| `PlaceholderHandler.java`             | Interface for resolving named placeholders to string values.                   |
| `PlaceholderRequest.java`             | Encapsulates a placeholder resolution request (player, identifier, arguments). |
| `effect/FlashEffect.java`             | Alternating text flash effect for scoreboard/action bar placeholders.          |
| `effect/FlashPlaceholderWrapper.java` | Wraps a placeholder with a flash animation effect.                             |
| `effect/ConditionalFlashWrapper.java` | Flash effect that activates only when a condition is met.                      |
| `replacer/Replacer.java`              | Functional interface for string token replacement.                             |
| `replacer/CharReplacer.java`          | Character-level replacement implementation.                                    |

### Subpackage: `timer/`

Scheduled task timer abstractions.

| Class                 | Description                                                                        |
|-----------------------|------------------------------------------------------------------------------------|
| `Timer.java`          | Base abstract timer with start/stop lifecycle.                                     |
| `CountdownTimer.java` | Counts down from a value to zero, firing callbacks at each tick and on completion. |
| `CountupTimer.java`   | Counts up from zero, firing callbacks at each tick.                                |
| `RepeatingTimer.java` | Repeats a task at a fixed interval (used by scoreboard refresh).                   |
| `SequenceTimer.java`  | Executes a sequence of timed actions in order.                                     |

### Subpackage: `utilities/`

Static utility classes used across all modules.

| Class                                | Description                                                                                                                          |
|--------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------|
| `ActionBarManager.java`              | Sends and manages action bar messages with priority queuing. All action bar sends must go through this class.                        |
| `ChatUtil.java`                      | Chat message formatting, color translation, and prefix utilities. Chat-only -- never used for action bars.                           |
| `DatabaseUtil.java`                  | Database connection testing and SQL utility methods.                                                                                 |
| `NumberUtil.java`                    | Number formatting, parsing, and abbreviation (e.g., `1000` to `1K`).                                                                 |
| `ParticleUtil.java`                  | Particle effect spawning utilities.                                                                                                  |
| `PlayerUtil.java`                    | Player state checks, inventory manipulation, and location utilities.                                                                 |
| `ReflectionUtil.java`                | Class discovery from JAR and filesystem, instantiation, and method invocation helpers. Used by DI container and repository scanning. |
| `TimeUtil.java`                      | Duration formatting and parsing (ticks to human-readable strings).                                                                   |
| `messages/TimeMessagesProvider.java` | Interface for localized time unit strings ("seconds", "minutes", etc.).                                                              |

### Root-Level Classes

| Class                 | Description                                                                                                              |
|-----------------------|--------------------------------------------------------------------------------------------------------------------------|
| `ItemBuilder.java`    | Fluent `ItemStack` builder with chained methods for name, lore, enchantments, flags, NBT data, skull textures, and more. |
| `Pair.java`           | Generic immutable pair (tuple of two values).                                                                            |
| `Placeholder.java`    | Simple `{key}` placeholder resolution utility.                                                                           |
| `TriConsumer.java`    | Functional interface accepting three arguments.                                                                          |
| `UnhandledError.java` | Wrapper for unhandled exceptions with context information.                                                               |

---

## gangland-item (Item System)

**Purpose:** Defines the item parsing framework, item converters, fuel system, unique items, and wearable armor. Used by
both `gangland-impl` (for concrete converters) and feature modules (for item type definitions).

**Java Files:** 13 | **Package:** `me.luckyraven.item.*`

### Root Classes

| Class                        | Description                                                                                                                                    |
|------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------|
| `ItemParser.java`            | Parses item format strings like `"TYPE:modifier{key=value,...}"` via regex. Delegates to registered `ItemConverter` implementations.           |
| `ItemConverter.java`         | Interface for converting a parsed item string into a Bukkit `ItemStack`. Each converter handles one prefix (e.g., `weapon:`, `ammo:`, `car:`). |
| `ItemConverterRegistry.java` | Registry of `ItemConverter` instances. Matches an input string to the appropriate converter by prefix.                                         |

### Subpackage: `fuel/`

Fuel system for vehicles and jetpacks.

| Class          | Description                                                                                       |
|----------------|---------------------------------------------------------------------------------------------------|
| `Fuel.java`    | Fuel data model (`@Builder`). Holds capacity, current level, consumption rate, and refuel amount. |
| `FuelBar.java` | Action bar fuel gauge rendering. Displays a 20-segment visual bar (`                              |||||||||...`) with color gradient from green to red. |
| `FuelKey.java` | NBT tag key constants for persisting fuel data on items (`fuel_capacity`, `fuel_current`, etc.).  |

### Subpackage: `unique/`

Unique items -- special items with custom behaviors.

| Class                 | Description                                                                                                                                                             |
|-----------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `UniqueItem.java`     | Unique item data model (`@Builder`). Properties: permission, material, display name, lore, `addOnJoin`, `addOnRespawn`, `dropOnDeath`, `inventorySlot`, fuel reference. |
| `UniqueItemKeys.java` | NBT tag key constants for unique item identification.                                                                                                                   |
| `UniqueItemUtil.java` | Utility methods for checking if an `ItemStack` is a unique item and extracting its identifier.                                                                          |

### Subpackage: `wearable/`

Wearable armor with special traits.

| Class                | Description                                                                                                                                                                                                    |
|----------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Wearable.java`      | Wearable armor data model (`@Builder`). Properties: name, material, armor slot, damage reduction percentage, `WearableTrait` set, leather color, jetpack flag, fuel reference.                                 |
| `WearableTrait.java` | Enum of armor enhancement types: `REINFORCED` (extra durability), `BULLETPROOF` (reduced projectile damage), `FIREPROOF` (fire resistance), `EXPLOSIVE_RESISTANT`, `PADDED` (fall damage reduction), and more. |

### Subpackage: `repair/`

Repair system interfaces.

| Class                 | Description                                                                                                             |
|-----------------------|-------------------------------------------------------------------------------------------------------------------------|
| `Repairable.java`     | Interface for items that can be repaired. Defines `getMaxDurability()`, `getCurrentDurability()`, and `repair(amount)`. |
| `RepairableType.java` | Enum of repairable item categories: `WEAPON`, `WEARABLE`.                                                               |

---

## plugin-persistence (Database & Repository)

**Purpose:** Generic persistence layer providing the repository pattern, database abstraction (MySQL/SQLite), table
definitions, query building, and file persistence utilities. This module has no knowledge of Gangland-specific entities.

**Java Files:** 20 | **Package:** `me.luckyraven.persistence.*`

### Root Classes (File Persistence)

| Class               | Description                                                                                               |
|---------------------|-----------------------------------------------------------------------------------------------------------|
| `FileManager.java`  | Manages YAML file creation, loading, and saving. Handles resource extraction from the plugin JAR.         |
| `FileHandler.java`  | Low-level file I/O operations with exception handling.                                                    |
| `FileLoader.java`   | Loads a single YAML file into a Bukkit `FileConfiguration`.                                               |
| `FolderLoader.java` | Loads all YAML files from a directory into a map of `FileConfiguration` objects. Used for weapon configs. |

### Subpackage: `repository/`

Repository pattern implementation.

| Class                     | Description                                                                                                                                                                                                                                                                                |
|---------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `IRepository<T>`          | Core CRUD interface: `loadAll()`, `save(T)`, `saveAll(Collection<T>)`, `delete(T)`, `setDataSupplier(Supplier<Collection<T>>)`.                                                                                                                                                            |
| `AbstractRepository<T>`   | Template pattern implementation. Provides sync/async save via `DatabaseHelper`, lifecycle management (`load`, `shutdown`). Subclasses implement: `doLoadAll()`, `processSave(T, DatabaseHelper)`, `getTable()`, `doDelete(T, DatabaseHelper)`.                                             |
| `Repository.java`         | Annotation marking a concrete repository class for auto-discovery by `RepositoryRegistry`. Includes the entity table class reference.                                                                                                                                                      |
| `RepositoryRegistry.java` | `Map<Class<?>, RepositoryEntry>` keyed by entity type. Scans a package for `@Repository`-annotated classes, resolves constructor dependencies, sorts by table foreign-key dependencies, and auto-registers. Registering two repositories under the same entity class overwrites the first. |

### Subpackage: `database/`

Database abstraction layer.

| Class                           | Description                                                                                                                                                           |
|---------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Database.java`                 | Abstract base class for database engines. Provides `table(Table)`, `select()`, `insert()`, `update()`, `delete()` methods that delegate to the underlying connection. |
| `DatabaseHandler.java`          | HikariCP connection pool manager. Handles pool configuration, connection acquisition, and shutdown.                                                                   |
| `DatabaseHelper.java`           | Wraps `DatabaseHandler` for transactional operations. Provides `execute(callback)` with automatic connection management and rollback on error.                        |
| `DatabaseManager.java`          | High-level database lifecycle manager. Coordinates table creation, schema validation, and migration.                                                                  |
| `DatabaseSettingsProvider.java` | Interface contract for database configuration values (host, port, database name, credentials, pool size). Implemented by `gangland-impl`.                             |

### Subpackage: `database/component/`

Table schema building blocks.

| Class                | Description                                                                                                                                                                    |
|----------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Table<T>.java`      | Generic table definition. Subclasses define: `searchCriteria(T)` (WHERE clause), `createTableQuery()` (DDL), `validateSchema()` (column checks), and column-to-field mappings. |
| `Attribute.java`     | Column descriptor: name, SQL type, nullable flag, default value, primary key flag.                                                                                             |
| `AttributeLink.java` | Foreign key descriptor: source column, referenced table, referenced column, cascade behavior.                                                                                  |

### Subpackage: `database/query/`

Fluent SQL query construction.

| Class               | Description                                                                                                                                                                            |
|---------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `QueryBuilder.java` | Fluent API for building SQL statements: `select()`, `from()`, `where()`, `join()`, `orderBy()`, `limit()`, `insert()`, `update()`, `delete()`. Parameterized to prevent SQL injection. |
| `Column.java`       | Represents a column reference in a query (table alias, column name).                                                                                                                   |

### Subpackage: `database/type/`

Database engine implementations.

| Class         | Description                                                                                                                                                                         |
|---------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `MySQL.java`  | MySQL implementation of `Database`. Configures HikariCP with MySQL-specific settings (JDBC URL, driver class, connection properties).                                               |
| `SQLite.java` | SQLite implementation of `Database`. Configures HikariCP for file-based SQLite with appropriate pool settings. On Windows, special care is needed for file locking (see CLAUDE.md). |

---

## plugin-common (Logger & Exceptions)

**Purpose:** Provides the shared logging utility and exception hierarchy used by all modules. Minimal dependency
footprint.

**Java Files:** 2 | **Package:** `me.luckyraven.*`

| Class                            | Description                                                                                                                                                                                                                                                                                                           |
|----------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `logger/Logger.java`             | Static logging utility. Resolves the calling module name via `module.properties` resource files. Caches module names in a `ConcurrentHashMap`. Detects Spigot at runtime to use `java.util.logging.Logger` (Bukkit) or `System.out` (test/standalone). Provides `info()`, `warn()`, `error()`, and `debug()` methods. |
| `exception/PluginException.java` | `RuntimeException` subclass with full constructor variants (message, cause, message+cause). Base exception for all plugin-specific errors.                                                                                                                                                                            |

---

## gangland-features/cops-n-crooks

**Purpose:** The cops-and-crooks gameplay system. Handles NPC cop/civilian spawning, wanted levels, bounties, kill
combos, detainment (handcuffs + jailing), and all associated AI behaviors.

**Java Files:** 91 | **Package:** `me.luckyraven.copsncrooks.*`

### Subpackage: `bounty/`

Bounty tracking and reward system.

| Class                 | Description                                                                                                                   |
|-----------------------|-------------------------------------------------------------------------------------------------------------------------------|
| `Bounty.java`         | Bounty data model for a player (amount, source, timestamp).                                                                   |
| `BountyContext.java`  | Context object passed to bounty calculations (killer, victim, multipliers).                                                   |
| `BountyExecutor.java` | Core bounty logic. Calculates bounty rewards on kill with multipliers based on victim's wanted level and killer's kill combo. |
| `BountySettings.java` | Interface contract for bounty configuration values (base reward, multipliers, max bounty).                                    |

### Subpackage: `combo/`

Kill combo tracking.

| Class                   | Description                                                                |
|-------------------------|----------------------------------------------------------------------------|
| `KillCombo.java`        | Tracks consecutive kills within a time window. Resets on death or timeout. |
| `KillComboTracker.java` | Per-player kill combo state management with expiration timers.             |

### Subpackage: `detainment/`

Handcuff and detainment system.

| Class                     | Description                                                                                                                                           |
|---------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| `DetainmentService.java`  | Core detainment logic. Manages handcuff application (progress bar), jailing, and release. Includes visual effects (particles, sounds) during cuffing. |
| `DetainmentRegistry.java` | Tracks currently detained players in memory.                                                                                                          |
| `DetainedPlayer.java`     | Data model for a detained player (UUID, jail location, detain time, sentence duration).                                                               |
| `DetainmentState.java`    | Enum: `FREE`, `BEING_CUFFED`, `CUFFED`, `JAILED`.                                                                                                     |

### Subpackage: `entity/`

Abstract NPC and spawner framework shared by cops and civilians.

| Class                          | Description                                                                                                                  |
|--------------------------------|------------------------------------------------------------------------------------------------------------------------------|
| `EntitySpawner.java`           | Wave-based NPC spawning engine. Manages spawn caps, spawn intervals, proximity activation, and despawn on distance.          |
| `EntitySpawnerPoint.java`      | A configured spawn point (location, radius, max NPCs, activation distance).                                                  |
| `EntityMark.java`              | Marker data for an NPC entity (type identifier, group assignment).                                                           |
| `EntityMarkManager.java`       | Manages entity marks and their lifecycle.                                                                                    |
| `SpawnConfigProvider.java`     | Interface contract for spawn configuration values (spawn interval, max per point, activation radius).                        |
| `npc/AbstractNpc.java`         | Base NPC class wrapping a Citizens NPC. Provides health, equipment, navigation, weapon handling, and behavior state machine. |
| `npc/NpcBehavior.java`         | Interface for NPC behavior states. Defines `onEnter()`, `onTick()`, `onExit()`, and `shouldTransition()`.                    |
| `npc/NpcNavigationConfig.java` | Navigation configuration (speed, attack range, pathfinding distance).                                                        |

### Subpackage: `npc/civilian/`

Civilian NPC system.

| Class                                        | Description                                                                                                 |
|----------------------------------------------|-------------------------------------------------------------------------------------------------------------|
| `CivilianService.java`                       | High-level civilian NPC management. Spawning, despawning, group assignment, and behavior coordination.      |
| `CivilianGroup.java`                         | Groups civilians by type (e.g., "shopkeeper", "pedestrian") with shared configuration.                      |
| `CivilianState.java`                         | Enum of civilian behavior states: `IDLE`, `WANDER`, `FLEE`, `COMBAT`, `LOOK`.                               |
| `npc/CivilianNpc.java`                       | Concrete civilian NPC. Extends `AbstractNpc` with civilian-specific equipment, drops, and behavior factory. |
| `npc/CivilianNpcFactory.java`                | Factory for creating `CivilianNpc` instances from configuration.                                            |
| `spawn/CivilianSpawnManager.java`            | Manages civilian spawner points and proximity-based activation.                                             |
| `spawn/CivilianSpawner.java`                 | Per-spawner-point civilian spawning logic.                                                                  |
| `state/CivilianBehavior.java`                | Base interface for civilian behavior implementations.                                                       |
| `state/CivilianBehaviorFactory.java`         | Factory creating behavior instances based on `CivilianState`.                                               |
| `state/behavior/CivilianIdleBehavior.java`   | Standing still, occasional idle animations.                                                                 |
| `state/behavior/CivilianWanderBehavior.java` | Random movement within a radius of spawn point.                                                             |
| `state/behavior/CivilianFleeBehavior.java`   | Runs away from threats (gunfire, nearby combat).                                                            |
| `state/behavior/CivilianCombatBehavior.java` | Armed civilians fight back when attacked.                                                                   |
| `state/behavior/CivilianLookController.java` | Looks at nearby players or points of interest.                                                              |

**Civilian Configuration Classes (`config/`):**

| Class                                 | Description                                                               |
|---------------------------------------|---------------------------------------------------------------------------|
| `CivilianSettings.java`               | Interface contract for civilian global settings.                          |
| `CivilianGroupConfig.java`            | Configuration for a civilian group (spawn weight, max count).             |
| `CivilianTypeConfig.java`             | Configuration for a civilian type (skin, name format).                    |
| `CivilianAIBehaviorConfig.java`       | AI behavior parameters (flee distance, combat aggression, wander radius). |
| `CivilianNavigationConfig.java`       | Pathfinding settings (speed, range).                                      |
| `CivilianInventoryConfig.java`        | Equipment and inventory configuration.                                    |
| `CivilianWearableConfig.java`         | Armor configuration for civilians.                                        |
| `CivilianDropConfig.java`             | Drop table configuration on death.                                        |
| `EntityMarkerConfig.java`             | Top-level entity marker configuration container.                          |
| `EntityMarkerLoader.java`             | Loads entity marker config from YAML.                                     |
| `YamlEntityMarkerConfigProvider.java` | YAML-based implementation of entity marker config.                        |

### Subpackage: `npc/police/`

Cop NPC system.

| Class                                   | Description                                                                                                |
|-----------------------------------------|------------------------------------------------------------------------------------------------------------|
| `CopManager.java`                       | Core cop NPC management with tier-based spawning, target tracking, and weapon pool assignment.             |
| `CopService.java`                       | High-level cop lifecycle: spawn triggered by wanted level, despawn on wanted level decay, tier escalation. |
| `CopGroup.java`                         | Groups cops by tier for coordinated behavior.                                                              |
| `npc/CopNpc.java`                       | Concrete cop NPC. 5 tiers of increasing difficulty (better weapons, armor, health).                        |
| `npc/CopNpcFactory.java`                | Factory for creating `CopNpc` instances with tier-appropriate equipment.                                   |
| `spawn/CopSpawnManager.java`            | Manages cop spawner points. Activated by wanted level thresholds.                                          |
| `spawn/CopSpawner.java`                 | Per-spawner-point cop spawning logic with wave escalation.                                                 |
| `state/CopState.java`                   | Enum of cop behavior states: `IDLE`, `PURSUING`, `COMBAT`, `CUFFING`, `RETURNING`.                         |
| `state/CopBehavior.java`                | Base interface for cop behavior implementations.                                                           |
| `state/CopBehaviorFactory.java`         | Factory creating cop behavior instances based on `CopState`.                                               |
| `state/CuffLockRegistry.java`           | Prevents multiple cops from cuffing the same player simultaneously.                                        |
| `state/behavior/IdleBehavior.java`      | Patrolling or standing at spawn point.                                                                     |
| `state/behavior/PursuingBehavior.java`  | Chasing a wanted player.                                                                                   |
| `state/behavior/CombatBehavior.java`    | Engaging a wanted player with weapons.                                                                     |
| `state/behavior/CuffingBehavior.java`   | Approaching and handcuffing a downed player.                                                               |
| `state/behavior/ReturningBehavior.java` | Returning to patrol area after losing target.                                                              |
| `targeting/TargetingManager.java`       | Target selection logic for cops.                                                                           |
| `targeting/WantedTargetingManager.java` | Prioritizes targets by wanted level and proximity.                                                         |

**Cop Configuration Classes (`config/`):**

| Class                        | Description                                                        |
|------------------------------|--------------------------------------------------------------------|
| `CopSettings.java`           | Interface contract for cop global settings.                        |
| `CopConfig.java`             | Top-level cop configuration container.                             |
| `CopConfigProvider.java`     | Interface for providing cop configuration.                         |
| `CopLoader.java`             | Loads cop configuration from YAML.                                 |
| `CopTierConfig.java`         | Per-tier configuration (health, armor, weapon pools, spawn count). |
| `YamlCopConfigProvider.java` | YAML-based implementation of cop config provider.                  |

### Subpackage: `wanted/`

Wanted level system.

| Class                 | Description                                                                                                                          |
|-----------------------|--------------------------------------------------------------------------------------------------------------------------------------|
| `WantedExecutor.java` | Core wanted level logic. Manages levels 0-5 with escalating consequences. Higher levels trigger more cop spawns and tier escalation. |
| `Wanted.java`         | Wanted state data model (level, decay timer, last crime timestamp).                                                                  |
| `WantedContext.java`  | Context for wanted level changes (crime type, severity, location).                                                                   |
| `WantedSettings.java` | Interface contract for wanted configuration (decay rate, level thresholds, cop trigger levels).                                      |

### Subpackage: `jail/`

Jail system.

| Class               | Description                                                                                    |
|---------------------|------------------------------------------------------------------------------------------------|
| `JailRegistry.java` | In-memory registry of all jail locations.                                                      |
| `JailService.java`  | Jail assignment logic: capacity checks, nearest jail selection, sentence duration calculation. |
| `Jail.java`         | Jail data model (name, location, capacity, current occupants).                                 |

### Subpackage: `listener/`

Event listeners for the cops-n-crooks system.

| Subpackage    | Listeners                                                                                                                                            |
|---------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| `civilian/`   | `CivilianDamageListener` (damage processing), `CivilianDeathListener` (death handling, drops), `CivilianInteractListener` (right-click interactions) |
| `detainment/` | `CopListener` (cop NPC death and combat events)                                                                                                      |
| `police/`     | `CuffingListener` (handcuff progress events), `DetainmentListener` (jail/release events)                                                             |

### Subpackage: `events/`

Custom Bukkit events (11 total).

| Subpackage | Events                                                                        | Description                      |
|------------|-------------------------------------------------------------------------------|----------------------------------|
| `bounty/`  | `BountyEvent`                                                                 | Fired on bounty change.          |
| `combo/`   | `KillComboEvent`                                                              | Fired on kill combo milestone.   |
| `npc/`     | `NpcEvent`, `CivilianDeathEvent`, `CopDeathEvent`                             | NPC lifecycle events.            |
| `police/`  | `CuffedEvent`, `DuringCuffingEvent`                                           | Cuffing progress and completion. |
| `wanted/`  | `WantedEvent`, `WantedStartEvent`, `WantedEndEvent`, `WantedLevelChangeEvent` | Wanted level lifecycle.          |

---

## gangland-features/gangland-weapon

**Purpose:** Complete weapon, ammunition, and projectile system. Defines weapon types (guns, melee, throwables,
incendiaries, biological), projectile physics, damage modifiers, reload mechanics, spread/recoil, and selective fire
modes.

**Java Files:** 83 | **Package:** `me.luckyraven.weapon.*`

### Root Classes

| Class                | Description                                                                                                                                                    |
|----------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Weapon.java`        | Core weapon data model. Contains all weapon properties: damage, ammo capacity, reload time, fire rate, projectile configuration, modifiers, scope, durability. |
| `WeaponService.java` | Interface for weapon resolution and management operations.                                                                                                     |
| `WeaponTag.java`     | NBT tag key constants for weapon data persistence on items.                                                                                                    |
| `SelectiveFire.java` | Enum of fire modes: `SEMI_AUTO`, `BURST`, `FULL_AUTO`. Weapons can support multiple modes that players toggle.                                                 |

### Subpackage: `types/`

Weapon type implementations.

| Class                              | Description                                                                                                |
|------------------------------------|------------------------------------------------------------------------------------------------------------|
| `WeaponType.java`                  | Enum: `GUN`, `MELEE`, `THROWABLE`, `INCENDIARY`, `BIOLOGICAL`.                                             |
| `gun/GunWeapon.java`               | Gun implementation. Handles shooting, ammo consumption, fire rate limiting, and full-auto task scheduling. |
| `gun/GunAction.java`               | Gun-specific action handler (left-click = shoot, right-click = scope/reload).                              |
| `gun/FullAutoTask.java`            | BukkitRunnable that fires projectiles at the weapon's fire rate while the player holds right-click.        |
| `melee/MeleeWeapon.java`           | Melee weapon implementation. Handles swing, hit detection, and damage application.                         |
| `melee/MeleeAction.java`           | Melee-specific action handler.                                                                             |
| `throwable/ThrowableWeapon.java`   | Throwable weapon implementation (grenades). Handles throw arc, fuse timer, and area-of-effect damage.      |
| `throwable/ThrowableAction.java`   | Throwable-specific action handler.                                                                         |
| `incendiary/IncendiaryWeapon.java` | Incendiary weapon implementation (flamethrower, molotov). Handles fire spread and burn damage over time.   |
| `incendiary/IncendiaryAction.java` | Incendiary-specific action handler.                                                                        |
| `biological/BiologicalWeapon.java` | Biological weapon implementation (syringe gun). Handles potion effect application on hit.                  |
| `biological/BiologicalAction.java` | Biological-specific action handler.                                                                        |

### Subpackage: `ammo/`

Ammunition system.

| Class                    | Description                                                                        |
|--------------------------|------------------------------------------------------------------------------------|
| `Ammunition.java`        | Ammunition data model (name, material, max stack size, price, compatible weapons). |
| `AmmunitionManager.java` | Registry and lookup for ammunition types. Manages ammo consumption and restocking. |

### Subpackage: `projectile/`

Projectile physics and rendering.

| Class                       | Description                                                                                                                         |
|-----------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| `WeaponProjectile.java`     | Core projectile class. Manages projectile lifecycle: launch, tick-based movement, collision detection, hit processing, and cleanup. |
| `WProjectile.java`          | Lightweight projectile data wrapper.                                                                                                |
| `ProjectileState.java`      | Enum: `FLYING`, `HIT_ENTITY`, `HIT_BLOCK`, `EXPIRED`.                                                                               |
| `ProjectileType.java`       | Enum: `BULLET`, `FLARE`, `ROCKET`, `SPREAD` (shotgun).                                                                              |
| `BlockDamageManager.java`   | Tracks and manages block destruction from explosive/high-caliber projectiles. Handles block regeneration timers.                    |
| `recoil/RecoilManager.java` | Applies camera recoil to the shooter via NMS packets. Recoil pattern varies by weapon and stance (standing/crouching/moving).       |
| `spread/SpreadManager.java` | Calculates projectile spread based on weapon accuracy, player movement state, and consecutive shots.                                |
| `type/Bullet.java`          | Standard bullet projectile (hitscan or fast particle).                                                                              |
| `type/Flare.java`           | Flare projectile with light emission and slow descent.                                                                              |
| `type/Rocket.java`          | Rocket projectile with explosion on impact.                                                                                         |
| `type/Spread.java`          | Shotgun spread -- fires multiple pellet projectiles in a cone.                                                                      |

### Subpackage: `modifiers/`

Damage modifiers applied to projectiles.

| Class                        | Description                                                        |
|------------------------------|--------------------------------------------------------------------|
| `ModifierHandler.java`       | Processes the modifier chain for a projectile hit.                 |
| `ArmorPiercingModifier.java` | Ignores a percentage of the target's armor value.                  |
| `BlockBreakModifier.java`    | Destroys blocks on impact (e.g., high-caliber rounds).             |
| `FlatDamageModifier.java`    | Adds flat bonus damage to the hit.                                 |
| `PenetrationModifier.java`   | Projectile passes through entities, hitting multiple targets.      |
| `RicochetModifier.java`      | Projectile bounces off surfaces and can hit additional targets.    |
| `TracerModifier.java`        | Renders a visible tracer particle trail along the projectile path. |

### Subpackage: `reload/`

Reload mechanics.

| Class                      | Description                                                                                    |
|----------------------------|------------------------------------------------------------------------------------------------|
| `Reload.java`              | Base reload abstraction. Manages reload state, cancellation, and completion callbacks.         |
| `ReloadType.java`          | Enum: `INSTANT`, `NUMBERED`.                                                                   |
| `type/InstantReload.java`  | Full magazine reload in a single action after a delay.                                         |
| `type/NumberedReload.java` | Per-round reload (e.g., shotgun shell-by-shell). Can be interrupted to fire with partial load. |

### Subpackage: `durability/`

| Class                       | Description                                                                    |
|-----------------------------|--------------------------------------------------------------------------------|
| `DurabilityCalculator.java` | Calculates weapon durability loss per shot/use and determines weapon breakage. |

### Subpackage: `wearable/`

| Class                  | Description                                                                                               |
|------------------------|-----------------------------------------------------------------------------------------------------------|
| `WearableService.java` | Manages wearable equipment effects: damage reduction calculation, trait application, and armor condition. |

### Subpackage: `configuration/`

Weapon configuration loading.

| Class                                 | Description                                                         |
|---------------------------------------|---------------------------------------------------------------------|
| `WeaponAddon.java`                    | Configuration addon interface for weapon YAML loading.              |
| `AmmunitionAddon.java`                | Configuration addon for ammunition YAML loading.                    |
| `parser/WeaponBaseData.java`          | Shared base data extracted from weapon YAML (name, material, lore). |
| `parser/GunWeaponParser.java`         | Parses gun weapon definitions from YAML sections.                   |
| `parser/MeleeWeaponParser.java`       | Parses melee weapon definitions.                                    |
| `parser/ThrowableWeaponParser.java`   | Parses throwable weapon definitions.                                |
| `parser/IncendiaryWeaponParser.java`  | Parses incendiary weapon definitions.                               |
| `parser/BiologicalWeaponParser.java`  | Parses biological weapon definitions.                               |
| `parser/AmmunitionSectionParser.java` | Parses ammunition section from YAML.                                |

### Subpackage: `dto/`

Data transfer objects for weapon configuration.

| DTO                   | Description                                             |
|-----------------------|---------------------------------------------------------|
| `AmmunitionData`      | Ammo type, max stack, price.                            |
| `BiologicalData`      | Potion effects, duration, amplifier.                    |
| `DamageData`          | Base damage, headshot multiplier, falloff.              |
| `DurabilityData`      | Max durability, loss per shot.                          |
| `IncendiaryData`      | Fire duration, spread radius, tick damage.              |
| `MeleeData`           | Swing speed, range, knockback.                          |
| `ModifiersData`       | Modifier type and parameters.                           |
| `ProjectileData`      | Speed, gravity, lifetime, particle.                     |
| `RecoilData`          | Horizontal/vertical recoil, recovery rate.              |
| `ReloadData`          | Reload time, type, action bar display.                  |
| `ReloadActionBarData` | Action bar progress rendering during reload.            |
| `ScopeData`           | Zoom level, overlay, movement speed while scoped.       |
| `SoundData`           | Shoot/reload/hit sound references.                      |
| `SpreadData`          | Base spread, movement spread modifier, crouch modifier. |
| `ThrowableData`       | Throw force, fuse time, explosion radius.               |

### Subpackage: `listener/`

Weapon event listeners.

| Listener                                           | Description                                                                       |
|----------------------------------------------------|-----------------------------------------------------------------------------------|
| `WeaponInteract.java`                              | Handles left/right click with a weapon in hand. Dispatches to weapon type action. |
| `ScopeJumpListener.java`                           | Prevents jumping while scoped.                                                    |
| `projectile/ProjectileDamageListener.java`         | Processes projectile-entity collisions and applies damage with modifiers.         |
| `reload/WeaponReloadListener.java`                 | Handles reload initiation (sneak + right-click or empty magazine).                |
| `reload/WeaponDroppedListener.java`                | Cancels active reloads when a weapon is dropped.                                  |
| `selective/WeaponSelectiveFireChangeListener.java` | Handles fire mode switching (e.g., semi-auto to full-auto).                       |

### Subpackage: `events/`

Custom weapon events (10 total).

| Event                                           | Description                                         |
|-------------------------------------------------|-----------------------------------------------------|
| `WeaponEvent.java`                              | Base weapon event with player and weapon reference. |
| `WeaponEntityDamageEvent.java`                  | Fired when a weapon damages an entity.              |
| `WeaponKillEntityEvent.java`                    | Fired when a weapon kills an entity.                |
| `projectile/WeaponShootEvent.java`              | Fired when a player shoots.                         |
| `projectile/WeaponProjectileLaunchEvent.java`   | Fired when a projectile is launched.                |
| `projectile/WeaponProjectileHitEvent.java`      | Fired when a projectile hits something.             |
| `reload/WeaponReloadEvent.java`                 | Base reload event.                                  |
| `reload/WeaponReloadStartEvent.java`            | Fired when reload begins.                           |
| `reload/WeaponReloadCompleteEvent.java`         | Fired when reload completes.                        |
| `selective/WeaponChangeSelectiveFireEvent.java` | Fired when fire mode is toggled.                    |

### Subpackage: `util/`

| Class                     | Description                                                                                     |
|---------------------------|-------------------------------------------------------------------------------------------------|
| `BlockGroupResolver.java` | Resolves which blocks can be broken by block-break modifiers based on material hardness groups. |

---

## gangland-features/gangland-gadget

**Purpose:** Vehicle system (cars), jetpacks, fuel management, repair system, and wearable equipment mechanics.

**Java Files:** 47 | **Package:** `me.luckyraven.gadget.*`

### Root Class

| Class             | Description                                  |
|-------------------|----------------------------------------------|
| `GadgetType.java` | Enum of gadget categories: `CAR`, `JETPACK`. |

### Subpackage: `car/`

Vehicle system.

| Class                  | Description                                                                                                                            |
|------------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| `CarService.java`      | High-level vehicle management. Handles spawning, despawning, ownership checks, fuel tracking, damage, speed calculations, and parking. |
| `Car.java`             | Car data model (type, owner, fuel, health, speed, location).                                                                           |
| `CarKey.java`          | NBT tag constants for car item identification and data.                                                                                |
| `CarManager.java`      | Registry of active vehicles. Tracks spawned cars and their sessions.                                                                   |
| `ParkedCar.java`       | Persisted parked car data (owner UUID, car type, park location).                                                                       |
| `ExhaustSide.java`     | Enum for exhaust particle emission side (LEFT, RIGHT, BOTH).                                                                           |
| `config/CarAddon.java` | Configuration addon for car YAML loading.                                                                                              |

**Vehicle Subsystem (`car/vehicle/`):**

| Class                                 | Description                                                                                                                                                |
|---------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `VehicleMovementTask.java`            | Tick-based vehicle physics. Processes WASD input, applies acceleration/deceleration, handles steering, collision detection, and fuel consumption per tick. |
| `VehicleSession.java`                 | Active driving session (player, car, start time, input state).                                                                                             |
| `VehicleRegistry.java`                | Maps Bukkit entities to vehicle sessions for event handling.                                                                                               |
| `ParkedVehicle.java`                  | Represents a parked vehicle entity in the world.                                                                                                           |
| `entity/VehicleEntity.java`           | Interface for the underlying Bukkit entity representing a vehicle.                                                                                         |
| `entity/MinecartVehicle.java`         | Minecart-based vehicle entity implementation.                                                                                                              |
| `packet/VehicleInputInterceptor.java` | Intercepts player input packets (WASD, jump, sneak) for vehicle control.                                                                                   |

### Subpackage: `fuel/`

| Class              | Description                                                                                                                                  |
|--------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| `FuelService.java` | Fuel management service. Handles fuel capacity, consumption rate calculation, refueling from fuel items, and fuel level persistence via NBT. |

### Subpackage: `jetpack/`

Jetpack flight system.

| Class                                 | Description                                                                                                       |
|---------------------------------------|-------------------------------------------------------------------------------------------------------------------|
| `JetpackService.java`                 | Jetpack management. Handles activation (jump while wearing jetpack wearable), fuel consumption, and deactivation. |
| `JetpackSession.java`                 | Active jetpack flight session (player, fuel state, velocity).                                                     |
| `JetpackTask.java`                    | Tick-based jetpack physics. Applies upward velocity, particle effects, fuel drain, and fall damage negation.      |
| `packet/JetpackInputInterceptor.java` | Intercepts jump/sneak packets for jetpack thrust control.                                                         |

### Subpackage: `config/`

| Class                      | Description                                                                                                             |
|----------------------------|-------------------------------------------------------------------------------------------------------------------------|
| `GadgetPhysicsConfig.java` | Interface contract for physics parameters (acceleration, max speed, friction, gravity). Implemented by `gangland-impl`. |

### Subpackage: `repair/`

Item repair system.

| Class                                  | Description                                                                                                               |
|----------------------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `RepairManager.java`                   | Core repair logic. Validates repair materials, calculates restoration amount (flat + percentage), and applies durability. |
| `RepairKeys.java`                      | NBT tag constants for repair-related data.                                                                                |
| `RepairMessages.java`                  | Interface contract for repair feedback messages.                                                                          |
| `anvil/RepairAnvilGui.java`            | Anvil-based GUI for repairing items with materials.                                                                       |
| `config/RepairConfig.java`             | Top-level repair configuration container.                                                                                 |
| `config/RepairConfigProvider.java`     | Interface for providing repair configuration.                                                                             |
| `config/RepairLoader.java`             | Loads repair configuration from YAML.                                                                                     |
| `config/RepairMaterialData.java`       | Material cost definition (material type, quantity, restoration amount).                                                   |
| `config/YamlRepairConfigProvider.java` | YAML-based repair config implementation.                                                                                  |
| `material/RepairMaterial.java`         | Repair material data model.                                                                                               |
| `material/RepairMaterialManager.java`  | Registry of valid repair materials and their restoration values.                                                          |
| `events/RepairEvent.java`              | Base repair event.                                                                                                        |
| `events/RepairStartEvent.java`         | Fired when a repair begins.                                                                                               |
| `events/RepairCompleteEvent.java`      | Fired when a repair completes.                                                                                            |

### Subpackage: `wearable/`

| Class                | Description                                    |
|----------------------|------------------------------------------------|
| `WearableAddon.java` | Configuration addon for wearable YAML loading. |

### Subpackage: `listener/`

Event listeners (11 total).

| Subpackage  | Listeners                                                                                      | Description                                                                           |
|-------------|------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------|
| `car/`      | `CarInteractListener`, `CarEntityInteractListener`, `CarDamageListener`, `CarDismountListener` | Right-click to enter, entity collision, vehicle damage processing, dismount handling. |
| `fuel/`     | `FuelHoldDisplayListener`, `FuelRefuelListener`                                                | Fuel bar display when holding fuel item, refueling on right-click.                    |
| `jetpack/`  | `JetpackActivateListener`, `JetpackEquipListener`, `JetpackFallDamageListener`                 | Jetpack activation on jump, equip detection, fall damage cancellation while active.   |
| `repair/`   | `RepairListener`                                                                               | Anvil interaction for repair processing.                                              |
| `wearable/` | `WearableEquipListener`                                                                        | Wearable armor equip/unequip and trait activation.                                    |

---

## gangland-ui/scoreboard-api

**Purpose:** FastBoard-based scoreboard rendering system with ViaVersion compatibility and progressive optimization
strategies.

**Java Files:** 8 | **Package:** `me.luckyraven.scoreboard.*`

| Class                                | Description                                                                                                                                           |
|--------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Scoreboard.java`                    | Main scoreboard orchestrator. Creates a per-player `DriverHandler`, manages `Line` instances, and uses `RepeatingTimer` for periodic refresh.         |
| `configuration/ScoreboardAddon.java` | Configuration addon interface for scoreboard YAML loading (title, lines, refresh interval).                                                           |
| `driver/DriverHandler.java`          | Abstract wrapper around FastBoard. Handles board creation, destruction, and line updates. Selects driver version based on ViaVersion player protocol. |
| `driver/version/DriverV1.java`       | Basic driver. Full board update on every refresh tick.                                                                                                |
| `driver/version/DriverV2.java`       | Optimized driver. Only updates lines that have changed since last refresh.                                                                            |
| `driver/version/DriverV3.java`       | Most optimized driver. Change detection with hash comparison -- only sends packets for actually modified lines.                                       |
| `part/Line.java`                     | Dynamic scoreboard line. Resolves placeholders on each refresh tick and caches the result.                                                            |
| `part/StaticLine.java`               | Static scoreboard line. Resolved once on creation, never re-evaluated.                                                                                |

---

## gangland-ui/inventory-api

**Purpose:** Custom inventory/GUI framework with YAML-driven layouts, conditional slots, multi-page navigation, and a
comprehensive event handling system.

**Java Files:** 37 | **Package:** `me.luckyraven.inventory.*`

### Root Classes

| Class                   | Description                                                                                                                                              |
|-------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `InventoryHandler.java` | Core inventory manager. Manages slot callbacks, size normalization (multiples of 9), item placement, and event routing.                                  |
| `InventoryBuilder.java` | Fluent API for constructing inventories from YAML. Supports placeholder resolution, conditional slot evaluation, fill patterns, and slot action binding. |
| `InventoryData.java`    | Inventory metadata (title, size, fill item, slots map).                                                                                                  |
| `InventoryOpener.java`  | Opens a built inventory for a player with optional state injection.                                                                                      |
| `OpenInventory.java`    | Tracks a currently open inventory for a player (handler reference, page state).                                                                          |
| `State.java`            | Key-value state container passed to inventory handlers for dynamic content.                                                                              |

### Subpackage: `condition/`

Conditional slot visibility system.

| Class                             | Description                                                                                         |
|-----------------------------------|-----------------------------------------------------------------------------------------------------|
| `BooleanExpressionEvaluator.java` | Evaluates boolean expressions (AND, OR, NOT) for conditional slot visibility.                       |
| `ConditionEvaluator.java`         | Evaluates individual conditions against player state (permissions, gang membership, balance, etc.). |
| `ConditionalSlotData.java`        | Data model for a slot with visibility conditions and alternate items.                               |
| `SlotCondition.java`              | Single condition definition (type, operator, value).                                                |

### Subpackage: `handler/`

Slot event handler interfaces and implementations.

| Class                             | Description                                                                         |
|-----------------------------------|-------------------------------------------------------------------------------------|
| `SlotEventHandler.java`           | Base interface for slot event handlers.                                             |
| `SlotContext.java`                | Context object passed to slot handlers (player, slot, click type, inventory state). |
| `SlotItemFactory.java`            | Functional interface for dynamic item generation per slot.                          |
| `ClickSlotHandler.java`           | Handler for inventory click events on a specific slot.                              |
| `CloseSlotHandler.java`           | Handler for inventory close events.                                                 |
| `DropSlotHandler.java`            | Handler for item drop events from a slot.                                           |
| `SwapHandSlotHandler.java`        | Handler for off-hand swap events.                                                   |
| `JoinSlotHandler.java`            | Handler for player join events (loads persistent inventory state).                  |
| `QuitSlotHandler.java`            | Handler for player quit events (saves inventory state).                             |
| `PlayerInteractSlotHandler.java`  | Handler for player interact events while holding an inventory item.                 |
| `AbstractCommandSlotHandler.java` | Base handler that executes a command when a slot is clicked.                        |

### Subpackage: `listener/`

Bukkit event listeners for inventory interaction.

| Class                         | Description                                                                                   |
|-------------------------------|-----------------------------------------------------------------------------------------------|
| `InventoryClickHandler.java`  | Routes `InventoryClickEvent` to the appropriate slot handler. Prevents default item movement. |
| `InventoryCloseHandler.java`  | Routes `InventoryCloseEvent` to close handlers. Cleans up open inventory tracking.            |
| `InventoryDragHandler.java`   | Cancels drag events in custom inventories.                                                    |
| `PlayerInventoryCleanup.java` | Cleans up inventory tracking data on player quit.                                             |

### Subpackage: `multi/`

Multi-page inventory support.

| Class                           | Description                                                                                               |
|---------------------------------|-----------------------------------------------------------------------------------------------------------|
| `MultiInventory.java`           | Multi-page inventory container. Manages page list and current page index.                                 |
| `MultiInventoryCreation.java`   | Creates paginated inventories from a data source with configurable items-per-page and navigation buttons. |
| `MultiInventoryNavigation.java` | Handles next/previous page navigation button clicks.                                                      |
| `ItemSourceProvider.java`       | Interface for providing dynamic item lists for paginated inventories.                                     |

### Subpackage: `part/`

Inventory building blocks.

| Class                        | Description                                                                |
|------------------------------|----------------------------------------------------------------------------|
| `Slot.java`                  | Slot definition with position, item, click action, and conditions.         |
| `Fill.java`                  | Fill pattern for empty slots (material, name).                             |
| `ButtonTags.java`            | Constants for navigation button identifiers (NEXT_PAGE, PREV_PAGE, CLOSE). |
| `PageConfig.java`            | Page configuration (items per page, navigation slot positions).            |
| `ConditionalSlotResult.java` | Result of evaluating a conditional slot (visible/hidden, resolved item).   |

### Subpackage: `service/`

| Class                    | Description                                                                                  |
|--------------------------|----------------------------------------------------------------------------------------------|
| `InventoryRegistry.java` | Global registry of all custom inventories. Maps inventory IDs to handlers for event routing. |

### Subpackage: `unique/`

| Class                    | Description                                                               |
|--------------------------|---------------------------------------------------------------------------|
| `UniqueItemHandler.java` | Handles unique item interaction events (right-click to open phone, etc.). |

### Subpackage: `util/`

| Class                | Description                                                                             |
|----------------------|-----------------------------------------------------------------------------------------|
| `InventoryUtil.java` | Utility methods for inventory manipulation (find empty slot, check space, count items). |

---

## gangland-ui/sign-api

**Purpose:** Sign interaction framework using the Chain of Responsibility pattern. Supports composable sign behaviors
via aspects, sign parsing, format registries, bulk operations, and validation.

**Java Files:** 28 | **Package:** `me.luckyraven.sign.*`

### Root Classes

| Class              | Description                                                                                        |
|--------------------|----------------------------------------------------------------------------------------------------|
| `SignService.java` | High-level sign management. Coordinates sign creation, interaction dispatch, and registry lookups. |
| `SignType.java`    | Enum or interface defining sign type identifiers.                                                  |

### Subpackage: `handler/`

| Class                         | Description                                                                                                                       |
|-------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| `SignHandler.java`            | Interface for sign interaction handlers.                                                                                          |
| `AspectBasedSignHandler.java` | Chain of Responsibility implementation. Chains `SignAspect` instances, executing each in order until one handles the interaction. |

### Subpackage: `aspect/`

| Class               | Description                                                                                                       |
|---------------------|-------------------------------------------------------------------------------------------------------------------|
| `SignAspect.java`   | Interface for composable sign behaviors. Each aspect handles one concern (e.g., money check, item transfer).      |
| `AspectResult.java` | Result of aspect execution: `CONTINUE` (pass to next aspect), `HANDLED` (stop chain), `DENIED` (stop with error). |

### Subpackage: `model/`

| Class                 | Description                                                          |
|-----------------------|----------------------------------------------------------------------|
| `ParsedSign.java`     | Interface for a parsed sign's data.                                  |
| `BaseParsedSign.java` | Base implementation with common fields (sign type, location, lines). |
| `SignFormat.java`     | Defines the expected format of a sign type (line patterns, colors).  |
| `SignLineFormat.java` | Format specification for a single sign line.                         |

### Subpackage: `parser/`

| Class                     | Description                                                   |
|---------------------------|---------------------------------------------------------------|
| `SignParser.java`         | Interface for parsing sign text into `ParsedSign` objects.    |
| `AbstractSignParser.java` | Base parser with common line extraction and validation logic. |

### Subpackage: `registry/`

| Class                     | Description                                                            |
|---------------------------|------------------------------------------------------------------------|
| `SignTypeRegistry.java`   | Maps sign type identifiers to their handlers, parsers, and formats.    |
| `SignFormatRegistry.java` | Registry of sign format definitions for sign creation validation.      |
| `SignTypeDefinition.java` | Bundles a sign type's handler, parser, format, and validator together. |

### Subpackage: `service/`

| Class                         | Description                                                               |
|-------------------------------|---------------------------------------------------------------------------|
| `SignFormatterService.java`   | Formats sign lines with colors and placeholders on placement.             |
| `SignInformation.java`        | Interface for providing sign metadata (display name, description).        |
| `SignInteraction.java`        | Represents a player's interaction with a sign (player, sign, click type). |
| `SignInteractionService.java` | Routes sign interactions to the correct handler based on sign type.       |

### Subpackage: `bulk/`

Bulk sign operations.

| Class                    | Description                                                              |
|--------------------------|--------------------------------------------------------------------------|
| `BulkActionManager.java` | Manages pending bulk sign operations (e.g., update all signs of a type). |
| `BulkActionPreview.java` | Preview of a bulk action before execution.                               |
| `BulkSignHandler.java`   | Executes bulk sign updates across all loaded chunks.                     |
| `PendingBulkAction.java` | Data model for a queued bulk action.                                     |

### Subpackage: `listener/`

| Class                     | Description                                                                            |
|---------------------------|----------------------------------------------------------------------------------------|
| `PlayerSignInteract.java` | Listens for `PlayerInteractEvent` on signs and dispatches to `SignInteractionService`. |
| `SignCreation.java`       | Listens for `SignChangeEvent` and validates/formats new signs.                         |

### Subpackage: `validation/`

| Class                          | Description                                                                      |
|--------------------------------|----------------------------------------------------------------------------------|
| `SignValidator.java`           | Interface for sign input validation.                                             |
| `AbstractSignValidator.java`   | Base validator with common validation logic (line count, format matching).       |
| `SignValidationException.java` | Exception thrown when sign validation fails, with a player-facing error message. |

---

## gangland-ui/lootchest-api

**Purpose:** Loot chest system with cracking mini-game, cooldown management, hologram integration, tiered loot tables,
and a comprehensive event lifecycle.

**Java Files:** 33 | **Package:** `me.luckyraven.lootchest.*`

### Root Classes

| Class                       | Description                                                                                                                                                                                |
|-----------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `LootChestService.java`     | Abstract service managing the loot chest lifecycle: handler registration, cooldown tracking, hologram display, cracking session orchestration. Concrete implementation in `gangland-impl`. |
| `ChestCooldownManager.java` | Tracks per-chest cooldown timers. Prevents re-opening chests before cooldown expires.                                                                                                      |

### Subpackage: `config/`

| Class                            | Description                                                                                     |
|----------------------------------|-------------------------------------------------------------------------------------------------|
| `LootChestConfig.java`           | Top-level loot chest configuration container.                                                   |
| `LootChestLoader.java`           | Loads loot chest definitions from YAML.                                                         |
| `LootChestMessagesProvider.java` | Interface contract for loot chest feedback messages.                                            |
| `LootChestSettingsProvider.java` | Interface contract for loot chest settings (cooldown duration, cracking time, hologram format). |

### Subpackage: `data/`

| Class                   | Description                                                                  |
|-------------------------|------------------------------------------------------------------------------|
| `LootChestData.java`    | Persistent loot chest data (location, tier, cooldown duration, last opened). |
| `LootChestSession.java` | Active session for a player interacting with a loot chest.                   |
| `CrackingSession.java`  | Active cracking mini-game session (progress, time remaining, player).        |
| `LootTable.java`        | Collection of loot items with rarity weights for random selection.           |
| `LootTier.java`         | Tier definition (name, rarity, item pool, min/max items).                    |

### Subpackage: `handler/`

Handler chain for loot chest lifecycle events.

| Class                                         | Description                                               |
|-----------------------------------------------|-----------------------------------------------------------|
| `LootChestHandler.java`                       | Base handler interface.                                   |
| `cracking/CrackingStartHandler.java`          | Handles cracking session initiation.                      |
| `cracking/CrackingTickHandler.java`           | Handles per-tick cracking progress updates.               |
| `cracking/CrackingSuccessHandler.java`        | Handles successful cracking completion.                   |
| `cracking/CrackingFailedHandler.java`         | Handles cracking failure (movement, damage, timeout).     |
| `lootchest/SessionStartHandler.java`          | Handles loot chest session start (opens chest inventory). |
| `lootchest/SessionCompleteHandler.java`       | Handles session completion (chest closing).               |
| `lootchest/ChestCooldownTickHandler.java`     | Handles per-tick cooldown updates for hologram display.   |
| `lootchest/ChestCooldownCompleteHandler.java` | Handles cooldown expiration (re-enables chest).           |

### Subpackage: `item/`

| Class                    | Description                                                                |
|--------------------------|----------------------------------------------------------------------------|
| `LootItemProvider.java`  | Interface for resolving loot item references to `ItemStack` instances.     |
| `LootItemReference.java` | Reference to a loot item (type prefix + identifier, e.g., `weapon:rifle`). |

### Subpackage: `events/`

Custom events (8 total + 1 base).

| Event                                           | Description                                             |
|-------------------------------------------------|---------------------------------------------------------|
| `LootChestEvent.java`                           | Base event with player and chest data references.       |
| `cracking/LootChestCrackingStartEvent.java`     | Fired when cracking begins.                             |
| `cracking/LootChestDuringCrackingEvent.java`    | Fired each tick during cracking.                        |
| `cracking/LootChestCrackingSuccessEvent.java`   | Fired on successful crack.                              |
| `cracking/LootChestCrackingFailureEvent.java`   | Fired on failed crack.                                  |
| `cracking/LootChestCrackingEndEvent.java`       | Fired when cracking ends (success or failure).          |
| `lootchest/LootChestOpenEvent.java`             | Fired when a loot chest is opened.                      |
| `lootchest/LootChestCloseEvent.java`            | Fired when a loot chest is closed.                      |
| `lootchest/LootChestCooldownCompleteEvent.java` | Fired when a chest's cooldown expires.                  |
| `lootchest/LootChestDuringCooldownEvent.java`   | Fired each tick during cooldown (for hologram updates). |

### Subpackage: `listener/`

| Class                    | Description                                                            |
|--------------------------|------------------------------------------------------------------------|
| `LootChestListener.java` | Bukkit event listener routing chest interactions to the handler chain. |

---

## gangland-ui/hologram-api

**Purpose:** Minimal hologram display system for floating text above loot chests, waypoints, and other world markers.

**Java Files:** 3 | **Package:** `me.luckyraven.hologram.*`

| Class                             | Description                                                                                                     |
|-----------------------------------|-----------------------------------------------------------------------------------------------------------------|
| `HologramService.java`            | Creates, updates, and removes holograms at world locations. Manages hologram entity lifecycle and text updates. |
| `Hologram.java`                   | Hologram data model (location, lines, visibility range). Wraps armor stand entities with invisible bodies.      |
| `HologramProtectionListener.java` | Prevents players from interacting with or damaging hologram armor stand entities.                               |

---

## gangland-compatibility

**Purpose:** NMS (Net Minecraft Server) compatibility layer. Provides version-specific implementations behind abstract
interfaces, allowing the plugin to run on Minecraft 1.10 through 1.21.

### version-impl (Interface Module)

**Java Files:** 6 | **Package:** `me.luckyraven.compatibility.*`

| Class                             | Description                                                                            |
|-----------------------------------|----------------------------------------------------------------------------------------|
| `Compatibility.java`              | Interface defining all version-specific operations that the plugin needs.              |
| `CompatibilitySetup.java`         | Initializes the correct compatibility implementation based on detected server version. |
| `CompatibilityWorker.java`        | Executes compatibility operations with error handling.                                 |
| `Version.java`                    | Enum of all supported Minecraft versions with their NMS package identifiers.           |
| `VersionSetup.java`               | Detects the running server version at load time by inspecting the server package name. |
| `recoil/RecoilCompatibility.java` | Interface for version-specific camera recoil packet manipulation.                      |

### Version Modules (27 modules)

Each version module contains exactly 2 Java files:

| Module Pattern    | Classes              | Description                                                                 |
|-------------------|----------------------|-----------------------------------------------------------------------------|
| `version-X_Y_RZ/` | `vX_Y_RZ.java`       | Implements `Compatibility` for that NMS version.                            |
|                   | `Recoil_X_Y_RZ.java` | Implements `RecoilCompatibility` with version-specific packet construction. |

**Supported Versions:**

| Module            | Minecraft Version |
|-------------------|-------------------|
| `version-1_10_R1` | 1.10.x            |
| `version-1_11_R1` | 1.11.x            |
| `version-1_12_R1` | 1.12.x            |
| `version-1_13_R1` | 1.13              |
| `version-1_13_R2` | 1.13.1 - 1.13.2   |
| `version-1_14_R1` | 1.14.x            |
| `version-1_15_R1` | 1.15.x            |
| `version-1_16_R1` | 1.16.1            |
| `version-1_16_R2` | 1.16.2 - 1.16.3   |
| `version-1_16_R3` | 1.16.4 - 1.16.5   |
| `version-1_17_R1` | 1.17.x            |
| `version-1_18_R1` | 1.18 - 1.18.1     |
| `version-1_18_R2` | 1.18.2            |
| `version-1_19_R1` | 1.19 - 1.19.2     |
| `version-1_19_R2` | 1.19.3            |
| `version-1_19_R3` | 1.19.4            |
| `version-1_20_R1` | 1.20 - 1.20.1     |
| `version-1_20_R2` | 1.20.2            |
| `version-1_20_R3` | 1.20.3 - 1.20.4   |
| `version-1_20_R4` | 1.20.5 - 1.20.6   |
| `version-1_21_R1` | 1.21              |
| `version-1_21_R2` | 1.21.1            |
| `version-1_21_R3` | 1.21.2 - 1.21.3   |
| `version-1_21_R4` | 1.21.4            |
| `version-1_21_R5` | 1.21.5            |
| `version-1_21_R6` | 1.21.6            |
| `version-1_21_R7` | 1.21.7            |

---

## Module Dependency Graph

```
gangland-build (shade assembly)
  +-- gangland-impl (main plugin)
  |     +-- gangland-core
  |     +-- gangland-item
  |     +-- plugin-persistence
  |     +-- plugin-common
  |     +-- cops-n-crooks
  |     +-- gangland-weapon
  |     +-- gangland-gadget
  |     +-- scoreboard-api
  |     +-- inventory-api
  |     +-- sign-api
  |     +-- lootchest-api
  |     +-- hologram-api
  |     +-- version-impl
  |     +-- version-1_10_R1 ... version-1_21_R7
  |
  +-- gangland-core (no plugin dependencies)
  |     +-- plugin-common
  |
  +-- gangland-item (no plugin dependencies)
  |     +-- plugin-common
  |     +-- gangland-core
  |
  +-- plugin-persistence
  |     +-- plugin-common
  |
  +-- cops-n-crooks
  |     +-- gangland-core
  |     +-- gangland-weapon
  |     +-- plugin-common
  |
  +-- gangland-weapon
  |     +-- gangland-core
  |     +-- gangland-item
  |     +-- plugin-common
  |     +-- version-impl
  |
  +-- gangland-gadget
  |     +-- gangland-core
  |     +-- gangland-item
  |     +-- gangland-weapon
  |     +-- plugin-common
  |
  +-- UI modules (scoreboard, inventory, sign, lootchest, hologram)
  |     +-- gangland-core
  |     +-- plugin-common
  |
  +-- version-impl (no plugin dependencies)
        +-- plugin-common
```

---

## Statistics Summary

| Module                | Java Files | Packages | Key Concern                                                             |
|-----------------------|------------|----------|-------------------------------------------------------------------------|
| `gangland-impl`       | 319        | 40+      | Main plugin: commands, listeners, managers, repositories, configuration |
| `gangland-build`      | 0          | 0        | Shade assembly                                                          |
| `gangland-core`       | 47         | 12       | DI container, utilities, frameworks                                     |
| `gangland-item`       | 13         | 4        | Item parsing, fuel, unique items, wearables                             |
| `plugin-persistence`  | 20         | 5        | Repository pattern, database abstraction                                |
| `plugin-common`       | 2          | 2        | Logger, exceptions                                                      |
| `cops-n-crooks`       | 91         | 20       | NPC cops/civilians, wanted, bounty, jails                               |
| `gangland-weapon`     | 83         | 17       | Weapons, projectiles, modifiers, reload                                 |
| `gangland-gadget`     | 47         | 14       | Vehicles, jetpacks, fuel, repair                                        |
| `scoreboard-api`      | 8          | 3        | FastBoard scoreboards                                                   |
| `inventory-api`       | 37         | 8        | Custom GUI framework                                                    |
| `sign-api`            | 28         | 8        | Sign interaction framework                                              |
| `lootchest-api`       | 33         | 7        | Loot chest system                                                       |
| `hologram-api`        | 3          | 1        | Floating text holograms                                                 |
| `version-impl`        | 6          | 2        | Compatibility interfaces                                                |
| Version modules (x27) | 54         | 27       | NMS adapters                                                            |
| **Total**             | **~791**   | **~170** |                                                                         |
