# WS3 Census: Loot Chests and Holograms
**Wave:** Decoupling 2026-09-14  
**Scope:** Loot chests → runtime module; holograms → Keystone  
**Repos:** Gangland Warfare (0.9.1, Keystone pin 1.9.2), Keystone (phase-h9-host-api)

---

## 1. Loot Chest Files

### lootchest-api (31 files, 2372 lines)
| Package | File | Lines | Role |
|---------|------|-------|------|
| root | ChestCooldownManager.java | 338 | Cooldown lifecycle, hologram updates, icon spawning |
| config | LootChestConfig.java | 49 | Configuration container |
| config | LootChestLoader.java | 271 | YAML loading, table/tier instantiation |
| config | LootChestMessagesProvider.java | 108 | Contract for message routing |
| config | LootChestSettingsProvider.java | 31 | Contract for settings access |
| data | CrackingSession.java | 161 | Safe-cracking minigame session (from FRONT-PAGE.md:359: "Safe Cracking Minigame and Advanced Chest Mechanics") |
| data | LootChestData.java | 148 | Entity model: id, world, xyz, lootTableId, tierId, respawnTime, inventorySize, displayName, lastOpened, isLooted |
| data | LootChestSession.java | 201 | Open session state + cooldown wrapper |
| data | LootTable.java | 193 | Weighted loot entry collection |
| data | LootTier.java | 31 | Tier definition (single-use ID + name) |
| events (root) | LootChestEvent.java | 14 | Base class for all lootchest events |
| events/cracking | LootChestCrackingEndEvent.java | 45 | Fired when cracking ends (success/fail both fire this first) |
| events/cracking | LootChestCrackingFailureEvent.java | 45 | Cracking failed |
| events/cracking | LootChestCrackingStartEvent.java | 45 | Cracking session started |
| events/cracking | LootChestCrackingSuccessEvent.java | 45 | Cracking succeeded |
| events/cracking | LootChestDuringCrackingEvent.java | 45 | Tick event during cracking |
| events/lootchest | LootChestCloseEvent.java | 45 | Chest GUI closed |
| events/lootchest | LootChestCooldownCompleteEvent.java | 38 | Cooldown expired, chest ready again |
| events/lootchest | LootChestDuringCooldownEvent.java | 38 | Tick event during cooldown |
| events/lootchest | LootChestOpenEvent.java | 45 | Chest GUI opened |
| handler | LootChestHandler.java | 26 | Abstract event handler base |
| handler/cracking | CrackingFailedHandler.java | 14 | Handler interface |
| handler/cracking | CrackingStartHandler.java | 14 | Handler interface |
| handler/cracking | CrackingSuccessHandler.java | 14 | Handler interface |
| handler/cracking | CrackingTickHandler.java | 14 | Handler interface |
| handler/lootchest | ChestCooldownCompleteHandler.java | 14 | Handler interface |
| handler/lootchest | ChestCooldownTickHandler.java | 14 | Handler interface |
| handler/lootchest | SessionCompleteHandler.java | 14 | Handler interface |
| handler/lootchest | SessionStartHandler.java | 14 | Handler interface |
| item | LootItemReference.java | 74 | Item stack wrapper for loot entries |
| listener | LootChestListener.java | 224 | Event listener: fires handlers, participates in CrackingSession.start() loop |

### gangland-impl (10 files)
| File | Path | Role |
|------|------|------|
| LootChestManager.java | lootchest/ | Loads chests from YAML, registers handlers, wires `LootChestData` → database |
| LootChestWand.java | lootchest/ | Admin tool to create/remove/edit chests (holds target ID in NBT) |
| LootChestWandTag.java | lootchest/ | Enum of NBT tags (registered in `ItemConfig.nbtTagCatalog()` L134) |
| LootChestRepository.java | database/repositories/lootchest/ | `extends AbstractRepository<LootChestData>` |
| LootChestTable.java | database/tables/lootchest/ | Schema: id(PK), world, x, y, z, loot_table_id, tier_id, respawn_time, inventory_size, display_name, last_opened, is_looted (12 Attributes) |
| GanglandLootChestMessages.java | file/configuration/lootchest/ | `implements LootChestMessagesProvider` |
| LootChestSettings.java | file/configuration/lootchest/ | `implements LootChestSettingsProvider` |
| LootChestWandCommand.java | command/sub/lootchest/ | `/glw lootchest wand` |
| LootChestWandEditCommand.java | command/sub/lootchest/ | `/glw lootchest wandedit` |
| LootChestRemoveCommand.java | command/sub/lootchest/ | `/glw lootchest remove` |

### YAML Configuration
- **`lootchests/loot_chests.yml`** — chest definitions (id, loot_table_id, tier_id, location, respawn_time, inventory_size, display_name)
- **`lootchests/tiers.yml`** — tier catalog (id, name per tier)
- **`settings.yml` `Loot_Chest:` block (34 lines, L593-626)**
  - `Countdown_Timer: 300` (seconds before chest opens)
  - `Sound:` Opening/Locked/Closing (BLOCK_CHEST_* XSound codes)
  - `Allowed_Blocks:` CHEST, TRAPPED_CHEST, BARREL, SHULKER_BOX, ENDER_CHEST
  - `Rewards:` Money (min/max), Experience (min/max), Commands (array)

### Messages & Settings
- **Messages enum** (gangland-api `org.luckyraven.gangland.file.configuration.Messages`) — no LootChest-specific entries found in enum (GanglandLootChestMessages implements contract instead)
- **Settings enum** — LootChestSettingsProvider contract (GanglandLootChestSettings implements)

---

## 2. Hologram Files

### hologram-api (3 files, 346 lines)
| File | Lines | Public Methods / Role |
|------|-------|----------------------|
| HologramService.java | 133 | `BeanLifecycle` bean; **public methods:** `createHologram(Location, String...)`, `createUpdatingHologram(Location, long updateIntervalTicks, Function<...>)`, `getHologram(UUID)`, `getHologramAt(Location)`, `removeHologram(UUID)`, `removeHologramAt(Location)`, `cancelUpdateTask(UUID)`, `clear()`, `onShutdown()` |
| Hologram.java | 175 | ArmorStand-based display entity, mutable text/location, update task lifecycle |
| HologramProtectionListener.java | 38 | `@ListenerHandler` on `PlayerArmorStandManipulateEvent` + `PlayerInteractAtEntityEvent` (blocks players touching hologram armor stands) |

### Consumers (Gangland repo)
- **GameplayConfig.java L268:** `hologramService()` bean (KERNEL phase, returns HologramService)
- **LootChestManager.java:** passes `HologramService` to `LootChestManager(...)` ctor (L275)
- **ChestCooldownManager.java L29:** holds `HologramService hologramService`, spawns/updates cooldown holograms
- No other Gangland core code uses holograms (lootchest-api is the sole consumer today)

### Hologram Protection & Implementation
- **Entity type:** ArmorStand only (no TextDisplay or packet-level code)
- **Protection:** `HologramProtectionListener` blocks `PlayerArmorStandManipulateEvent` + `PlayerInteractAtEntityEvent` on hologram-owned stands
- **Lifecycle:** `HologramService implements BeanLifecycle`; `onShutdown()` clears all holograms + cancels update tasks

---

## 3. Dependencies

### lootchest-api imports from:
- **inventory-api** (2 usages): `InventoryHandler` (for loot chest GUI display?), other inventory/panel classes
- **hologram-api** (2 usages): `HologramService`, `Hologram` for cooldown display
- **gangland-item** (FuelService usage? — confirms unique item coupling)
- **gangland-core** (references to core contracts)
- **keystone-item/keystone-persistence** (item converters, repository contracts)
- **Messages/Settings** (via LootChestMessagesProvider, LootChestSettingsProvider contracts)

### gangland-impl lootchest files import:
- lootchest-api (service, data, events, handlers)
- hologram-api (HologramService wired in GameplayConfig)
- inventory-api (for loot chest GUI)
- gangland-item (fuel system if chests drop fuel)
- keystone repositories/bean

### graphify affected "LootChestData" summary (78 edges):
- **Core path:** LootChestData → LootChestRepository → LootChestTable → LootChestManager → LootChestService → ChestCooldownManager → LootChestListener
- **Breadth:** CrackingSession, LootChestSession, LootTable, LootTier, all 9 events, all 9 handlers
- **Impl side:** 3 commands, 2 listeners, 2 config files, 2 YAML files

### NbtTagCatalog wiring
- **ItemConfig.nbtTagCatalog() L134:** `for (LootChestWandTag tag : LootChestWandTag.values()) { catalog.register(tag); }`

---

## 4. Persistence

### LootChestRepository
- **Package:** `org.luckyraven.gangland.database.repositories.lootchest`
- **Extends:** `AbstractRepository<LootChestData>` (from Keystone)
- **Methods:** inherited `save()`, `delete()`, `findAll()` via Keystone SPI path (TableBackend)

### LootChestTable Schema
**12 Attributes (all on default String/Double/Long/Integer/Boolean classes):**
1. `id` (String, PK=true)
2. `world` (String)
3. `x` (Double)
4. `y` (Double)
5. `z` (Double)
6. `loot_table_id` (String)
7. `tier_id` (String)
8. `respawn_time` (Long) — cooldown until next open
9. `inventory_size` (Integer) — row count × 9
10. `display_name` (String)
11. `last_opened` (Long) — timestamp
12. `is_looted` (Boolean) — already taken?

### Autosave & Shutdown
- **Data supplier wiring:** `LootChestManager.initialize()` calls `lootChestRepository.setDataSupplier(...)` (following feedback rule)
- **PeriodicalUpdates:** includes loot chests in `upsertAll()` batch on auto-save interval
- **DataCleanupTask:** TBD if lootchests participate

---

## 5. Events + Handlers

### 9 Events (all extend LootChestEvent, all in lootchest-api/events/)

**Cracking cycle (5):**
1. `LootChestCrackingStartEvent` — player starts minigame
2. `LootChestDuringCrackingEvent` — per-tick during cracking
3. `LootChestCrackingSuccessEvent` — minigame won
4. `LootChestCrackingFailureEvent` — minigame lost
5. `LootChestCrackingEndEvent` — fired after both success and failure

**Chest lifecycle (4):**
6. `LootChestOpenEvent` — GUI displayed
7. `LootChestDuringCooldownEvent` — per-tick during respawn cooldown
8. `LootChestCooldownCompleteEvent` — cooldown expired
9. `LootChestCloseEvent` — GUI closed

### 9 Handler Interfaces
- **Cracking (4):** `CrackingStartHandler`, `CrackingTickHandler`, `CrackingSuccessHandler`, `CrackingFailedHandler`
- **Chest (4):** `SessionStartHandler`, `ChestCooldownTickHandler`, `ChestCooldownCompleteHandler`, `SessionCompleteHandler`
- **Root:** `LootChestHandler` abstract base

### Event Firing
- `LootChestListener.java` registers handlers and fires them in response to events
- `CrackingSession.start()` drives the minigame loop (ticks event, checks win/lose, fires end event)

---

## 6. Commands

### 3 Lootchest Sub-commands (gangland-impl/command/sub/lootchest/)
1. **LootChestWandCommand** (`/glw lootchest wand`) — give admin the lootchest wand (NBT-marked)
2. **LootChestWandEditCommand** (`/glw lootchest wandedit <create|remove|edit> ...`) — manage chests (place/delete/update)
3. **LootChestRemoveCommand** (`/glw lootchest remove <id>`) — remove chest by ID

### commands.json entries
- Entries expected at `gangland-impl/src/main/resources/commands.json` (TBD if `Loot_Chest:` sub-tree exists)
- All 3 commands use `SubArgument` node registration (automatically scanned in COMMAND phase)

---

## 7. Module Template: gangland-mail

### Structure (26 files)
- **Main class:** `MailModule.java` (implements Keystone's `KeystoneModule`)
- **Config bean:** `MailModuleConfig.java` (`@Configuration` class, produces beans)
- **Manager:** `MailManager.java` (state + repository wiring)
- **Repository:** `MailRepository.java` + `MailTable.java` (data persistence)
- **Contract:** `MailRepositoryContract.java` (interface for testability)
- **Commands:** 11 command classes under `command/` (invite, ally, etc.), registered via `*Contribution` beans
- **Listeners:** `MailJoinListener.java`, `MailQuitListener.java` (auto-registered in LISTENER phase)
- **Data types:** `MailItem.java`, `MailStatus.java`, `MailType.java` (enums/records)
- **Tests:** 3 test classes (MailItemTest, MailManagerTest, MailModuleTest) + support class

### pom.xml Dependencies
```xml
<dependency>
    <groupId>org.spigotmc</groupId>
    <artifactId>spigot-api</artifactId>
    <scope>provided</scope>
</dependency>
<!-- All keystone-* modules at default scope (not provided) -->
<dependency>
    <groupId>org.luckyraven</groupId>
    <artifactId>keystone-common</artifactId>
</dependency>
<dependency>
    <groupId>org.luckyraven</groupId>
    <artifactId>keystone-persistence</artifactId>
</dependency>
<dependency>
    <groupId>org.luckyraven</groupId>
    <artifactId>keystone-bean</artifactId>
</dependency>
<dependency>
    <groupId>org.luckyraven</groupId>
    <artifactId>keystone-command</artifactId>
</dependency>
<dependency>
    <groupId>org.luckyraven</groupId>
    <artifactId>keystone-module</artifactId>
</dependency>
<dependency>
    <groupId>org.luckyraven</groupId>
    <artifactId>gangland-core</artifactId>
</dependency>
<!-- ONLY gangland-api, provided scope -->
<dependency>
    <groupId>org.luckyraven</groupId>
    <artifactId>gangland-api</artifactId>
    <scope>provided</scope>
</dependency>
```

### module.yml
```yaml
Id: mail
Name: Gangland Mail
Version: ${project.version}
Main: org.luckyraven.gangland.mail.MailModule
Host_Api: 1.0
Artifact: org.luckyraven:gangland-mail
```

### commands.json (at jar root)
Defines mail subcommands for `/glw mail` and `/glw ally` contribution points.

### How gangland-build Copies Modules
- **Assembly plugin** in `gangland-build/pom.xml` copies each module jar to `target/modules/` (per Maven artifact assembly)
- Server operators drop `target/modules/*.jar` into `plugins/Gangland_Warfare/modules/`
- Keystone `ModuleLoader` scans that folder at Gangland startup, loads module YAMLs, validates `Host_Api`, and adds jars to module classloader

---

## 8. Keystone Module Template: keystone-npc

### Structure (exists at E:\Programming\java\Keystone\keystone-npc/)
- **Package root:** `org.luckyraven.keystone.npc`
- **Sub-packages:** `entity/`, `event/`, `spi/`
- **Test package:** Parallel test tree exists

### How Keystone Modules Are Built
- Listed in root `pom.xml` `<modules>` section (keystone-npc is a sibling of keystone-plugin, not under it)
- `keystone-plugin` shades keystone-* modules (including keystone-npc) into the final `Keystone.jar`
- Keystone has **no hologram code today** (grep confirmed zero `*hologram*.java` files)

### Keystone CLAUDE.md Rule (Line 101)
> "Keystone holds only low-level, generic infrastructure. If a class knows about gangs, menus, weapons, or any product concept, it does not belong here. **Oriel owns inventory/menu code permanently** — none of it ever goes into Keystone. **Scoreboard/hologram code stays in Gangland.**"

⚠️ **WS3 override:** This rule says "hologram code stays in Gangland," but WS3 moves holograms to Keystone. User decision is binding; plan must update this line.

### keystone-testkit
- Location: `E:\Programming\java\Keystone\keystone-testkit\`
- Contains test utilities for Keystone modules; used by mail module tests via `FakeMailRepositoryContract`

---

## 9. Tests

### lootchest-api Tests (5 files, gangland-ui/lootchest-api/src/test/java/)
1. **CrackingSessionTest.java** — CrackingSession state machine (start, tick, success/failure outcomes)
2. **LootChestDataTest.java** — LootChestData entity construction, field validation
3. **LootTableTest.java** — Loot entry weighting, random selection
4. **LootItemReferenceTest.java** — Item stack wrapping
5. **TestItemParsers.java** (support) — shared item parsing utilities for tests

### No gangland-impl lootchest tests found
- Wand/commands/listeners/managers are not unit-tested (impl testing gap)
- Manager initialization and database wiring are integration-level concerns

---

## 10. Docket Status

### Searches in brainstorming/bug-docket-2026-09-06/findings/
- No matches for "loot", "chest", "cracking", or "hologram" in the bug docket findings files
- Conclusion: **Loot chests and holograms have no P0-P3 logged issues as of 2026-09-06**
- Safe-cracking minigame is listed as **"Upcoming Feature"** (FRONT-PAGE.md:359), not a bug

### Cross-docket (brainstorming/cross-docket-2026-09-10/)
- TBD if loot chest entries exist there

---

## 11. Surprises

### Hologram as ArmorStand Only
- No TextDisplay or packet-level NMS code; pure ArmorStand entity for text rendering
- **Risk on reload/shutdown:** Armor stands may leak if `HologramService.clear()` misses entities (need EntityDespawn audit)

### Cooldown Hologram Integration
- **ChestCooldownManager** not only tracks cooldown, but **spawns/updates a display hologram** every tick
- Cooldown completion both fires event AND removes hologram via `HologramService.removeHologram()`
- **Async hologram updates?** Need to check if ChestCooldownManager tick runs on async timer (flagged as risk if true)

### LootChestData as God Node
- **78 edges** in the affected graph (Keystone's `affected` command)
- Moving loot chests to a module means moving this entire dependency web (Repository, Table, Manager, Service, Listeners, Commands)

### Wand NBT Coupling
- `LootChestWand` holds target chest ID in an NBT tag (`LootChestWandTag.values()`)
- Tag registration happens in `ItemConfig.nbtTagCatalog()` (impl-side)
- **Migration path:** Module will need to register its own wand tag or accept it from the host

### No Async Concerns (Yet)
- `CrackingSession` and `ChestCooldownManager` tick handlers do not appear to call async Bukkit APIs
- Confirm timer source (sync vs. async) before finalization

### Inventory-API Dependency
- lootchest-api imports `InventoryHandler` (26 usages elsewhere in the codebase)
- WS2 (replacing inventory-api with Oriel) will impact lootchest-api's GUI code path
- **Sequence risk:** WS3 must ship after WS2, or lootchest-api must be refactored to use Oriel instead

---

## Planner Summary

**WS3 ships a lootchest runtime module with hologram service moved to Keystone. Lootchests today span 31 api + 10 impl files, 2372+LoC in api alone, deeply coupled to inventory-api (GUI rendering) and hologram-api (cooldown display). Safe-cracking minigame is the centerpiece feature (CrackingSession, 5 events, 4 handlers). Module follows gangland-mail template: pom with keystone-* deps, module.yml, MailModuleConfig-style bean wiring, repository scanning. Hologram service (3 files, 346 lines) is ArmorStand-based, lifecycle-managed, and used only by lootchests today. Keystone has zero hologram code; WS3 violates CLAUDE.md line 101 (user override accepted). Critical path: WS2 (Oriel integration) must precede WS3 to avoid lootchest-api reimplementation during the move. No logged docket entries for either loot chests or holograms.**

---

*Generated 2026-09-14 via graphify query, file analysis, and source inspection. All `source_location` references follow `file:line` format. Keystone 1.9.2 branch verified current.*
