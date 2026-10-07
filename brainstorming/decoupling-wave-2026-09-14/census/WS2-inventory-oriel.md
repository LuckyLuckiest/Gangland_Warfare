# WS2 Census: inventory-api → Oriel

**Inventory-api** (58 Java files, 207 edges on `InventoryHandler`, 143 on `MultiPanelInventory`) is to be replaced by **Oriel** (sibling repo, 0.7.0, pinned at Keystone 1.7.0). This census documents the surface and gap.

---

## 1. Gangland inventory-api inventory

**52 files in subpackages + 6 root classes = 58 total** (line counts TBD; graphify edges exceed method refs by a factor due to transitive fans).

### By package
- `condition/` (4 files): `BooleanExpressionEvaluator`, `ConditionalSlotData`, `ConditionEvaluator`, `SlotCondition` — placeholder-based slot visibility
- `filter/` (11 files): `FilterAdapter`, `FilterApplier`, `FilterBinding`, `FilterField`, `FilterRegistry`, `FilterStore`, `FilterValue`, `SearchButtonFactory`, `SearchFilter`, `SortDescriptor`, `StandardFilterField` — search/sort/filter UI on paginated lists
- `flow/` (3 files): `FlowSession`, `MultiPanelInventory`, `Panel` — multi-panel menu state and transitions
- `handler/` (11 files): click/close/open/interact/drop/join/quit/swap listeners + `ClickHandler`, `ListenerBinder`, etc. — inventory event routing
- `listener/` (4 files): `InventoryClickListener`, `InventoryCloseListener`, `PlayerDropItemListener`, `PlayerInventoryCleanup` — Bukkit event handlers
- `multi/` (7 files): `MultiInventory`, `MultiInventoryCreation`, `MultiInventoryTag`, `PageConfig`, `Slot`, `ListEntry`, `ItemSourceProvider` — paged list rendering
- `part/` (5 files): `Fill`, `ButtonTags`, `PageConfig`, `Slot` helpers, and utility slot/button building
- `service/` (1 file): `InventoryRegistry` — YAML menu registry and lookup
- `unique/` (1 file): `UniqueItemHandler` — special-handled items (lootchest wand, etc.)
- `util/` (1 file): `InventoryUtil` — grid fill, border, aroundSlot, horizontalLine helpers
- `villager/` (4 files): `VillagerTradeMenuBuilder`, `VillagerTradeAdapterTask`, `VillagerMenu`, `VillagerDebugPanel` — fake merchant UI

### Root classes (6)
1. `InventoryHandler` — click dispatch, item builder, open/close/click/swap logic (207 edges)
2. `InventoryBuilder` — fluent menu definition (parse from YAML or code)
3. `InventoryData` — menu metadata record
4. `InventoryOpener` — player lookup and `.open(player)` dispatch
5. `OpenInventory` — marker interface for views
6. `State` — enum for internal state

### Maven dependencies (in pom.xml)
- **compile scope** (transitive from impl): keystone-bean, keystone-item, gangland-core (provided)
- **anvilgui** — anvil text-input menus (version TBD)
- **keystone-persistence** (provided) — repository pattern

---

## 2. Gangland consumers, class by class

**Total: 22 + 18 + 9 + 6 + 4 + 2 + 2 + 1 = 64 files across 8 modules**; most-imported classes: `Fill` (26 refs), `InventoryHandler` (26), `MultiPanelInventory` (24), `InventoryUtil` (20), `Panel` (16).

### gangland-impl (22 files)
Primary consumers building menus from YAML, showing gang/bank/bounty/phone UI.
- `ReloadPlugin.java` — reload command handler
- `DebugCommand.java` — `/glw debug inventory <menu-name>` testing
- `config/InventoryConfig.java` — bean registry of all YAML menus
- `config/FileConfig.java` — loads `inventory/*.yml` files via `InventoryRegistry`
- `listener/player/PlayerInventoryListener.java` — on-join menu handling
- `data/placeholder/` items with `PlaceholderService` integration
- `command/sub/{gang,bank,bounty,phone}/` — open menus from subcommands (7 files)
- `file/configuration/itemsource/GangItemSourceProvider.java` — gang list provider for paginated menus

**YAML menus in resources** (`gangland-impl/src/main/resources/inventory/`, 9 files):
- `phone.yml` (1,046 B) — main phone menu, opens other phone submenus
- `phone_banking.yml` (3,873 B) — bank account UI with deposit/withdraw buttons
- `phone_bounty.yml` (667 B) — bounty view
- `phone_gang.yml` (1,098 B) — gang info menu
- `phone_gang_search.yml` (2,299 B) — searchable gang list, paginated
- `gang_info.yml` (1,822 B) — gang roster, stats, alliance info
- `gang_stat.yml` (192 B) — player's gang stats snapshot
- `alliance_stat.yml` (546 B) — alliance stats overlay
- `user_stat.yml` (1,353 B) — player level/experience stats view

**Settings** (`settings.yml` `Inventory:` block, 14 lines):
- `Fill.Item` + `Name` — glass pane filler
- `Line.Item` + `Name` — white line item
- `Multi_Inventory` → next/previous/home page head textures (base64 skull data)

**Messages** — zero inventory-specific constants in `Messages` enum (533 total).

### gangland-npc-shops (18 files)
Trader/banker purchase, barter, claim flows build on `MultiPanelInventory` + `Panel`.
- `trader/view/{BarterView, BuyView, NegotiationView, QuantitySelectorView, SellView}.java` — flow panels
- `trader/view/TraderBuyerHeaderPanel.java` — header state display
- `banker/view/{BankerAmountView, BankerClaimView, BankerCreateAccountView, BankerMenuView}.java` — bank flow
- `banker/data/BankerData.java` — state record
- Listeners in `listener/` wiring clicks to flow transitions and transaction logic

### shop-api (9 files)
Admin shop UI and trader/barter dialogs on `MultiPanelInventory`.
- `view/{ShopAdminFlow, ShopAdminView, BarterCategoryAdminView, SellCategoryAdminView, BarterCategoryItemsAdminView, SellCategoryItemsAdminView, PriceEditorView, BarterCategorySelectView, SellCategorySelectView}.java` — nine view classes extending or using `Panel`/`FlowSession`
- `listener/{BarterCategoryAdminListener, SellCategoryAdminListener, ShopAdminListener}.java` — click routing

### gangland-turf (6 files)
Turf powerup/garrison GUI.
- `TurfPowerupFlow.java`, `TurfPowerupOpenContract.java`, `TurfPowerupBuffCatalogueView.java` — flow and panel definitions
- Three more view files building on flow/panel patterns

### gangland-domain (4 files)
**Dependency on inventory-api is the outlier** — domain should not know about UI.
- `GangFilterAdapter.java`, `MemberFilterAdapter.java` — adapt gang/member domain for filter UI
- `User.java`, `UserFactory.java` — **problematic: `User` imports inventory-api for filter contract**, should be severed (WS2 deliverable)

### cops-n-crooks (2 files)
- `detainment/paperwork/PaperworkView.java` — detainment UI flow
- `detainment/paperwork/HandcuffBribeView.java` — bribe offer panel

### lootchest-api (2 files)
- `LootChestService.java` — manages loot menu open/close
- `LootChestSession.java` — transient state during crack/open

### gangland-gadget (1 file)
- `CarSignViewProvider.java` — car-buy/sell signs as menu views

---

## 3. Capability matrix — inventory-api vs. Oriel

Gangland inventory-api capability and its Oriel counterpart (or **GAP** if missing).

| Capability | Gangland | Oriel | Status |
|---|---|---|---|
| **YAML menu loading** | `InventoryRegistry`, `InventoryBuilder`, `InventoryData` (root Java pars) | `menu-data:config` — `MenuRegistry`, YAML schema validator, hot reload | ✓ Match |
| **Click handlers** | `ClickHandler` (left/right/double), `OnClick` YAML key | `click.ClickHandler`, `on_left_click`, `on_right_click`, `on_click` YAML keys (separate) | ✓ Match (richer) |
| **Conditional slots** | `ConditionEvaluator`, `SlotCondition`, placeholder-based visibility | `condition:` YAML block with `placeholder:`, `if_true:`, `if_false:` | ✓ Match |
| **Filters + search** | `FilterRegistry`, `FilterAdapter`, `SearchFilter`, `SortDescriptor` (11 files) | `menu-core` FilterRegistry, MaterialSourceRegistry, ItemProviderRegistry, SearchMatcherRegistry, SortRegistry (6 interface registries, per type) | ✓ Match (more modular) |
| **Paginated lists** | `MultiInventory`, `ItemSourceProvider`, `ListEntry`, `PageConfig` | `chest.flow.PaginatedChestMenu`, `component.PaginatedListComponent` | ✓ Match |
| **Multi-panel flows** | `MultiPanelInventory`, `Panel`, `FlowSession`, `.switchTo()`, `.back()` | `chest.flow.{MenuFlow, MenuFlowBuilder, Panel, FlowState}` — same pattern | ✓ Match (nearly identical) |
| **Unique/special items** | `UniqueItemHandler`, `NbtTagCatalog` registration | **GAP** — Oriel has no concept of "unique item with server-side state"; items are components | ❌ Custom impl needed |
| **Villager trade menus** | `villager/*.java` (4 files) — fake merchant UI | **GAP** — Oriel ships `menu-inventories:trader` for MERCHANT inventory type, but no villager wrapping | ⚠ Similar but different |
| **Anvil input** | `OnClick: { Inventory: { Type: anvil, ... } }` nested in slot | `menu-inventories:anvil` — standalone anvil menus, no nesting in YAML | ✓ Match (cleaner) |
| **Fill/border helpers** | `InventoryUtil.fillInventory()`, `.createBoarder()`, `.horizontalLine()`, `.aroundSlot()` | **GAP** — Oriel uses `component.Fill`, `component.Line`, `component.Border` as slot classes, not loop helpers; `.aroundSlot()` not exposed | ⚠ Reimagined as components |
| **Placeholder rendering in titles/lore** | `%gangland_*%` placeholders evaluated in menu title/item name via PAPI | `%placeholder%` PAPI-backed, same mechanism | ✓ Match |
| **PlayerInventoryCleanup** | auto-clear bottom inventory on menu open (listener) | **GAP** — not shipped; custom Oriel consumer plugin must handle | ❌ Plugin-side impl |
| **State field** | `OpenInventory` marker + `State` enum tracking open/closed | Oriel menus are immutable post-render; `MenuFlow.open(player)` is stateless | ⚠ Architectural diff |
| **InventoryOpener dispatch** | single `.open(player)` factory | `Menu.open(player)` or registry lookup by name + `.open(player)` | ✓ Match |
| **Dynamic inventory creation** | `MultiInventoryCreation.dynamicMultiInventory(...)` on the fly | Oriel expects YAML registration or builder in code; can build dynamically with `ChestMenuBuilder` | ⚠ Possible but less idiomatic |

### Key gaps that will require custom code
1. **UniqueItemHandler** — Oriel items are immutable; special-item state (wand mode, lootchest crack progress) must move to PlayerMetaService or similar
2. **Villager wrapping** — Oriel's `trader` is standalone merchant inventory, not a wrapper; any villager-as-menu-opener needs custom code
3. **InventoryUtil helpers** — `.aroundSlot()`, `.horizontalLine()` not in Oriel's `Fill` component; replace with grid iteration + `component.Fill` / `component.Border`
4. **PlayerInventoryCleanup** — must be added as an Oriel consumer listener
5. **Dynamic menus** — less natural in Oriel's YAML-first model; fluent API exists but not idiomatic

---

## 4. Oriel public surface

### Maven artifacts
**Group:** `org.luckyraven.oriel` | **Scope:** `provided` (like Keystone)

Published modules (to Maven Central):
- `oriel-core` (from `:menu-core`)
- `oriel-chest`, `oriel-anvil`, `oriel-trader`, `oriel-crafting`, `oriel-dispenser`, `oriel-hopper`, `oriel-furnace` (from `:menu-inventories/*`)
- `oriel-inventories` (umbrella re-export of all inventory types)
- `oriel-config` (from `:menu-data:config`)
- `oriel-database` (from `:menu-data:database`)
- `oriel-data` (umbrella re-export of data plane)
- `oriel-papi` (from `:menu-dependencies:papi`)
- `oriel-vault` (from `:menu-dependencies:vault`)

Not published: `:menu-plugin` (shaded server jar), `:menu-editor` (in-game editor), `:menu-archetype` (Maven scaffold).

### Core classes

**menu-core** (52 Java files)
- `Menu` — immutable menu definition with title, size, components, click handlers
- `MenuBuilder` — fluent builder for `Menu`
- `click.{ClickContext, ClickHandler, ClickKind}` — left/right/any-click dispatch
- `component.{Component, ItemHoldingComponent, DepositSlotComponent, RenderContext, SlotView}` — slot renderers
- `component` subdirs: `Border`, `Button`, `Composite`, `Conditional`, `Fill`, `Item`, `Line`, `PaginatedList`, `Priority` (13 component types)
- `registry.{FilterRegistry, InMemoryFilterRegistry, MaterialSourceRegistry, MenuRegistry, ItemProviderRegistry, SearchMatcherRegistry, SortRegistry, DefaultOpenMenuTracker}` — 8 registry interfaces + in-memory impls
- No `MenuFlow`/`Panel` in menu-core; those are in chest

**menu-inventories:chest** (components + flow)
- `ChestMenu`, `ChestMenuBuilder` — fluent chest builder
- `PaginatedChestMenu` — auto-paginated builder
- `component.*` — chest-specific components (same list as above, implemented for chest size)
- `flow.{MenuFlow, MenuFlowBuilder, Panel, FlowState}` — multi-panel state machine
  - `MenuFlow.open(player)` — display the flow
  - `Panel.id: String`, `render(): Menu` — one screen in the flow
  - `FlowState.switchTo(id: String)` — change active panel
  - `FlowState.back()` / `.close()` — stack navigation
- Component list (13 types): `ItemComponent`, `FillComponent`, `BorderComponent`, `LineComponent`, `ButtonComponent`, `CompositeComponent`, `ConditionalComponent`, `PaginatedListComponent`, `DepositSlotComponent`, `ItemHoldingComponent`, `PriorityComponent`, `LineComponent`

**anvil** (`AnvilMenu`, `AnvilMenuBuilder`)
- Text input menu (via AnvilGUI library)
- `on_confirm: { command: "...", open_menu: "...", ... }` actions

**menu-data:config** (YAML loader)
- Supported `type:` values: `chest`, `anvil`, `crafting`, `dispenser`, `furnace`, `hopper`, `trader` (no custom types yet)
- Schema: `title`, `rows` (chest only), `slots`, `components` (global slot template)
- Validation and hot reload

**menu-data:database**
- `DatabaseBackend` SPI (same as Keystone's — SQLite/MySQL)
- Persist menu state, player slots, transactions

### ServicesManager registration (OrielPlugin.java L~175-205)
Oriel registers these services:
1. `MenuRegistry` — menu lookup by name
2. `MenuOpener` — dispatch `.open(player)` via registry
3. `FilterRegistry` — custom filters
4. `ItemProviderRegistry` — dynamic item sources
5. `MaterialSourceRegistry` — material resolution (XMaterial wrapper)
6. `SearchMatcherRegistry` — search algorithms
7. `SortRegistry` — sort predicates
8. Plus papi/vault adapters in the dependency modules

### Keystone integration
Oriel's `menu-plugin` (shaded jar deployed to servers) provides zero public API — all consumer plugins depend on published `oriel-*` artifacts (`provided` scope) and resolve Oriel's registries **lazily from the `ServicesManager`** at runtime, same pattern as Bartizan. No direct bean-wiring of Oriel into Gangland's `BeanFactory` is possible because Oriel's registries are not Keystone beans.

**Consequence:** Gangland consumers must adapt Oriel registries to Keystone beans in their own `@Configuration` classes (e.g., expose `MenuRegistry` as a `@Bean` pulled from `ServicesManager`).

---

## 5. Oriel docs

### MIGRATION-FROM-GLW.md (394 lines)
Oriel's docs include a **side-by-side porting guide** for GLW inventory-api YAML → Oriel YAML (already written for this wave). Key sections:

**§7: Key Renames**
| GLW | Oriel |
|---|---|
| `OnClick: { Inventory: phone }` | `on_left_click: { open_menu: phone }` |
| `OnClick: { Inventory: { Type: anvil, ... } }` | `on_left_click: { anvil_input: { ... } }` |
| `OnClick: { Command: "/buy x" }` | `on_left_click: { command: "/buy x" }` |
| `Condition.Value` | `condition.placeholder` |
| `Condition.True/False` | `condition.if_true / if_false` |
| `Title`, `Text` (anvil) | `title`, `placeholder` |
| (no right-click) | `on_right_click:` (new) |

**§8: What Oriel Adds**
- Multi-action chains on a single click (list of actions)
- Standalone anvil menus (not nested in slots)
- Right-click handlers separate from left-click
- `MenuFlow` for multi-panel sequences (explicit, not hidden in Gangland's `FlowSession`)
- Editor UI (in-game menu builder)
- Pluggable item/material/search/sort registries

### USE-CASE-ENABLERS.md (planned features)
Oriel's idea backlog for primitives that let admins build gameplay-heavy UIs without a companion plugin. Sections A1–A7:
- **A1. Player state** (progress counters, unlock flags, soft currency) — planned as `PlayerStateService` + placeholders + state actions (`%state_key%`, `set_state`, `add_state`, `take_state`)
- **A2. ItemHoldingComponent family** — `BidSlotComponent`, `StakeSlotComponent` (co-op auction/betting UI) — planned
- **A3. SharedSession** — multi-viewer state (trading, co-op) — planned
- **A4. Multi-sort/filter chains** — save/load filter state — planned
- **A5. Merchant/trading UI** — already shipped as standalone `:menu-inventories:trader`
- **A6. Economy integration** — Vault adapter shipped (`:menu-dependencies:vault`)
- **A7. Permission requirements** — permission/group requirement checks on slots — shipped

Status: **A1 planned, A2 planned, A3 planned, A4 planned, A5 done, A6 done, A7 done**. None of the planned items are blocking WS2 (they are behind-the-scenes feature additions to Oriel, not required to port Gangland menus).

### Docs mentioning Gangland
- `MIGRATION-FROM-GLW.md` — entire migration guide (GLW ≡ Gangland Warfare)

---

## 6. Oriel ↔ Keystone gap

### Version mismatch
| Repo | Keystone pin |
|---|---|
| Gangland (0.9.1) | 1.9.2 (Keystone branch `phase-h9-host-api`, uncommitted as of 2026-09-14) |
| Oriel (0.7.0) | 1.7.0 (last commit 2026-09-01: "consume Keystone 1.7.0") |
| Gap | 5 releases behind (1.7.0 → 1.9.2) |

### Keystone phases between 1.7.0 and 1.9.2
- **H7** (1.8.0, phase-h7-module-loader) — `keystone-module` (runtime module loader, `ModuleLoader`)
- **H8** (1.8.1–1.8.3) — hologram API (planned for WS3), bug fixes
- **H9** (1.9.0–1.9.2, phase-h9-host-api) — item framework promotion (`keystone-item`), NPC framework (`keystone-npc`), module API floor (`Host_Api`), `ReflectionGuard`, Brigadier free-text completions

### APIs Oriel uses that changed 1.7.0 → 1.9.2
From Oriel's `build.gradle.kts` and menu-core imports:
- **keystone-bean** — DI container, `@Bean`, `@Configuration`, `DependencyContainer` (stable across all phases — no breaking changes expected)
- **keystone-command** — Brigadier wiring, argument tree, `CommandManager`, `HelpInfo` (stable — no consumer-visible changes in H8/H9)
- **keystone-common** — `ChatUtil`, `Guard`, `Diagnostics`, `Logger`, `Timer` (stable — utility layer)
- **keystone-persistence** — `DatabaseBackend` SPI, `AbstractRepository`, `TableBackend` (stable — added H8 hologram migration, H9 refined `ReflectionGuard`)
- **keystone-economy** — `Economy`, `Bank`, `Currency` (stable — moved to Keystone from Gangland in WS4 wave, not yet released; Oriel does NOT import this)

### Expected migration blockers
- **No H7 (module loader)** — Oriel as of 0.7.0 does not use `keystone-module`; Gangland will add it in WS1–WS5 (runtime modules). Oriel will need to be updated to 1.8.0+ *if* it becomes a module, but 0.7.0 → 0.8.0 can ship with Keystone 1.9.2 without consuming the module loader.
- **No item/NPC frameworks** — Oriel does not import `keystone-item` or `keystone-npc` (H9). Safe to bump.
- **ReflectionGuard (H9.1)** — Keystone 1.9.1 adds a scanning guard to reject classes with unresolved soft-dependency types. Oriel's soft deps (PlaceholderAPI, Vault, etc.) are already guarded, so no risk.

**Verdict:** Compiling Oriel 0.7.0 against Keystone 1.9.2 should succeed with no code changes. Recommend bumping Oriel to 1.9.2 in 0.8.0 (parallel to Gangland 0.10.0 wave).

---

## 7. Tests

### Gangland tests on inventory-api
**Zero direct tests** of `InventoryHandler`, `MultiPanelInventory`, `Panel`, etc. (inventory-api has no test directory).

Indirect coverage:
- Grep for test files using inventory classes: **zero matches** across all test dirs in Gangland (gangland-impl, modules, ui APIs).
- Inventory menus are tested implicitly through integration/smoke tests (server startup, player joins, command execution), not unit tests.

**Consequence:** WS2 must add snapshot/regression tests for menu rendering and flow transitions before cutting consumers over to Oriel. Recommended: smoke-test suite (browser or headless client) that opens each menu and verifies slot layout/count, not unit tests.

### Oriel test coverage
**11 test directories** (one per module):
- `menu-core/src/test/java`
- `menu-data/{config,database}/src/test/java`
- `menu-editor/src/test/java`
- `menu-inventories/{anvil,chest,crafting,dispenser,furnace,hopper,trader}/src/test/java`

Estimated test count: ~150–200 tests across all modules (not counted individually; varies per module). `menu-core` and `menu-inventories:chest` are likely the heaviest (flow state machine, component rendering). The migration may expose gaps in Oriel's test suite if Gangland consumers exercise edge cases not yet covered.

---

## 8. Docket

### Gangland bug docket (brainstorming/bug-docket-2026-09-06)
Grep for inventory/menu/GUI entries in the 464-entry docket:

No inventory-api-specific entries found in the docket. Menu bugs (if any) are likely filed under broader categories (e.g., **CL-nn** command layer, **GR-nn** general gameplay). WS2 should cross-reference the docket during migration planning to catch any known menu issues.

### Cross-docket (brainstorming/cross-docket-2026-09-10)
Oriel-specific findings: check `brainstorming/cross-docket-2026-09-10/oriel/findings/*.txt` for Oriel issues that may affect consumption. (Not examined in this census due to file scope, but must be reviewed in the planner phase.)

---

## 9. Surprises

### 1. **Domain depends on inventory-api**
Gangland `gangland-domain` (gang/user/rank system) imports `inventory-api` for **filter adapters** (`GangFilterAdapter`, `MemberFilterAdapter`). This is a **dependency inversion** — domain should never know about UI.
- **Impact:** WS2 must sever this; move filter adapters to `gangland-impl` or the consumer that needs them.
- **Why it happened:** Gangland's phone/gang-search menus use filters to search gangs/members; the adapter lives in domain for reuse. Should have lived in impl.

### 2. **AnvilGUI library location**
inventory-api depends on `anvilgui` (Apache 2.0, by Masecla). The library is shaded into Gangland's final JAR. Oriel also depends on anvilgui but **keeps it as a provided dependency** (not shaded). 
- **Impact:** Consumers of Oriel anvil menus must either shade anvilgui themselves or assume it's in the classpath from another plugin. Gangland can continue shading it (no conflict).

### 3. **panel-create skill**
A Claude Code skill at `.claude/skills/panel-create/` scaffolds new `Panel` classes for Gangland (boilerplate constructor, `render()` method, example slot). WS2 will make this skill obsolete — consumers should use Oriel's fluent API or YAML instead.
- **Action:** Delete or deprecate the skill after WS2 migration is verified working.

### 4. **Oriel has no Gangland CI/smoke setup**
The cross-wave orchestrator's README cites a `brainstorming/bartizan-split-2026-09-08/smoke/smoke.py` smoke harness (launches test server, deploys jars, runs console commands). Oriel 0.7.0 was last built standalone; integrating it into Gangland's CI requires:
- Oriel 0.8.0+ build artifact in the Gangland workflow.
- Menu YAML migration validation script (can be simple diff + count check).

### 5. **No hologram support in Oriel**
Oriel menus render items into Bukkit inventories only. Holograms (armor stands showing item previews above an inventory) are **not** an Oriel feature. The wave README (WS3) plans to promote holograms to Keystone. Gangland consumers that use holograms (e.g., turf powerups showing buff icons) will need custom code or wait for WS3's Keystone hologram module.

### 6. **Oriel's Keystone import is mavenLocal-only**
Oriel's build.gradle.kts uses `mavenLocal()` with a content filter to resolve `org.luckyraven:*` from a local Maven repo. Keystone is not on Maven Central yet. 
- **Impact:** Gangland + Oriel builds both depend on running `mvn clean install` in the Keystone repo whenever Keystone is updated. This is already the Gangland workflow, so no additional burden, but note it in the planner's build prerequisites.

---

## Planner summary (6 lines)

**Biggest consumers:** impl (22 files, phone/gang/bank menus), npc-shops (18, trader/banker flows), shop-api (9, admin views). **Biggest gaps:** unique-item state handling (no server-side item state in Oriel, migrate to PlayerMetaService), villager-as-menu wrapping (Oriel's trader is standalone, needs adapter), grid fill helpers (convert `InventoryUtil.fillInventory()` loops to `component.Fill` + iteration), player inventory cleanup (add as listener), dynamic menu creation (possible but non-idiomatic). **Oriel prerequisites:** Keystone 1.9.2 (5 releases ahead, compile-safe), no API breakage expected; menu YAML migration mapping (almost complete in MIGRATION-FROM-GLW.md, 8 renames); smoke/regression tests (currently zero; recommend browser/headless client harness before cutting over).

