# Decoupling wave — 2026-09-14 (PLANNING ONLY, nothing implemented)

User request (verbatim intent): several features must stop being baked into Gangland Warfare:

| WS | Feature | Target | User's words |
|---|---|---|---|
| WS1 | Scoreboard (`gangland-ui/scoreboard-api` + impl wiring + `settings.yml` `Scoreboard:` + `scoreboard.yml`) | **Its own standalone plugin** (new sibling repo, Keystone-powered like Bartizan). Removed from Gangland settings entirely. | "The scoreboard needs to be its own plugin, thus remove it from the settings." |
| WS2 | `gangland-ui/inventory-api` (58 files, `InventoryHandler` 207 edges, `MultiPanelInventory` 143 edges) | **Replaced by Oriel** (sibling repo `E:\Programming\java\Oriel`, 0.7.0). Gangland consumes Oriel's menus/flows/registries instead of owning a menu framework. | "The inventory-api needs to be basically linked to oriel, thus using oriel features rather than being independent." |
| WS3 | Loot chests (`gangland-ui/lootchest-api` 32 files + impl manager/wand/commands/repo) and `gangland-ui/hologram-api` (3 files) | Loot chests **revamped and turned into a runtime module** (`modules/gangland-lootchest-<rev>.jar`); holograms **promoted to Keystone** so the module (and any plugin) uses Keystone's hologram service. | "Lootchests needs a revamp, since they are coupled with hologram api and they can be set as a module. Thus you can have hologram to be in keystone plugin." |
| WS4 | `gangland-ui/shop-api` (37 files: registry/model, transactions, valuation, yaml io, admin views) | **Promoted to Keystone** as a reusable shop system, not Gangland-specific. | "The shop-api can be a system that is in keystone and not specifically in gangland warfare and then we can reuse it again." |
| WS5 | Gang system (`gangland-domain` `org.luckyraven.gangland.gang.*`, 151 impl files, gang/rank commands, gang menus, gang placeholders) | **A runtime module of its own**, with turf implemented on top of it (`turf` → `Depends: [gang, civilians]`). | "The gang system can be a module by itself, and thus implemented with turfs." |
| WS6 | `gangland-api` (module API, `Host_Api 1.0`) | **Improved Bartizan-style** so most features are reachable through the api (facade on `ServicesManager`, api events, catalogs, service table doc). | "Improve the api system so that most of the features can be used over there, check bartizan plugin how it was done." |

Process the user asked for: Fable orchestrates; **Haiku** census agents → **Sonnet** planners → **Opus** reviewers.
Tools: graphify first (all four repos have a fresh `graphify-out/` as of 2026-09-14), context-mode, feature-dev
roles, ponytail stance (smallest plan that works; delete over add). **No code is written in this wave.**

## Orientation facts (orchestrator, 2026-09-14, from graphify + census commands)

### Repos and versions
| Repo | Path | Branch / rev | Graph |
|---|---|---|---|
| Gangland Warfare | `E:\Programming\java\Gangland Warfare [Cubed-GTA recoded]` | `0.9.1` (rev 0.9.1, Keystone pin 1.9.2, Bartizan pin **0.1.0** — Bartizan itself is at 0.3.0) | fresh (no java/yml newer than graph.json) |
| Keystone | `E:\Programming\java\Keystone` | `phase-h9-host-api` = 1.9.2 (modules: common, bean, command, item, persistence, module, npc, hooks, testkit, plugin) | refreshed 2026-09-14 |
| Bartizan | `E:\Programming\java\Bartizan` | `0.3.0` (`bartizan-api` 79 files + `bartizan-plugin` 115 files) | refreshed 2026-09-14 |
| Oriel | `E:\Programming\java\Oriel` | `0.7.0` (last commit 2026-09-01 "consume Keystone **1.7.0**" — behind Keystone by five releases) | fresh |

### How Bartizan did its api (the model for WS6)
- Two Maven modules only: `bartizan-api` (public model + contracts, `provided` scope for every consumer, zero NMS /
  craftbukkit / gangland symbols) and `bartizan-plugin` (runtime, never a dependency of anything).
- `BartizanApi` facade = five catalog accessors (`weapons()`, `wearables()`, `ammunition()`, `npcWeapons()`,
  `items()`), registered on Bukkit's `ServicesManager` by `WiringConfig.bartizanApi(...)`, `unregisterAll` on
  disable. Consumers resolve it **lazily on every call, never cached** (enable order is unknown).
- Direction fixed: Bartizan publishes (`BartizanApi`, `ItemVocabulary`, `WeaponRaytracer`), pulls one
  consumer-implemented interface it defines itself (`CombatEligibility`, with a safe default).
- Api carries events (`api.event.*`, 13), DTO records (`weapon.dto.*`, 21), domain types, service contracts.
- A type moves to the api only if a consumer must name it directly or another api signature needs it.
- Docs: `Bartizan/documentation/bartizan-api.md` = resolution snippet + service table + accessor table + events.
- Bartizan is NOT a Keystone module host (no `keystone-module`, no `module.yml`).

### How Oriel exposes itself (the target for WS2)
- Maven modules: `menu-core` (Menu, MenuBuilder, registries, click/args/animation/requirement),
  `menu-inventories/{chest, anvil, crafting, dispenser, furnace, hopper, trader}`, `menu-data/{config, database}`,
  `menu-plugin`, `menu-archetype` (a consumer example plugin: `ChestMenu.builder().title(..).rows(3).border(..)
  .slot(11, ItemComponent.of(..)).slot(15, ButtonComponent.of(..).onLeftClick(ctx -> ..)).build().open(player)`).
- Chest components: Border, Button, Composite, Conditional, DepositSlot, Fill, Item, Line, PaginatedList, Priority.
- `chest/flow/{MenuFlow, MenuFlowBuilder, Panel, FlowState}` — Oriel already has a multi-panel flow concept that
  mirrors Gangland's `MultiPanelInventory`/`Panel`/`FlowSession`.
- Registries published on `ServicesManager` by `OrielPlugin` (`MenuRegistry`, `MenuOpener`, item provider /
  material source / filter / search matcher / sort / source provider registries).
- `Oriel/docs/MIGRATION-FROM-GLW.md` (394 lines) is a side-by-side GLW `inventory-api` YAML → Oriel YAML guide
  (already written for exactly this move). `docs/USE-CASE-ENABLERS.md` lists shop/economy/bank primitives.
- Oriel `plugin.yml`: `depend: [Keystone]`, `softdepend: [PlaceholderAPI, NBTAPI, Vault]`.

### Keystone rules that this wave collides with (must be amended by the user's decision, not silently ignored)
- Keystone `CLAUDE.md` line 101: "Keystone holds only low-level, generic infrastructure. If a class knows about
  gangs, menus, weapons, or any product concept, it does not belong here. **Oriel owns inventory/menu code
  permanently** — none of it ever goes into Keystone. **Scoreboard/hologram code stays in Gangland.**"
  → WS3 (hologram → Keystone) and WS4 (shop → Keystone) both override this line. A shop system is a product
  concept by that definition, and `shop-api`'s six view classes sit on `inventory-api` (→ Oriel, which depends on
  Keystone, so Keystone can never depend on Oriel). Consequence: a Keystone shop module must be **headless**
  (model, registry, transactions, valuation, yaml io, events) with views in the consumer.
- Keystone shared-classloader rule: no static singleton holding per-plugin data; every consumer instantiates its
  own `BeanFactory` etc.
- Keystone floor: Spigot API 1.16, Java 17; XSeries for version-drifting enums.

### Gangland facts per workstream (packages are all `org.luckyraven.gangland.<x>`)
- **Scoreboard**: `scoreboard-api` = `Scoreboard`, `configuration/ScoreboardAddon`, `driver/DriverHandler`,
  `driver/version/DriverV{1,2,3}`, `part/{Line,StaticLine}` (+2 tests); deps `fastboard`, `viaversion-api`.
  Consumers: impl `scoreboard/ScoreboardManager` (70 lines), `bootstrap/ScoreboardLifecycleService` (58),
  `listener/player/PlayerScoreboardListener` (41), `config/FileConfig` (bean), `config/KernelConfig`
  (`scoreboard.yml` FileHandler), `config/SchedulingConfig`, `Gangland` (bStats `scoreboard_driver` chart),
  `bootstrap/ReloadPlugin.scoreboardReload`, `command/sub/ReloadCommand`, `command/sub/DownloadResourceCommand`,
  `listener/player/RemoveAccountListener`; **domain `gang/user/User` holds a `Scoreboard` field** (so
  `gangland-domain`'s pom depends on `scoreboard-api`); `Settings.getScoreboardDriver/isScoreboardEnabled`;
  `settings.yml` `Scoreboard:` (14 lines, `Enable`, `Driver: Driver_V3`); `scoreboard.yml` (108 lines).
  Placeholders render through `PlaceholderService`/PAPI (`GanglandPlaceholder` expansion in `WiringConfig`).
- **Inventory**: 58 files in `inventory-api` (`condition` 4, `filter` 11, `flow` 3, `handler` 11, `listener` 4,
  `multi` 7, `part` 5, `service` 1, `unique` 1, `util` 1, `villager` 4, root 6). Compile deps: anvilgui,
  keystone-item, gangland-core. Importers: impl 22 files, npc-shops 18, turf 6, cops 2, gadget 1, domain 7
  (`GangFilterAdapter`, `MemberFilterAdapter`, `User`, `UserFactory`, …), lootchest-api 2, shop-api 9.
  Most-imported: `part.Fill` 26, `InventoryHandler` 26, `flow.MultiPanelInventory` 24, `util.InventoryUtil` 20,
  `flow.Panel` 16, `service.InventoryRegistry` 6, `part.ButtonTags` 4, `multi.MultiInventory` 4,
  `flow.FlowSession` 4, `unique.UniqueItemHandler` 3. YAML menus: `gangland-impl/src/main/resources/inventory/`
  = alliance_stat, gang_info, gang_stat, phone, phone_banking, phone_bounty, phone_gang, phone_gang_search,
  user_stat; `settings.yml` `Inventory:` 18 lines. Zero tests in inventory-api.
- **Loot chests**: `lootchest-api` 32 files (+5 tests): `LootChestService`, `ChestCooldownManager`, `config/*`
  (loader, messages/settings providers), `data/{CrackingSession, LootChestData, LootChestSession, LootTable,
  LootTier}`, `events/**` (9), `handler/**` (9), `item/LootItemReference`, `listener/LootChestListener`. Deps:
  inventory-api, hologram-api, gangland-item, gangland-core, keystone-item/persistence. Impl side (13 files):
  `lootchest/{LootChestManager, LootChestWand, LootChestWandTag}`, `command/sub/lootchest/*` (3),
  `database/repositories/lootchest/LootChestRepository` + `tables/lootchest/LootChestTable`,
  `file/configuration/lootchest/{GanglandLootChestMessages, LootChestSettings}`, `listener/loot/{LootChestEarnGoodsListener,
  LootChestWandListener}`; beans in `config/GameplayConfig` (`hologramService` L268, `lootChestManager` L273,
  `lootChestService` L280, `lootChestLoader` L285, `initializeLootChestLoader` L314); `ItemConfig.nbtTagCatalog()`
  registers `LootChestWandTag`s. YAML: `lootchests/{loot_chests.yml, tiers.yml}`, `settings.yml` `Loot_Chest:` 34
  lines. `LootChestData` is a god node (78 edges).
- **Hologram**: 3 files, ArmorStand-based (`Hologram`, `HologramService` implements `BeanLifecycle`,
  `HologramProtectionListener` `@ListenerHandler`), imports keystone-bean + `ChatUtil`. Consumers: impl 2
  (GameplayConfig bean + one more), lootchest-api 2.
- **Shop**: `shop-api` 37 files (+8 tests): root `BarterCategory, EntryKind, SellCategory, ShopDefinition,
  ShopItemEntry, ShopRegistry`; `config/ShopUiSettings`; `event/ShopEditedEvent`; `handler/ShopEditPersistenceHandler`;
  `io/{ShopYamlReader, ShopYamlWriter}`; `listener/{BarterCategoryAdminListener, SellCategoryAdminListener,
  ShopAdminListener}`; `message/{ShopDisplayResolver, ShopMessageContract}`; `transaction/*` (11: purchase/sell/
  barter services + results/outcomes + `PaymentHandler`/`PaymentException`); `valuation/*` (4);
  `view/*` (6 admin views on `MultiPanelInventory`). Deps: keystone-bean/item/persistence/common, gangland-core,
  inventory-api, gangland-item. Importers: npc-shops 19 files, impl 9 (`config/ShopConfig.shopRegistry` L104,
  trader listeners), api 1 (`file/configuration/shop/GanglandShopDisplayResolver`). Most-imported: `ShopRegistry`
  13, `ShopDisplayResolver` 9, `ShopMessageContract` 7, `ShopItemEntry` 6, `ShopDefinition` 5, `ShopAdminFlow` 4.
- **Gang**: domain `org.luckyraven.gangland.gang.*` 40 files: `gang` 5 (`Gang, GangAlliance, GangFilterAdapter,
  GangManager, GangSettings`), `bounty` 4, `contract` 9 (`GangAllianceRepositoryContract, GangLookupContract,
  GangMessageContract, GangPermissionBridgeContract, GangSettingsContract, MemberRepositoryContract,
  PermissionRegistryContract, RankLookupContract, UserLookupContract`), `events` 7, `member` 3, `permission` 1,
  `rank` 6, **`user` 4 (`User, UserManager, Level, UserFactory` — the player record every module uses; `UserManager`
  438 edges, `User` 161)**, `vault/permission` 1, `wanted` 6. Impl: 151 files import gang.*; `command/sub/gang` 18,
  `rank` 13, `bounty` 3, `wanted` 4, `level` 7; `database/repositories/{gang 2, rank 3}` + tables;
  `listener/gang/GangMembersDamageListener`; `config/GangModuleConfig` (9 contract beans), `config/GangFilterRegistration`,
  `config/DataConfig` beans (`userManager` L78, `offlineUserManager` L91, `rankManager` L121, `gangManager` L127,
  `memberManager` L134, `wantedKillTrackers` L171, `registerGanglandPermissions` L182); placeholders in
  `data/placeholder/worker/GanglandPlaceholder`; `file/configuration/inventory/itemsource/GangItemSourceProvider`.
  Module importers of gang.*: turf 36 files (`Gang` 27, `GangLookupContract` 20, `UserLookupContract` 11, `User` 10,
  `Member` 4), mail 14 (`UserManager, GangManager, MemberManager, User, Gang, RankManager, GangPermissions`),
  cops 10 (`Wanted`, `UserManager`, `WantedKillTrackers`), gadget 8 (`UserManager`, `MemberManager`), npc-shops 6
  (`UserManager`, `User`), civilians 2. `settings.yml`: `Gang:` 29 lines, `User:` 86, `Bounty:` 21, `Wanted:` 37.
  God nodes: `UserManager` 438, `MemberManager` 157, `GangManager` 150, `Gang` 126, `RankManager` 122,
  `GangLookupContract` 85.
- **Existing seams** (documentation/module-loader.md `## Core seams`): Contributions (`CommandContribution`,
  `SignTypeContribution`, `SignViewProvider`, pulled via `container.getAllInstances`), Holders (`GanglandMoneyDropClassifier`
  /`NpcMoneyDropSource`, `BankTiers`/`BankTierView`, `WantedKillTrackers`/`WantedKillTracker`), registry injection
  (`ItemConverterRegistry`/`ItemSerializerRegistry`/`ItemRefresherRegistry`, `NbtTagCatalog`), `ItemVocabulary` SPI
  (Keystone 1.9.0, `ServicesManager`). Module descriptor keys: `Depends:` (module), `Plugins:` (plugin).
- **`gangland-api` today** (30 files): `GanglandApi.VERSION = "1.0"`, `Messages`, `Settings`, `GanglandChatUtil`,
  `Command`(+HelpInfo, command/data, command/extension), `data/economy/*`, `data/teleportation/*`,
  `events/{user,teleportation}`, `sign/{aspect,extension,type,parser}`, `item/ItemAttributes`, `TimeMessages`;
  re-exports domain/core/item/inventory/sign/shop-api at compile scope. Rule: within a major the api only adds.
  Not in the api by design: `Gangland`, `GanglandContext`, `WaypointManager`, `PlaceholderService`,
  `GanglandDatabase`, `CommandManager`, `SignManager`, `NbtTagCatalog`.
- Build: `gangland-build` shades every `org.luckyraven:*` except the six modules + `anvilgui` + `fastboard` +
  bstats. `gangland-domain` pom depends on `inventory-api` and `scoreboard-api` (must be cut in WS1/WS2).

### Orchestrator's working assumptions (to be confirmed by the user in the plan review)
- A1. Version plan follows the Bartizan precedent (core features removed with their settings = minor bump):
  Gangland **0.10.0** on a new branch off 0.9.1; Keystone **1.10.0** (new modules); Oriel **0.8.0** (Keystone
  1.9.2/1.10.0 consumption + gap fixes); the scoreboard plugin starts at **0.1.0**; `Host_Api` → **2.0**
  (removing inventory-api/shop-api/domain gang types from the api is a breaking change).
- A2. "Gang module implemented with turfs" = a separate `gangland-gang` module (id `gang`) that `turf` and `mail`
  declare in `Depends:`; the user record (`User`, `UserManager`, `Level`) stays in the core. The alternative
  (fold turf into the gang module) is recorded as an option.
- A3. Shop in Keystone = headless `keystone-shop`; the admin/trader views are rebuilt on Oriel inside the consumer.
- A4. Hologram in Keystone = a small `keystone-hologram` module (needs keystone-bean for the listener/lifecycle,
  so it cannot sit in keystone-common).
- A5. The scoreboard plugin has no Gangland dependency: it renders PAPI placeholders (`%gangland_*%` already exist)
  through FastBoard, Keystone-powered (`depend: [Keystone]`, `softdepend: [PlaceholderAPI, ViaVersion]`).

## Folder layout
- `census/WS<n>-*.md` — Haiku census (file lists, import graphs, config keys, tests, seams) per workstream.
- `plans/WS<n>-*.md` — Sonnet plans (scope, target layout, step list, seams, risks, tests, open decisions).
- `reviews/REVIEW-WS<n>.md` — Opus reviews (blockers, corrections, verdict).
- `PLAN.md` — consolidated plan (orchestrator) = the artifact's source.
