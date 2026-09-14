# Bartizan system map — 2026-09-10 (HEAD 0e18356, branch master)

| Code | Slug | Name | Modules / packages | Files | Hubs (god nodes / entry points) | Risk hotspots |
|---|---|---|---|---|---|---|
| WM | weapon-model | Weapon types & models | bartizan-api: api/weapon/**, api/weapon/dto/**, api/weapon/durability/**, api/weapon/modifiers/**, api/weapon/recoil/**, api/weapon/reload/**, api/weapon/spread/** | bartizan-plugin: weapon/ (type defs only) | 45 | Weapon, GunWeapon, IncendiaryWeapon, ThrowableWeapon, BiologicalWeapon, MeleeWeapon, WeaponType, ModifiersData, ReloadData, ProjectileState, DurabilityData | enum parsing in WeaponType (5 action types: GUN, AUTO, BURST, INCENDIARY, THROWABLE, MELEE), NBT round-trip via Weapon.toNBT/fromNBT, durability float precision, modifier/recoil/spread DTO serialization |
| FA | firing-actions | Firing & ammunition | bartizan-plugin: weapon/action/**, fire/** | bartizan-api: api/ammo/** | 11 | GunAction, FullAutoTask, BurstTask, IncendiaryAction, BiologicalAction, MeleeAction, ThrowableAction, AmmunitionManager, PluginFireRegistry | async firing timers (Timer.start(true) full-auto loops), ammo stack consumption (damage reduction vs inventory), selective fire state machine (transitions: SINGLE/BURST/AUTO), held-button duration, player inventory mutation mid-combat |
| WE | wearables | Wearables (armor/jewelry) | bartizan-plugin: wearable/**, command/wearable/** | bartizan-api: api/wearable/** | 8 | WearableService, WearableEquipListener, WearableCommand, WearableInfoCommand, WearableListCommand, WearableGiveCommand, WearableRefresher, WearableConverter | armor slot swap race conditions (equip/unequip event ordering), NBT tag binding to player armor stacks (durability tracking), item stack mutation during ItemRefresher refresh calls, command permission chain (bartizan.wearable.give/.info/.list) |
| RT | raytrace | Projectile raytrace & impact | bartizan-plugin: raytrace/**, listener/projectile/** | bartizan-api: api/raytrace/** | bartizan-plugin: listener/death/** | bartizan-api: api/event/** (WeaponRaytraceImpactEvent, WeaponEntityDamageEvent) | 9 | WeaponRaytracer, WeaponRaytracerImpl, SteppedProjectileTask, RaytraceContext, RaytraceRequest, RaytraceDamageFlag, ProjectileDamageListener, WeaponEntityDamageEvent, WeaponRaytraceImpactEvent, CombatEligibility | entity raycast loop stepping (async task per projectile), block collision with hitbox boundaries, entity damage application (apply before/after other plugins), fall damage immunity during flight, CombatEligibility contract lookup via Bukkit ServicesManager (lazy, no cache) |
| CF | config-parsing | Configuration & parsing | bartizan-plugin: configuration/parser/**, file/**, config/ItemConfig | bartizan-plugin: item/** | bartizan-api: api/item/** | 27 | GunWeaponParser, IncendiaryWeaponParser, BiologicalWeaponParser, ThrowableWeaponParser, MeleeWeaponParser, AmmunitionSectionParser, ModifiersSectionParser, SelectiveFireSectionParser, ItemConfig, BartizanItemVocabulary, WeaponConverter, AmmunitionConverter, WearableConverter, WeaponRefresher, AmmunitionItemRefresher, WearableRefresher, WeaponItemApi | YAML keys (missing/malformed section headers), float coercion (recoil/spread precision), WeaponType.valueOf() enum mismatch, modifier array iteration (length validation), item vocabulary registration phase (pre-CONFIG bean ordering), ItemConverter NBT read/write symmetry, FileInitializer fallback on init failure |
| DB | persistence | Database & persistence | bartizan-plugin: database/**, data/** | 7 | WeaponRepository, AmmunitionRepository, WearableRepository, WeaponAutoSaveTask, WeaponDataCleanupTask, TableBackend (Keystone), Weapon table schema | SQLite/MySQL schema changes (if Weapon columns added/removed), HikariCP pool shutdown and temp file cleanup (Windows file lock leak via -shm/-wal files), bulk upsert in WeaponAutoSaveTask (transaction scope), cascade logic on ammo/wearable deletion, @TempDir(cleanup=NEVER) + releaseDbFiles() in tests |
| CM | commands | Admin commands | bartizan-plugin: command/**, command/data/**, command/wearable/** | 17 | WeaponCommand, WeaponGiveCommand, WeaponInfoCommand, WeaponListCommand, AmmunitionCommand, AmmunitionGiveCommand, AmmunitionInfoCommand, AmmunitionListCommand, WearableCommand, WearableGiveCommand, WearableInfoCommand, WearableListCommand, DebugCommand, ReloadCommand, HelpInfo, CommandInformation, InformationManager | permission checks (bartizan.weapon.give/info/list, bartizan.ammo.give/info/list, bartizan.wearable.give/info/list), player inventory mutations in give commands (stack size limits), enum argument parsing (WeaponType, AmmoType via XSeries), debug NBT inspection (toNBT() serialization), help text localization via Messages enum |
| EV | events | Event listeners & hooks | bartizan-plugin: listener/**, listener/reload/**, listener/fire/**, listener/death/**, listener/player/**, listener/wearable/**, listener/selective/** | 11 | WeaponInteract, WeaponReloadListener, WeaponDroppedListener, WeaponSelectiveFireChangeListener, WeaponDeathListener, WearableEquipListener, PlayerJoinListener, ProjectileDamageListener | PlayerInteractEvent.RIGHT_CLICK_AIR cancellation (ignoreCancelled=false to see air clicks), held-key state machine across multiple ticks, entity death event priority (plugin order), armor equip/unequip ordering, fire-state cleanup on weapon drop/unequip, @ListenerHandler condition gates (Citizens soft-depend) |
| NU | nucleus | Plugin core & bootstrap | bartizan-plugin: bootstrap/**, config/ (bean wiring only), Bartizan.java, util/**, npc/**, metrics/** | 10 | Bartizan (JavaPlugin.onEnable/onDisable), GanglandContext (bootstrap orchestrator), WiringConfig, BeanFactory (Keystone), BartizanChatUtil, NpcWeaponControllerImpl, BartizanBootstrapTest | bean instantiation order (ItemConfig must load before commands; WeaponService before listeners), Keystone version mismatch (provided scope, no shade), ServicesManager lazy lookups (BartizanApi, ItemVocabulary, CombatEligibility registered/resolved at use-time, never cached), plugin.yml softdepend: [Citizens, ViaVersion, PlaceholderAPI] (Citizens: NpcSupport.available() guarding), DependencyContainer circular-ref detection |

## Package -> system (every main package once)

- org/luckyraven/bartizan/api/weapon** -> WM
- org/luckyraven/bartizan/api/weapon/dto -> WM
- org/luckyraven/bartizan/api/weapon/durability -> WM
- org/luckyraven/bartizan/api/weapon/modifiers -> WM
- org/luckyraven/bartizan/api/weapon/modifiers/action -> WM
- org/luckyraven/bartizan/api/weapon/recoil -> WM
- org/luckyraven/bartizan/api/weapon/reload -> WM
- org/luckyraven/bartizan/api/weapon/spread -> WM
- org/luckyraven/bartizan/api/ammo -> FA
- org/luckyraven/bartizan/api/combat -> RT
- org/luckyraven/bartizan/api/event -> RT
- org/luckyraven/bartizan/api/item -> CF
- org/luckyraven/bartizan/api/npc -> NU
- org/luckyraven/bartizan/api/raytrace -> RT
- org/luckyraven/bartizan/api/wearable -> WE
- org/luckyraven/bartizan/api -> WM
- org/luckyraven/bartizan -> NU
- org/luckyraven/bartizan/ammo -> FA
- org/luckyraven/bartizan/bootstrap -> NU
- org/luckyraven/bartizan/command -> CM
- org/luckyraven/bartizan/command/data -> CM
- org/luckyraven/bartizan/command/wearable -> CM
- org/luckyraven/bartizan/config -> NU
- org/luckyraven/bartizan/configuration -> CF
- org/luckyraven/bartizan/configuration/parser -> CF
- org/luckyraven/bartizan/data -> DB
- org/luckyraven/bartizan/database -> DB
- org/luckyraven/bartizan/file -> CF
- org/luckyraven/bartizan/fire -> FA
- org/luckyraven/bartizan/item -> CF
- org/luckyraven/bartizan/listener -> EV
- org/luckyraven/bartizan/listener/death -> EV
- org/luckyraven/bartizan/listener/fire -> EV
- org/luckyraven/bartizan/listener/player -> EV
- org/luckyraven/bartizan/listener/projectile -> RT
- org/luckyraven/bartizan/listener/reload -> EV
- org/luckyraven/bartizan/listener/selective -> EV
- org/luckyraven/bartizan/listener/wearable -> EV
- org/luckyraven/bartizan/metrics -> NU
- org/luckyraven/bartizan/npc -> NU
- org/luckyraven/bartizan/raytrace -> RT
- org/luckyraven/bartizan/util -> NU
- org/luckyraven/bartizan/weapon -> FA
- org/luckyraven/bartizan/weapon/action -> FA
- org/luckyraven/bartizan/wearable -> WE

## Scanner briefing per system

### WM weapon-model

**What it does:** Defines all five weapon types (Gun, Incendiary, Biological, Throwable, Melee) and shared data structures (modifiers, recoil, spread, reload, durability, projectile state). Weapon instances are immutable at runtime; changes go through cloning and NBT serialization.

**Entry points to start from:**
- `Weapon` (base record with .toNBT()/.fromNBT())
- `WeaponType` enum (5 values; parser gate)
- `GunWeapon`, `IncendiaryWeapon`, `BiologicalWeapon`, `ThrowableWeapon`, `MeleeWeapon` (concrete types)
- `ModifiersData`, `ReloadData` (DTO records)
- `DurabilityData`, `ProjectileState`, `RaytraceDamageFlag` (stateful models)

**Where money/items/persistence/threading/permissions live:**
- Persistence: weapons serialized to player NBT via Bukkit ItemStack.getItemMeta().getPersistentDataContainer() (consumer apps read .toNBT())
- Items: Keystone ItemBuilder wraps Weapon instances; no direct Item mutation here
- Economy: none (weapon ownership tracked by gang, not item cost)
- Threading: model is stateless/read-only; safe for concurrent access
- Permissions: none (permission gates are in CM system)

**Config/YAML files it reads:**
- bartizan-plugin/src/main/resources/weapon/\*.yml (26 weapon definitions: awp.yml, pistol.yml, knife.yml, grenade.yml, etc.)
- Each weapon YAML is parsed into Weapon via GunWeaponParser/IncendiaryWeaponParser/etc.

**Tests that exist for it:**
- bartizan-api/src/test/java/org/luckyraven/bartizan/api/weapon/dto/DamageDataTest.java
- bartizan-api/src/test/java/org/luckyraven/bartizan/api/weapon/durability/DurabilityCalculatorTest.java
- bartizan-api/src/test/java/org/luckyraven/bartizan/api/support/WeaponFixtures.java (test fixtures)
- bartizan-plugin/src/test/java/org/luckyraven/bartizan/item/WeaponItemApiTest.java

---

### FA firing-actions

**What it does:** Handles weapon firing dispatch, ammunition consumption, and sustained-fire mechanics (full-auto loops, burst timing, selective fire state machine). Bridges player input (interact event from EV) to projectile spawning (RT) and ammo removal (DB).

**Entry points to start from:**
- `GunAction` (primary dispatcher from WeaponInteract listener)
- `FullAutoTask`, `BurstTask` (async repeating timers for hold-to-fire)
- `IncendiaryAction`, `BiologicalAction`, `MeleeAction`, `ThrowableAction` (action-type implementations)
- `AmmunitionManager` (stack validation and removal)
- `PluginFireRegistry` (fire-state lifecycle per player/weapon combo)

**Where money/items/persistence/threading/permissions live:**
- Items: ammo consumed from player.getInventory() (stack.subtract)
- Persistence: fire-state stored in PluginFireRegistry (in-memory, not persisted; reset on logout)
- Threading: async timers (Timer.start(true)) — full-auto is async; runs on Bukkit scheduler
- Economy: none
- Permissions: none (checked in CM)

**Config/YAML files it reads:**
- bartizan-plugin/src/main/resources/items/ammunition.yml (ammo definitions)
- Per-weapon YAML (Weapon.getAmmo() references ammo type)

**Tests that exist for it:**
- bartizan-plugin/src/test/java/org/luckyraven/bartizan/weapon/SelectiveFireTest.java
- bartizan-plugin/src/test/java/org/luckyraven/bartizan/weapon/WeaponConsumeShotTest.java

---

### WE wearables

**What it does:** Equips armor/jewelry to player armor slots (helmet, chestplate, leggings, boots). Wearables are tracked via NBT on each piece and refreshed on equip.

**Entry points to start from:**
- `WearableService` (registry and lookup)
- `WearableEquipListener` (PlayerArmorChangeEvent hook)
- `WearableCommand`, `WearableInfoCommand`, `WearableListCommand`, `WearableGiveCommand` (admin commands)
- `WearableRefresher`, `WearableConverter` (item converters)

**Where money/items/persistence/threading/permissions live:**
- Items: wearables are ItemStack instances on player armor slots
- Persistence: database via WearableRepository (track owned wearables by UUID)
- Threading: single-threaded (event listeners are sync)
- Economy: none
- Permissions: bartizan.wearable.give, bartizan.wearable.info, bartizan.wearable.list

**Config/YAML files it reads:**
- bartizan-plugin/src/main/resources/items/wearables.yml (wearable catalog)

**Tests that exist for it:**
- bartizan-plugin/src/test/java/org/luckyraven/bartizan/listener/wearable/WearableEquipListenerTest.java

---

### RT raytrace

**What it does:** Fires projectiles via line-of-sight raycast, steps them through blocks and entities, applies impact damage events, and coordinates with consumer-plugin damage eligibility checks (CombatEligibility). Handles three raytrace classes that moved from bartizan-api: WeaponShooting, WeaponMuzzle, SteppedProjectileTask (now in plugin).

**Entry points to start from:**
- `WeaponRaytracer` (contract; consumer API)
- `WeaponRaytracerImpl` (implementation; does the actual stepping)
- `SteppedProjectileTask` (async task per projectile)
- `RaytraceContext`, `RaytraceRequest` (DTO; step-by-step config)
- `ProjectileDamageListener` (entity damage hook)
- `WeaponRaytraceImpactEvent` (event fired on impact)

**Where money/items/persistence/threading/permissions live:**
- Items: none
- Persistence: none (projectiles are ephemeral)
- Threading: async task (Timer.start(true)) — ray stepping is async per Keystone guidance
- Economy: none
- Permissions: none

**Config/YAML files it reads:**
- Weapon.Spread/Recoil from WM (applied during ray generation)

**Tests that exist for it:**
- bartizan-api/src/test/java/org/luckyraven/bartizan/api/raytrace/SteppedProjectileTaskTest.java
- bartizan-plugin/src/test/java/org/luckyraven/bartizan/listener/death/WeaponDeathListenerTest.java

---

### CF config-parsing

**What it does:** Loads weapon/ammo/wearable YAML files from disk, parses them into domain objects, and registers the item vocabulary with Keystone's ItemVocabulary SPI.

**Entry points to start from:**
- `ItemConfig` (bean; wires parsers and vocabulary)
- `GunWeaponParser`, `IncendiaryWeaponParser`, `BiologicalWeaponParser`, `ThrowableWeaponParser`, `MeleeWeaponParser` (per-type YAML loaders)
- `AmmunitionSectionParser`, `ModifiersSectionParser`, `SelectiveFireSectionParser` (sub-section parsers)
- `BartizanItemVocabulary` (registers weapon:/ammo:/wearable: item prefixes)
- `WeaponConverter`, `AmmunitionConverter`, `WearableConverter` (to/from ItemStack NBT)
- `WeaponRefresher`, `AmmunitionItemRefresher`, `WearableRefresher` (apply visual updates on refresh)

**Where money/items/persistence/threading/permissions live:**
- Items: ItemStack instances are created/updated by converters (NBT tags)
- Persistence: database lookups in ItemConfig (used by Keystone's ItemBuilder)
- Threading: single-threaded (runs in CONFIG phase)
- Economy: none
- Permissions: none

**Config/YAML files it reads:**
- bartizan-plugin/src/main/resources/weapon/*.yml (26 weapon definitions)
- bartizan-plugin/src/main/resources/items/ammunition.yml
- bartizan-plugin/src/main/resources/items/wearables.yml

**Tests that exist for it:**
- bartizan-plugin/src/test/java/org/luckyraven/bartizan/item/WeaponItemPredicatesTest.java

---

### DB persistence

**What it does:** Manages the Weapon, Ammunition, and Wearable database tables; provides autosave and cleanup tasks.

**Entry points to start from:**
- `WeaponRepository`, `AmmunitionRepository`, `WearableRepository` (CRUD interfaces)
- `WeaponAutoSaveTask` (periodic upsert all weapons)
- `WeaponDataCleanupTask` (delete orphaned records)
- `TableBackend` (from Keystone; dialect upsert)

**Where money/items/persistence/threading/permissions live:**
- Items: none (database records are weapon/ammo/wearable IDs, not items)
- Persistence: SQLite or MySQL (chosen via settings.yml)
- Threading: autosave runs async (PeriodicalUpdates scheduler)
- Economy: none
- Permissions: none

**Config/YAML files it reads:**
- bartizan-plugin/src/main/resources/settings.yml (database.type, autosave interval)

**Tests that exist for it:**
- bartizan-plugin/src/test/java/org/luckyraven/bartizan/bootstrap/BartizanBootstrapTest.java (bootstrap DB)
- bartizan-plugin/src/test/java/org/luckyraven/bartizan/data/WeaponAutoSaveTaskTest.java
- bartizan-plugin/src/test/java/org/luckyraven/bartizan/data/WeaponDataCleanupTaskTest.java
- bartizan-plugin/src/test/java/org/luckyraven/bartizan/database/WeaponTableImportTaskTest.java

---

### CM commands

**What it does:** Provides admin commands to list, inspect, and give weapons/ammo/wearables to players. Commands dispatch through Keystone's CommandManager.

**Entry points to start from:**
- `WeaponCommand`, `AmmunitionCommand`, `WearableCommand` (top-level command nodes)
- `WeaponGiveCommand`, `WeaponInfoCommand`, `WeaponListCommand` (weapon sub-commands; same for ammo/wearable)
- `DebugCommand` (inspect NBT, reload YAML)
- `ReloadCommand` (reload all YAML files)
- `CommandInformation`, `InformationManager` (help text storage)
- `HelpInfo` (help rendering)

**Where money/items/persistence/threading/permissions live:**
- Items: player.getInventory().addItem() in give commands
- Persistence: none
- Threading: single-threaded (command execution is sync)
- Economy: none
- Permissions: bartizan.weapon.*, bartizan.ammo.*, bartizan.wearable.* (checked via Keystone permission framework)

**Config/YAML files it reads:**
- bartizan-plugin/src/main/resources/message/message_en.yml (command help text, localized via Messages enum)

**Tests that exist for it:**
- bartizan-plugin/src/test/java/org/luckyraven/bartizan/command/data/CommandInformationTest.java
- bartizan-plugin/src/test/java/org/luckyraven/bartizan/command/data/InformationManagerTest.java

---

### EV events

**What it does:** Listens to Bukkit events (PlayerInteractEvent, PlayerArmorChangeEvent, EntityDeathEvent, etc.) and coordinates weapon firing, reloading, and wearable equipping.

**Entry points to start from:**
- `WeaponInteract` (PlayerInteractEvent → GunAction)
- `WeaponReloadListener` (inventory change triggers reload animation)
- `WeaponDroppedListener` (item drop → fire-state cleanup)
- `WeaponSelectiveFireChangeListener` (shift-click to cycle modes)
- `WeaponDeathListener` (entity death → drop weapon)
- `WearableEquipListener` (armor change → equip/unequip)
- `PlayerJoinListener` (player login → restore weapons)

**Where money/items/persistence/threading/permissions live:**
- Items: none (listeners observe, don't modify)
- Persistence: WeaponInteract loads from player NBT
- Threading: all listeners are sync (Bukkit main thread)
- Economy: none
- Permissions: none (gated at gameplay, not admin)

**Config/YAML files it reads:**
- None (listeners use runtime state)

**Tests that exist for it:**
- bartizan-plugin/src/test/java/org/luckyraven/bartizan/listener/death/WeaponDeathListenerTest.java

---

### NU nucleus

**What it does:** Plugin entry point, bootstrap orchestrator, bean wiring, and cross-plugin contract resolution (ServicesManager lookups). Coordinates all other systems.

**Entry points to start from:**
- `Bartizan` (JavaPlugin; onEnable/onDisable)
- `GanglandContext` (bootstrap driver; from Keystone)
- `WiringConfig` (bean factory for WeaponService, repositories, etc.)
- `BartizanChatUtil` (colorized chat, action bars)
- `NpcWeaponControllerImpl` (NPC weapon holding)

**Where money/items/persistence/threading/permissions live:**
- Items: none (coordination only)
- Persistence: DependencyContainer singleton lifecycle
- Threading: bootstrap is sync; bean instantiation order matters
- Economy: none
- Permissions: none

**Config/YAML files it reads:**
- bartizan-plugin/src/main/resources/plugin.yml (softdepend: [Citizens, ViaVersion, PlaceholderAPI], depend: [Keystone])
- bartizan-plugin/src/main/resources/settings.yml (general plugin settings)

**Tests that exist for it:**
- bartizan-plugin/src/test/java/org/luckyraven/bartizan/bootstrap/BartizanBootstrapTest.java
