# Oriel system map — 2026-09-10 (HEAD 0a7d38, branch master)

| Code | Slug | Name | Modules / packages | Files | Hubs (god nodes / entry points) | Risk hotspots |
|---|---|---|---|---|---|---|
| MC | menu-core | Menu core API | menu-core: core/registry/**, core/animation/**, core/click/**, core/component/**, core/requirement/**, core/*.java, core/args/** | 52 | MenuRegistry (170), Menu (141), Component (121), ClickHandler (217), RequirementHook (80) | registry lifecycle thread-safety, nested component rendering |
| CR | menu-config | Configuration & YAML loading | menu-data/config: config/loader/**, config/importer/**, config/action/**, config/**.java, config/component/**, config/requirement/**, config/command/**, config/template/** | 61 | ChestMenuLoader (59), ActionRegistry (39), BetterGUIImporter (74), DeluxeMenusImporter (50) | YAML parsing, BetterGUI/DeluxeMenus importer compatibility, hot reload |
| ED | menu-editor | In-game editor | menu-editor: editor/screen/**, editor/**.java | 50 | EditorController (354), MenuEditor (45), EditorScreen (45), ActionScreen (29) | editor session state, inventory click conflicts, undo/redo safety |
| CH | menu-chest | Chest menu implementation | menu-inventories/chest: chest/internal/**, chest/component/**, chest/**.java, chest/flow/**, chest/listener/** | 35 | ChestMenuBuilder (119), ChestMenu (87), PaginatedChestMenu (37), ItemComponent (100), PaginatedControlRegistry (38) | pagination slot arithmetic, open/close races, item rendering |
| IV | menu-inventory | Small inventory types | menu-inventories/anvil:** (internal, *.java), menu-inventories/crafting/**, menu-inventories/dispenser/**, menu-inventories/furnace/**, menu-inventories/hopper/**, menu-inventories/trader/** | 42 | FurnaceMenu (56), AnvilMenu, TraderMenu (42), WorkbenchMenu (52), DispenserMenu (52), HopperMenu (52) | MenuType API compatibility (Paper 1.21+), fake-window NMS calls, Merchant packet handling |
| DB | menu-persistence | Persistence layer | menu-data/database: database/history/** | 2 | DatabaseFaultSink, HistoryStore | SQLite/MySQL backend selection, concurrent writes |
| DP | menu-plugin | Plugin bootstrap & commands | menu-plugin: plugin/config/**, plugin/command/**, plugin/bootstrap/**, plugin/**.java, plugin/demo/**, plugin/dependency/** | 29 | OrielPlugin (entry), OrielContext (bootstrap driver), KernelConfig, WiringConfig, UpdateCheckBootstrap | bean ordering, soft-dependency gate (Citizens check), reload lifecycle |
| DI | menu-integration | Third-party integrations | menu-dependencies/papi/**, menu-dependencies/vault/economy/**, menu-dependencies/vault/permission/** | 6 | OrielPlaceholderExpansion (PAPI), EconomyActions, PermissionActions, ConditionRequirements, GroupRequirements | PAPI parsing failures, Vault provider availability at startup, economy transaction boundaries |

---

## Package -> system (every main package once)

- org/luckyraven/oriel/core/registry -> MC
- org/luckyraven/oriel/core/animation -> MC
- org/luckyraven/oriel/core/click -> MC
- org/luckyraven/oriel/core/component -> MC
- org/luckyraven/oriel/core/requirement -> MC
- org/luckyraven/oriel/core -> MC
- org/luckyraven/oriel/core/args -> MC

- org/luckyraven/oriel/config/loader -> CR
- org/luckyraven/oriel/config/importer -> CR
- org/luckyraven/oriel/config/action -> CR
- org/luckyraven/oriel/config -> CR
- org/luckyraven/oriel/config/component -> CR
- org/luckyraven/oriel/config/requirement -> CR
- org/luckyraven/oriel/config/command -> CR
- org/luckyraven/oriel/config/template -> CR

- org/luckyraven/oriel/editor/screen -> ED
- org/luckyraven/oriel/editor -> ED

- org/luckyraven/oriel/chest/internal -> CH
- org/luckyraven/oriel/chest/component -> CH
- org/luckyraven/oriel/chest -> CH
- org/luckyraven/oriel/chest/flow -> CH
- org/luckyraven/oriel/chest/listener -> CH

- org/luckyraven/oriel/anvil/internal -> IV
- org/luckyraven/oriel/anvil -> IV
- org/luckyraven/oriel/crafting -> IV
- org/luckyraven/oriel/crafting/internal -> IV
- org/luckyraven/oriel/crafting/listener -> IV
- org/luckyraven/oriel/dispenser -> IV
- org/luckyraven/oriel/dispenser/internal -> IV
- org/luckyraven/oriel/dispenser/listener -> IV
- org/luckyraven/oriel/furnace -> IV
- org/luckyraven/oriel/furnace/internal -> IV
- org/luckyraven/oriel/furnace/listener -> IV
- org/luckyraven/oriel/hopper -> IV
- org/luckyraven/oriel/hopper/internal -> IV
- org/luckyraven/oriel/hopper/listener -> IV
- org/luckyraven/oriel/trader -> IV
- org/luckyraven/oriel/trader/internal -> IV
- org/luckyraven/oriel/trader/listener -> IV

- org/luckyraven/oriel/database/history -> DB

- org/luckyraven/oriel/plugin/config -> DP
- org/luckyraven/oriel/plugin/command -> DP
- org/luckyraven/oriel/plugin/bootstrap -> DP
- org/luckyraven/oriel/plugin -> DP
- org/luckyraven/oriel/plugin/demo -> DP
- org/luckyraven/oriel/plugin/dependency -> DP

- org/luckyraven/oriel/papi -> DI
- org/luckyraven/oriel/vault/economy -> DI
- org/luckyraven/oriel/vault/permission -> DI

---

## Scanner briefing per system

### MC menu-core
Menu API contract and runtime lifecycle. Provides Menu (base interface), MenuBuilder (fluent), Component (slot-level logic), MenuRegistry (named menu store), ClickContext (event dispatch), ClickHandler (slot handler routing), and animation/requirement systems.

**Entry points to start from:**
- `MenuRegistry.register(String name, Menu)`, `MenuRegistry.getMenu(String)`
- `Menu.open(Player)`, `Menu.close(Player)`
- `Component.render(RenderContext)`, `Component.onClick(ClickContext)`
- `ClickHandler.handle(ClickContext)`
- `Requirement.evaluate(Player)` and `RequirementHook` (registry seam)

**Where persistence/threading/permissions live:**
- No persistence in core (delegate to CR). No economy checks (delegate to DI). No item mutations here — Component.onClick returns an immutable result; side effects in plugins.
- Thread-safety: MenuRegistry thread-safe map. ClickContext immutable. RenderContext read-only view of Menu state.
- Permissions: Component can check via Requirement; core does not enforce.

**Config/YAML files it reads:** None (files handled by CR).

**Tests that exist:** 84 test files across the repo; MC-specific tests in `menu-core/src/test/java/`.

### CR menu-config
YAML parsing and in-memory schema validation. Loads chest/anvil/trader/furnace/hopper/dispenser/crafting menus from user-provided YML. Importers convert BetterGUI and DeluxeMenus definitions to Oriel schema. Action registry maps string → action handler (click, open, close, broadcast, etc.).

**Entry points to start from:**
- `ChestMenuLoader.load(File)` → ChestMenu
- `BetterGUIImporter.importMenu(File)` → ChestMenu
- `DeluxeMenusImporter.importMenu(File)` → ChestMenu
- `ActionRegistry.get(String)` → Action handler

**Where persistence/threading/permissions live:**
- Permissions: RequirementRegistry holds parseable requirement strings; evaluates at render time via core Requirement.evaluate(). PermissionActions check player permission bits.
- Economy: handled by DI (Vault); CR action parser accepts economy action strings.
- I/O: hot reload via MenuConfigService.reload() scans file system on command; no threading — single-threaded reload via main thread.

**Config/YAML files it reads:**
- `menu-plugin/src/main/resources/menus/*.yml` (32 demo menus)
- `menu-plugin/src/main/resources/actions/*.yml` (action catalog)
- `menu-plugin/src/main/resources/templates/*.yml` (reusable snippets)

**Tests that exist:** ChestYamlLoaderTest, CustomComponentLoaderTest, RequirementParserTest, PaginatedControlsBugTest, ConfigFilePipelineStressTest, etc. (test files in menu-data/config/src/test/).

### ED menu-editor
In-game visual menu editor UI. Presents EditorScreen (inventory-based) for menu type selection, then component-grid editors for layout, property screens for item/requirement/action editing. State machine via EditorController navigates between screens. Modifications live in editor session until saved to disk.

**Entry points to start from:**
- `EditorController.editor()` (access point from `/menu edit`)
- `EditorScreen.openScreen(MenuEditor session)`
- `EditorType.CHEST`, `.ANVIL`, etc. (inventory kind selector)
- `MenuEditor.getMenu()`, `.save()`, `.revert()`

**Where persistence/threading/permissions live:**
- Persistence: MenuEditor session holds in-memory diff; save() writes to disk via MenuConfigService.
- Permissions: editor-open requires `/menu admin` (checked in plugin command layer, DP).
- Threading: single-threaded; inventory clicks happen on main thread.

**Config/YAML files it reads:** None directly; writes via MenuConfigService to user menus.

**Tests that exist:** EditorControllerNavigationTest, EditorTypesNavigationTest, RequirementEditingNavigationTest.

### CH menu-chest
Chest-based menu implementation. Provides ChestMenu (base), ChestMenuBuilder (fluent), PaginatedChestMenu (multi-page with forward/back buttons). Internal component types (buttons, spacers, custom) render to ItemStacks in slots. Flow system navigates between related menus. Listener layer (ChestMenuClickListener, ChestMenuCloseListener) bridges Bukkit events to core ClickContext.

**Entry points to start from:**
- `ChestMenuBuilder.size(int)`, `.title(Component)`, `.setItem(slot, Component)`
- `ChestMenu.open(Player)`
- `PaginatedChestMenu` (subclass for multi-page)
- `ChestMenuLoader.load(File)` from CR

**Where persistence/threading/permissions live:**
- Persistence: none; state lives in Component trees during a session.
- Economy: Component can wrap a CostComponent that delegates to DI's EconomyActions.
- Item handling: ComponentItemStack renders to Bukkit ItemStack in onClick(); mutations stay in memory until a save action posts to persistence.
- Threading: single-threaded; events on main thread.

**Config/YAML files it reads:** None (CR handles YAML → ChestMenu).

**Tests that exist:** ChestLifecycleClickContext, ChestMenuClickListener, ChestMenuCloseListener tests. Pagination tests in config module (CR).

### IV menu-inventory
Lightweight inventory menu types: AnvilMenu (text input via AnvilGUI wrapper), TraderMenu (Bukkit MERCHANT InventoryType), WorkbenchMenu (CRAFTING), FurnaceMenu (FURNACE with MenuType API on Paper 1.21+, fallback on older), DispenserMenu (DISPENSER fake window), HopperMenu (5-slot fixed). Each is a one-file or tiny-internal module with a single Loader in CR. All share core Menu/Component contract.

**Entry points to start from:**
- `AnvilMenu`, `TraderMenu`, `WorkbenchMenu`, `FurnaceMenu`, `DispenserMenu`, `HopperMenu` (one per type)
- `.open(Player)`
- Type-specific loaders: `AnvilMenuLoader`, `TraderMenuLoader`, etc. in CR
- PacketAdapter.openFakeWindow (NMS reflection for Furnace/Dispenser on non-Paper builds)

**Where persistence/threading/permissions live:**
- Persistence: none (same as CH — Component-driven).
- Permissions/Economy: same delegation as CH → core Requirements → DI.
- Item handling: same as CH.
- Threading: single-threaded main-thread events.
- NMS: FurnaceMenu requires Paper MenuType API (1.21+); falls back to warning on older. DispenserMenu/FurnaceMenu on older builds use PacketAdapter reflection to NMS. Open failures return -1 to caller.

**Config/YAML files it reads:** None directly (CR handles loaders).

**Tests that exist:** Test files per type in menu-inventories/*/src/test/ (implied by structure; explicit test count in CR).

### DB menu-persistence
History tracking (audit log of menu loads/opens/closes). DatabaseFaultSink captures diagnostic faults. Pluggable backend (SqliteBackend, MysqlBackend). Minimal module; most persistence logic delegates to Keystone's DatabaseBackend SPI.

**Entry points to start from:**
- `DatabaseFaultSink.record(Fault)` (via Keystone diagnostics bean)
- `HistoryStore.log(MenuOpen/Close event)`

**Where persistence/threading/permissions live:**
- Persistence: SQLite or MySQL (chosen at startup via Keystone config). Batch writes on interval via Keystone scheduler.
- Threading: backend handles connection pooling; writes are async via Keystone batching.
- No permissions; no economy; audit-only.

**Config/YAML files it reads:** None (Keystone config drives backend selection).

**Tests that exist:** DatabaseFaultSinkSqliteTest.

### DP menu-plugin
JavaPlugin entry point and wiring. OrielPlugin (thin) delegates to OrielContext (bootstrap driver). KernelConfig and WiringConfig bean factories produce MenuRegistry, MenuEditor, MenuConfigService singletons. UpdateCheckBootstrap checks for newer versions on startup. DefaultListenerService + Keystone wiring auto-registers listener classes. Command subclasses (ReloadCommand, OpenCommand, DebugCommand, etc.) dispatch `/menu` sub-commands. Dependency gate checks soft deps (PlaceholderAPI, Vault, Citizens if referenced in plugins/features).

**Entry points to start from:**
- `OrielPlugin.onEnable()` → OrielContext.bootstrap()
- `KernelConfig`, `WiringConfig` @Bean factories
- Command: `/menu reload`, `/menu open <name>`, `/menu edit <name>`, `/menu debug`

**Where persistence/threading/permissions live:**
- Permissions: command handlers check `sender.hasPermission("oriel.admin")`, etc. (Keystone command framework enforces).
- Threading: reload() is sync; blocks main thread while re-parsing YAML.
- Lifecycle: plugin shutdown calls context.shutdown() to flush pending writes (Keystone bean lifecycle).

**Config/YAML files it reads:**
- `menu-plugin/src/main/resources/plugin.yml` (Spigot metadata)
- `menu-plugin/src/main/resources/config.yml` (Oriel settings: auto-load paths, soft-dep gates)
- 32 demo menus under `menu-plugin/src/main/resources/menus/`

**Tests that exist:** Bean wiring tests, command dispatch tests (counted in 84 total).

### DI menu-integration
Soft-dependency integration providers. OrielPlaceholderExpansion (PAPI hook) allows menu YAML to reference `%oriel_*%` placeholders. EconomyActions/EconomyRequirements wrap Vault Economy calls (check balance, withdraw, deposit). PermissionActions/GroupRequirements wrap Vault Permission calls (group membership, permission checks). Resolves providers fresh from Bukkit.getServicesManager() on every call, never cached.

**Entry points to start from:**
- `OrielPlaceholderExpansion.onPlaceholderRequest(Player, String)` (Vault Economy/Permission)
- `EconomyActions.withdraw(Player, double)`
- `PermissionActions.addGroup(Player, String)`

**Where persistence/threading/permissions live:**
- Permissions: direct call to Vault Permission provider (e.g., LuckPerms, PermissionsEx).
- Economy: direct call to Vault Economy (e.g., Essentials, EssentialsX).
- Threading: Vault calls are sync; main thread only.
- Soft-dep check: Keystone.HOOK_PROVIDERS.get(VaultEconomy.class) returns null if Vault absent; action degrades gracefully.

**Config/YAML files it reads:** None; configuration lives in Vault/PAPI (external).

**Tests that exist:** RequirementHookTest, PlaceholderTest (counted in 84 total).

---

## Notes

- **Archetype (menu-archetype):** Maven archetype scaffold JAR; 1 template resource file. Listed under "tooling" — not a system.
- **Total test files:** 83 across all modules.
- **Total source files:** 277 (239 main + 2 multi-module glue).
- **God-node concentration:** EditorController (354) dominates; ClickHandler and MenuRegistry (217, 170) form the core backbone. ChestMenuBuilder (119) and ItemComponent (100) show chest-heavy complexity.
