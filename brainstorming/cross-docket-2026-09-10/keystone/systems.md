# Keystone system map — 2026-09-10 (HEAD 61621a8, branch phase-h8-item-npc)

| Code | Slug | Name | Modules / packages | Files | Hubs (god nodes / entry points) | Risk hotspots |
|---|---|---|---|---|---|---|
| BN | bean-container | Bean container & DI | keystone-bean: org/luckyraven/keystone/bean/** | 23 | BeanFactory, BeanGraph, DependencyContainer, Bean, Configuration | Reflection scans, phase ordering, reload cycles, circular dependency detection |
| CM | command-framework | Command & argument system | keystone-command: org/luckyraven/keystone/command/** | 19 | CommandManager, Argument, Tree, TargetSelector, Command | Argument tree parsing, tab completion Brigadier integration, dispatch routing, recursive tree traversal |
| IT | item-framework | Item builder & converters | keystone-item: org/luckyraven/keystone/item/** | 20 | ItemBuilder, ItemConverterRegistry | NBT serialization, type:modifier{k=v} parsing, converter chaining, reflective NBT seam |
| CF | config-file | Config parsing & file management | keystone-persistence: config/** + core/** | 26 | FileManager, FileHandler, ConfigSerializer | File I/O, YAML parsing, encoding/normalization, config fallback chains, initialization recovery |
| DB | database-repos | Database backends & repositories | keystone-persistence: database/** + repository/** + audit/** | 37 | DatabaseHandler, Table, DatabaseBackend, SqliteBackend, AbstractRepository, RepositoryRegistry | SQL generation, transaction handling, HikariCP pooling, schema diffing, batch upsert logic, connection fallback |
| MO | module-loader | Runtime module system | keystone-module: org/luckyraven/keystone/module/** | 17 | ModuleLoader, KeystoneModule, ModuleRegistrar | Classloader isolation, artifact resolution, descriptor parsing, module versioning, update service |
| NP | npc-system | NPC base & navigation | keystone-npc: org/luckyraven/keystone/npc/** | 19 | AbstractNpc, NpcNavigationDelegate, EntitySpawner, NpcDifficulty | Entity spawning persistence, navigation state machines, combat delegate dispatch, Citizens soft-dependency |
| HK | hooks-integration | External plugin hooks | keystone-hooks: org/luckyraven/keystone/{vault,papi,economy}/** | 17 | VaultEconomyProvider, PlaceholderAPIProvider, Bank, EconomyHandler | Plugin availability checks, Vault economy bridge, PlaceholderAPI event callbacks, currency conversions |
| UC | utils-helpers | Utilities, data structures & test fixtures | keystone-common: {util,datastructure,color,meta,permission,logging,message,sound,exception}/** + keystone-testkit | 49 | (various utility classes, TestEntity, MockPluginFactory) | Wide reuse surface, shared dependencies, test fixture setup/teardown, mock plugin lifecycle |
| SC | scheduling-cooldowns | Timers, cooldowns & updates | keystone-common: {timer,cooldown,update}/** + keystone-persistence: cooldown/** | 14 | Timer, Cooldown, UpdateService | Async timer scheduling, cooldown expiration tracking, version checking, timer cancellation on reload |
| DG | diagnostics-error | Diagnostics, error handling & NMS | keystone-common: {diagnostics,result,nms,nms/internal,nms/input}/** + keystone-persistence: message/** | 27 | ConfigReport (108 edges), Fault (79 edges), Diagnostics, Result (50 edges), SourceLocation (55 edges), NmsVersion, NmsCache | Error categorization via Fault, diagnostic pipeline collection, packet handling, NMS reflection-first detection, version branching |
| PH | placeholders | Placeholder templates & providers | keystone-common: placeholder/** | 12 | PlaceholderRegistry, PlaceholderEffect, PlaceholderProvider, PlaceholderReplacer | Template parsing with recursive expansions, provider chain resolution, effect application order, nested placeholder cycles |

## Package -> system (every main package once)

### keystone-bean
- org/luckyraven/keystone/bean -> BN
- org/luckyraven/keystone/bean/autowire -> BN
- org/luckyraven/keystone/bean/command -> BN
- org/luckyraven/keystone/bean/listener -> BN
- org/luckyraven/keystone/bean/conditional -> BN

### keystone-command
- org/luckyraven/keystone/command -> CM
- org/luckyraven/keystone/command/argument -> CM
- org/luckyraven/keystone/command/brigadier -> CM

### keystone-common
- org/luckyraven/keystone/util -> UC
- org/luckyraven/keystone/util/messages -> UC
- org/luckyraven/keystone/datastructure -> UC
- org/luckyraven/keystone/color -> UC
- org/luckyraven/keystone/meta -> UC
- org/luckyraven/keystone/permission -> UC
- org/luckyraven/keystone/logging -> UC
- org/luckyraven/keystone/message -> UC
- org/luckyraven/keystone/sound -> UC
- org/luckyraven/keystone/exception -> UC
- org/luckyraven/keystone/timer -> SC
- org/luckyraven/keystone/cooldown -> SC
- org/luckyraven/keystone/update -> SC
- org/luckyraven/keystone/diagnostics -> DG
- org/luckyraven/keystone/result -> DG
- org/luckyraven/keystone/nms -> DG
- org/luckyraven/keystone/nms/internal -> DG
- org/luckyraven/keystone/nms/input -> DG
- org/luckyraven/keystone/placeholder -> PH
- org/luckyraven/keystone/placeholder/effect -> PH
- org/luckyraven/keystone/placeholder/provider -> PH
- org/luckyraven/keystone/placeholder/replacer -> PH

### keystone-hooks
- org/luckyraven/keystone/vault/permission -> HK
- org/luckyraven/keystone/vault/economy -> HK
- org/luckyraven/keystone/papi -> HK
- org/luckyraven/keystone/economy -> HK
- org/luckyraven/keystone/economy/bank -> HK
- org/luckyraven/keystone/economy/exception -> HK

### keystone-item
- org/luckyraven/keystone/item -> IT
- org/luckyraven/keystone/item/nbt -> IT
- org/luckyraven/keystone/item/spi -> IT

### keystone-module
- org/luckyraven/keystone/module -> MO
- org/luckyraven/keystone/module/artifact -> MO
- org/luckyraven/keystone/module/update -> MO

### keystone-npc
- org/luckyraven/keystone/npc -> NP
- org/luckyraven/keystone/npc/entity -> NP
- org/luckyraven/keystone/npc/spi -> NP
- org/luckyraven/keystone/npc/event -> NP

### keystone-persistence
- org/luckyraven/keystone/persistence -> (split: core 5 files to CF)
- org/luckyraven/keystone/persistence/config -> CF
- org/luckyraven/keystone/persistence/database -> DB
- org/luckyraven/keystone/persistence/repository -> DB
- org/luckyraven/keystone/persistence/audit -> DB
- org/luckyraven/keystone/persistence/cooldown -> SC
- org/luckyraven/keystone/persistence/message -> DG

### keystone-testkit
- org/luckyraven/keystone/testkit -> UC

## Scanner briefing per system

### BN bean-container
**What it does:**
Spring-style dependency injection container with phased bean lifecycle (KERNEL → FILE → DATABASE → CONFIG → LIFECYCLE → LISTENER → COMMAND). Automatic constructor injection resolver, topological sorting of `@Bean` methods, listener/command registration hooks, reload and shutdown pipelines.

**Entry points to start from:**
- BeanFactory.scan(basePackage) — bootstrap entry
- BeanFactory.instantiate() — phase execution
- DependencyContainer — service registry
- BeanGraph — topological sort engine

**Where money/items/persistence/threading/permissions live:**
- Bean lifecycle manages all consumer initialization; reload coordination happens here
- No domain logic; pure infrastructure

**Config/YAML files it reads:**
- None directly; config reading delegated to CF (Config & File Management)

**Tests that exist for it:**
BeanFactoryTest, BeanFactoryRegisterConfigurationTest, BeanFactoryMissingTypeTest, BeanLifecyclePhaseOrderTest, ConditionalBeanTest, ListenerServiceTest, CommandServiceTest, ReloadServiceTest, BeanGraphTest, DependencyContainerTest, (11 tests total)

---

### CM command-framework
**What it does:**
Command dispatch framework: `CommandManager` routes `/glw <subcommand>` → argument tree matching. `Argument` subtypes (`SubArgument`, `OptionalArgument`, etc.) form recursive trees; dispatchers resolve player/sender and argument values. Brigadier/Commodore client-side tab completion hooks. Command help rendering through `commands.json` manifest.

**Entry points to start from:**
- CommandManager.dispatch(sender, command, args) — main entry
- Argument tree root navigation
- TabCompleter for client-side completion
- SubCommand resolver

**Where money/items/persistence/threading/permissions live:**
- No domain logic; command argument resolution only

**Config/YAML files it reads:**
- commands.json (consumed, not parsed here; that's in CF)

**Tests that exist for it:**
ArgumentSpecifierTest, ArgumentMessagesTest, ArgumentLockTest, CommandManagerTest, TabCompleterTest, BrigadierCompletionTest, CommandHelpTest, TargetSelectorTest, TreeNavigationTest, OptionalArgumentTest, (14 tests total)

---

### IT item-framework
**What it does:**
Item builder pattern (`ItemBuilder`), converter/serializer/refresher registries, type:modifier{k=v} parsing, optional NBT seam for reflective lore/tag handling. Integrated into config parser (CF) via `ItemDslAdapter`.

**Entry points to start from:**
- ItemBuilder.of(material) — builder entry
- ItemConverterRegistry.get(type) — converter lookup
- ItemRefresherRegistry.register(type, refresher) — refresher installation
- ItemDefinitions — static template registry

**Where money/items/persistence/threading/permissions live:**
- Item data lives here; NBT reflection is in item.nbt SPI

**Config/YAML files it reads:**
- type:modifier parsing (YAML read by CF, interpreted by IT)

**Tests that exist for it:**
ItemBuilderTest, ItemBuilderCloneTest, ItemBuilderMaxStackSizeTest, ItemBuilderDegradedTest, ItemConverterRegistryTest, ItemRefresherTest, ItemDefinitionsTest, ItemDslAdapterTest, NbtBridgeTest, ItemSerializationTest, (17 tests total)

---

### CF config-file
**What it does:**
YAML config parsing (FileConfiguration, LanguageLoader), file family initialization (copies default YAML from jar to disk, handles encoding/normalization), recovery on init failure (regenerate-from-jar + retry), `ItemDslAdapter` bridges item vocabulary into config loading.

**Entry points to start from:**
- FileManager.initializeAll() — bootstrap all YAML files
- FileHandler.writeDefaults() — copy jar → disk
- ConfigSerializer.read(file) — parse a config
- LanguageLoader.loadAllLanguages() — multi-language loading

**Where money/items/persistence/threading/permissions live:**
- File I/O, YAML parsing; no domain logic

**Config/YAML files it reads:**
- All .yml files in data folder (settings.yml, language files, module defaults, etc.)

**Tests that exist for it:**
ConfigParserTest, FileManagerTest, FileHandlerTest, FileInitializerTest, LanguageLoaderTest, ConfigRecoveryTest, YamlEncodingTest, ItemDslAdapterTest, (8-12 tests estimated in persistence suite)

---

### DB database-repos
**What it does:**
DatabaseBackend SPI (SqliteBackend, MysqlBackend, legacy Database/SQLite/MySQL stubs), table schema management (Table, Attribute, diff engine), JDBC query generation via TableBackend, HikariCP connection pooling, AbstractRepository CRUD, RepositoryRegistry scanner, batch upsert/delete, audit logging.

**Entry points to start from:**
- DatabaseHandler.connect(type) → backend resolution
- DatabaseBackend.upsertAll(table, rows) — batch writes
- TableBackend for query generation
- AbstractRepository.saveAll() — consumer CRUD

**Where money/items/persistence/threading/permissions live:**
- All persistence (gangs, members, users, bounties, etc.) routes through here

**Config/YAML files it reads:**
- Database configuration (host, port, database name) from CF

**Tests that exist for it:**
DatabaseHandlerTest, SqliteBackendTest, MysqlBackendTest, TableSchemaTest, AttributeTest, TableBackendTest, RepositoryRegistryTest, AbstractRepositoryTest, BatchUpsertSqliteTest, BatchUpsertMysqlTest, AuditLogServiceSqliteTest, ConnectionPoolTest, ForeignKeyTest, UniqueConstraintTest, TransactionTest, (35-45 tests total)

---

### MO module-loader
**What it does:**
Runtime module loading: reads `module.yml` descriptors, checks `Host_Api`/`Depends`/`Plugins` keys, parent-first `ModuleClassLoader`, JDK-only Maven artifact resolver (no Gradle/SBT), `ModuleUpdateService` checks for newer .jar versions online.

**Entry points to start from:**
- ModuleLoader.load(folder) — bootstrap all .jar files
- ModuleDescriptor parsing from module.yml
- ArtifactResolver.resolve(groupId, artifactId, version) — Maven artifact download
- ModuleUpdateService.checkUpdates() — version polling

**Where money/items/persistence/threading/permissions live:**
- Module lifecycle management; no domain logic

**Config/YAML files it reads:**
- module.yml (one per .jar in plugins/Gangland_Warfare/modules/)

**Tests that exist for it:**
ModuleLoaderTest, ModuleDescriptorTest, ArtifactCoordinateTest, ArtifactResolverTest, MavenMetadataTest, ModuleUpdateServiceTest, ModuleClassLoaderTest, (8 tests total)

---

### NP npc-system
**What it does:**
Citizens-backed NPC base: `AbstractNpc` wraps a Citizens NPC and manages entity lifecycle. `NpcNavigationDelegate` handles pathfinding and movement state. `NpcCombatDelegate` handles attack cadence and target selection. `EntitySpawner` provisions new Citizens entities. `NpcSupport` presence check (Citizens soft-dependency). Persisted entity marks via `NpcMarkManager`. SPI (`NpcRangedAttack`, `NpcTargetFilter`, `NpcMarkDefaults`) for consumer-specific combat/marking logic.

**Entry points to start from:**
- AbstractNpc.spawn(location, type) — create a new NPC
- NpcNavigationDelegate.navigateTo(target) — movement dispatch
- EntitySpawner for Citizens entity setup
- NpcMarkManager for mark persistence

**Where money/items/persistence/threading/permissions live:**
- NPC entity state and mark persistence; Citizens integration point

**Config/YAML files it reads:**
- NPC spawn config (moved to consumer modules in 0.9.0; npc/cops.yml, npc/civilians.yml in cops-n-crooks/civilians/turf modules)

**Tests that exist for it:**
AbstractNpcDestroyTest, EntitySpawnerTest, NpcCombatDelegateTest, NpcNavigationTest, NpcMarkManagerTest, (6 tests total)

---

### HK hooks-integration
**What it does:**
Vault permission and economy hooks, PlaceholderAPI provider registration, generic `EconomyHandler` abstraction and `Bank` interface, `Currency` record for multi-currency support, Vault economy bridge via `VaultEconomyProvider`.

**Entry points to start from:**
- VaultEconomyProvider.getBalance(player) — economy bridge
- PlaceholderAPIProvider.register(PlaceholderExpansion) — template hook
- Bank.withdraw(player, amount) — banking operation
- EconomyHandler for currency conversions

**Where money/items/persistence/threading/permissions live:**
- All Vault/economy integration; permission checks in consumers only

**Config/YAML files it reads:**
- Economy config (from CF)

**Tests that exist for it:**
BankTest, CurrencyTest, VaultPermissionTest, EconomyHandlerTest, PlaceholderProviderTest, VaultBridgeTest, MultiCurrencyTest, (9 tests total)

---

### UC utils-helpers
**What it does:**
Generic utilities: ChatUtil (color/formatting), Result/Success/Failure wrappers, data structures (DoublyLinkedNode, LRU cache, etc.), color enum, metadata tagging system, permission checking, logging wrapper (redirects to `@CustomLog` logger), sound effect references. `MockPluginFactory`, `MockEntity`, `TestEntity` fixtures for consumer unit tests.

**Entry points to start from:**
- ChatUtil.color(text) — color code replacement
- Result.success(value)/failure(error) — error handling
- CustomLog annotation — logging setup
- MockPluginFactory for test setup

**Where money/items/persistence/threading/permissions live:**
- Logging only; no domain logic

**Config/YAML files it reads:**
- None directly

**Tests that exist for it:**
ChatUtilTest, ResultTest, ColorEnumTest, DataStructureTest, PermissionTest, LoggingTest, MockPluginFactoryTest, (10-15 tests in keystone-common suite)

---

### SC scheduling-cooldowns
**What it does:**
`Timer` class for async/sync repeating tasks (start(async), cancel(), isRunning()). `Cooldown` tracker for player/entity action gating (on cooldown, remaining time, reset). `UpdateService` for version checking and download-on-demand. Integration with reload pipeline (timers cancel on reload, cooldowns persist).

**Entry points to start from:**
- Timer.start(async) — task scheduling
- Cooldown.on(player) — check/set cooldown
- UpdateService.checkForUpdates() — version poll

**Where money/items/persistence/threading/permissions live:**
- Threading (async timers); no domain state

**Config/YAML files it reads:**
- Update check config (from CF)

**Tests that exist for it:**
TimerTest, CooldownTest, UpdateServiceTest, AsyncTimerTest, ReloadTimerCancellationTest, (5-7 tests in keystone-common suite)

---

### DG diagnostics-error
**What it does:**
`Diagnostics` hub for collecting and reporting faults. `Fault` enum categorizes errors (DATABASE_CONNECTION, NMS_REFLECTION, CONFIG_PARSE, etc.) with configurable severity. `ConfigReport` aggregates diagnostics into a server-wide status report. `Result<T>` wrapper for error flows. `SourceLocation` for code attribution. NMS detection: `NmsVersion`, `NmsCache` (reflection-first, no per-version modules), packet adapter layer (outbound `PacketAdapter`/`PacketBridge`, inbound `PlayerInputInterceptor`).

**Entry points to start from:**
- Diagnostics.fault(Fault.CODE_KEY, msg) — record a fault
- ConfigReport.generate() — server status report
- NmsVersion.current() — detect server version
- PacketAdapter for NMS packet handling

**Where money/items/persistence/threading/permissions live:**
- Error tracking; NMS packet handling (threading context critical)

**Config/YAML files it reads:**
- Diagnostic severity levels (from CF)

**Tests that exist for it:**
DiagnosticsTest, ConfigReportTest, FaultEnumTest, NmsVersionTest, PacketAdapterTest, CameraRotationPacketsTest, ClassifierChainTest, (10-15 tests in keystone-common suite)

---

### PH placeholders
**What it does:**
Template placeholder system: `PlaceholderRegistry` holds providers (PlaceholderProvider, yielding custom string values). `PlaceholderReplacer` scans text for `%placeholder%` tokens and recursively expands them. `PlaceholderEffect` modifies the expansion (uppercase, truncate, etc.). Integrated with PlaceholderAPI for community expansion support.

**Entry points to start from:**
- PlaceholderRegistry.register(placeholder, provider) — hook registration
- PlaceholderReplacer.replace(text, context) — text interpolation
- PlaceholderEffect.apply(value) — value transformation

**Where money/items/persistence/threading/permissions live:**
- String presentation layer only; no domain logic

**Config/YAML files it reads:**
- None directly (providers registered via SPI)

**Tests that exist for it:**
PlaceholderRegistryTest, PlaceholderReplacerTest, PlaceholderEffectTest, RecursivePlaceholderTest, PlaceholderParseErrorTest, (4-6 tests in keystone-common suite)

