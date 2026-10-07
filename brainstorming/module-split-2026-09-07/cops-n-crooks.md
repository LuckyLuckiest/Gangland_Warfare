# Flip 1: cops-n-crooks → runtime module `copsncrooks`

**Assumes done:** only the **mail** pilot is a runtime module. `gangland-weapon`, `gangland-turf` and
`gangland-gadget` are still compile-time dependencies of `gangland-impl` and stay inside the core jar during this
flip, so cops-n-crooks' dependencies on them become `provided` and `module.yml` carries **no `Depends:`** yet.

**Module:** `gangland-features/cops-n-crooks` · id `copsncrooks` · package root `org.luckyraven.gangland.copsncrooks` ·
jar `target/modules/cops-n-crooks-0.8.4.jar` · `Depends:` now `[]`, later `[turf]` (flip 3) then `[turf, weapon]`
(flip 4) — those edits belong to the later checklists.

**Planner:** opus, 2026-09-07. **Graph:** `graphify-out/graph.json` refreshed 2026-09-07 16:28 (newer than HEAD
`d6bb33ac`, so it is fresh).

---

## 0. Summary for the scrum master

| Number | What |
|---|---|
| **101** | `gangland-impl/src/main` files that reference `org.luckyraven.gangland.copsncrooks.*` today |
| **90** | moved whole into the module |
| **11** | split (feature slice moves, core keeps a feature-free remainder) |
| **0** | kept as-is with a cops reference (the grep gate must come back empty) |
| **7** | new core classes (4 seams + 1 UI-settings extraction + 1 turf holder + 1 seam interface pair) |
| **14** | new module files (entry, descriptor, 6 configs, 2 seam impls, 2 command contributions, module test, commands.json) |
| **3** | impl tests moved into the module's `src/test` |
| **43** | `commands.json` keys cut from core into the module — `InformationManagerTest` **225 → 182** |
| **5** | YAML defaults moved into the module jar (`npc/cops.yml`, `npc/civilians.yml`, `npc/trader_traits.yml`, `npc/bank_tiers.yml`, `turf/turf_npcs.yml`) |

**Seams introduced** (all follow one pattern: core owns exactly **one** holder bean with a safe default; the module
installs a delegate from a single `@PostConstruct` — never a second bean of the same type, because
`DependencyContainer.registerInstance` walks the type hierarchy and `getInstance` returns `list.get(0)`, so two
beans of one interface would resolve non-deterministically):

1. `NpcMoneyDropSource` + the existing `GanglandMoneyDropClassifier` (core) — cop/civilian recognition for cash drops.
2. `BankTierView` + `BankTiers` (core) — bank tier caps for `/glw bank deposit`, the death penalty and placeholders.
3. `WantedKillTracker` + `WantedKillTrackers` (core, `gang.wanted`) — kill-combo and "does this NPC count for wanted".
4. `TurfNpcContracts` (in `gangland-turf`, still core) — holder for the existing `TurfNpcContract`.
5. Two `CommandContribution` beans: `parent() == "bank"` (`/glw bank menu`) and `parent() == "turf"`
   (`/glw turf powerupnpc`).

**Two cross-plan commitments the turf and gadget planners depend on** (deviating from either needs a written note to
that flip's executor):

- **Turf ("design A" in `turf.md` T0).** `config/TurfNpcsConfig.java` has **no turf-only remainder** — all its beans,
  `TurfNpcsConfigLoader`, `TurfPowerupNpcRepository`, `TurfPowerupNpcTable` and the `turf/turf_npcs.yml` default
  produce or consume `copsncrooks` types only, so **flip 1 moves all of them into the cops module** (T8, T9, T15,
  T17). `turf/turf_powerups.yml` stays with turf/core. `TurfPowerupNpcCommand` moves too and reattaches under
  `/glw turf` through a `CommandContribution` with `parent() == "turf"`; `TurfCommand` loses its `TurfPowerupManager`
  parameter and queries `CommandContributions` the way `GangCommand` does (T12). The `commands.json` key
  `turf_powerupnpc` moves with it — it is one of the 43 keys below, so the count is **225 → 182**.
  Two things flip 3 inherits from flip 1 and must carry: **`config/TurfConfig.java` will hold 21 `@Bean` methods, not
  the 20 it has today** (T8 adds `turfNpcContracts()`, the seam-4 holder), and
  `command/sub/turf/TurfSelectionResolver` will already be `public final` with a `public static resolve(...)` (T12
  widened it; flip 3 still owns moving it to `org.luckyraven.gangland.turf.command`).
- **Gadget (flip 2).** The gadget-only remainder of `config/CopsAndGadgetsConfig.java` is **renamed to
  `config/GadgetConfig.java`** in T14 (`git mv`, same `@Configuration` phase, cops beans deleted, javadoc rewritten)
  so flip 2's checklist can address it by that name.

**p0-wave-3 overlap (merge `0.8.3` before starting):** exactly **two** files this plan edits are also being edited in
`.claude/worktrees/p0-wave-3` — `gangland-impl/src/main/java/org/luckyraven/gangland/config/CopsAndGadgetsConfig.java`
(T14 — p0-wave-3 is adding a `carAccessPolicy` bean to it, which must survive the rename into `GadgetConfig`) and
`gangland-impl/src/main/java/org/luckyraven/gangland/data/placeholder/worker/GanglandPlaceholder.java` (T5).
`GameplayConfig`, `command/sub/gang/*`, `command/sub/waypoint/*`, `command/sub/debug/DebugCommand.java`,
`Messages.java`, `sign/GanglandSignInformation.java`, gadget `listener/car/*` and `message_*.yml` are **not** touched by
this flip (verified: none of them import a cops type).

**Task groups**

| Group | Tasks | Files | Gate |
|---|---|---|---|
| A | T1–T2 | 6 | expected-failure gate: reactor fails **only** in `gangland-impl` |
| B | T3–T6 | 12 | none (reactor red by design) |
| C | T7–T8 | 21 | none |
| D | T9 | 18 | none |
| E | T10 | 7 | none |
| F | T11 | 22 | none |
| G | T12 | 23 | none |
| H | T13–T15 | 12 | none |
| I | T16–T17 | 12 | **G1** — `mvn clean install -DskipTests` green + grep empty |
| J | T18 | 8 | **G2** — `mvn test` green |
| K | T19–T20 | 7 | **G3 / G4** — jars + docs, then `graphify update . --force` |

**Three biggest risks**

1. **`EntityDamageListener` (T5).** The kill-credit path threads `KillCombo` and `EntityMarkManager` through five call
   sites and three callbacks. The seam must preserve today's exact behaviour when the module is **absent**
   (`isActive() == false` ⇒ the existing `else handleWanted(...)` branches run, i.e. combo-disabled behaviour) and when
   it is **present** (identical to today). Any drift silently changes wanted-level and bounty payouts.
2. **YAML parent-first resolution (T17).** `npc/*.yml` and `turf/turf_npcs.yml` must exist at exactly those paths in
   the module jar **and no longer exist in the core jar**. If a stale copy stays in `gangland-impl/src/main/resources`,
   the parent-first `ModuleClassLoader` keeps serving the core's copy and the module's defaults are dead. The
   corresponding `fm.addFile(...)` lines in `KernelConfig` must be deleted in the same commit.
3. **Bean-type ambiguity.** `TraderSettings extends ShopUiSettings`; `TraderSettingsImpl` therefore registers under
   `ShopUiSettings` too. Core must **not** publish a `ShopUiSettings` bean — the core shop-admin views construct
   `GanglandShopUiSettings` inline (T13). Same class of hazard applies to every seam; the holder pattern is mandatory,
   not stylistic.

---

## 1. Inventory (facts, with source locations)

### 1.1 Impl files that reference the feature

Enumerated with `grep -rl "org\.luckyraven\.gangland\.copsncrooks\." gangland-impl/src/main` → **101** files.
Paths below are relative to `gangland-impl/src/main/java/org/luckyraven/gangland/`.
Target packages are relative to `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/`.

#### Commands (48)

| path | role | uses | decision | p0-w3 |
|---|---|---|---|---|
| `command/sub/cops/CopCommand.java` | command | `CopManager`, `CopSpawnManager` | **MOVE** → `copsncrooks.command.cops` | n |
| `command/sub/cops/CopListCommand.java` | command | `CopManager` | **MOVE** → `copsncrooks.command.cops` | n |
| `command/sub/cops/spawner/CopSpawnerCommand.java` | command | `CopSpawnManager` | **MOVE** → `copsncrooks.command.cops.spawner` | n |
| `command/sub/cops/spawner/CopSpawnerInfoCommand.java` | command | `CopSpawner` | **MOVE** → `copsncrooks.command.cops.spawner` | n |
| `command/sub/cops/spawner/CopSpawnerListCommand.java` | command | `CopSpawner` | **MOVE** → `copsncrooks.command.cops.spawner` | n |
| `command/sub/cops/spawner/CopSpawnerRemoveCommand.java` | command | `CopSpawnManager` | **MOVE** → `copsncrooks.command.cops.spawner` | n |
| `command/sub/cops/spawner/CopSpawnerSetCommand.java` | command | `CopSpawnManager` | **MOVE** → `copsncrooks.command.cops.spawner` | n |
| `command/sub/cops/spawner/CopSpawnerTeleportCommand.java` | command | `CopSpawner` | **MOVE** → `copsncrooks.command.cops.spawner` | n |
| `command/sub/civilians/CivilianCommand.java` | command | `CivilianService` | **MOVE** → `copsncrooks.command.civilians` | n |
| `command/sub/civilians/CivilianGroupsCommand.java` | command | `CiviliansLoader` | **MOVE** → `copsncrooks.command.civilians` | n |
| `command/sub/civilians/CivilianListCommand.java` | command | `CivilianNpcRegistry` | **MOVE** → `copsncrooks.command.civilians` | n |
| `command/sub/civilians/CivilianSpawnCommand.java` | command | `CivilianService` | **MOVE** → `copsncrooks.command.civilians` | n |
| `command/sub/civilians/CivilianSpawnGroupCommand.java` | command | `CivilianSpawnManager` | **MOVE** → `copsncrooks.command.civilians` | n |
| `command/sub/civilians/spawner/CivilianSpawnerCommand.java` | command | `CivilianSpawnManager` | **MOVE** → `copsncrooks.command.civilians.spawner` | n |
| `command/sub/civilians/spawner/CivilianSpawnerInfoCommand.java` | command | `CivilianSpawner` | **MOVE** → `copsncrooks.command.civilians.spawner` | n |
| `command/sub/civilians/spawner/CivilianSpawnerListCommand.java` | command | `CivilianSpawner` | **MOVE** → `copsncrooks.command.civilians.spawner` | n |
| `command/sub/civilians/spawner/CivilianSpawnerRemoveCommand.java` | command | `CivilianSpawnManager` | **MOVE** → `copsncrooks.command.civilians.spawner` | n |
| `command/sub/civilians/spawner/CivilianSpawnerSetCommand.java` | command | `CivilianSpawnManager` | **MOVE** → `copsncrooks.command.civilians.spawner` | n |
| `command/sub/civilians/spawner/CivilianSpawnerSetGroupCommand.java` | command | `CivilianSpawnManager` | **MOVE** → `copsncrooks.command.civilians.spawner` | n |
| `command/sub/civilians/spawner/CivilianSpawnerTeleportCommand.java` | command | `CivilianSpawner` | **MOVE** → `copsncrooks.command.civilians.spawner` | n |
| `command/sub/cuff/CuffCommand.java` | command | `DetainmentService` | **MOVE** → `copsncrooks.command.cuff` | n |
| `command/sub/cuff/UncuffCommand.java` | command | `DetainmentService` | **MOVE** → `copsncrooks.command.cuff` | n |
| `command/sub/jail/JailCommand.java` (`JailCommand.java:22`) | command | `JailRegistry`, `JailService` | **MOVE** → `copsncrooks.command.jail` | n |
| `command/sub/jail/JailCreateCommand.java` | command | `JailRegistry` | **MOVE** → `copsncrooks.command.jail` | n |
| `command/sub/jail/JailInfoCommand.java` | command | `Jail` | **MOVE** → `copsncrooks.command.jail` | n |
| `command/sub/jail/JailListCommand.java` | command | `JailRegistry` | **MOVE** → `copsncrooks.command.jail` | n |
| `command/sub/jail/JailReleaseCommand.java` | command | `ReleasePipeline`, `ReleaseReason` | **MOVE** → `copsncrooks.command.jail` | n |
| `command/sub/jail/JailRemoveCommand.java` | command | `JailRegistry` | **MOVE** → `copsncrooks.command.jail` | n |
| `command/sub/jail/JailSetExitCommand.java` | command | `JailExitRegistry` | **MOVE** → `copsncrooks.command.jail` | n |
| `command/sub/jail/JailTeleportCommand.java` | command | `JailRegistry` | **MOVE** → `copsncrooks.command.jail` | n |
| `command/sub/jail/JailThrowCommand.java` (`JailThrowCommand.java:20`) | command | `JailIntakeService` | **MOVE** → `copsncrooks.command.jail` | n |
| `command/sub/trader/TraderCommand.java` | command | `TraderManager` | **MOVE** → `copsncrooks.command.trader` | n |
| `command/sub/trader/TraderCreateCommand.java` | command | `TraderManager`, `TraderTraitRegistry` | **MOVE** → `copsncrooks.command.trader` | n |
| `command/sub/trader/TraderRemoveCommand.java` | command | `TraderManager` | **MOVE** → `copsncrooks.command.trader` | n |
| `command/sub/trader/edit/TraderEditCommand.java` | command | `TraderManager` | **MOVE** → `copsncrooks.command.trader.edit` | n |
| `command/sub/trader/edit/TraderEditNameCommand.java` | command | `TraderManager` | **MOVE** → `copsncrooks.command.trader.edit` | n |
| `command/sub/trader/edit/TraderEditShopCommand.java` | command | `TraderManager` | **MOVE** → `copsncrooks.command.trader.edit` | n |
| `command/sub/trader/edit/TraderEditTraitCommand.java` | command | `TraderTraitRegistry` | **MOVE** → `copsncrooks.command.trader.edit` | n |
| `command/sub/banker/BankerCommand.java` | command | `BankerManager` | **MOVE** → `copsncrooks.command.banker` | n |
| `command/sub/banker/BankerCreateCommand.java` | command | `BankerManager` | **MOVE** → `copsncrooks.command.banker` | n |
| `command/sub/banker/BankerEditCommand.java` | command | `BankerManager` | **MOVE** → `copsncrooks.command.banker` | n |
| `command/sub/banker/BankerEditNameCommand.java` | command | `BankerManager` | **MOVE** → `copsncrooks.command.banker` | n |
| `command/sub/banker/BankerRemoveCommand.java` | command | `BankerManager` | **MOVE** → `copsncrooks.command.banker` | n |
| `command/sub/bank/BankMenuCommand.java` (`BankMenuCommand.java:19,30`) | command | `BankerFlow.startFromPhone` | **MOVE** → `copsncrooks.command.bank` + new `BankMenuContribution` (`parent() == "bank"`) | n |
| `command/sub/bank/BankCommand.java` (`BankCommand.java:47-48,127-136`) | command | `BankTierRegistry`, `BankerFlow` | **SPLIT** — drop both ctor params; add `BankTiers` (seam 2) and `DependencyContainer`; append `contributions.createFor("bank", …)` | n |
| `command/sub/bank/BankDepositCommand.java` (`BankDepositCommand.java:39,101,209-212`) | command | `BankTier`, `BankTierRegistry` | **SPLIT** — `BankTierRegistry` → `BankTiers`; `BankTier` → `BankTierView` | n |
| `command/sub/turf/TurfPowerupNpcCommand.java` (`TurfPowerupNpcCommand.java:9,26`) | command | `TurfPowerupManager` | **MOVE** → `copsncrooks.command.turf` + new `TurfPowerupNpcContribution` (`parent() == "turf"`) | n |
| `command/sub/turf/TurfCommand.java` (`TurfCommand.java:8,126-128`) | command | `TurfPowerupManager` | **SPLIT** — drop the param, add `DependencyContainer`, append `contributions.createFor("turf", …)` | n |
| `command/sub/turf/TurfSelectionResolver.java` (`:24`, `:29`) | command helper | nothing (no cops import — **not** one of the 101) | **KEEP, visibility change only** — `final class` → `public final class`, `static resolve(...)` → `public static resolve(...)`, because T12 moves its cross-package caller `TurfPowerupNpcCommand:61` into the module. Stays in `command/sub/turf/`; flip 3 moves it | n |

#### Configuration (6)

| path | role | decision | p0-w3 |
|---|---|---|---|
| `config/CopsAndGadgetsConfig.java` (429 lines, `:91`) | config | **SPLIT + RENAME to `config/GadgetConfig.java`** — 26 cops beans move; keep only `carMessageContract()` (`:152`), `carService()` (`:401`), `jetpackService()` (`:413`) **plus `carAccessPolicy()` once `0.8.3` is merged**; `moneyDropClassifier()` (`:426`) loses its cops params and moves to `DataConfig` | **y** |
| `config/BankerConfig.java` (141 lines) | config | **MOVE** whole → `copsncrooks.config.BankerModuleConfig`; the two `permissionManager.addPermission(BankCommand.*)` lines (`:48,:51`) stay in core (moved into `DataConfig.registerGanglandPermissions()`) | n |
| `config/ShopConfig.java` (276 lines) | config | **SPLIT** — 16 trader beans → `copsncrooks.config.TraderModuleConfig`; 15 shop-api beans stay; `registerPermissions()` (`:69-73`, references `ShopViewOpenerImpl.ADMIN_PERMISSION`) moves to the module | n |
| `config/TurfNpcsConfig.java` (117 lines) | config | **MOVE** whole → `copsncrooks.config.TurfNpcsModuleConfig` — **every** bean produces or wires a `copsncrooks.npc.turf` / `copsncrooks.npc.civilian` type, so there is **no turf-only remainder**; the file is deleted from core. The `turfNpcContract()` bean (`:72`) becomes an `install()` call on seam 4 | n |
| `config/FileConfig.java` (`:5,:6,:14,:122,:127,:132`) | config | **SPLIT** — `copSettings()`, `civilianSettings()`, `civilianSpawnConfigProvider()` move; narrow the wildcard import `file.configuration.copsncrooks.*` | n |
| `config/WiringConfig.java` (`:7,:57`) | config | **SPLIT** — `ganglandPlaceholder()` takes `BankTiers` instead of `BankTierRegistry` | n |

#### Contract implementations (20)

| path | implements | decision | p0-w3 |
|---|---|---|---|
| `data/detainment/GanglandDetainmentCosts.java` | `DetainmentCostsContract` | **MOVE** → `copsncrooks.integration.detainment` | n |
| `data/detainment/GanglandDetainmentEconomyContract.java` | `DetainmentEconomyContract` | **MOVE** → `copsncrooks.integration.detainment` | n |
| `data/detainment/GanglandDetainmentSounds.java` | `DetainmentSoundContract` | **MOVE** → `copsncrooks.integration.detainment` | n |
| `data/detainment/GanglandMoneyIconProvider.java` | `MoneyIconProvider` | **MOVE** → `copsncrooks.integration.detainment` | n |
| `data/detainment/GanglandReleaseExitContract.java` | `ReleaseExitContract` | **MOVE** → `copsncrooks.integration.detainment` | n |
| `data/detainment/GanglandWantedClearContract.java` | `WantedClearContract` | **MOVE** → `copsncrooks.integration.detainment` | n |
| `data/detainment/inventory/GanglandSeizedInventoryService.java` (`:33` wires `setDataSupplier`) | `SeizedInventoryService` | **MOVE** → `copsncrooks.integration.detainment` | n |
| `file/configuration/copsncrooks/BankerSettingsImpl.java` | `BankerSettings` | **MOVE** → `copsncrooks.integration.config` | n |
| `file/configuration/copsncrooks/GanglandBankerEconomy.java` | `BankerEconomyContract` | **MOVE** → `copsncrooks.integration.config` | n |
| `file/configuration/copsncrooks/GanglandBankerMessages.java` | `BankerMessageContract` | **MOVE** → `copsncrooks.integration.config` | n |
| `file/configuration/copsncrooks/GanglandCivilianSettings.java` | `CivilianSettings` | **MOVE** → `copsncrooks.integration.config` | n |
| `file/configuration/copsncrooks/GanglandCivilianSpawnConfigProvider.java` | `SpawnConfigProvider` | **MOVE** → `copsncrooks.integration.config` | n |
| `file/configuration/copsncrooks/GanglandCopSettings.java` | `CopSettings` | **MOVE** → `copsncrooks.integration.config` | n |
| `file/configuration/copsncrooks/GanglandDetainmentMessages.java` | `DetainmentMessageContract` | **MOVE** → `copsncrooks.integration.config` | n |
| `file/configuration/copsncrooks/GanglandTraderEconomy.java` | `TraderEconomyContract` | **MOVE** → `copsncrooks.integration.config` | n |
| `file/configuration/copsncrooks/GanglandTraderMessages.java` | `TraderMessageContract` | **MOVE** → `copsncrooks.integration.config` | n |
| `file/configuration/shop/TraderSettingsImpl.java` | `TraderSettings` | **MOVE** → `copsncrooks.integration.config` | n |
| `file/configuration/turf/TurfNpcContractImpl.java` (`:32`) | `TurfNpcContract` | **MOVE** → `copsncrooks.integration.turf` | n |
| `file/configuration/turf/TurfNpcsConfigLoader.java` (`:29`) | loader | **MOVE** → `copsncrooks.integration.turf` | n |
| `file/configuration/turf/TurfPowerupOpenContractImpl.java` (`:24`) | `TurfPowerupOpenContract` | **MOVE** → `copsncrooks.integration.turf` | n |

> `file/configuration/copsncrooks/GanglandBountySettings.java` and `GanglandWantedSettings.java` are **not** in the
> 101 — they implement core `BountySettings`/`WantedSettings` and carry no cops import. They **stay in core** but the
> directory is renamed to `file/configuration/wanted/` (T16) so no `copsncrooks`-named package survives in the core jar.

#### Persistence (18)

All **MOVE** → `copsncrooks.database` (flat package, both repositories and tables), none touched by p0-wave-3.

| path | entity |
|---|---|
| `database/repositories/copsncrooks/CivilianSpawnerRepository.java` | `CivilianSpawner` |
| `database/repositories/copsncrooks/CopSpawnerRepository.java` | `CopSpawner` |
| `database/repositories/copsncrooks/DetainmentRepository.java` | `DetainedPlayer` |
| `database/repositories/copsncrooks/JailExitRepository.java` | `JailExit` |
| `database/repositories/copsncrooks/JailRepository.java` | `Jail` |
| `database/repositories/copsncrooks/SeizedInventoryRepository.java` | `SeizedInventory` |
| `database/repositories/banker/BankerRepository.java` | `BankerData` |
| `database/repositories/trader/TraderRepository.java` | `TraderData` |
| `database/repositories/turf/TurfPowerupNpcRepository.java` (`:7`, `:22`) | `TurfPowerupData` |
| `database/tables/copsncrooks/CivilianSpawnerTable.java` · `CopSpawnerTable.java` · `DetainmentTable.java` · `JailExitTable.java` · `JailTable.java` · `SeizedInventoryTable.java` | (6 tables) |
| `database/tables/banker/BankerTable.java` | |
| `database/tables/trader/TraderTable.java` | |
| `database/tables/turf/TurfPowerupNpcTable.java` (`:3`, `:10`) | |

> `database/repositories/turf/` keeps `ActiveTurfBuffRepository`, `TurfGarrisonRepository`, `TurfRepository`;
> `database/tables/turf/` keeps `ActiveTurfBuffTable`, `TurfGarrisonTable`, `TurfTable`. The `copsncrooks/`, `banker/`
> and `trader/` directories are deleted (they become empty).

#### Listeners (7)

| path | decision | p0-w3 |
|---|---|---|
| `listener/npc/CivilianDeathRewardListener.java` (`:7-9`) | **MOVE** → `copsncrooks.listener.npc` | n |
| `listener/trader/TraderBarterListener.java` (`:8-11`) | **MOVE** → `copsncrooks.listener.trader` | n |
| `listener/trader/TraderBuyListener.java` (`:8-11`) | **MOVE** → `copsncrooks.listener.trader` | n |
| `listener/trader/TraderSellListener.java` (`:8-11`) | **MOVE** → `copsncrooks.listener.trader` | n |
| `listener/player/WantedLevelListener.java` (35 lines, `KillCombo` only) | **MOVE** → `copsncrooks.listener.player` | n |
| `listener/player/EntityDamageListener.java` (292 lines; cops at `:13-15,46-47,53-54,59-60,63,114-117,143-151,161-166,176-184,187,197`) | **SPLIT** — seam 3 | n |
| `listener/player/PlayerDeathListener.java` (260 lines; cops at `:14-15,45,52,173`) | **SPLIT** — seam 2 | n |

#### Cross-cutting (2)

| path | decision | p0-w3 |
|---|---|---|
| `data/economy/GanglandMoneyDropClassifier.java` | **SPLIT** — class stays in core, loses `CopManager`/`CivilianNpcRegistry`, gains an installable `NpcMoneyDropSource` (seam 1) | n |
| `data/placeholder/worker/GanglandPlaceholder.java` (`:7-8,41,49,233-243`) | **SPLIT** — `BankTierRegistry` → `BankTiers`, `BankTier` → `BankTierView` (seam 2) | **y** |

### 1.2 Feature-side files that reference impl

**None today.** Verified: `grep -rl "org\.luckyraven\.gangland\.copsncrooks\." --include=*.java gangland-features
gangland-ui gangland-infra gangland-core gangland-compatibility gangland-build` (excluding `cops-n-crooks/` itself)
returns nothing, and `gangland-ui/shop-api` is cops-free — `TraderSettings extends ShopUiSettings`
(`cops-n-crooks/.../npc/trader/config/TraderSettings.java:7`), never the other way round. After this flip
cops-n-crooks depends on `gangland-impl` at `provided` scope, which is the sanctioned direction.

`gangland-impl/src/main/java/org/luckyraven/gangland/Gangland.java`, `plugin.yml`, `bootstrap/**`, `data/plugin/**`,
`scoreboard/**`, `inventory/**`, `sign/**`, `file/configuration/Settings.java` and `file/configuration/Messages.java`
all contain **zero** cops references (verified by grep) — nothing to do there. Cops-related *message keys* and
*settings keys* (`DETAINMENT_*`, `BANKER_*`, `TRADER_*`, `Settings.isWantedKillComboEnabled()`,
`Settings.getWantedKillCounter()`, `Settings.getTrader*()`, `Settings.getBankResetPeriodSeconds()`) stay in core
per design rule 5 — the module reads `Settings`/`Messages` directly.

### 1.3 Beans the feature contributes today

| Core config | `@Bean` method (line) | Phase | Parameters | Target |
|---|---|---|---|---|
| `CopsAndGadgetsConfig` | `civiliansLoader` (`:106`) | CONFIG | `ItemParser`, `CivilianSettings`, `FileManager` | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `entityMarkManager` (`:117`) | CONFIG | `CiviliansLoader` | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `killCombo` (`:126`) | CONFIG | `Settings` **(load-order-only param — keep it)** | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `jailRegistry` (`:131`) | CONFIG | — | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `jailService` (`:136`) | CONFIG | `JailRegistry`, `RepositoryRegistry` | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `detainmentRegistry` (`:142`) | CONFIG | `JailRegistry`, `RepositoryRegistry` | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `detainmentMessageContract` (`:148`) | CONFIG | — | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `detainmentService` (`:157`) | CONFIG | `DetainmentRegistry`, `JailService`, `DetainmentMessageContract`, `PermissionManager` | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `cuffLockRegistry` (`:174`) | CONFIG | — | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `jailExitRegistry` (`:179`) | CONFIG | — | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `jailExitService` (`:184`) | CONFIG | `JailExitRegistry`, `RepositoryRegistry` | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `detainmentCostsContract` (`:190`) | CONFIG | — | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `detainmentSoundContract` (`:194`) | CONFIG | — | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `wantedClearContract` (`:199`) | CONFIG | `@Qualifier("online") UserManager<Player>` | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `detainmentEconomyContract` (`:204`) | CONFIG | `@Qualifier("online") UserManager<Player>` | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `releaseExitContract` (`:209`) | CONFIG | `JailExitRegistry`, `WaypointManager` | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `moneyIconProvider` (`:214`) | CONFIG | `MoneyAddon` | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `seizedInventoryService` (`:219`) | CONFIG | `RepositoryRegistry` | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `paperworkItemFactory` (`:225`) | CONFIG | `DetainmentMessageContract` | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `transitService` (`:230`) | CONFIG | `DetainmentService`, `DetainmentRegistry`, … | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `releasePipeline` (`:236`) | CONFIG | `DetainmentService`, `DetainmentRegistry`, … | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `jailIntakeService` (`:247`) | CONFIG | `DetainmentService`, … | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `bribeService` (`:266`) | CONFIG | `DetainmentService`, `DetainmentRegistry`, … | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `bailService` (`:275`) | CONFIG | `DetainmentService`, `DetainmentRegistry`, … | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `sentenceService` (`:282`) | CONFIG | `DetainmentRegistry`, `DetainmentService`, … | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `breakFreeService` (`:289`) | CONFIG | `DetainmentService`, `DetainmentCostsContract`, … | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `handcuffBribeView` (`:296`) | CONFIG | `BribeService`, `DetainmentEconomyContract`, … | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `paperworkView` (`:303`) | CONFIG | `DetainmentRegistry`, `DetainmentCostsContract`, … | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `copLoader` (`:317`) | CONFIG | `ItemParser`, … | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `wantedTargetingManager` (`:328`) | CONFIG | — | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `civilianNpcRegistry` (`:333`) | CONFIG | — | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `civilianNpcFactory` (`:338`) | CONFIG | `EntityMarkManager`, … | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `copSpawnManager` (`:347`) | CONFIG | `CopLoader`, … | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `copManager` (`:359`) | CONFIG | `CopSpawnManager`, `CivilianNpcRegistry`, … | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `copService` (`:371`) | CONFIG | `CopManager`, `WantedTargetingManager` | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `civilianSpawnManager` (`:375`) | CONFIG | `CivilianNpcFactory`, … | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `civilianService` (`:386`) | CONFIG | `CiviliansLoader`, … | `CopsNCrooksModuleConfig` |
| `CopsAndGadgetsConfig` | `moneyDropClassifier` (`:426`) | CONFIG | `CopManager`, `CivilianNpcRegistry` | **stays in core** in `DataConfig`, params dropped (seam 1) |
| `BankerConfig` | all 12 beans (`:44`–`:136`) | CONFIG | see file | `BankerModuleConfig` |
| `ShopConfig` | `moodService` (`:88`), `traderMessageContract` (`:100`), `traderTraitRegistry` (`:139`), `traderTraitsLoader` (`:144`), `traderSettings` (`:160`), `traderEconomyContract` (`:167`), `barterView` (`:172`), `quantitySelectorView` (`:181`), `negotiationView` (`:186`), `traderSellView` (`:193`), `traderShopView` (`:202`), `traderModeSelectView` (`:208`), `traderFlow` (`:213`), `traderRespawnService` (`:257`), `traderManager` (`:261`), `shopViewOpener` (`:270`) | CONFIG | see file | `TraderModuleConfig` |
| `ShopConfig` | `registerPermissions()` `@PostConstruct` (`:69`) | CONFIG | — | `TraderModuleConfig` |
| `TurfNpcsConfig` | all 10 beans (`:47`–`:110`) | CONFIG | see file | `TurfNpcsModuleConfig` |
| `FileConfig` | `copSettings` (`:122`), `civilianSettings` (`:127`), `civilianSpawnConfigProvider` (`:132`) | **FILE** | — | `CopsNCrooksFileConfig` (`@Configuration(phase = Phase.FILE)`) |
| `KernelConfig` | `fileManager()` (`:167`) — 5 `fm.addFile(...)` lines (`:182`–`:185`, `:188`) | **KERNEL** | — | `CopsNCrooksYamlConfig` (`@Configuration(phase = Phase.KERNEL)`) |
| `WiringConfig` | `ganglandPlaceholder` (`:57`) | CONFIG | includes `BankTierRegistry` | **stays**, param → `BankTiers` |

**Load-order params to preserve** (memory rule *bean ordering via params*): `killCombo(Settings settings)`,
`traderSettings(@SuppressWarnings("unused") Settings settings)`, `bankerSettings(@SuppressWarnings("unused") Settings
settings, PermissionManager permissionManager)` and the three `TurfPowerupView` beans' `@SuppressWarnings("unused")
Settings settings` params — carry them across verbatim.

### 1.4 Resources

**YAML defaults to move** (from `gangland-impl/src/main/resources/` to
`gangland-features/cops-n-crooks/src/main/resources/` **at exactly the same relative path** — `FileHandler`'s
resource lookup is `directory + fileType` with forward slashes,
`Keystone/keystone-persistence/.../FileHandler.java:86-92,191,202,233-235`):

| file | registered today | loaded by |
|---|---|---|
| `npc/cops.yml` | `KernelConfig.java:182` | `CopLoader` |
| `npc/civilians.yml` | `KernelConfig.java:183` | `CiviliansLoader` |
| `npc/trader_traits.yml` | `KernelConfig.java:184` | `TraderTraitsLoader` |
| `npc/bank_tiers.yml` | `KernelConfig.java:185` | `BankTiersLoader` |
| `turf/turf_npcs.yml` | `KernelConfig.java:188` | `TurfNpcsConfigLoader` |

**Stays in core:** `turf/turf_powerups.yml` (`KernelConfig.java:187`, loaded by turf's `PowerupRegistryLoader`),
`inventory/phone_banking.yml` (`GameplayConfig.java:169` — a core inventory definition that routes to `/glw bank menu`
as a *command string*, no cops type), every `items/*`, `weapon/*`, `lootchests/*`, `settings.yml`, `scoreboard.yml`,
`message/message_*.yml`.

**`commands.json` keys to cut from `gangland-impl/src/main/resources/commands.json` into the module's own
`commands.json`** — exactly **43**:

```
bank_menu
banker  banker_create  banker_edit_name  banker_help  banker_remove
civilian_groups  civilian_help  civilian_list  civilian_spawn  civilian_spawngroup
civilian_spawner_info  civilian_spawner_list  civilian_spawner_remove  civilian_spawner_set
civilian_spawner_setgroup  civilian_spawner_teleport
cop_help  cop_list
cop_spawner_info  cop_spawner_list  cop_spawner_remove  cop_spawner_set  cop_spawner_teleport
cuff  uncuff
jail_create  jail_help  jail_info  jail_list  jail_release  jail_remove  jail_setexit
jail_teleport  jail_throw
trader  trader_create  trader_edit_name  trader_edit_shop  trader_edit_trait  trader_help  trader_remove
turf_powerupnpc
```

Core `commands.json` goes **225 → 182**.

**`module.properties`** already exists at
`gangland-features/cops-n-crooks/src/main/resources/org/luckyraven/gangland/copsncrooks/module.properties`
(content `module.name=${project.name}`) — nothing to create. Resource filtering is on globally (`pom.xml:504-509`),
so `${project.version}` in `module.yml` resolves.

### 1.5 Tests

**Impl tests that reference the feature (3 — move to `gangland-features/cops-n-crooks/src/test/java/…`):**

| from | to | notes |
|---|---|---|
| `gangland-impl/src/test/java/org/luckyraven/gangland/data/detainment/inventory/GanglandSeizedInventoryServiceTest.java` (124 lines) | `…/copsncrooks/integration/detainment/GanglandSeizedInventoryServiceTest.java` | mockito only |
| `gangland-impl/src/test/java/org/luckyraven/gangland/database/repositories/copsncrooks/DetainmentRepositoryMigrationTest.java` (214 lines) | `…/copsncrooks/database/DetainmentRepositoryMigrationTest.java` | uses `keystone-testkit` + sqlite; `@TempDir(cleanup = CleanupMode.NEVER)` already correct |
| `gangland-impl/src/test/java/org/luckyraven/gangland/database/repositories/copsncrooks/DetainmentRepositorySpiTest.java` (125 lines) | `…/copsncrooks/database/DetainmentRepositorySpiTest.java` | same |

> The planner brief also named `bootstrap/PeriodicalUpdatesTest` and `data/plugin/PluginDataCleanupServiceTest`.
> **Verified: neither references a cops type** (`grep -n -i "cop\|jail\|detain\|trader\|banker\|civilian"` matches only
> prose in comments). They stay in `gangland-impl` unchanged.

**Feature tests that already exist** (stay put):
`copsncrooks/combo/KillComboTrackerTest`, `detainment/DetainmentServiceQuitTest`,
`detainment/economy/DetainmentCostsContractTest`, `jail/JailExitRegistryTest`, `jail/JailExitServiceTest`,
`jail/JailRegistryTest`, `npc/police/state/CuffLockRegistryTest`, `npc/trader/view/BarterViewTest`,
`support/FakeRepository`.

**Tests that assert counts:** `gangland-impl/src/test/java/org/luckyraven/gangland/command/data/
InformationManagerTest.java:41` — `assertEquals(225, manager.getCommands().size(), …)` → **182**. Its second test
(`:49-60`, "merge folds a module's commands.json") is count-relative and needs no change.

Test dependencies (`junit-jupiter`, `mockito-core`, `keystone-testkit`, `sqlite-jdbc`) come from the root
`pom.xml` `<dependencies>` and are inherited by every module — **never add them to `cops-n-crooks/pom.xml`**.

### 1.6 Seams needed

All four use the same shape and are installed from **one** `@PostConstruct` in `CopsNCrooksModuleConfig`. That hook
runs inside `BeanFactory.instantiate()`, i.e. **before** `GanglandContext.runListenerPhase()` and
`runCommandPhase()` (`bootstrap/GanglandContext.java:184-190`), so every consumer below — all listeners, all commands
and one lazily-reading placeholder bean — sees the installed delegate.

| # | Name | Core package | Methods | Registered by | Consumed by | Why a seam |
|---|---|---|---|---|---|---|
| 1 | `NpcMoneyDropSource` (interface) + `GanglandMoneyDropClassifier` (existing class, becomes the holder) | `org.luckyraven.gangland.data.economy` | `@Nullable MoneyDropContext classify(LivingEntity)` on the interface; `install(NpcMoneyDropSource)` on the holder | core `@Bean` in `DataConfig`; module `@PostConstruct` installs `CopsMoneyDropSource` | `gangland-infra/gangland-item/.../listener/money/MoneyDropListener.java:29,34,37` requires a `MoneyDropClassifier` in its constructor — with no bean the listener silently fails to register and all cash drops break | the consumer is in a core infra module and must work with zero modules installed |
| 2 | `BankTierView` (interface) + `BankTiers` (holder) | `org.luckyraven.gangland.data.economy` | view: `String id()`, `String displayName()`, `BigDecimal maxBalance()`, `BigDecimal dailyDepositLimit()`, `double deathLossDiscount()`; holder: `@Nullable BankTierView tierFor(Bank)`, `install(Function<Bank, BankTierView>)` | core `@Bean` in `DataConfig`; module `@PostConstruct` | `BankDepositCommand:101,209-212`, `PlayerDeathListener:173`, `GanglandPlaceholder:233-243` | `/glw bank` and the bank placeholders are core economy; only the *tier catalogue* is a Banker-NPC concept. All three call sites already null-check the tier, so "no module" degrades to "no caps / no insurance discount / empty tier placeholders" with no new branches |
| 3 | `WantedKillTracker` (interface) + `WantedKillTrackers` (holder) | `org.luckyraven.gangland.gang.wanted` (gangland-domain) | `boolean countsForWanted(Entity)`, `void recordKill(Player, Wanted, Entity, int)`, `void resetCombo(UUID)`, `void onWantedTrigger(Consumer<Player>)`, `void onComboReset(Consumer<Player>)`, `void onVictimDeath(Consumer<UUID>)`; holder adds `boolean isActive()` and `install(WantedKillTracker)` | core `@Bean` in `DataConfig`; module `@PostConstruct` installs `KillComboWantedTracker` | `EntityDamageListener:114-117,143-151,161-166,176-184` | the listener owns bounty payout, kill counters and blood particles (all core) while `KillCombo`/`EntityMarkManager` are NPC infrastructure that must stay in cops-n-crooks (memory rule *npcs in copsncrooks*). Splitting the listener into two event handlers would risk double-counting kills |
| 4 | `TurfNpcContracts` (holder implementing the existing `TurfNpcContract`) | `org.luckyraven.gangland.turf.turfnpcs` (**gangland-turf** — still core at flip 1; flip 3 carries it along) | inherits `TurfNpcContract`'s four methods as no-ops; adds `install(TurfNpcContract)` | core `@Bean` in `config/TurfConfig.java`; module `@PostConstruct` installs `TurfNpcContractImpl` | `gangland-features/gangland-turf/.../listener/powerups/GarrisonDeployListener.java:33` (`@RequiredArgsConstructor`, field `private final TurfNpcContract npcs;` → change to `TurfNpcContracts`) | the turf listener is constructor-injected; with no bean of that type it would be skipped with a warning and garrison deployment would vanish silently |

**Command contributions** (the existing `command/extension/CommandContribution` seam, model:
`GangCommand.java:85,152`):

| contribution | `parent()` | attaches | queried by |
|---|---|---|---|
| `copsncrooks.command.bank.BankMenuContribution` | `"bank"` | `BankMenuCommand` | `BankCommand.initializeArguments()` (new `contributions.createFor("bank", …)`) |
| `copsncrooks.command.turf.TurfPowerupNpcContribution` | `"turf"` | `TurfPowerupNpcCommand` | `TurfCommand.initializeArguments()` (new `contributions.createFor("turf", …)`) |

---

## 2. Ordered tasks

> **Read this first.** M1 (the poms) comes first on purpose: it turns the compiler into the work list. From the end of
> T1 until the end of **T17** the reactor is **red**, and that is expected — do not "fix" a compile error by putting a
> cops import back into `gangland-impl`. The only intermediate gate is the *expected-failure* gate at the end of
> group A. The first green build is **G1** after T17.
>
> **Line numbers are as of `d6bb33ac`; if a file was touched by the `0.8.3` merge, locate by symbol and record the
> drift in §7.**

---

### Group A — poms and module entry

#### T1 — Pom flips (M1)  (group A, 3 files)

- **Do:**
  1. `gangland-impl/pom.xml`: delete the `<dependency>` block whose `<artifactId>` is `cops-n-crooks` (around
     line 89). Leave `gangland-weapon`, `gangland-turf`, `gangland-gadget`, `shop-api`, `inventory-api` untouched.
  2. `gangland-features/cops-n-crooks/pom.xml`:
     - add `<scope>provided</scope>` to the existing `gangland-weapon` and `gangland-turf` dependencies;
     - add a `keystone-module` dependency (no `<version>` — managed by the root pom) and a `keystone-command`
       dependency (the moved `Command`/`SubArgument` subclasses need it);
     - add, as the last dependency, with the same comment mail carries:
       ```xml
       <!-- The host (core) jar. Provided: at runtime this module is loaded from plugins/Gangland_Warfare/modules/
            by Keystone's ModuleLoader, whose parent is the host plugin's classloader, so every impl class
            resolves from there. Dependency direction is module -> core; core never names a cops type. -->
       <dependency>
           <groupId>org.luckyraven</groupId>
           <artifactId>gangland-impl</artifactId>
           <scope>provided</scope>
       </dependency>
       ```
     - do **not** add any test dependency (root pom `<dependencies>` already supplies junit/mockito/testkit/sqlite).
  3. `gangland-build/pom.xml`, three edits mirroring `gangland-mail`:
     - `<artifactSet><excludes>`: add `<exclude>org.luckyraven:cops-n-crooks</exclude>` beside the mail one;
     - `maven-dependency-plugin` execution `copy-runtime-modules`: add an `<artifactItem>` for
       `org.luckyraven:cops-n-crooks:${project.version}`;
     - `<dependencies>`: add `cops-n-crooks` with `<version>${project.parent.version}</version>` and
       `<scope>provided</scope>`.
- **Why:** the compiler becomes the authoritative work list for everything that follows.
- **Done when:** `mvn clean install -DskipTests 2>&1 | tee /tmp/flip1-worklist.txt` fails, and every `[ERROR]` line
  names a file under `gangland-impl/src/main/java/`. Save that file — it is the checklist for T5–T17.
  `grep -c "ERROR.*gangland-impl" /tmp/flip1-worklist.txt` > 0 and
  `grep "ERROR.*\.java" /tmp/flip1-worklist.txt | grep -v "gangland-impl" | wc -l` == 0.
- **Watch out:** `cops-n-crooks` now builds **after** `gangland-impl` in the reactor, so it will not build at all
  until impl compiles. That is fine. Do not reorder `<modules>` in `gangland-features/pom.xml` — Maven sorts by
  dependency, not by declaration order.

#### T2 — Module entry point and descriptor (M2)  (group A, 3 files)

- **Do:** in `gangland-features/cops-n-crooks/src/main/`:
  1. `java/org/luckyraven/gangland/copsncrooks/CopsNCrooksModule.java` — modelled on
     `gangland-features/gangland-mail/src/main/java/org/luckyraven/gangland/mail/MailModule.java`:
     ```java
     @CustomLog
     public final class CopsNCrooksModule implements KeystoneModule {

         public static final String LISTENER_PACKAGE   = "org.luckyraven.gangland.copsncrooks.listener";
         public static final String COMMAND_PACKAGE    = "org.luckyraven.gangland.copsncrooks.command";
         public static final String REPOSITORY_PACKAGE = "org.luckyraven.gangland.copsncrooks.database";

         @Override
         public void configure(ModuleRegistrar registrar) {
             registrar.configuration(CopsNCrooksYamlConfig.class)
                      .configuration(CopsNCrooksFileConfig.class)
                      .configuration(CopsNCrooksModuleConfig.class)
                      .configuration(BankerModuleConfig.class)
                      .configuration(TraderModuleConfig.class)
                      .configuration(TurfNpcsModuleConfig.class)
                      .listenerPackage(LISTENER_PACKAGE)
                      .commandPackage(COMMAND_PACKAGE)
                      .repositoryPackage(REPOSITORY_PACKAGE);
         }

         @Override
         public void onEnabled(ModuleContext context) {
             log.info("Cops-n-crooks module {} enabled", context.module().descriptor().version());
         }

         @Override
         public void onDisabled() {
             log.debug("Cops-n-crooks module disabled");
         }
     }
     ```
     (The six config classes are created in T13–T16; the file will not compile until then — expected.)
  2. `resources/module.yml` at the jar root:
     ```yaml
     # Keystone module descriptor - read by Gangland's ModuleLoader from plugins/Gangland_Warfare/modules/.
     Id: copsncrooks
     Name: Cops N Crooks
     Version: ${project.version}
     Main: org.luckyraven.gangland.copsncrooks.CopsNCrooksModule
     Host_Api: 0.8
     Artifact: org.luckyraven:cops-n-crooks
     ```
     **No `Depends:` key** — weapon and turf are still inside the core jar at this flip.
  3. `resources/commands.json` — start as `{}`; T17 fills it.
- **Why:** M2; the module must declare its packages before any move can be verified.
- **Done when:** the three files exist; `module.properties` is already present at
  `resources/org/luckyraven/gangland/copsncrooks/module.properties` (verify, do not recreate).
- **Watch out:** `module.yml` uses house YAML style — block maps, `Capitalized_Underscore_Separated` keys, no inline
  `{}`. Resource filtering is enabled globally so `${project.version}` is substituted at build time.

---

### Group B — core seams

*(No compile gate; the reactor stays red.)*

#### T3 — Seam 1: `NpcMoneyDropSource` (group B, 2 files)

- **Do:**
  1. New `gangland-impl/src/main/java/org/luckyraven/gangland/data/economy/NpcMoneyDropSource.java`:
     ```java
     public interface NpcMoneyDropSource {
         /** @return the NPC classification for {@code entity}, or {@code null} when it is not a managed NPC. */
         @Nullable MoneyDropContext classify(LivingEntity entity);
     }
     ```
  2. Rewrite `data/economy/GanglandMoneyDropClassifier.java`: remove the `CopManager` and `CivilianNpcRegistry`
     imports and fields and the `@RequiredArgsConstructor`; add a no-arg constructor, a
     `private volatile NpcMoneyDropSource npcSource;` field and
     `public void install(NpcMoneyDropSource source) { this.npcSource = source; }`. New `classify` body:
     ```java
     @Override
     public MoneyDropContext classify(LivingEntity entity) {
         if (entity instanceof Player) return MoneyDropContext.PLAYER;

         NpcMoneyDropSource source = this.npcSource;
         if (source != null) {
             MoneyDropContext context = source.classify(entity);
             if (context != null) return context;
         }
         return MoneyDropContext.MOB;
     }
     ```
- **Why:** keeps a single `MoneyDropClassifier` bean in the container so `MoneyDropListener` always constructs.
- **Done when:** `grep -c copsncrooks gangland-impl/src/main/java/org/luckyraven/gangland/data/economy/GanglandMoneyDropClassifier.java` == 0.
- **Watch out:** method braces on their own lines (house rule); keep the class name and package so nothing else
  needs touching.

#### T4 — Seam 2: `BankTierView` + `BankTiers` (group B, 2 files)

- **Do:** two new files in `gangland-impl/src/main/java/org/luckyraven/gangland/data/economy/`:
  1. `BankTierView.java` — five accessors named **exactly** `id()`, `displayName()`, `maxBalance()`,
     `dailyDepositLimit()`, `deathLossDiscount()` with types `String, String, BigDecimal, BigDecimal, double`
     (they match the `BankTier` record's component accessors verbatim, so T13 only adds `implements BankTierView`).
  2. `BankTiers.java`:
     ```java
     public final class BankTiers {

         private volatile Function<Bank, BankTierView> lookup;

         public void install(Function<Bank, BankTierView> lookup) {
             this.lookup = lookup;
         }

         /** @return the tier for {@code bank}, or {@code null} when no tier catalogue is installed. */
         @Nullable
         public BankTierView tierFor(Bank bank) {
             Function<Bank, BankTierView> current = this.lookup;
             if (current == null || bank == null) return null;
             return current.apply(bank);
         }
     }
     ```
     (`Bank` is `org.luckyraven.keystone.economy.bank.Bank`.)
- **Why:** three core consumers need bank tier numbers without naming a Banker-NPC type.
- **Done when:** both new files exist and
  `grep -c copsncrooks gangland-impl/src/main/java/org/luckyraven/gangland/data/economy/BankTierView.java` == 0 and
  `grep -c copsncrooks gangland-impl/src/main/java/org/luckyraven/gangland/data/economy/BankTiers.java` == 0.
  Do **not** try to compile here — the reactor is red by design from T1 until G1; compilation of these two files is
  verified at gate G1.

#### T5 — Rewire the three `BankTier` consumers (group B, 4 files) — **p0-wave-3 overlap**

- **Do:**
  1. `command/sub/bank/BankDepositCommand.java`: replace imports `…copsncrooks.npc.banker.tier.BankTier` /
     `BankTierRegistry` with `…data.economy.BankTierView` / `BankTiers`; field `BankTierRegistry tierRegistry` →
     `BankTiers bankTiers` (constructor param at `:43` likewise); `:101` `BankTier tier = resolveTier(bank);` →
     `BankTierView tier = bankTiers.tierFor(bank);`; delete the private `resolveTier` method (`:209-213`). Every
     other line is unchanged — `tier.maxBalance()`, `tier.dailyDepositLimit()` and the existing `tier == null`
     branches keep working.
  2. `listener/player/PlayerDeathListener.java`: same import/field/param swap (`:14-15,45,52`); `:170-176`
     `bankInsuranceDiscount` becomes
     ```java
     private double bankInsuranceDiscount(User<Player> user) {
         Bank bank = user.getBank();
         if (bank == null) return 0D;
         BankTierView tier = bankTiers.tierFor(bank);
         return tier == null ? 0D : tier.deathLossDiscount();
     }
     ```
  3. `data/placeholder/worker/GanglandPlaceholder.java` (**p0-wave-3 edits this file**): same import/field/param
     swap (`:7-8,41,49`); `:233-234` becomes `BankTierView tier = bankTiers.tierFor(bank);` — the following
     `tier == null ? … :` expressions at `:236-243` are unchanged.
  4. `config/WiringConfig.java`: `:7` import `BankTierRegistry` → `org.luckyraven.gangland.data.economy.BankTiers`;
     the `ganglandPlaceholder(...)` bean (`:57`) takes `BankTiers` where it took `BankTierRegistry`.
- **Why:** removes 4 of the 101 cops references.
- **Done when:** `grep -n "BankTier\b\|BankTierRegistry" gangland-impl/src/main/java/org/luckyraven/gangland/command/sub/bank/BankDepositCommand.java gangland-impl/src/main/java/org/luckyraven/gangland/listener/player/PlayerDeathListener.java gangland-impl/src/main/java/org/luckyraven/gangland/data/placeholder/worker/GanglandPlaceholder.java gangland-impl/src/main/java/org/luckyraven/gangland/config/WiringConfig.java` returns nothing.
- **Watch out:** **`GanglandPlaceholder.java` is being edited by the `p0-wave-3` session in
  `.claude/worktrees/p0-wave-3`.** The scrum master must merge `0.8.3` into `0.8.4` before this task starts, or the
  merge will conflict on the constructor parameter list.

#### T6 — Seam 3: `WantedKillTracker` + `WantedKillTrackers`, and `EntityDamageListener` (group B, 4 files)

- **Do:**
  1. New `gangland-infra/gangland-domain/src/main/java/org/luckyraven/gangland/gang/wanted/WantedKillTracker.java`:
     ```java
     public interface WantedKillTracker {
         boolean countsForWanted(Entity victim);
         void recordKill(Player killer, Wanted wanted, Entity victim, int resetAfterSeconds);
         void resetCombo(UUID victimId);
         void onWantedTrigger(Consumer<Player> handler);
         void onComboReset(Consumer<Player> handler);
         void onVictimDeath(Consumer<UUID> handler);
     }
     ```
  2. New `…/gang/wanted/WantedKillTrackers.java` — the holder. Fields: `volatile WantedKillTracker delegate;` plus
     `Consumer<Player> wantedTrigger, comboReset; Consumer<UUID> victimDeath;`.
     - `public boolean isActive() { return delegate != null; }`
     - `public void install(WantedKillTracker tracker)` — assigns, then **replays** any handler already stored onto
       the new delegate (so install-order never matters).
     - `countsForWanted` → `delegate != null && delegate.countsForWanted(victim)`.
     - `recordKill` / `resetCombo` → no-op when `delegate == null`.
     - each `on*` → store the handler and, if a delegate exists, forward it.
  3. `gangland-impl/src/main/java/org/luckyraven/gangland/listener/player/EntityDamageListener.java`:
     - delete imports `…copsncrooks.combo.KillCombo`, `…copsncrooks.events.combo.KillComboEvent`,
       `…copsncrooks.npc.entity.EntityMarkManager` (`:13-15`); import `…gang.wanted.WantedKillTrackers`;
     - replace fields `entityMarkManager` + `killCombo` (`:46-47`) with a single
       `private final WantedKillTrackers wantedKills;` and the two constructor params (`:53-54`) with one;
     - `:114` `entityMarkManager.countsForWanted(deadPlayer)` → `wantedKills.countsForWanted(deadPlayer)`;
       `:161` `!entityMarkManager.countsForWanted(victim)` → `!wantedKills.countsForWanted(victim)`;
     - **every** `Settings.isWantedKillComboEnabled()` guard (`:115`, `:143`, `:149`, `:164`) becomes
       `wantedKills.isActive() && Settings.isWantedKillComboEnabled()`. This is what preserves today's behaviour with
       the module absent: the existing `else handleWanted(...)` / `else` branches take over exactly as they do when
       the combo setting is off;
     - `killCombo.recordKill(…)` → `wantedKills.recordKill(…)` (3 sites: `:116`, `:150`, `:165`);
       `killCombo.resetCombo(…)` → `wantedKills.resetCombo(…)` (`:144`);
     - `setupKillComboCallbacks()` (`:176-185`) becomes
       ```java
       private void setupKillComboCallbacks() {
           wantedKills.onWantedTrigger(this::onKillComboWantedTrigger);
           wantedKills.onComboReset(this::onKillComboReset);
           wantedKills.onVictimDeath(this::onPlayerDeathResetWanted);
       }
       ```
     - change `onKillComboWantedTrigger(KillComboEvent event)` (`:187`) and `onKillComboReset(KillComboEvent event)`
       (`:197`) to take a `Player player` directly and drop the `event.getPlayer()` lines — those are the **only**
       members of `KillComboEvent` either method reads. `onPlayerDeathResetWanted(UUID)` is unchanged.
- **Why:** the largest single core⇄feature entanglement; a seam is cheaper and safer than splitting the listener.
- **Done when:** `grep -c copsncrooks gangland-impl/src/main/java/org/luckyraven/gangland/listener/player/EntityDamageListener.java` == 0, and reading the diff shows every previously-`Settings.isWantedKillComboEnabled()`-guarded branch now also requires `wantedKills.isActive()`.
- **Watch out:** `gangland-domain` already depends on `spigot-api` (it uses `Player`/`OfflinePlayer` in
  `UserManager`), so `Entity`/`Player` in the interface is fine. Do **not** move `Wanted` — it stays in
  `gang.wanted`.

---

### Group C — contract implementations

#### T7 — Move the detainment and config contract impls (group C, 17 files)

- **Do:** `git mv` each file, then fix its `package` line and every import of it. Target root
  `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/`:
  - `data/detainment/GanglandDetainmentCosts.java`, `GanglandDetainmentEconomyContract.java`,
    `GanglandDetainmentSounds.java`, `GanglandMoneyIconProvider.java`, `GanglandReleaseExitContract.java`,
    `GanglandWantedClearContract.java`, `data/detainment/inventory/GanglandSeizedInventoryService.java`
    → `integration/detainment/` (package `org.luckyraven.gangland.copsncrooks.integration.detainment`).
  - `file/configuration/copsncrooks/BankerSettingsImpl.java`, `GanglandBankerEconomy.java`,
    `GanglandBankerMessages.java`, `GanglandCivilianSettings.java`, `GanglandCivilianSpawnConfigProvider.java`,
    `GanglandCopSettings.java`, `GanglandDetainmentMessages.java`, `GanglandTraderEconomy.java`,
    `GanglandTraderMessages.java`, and `file/configuration/shop/TraderSettingsImpl.java`
    → `integration/config/` (package `org.luckyraven.gangland.copsncrooks.integration.config`).
  - Delete the now-empty `gangland-impl/.../data/detainment/` (including `inventory/`) directory.
- **Why:** M4 — contract implementations move, the interfaces stay in cops-n-crooks as the test seam.
- **Done when:** `ls gangland-impl/src/main/java/org/luckyraven/gangland/data/detainment` fails (directory gone) and
  `ls gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/integration/config | wc -l`
  == 10.
- **Watch out:** `GanglandSeizedInventoryService:33` calls `repository.setDataSupplier(cache::values)` — that call
  must survive the move verbatim (memory rule *repository data supplier*), otherwise autosave throws
  `No data supplier set for repository: SeizedInventoryRepository`.

#### T8 — Move the turf-NPC glue and leave the core turf remainder (group C, 4 files)

- **Do:**
  1. `git mv` `file/configuration/turf/TurfNpcContractImpl.java`, `TurfNpcsConfigLoader.java`,
     `TurfPowerupOpenContractImpl.java` → `copsncrooks/integration/turf/` (package
     `org.luckyraven.gangland.copsncrooks.integration.turf`). `file/configuration/turf/` keeps only
     `GanglandTurfMessages.java` and `GanglandTurfSounds.java`.
  2. New `gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/turfnpcs/TurfNpcContracts.java`
     — seam 4: `implements TurfNpcContract`, all four methods no-op when `delegate == null`, plus
     `public void install(TurfNpcContract delegate)`.
  3. `gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/listener/powerups/
     GarrisonDeployListener.java:33`: change the field type from `TurfNpcContract` to `TurfNpcContracts` (the class
     uses `@RequiredArgsConstructor`, so the constructor follows automatically).
  4. **Register the holder as a core bean** — `gangland-impl/src/main/java/org/luckyraven/gangland/config/
     TurfConfig.java`: add `import org.luckyraven.gangland.turf.turfnpcs.TurfNpcContracts;` and, beside the other
     turf beans,
     ```java
     /**
      * The turf → NPC bridge. Always present so GarrisonDeployListener always constructs; inert until the
      * copsncrooks module installs TurfNpcContractImpl into it (see documentation/module-loader.md, "Core seams").
      */
     @Bean
     public TurfNpcContracts turfNpcContracts() {
         return new TurfNpcContracts();
     }
     ```
     This is the **only** producer of the type — T15 deletes the old `TurfNpcsConfig.turfNpcContract()` bean, and
     T13's `installCoreSeams()` calls `context.get(TurfNpcContracts.class).install(...)`, which would NPE without it.
- **Why:** the turf module keeps working with zero modules installed; garrison/Quartermaster calls become no-ops
  instead of a failed listener registration.
- **Done when:** `grep -rn "copsncrooks" gangland-impl/src/main/java/org/luckyraven/gangland/file/configuration/turf/`
  returns nothing, and
  `grep -c "^	@Bean" gangland-impl/src/main/java/org/luckyraven/gangland/config/TurfConfig.java` == **21**
  (20 today + `turfNpcContracts()`).
- **Watch out:** flip 3 (`turf.md` T7) moves "every `@Bean` method in `TurfConfig`" into the turf module — it must
  carry **21**, not the 20 its table enumerates. This is flagged in §0 and §6.
- **Watch out:** the field type must be the **concrete holder**, not the interface — the module never registers a
  second `TurfNpcContract` bean (it installs into the holder), and naming the concrete type makes that impossible
  to get wrong later.

---

### Group D — persistence

#### T9 — Move repositories and tables (M5) (group D, 18 files)

- **Do:** `git mv` all 18 files listed in §1.1 "Persistence" into
  `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/database/` (one flat package,
  `org.luckyraven.gangland.copsncrooks.database`). Fix each `package` line, each cross-import (repositories import
  their table), and every import site in the module (`BankerManager`, `TraderManager`, `TurfPowerupManager`,
  `DetainmentRegistry`, `JailService`, `JailExitService`, `EntitySpawner` resolve their repositories through
  `RepositoryRegistry`, so most sites are the `@Bean` methods moved in group H).
  Delete the emptied directories `database/repositories/copsncrooks/`, `database/repositories/banker/`,
  `database/repositories/trader/`, `database/tables/copsncrooks/`, `database/tables/banker/`,
  `database/tables/trader/`; delete only `TurfPowerupNpcRepository.java` / `TurfPowerupNpcTable.java` from
  `database/repositories/turf/` and `database/tables/turf/` (the other three files in each stay).
- **Why:** M5; `DatabaseConfig.java:80-86` already scans every module's declared repository package through the
  module classloader, so the tables join the same schema pass.
- **Done when:** `ls gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/database/*.java | wc -l` == 18 and
  `grep -rn "copsncrooks" gangland-impl/src/main/java/org/luckyraven/gangland/database/` returns nothing.
- **Watch out:** `@Repository` annotations and the `AbstractRepository<T>` type parameters are unchanged — only the
  package moves. `RepositoryRegistry` is keyed by entity class, so no two moved repositories may share an entity
  type (they don't).

---

### Group E — listeners

#### T10 — Move the feature listeners (M6) (group E, 7 files)

- **Do:** `git mv` into `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/listener/`:
  - `listener/npc/CivilianDeathRewardListener.java` → `listener/npc/` (package `…copsncrooks.listener.npc`);
  - `listener/trader/TraderBarterListener.java`, `TraderBuyListener.java`, `TraderSellListener.java`
    → `listener/trader/` (package `…copsncrooks.listener.trader`);
  - `listener/player/WantedLevelListener.java` → `listener/player/` (package `…copsncrooks.listener.player`).
  Delete the emptied `gangland-impl/.../listener/npc/` and `listener/trader/` directories; `listener/player/` keeps
  `EntityDamageListener` and `PlayerDeathListener` (already rewired in T5/T6) plus its other members.
- **Why:** M6; `GanglandContext.runListenerPhase()` (`:213-219`) scans each module's declared listener packages
  through the module classloader.
- **Done when:** `grep -rn "org\.luckyraven\.gangland\.copsncrooks\." gangland-impl/src/main/java/org/luckyraven/gangland/listener/` returns nothing.
- **Watch out:** keep `@ListenerHandler` and every `@EventHandler(priority = …)` exactly as-is — the trader listeners
  run at `EventPriority.NORMAL` and the civilian one at `NORMAL, ignoreCancelled = true`. Listener packages must
  stay under `copsncrooks.listener.<sub>` (memory rule *listener package*).

---

### Group F — commands, part 1

#### T11 — Move the cops, civilians and cuff command trees (M7) (group F, 22 files)

- **Do:** `git mv` into
  `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/command/`:
  - `command/sub/cops/` (2 files) → `command/cops/`, `command/sub/cops/spawner/` (6) → `command/cops/spawner/`;
  - `command/sub/civilians/` (5) → `command/civilians/`, `command/sub/civilians/spawner/` (7)
    → `command/civilians/spawner/`;
  - `command/sub/cuff/` (2) → `command/cuff/`.
  Fix `package` lines and the intra-tree imports (`CopCommand` imports `CopSpawnerCommand`, `CivilianCommand`
  imports `CivilianSpawnerCommand`).
- **Why:** M7 — top-level `/glw` commands are scanned from the module's `commandPackage`.
- **Done when:** `ls gangland-impl/src/main/java/org/luckyraven/gangland/command/sub/ | grep -E "^(cops|civilians|cuff)$"` returns nothing.
- **Watch out:** these classes extend `org.luckyraven.gangland.command.Command` /
  `org.luckyraven.keystone.command.argument.SubArgument` — both resolve from the host jar at `provided` scope. The
  `@CommandHandler` annotation stays on the top-level classes only (`CopCommand`, `CivilianCommand`, `CuffCommand`,
  `UncuffCommand`); the sub-arguments keep package-private visibility where they have it, which is fine because the
  package moves as a unit.

---

### Group G — commands, part 2

#### T12 — Move jail / trader / banker and wire the two contributions (M7) (group G, 23 files)

- **Do:**
  1. `git mv` → `copsncrooks/command/`: `command/sub/jail/` (9) → `command/jail/`;
     `command/sub/trader/` (3) → `command/trader/` and `command/sub/trader/edit/` (4) → `command/trader/edit/`;
     `command/sub/banker/` (5) → `command/banker/`.
  2. `git mv` `command/sub/bank/BankMenuCommand.java` → `copsncrooks/command/bank/BankMenuCommand.java`
     (package `org.luckyraven.gangland.copsncrooks.command.bank`); make the class and constructor `public`.
     New `copsncrooks/command/bank/BankMenuContribution.java`:
     ```java
     public final class BankMenuContribution implements CommandContribution {

         private final Gangland   gangland;
         private final BankerFlow bankerFlow;

         public BankMenuContribution(Gangland gangland, BankerFlow bankerFlow) { … }

         @Override
         public String parent() {
             return "bank";
         }

         @Override
         public List<Argument> create(Tree<Argument> tree, Argument parent) {
             return List.of(new BankMenuCommand(gangland, tree, parent, bankerFlow));
         }
     }
     ```
  3. `git mv` `command/sub/turf/TurfPowerupNpcCommand.java` → `copsncrooks/command/turf/TurfPowerupNpcCommand.java`
     (package `org.luckyraven.gangland.copsncrooks.command.turf`); make the class and constructor `public`. New
     `copsncrooks/command/turf/TurfPowerupNpcContribution.java` with `parent()` returning `"turf"`, constructed with
     whatever `TurfPowerupNpcCommand`'s constructor needs today (`Gangland`, `TurfManager`, `TurfPowerupManager`,
     the selection resolver — copy the argument list from `TurfCommand.java:126-128`).
     **Widen the resolver it calls.** `TurfPowerupNpcCommand.java:61` calls
     `TurfSelectionResolver.resolve(sender, turfs, selections, messages)`, and
     `gangland-impl/src/main/java/org/luckyraven/gangland/command/sub/turf/TurfSelectionResolver.java:24` is
     package-private `final class` with a package-private `static @Nullable Turf resolve(...)` at `:29`. Once the
     caller lives in another package that no longer compiles. Change the class to `public final class` and
     `resolve(...)` to `public static @Nullable Turf resolve(...)`. Leave the private constructor alone, and
     **leave the file in `command/sub/turf/`** — the other nine callers
     (`TurfBuffCommand`, `TurfDeleteCommand`, `TurfGarrisonCommand`, `TurfIncomeCommand`, `TurfInfoCommand`,
     `TurfSetOwnerCommand`, `TurfStatusCommand`, `TurfTpCommand`) are core turf commands that stay until flip 3;
     `turf.md` T3 moves the resolver to `org.luckyraven.gangland.turf.command` then.
  4. `command/sub/bank/BankCommand.java`: delete the `BankTierRegistry` and `BankerFlow` imports (`:8-9`) and the
     matching fields/params (`:47-48,:53-54,:59-60`); add `BankTiers bankTiers` (passed to `BankDepositCommand`) and
     `DependencyContainer container`; add `private final CommandContributions contributions;` initialised with
     `CommandContributions.from(container)` (model: `GangCommand.java:85`). In `initializeArguments()` delete the
     `BankMenuCommand menu = …` line and the `arguments.add(menu);` line, and append
     `arguments.addAll(contributions.createFor("bank", getArgumentTree(), getArgument()));` where `menu` used to sit.
     Add a javadoc line documenting that `bank` is a contribution path (mirroring `CommandContribution`'s doc).
  5. `command/sub/turf/TurfCommand.java`: delete the `TurfPowerupManager` import (`:8`) and its constructor param;
     add `DependencyContainer container` + `CommandContributions.from(container)`; delete the
     `TurfPowerupNpcCommand powerupNpc = …` construction (`:126-128`) and its `arguments.add(powerupNpc);`, and
     append `arguments.addAll(contributions.createFor("turf", getArgumentTree(), getArgument()));` in the same
     position.
- **Why:** M7 — sub-arguments under a core command must arrive through `CommandContribution`.
- **Done when:** `grep -rn "org\.luckyraven\.gangland\.copsncrooks\." gangland-impl/src/main/java/org/luckyraven/gangland/command/` returns nothing.
- **Watch out:** `CommandContributions.from(container)` is a **snapshot** taken in the command constructor — that is
  safe because commands are built in the COMMAND phase, after every module bean exists
  (`GanglandContext.java:245-259`). Keep the ordering of `arguments.add(...)` calls otherwise unchanged: the
  argument order decides `/glw bank` and `/glw turf` tab-completion order.

---

### Group H — configurations and remaining core cleanup

#### T13 — Module configs: `CopsNCrooksModuleConfig` + `BankerModuleConfig` (group H, 3 files)

- **Do:** in `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/config/`:
  1. `CopsNCrooksModuleConfig.java` — `@CustomLog @Configuration`, constructor
     `(Gangland gangland, GanglandContext context)`. Copy across, **verbatim including every parameter**, the 37 cops
     `@Bean` methods listed for `CopsAndGadgetsConfig` in §1.3 (all except `carMessageContract`, `carService`,
     `jetpackService`, `moneyDropClassifier`). Then add the single install hook:
     ```java
     @PostConstruct
     public void installCoreSeams() {
         // Runs inside BeanFactory.instantiate(), before GanglandContext's listener and command scans, so every
         // consumer (listeners, commands, the placeholder bean's lazy reads) sees the delegate.
         context.get(GanglandMoneyDropClassifier.class)
                .install(new CopsMoneyDropSource(context.get(CopManager.class),
                                                 context.get(CivilianNpcRegistry.class)));

         BankTierRegistry tiers = context.get(BankTierRegistry.class);
         context.get(BankTiers.class).install(bank -> {
             BankTier tier = tiers.get(bank.getTierId());
             return tier != null ? tier : tiers.first();
         });

         context.get(WantedKillTrackers.class)
                .install(new KillComboWantedTracker(context.get(KillCombo.class),
                                                    context.get(EntityMarkManager.class)));

         context.get(TurfNpcContracts.class).install(context.get(TurfNpcContract.class));
     }
     ```
     (`GanglandContext.get(Class)` is the sanctioned lazy accessor — `GanglandContext.java:122`; `MailModuleConfig`
     uses the same pattern for its expiry timer.)
     **Note:** the last line needs the `TurfNpcContractImpl` instance; produce it as a **private field** built in
     `TurfNpcsModuleConfig` (T15) and fetched here by its concrete type `TurfNpcContractImpl`, not by the interface —
     never register a second bean under `TurfNpcContract`.
  2. `copsncrooks/seam/CopsMoneyDropSource.java` — `implements NpcMoneyDropSource`; body is the cops half of the old
     `GanglandMoneyDropClassifier`:
     ```java
     @Override
     public MoneyDropContext classify(LivingEntity entity) {
         if (copManager != null && copManager.isCopNpc(entity)) return MoneyDropContext.COP;
         if (civilianNpcRegistry != null && civilianNpcRegistry.getNpc(entity.getUniqueId()) != null) {
             return MoneyDropContext.CIVILIAN;
         }
         return null;
     }
     ```
  3. `copsncrooks/seam/KillComboWantedTracker.java` — `implements WantedKillTracker`, wrapping `KillCombo` and
     `EntityMarkManager`: `countsForWanted` → `entityMarks.countsForWanted(victim)`; `recordKill` →
     `killCombo.recordKill(killer, wanted, victim, resetAfterSeconds)`; `resetCombo` → `killCombo.resetCombo(id)`;
     `onWantedTrigger(h)` → `killCombo.setOnWantedLevelTrigger(event -> h.accept(event.getPlayer()))`;
     `onComboReset(h)` → `killCombo.setOnComboReset(event -> h.accept(event.getPlayer()))`;
     `onVictimDeath(h)` → `killCombo.setOnPlayerDeath(h::accept)`.
  4. `BankerModuleConfig.java` — the whole of `config/BankerConfig.java` minus the two
     `permissionManager.addPermission(BankCommand.*)` lines (`:48,:51`), which move to core in T16. Then **delete**
     `gangland-impl/src/main/java/org/luckyraven/gangland/config/BankerConfig.java`.
  5. `BankTier.java` (`cops-n-crooks/.../npc/banker/tier/BankTier.java:18`): add
     `implements org.luckyraven.gangland.data.economy.BankTierView` to the record declaration. No body change — all
     five component accessors already match the interface.
- **Why:** M3 + M9.
- **Done when** (both commands are run **after T14**, which is what renames the core config):
  ```bash
  grep -c copsncrooks gangland-impl/src/main/java/org/luckyraven/gangland/config/GadgetConfig.java            # 0
  grep -c "\.install(" gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/config/CopsNCrooksModuleConfig.java   # 4
  ```
- **Watch out:** keep `killCombo(Settings settings)`'s unused `Settings` parameter — it is a load-order edge
  (memory rule *bean ordering via params*).

#### T14 — `CopsAndGadgetsConfig` → `GadgetConfig` + `DataConfig` seam beans (group H, 2 files) — **p0-wave-3 overlap**

- **Do:**
  1. `git mv gangland-impl/src/main/java/org/luckyraven/gangland/config/CopsAndGadgetsConfig.java
     gangland-impl/src/main/java/org/luckyraven/gangland/config/GadgetConfig.java`, rename the class and its
     constructor to `GadgetConfig`, and keep the `@Configuration` annotation exactly as it is (default CONFIG phase,
     no `phase =` argument). Then delete all cops imports (`:6-42`, `:46-48`, `:52-53`) and every bean listed in §1.3
     for this file. What remains: the class, the `Gangland` field/constructor, `carMessageContract()` (`:152`),
     `carService()` (`:401`), `jetpackService()` (`:413`) **and `carAccessPolicy()`, which `p0-wave-3` adds to this
     file — it is gadget-only and must survive the rename**. Rewrite the class javadoc to describe gadget wiring only
     (car, jetpack, fuel). The name `GadgetConfig` is fixed by agreement with the flip 2 (gadget) planner, whose
     checklist addresses the file by that name.
  2. `config/DataConfig.java`: add three seam beans and the two bank permissions:
     ```java
     @Bean
     public MoneyDropClassifier moneyDropClassifier() {
         return new GanglandMoneyDropClassifier();
     }

     @Bean
     public BankTiers bankTiers() {
         return new BankTiers();
     }

     @Bean
     public WantedKillTrackers wantedKillTrackers() {
         return new WantedKillTrackers();
     }
     ```
     and, inside the existing `@PostConstruct registerGanglandPermissions()` (`DataConfig.java:148`), the two lines
     lifted from `BankerConfig.java:48,51` with their comments:
     `permissionManager.addPermission(BankCommand.BYPASS_CAP_PERMISSION);` and
     `permissionManager.addPermission(BankCommand.ADMIN_PERMISSION);`.
- **Why:** the core keeps a single, always-present bean for each seam, and the two bank permission nodes stay
  registered when no module is installed (they gate core `/glw bank deposit|withdraw` forms).
- **Done when:** `test ! -f gangland-impl/src/main/java/org/luckyraven/gangland/config/CopsAndGadgetsConfig.java`,
  `grep -c copsncrooks gangland-impl/src/main/java/org/luckyraven/gangland/config/GadgetConfig.java` == 0, and
  `mvn -q -pl gangland-impl -am compile` no longer reports errors in these two files.
- **Watch out:** **`CopsAndGadgetsConfig.java` is being edited by `p0-wave-3`, which adds a `carAccessPolicy` bean to
  it. Merge `0.8.3` before starting this task** — doing the `git mv` first would turn that into a delete/add
  conflict. Check whether `registerGanglandPermissions()` currently resolves `PermissionManager` from the context or
  takes it as a parameter, and follow whichever pattern is already there.

#### T15 — `TraderModuleConfig`, `TurfNpcsModuleConfig` and the core `ShopConfig` remainder (group H, 4 files)

- **Do:**
  1. New `copsncrooks/config/TraderModuleConfig.java` — the 16 trader `@Bean` methods listed in §1.3 plus the
     `@PostConstruct registerPermissions()` lifted verbatim from `ShopConfig.java:69-73` (it registers
     `ShopViewOpenerImpl.ADMIN_PERMISSION`, a cops constant).
  2. New `copsncrooks/config/TurfNpcsModuleConfig.java` — the whole of `config/TurfNpcsConfig.java`, with one
     change: the `turfNpcContract(...)` bean (`:72`) is renamed to `turfNpcContractImpl(...)` and its **return type
     changed to the concrete `TurfNpcContractImpl`**, so nothing registers a second bean under `TurfNpcContract`.
     Then **delete** `gangland-impl/src/main/java/org/luckyraven/gangland/config/TurfNpcsConfig.java`.
     Keep the three `@SuppressWarnings("unused") Settings settings` parameters on the powerup view beans.
  3. `config/ShopConfig.java`: delete every trader import (`:7-18`, `:25-26`, `:29`), the 16 trader beans and the
     `@PostConstruct registerPermissions()` method; if `context` becomes unused, drop the field and shrink the
     constructor to `(Gangland gangland)`. For the four beans that took `TraderSettings`
     (`negotiationView` is trader-side and moves; `priceEditorView` `:219`, `shopAdminView` `:241` stay), change the
     parameter to nothing and construct `ShopUiSettings` inline from a private field:
     ```java
     private final ShopUiSettings shopUiSettings = new GanglandShopUiSettings();
     ```
     **Do not add a `ShopUiSettings` `@Bean`** — `TraderSettings extends ShopUiSettings`, so a module-side
     `TraderSettings` bean also registers under `ShopUiSettings` and `getInstance` would return whichever landed
     first.
  4. New `gangland-impl/src/main/java/org/luckyraven/gangland/file/configuration/shop/GanglandShopUiSettings.java`
     — `implements ShopUiSettings` with the three methods copied verbatim from `TraderSettingsImpl`
     (`getMaxModeMultiplier()` → `Settings.getTraderMaxModeMultiplier()`, `getInventoryFillName()` →
     `Settings.getInventoryFillName()`, `getInventoryFillItem()` → `Settings.getInventoryFillItem()`).
- **Why:** M3 + the ambiguity guard in risk 3.
- **Done when:** `grep -c copsncrooks gangland-impl/src/main/java/org/luckyraven/gangland/config/ShopConfig.java` == 0 and `ls gangland-impl/src/main/java/org/luckyraven/gangland/config/ | grep -E "TurfNpcsConfig|BankerConfig"` returns nothing.
- **Watch out:** `shopRegistry`, `shopPurchaseService`, `shopBarterService`, `shopSellService`, `sellValuator`,
  `categoryBarterValuator`, `shopMessageContract`, `shopDisplayResolver`, `shopYamlReader`, `shopYamlWriter`,
  `priceEditorView`, `sellCategoryItemsAdminView`, `barterCategoryItemsAdminView`, `shopAdminView` and
  `shopAdminFlow` **stay in core** — `command/sub/shop/ShopCommand.java:19` injects `ShopRegistry` and
  `ShopAdminFlow`, so `/glw shop` must work with zero modules.

#### T16 — Core `FileConfig` remainder and the `file/configuration/copsncrooks` rename (group H, 3 files)

- **Do:**
  1. `config/FileConfig.java`: delete the `CivilianSettings` / `CopSettings` imports (`:5-6`) and the three beans
     `copSettings()` (`:122`), `civilianSettings()` (`:127`), `civilianSpawnConfigProvider()` (`:132`). Replace the
     wildcard import `org.luckyraven.gangland.file.configuration.copsncrooks.*` (`:14`) with explicit imports of
     `org.luckyraven.gangland.file.configuration.wanted.GanglandBountySettings` and `…wanted.GanglandWantedSettings`.
  2. `git mv gangland-impl/src/main/java/org/luckyraven/gangland/file/configuration/copsncrooks
     gangland-impl/src/main/java/org/luckyraven/gangland/file/configuration/wanted` (only the two remaining files),
     updating their `package` lines to `org.luckyraven.gangland.file.configuration.wanted`.
  3. New `copsncrooks/config/CopsNCrooksFileConfig.java` — `@CustomLog @Configuration(phase = Phase.FILE)` holding
     the three beans removed in step 1, unchanged.
- **Why:** M3 + M4; it also guarantees the G1 grep and any casual `grep -r copsncrooks gangland-impl` come back
  clean, with no misleading package name left behind.
- **Done when:** `grep -rn "copsncrooks" gangland-impl/src/main/java/ | wc -l` == 0.

#### T17 — YAML, `commands.json` and `KernelConfig` (M8 + M7) (group H, 12 files)

- **Do:**
  1. `git mv` these five files from `gangland-impl/src/main/resources/` to
     `gangland-features/cops-n-crooks/src/main/resources/`, **keeping the relative path byte-for-byte**:
     `npc/cops.yml`, `npc/civilians.yml`, `npc/trader_traits.yml`, `npc/bank_tiers.yml`, `turf/turf_npcs.yml`.
     After the move `gangland-impl/src/main/resources/npc/` must not exist, and
     `gangland-impl/src/main/resources/turf/` must contain only `turf_powerups.yml`.
  2. `config/KernelConfig.java`: delete the five `fm.addFile(...)` lines at `:182`, `:183`, `:184`, `:185` and `:188`.
     Keep `:187` (`turf_powerups`).
  3. New `copsncrooks/config/CopsNCrooksYamlConfig.java`:
     ```java
     @CustomLog
     @Configuration(phase = Phase.KERNEL)
     public class CopsNCrooksYamlConfig {

         private final Gangland gangland;

         public CopsNCrooksYamlConfig(Gangland gangland) {
             this.gangland = gangland;
         }

         /**
          * Registers this module's YAML defaults with the host FileManager. The five-argument FileHandler copies the
          * bundled default out of the MODULE jar (parent-first loader), so the same paths must no longer exist in the
          * core jar. Runs in KERNEL, after KernelConfig produced the FileManager (parameter = ordering edge).
          */
         @Bean
         public CopsNCrooksFiles copsNCrooksFiles(FileManager fileManager, ModuleLoader moduleLoader) {
             ClassLoader loader = moduleLoader.classLoader();
             fileManager.addFile(new FileHandler(gangland, "cops", "npc", ".yml", loader), true);
             fileManager.addFile(new FileHandler(gangland, "civilians", "npc", ".yml", loader), true);
             fileManager.addFile(new FileHandler(gangland, "trader_traits", "npc", ".yml", loader), true);
             fileManager.addFile(new FileHandler(gangland, "bank_tiers", "npc", ".yml", loader), true);
             fileManager.addFile(new FileHandler(gangland, "turf_npcs", "turf", ".yml", loader), true);
             return new CopsNCrooksFiles();
         }

         /** Marker so the registration above is an ordinary @Bean in the phased pipeline. */
         public static final class CopsNCrooksFiles { }
     }
     ```
     `ModuleLoader` is registered in the container by `GanglandContext` (`GanglandContext.java:110`), so it injects
     as a plain parameter.
  4. `gangland-impl/src/main/resources/commands.json`: delete the 43 keys listed in §1.4.
     `gangland-features/cops-n-crooks/src/main/resources/commands.json`: replace `{}` with those 43 entries,
     **usage and description strings copied verbatim** (tab-indented, same style as the core file and
     `gangland-mail`'s).
- **Why:** M8 + M7. `KernelConfig.informationManager(ModuleLoader)` (`KernelConfig.java:62-66`) already merges each
  module's `commands.json` into the `/glw help` index.
- **Done when:**
  ```
  test ! -d gangland-impl/src/main/resources/npc
  ls gangland-impl/src/main/resources/turf            # only turf_powerups.yml
  python -c "import json;print(len(json.load(open('gangland-impl/src/main/resources/commands.json'))))"        # 182
  python -c "import json;print(len(json.load(open('gangland-features/cops-n-crooks/src/main/resources/commands.json'))))"  # 43
  ```
- **Watch out:** resource filtering is on for every module (`pom.xml:504-509`) — if any of the five YAML files
  contains a literal `${...}` sequence Maven will try to substitute it. Check with
  `grep -n '\${' gangland-features/cops-n-crooks/src/main/resources/npc/*.yml
  gangland-features/cops-n-crooks/src/main/resources/turf/turf_npcs.yml`; if it hits, report it rather than
  improvising (that is a pre-existing hazard in the core jar too).

---

### 🚦 GATE G1 — first green build

```bash
mvn clean install -DskipTests -q
grep -rn "org\.luckyraven\.gangland\.copsncrooks\." gangland-impl/src            # must be EMPTY
grep -rn "copsncrooks" gangland-impl/src/main/java | wc -l                        # must be 0
```

If `mvn` still fails, the failing files are the remainder of `/tmp/flip1-worklist.txt` — work them with the same
MOVE/SPLIT/SEAM decisions from §1.1. Do **not** re-add a cops dependency to `gangland-impl/pom.xml`.

---

### Group I — tests

#### T18 — Move tests, add the module test, fix the count (M10) (group I, 8 files)

- **Do:**
  1. `git mv` the three impl tests to the destinations in §1.5 and fix their `package` lines and the imports of
     `DetainmentTable` / `JailTable` (now `org.luckyraven.gangland.copsncrooks.database`).
  2. Change `gangland-impl/src/test/java/org/luckyraven/gangland/command/data/InformationManagerTest.java:41`:
     `assertEquals(225, …)` → `assertEquals(182, …)`, and update the message string (it currently reads
     "…commands.json in 0.8.2") to say the 43 cops/banker/trader/civilian/jail/cuff keys moved to the
     `copsncrooks` module in 0.8.4.
  3. New `gangland-features/cops-n-crooks/src/test/java/org/luckyraven/gangland/copsncrooks/CopsNCrooksModuleTest.java`,
     modelled exactly on `MailModuleTest`:
     - test 1: `new CopsNCrooksModule().configure(registrations)` registers the six configuration classes in
       declaration order, `List.of(CopsNCrooksModule.LISTENER_PACKAGE)`,
       `List.of(CopsNCrooksModule.COMMAND_PACKAGE)`, `List.of(CopsNCrooksModule.REPOSITORY_PACKAGE)`;
     - test 2: the declared packages match where the classes actually live —
       `assertEquals(CopsNCrooksModule.LISTENER_PACKAGE, CivilianDeathRewardListener.class.getPackageName()
       .substring(0, …))` — simplest form: assert `DetainmentRepository.class.getPackageName()` equals
       `REPOSITORY_PACKAGE`, `CopCommand.class.getPackageName().startsWith(COMMAND_PACKAGE)` and
       `TraderBuyListener.class.getPackageName().startsWith(LISTENER_PACKAGE)`;
     - test 3: `assertFalse(registrations.commandPackages().isEmpty(), "copsncrooks ships top-level /glw commands")`.
  4. New `gangland-features/cops-n-crooks/src/test/java/org/luckyraven/gangland/copsncrooks/seam/
     KillComboWantedTrackerTest.java` — assert the three `on*` installers forward through to the `KillCombo`
     setters and that `countsForWanted` delegates to `EntityMarkManager` (mockito).
  5. New `gangland-infra/gangland-domain/src/test/java/org/luckyraven/gangland/gang/wanted/
     WantedKillTrackersTest.java` — assert (a) with nothing installed `isActive()` is false, `countsForWanted`
     returns false and `recordKill`/`resetCombo` do not throw; (b) a handler registered **before** `install` is
     replayed onto the delegate; (c) after `install`, every call forwards.
  6. New `gangland-impl/src/test/java/org/luckyraven/gangland/data/economy/BankTiersTest.java` — `tierFor` returns
     `null` before `install`, and the installed function's result after.
- **Show the new tests red first:** write `WantedKillTrackersTest` and `BankTiersTest` **before** T4/T6 land if you
  are running the tasks out of order; if you are running in order (recommended), make each new test red by
  temporarily stubbing the holder method to `throw new UnsupportedOperationException()`, run
  `mvn -q -pl <module> -am test -Dtest=<TestName>` to see it fail, then restore the real body and see it pass.
  Record both outputs in the status table. `CopsNCrooksModuleTest` is shown red by first running it against a
  `configure` that registers nothing.
- **Why:** M10.
- **Done when:** `mvn test` is green across the reactor (**gate G2**) and
  `grep -rn "copsncrooks" gangland-impl/src/test | wc -l` == 0.
- **Watch out:** the two moved repository tests touch a real SQLite file — they already use
  `@TempDir(cleanup = CleanupMode.NEVER)` and `DbFiles`; keep both. Never add junit/mockito/testkit/sqlite to
  `cops-n-crooks/pom.xml` (inherited from the root pom).

---

### 🚦 GATE G2 — `mvn test`

```bash
mvn test
```
Expected: green; `InformationManagerTest` asserts 182; `CopsNCrooksModuleTest` passes.

---

### Group K — docs and verification

#### T19 — Docs (M11) (group K, 5 files)

- **Do:**
  1. `CLAUDE.md`:
     - **Module Structure** table: change the `gangland-features/cops-n-crooks` row's Purpose to note it is a
       **runtime module** (`modules/cops-n-crooks-<rev>.jar`, never in the core jar), matching the
       `gangland-mail` row's wording.
     - **"Two tiers"** section: `gangland-mail` → "`gangland-mail`, `cops-n-crooks`"; the remaining flip order
       becomes **gadget → turf → weapon**; note that cops-n-crooks' `gangland-weapon` / `gangland-turf`
       dependencies are `provided` and gain `Depends:` entries at flips 3 and 4.
     - **Commands** paragraph: add that `BankCommand` queries the contribution path `bank` and `TurfCommand` the
       path `turf`, beside the existing `gang` / `gang.ally` note.
     - **Key Configuration Files**: `cops.yml` is no longer under `gangland-impl/src/main/resources/`; it and
       `civilians.yml`, `trader_traits.yml`, `bank_tiers.yml`, `turf_npcs.yml` ship in the module jar.
  2. `documentation/module-loader.md`:
     - "What is a module today" table: add a cops-n-crooks row; remove it from the "still compile-time" row.
     - "Order for the remaining flips": **gadget → turf → weapon**.
     - New section titled **exactly `## Core seams`** (that literal heading — flips 2, 3 and 4 append rows to this
       same section rather than creating their own), as a table documenting the four holders (name, core package,
       default behaviour, who installs) and the two new contribution paths `bank` and `turf`.
     - Add the module-YAML rule already stated in §1.4: the default lives in the module jar at the **data-folder**
       path (`npc/cops.yml`), not under `<module>/`, because `FileHandler` resolves `directory/name+type` from the
       loader; the same path must be gone from the core jar.
  3. `documentation/README.md`: update any mention of cops-n-crooks as a compile-time dependency.
  4. Memory `project_module_loader_plan.md`: record flip 1 done, the four seams, `InformationManagerTest` 225 → 182,
     and that flips 3 and 4 owe cops-n-crooks a `Depends:` entry.
  5. `brainstorming/module-split-2026-09-07/README.md` status board: flip 1 row.
- **Done when:** `grep -n "cops-n-crooks" CLAUDE.md documentation/module-loader.md` shows the module wording, and no
  doc still calls it a compile-time dependency of impl.

#### T20 — Verification and graph refresh (M12) (group K, 2 commands)

- **Do:** run gates G3 and G4 (§5), then `graphify update . --force`.
- **Done when:** both `unzip -l` checks below behave as specified and the graph is newer than the working tree.

---

## 3. Tests

**Move (3):** see §1.5 — `GanglandSeizedInventoryServiceTest`, `DetainmentRepositoryMigrationTest`,
`DetainmentRepositorySpiTest`.

**Change (1):** `gangland-impl/src/test/java/org/luckyraven/gangland/command/data/InformationManagerTest.java:41` —
`assertEquals(225, …)` → **`assertEquals(182, …)`** plus the explanatory message. Its "merge folds a module's
commands.json" test at `:49-60` is count-relative and needs no edit.

**New (4):**

| test | asserts | shown red how |
|---|---|---|
| `copsncrooks/CopsNCrooksModuleTest` | `configure` registers the six configs and the three packages; declared packages match the real ones; `commandPackages()` is non-empty | run first against a `configure` body that registers nothing |
| `copsncrooks/seam/KillComboWantedTrackerTest` | `onWantedTrigger` / `onComboReset` / `onVictimDeath` reach `KillCombo`'s three setters and translate `KillComboEvent` → `Player`; `countsForWanted` delegates to `EntityMarkManager` | run before `KillComboWantedTracker` exists (compile error) then against a stubbed no-op body |
| `gang/wanted/WantedKillTrackersTest` (gangland-domain) | default is inert (`isActive()` false, `countsForWanted` false, no throw); handlers registered before `install` are replayed; after `install` everything forwards | stub `install` to a no-op, watch the replay test fail, then restore |
| `data/economy/BankTiersTest` (gangland-impl) | `tierFor` is `null` before `install`, returns the function's result after | stub `tierFor` to always return `null`, watch the second case fail, then restore |

Every new test must be observed failing before the change that makes it pass, and the failing output pasted into the
status table.

---

## 4. Docs and config

| file | edit |
|---|---|
| `CLAUDE.md` | module table row, two-tier section, remaining flip order (**gadget → turf → weapon**), contribution paths `bank` / `turf`, YAML location of the five moved files |
| `documentation/module-loader.md` | module table, flip order, new **`## Core seams`** section (that exact heading — later flips append to it), module-YAML path rule |
| `documentation/README.md` | drop "compile-time dependency" wording for cops-n-crooks |
| `brainstorming/module-split-2026-09-07/README.md` | status board row for flip 1 |
| memory `project_module_loader_plan.md` | flip 1 landed; seams; 225 → 182; `Depends:` debt owed by flips 3 and 4 |
| `gangland-features/cops-n-crooks/src/main/resources/module.yml` | new (T2) — `Id: copsncrooks`, `Host_Api: 0.8`, `Artifact: org.luckyraven:cops-n-crooks`, **no `Depends`** |
| `gangland-features/cops-n-crooks/src/main/resources/commands.json` | new — the 43 keys from §1.4 |
| `gangland-impl/src/main/resources/commands.json` | 225 → 182 keys |
| `gangland-impl/pom.xml` | drop the `cops-n-crooks` dependency |
| `gangland-features/cops-n-crooks/pom.xml` | `gangland-impl` provided; `gangland-weapon` + `gangland-turf` → provided; add `keystone-module`, `keystone-command`; **no test deps** |
| `gangland-build/pom.xml` | shade `<exclude>org.luckyraven:cops-n-crooks</exclude>`; `maven-dependency-plugin` `<artifactItem>`; `<dependency>` at provided scope |
| `config/KernelConfig.java` | delete the 5 `fm.addFile(...)` lines for `npc/*` and `turf/turf_npcs` |
| `config/CopsAndGadgetsConfig.java` | `git mv` → `config/GadgetConfig.java`, class renamed, cops beans deleted (name agreed with the flip 2 planner) |
| `config/TurfNpcsConfig.java`, `config/BankerConfig.java` | deleted from core (moved whole into the module) |
| `config/TurfConfig.java` | **+1 `@Bean`** — `turfNpcContracts()` (seam 4 holder) plus its import; the file goes **20 → 21** beans, which flip 3 (`turf.md` T7) must carry |
| `command/sub/turf/TurfSelectionResolver.java` | visibility only — `public final class`, `public static resolve(...)`; stays in core until flip 3 |

---

## 5. Verification (G1–G4 instantiated)

**G1 — compile**
```bash
mvn clean install -DskipTests -q
grep -rn "org\.luckyraven\.gangland\.copsncrooks\." gangland-impl/src     # expect: no output
grep -rn "copsncrooks" gangland-impl/src/main/java | wc -l                # expect: 0
ls gangland-impl/src/main/java/org/luckyraven/gangland/config/ | grep -E "CopsAndGadgetsConfig|BankerConfig|TurfNpcsConfig"   # expect: no output
ls gangland-impl/src/main/java/org/luckyraven/gangland/config/GadgetConfig.java                                               # expect: PRESENT
```

**G2 — tests**
```bash
mvn test
```
Expect green; `InformationManagerTest` asserts **182**.

**G3 — jars**
```bash
mvn clean package -DskipTests
unzip -l target/gangland_warfare-0.8.4.jar | grep "org/luckyraven/gangland/copsncrooks/"   # expect: no output
unzip -l target/gangland_warfare-0.8.4.jar | grep -E "npc/(cops|civilians|trader_traits|bank_tiers)\.yml|turf/turf_npcs\.yml"  # expect: no output
unzip -l target/gangland_warfare-0.8.4.jar | grep "turf/turf_powerups.yml"                 # expect: PRESENT
unzip -l target/modules/cops-n-crooks-0.8.4.jar | grep -E "module\.yml|commands\.json"     # expect: both at the jar root
unzip -l target/modules/cops-n-crooks-0.8.4.jar | grep -E "npc/|turf/turf_npcs\.yml"       # expect: the 5 YAMLs
unzip -p target/modules/cops-n-crooks-0.8.4.jar module.yml                                 # Version must be 0.8.4, no Depends key
unzip -p target/modules/cops-n-crooks-0.8.4.jar commands.json | python -c "import json,sys;print(len(json.load(sys.stdin)))"   # 43
```

**G4 — docs**
```bash
git diff --stat CLAUDE.md documentation/module-loader.md documentation/README.md
```
Then `graphify update . --force`.

---

## 6. Risks and open questions for the scrum master

1. **Merge `0.8.3` before T5 and T14.** `GanglandPlaceholder.java` and `CopsAndGadgetsConfig.java` are open in the
   `p0-wave-3` worktree. Both are structural edits here (constructor signature, whole-bean deletions), so a late
   merge will be painful.
2. **`TurfNpcsConfig` has no turf-only remainder — this is `turf.md`'s "design A" and both planners agree.** The
   sprint README predicted a straddle where cops takes "the cops half" and turf keeps a remainder. In fact all ten
   beans in `config/TurfNpcsConfig.java` produce or wire `copsncrooks.npc.turf` / `copsncrooks.npc.civilian` types
   (`TurfNpcsConfig.java:4-13`), as do `TurfNpcsConfigLoader` (`:5-6`), `TurfPowerupNpcRepository:7,22`,
   `TurfPowerupNpcTable:3,10` and `turf/turf_npcs.yml`, so **flip 1 moves all of them** and deletes the config from
   core. `turf/turf_powerups.yml` is the only turf YAML that stays. The only turf-side residue flip 1 creates is the
   `TurfNpcContracts` holder in `gangland-turf` (T8) and its `@Bean` in `config/TurfConfig.java`.
   **Flip 3's checklist inherits three things:** (a) move `TurfNpcContracts` with the turf module and add
   `Depends: [turf]` to cops-n-crooks' `module.yml`; (b) `config/TurfConfig.java` will contain **21** `@Bean` methods
   — the 20 it has today plus `turfNpcContracts()` added by T8 — so `turf.md` T7's "all 20 beans, verbatim" must
   become 21; (c) `command/sub/turf/TurfSelectionResolver` is already `public final` with a `public static
   resolve(...)` (widened by T12 because `TurfPowerupNpcCommand:61` calls it from the module), and flip 3 still owns
   relocating it to `org.luckyraven.gangland.turf.command`. Its "move the TurfNpcsConfig remainder" item is already
   done. **If an executor deviates from any of this, write it into `turf.md`'s status table before flip 3 starts.**
3. **`/glw bank` degrades without the module.** With no cops module, `bank deposit` applies no tier cap and no daily
   limit (the existing `tier == null` branches), the death penalty applies no insurance discount, and
   `%..bank_tier%` / `bank_tier_display` / `bank_tier_cap` / `bank_daily_deposit_limit` placeholders render empty.
   **Default chosen:** accept this — it is the honest "no Banker NPC catalogue installed" state, and every call site
   already handled `tier == null`. If the user wants a core fallback tier instead, that is a settings-driven
   `BankTiers` default and a follow-up.
4. **`documentation/module-loader.md` currently says module YAML lives under `src/main/resources/<module>/`.** That
   contradicts how `FileHandler`'s five-argument constructor resolves resources
   (`Keystone/keystone-persistence/.../FileHandler.java:191,202,233-235`: `directory + fileType`). This plan follows
   the planner brief's rule 6 — the module jar carries `npc/cops.yml` at exactly that path — and T19 fixes the doc.
   Flag it to the other planners so gadget/turf/weapon converge on the same rule.
5. **`EntityDamageListener` behaviour parity is not test-pinned.** There is no existing unit test over its kill-credit
   path, and writing one needs heavy Bukkit mocking. **Default chosen:** cover the seam itself
   (`WantedKillTrackersTest`, `KillComboWantedTrackerTest`) and put "wanted level rises on a cop kill; kill combo
   still resets on death; bounty still pays out" on the G6 server smoke checklist. If the scrum master wants it
   pinned, that is a new bug-docket-adjacent test task, not part of this flip.
6. **Bug-docket hygiene.** No new bug was found while planning. If an executor finds one, it goes to
   `brainstorming/bug-docket-2026-09-06/triage/<slug>.txt` and the docket is rebuilt with `build_docket.py` — never
   only into a commit message.

---

## 7. Status table (executors fill this)

| Task | Status | Executor | Notes (what changed, what was skipped, failures verbatim) |
|---|---|---|---|
| T1 poms | done | sonnet-exec | Removed `cops-n-crooks` dep from `gangland-impl/pom.xml`. `cops-n-crooks/pom.xml`: `gangland-weapon`/`gangland-turf` → `provided`, added `keystone-module`, `keystone-command`, and the `gangland-impl` provided dep (mail's comment). `gangland-build/pom.xml`: added the exclude, the `copy-runtime-modules` artifactItem, and the provided dependency, mirroring mail. **Deviation not in the checklist text:** removing the `cops-n-crooks` dep from `gangland-impl` also silently dropped a *transitive* `gangland-item` dependency it never declared directly (cops-n-crooks pulled it in at compile scope) — first build after the pom edit failed with ~9 extra non-cops errors in `item/configuration/UniqueItemAddon.java` and `bootstrap/PlayerBootstrapService.java` (`item.unique`/`item.fuel`/`item.contract` package/symbol not found), which are outside the 101-file cops worklist and would have broken every later gate. Fixed by adding a direct `gangland-item` (default/compile scope) dependency to `gangland-impl/pom.xml`, same style as the other local deps in that block; recorded here since it is a pom addition the checklist did not call for. |
| T2 module entry | done | sonnet-exec | Created `copsncrooks/CopsNCrooksModule.java` (verbatim per checklist, will not compile until T13-T16 add the 6 config classes — expected), `resources/module.yml` (no `Depends:`), `resources/commands.json` (`{}`). Verified `module.properties` already present at `resources/org/luckyraven/gangland/copsncrooks/module.properties` — not recreated. |
| **Gate A** (expected failure, impl only) | done | sonnet-exec | `mvn clean install -DskipTests`: reactor fails **only** on `gangland-impl` (`Gangland ... FAILURE`); `Cops N Crooks` and everything after is `SKIPPED`; all 18 modules before it (incl. `Gangland Item`) are `SUCCESS`. `grep -c "ERROR.*gangland-impl"` = 202, `grep "ERROR.*\.java" \| grep -v gangland-impl \| wc -l` = 0. Saved `mvn clean install -DskipTests 2>&1 \| grep -E "^\[ERROR\].*\.java" \| sort -u > brainstorming/module-split-2026-09-07/cops-errors-after-T1.txt` (100 lines — javac's default `-Xmaxerrs 100` caps the file, so it is a prefix of the full error set, not the complete 101-file list; later executors comparing against it should treat "shrinks and stays within" loosely for files past the cap). |
| T3 seam 1 `NpcMoneyDropSource` | done | sonnet-exec | Created `data/economy/NpcMoneyDropSource.java`. Rewrote `GanglandMoneyDropClassifier.java`: dropped `CopManager`/`CivilianNpcRegistry` fields, `@RequiredArgsConstructor`, and imports; added no-arg ctor, `volatile NpcMoneyDropSource npcSource`, `install(...)`, new `classify` body per checklist. `grep -c copsncrooks` on the file = 0. |
| T4 seam 2 `BankTierView`/`BankTiers` | done | sonnet-exec | Created `data/economy/BankTierView.java` (5 accessors, verified they match the `BankTier` record's component accessors verbatim) and `data/economy/BankTiers.java` (verbatim from checklist). No compile attempted (reactor red by design until G1) — both grep-clean of `copsncrooks`. |
| T5 rewire BankTier consumers (p0-w3) | done | sonnet-exec | **Drift:** `GanglandPlaceholder.java` line numbers moved from the checklist's `:7-8,41,49,233-243` to `:7-8,42,50,237` because the p0-wave-3 merge (already landed, commit cc67598d) added a `UniqueItemAddon uniqueItemAddon` field/param ahead of the bank-tier one; content otherwise matched exactly, no conflict. Rewired all 4 files (`BankDepositCommand`, `PlayerDeathListener`, `GanglandPlaceholder`, `WiringConfig`): `BankTierRegistry`/`BankTier` imports → `BankTiers`/`BankTierView`; fields/ctor params swapped; call sites → `bankTiers.tierFor(bank)`; deleted `BankDepositCommand.resolveTier(...)`. `grep -n "BankTier\b\|BankTierRegistry"` across all 4 files returns nothing. Note: `BankCommand.java` (constructs `BankDepositCommand`) still passes the old type — that rewire is explicitly T12/group G's job, not T5's; left as-is (part of the expected red reactor). |
| T6 seam 3 + `EntityDamageListener` | done | sonnet-exec | Created `gangland-domain/.../gang/wanted/WantedKillTracker.java` (interface) and `WantedKillTrackers.java` (holder, install-time handler replay, no-op when inactive) per checklist verbatim. Rewired `EntityDamageListener.java`: dropped `KillCombo`/`KillComboEvent`/`EntityMarkManager` imports and fields, single `WantedKillTrackers wantedKills` field/param; all 4 `Settings.isWantedKillComboEnabled()` guards now also require `wantedKills.isActive()`; 3 `recordKill` + 1 `resetCombo` call sites moved to `wantedKills.*`; `setupKillComboCallbacks()` now wires `onWantedTrigger/onComboReset/onVictimDeath`; `onKillComboWantedTrigger`/`onKillComboReset` take `Player` directly (event.getPlayer() lines dropped); `onPlayerDeathResetWanted(UUID)` unchanged. `grep -c copsncrooks` = 0; verified by grep that no `KillCombo`/`EntityMarkManager` **type** references remain (only method names like `setupKillComboCallbacks`/`isWantedKillComboEnabled` that share the substring). |
| T7 move detainment/config contract impls | done | sonnet-exec | `git mv` all 17 files. `data/detainment/{*.java,inventory/GanglandSeizedInventoryService.java}` (7) → `copsncrooks/integration/detainment/` (package `...copsncrooks.integration.detainment`); `file/configuration/copsncrooks/{BankerSettingsImpl,GanglandBankerEconomy,GanglandBankerMessages,GanglandCivilianSettings,GanglandCivilianSpawnConfigProvider,GanglandCopSettings,GanglandDetainmentMessages,GanglandTraderEconomy,GanglandTraderMessages}.java` (9) + `file/configuration/shop/TraderSettingsImpl.java` (1) → `copsncrooks/integration/config/` (package `...copsncrooks.integration.config`), 10 files. Fixed each moved file's `package` line only. Deleted the now-empty `data/detainment/` (+ `inventory/`) directories (git doesn't track empty dirs, so `rmdir` after the moves). Verified `GanglandSeizedInventoryService:33`'s `repository.setDataSupplier(cache::values)` survived verbatim. **Scope decision:** the only files importing these 17 classes are `config/{BankerConfig,CopsAndGadgetsConfig,FileConfig,ShopConfig}.java` (owned whole by T13/T14/T15/T16 — each deletes or wholesale-rewrites the referencing beans/imports, not just adjusts them) and `gangland-impl/src/test/.../GanglandSeizedInventoryServiceTest.java` (owned by T18's test move) — none touched, per "never touch files owned by later tasks except forced import lines" (no fix was forced since those files are replaced outright, not adjusted). Done-when: `ls gangland-impl/.../data/detainment` → "No such file or directory" (gone); `ls .../copsncrooks/integration/config \| wc -l` == 10. Both confirmed. |
| T8 move turf glue + `TurfNpcContracts` | done | sonnet-exec | `git mv` `file/configuration/turf/{TurfNpcContractImpl,TurfNpcsConfigLoader,TurfPowerupOpenContractImpl}.java` → `copsncrooks/integration/turf/` (package `...copsncrooks.integration.turf`); `file/configuration/turf/` now holds only `GanglandTurfMessages.java`/`GanglandTurfSounds.java` as expected. Created `gangland-features/gangland-turf/.../turf/turfnpcs/TurfNpcContracts.java` (seam 4 holder, `implements TurfNpcContract`, all 4 methods no-op when `delegate == null`, `install(TurfNpcContract)`). `GarrisonDeployListener.java:33` (line number unchanged from checklist — no drift) field type `TurfNpcContract` → `TurfNpcContracts` (import updated); `@RequiredArgsConstructor` regenerates the ctor automatically, no manual ctor edit needed. `config/TurfConfig.java`: added `import ...turf.turfnpcs.TurfNpcContracts;` and the `turfNpcContracts()` `@Bean` (javadoc verbatim from checklist) at the end of the class, before the closing brace, after the `TurfBossBarListener`/`TurfCaptureNotifier` comment. Only `TurfPowerupFlowSession.java` in the module referenced `TurfPowerupOpenContractImpl` and only in a `{@code}` javadoc string (not an import) — no fix needed there. Done-when: `grep -rn copsncrooks .../file/configuration/turf/` → empty; `grep -c "^	@Bean" config/TurfConfig.java` == 21 (was 20). Both confirmed. Post-task build check: `mvn clean install -DskipTests` — reactor still fails **only** in `gangland-impl`; `Gangland Turf` module (which now contains `TurfNpcContracts` + the rewired listener) built **SUCCESS**, confirming those two files compile clean. Saved `cops-errors-after-T8.txt` (100 lines, javac's `-Xmaxerrs` cap, same as T1's file); every `gangland-impl` file named in it is one of the 101-file checklist inventory owned by T9–T12 (persistence/listeners/commands not yet moved) — no error outside the checklist's inventory. Diff vs. `cops-errors-after-T1.txt`: `BankDepositCommand.java` dropped off (fixed by T5); `CopSpawnerCommand.java`/`CopSpawnerInfoCommand.java` newly visible only because earlier-in-file errors ahead of them were resolved, pushing the 100-line cap further into the same still-untouched file set — not a new out-of-scope file. |
| T9 move repositories + tables | done | sonnet-exec | `git mv` all 18 files (6 `repositories/copsncrooks/*` + `BankerRepository` + `TraderRepository` + `TurfPowerupNpcRepository`; 6 `tables/copsncrooks/*` + `BankerTable` + `TraderTable` + `TurfPowerupNpcTable`) into one flat `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/database/` package (`org.luckyraven.gangland.copsncrooks.database`). Fixed all 18 `package` lines (from 8 distinct source packages: `database.repositories.{copsncrooks,banker,trader,turf}`, `database.tables.{copsncrooks,banker,trader}`) via `sed`. Deleted the now-same-package cross-imports (10 lines across `BankerRepository`, `CivilianSpawnerRepository`, `CopSpawnerRepository`, `DetainmentRepository` (2: its own table + `JailTable`), `JailExitRepository`, `JailRepository`, `SeizedInventoryRepository`, `TraderRepository`, `TurfPowerupNpcRepository` — each repo's `import org.luckyraven.gangland.database.tables.*` line, now redundant since repo+table share a package). Deleted the 6 emptied directories (`repositories/{copsncrooks,banker,trader}`, `tables/{copsncrooks,banker,trader}`) via `rmdir` after confirming each was empty; `repositories/turf` and `tables/turf` were left alone (still hold `ActiveTurfBuffRepository`/`TurfGarrisonRepository`/`TurfRepository` and their tables). No drift from the checklist. Done-when: `ls .../copsncrooks/database/*.java \| wc -l` == 18 (confirmed); `grep -rn copsncrooks gangland-impl/.../database/` empty (confirmed). Also spot-checked all 9 moved `@Repository` classes keep the 3-arg `(JavaPlugin, DatabaseHandler, DatabaseBackend)` constructor. No compile attempted (reactor red by design until G1; group D has no gate). |
| T10 move listeners | done | sonnet-exec | `git mv` 5 files into `copsncrooks/listener/{npc,trader,player}`: `CivilianDeathRewardListener.java` → `listener/npc/` (joined an otherwise-empty target dir); `TraderBarterListener.java`, `TraderBuyListener.java`, `TraderSellListener.java` → `listener/trader/` (joined 4 pre-existing module-only listeners already there: `BarterSessionListener`, `TraderDamageListener`, `TraderInteractListener`, `TraderSellSessionListener` — no name collisions); `WantedLevelListener.java` → `listener/player/`. Fixed all 5 `package` lines. Deleted the now-emptied `gangland-impl/.../listener/npc/` and `listener/trader/` directories; `listener/player/` retains its other 7 members (`BountyIncreaseListener`, `CreateAccountListener`, `CustomPlayerDeathListener`, `EntityDamageListener`, `LevelUpListener`, `LoadResourcePackListener`, `PlayerDeathListener`, `PlayerScoreboardListener`, `RemoveAccountListener` — 9 total, checklist said "plus its other members", consistent). No content changes beyond the package line — `@ListenerHandler` and every `@EventHandler(priority = …)` untouched, verified by reading each file's header before/after. No drift. Done-when: `grep -rn "org\.luckyraven\.gangland\.copsncrooks\." gangland-impl/.../listener/` empty (confirmed). **Post-T10 gate (per orchestrator instructions, not the checklist's own gate — group E has none):** `mvn clean install -DskipTests` still fails only in `gangland-impl` as expected; saved deduped `[ERROR].*\.java` lines (javac's 100-line cap, same caveat as `cops-errors-after-T1.txt`/`cops-errors-after-T8.txt`) to `brainstorming/module-split-2026-09-07/cops-errors-after-T10.txt`. All 23 distinct files in it are under `command/sub/{bank,banker,civilians,cops}/**`, i.e. owned by T11 (cops/civilians/cuff) and T12 (jail/trader/banker/bank + contributions) — none outside the 101-file checklist inventory. Grepped the **full** (untruncated, 332-`[ERROR]`-line) build log for `database/repositories\|database/tables\|listener/npc\|listener/trader` — zero matches anywhere, confirming T9/T10 fully eliminated those errors (not just pushed past the 100-line cap). File-set diff against `cops-errors-after-T8.txt` is empty because both snapshots are dominated by the same alphabetically-earlier command files within the 100-line cap, not because T9/T10 made no progress — the full-log grep above is the real evidence. |
| T11 move cops/civilians/cuff commands | done | sonnet-exec | `git mv` all 22 files (file count matched the checklist's "22 files" exactly, no drift) into `copsncrooks/command/`: `command/sub/cops/{CopCommand,CopListCommand}.java` → `command/cops/` (package `...copsncrooks.command.cops`), `command/sub/cops/spawner/*` (6) → `command/cops/spawner/` (package `...copsncrooks.command.cops.spawner`); `command/sub/civilians/{CivilianCommand,CivilianGroupsCommand,CivilianListCommand,CivilianSpawnCommand,CivilianSpawnGroupCommand}.java` (5) → `command/civilians/` (package `...copsncrooks.command.civilians`), `command/sub/civilians/spawner/*` (7) → `command/civilians/spawner/` (package `...copsncrooks.command.civilians.spawner`); `command/sub/cuff/{CuffCommand,UncuffCommand}.java` (2) → `command/cuff/` (package `...copsncrooks.command.cuff`). Fixed all 22 `package` lines. Fixed the 2 intra-tree imports named in the checklist: `CopCommand` → `import ...copsncrooks.command.cops.spawner.CopSpawnerCommand;`, `CivilianCommand` → `import ...copsncrooks.command.civilians.spawner.CivilianSpawnerCommand;`. No other imports needed changes — `org.luckyraven.gangland.command.Command` (core, unmoved) and all `org.luckyraven.keystone.*` imports resolve unchanged at `provided` scope. Deleted the now-emptied `gangland-impl/.../command/sub/{cops,civilians,cuff}/` directories (incl. their `spawner/` subdirs) via `rmdir`. Verified `@CommandHandler` stayed on exactly the 4 top-level classes (`CopCommand`, `CivilianCommand`, `CuffCommand`, `UncuffCommand`); no permission strings, `OptionalArgument` chains or `var list` help filters touched — only package/import lines edited. No drift from the checklist (line numbers weren't needed since whole files moved). Did **not** touch `commands.json` (T17's job per the brief). Done-when: `ls gangland-impl/.../command/sub/ \| grep -E "^(cops\|civilians\|cuff)$"` → empty (confirmed). Post-task build check (not the checklist's own gate — group F has none): `mvn clean install -DskipTests` — reactor still fails **only** in `gangland-impl`; grep of the full untruncated log for `command/sub/cops\|command/sub/civilians\|command/sub/cuff` = 0 matches, confirming the move is complete and nothing outside it references the old paths. Saved deduped `[ERROR].*\.java` lines (200 raw → 100 unique, no javac cap hit this time) to `brainstorming/module-split-2026-09-07/cops-errors-after-T11.txt`; every one of the 4 distinct files/dirs it names (`bank`, `banker`, `jail`, `trader`) is owned by **T12** (group G) — none outside the 101-file checklist inventory. |
| T12 move jail/trader/banker + contributions | done | sonnet-exec | `git mv` 21 files into `copsncrooks/command/`: `command/sub/jail/*` (9) → `command/jail/` (package `...copsncrooks.command.jail`); `command/sub/trader/{TraderCommand,TraderCreateCommand,TraderRemoveCommand}.java` (3) → `command/trader/` (package `...copsncrooks.command.trader`) and `command/sub/trader/edit/*` (4) → `command/trader/edit/` (package `...copsncrooks.command.trader.edit`); `command/sub/banker/*` (5) → `command/banker/` (package `...copsncrooks.command.banker`). Fixed all 21 `package` lines. Fixed the one intra-tree import the checklist named: `TraderCommand` → `import ...copsncrooks.command.trader.edit.TraderEditCommand;` (verified by grep this was the *only* `command.sub.{jail,trader,banker}` cross-reference anywhere in the tree). Deleted the emptied `command/sub/{jail,banker}/` and `command/sub/trader/{edit,}` directories via `rmdir`. `git mv` `command/sub/bank/BankMenuCommand.java` → `copsncrooks/command/bank/BankMenuCommand.java` (package fixed, class + constructor made `public`); created `copsncrooks/command/bank/BankMenuContribution.java` verbatim per checklist (`parent()` = `"bank"`). `git mv` `command/sub/turf/TurfPowerupNpcCommand.java` → `copsncrooks/command/turf/TurfPowerupNpcCommand.java` (package fixed; class + constructor made `public`, kept non-`final` since the pre-move class was plain `class` and the checklist only said "public", not "public final" for this one — unlike `BankMenuCommand`, which the checklist explicitly wrote as `public final`); added `import ...command.sub.turf.TurfSelectionResolver;` since the resolver stays behind in its old package and the caller is now cross-package. Created `copsncrooks/command/turf/TurfPowerupNpcContribution.java` (`parent()` = `"turf"`, constructor args copied from `TurfCommand.java:126-128`'s old `new TurfPowerupNpcCommand(...)` call: `Gangland, TurfManager, WandSelectionManager, TurfMessageContract, TurfPowerupManager`). Widened `command/sub/turf/TurfSelectionResolver.java` (no drift: still `:24` class decl, `:29` `resolve(...)`) to `public final class` / `public static @Nullable Turf resolve(...)`; left in place in `command/sub/turf/` per the checklist (flip 3 moves it). `BankCommand.java`: dropped `BankTierRegistry tierRegistry` + `BankerFlow bankerFlow` fields/params (already down to just `BankTierRegistry`/`BankerFlow`, since T5 had already swapped `BankTier`→`BankTierView` on the *other* three consumers but explicitly left `BankCommand` for T12, per T5's note); added `BankTiers bankTiers` + `DependencyContainer container` + `CommandContributions contributions = CommandContributions.from(container)`; `initializeArguments()` now passes `bankTiers` to `BankDepositCommand` and appends `contributions.createFor("bank", getArgumentTree(), getArgument())` where the removed `BankMenuCommand menu` construction/add used to sit. `TurfCommand.java`: dropped `TurfPowerupManager powerupNpcs` field/param (no drift: import at `:8`, ctor use at `:126-128` as documented); added `DependencyContainer container` + `CommandContributions contributions`; `initializeArguments()` drops the `TurfPowerupNpcCommand powerupNpc` construction/add and appends `contributions.createFor("turf", ...)` in the same slot. Argument-add ordering preserved everywhere (menu/powerupnpc contributions appended last, same position the removed constructions occupied). No other files needed forced-import fixes. **Gap noticed, not fixed (out of T12's scope):** neither T12 nor T13's text registers `BankMenuContribution`/`TurfPowerupNpcContribution` as `@Bean`s anywhere — `CommandContributions.from(container)` uses `container.getAllInstances(CommandContribution.class)`, which only finds beans a `@Configuration` class produces. T13's `CopsNCrooksModuleConfig` copies exactly the 37 `CopsAndGadgetsConfig` beans plus the 4-line `installCoreSeams()` hook; nothing in its text adds `@Bean bankMenuContribution(...)` / `@Bean turfPowerupNpcContribution(...)`. Without such a bean added when T13 runs, `/glw bank menu` and `/glw turf powerupnpc` will compile but be unreachable at runtime (empty `createFor(...)` results) even with the module installed. Flagging for the T13 executor / scrum master rather than widening this task's scope to add a config file that T13 owns and hasn't been created yet. **Done when (checked):** `grep -rn "org\.luckyraven\.gangland\.copsncrooks\." gangland-impl/src/main/java/org/luckyraven/gangland/command/` → empty (confirmed). **Post-task build check (not this task's own gate — T12 has "none" per §0's table, but the group-boundary instruction from the orchestrator requires the G1-style comparison before T13 starts):** `mvn clean install -DskipTests` still fails only in `gangland-impl`; saved deduped `[ERROR].*\.java` lines to `brainstorming/module-split-2026-09-07/cops-errors-after-T12.txt` (100 lines, javac's `-Xmaxerrs` cap — but the full untruncated log has only 324 `[ERROR]` lines total and reduces, via `grep -oE "gangland-impl/src/main/java/[^:]+\.java" | sort -u`, to exactly **2 distinct files**: `config/BankerConfig.java` and `config/CopsAndGadgetsConfig.java` — both owned outright by group H (T13 deletes/replaces `BankerConfig`, T14 renames `CopsAndGadgetsConfig`→`GadgetConfig`). Diffed the file-set against `cops-errors-after-T11.txt`: all 17 T11-snapshot files under `command/sub/{bank,banker,jail,trader}/` are gone, replaced by nothing new outside group H — clean handoff, nothing outside the checklist's inventory. |
| T13 `CopsNCrooksModuleConfig` + `BankerModuleConfig` + seam impls | done | sonnet-exec | Created `copsncrooks/config/CopsNCrooksModuleConfig.java` — copied all 37 non-gadget beans from `CopsAndGadgetsConfig` verbatim (incl. `killCombo(Settings settings)`'s load-order-only param), plus `installCoreSeams()` `@PostConstruct` exactly per checklist (4 `.install(...)` calls: `GanglandMoneyDropClassifier`, `BankTiers` via `bank -> tiers.get(bank.getTierId()) or tiers.first()`, `WantedKillTrackers`, `TurfNpcContracts.install(context.get(TurfNpcContractImpl.class))`). Created `copsncrooks/seam/CopsMoneyDropSource.java` (`implements NpcMoneyDropSource`) and `copsncrooks/seam/KillComboWantedTracker.java` (`implements WantedKillTracker`, wraps `KillCombo`/`EntityMarkManager`, `KillComboEvent.getPlayer()` via its Lombok `@Getter`). Created `copsncrooks/config/BankerModuleConfig.java` — whole of old `BankerConfig` minus the two `permissionManager.addPermission(BankCommand.*)` lines; kept `PermissionManager permissionManager` param verbatim on `bankerSettings(...)` per the checklist's load-order-params list even though now body-unused. Added `implements org.luckyraven.gangland.data.economy.BankTierView` to the `BankTier` record (`BankTier.java:18`, no drift, all 5 accessors already matched). `git rm`'d core `config/BankerConfig.java`. **Drift found in the checklist itself, not the code:** T13's own item-4 text says the two dropped permission lines "move to core in T16", but §1.3's table row for `BankerConfig` and T14's own body both say `DataConfig.registerGanglandPermissions()` (T14) — confirmed by reading T14 in full and by `DataConfig.java` already resolving `PermissionManager` from `context.get(...)` inside that exact `@PostConstruct`, which is the pattern T14 says to follow. Treated T14 as authoritative (executed the two lines there, not in T16) since T16's own body (read in full) never mentions `BankCommand` at all. Per-task done-when checks (both explicitly deferred to run after T14): `grep -c copsncrooks config/GadgetConfig.java` = 0, `grep -c "\.install("` on `CopsNCrooksModuleConfig.java` = 4 — both confirmed after T14/T15 landed. Bean count check (not in the checklist's done-when, self-verified): 37 `@Bean` in `CopsNCrooksModuleConfig` before the two contribution beans were added per the scrum master's addendum (39 after). |
| T14 `CopsAndGadgetsConfig` → `GadgetConfig` + `DataConfig` (p0-w3) | done | sonnet-exec | `git mv config/CopsAndGadgetsConfig.java config/GadgetConfig.java`, renamed class/constructor to `GadgetConfig`, deleted all cops imports/beans, kept `carMessageContract()`, `carService()`, `jetpackService()` **and `carAccessPolicy()`** (already present from the `0.8.3`/p0-wave-3 merge — no conflict encountered since that merge had already landed before this session started, confirmed by `git log`; the bean and its `GD-06` javadoc carried over verbatim) — rewrote the class javadoc to describe gadget-only wiring. `DataConfig.java`: added 3 seam `@Bean`s (`moneyDropClassifier()` → `new GanglandMoneyDropClassifier()`, `bankTiers()`, `wantedKillTrackers()`) plus, inside the existing `registerGanglandPermissions()` `@PostConstruct` (which already resolves `PermissionManager` via `context.get(...)`, matching the "Watch out" note's expected pattern), the two lines `permissionManager.addPermission(BankCommand.BYPASS_CAP_PERMISSION/ADMIN_PERMISSION)` lifted from the old `BankerConfig` with a comment; added the `BankCommand`/`BankTiers`/`GanglandMoneyDropClassifier`/`WantedKillTrackers`/`MoneyDropClassifier` imports. Done-when confirmed: `test ! -f config/CopsAndGadgetsConfig.java` (gone), `grep -c copsncrooks config/GadgetConfig.java` = 0. Full-reactor `mvn clean install -DskipTests`: `Gangland Gadgets` and `Gangland Domain`/`Gangland Turf` modules (which depend on nothing this task touches) built SUCCESS; `gangland-impl` still fails only on `FileConfig.java` (T16's file, unrelated to this task) — confirms `GadgetConfig.java`/`DataConfig.java` themselves compile clean. |
| T15 `TraderModuleConfig` + `TurfNpcsModuleConfig` + `ShopConfig` remainder | done | sonnet-exec | Created `copsncrooks/config/TraderModuleConfig.java` (16 `@Bean`s, verified by count) + its `registerPermissions()` `@PostConstruct` lifted verbatim from `ShopConfig` (registers `ShopViewOpenerImpl.ADMIN_PERMISSION`). Created `copsncrooks/config/TurfNpcsModuleConfig.java` — whole of old `TurfNpcsConfig` (10 beans, verified), with `turfNpcContract(...)` renamed to `turfNpcContractImpl(...)` returning the concrete `TurfNpcContractImpl` (not the `TurfNpcContract` interface) so nothing registers a second bean under that interface; kept all three `@SuppressWarnings("unused") Settings settings` powerup-view params. `git rm`'d core `config/TurfNpcsConfig.java`. Rewrote core `config/ShopConfig.java` down to the 15 shop-api beans that stay (verified by count) — deleted the 16 trader beans + `registerPermissions()` + the now-unused `context`/`GanglandContext` field+param (shrunk constructor to `(Gangland gangland)`); for the two beans that keep a `TraderSettings`-typed param in the original (`priceEditorView`, `shopAdminView`), replaced the parameter with a private inline field `ShopUiSettings shopUiSettings = new GanglandShopUiSettings()` per the checklist's ambiguity guard (`sellCategoryItemsAdminView`/`barterCategoryItemsAdminView` never took `TraderSettings` in the current code, so only those two needed the swap — checklist's "four beans" phrasing appears to be its own imprecision; recorded as a drift note, not acted on further since only two `TraderSettings`-typed params existed to fix). Created `file/configuration/shop/GanglandShopUiSettings.java` (`implements ShopUiSettings`, 3 methods copied verbatim from `TraderSettingsImpl`). Done-when confirmed: `grep -c copsncrooks config/ShopConfig.java` = 0; `ls config/ | grep -E "TurfNpcsConfig\|BankerConfig"` empty. **Scrum-master addendum (not a checklist task, ordered after T12's report):** added `bankMenuContribution(Gangland, BankerFlow)` and `turfPowerupNpcContribution(Gangland, TurfManager, WandSelectionManager, TurfMessageContract, TurfPowerupManager)` `@Bean` methods to `CopsNCrooksModuleConfig` (constructed from the exact constructor params `BankMenuContribution`/`TurfPowerupNpcContribution` already declare, per T12's own gap note) — without these, `CommandContributions.from(container)` never sees either contribution and `/glw bank menu` / `/glw turf powerupnpc` compile but are unreachable. **Gate (group H end, per orchestrator instruction — group H itself has no gate in §0's table):** `mvn clean install -DskipTests 2>\|grep -E "^\[ERROR\].*\.java"\|sort -u > cops-errors-after-T15.txt` → exactly 5 lines, all `config/FileConfig.java` (T16's file: `copsncrooks.npc.civilian.config`/`copsncrooks.npc.police.config` package-not-found + 3 "cannot find symbol" for `CopSettings`/`CivilianSettings`/`GanglandCivilianSpawnConfigProvider`). Full untruncated log cross-checked (`grep -oE "gangland-impl/src/main/java/[^:]+\.java"\|sort -u` = exactly 1 distinct file). Reactor: everything through `Gangland Turf` is SUCCESS (incl. `Gangland Gadgets`, `Gangland Domain`); `Gangland` (impl) FAILS on `FileConfig.java` only; `Gangland Mail`/`Cops N Crooks`/version modules SKIPPED. `gangland-impl` does **not** compile yet — expected, T16/T17 own the remaining work; did not proceed into T16 per instructions. |
| T16 `FileConfig` remainder + package rename | done | sonnet-exec | Deleted `CivilianSettings`/`CopSettings` imports and the three beans `copSettings()`, `civilianSettings()`, `civilianSpawnConfigProvider()` from `config/FileConfig.java`; replaced the wildcard import `file.configuration.copsncrooks.*` with explicit imports of `file.configuration.wanted.GanglandBountySettings`/`GanglandWantedSettings`. `git mv file/configuration/copsncrooks → file/configuration/wanted` (the 2 remaining files), fixed both `package` lines. Created `copsncrooks/config/CopsNCrooksFileConfig.java` (`@Configuration(phase = Phase.FILE)`) holding the 3 beans unchanged, importing `CopSettings`/`CivilianSettings` from `copsncrooks.npc.{police,civilian}.config` and `GanglandCopSettings`/`GanglandCivilianSettings`/`GanglandCivilianSpawnConfigProvider` from `copsncrooks.integration.config` (their T7 destination). No drift from the checklist text. **Done-when partially fails, not by my edits:** `grep -rn "copsncrooks" gangland-impl/src/main/java/ \| wc -l` == 1, not 0 — the sole hit is `config/TurfConfig.java:203`, a javadoc comment ("...inert until the copsncrooks module installs TurfNpcContractImpl...") that T8 (group C, already `done`) wrote **verbatim from the checklist's own T8 text**. This is a self-contradiction in the checklist (T8's mandated wording vs. T16/G1's "zero copsncrooks substring" gate), not a bug in my task's scope — left untouched. Confirmed the narrower, FQN-anchored grep the orchestrator's own G1 mandate uses (`org\.luckyraven\.gangland\.copsncrooks\.`) does **not** match this comment (no trailing dot after `copsncrooks`), so the mandatory gate as given to this executor still passes empty; only the checklist's own broader literal-substring check (§G1 second line, and T16's own done-when) is affected. Flagging for the scrum master rather than editing a group-C file outside this task's scope. |
| T17 YAML + commands.json + `KernelConfig` | done | sonnet-exec | `git mv` the 5 YAML files (`npc/cops.yml`, `npc/civilians.yml`, `npc/trader_traits.yml`, `npc/bank_tiers.yml`, `turf/turf_npcs.yml`) from `gangland-impl/src/main/resources/` to `gangland-features/cops-n-crooks/src/main/resources/` at byte-identical relative paths; `rmdir`'d the now-empty `gangland-impl/src/main/resources/npc/`; `gangland-impl/src/main/resources/turf/` now holds only `turf_powerups.yml`. Checked all 5 moved YAMLs for a literal `${` — none found, no filtering hazard. `config/KernelConfig.java`: deleted the 5 `fm.addFile(...)` lines for `cops`/`civilians`/`trader_traits`/`bank_tiers` (all `npc`) and `turf_npcs` (`turf`); kept `turf_powerups` (`turf`) untouched. Created `copsncrooks/config/CopsNCrooksYamlConfig.java` verbatim from the checklist's code block (`@Configuration(phase = Phase.KERNEL)`, ctor `(Gangland gangland)`, `copsNCrooksFiles(FileManager, ModuleLoader)` bean using `moduleLoader.classLoader()` and the 5-arg `FileHandler(gangland, name, directory, ".yml", loader)` calls, `CopsNCrooksFiles` marker class). `commands.json` split: wrote a one-off Python script (verified all 43 keys existed in the core file before removal, verified zero overlap after) that moved the exact 43 keys listed in §1.4 from `gangland-impl/src/main/resources/commands.json` into `gangland-features/cops-n-crooks/src/main/resources/commands.json` (which had been `{}` since T2), preserving each entry's `usage`/`description` strings and the file's tab-indented style byte-for-byte (spot-checked `head`/`tail` with `cat -A` against the pre-existing house style and against `gangland-mail`'s `commands.json`). Done-when confirmed: `test ! -d gangland-impl/src/main/resources/npc` (gone); `ls gangland-impl/src/main/resources/turf` → only `turf_powerups.yml`; core `commands.json` count 225→**182**; module `commands.json` count 0→**43**; zero key overlap between the two files. |
| **Gate G1** compile + grep | **FAILED — blocked, outside T16/T17 scope** | sonnet-exec | `mvn clean install -DskipTests -q` fails with exactly one root cause, 3 `[ERROR]` sites, all in one file that neither T16 nor T17 touches: <br>`gangland-impl/src/main/java/org/luckyraven/gangland/data/placeholder/worker/GanglandPlaceholder.java:[261,63] cannot find symbol` — `symbol: method interestRate()` — `location: variable tier of type org.luckyraven.gangland.data.economy.BankTierView`<br>`GanglandPlaceholder.java:[265,90] cannot find symbol` — `method weeklyLoanAmount()` — same location type<br>`GanglandPlaceholder.java:[268,90] cannot find symbol` — `method monthlyLoanAmount()` — same location type<br>**Root cause (pre-existing, group B — T4/T5, both already marked `done`):** the `BankTier` record (`copsncrooks/.../npc/banker/tier/BankTier.java`) has 10 components including `interestRate`, `weeklyLoanAmount`, `monthlyLoanAmount`, but T4's `BankTierView` interface was scoped to exactly the 5 accessors the checklist named (`id, displayName, maxBalance, dailyDepositLimit, deathLossDiscount`) — matching only the 3 call sites T4/T5's own text enumerated (`BankDepositCommand`, `PlayerDeathListener`, and `GanglandPlaceholder:233-243`). T5's checklist text claimed `GanglandPlaceholder`'s tier-reading code ends at `:236-243`; in the actual file (post-p0-wave-3 merge) that block continues further, with `bank_interest_rate`/`bank_weekly_amount`/`bank_monthly_amount` placeholders at `:261,265,268` reading 3 more `BankTier` fields the interface never exposed. This is a genuine gap in the seam-2 design (§1.6 row 2), not something T16 or T17's own "Do:" steps name a file for. Per the executor brief ("fix only what is inside T16/T17's scope... if the failure is outside your scope, stop and report"), **left unfixed** — no edit made to `BankTierView.java` or `GanglandPlaceholder.java`. `grep -rn "org\.luckyraven\.gangland\.copsncrooks\." gangland-impl/src` and `grep -rn "copsncrooks" gangland-impl/src/main/java \| wc -l` were **not** run to a final pass/fail verdict beyond what T16's row already recorded, since the mandatory `mvn` half of G1 did not go green. Confirmed via the build log that GanglandPlaceholder.java is the **only** file with any error (`grep -oE "gangland-impl/src/main/java/[^:]+\.java" build.log \| sort -u` → 1 result) and that `FileConfig.java`, `KernelConfig.java`, `CopsNCrooksYamlConfig.java`, `CopsNCrooksFileConfig.java` produced zero errors — T16/T17's own edits compile clean. **Recommended fix for the scrum master/T4 owner:** add `double interestRate();`, `BigDecimal weeklyLoanAmount();`, `BigDecimal monthlyLoanAmount();` to `BankTierView` (matching `BankTier`'s remaining record components, which already satisfy them with no code change on the `BankTier` side) — a 3-line, low-risk addition; then re-run G1. |
| T18 tests | done | sonnet-exec | `git mv` all 3 impl tests to their §1.5 destinations: `GanglandSeizedInventoryServiceTest` → `copsncrooks/integration/detainment/` (package fixed; `SeizedInventory` import kept — different package, same as `GanglandSeizedInventoryService`'s own T7 destination); `DetainmentRepositoryMigrationTest`/`DetainmentRepositorySpiTest` → `copsncrooks/database/` (package fixed; dropped the `database.tables.copsncrooks.{DetainmentTable,JailTable}` imports — both types now live in the *same* flat `copsncrooks.database` package per T9, so the import lines are gone, not rewritten; kept the `copsncrooks.detainment.{DetainedPlayer,DetainmentState}` imports, which are still a different package). All three verified individually green first: `mvn -pl gangland-features/cops-n-crooks test -Dtest=GanglandSeizedInventoryServiceTest,DetainmentRepositoryMigrationTest,DetainmentRepositorySpiTest` → 6+4+5 = 15 tests, 0 failures (ran as three separate single-class invocations since a comma-list under `-Dtest` was routed to the wrong reactor module by `-am`; documented here, not a scope change). `InformationManagerTest.java:41`: `assertEquals(225, …)` → `assertEquals(182, …)`, message rewritten to name the 43 cops/banker/trader/civilian/jail/cuff keys and "0.8.4" (was "0.8.2"). Created `copsncrooks/CopsNCrooksModuleTest.java` (3 tests) modelled on `MailModuleTest`: config-registration order + package lists, the three "declared package matches the real class" assertions (`DetainmentRepository`/`CopCommand`/`TraderBuyListener`, all verified against their actual current locations first), and the non-empty-command-packages test. Created `copsncrooks/seam/KillComboWantedTrackerTest.java` (4 tests): `countsForWanted` delegates to a mocked `EntityMarkManager`; the three `on*` installers verified via `ArgumentCaptor` to reach `KillCombo`'s Lombok `@Setter`s and translate `KillComboEvent`→`Player` (or pass the `UUID` straight through for `onVictimDeath`). Created `gangland-infra/gangland-domain/.../gang/wanted/WantedKillTrackersTest.java` (3 tests) and `gangland-impl/.../data/economy/BankTiersTest.java` (2 tests), both verbatim to the checklist's (a)/(b)/(c) and two-case descriptions. **Red-then-green shown for all 4 new tests, in order, by temporarily stubbing the production body (not "run before the class exists" — every seam/holder from T4/T6/T13 was already `done`):** `CopsNCrooksModule.configure()` stubbed to a no-op → `Tests run: 3, Failures: 2` (`configure_declaresConfigsAndPackages`, `commandPackages_isNotEmpty`; the packages-match test still passed, as expected since it doesn't call `configure`) → restored → green. `KillComboWantedTracker`'s three `on*` methods stubbed to no-ops → `Tests run: 4, Failures: 3` (all three forwarding tests; `countsForWanted` still passed) → restored → green. `WantedKillTrackers.install(...)` stubbed to skip the three replay lines → `Tests run: 3, Failures: 1` (only the replay test; the inert-by-default and after-install tests still passed) → restored → green. `BankTiers.tierFor(...)` stubbed to always return `null` → `Tests run: 2, Failures: 1` (only the after-install case, exactly as the checklist's own table predicted) → restored → green. **Blocking bug found and fixed, outside T18's own file list but required to reach G2 (recorded, not left for later):** `gangland-features/cops-n-crooks/.../config/CopsNCrooksModuleConfig.java` (T13's file, already marked `done`) was missing `import org.luckyraven.keystone.permission.PermissionManager;` — `bankerSettings(...)`'s param (kept per the checklist's own "load-order params to preserve" list) referenced the type unqualified. `mvn clean install -DskipTests` never main-compiled `cops-n-crooks` in any earlier gate (the reactor halted at `gangland-impl` test-compile first every time, since impl builds before the module in dependency order), so this was never actually exercised until T18 removed the last blocker ahead of it. One-line fix (added the import); everything else in the file untouched. **Self-contradiction, same class as T16's row, left as documented rather than reworded:** the checklist's own step 2 text ("update the message string ... to say the 43 ... keys moved to the copsncrooks module in 0.8.4") mandates the literal substring `copsncrooks` in `InformationManagerTest.java`, so this task's own done-when (`grep -rn "copsncrooks" gangland-impl/src/test \| wc -l == 0`) cannot both be satisfied and follow the checklist's literal instruction. Actual count: **1** (that one message string only — confirmed by `grep -rn copsncrooks gangland-impl/src/test`, no other hit). Not reworded to dodge the grep since the checklist dictates the exact wording. |
| **Gate G2** `mvn test` | **done** | sonnet-exec | `mvn test -q -B` (full reactor) → exit code **0**, no `[ERROR]` lines. Aggregated every `target/surefire-reports/*.txt` across all 21 modules: **`Tests run: 807, Failures: 0, Errors: 0, Skipped: 0`**. New/moved test files present with their expected counts: `CopsNCrooksModuleTest` 3, `KillComboWantedTrackerTest` 4, `WantedKillTrackersTest` 3, `BankTiersTest` 2, `DetainmentRepositoryMigrationTest` 6, `DetainmentRepositorySpiTest` 4, `GanglandSeizedInventoryServiceTest` 5, `InformationManagerTest` 5 (the 182-assertion test included). `grep -rn "copsncrooks" gangland-impl/src/test \| wc -l` = **1**, not 0 — see T18's note directly above (the mandated `InformationManagerTest` message string); the narrower `org\.luckyraven\.gangland\.copsncrooks\.` import-style check the sprint README's own G1/G2 wording uses stays empty. No `[ERROR] GanglandSeizedInventoryService] Failed to restore...` / `ShutdownSequence.ShutdownStage 'shutdown.save' failed` log lines in the run are test failures — both are `@CustomLog`/logger output from tests that deliberately drive a failure path (`restore_corruptBlob_*`, `ShutdownSequenceTest.run_forceUpdateThrows_stillClosesConnectionsAndBackend`) and are asserted against with `assertDoesNotThrow`/`verify(..., never())`, not raw exceptions. |
| T19 docs | done | sonnet-exec | `CLAUDE.md` (gitignored/untracked per `.gitignore:37`, so it never shows in `git diff` — expected, not a defect): Module Structure row for `gangland-features/cops-n-crooks` now says **Runtime module since 0.8.4**; "Two tiers" bullet rewritten — runtime modules = `gangland-mail` and, since 0.8.4, `cops-n-crooks`; remaining order **gadget → turf → weapon**; noted cops-n-crooks' `gangland-weapon`/`gangland-turf` deps are `provided` and gain `Depends:` at flips 3/4; fixed the stale "YAML defaults under `src/main/resources/<module>/`" wording to "in the module jar at the same path as the data folder (e.g. `npc/cops.yml`)"; added the `BankCommand`→`bank` / `TurfCommand`→`turf` contribution-path sentence beside the existing `gang`/`gang.ally` note (that note lives in the "Two tiers" bullet, not the separate "### Commands" architecture section — checklist's "Commands paragraph" phrasing matched to where the existing gang/gang.ally sentence actually is); Key Configuration Files bullet replaced the stale single `cops.yml` line with all 5 moved YAML paths and "no longer under `gangland-impl/src/main/resources/`... ship inside `modules/cops-n-crooks-<rev>.jar`"; YAML rule sentence at the bottom fixed the same stale `resources/<module>/` wording. `documentation/module-loader.md`: "What is a module today" table gets its own cops-n-crooks row (`runtime module since 0.8.4`) and the "still compile-time" row now lists only turf/weapon/gadget; "Order for the remaining flips" → **gadget → turf → weapon** plus the `Depends:` note; "Writing a module" YAML bullet rewritten to the data-folder-path rule (same fix as CLAUDE.md); new section titled exactly `## Core seams` added after "Attaching sub-arguments under a core command" and before "Faults you will see in the console", with a table of the 4 holders (`GanglandMoneyDropClassifier`/`NpcMoneyDropSource`, `BankTiers`/`BankTierView`, `WantedKillTrackers`/`WantedKillTracker`, `TurfNpcContracts`) and the 2 contribution paths (`bank`, `turf`), plus a note that later flips append rows here. `documentation/README.md`: checked for "compile-time dependency" / `compile-time` / `runtime module` / `module.yml` / `Depends:` mentions of cops-n-crooks — **none exist**, so no edit was needed there (its cops-n-crooks mentions are all feature-doc links, unrelated to module-flip status); recorded as verified-no-op rather than skipped. `brainstorming/module-split-2026-09-07/README.md` status board: flip-1 row's Execution status column updated to record T1–T20 executed and point at this file's §7 for gate evidence (this folder is untracked/local per its own README, so this edit does not appear in any `git diff`). Memory file `project_module_loader_plan.md`: **not edited** — per this task's own rules ("Memory files are the scrum master's; do not edit `~/.claude`"), left for the scrum master. Also reworded, outside T19's own file list but required to make the G1-style "zero `copsncrooks` substring" grep hold (per the orchestrator's clarification #2, flagged as self-contradictions by T16/T18's rows): `config/TurfConfig.java`'s `turfNpcContracts()` javadoc "the copsncrooks module installs" → "the cops-n-crooks module installs"; `InformationManagerTest.java:43`'s assertion message "moved to the copsncrooks module's" → "moved to the cops-n-crooks module's". No behaviour change either way (comment/message text only). `grep -rn "copsncrooks" gangland-impl/src/main/java \| wc -l` and the same for `src/test` are now both **0** (previously 1 line each, per T16/T18/G2's rows). **Done-when confirmed:** `grep -n "cops-n-crooks" CLAUDE.md documentation/module-loader.md` shows the module wording in both files (13 lines total); no remaining line in either file calls cops-n-crooks a compile-time dependency of impl. |
| T20 **Gate G3 / G4** + `graphify update . --force` | done | sonnet-exec | **G3:** `mvn clean package -DskipTests -q -B` → exit 0 (build log is all `Adding inheritance ...` shade-plugin noise from the NMS version modules, no `[ERROR]`). `unzip -l target/gangland_warfare-0.8.4.jar \| grep "org/luckyraven/gangland/copsncrooks/"` → **empty** (count 0). `unzip -l target/gangland_warfare-0.8.4.jar \| grep -E "npc/(cops\|civilians\|trader_traits\|bank_tiers)\.yml\|turf/turf_npcs\.yml"` → **empty** (count 0). `unzip -l target/gangland_warfare-0.8.4.jar \| grep "turf/turf_powerups.yml"` → **PRESENT** (`2591  2026-09-07 21:00   turf/turf_powerups.yml`). `unzip -l target/modules/cops-n-crooks-0.8.4.jar \| grep -E "module\.yml\|commands\.json"` → both present at jar root (`267` and `5955` bytes). `unzip -l target/modules/cops-n-crooks-0.8.4.jar` filtered to `.yml$` → the 5 expected files present at their exact paths (`module.yml`, `npc/bank_tiers.yml`, `npc/civilians.yml`, `npc/cops.yml`, `npc/trader_traits.yml`, `turf/turf_npcs.yml`); `org/luckyraven/gangland/copsncrooks/module.properties` present; every `.class` entry's package root reduces (via `sed`) to exactly one distinct value, `org/luckyraven/gangland/copsncrooks` — no core class leaked into the module jar. `unzip -p target/modules/cops-n-crooks-0.8.4.jar module.yml` → `Id: copsncrooks`, `Version: 0.8.4` (resolved from `${project.version}`), `Host_Api: 0.8`, `Artifact: org.luckyraven:cops-n-crooks`, **no `Depends:` key** — matches §0's "Depends: now []" statement. `unzip -p target/modules/cops-n-crooks-0.8.4.jar commands.json \| python -c "...len(json.load(...))"` → **43**. Core `commands.json` count (not in the checklist's own G3 command list but cross-checked against §1.4's "225 → 182" claim) → **182**. All pasted verbatim above/below this row. **G4:** `git diff --stat -- CLAUDE.md documentation/` → `CLAUDE.md` does not appear (it is gitignored — `.gitignore:37` — so this is expected, not a gate failure; its edits are confirmed present on disk and by the grep below) and `documentation/module-loader.md \| 49 +++++++++++++++++++++++++++++++++++-------` (41 insertions, 8 deletions) — `documentation/README.md` shows no diff because T19 found nothing to change there (verified no-op, recorded above). `grep -n "cops-n-crooks" CLAUDE.md documentation/module-loader.md` → 13 matching lines across both files (pasted under T19's row), all carrying the runtime-module wording, none calling it compile-time. Finally ran `graphify update . --force` (AST-only): **"Rebuilt: 15757 nodes, 42954 edges, 569 communities"**; `graph.html` skipped ("Graph has 15757 nodes - too large for HTML viz (limit: 5000)") as expected per CLAUDE.md's own note that `graph.html` is not regenerated above 5000 nodes; one non-fatal warning about 5 source files (bugs.json/observations.json/three commands.json files) producing zero AST nodes, unrelated to this flip's edits. Graph is now newer than the working tree's edits. **Nothing skipped; no open questions for this group.** |
| G5 review | done | scrum master + code-review agent | Critical: `DataConfig.moneyDropClassifier()` declared as the `MoneyDropClassifier` interface → `context.get(GanglandMoneyDropClassifier.class)` null → NPE in `installCoreSeams()` with the module present. Fixed: return type is now the concrete holder. Pinned by new `config/HolderSeamBeanTypeTest` (4 tests; red on the old signature, green after). Also fixed at G1: `BankTierView` gained `interestRate()`, `weeklyLoanAmount()`, `monthlyLoanAmount()`. Everything else in the review checked out (seam parity, poms, contributions, YAML paths, `GadgetConfig`, `ShopConfig`). |
