# Flip 3: gangland-turf → runtime module `turf`

**Assumes done:** flip 1 (`cops-n-crooks` → module `copsncrooks`) and flip 2 (`gangland-gadget` → module `gadget`).
`gangland-mail` has been a module since 0.8.2. **`gangland-weapon` is still a compile-time dependency of
`gangland-impl`** when this flip runs — turf does not depend on weapon, so that is irrelevant here except in the
gates (the core jar still contains `org/luckyraven/gangland/weapon/`).
**Module:** `gangland-features/gangland-turf` · id `turf` · package root `org.luckyraven.gangland.turf` · jar
`target/modules/gangland-turf-0.8.4.jar` · `Depends:` **none** (turf depends on nothing but the core).
**This flip also edits the already-flipped `copsncrooks` module**: its pom keeps `gangland-turf` but at `provided`
scope, and its `module.yml` gains `Depends:` with `- turf` (block-style list), because `cops-n-crooks` code imports
`org.luckyraven.gangland.turf.*` (13 import sites, listed in §1.2).
**Planner:** opus, 2026-09-07. **Graph:** `graphify-out/graph.json` refreshed 2026-09-07 16:28 (newer than HEAD
`d6bb33ac`, committed 16:11) — fresh, no rebuild needed before planning.

---

## 0. Summary for the scrum master

| | |
|---|---|
| Impl main files that name a turf type today | **30** (`grep -rl "org\.luckyraven\.gangland\.turf\." gangland-impl/src/main/java`) |
| Of those, flip 1 is expected to have already taken | **4** (`config/TurfNpcsConfig.java`, `file/configuration/turf/TurfNpcContractImpl.java`, `file/configuration/turf/TurfPowerupOpenContractImpl.java`, `command/sub/turf/TurfPowerupNpcCommand.java` — each names a `copsncrooks` type) |
| **Impl files this flip MOVES** | **26** → 17 commands, 3 repositories, 3 tables, 2 contract impls, 1 config class (`TurfConfig.java`, moved+renamed to `TurfModuleConfig`) |
| Impl files SPLIT | **0** — no impl file mixes core and turf concerns; `TurfConfig` is 100 % turf |
| Impl files KEPT (turf-aware but feature-type-free) | **3**: `file/configuration/Settings.java` (33 plain `Turf.*` scalar keys, no turf types), `file/configuration/Messages.java` (`TURF_*` enum constants, no turf types), `config/KernelConfig.java` (loses 1–2 `FileHandler` lines only) |
| **New seams introduced** | **none.** Turf needs no core→module callback. The one core→feature call path (`/glw turf powerupnpc`, which needs the cops `TurfPowerupManager`) is already solved by flip 1 through the existing `CommandContribution` seam; this flip reuses it unchanged. It also inherits flip 1's `TurfNpcContracts` holder — the class already lives in `gangland-turf` (`turf/turfnpcs/`), and only its `@Bean` method rides along with `TurfConfig` into `TurfModuleConfig` (§1.3 row 21). Nothing is added to the `## Core seams` doc section. |
| Resources moved | `gangland-impl/src/main/resources/turf/turf_powerups.yml` → `gangland-features/gangland-turf/src/main/resources/turf/turf_powerups.yml` (54 lines). `turf_npcs.yml` is **cops'**, not turf's (§1.4). |
| `commands.json` | 16 or 17 keys leave core (17 minus `turf_powerupnpc` if flip 1 took it) → new module `commands.json`. `InformationManagerTest` count drops by exactly that many; **the executor computes the number in T0, it is not hard-coded here** (flip 1 and flip 2 both changed it first). |
| Tests | 17 turf test files already live in `gangland-turf/src/test` — **nothing to move**. 0 impl tests reference a turf type. 1 new test: `TurfModuleTest`. |
| **p0-wave-3 overlap** | **NONE.** The `p0-wave-3` worktree edits `config/CopsAndGadgetsConfig.java`, `config/GameplayConfig.java`, `command/sub/gang/*`, `command/sub/waypoint/*`, `command/sub/debug/DebugCommand.java`, `data/placeholder/worker/GanglandPlaceholder.java`, `file/configuration/Messages.java`, `sign/GanglandSignInformation.java`, gadget `listener/car/*`, `message_en/es.yml`. This flip touches none of them. Adjacent-but-safe: this flip edits `commands.json` (p0-wave-3 edits `message_*.yml`, a different file) and *reads* `Messages.java` without editing it. |
| Task groups | **A** (T1–T2, 6 files) · **B** (T3–T4, 19 files) · **C** (T5–T9, 15 files) · **D** (T10–T14, 8 files). Compile gate after each. |
| Biggest risks | 1. **Flip-1 contract drift** — this flip inherits four flip-1 outputs: the `CommandContribution` at parent `turf`, the widened `TurfSelectionResolver`, the `turfNpcContracts()` bean in `TurfConfig`, and the removal of `turf_npcs.yml` + its `KernelConfig` line. All four are the *confirmed* cops design (consistency review findings 1, 2, 9), but T0 verifies each before anything moves. 2. **`turf/turf_powerups.yml` must vanish from the core jar** or the parent-first module classloader silently keeps serving the stale core copy (`FileHandler.java:202-205`). 3. **Reactor cycle** — the impl-pom and turf-pom edits must land in the *same* commit/task or Maven fails with `The projects in the reactor contain a cyclic reference`. |

---

## 1. Inventory (facts, with source locations)

### 1.1 Impl files that reference the feature

All 30 rows below come from
`grep -rl "org\.luckyraven\.gangland\.turf\." gangland-impl/src/main/java` (30 hits, run 2026-09-07).
"p0-w3?" = touched by the `p0-wave-3` worktree — **no** for every row.

Target packages: commands → `org.luckyraven.gangland.turf.command`; repositories + tables →
`org.luckyraven.gangland.turf.database`; contract impls → `org.luckyraven.gangland.turf.config`; the module
entry/config classes → the module root `org.luckyraven.gangland.turf`.

| # | path (under `gangland-impl/src/main/java/org/luckyraven/gangland/`) | role | uses from turf | decision | p0-w3? |
|---|---|---|---|---|---|
| 1 | `command/sub/turf/TurfCommand.java` | command (`@CommandHandler`, `TurfCommand.java:34-35`) | `TurfManager`, `WandSelectionManager`, `TurfMessageContract`, `GarrisonManager`, `PowerupRegistry`, `ActiveBuffManager`, `Turf`, `GangDisplayNameResolver` | **MOVE** → `turf.command` | n |
| 2 | `command/sub/turf/TurfWandCommand.java` | sub-argument | `WandSelectionManager` | **MOVE** → `turf.command` | n |
| 3 | `command/sub/turf/TurfPos1Command.java` | sub-argument | `WandSelectionManager` | **MOVE** → `turf.command` | n |
| 4 | `command/sub/turf/TurfPos2Command.java` | sub-argument | `WandSelectionManager` | **MOVE** → `turf.command` | n |
| 5 | `command/sub/turf/TurfCreateCommand.java` | sub-argument | `TurfManager`, `CuboidRegion`, `Selection` | **MOVE** → `turf.command` | n |
| 6 | `command/sub/turf/TurfDeleteCommand.java` | sub-argument | `TurfManager`, `Turf` | **MOVE** → `turf.command` | n |
| 7 | `command/sub/turf/TurfSetOwnerCommand.java` | sub-argument | `TurfManager`, `TurfOwnerChangedEvent` | **MOVE** → `turf.command` | n |
| 8 | `command/sub/turf/TurfListCommand.java` | sub-argument (+ static `sendRow`, used by `TurfCommand.java:174`) | `TurfManager`, `Turf` | **MOVE** → `turf.command` | n |
| 9 | `command/sub/turf/TurfInfoCommand.java` | sub-argument (+ static `renderInfo`, used by `TurfCommand.java:90`) | `TurfManager`, `Turf` | **MOVE** → `turf.command` | n |
| 10 | `command/sub/turf/TurfShowCommand.java` | sub-argument | `TurfVisualization`, `TurfManager`, `Selection` | **MOVE** → `turf.command` | n |
| 11 | `command/sub/turf/TurfStatusCommand.java` | sub-argument | `TurfRuntimeState`, `CapturePhase`, `TurfManager` | **MOVE** → `turf.command` | n |
| 12 | `command/sub/turf/TurfSelectCommand.java` | sub-argument | `TurfManager`, `WandSelectionManager` | **MOVE** → `turf.command` | n |
| 13 | `command/sub/turf/TurfTpCommand.java` | sub-argument | `TurfManager`, `Turf` | **MOVE** → `turf.command` | n |
| 14 | `command/sub/turf/TurfIncomeCommand.java` | sub-argument | `TurfManager`, `Turf` | **MOVE** → `turf.command` | n |
| 15 | `command/sub/turf/TurfGarrisonCommand.java` | sub-argument | `GarrisonManager`, `TurfManager` | **MOVE** → `turf.command` | n |
| 16 | `command/sub/turf/TurfBuffCommand.java` | sub-argument | `PowerupRegistry`, `PowerupDefinition`, `ActiveBuffManager` | **MOVE** → `turf.command` | n |
| 17 | `command/sub/turf/TurfSelectionResolver.java` | command helper (`final class`, package-private today — `TurfSelectionResolver.java:24`) | `TurfManager`, `WandSelectionManager`, `Selection`, `Turf` | **MOVE** → `turf.command` | n |
| 18 | `command/sub/turf/TurfPowerupNpcCommand.java` | sub-argument that **also** imports `copsncrooks.npc.turf.TurfPowerupManager` (`TurfPowerupNpcCommand.java:9`) | `TurfManager`, `WandSelectionManager`, `Turf` | **flip 1 owns it** — expect it gone. If still present: see §6 Q1 fallback | n |
| 19 | `config/TurfConfig.java` | `@Configuration` (CONFIG phase), 20 `@Bean` methods, 100 % turf | everything (see §1.3) | **MOVE + rename** → `org.luckyraven.gangland.turf.TurfModuleConfig`; delete from core | n |
| 20 | `config/TurfNpcsConfig.java` | `@Configuration`, 8 `@Bean` methods, **all 8 produce or consume `copsncrooks` types** (`TurfNpcsConfig.java:4-13,16-20`) | `TurfManager`, `ActiveBuffManager`, `GarrisonManager`, `PowerupRegistry`, `TurfNpcContract` | **flip 1 owns it** — expect the whole file gone. If a turf-only remainder survived: fold it into `TurfModuleConfig` (T7) | n |
| 21 | `database/repositories/turf/TurfRepository.java` | `@Repository(Turf.class)` implementing `TurfRepositoryContract` (`TurfRepository.java:21-22`) | `Turf`, `CuboidRegion`, `TurfRepositoryContract` | **MOVE** → `turf.database` | n |
| 22 | `database/repositories/turf/ActiveTurfBuffRepository.java` | `@Repository` implementing `ActiveBuffRepositoryContract` | `ActiveTurfBuff`, contract | **MOVE** → `turf.database` | n |
| 23 | `database/repositories/turf/TurfGarrisonRepository.java` | `@Repository` implementing `GarrisonRepositoryContract` | `Garrison`, contract | **MOVE** → `turf.database` | n |
| 24 | `database/tables/turf/TurfTable.java` | `Table<Turf>` (`TurfTable.java:10-12`, table name `"turf"`) | `Turf` | **MOVE** → `turf.database` | n |
| 25 | `database/tables/turf/ActiveTurfBuffTable.java` | `Table<ActiveTurfBuff>` | `ActiveTurfBuff` | **MOVE** → `turf.database` | n |
| 26 | `database/tables/turf/TurfGarrisonTable.java` | `Table<Garrison>` | `Garrison` | **MOVE** → `turf.database` | n |
| 27 | `file/configuration/turf/GanglandTurfMessages.java` | contract impl — routes `TurfMessageContract` through core `Messages` (`GanglandTurfMessages.java:18-22`) | `TurfMessageContract` | **MOVE** → `turf.config` | n |
| 28 | `file/configuration/turf/GanglandTurfSounds.java` | contract impl — `TurfSoundContract` over `Settings` + Keystone `SoundEffect` | `TurfSoundContract` | **MOVE** → `turf.config` | n |
| 29 | `file/configuration/turf/TurfNpcContractImpl.java` | implements turf's `TurfNpcContract` **using cops types** (`TurfNpcsConfig.java:76` builds it from `TurfDefenderDeployer`, `TurfPowerupManager`) | `TurfNpcContract` | **flip 1 owns it** — expect it gone (cops module) | n |
| 30 | `file/configuration/turf/TurfPowerupOpenContractImpl.java` | implements **cops'** `TurfPowerupOpenContract` using `TurfManager` | `TurfManager` | **flip 1 owns it** — expect it gone (cops module) | n |

Two more files sit in the same directories but do **not** import a turf type and therefore belong to flip 1:
`database/repositories/turf/TurfPowerupNpcRepository.java` and `database/tables/turf/TurfPowerupNpcTable.java`
(both named by `TurfNpcsConfig.java:16,65`), plus `file/configuration/turf/TurfNpcsConfigLoader.java`
(`TurfNpcsConfigLoader.java:5-6` imports only `copsncrooks` types). **This flip must leave them alone**; if they are
still in core at T0, see §6 Q2.

Core files that mention "turf" but name **no** turf type — all **KEEP**:

- `file/configuration/Settings.java:219-248` (field block) and `:730-780ish` (loader): 33 scalar getters
  (`getTurfIncomeIntervalMinutes`, `getTurfCaptureDurationSeconds`, …) read from the `Turf:` block of the shared
  `settings.yml` (`gangland-impl/src/main/resources/settings.yml:703`). Per PLANNER-BRIEF rule 5 plain feature
  config keys stay in core and the module reads `Settings` directly — which `TurfConfig.java:114-127,177-183` and
  `GanglandTurfSounds` already do. **No edit.**
- `file/configuration/Messages.java`: `TURF_*` enum constants only, consumed via `Messages.valueOf(key)` in
  `GanglandTurfMessages.java:22`. **No edit.** (This is a `p0-wave-3` file — leaving it untouched is what keeps this
  flip conflict-free.)
- `config/KernelConfig.java:187-188`: the two `FileHandler` registrations for `turf/*.yml`. Line 187
  (`turf_powerups`) moves to the turf module (T8); line 188 (`turf_npcs`) belongs to cops (§1.4).
- `gangland-impl/src/test/java/.../command/extension/CommandContributionsTest.java:56` contains the *string*
  `"turf"` in `assertFalse(contributions.hasAny("turf"))`. No dependency. **KEEP, no edit.**

`gangland-infra/gangland-domain` has **no** turf types — only two prose comments
(`gang/contract/GangLookupContract.java:9`, `gang/Gang.java:42`). Nothing to do.
`scoreboard/**`, `sign/**`, `data/placeholder/**`, `data/economy/**` and `Gangland.java` (`bStats()` names only
`WeaponAddon`, `Gangland.java:117`) contain **zero** turf references.

### 1.2 Feature-side files that reference impl

`gangland-turf/src/main/java` imports **no** `gangland-impl` type today. Its only non-`turf` `org.luckyraven`
imports are (`grep -rh "^import org.luckyraven" gangland-features/gangland-turf/src/main/java | sort -u`):

`org.luckyraven.gangland.gang.{Gang, contract.GangLookupContract, contract.UserLookupContract, member.Member,
user.User}` (all **gangland-domain**, core jar) and Keystone (`bean.BeanLifecycle`, `bean.listener.ListenerHandler`,
`exception.PluginException`, `item.ItemBuilder`, `persistence.{FileHandler, FileManager, config.*, repository.IRepository}`,
`util.{ActionBarManager, ChatUtil}`). After this flip the module *does* gain impl imports (`Gangland`, `Messages`,
`Settings`, `GanglandChatUtil`, `command.Command`) via the moved files — that is the allowed module → core direction.

**Cops-n-crooks (already a module) imports turf** at 13 sites in 6 files — this is why cops needs
`Depends: [turf]` (`grep -rn "org\.luckyraven\.gangland\.turf\." gangland-features/cops-n-crooks/src`):

| cops file | turf types imported |
|---|---|
| `listener/turf/TurfFriendlyFireListener.java:20-21` | `turf.data.Turf`, `turf.manager.TurfManager` |
| `npc/turf/view/TurfPowerupBuffCatalogueView.java:16-18` | `turf.powerups.{ActiveBuffManager, PowerupDefinition, PowerupRegistry}` |
| `npc/turf/view/TurfPowerupFlow.java:8` | `turf.data.Turf` |
| `npc/turf/view/TurfPowerupFlowSession.java:6` | `turf.data.Turf` |
| `npc/turf/view/TurfPowerupGarrisonView.java:16-17` | `turf.data.Turf`, `turf.powerups.GarrisonManager` |
| `npc/turf/view/TurfPowerupMenuView.java:14-16` | `turf.data.Turf`, `turf.powerups.{ActiveBuffManager, GarrisonManager}` |

**Should any of that be a seam instead?** No. All six are cops UI/listener classes *consuming* turf read models
(`Turf`, the two powerup managers, the registry) — the dependency direction cops → turf is the same direction the
poms already encode, and Keystone's module loader supports exactly this through `Depends:` (a shared parent-first
`ModuleClassLoader`, `GanglandContext.java:66`). Inverting it would mean inventing a turf-side seam for a read model
that turf itself owns — more machinery, no decoupling. **Keep the direct dependency; add `Depends: - turf`.**

### 1.3 Beans the feature contributes today

Every `@Bean` in `config/TurfConfig.java`. The class carries a bare `@Configuration`, and
`Configuration.phase()` defaults to `Phase.CONFIG` (Keystone `keystone-bean/.../Configuration.java:37`), so they are
**all CONFIG-phase beans**. **20 exist today; flip 1 adds a 21st** (`turfNpcContracts()`, row 21 below — see
consistency review finding 1), so `TurfModuleConfig` must carry **21**. All of them move verbatim into
`org.luckyraven.gangland.turf.TurfModuleConfig` (also `@Configuration`, default CONFIG phase). **Do not reorder or
drop parameters** — the `@SuppressWarnings("unused") Settings settings` parameters at `TurfConfig.java:177` are pure
ordering edges (memory rule *feedback_bean_ordering_via_params*).

| # | method (`TurfConfig.java`) | line | parameters | notes |
|---|---|---|---|---|
| 1 | `turfRepositoryContract` | 41 | `TurfRepository` | concrete repo comes from the DATABASE phase hook (`GanglandContext.java:199-212`) |
| 2 | `powerupRegistry` | 46 | — | |
| 3 | `powerupRegistryLoader` | 51 | `PowerupRegistry`, `FileManager` | ctor calls `fileManager.checkFileLoaded("turf_powerups")` (`PowerupRegistryLoader.java:30,40-41`) → the file must be registered in KERNEL (T8) |
| 4 | `activeBuffRepositoryContract` | 56 | `ActiveTurfBuffRepository` | |
| 5 | `garrisonRepositoryContract` | 61 | `TurfGarrisonRepository` | |
| 6 | `activeBuffManager` | 66 | `Gangland`, `ActiveBuffRepositoryContract` | calls `manager.initialize()` → `setDataSupplier` (`ActiveBuffManager.java:53-55`) |
| 7 | `garrisonManager` | 72 | `GarrisonRepositoryContract` | `initialize()` → `setDataSupplier` (`GarrisonManager.java:29`) |
| 8 | `turfMessageContract` | 78 | — | `new GanglandTurfMessages()` |
| 9 | `turfSoundContract` | 83 | — | `new GanglandTurfSounds()` |
| 10 | `turfDisplayContract` | 88 | — | lambda `Settings::isTurfShowEnterTitle` |
| 11 | `turfManager` | 93 | `TurfRepositoryContract` | `initialize()` → `setDataSupplier` (`TurfManager.java:61`) |
| 12 | `wandSelectionManager` | 99 | `PermissionManager` | registers `WandSelectionManager.ADMIN_PERMISSION` |
| 13 | `captureSettings` | 106 | — | reads 9 static `Settings` getters |
| 14 | `captureService` | 124 | `TurfManager`, `GangLookupContract`, `UserLookupContract`, `CaptureSettings`, `TurfSoundContract` | |
| 15 | `turfLocationTracker` | 133 | `Gangland`, `TurfManager`, `CaptureService` | `.start()` |
| 16 | `turfIncomeDistributor` | 140 | `Gangland`, `TurfManager`, `GangLookupContract`, `ActiveBuffManager` | `.start()` |
| 17 | `inactivityReleaseTask` | 151 | `TurfManager`, `GangLookupContract`, `CaptureSettings` | |
| 18 | `gangPresenceTracker` | 158 | `Gangland`, `GangLookupContract`, `UserLookupContract`, `CaptureSettings`, `InactivityReleaseTask` | `.start()` |
| 19 | `turfContributionSettings` | 172 | `Settings` (**ordering-only**, `@SuppressWarnings("unused")`) | |
| 20 | `turfContributionTickTask` | 181 | `Gangland`, `TurfManager`, `GangLookupContract`, `UserLookupContract`, `TurfContributionSettings` | `.start()` |
| **21** | **`turfNpcContracts()`** — **added to `TurfConfig.java` by flip 1** (cops T8 step 4, consistency review finding 1) | n/a (new) | — | returns `new TurfNpcContracts()`: the holder seam for the existing `TurfNpcContract`. The **holder class itself already lives in this module**, `gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/turfnpcs/TurfNpcContracts.java`, beside `TurfNpcContract.java` — so it **moves with the turf module as-is, no relocation and no package change**. The cops module installs into it from `@PostConstruct` (`CopsNCrooksModuleConfig.installCoreSeams()`), and `GarrisonDeployListener` consumes it. |

The trailing comment at `TurfConfig.java:191-196` (why `TurfBossBarListener` / `TurfCaptureNotifier` are **not**
`@Bean`s — a second instance would silently swallow events, memory rule *feedback_listener_bean_conflict*) must be
copied into `TurfModuleConfig` verbatim.

**Row 21 is the one bean in this table that does not exist at `d6bb33ac`.** Flip 1 adds it to
`config/TurfConfig.java` so that `GarrisonDeployListener` (turf) and `installCoreSeams()` (cops) have a holder to
meet in; when this flip moves `TurfConfig` into the module, the bean method comes along and keeps working — cops
still reaches it through the container after `Depends: turf` guarantees the turf module loaded first. **T0 step A
must confirm it is there**; if flip 1 skipped it, garrison deploy is already dead on `0.8.4` and that is flip 1's
bug, not ours — escalate rather than inventing a second holder bean.

`config/TurfNpcsConfig.java`'s 8 beans are all cops-owned (see row 20 of §1.1) — **flip 1's work list, not ours**.

### 1.4 Resources

| resource | today | after this flip | why |
|---|---|---|---|
| `turf/turf_powerups.yml` (54 lines) | `gangland-impl/src/main/resources/turf/turf_powerups.yml`, registered `KernelConfig.java:187` | **`gangland-features/gangland-turf/src/main/resources/turf/turf_powerups.yml`**, registered by the module (T8) | sole consumer is `PowerupRegistryLoader` (`PowerupRegistryLoader.java:30`), a turf class |
| `turf/turf_npcs.yml` (42 lines) | `gangland-impl/src/main/resources/turf/turf_npcs.yml`, registered `KernelConfig.java:188` | **cops module** — *not* this flip's file | its only reader is `TurfNpcsConfigLoader`, which produces `copsncrooks.npc.turf.config.TurfPowerupSettings` and `copsncrooks.npc.turf.defender.TurfDefenderConfig` (`TurfNpcsConfigLoader.java:5-6,57,65`). Both are cops types. |
| `settings.yml` `Turf:` block (`settings.yml:703`) | core | **stays core** | shared top-level file; the module reads it through `Settings` (rule 5) |
| `message/message_en.yml` / `message_es.yml` `TURF_*` keys | core | **stay core** | shared top-level files; `GanglandTurfMessages` routes through the core `Messages` enum |
| `module.properties` | **already exists**: `gangland-turf/src/main/resources/org/luckyraven/gangland/turf/module.properties`, content `module.name=${project.name}` (identical to mail's) | unchanged | M2's `module.properties` item is already satisfied |
| `module.yml` | absent | **new**, jar root (T2) | |
| `commands.json` | 17 turf keys in `gangland-impl/src/main/resources/commands.json` | **new** `gangland-turf/src/main/resources/commands.json` at the jar root | |

**Exact `commands.json` keys** (from `json.load()` of the core file, 2026-09-07 — the file has 225 top-level keys and
17 turf keys):

```
turf, turf_wand, turf_pos1, turf_pos2, turf_create, turf_delete, turf_setowner, turf_list,
turf_info, turf_show, turf_status, turf_select, turf_tp, turf_income, turf_powerupnpc,
turf_garrison, turf_buff
```

`turf_powerupnpc` is the one that belongs to **cops** if flip 1 moved `TurfPowerupNpcCommand` — T0 decides. All the
others are unconditionally this module's.

> `TurfCommand`'s help page filters `getCommands()` by `startsWith("turf")` (`TurfCommand.java:74`). `InformationManager`
> merges every module's `commands.json` into one index (`KernelConfig.java:60-70`), so the help page is complete
> regardless of which module jar owns `turf_powerupnpc`.

### 1.5 Tests

| | |
|---|---|
| Impl tests referencing a turf type | **none.** `grep -rl "org\.luckyraven\.gangland\.turf\." gangland-impl/src/test` → empty. **Nothing to move.** |
| Turf tests that already exist | 17 files in `gangland-features/gangland-turf/src/test`: 10 test classes (`CaptureServiceHelpersTest`, `CaptureServiceOwnedTurfTest`, `CaptureServiceStartAndCompleteTest`, `CaptureServiceUnclaimedTurfTest`, `data/CuboidRegionTest`, `manager/TurfManagerTest`, `powerups/{ActiveBuffManagerTest, ActiveTurfBuffTest, GarrisonManagerTest, PowerupRegistryTest}`) + 7 support classes under `turf/support/`. They import only `gangland.gang.*` (domain) and `keystone.testkit.{BukkitStatics, PluginMocks}` — **no impl types, so the pom change cannot break them.** |
| Tests asserting counts | `gangland-impl/src/test/java/org/luckyraven/gangland/command/data/InformationManagerTest.java:41` — `assertEquals(225, …)` on branch `0.8.3`. Flips 1 and 2 will have lowered it; T4 recomputes (see §3). |
| New test | `gangland-features/gangland-turf/src/test/java/org/luckyraven/gangland/turf/TurfModuleTest.java`, modelled on `MailModuleTest.java` |

### 1.6 Seams needed

**None.** Every remaining core→turf reference is a *move*, not a callback:

- No core code subscribes to a turf event, reads a turf registry at startup, resolves a turf placeholder, or renders
  a turf sign (verified: `grep -rn "Turf\|turf"` over `gangland-impl/src/main/java` returns only `KernelConfig`,
  `Messages` and `Settings` outside the six turf directories).
- `Gangland.bStats()` has no turf chart (`Gangland.java:112-130`), so the weapon plan's `MetricsContributor` is not
  needed here.
- `ItemConfig.itemRefresherRegistry` hard-lists only weapon and gadget refreshers — no turf entry.
- The single genuine core→cops call inside the turf command tree (`/glw turf powerupnpc`) is resolved **by flip 1**
  through the existing `command/extension/CommandContribution` seam (`CommandContribution.java:18-30`,
  `CommandContributions.java:23-41`), exactly as `GangCommand.java:85,152` already does for mail. This flip carries
  that seam across the module boundary unchanged — see T3 step 5 and §6 Q1.

---

## 2. Ordered tasks

Four groups. **Run `mvn` from the repo root**; always pass `-am` when targeting one module (CLAUDE.md).
The executor never commits.

**Line numbers are as of `d6bb33ac`; after flips 1–2 or the `0.8.3` merge have touched a file, locate by symbol and
record the drift in §7.** Every "Done when" in this plan is grep- or build-based rather than line-based, so a shifted
line cannot make a task silently pass.

---

### Group A — poms and module entry

#### T0 — Reconnaissance: pin down what flip 1 actually did  (group A, 0 files changed)

- **Do:** run each command and write the answer into the §7 status table before touching anything.

  ```bash
  cd "E:/Programming/java/Gangland Warfare [Cubed-GTA recoded]"

  # A. Which of the 30 files are still in core?
  grep -rl "org\.luckyraven\.gangland\.turf\." gangland-impl/src/main/java | sort

  # B. Does TurfCommand still name a cops type?  (expected: no output)
  grep -rn "copsncrooks" gangland-impl/src/main/java/org/luckyraven/gangland/command/sub/turf/

  # C. Where did TurfPowerupNpcCommand go?  (expected: the cops module)
  find gangland-features gangland-impl -name "TurfPowerupNpcCommand.java" -not -path "*/target/*"

  # D. TurfSelectionResolver: CONFIRMED design — flip 1 (cops T12 step 3) widens it to `public final class` with
  #    `public static Turf resolve(...)` and LEAVES IT IN command/sub/turf/. This flip's T3 moves it to
  #    org.luckyraven.gangland.turf.command.  Expect: "public final class TurfSelectionResolver" + "public static".
  grep -n "class TurfSelectionResolver\|static .* resolve" \
       gangland-impl/src/main/java/org/luckyraven/gangland/command/sub/turf/TurfSelectionResolver.java

  # D2. The TurfNpcContracts holder bean flip 1 adds to TurfConfig (consistency review finding 1).
  #     Expect one hit; if empty, escalate — see §1.3 row 21.
  grep -n "TurfNpcContracts" gangland-impl/src/main/java/org/luckyraven/gangland/config/TurfConfig.java

  # E. Which turf_* commands.json keys are still in the CORE file, and how many entries total?
  python -c "import json;d=json.load(open('gangland-impl/src/main/resources/commands.json',encoding='utf-8'));t=[k for k in d if k=='turf' or k.startswith('turf_')];print('core total',len(d));print('turf keys',len(t),t)"

  # F. What count does InformationManagerTest assert right now?
  grep -n "assertEquals(.*manager.getCommands().size()" \
       gangland-impl/src/test/java/org/luckyraven/gangland/command/data/InformationManagerTest.java

  # G. Do TurfNpcsConfig / TurfNpcsConfigLoader / TurfPowerupNpc{Repository,Table} / turf_npcs.yml still sit in core?
  ls gangland-impl/src/main/java/org/luckyraven/gangland/config/TurfNpcsConfig.java \
     gangland-impl/src/main/java/org/luckyraven/gangland/file/configuration/turf/ \
     gangland-impl/src/main/java/org/luckyraven/gangland/database/repositories/turf/ \
     gangland-impl/src/main/java/org/luckyraven/gangland/database/tables/turf/ \
     gangland-impl/src/main/resources/turf/ 2>&1
  grep -n "turf_powerups\|turf_npcs" gangland-impl/src/main/java/org/luckyraven/gangland/config/KernelConfig.java
  ```

- **Why:** every downstream file count and the `InformationManagerTest` number depend on flip 1's outcome, and this
  plan was written before flip 1 executed.
- **Done when:** the §7 status table row for T0 records: the file list from A, yes/no for B, the path from C, the
  visibility from D, the key list + core total from E, the current asserted count from F, and G's survivors.
- **Watch out:** if **B prints anything** (core `command/sub/turf/**` still imports `copsncrooks`), **stop and escalate
  to the scrum master** — flip 1 is not green and this flip cannot start. §6 Q1 has the fallback design if the scrum
  master tells you to proceed anyway.

#### T1 — Poms: detach turf from the core, attach it to the module chain  (group A, 4 files)

- **Do:**
  1. `gangland-impl/pom.xml` — **delete** the `gangland-turf` dependency block (currently around line 109, the
     `<artifactId>gangland-turf</artifactId>` entry).
  2. `gangland-features/gangland-turf/pom.xml`:
     - Add `keystone-command` and `keystone-module` dependencies (no `<scope>` — the root
       `dependencyManagement` already pins them `provided`, `pom.xml:189-206`). `keystone-command` is needed by the
       moved `Command`/`SubArgument`/`OptionalArgument`/`Tree` usage; `keystone-module` by `TurfModule`.
     - Change `gangland-core` and `gangland-domain` to `<scope>provided</scope>` (they ship inside the core jar; the
       module resolves them from there at runtime — README M1).
     - Add, at the end of `<dependencies>`, with the same comment mail carries
       (`gangland-features/gangland-mail/pom.xml`, the "The host (core) jar" block):
       ```xml
       <dependency>
           <groupId>org.luckyraven</groupId>
           <artifactId>gangland-impl</artifactId>
           <scope>provided</scope>
       </dependency>
       ```
     - Leave `spigot-api` (provided), `keystone-bean`, `keystone-item`, `keystone-common`, `keystone-persistence`,
       `keystone-hooks` (supplies `org.luckyraven.keystone.economy.Currency`, used by `TurfIncomeCommand`), `XSeries`
       and `item-nbt-api-plugin` exactly as they are.
     - **Do not add junit/mockito/keystone-testkit** — they are inherited from the root `<dependencies>`
       (`pom.xml`, global block: `junit-jupiter`, `mockito-core`, `keystone-testkit`, `sqlite-jdbc`).
  3. `gangland-features/cops-n-crooks/pom.xml` — change the existing `gangland-turf` dependency to
     `<scope>provided</scope>` (leave the artifact in place; cops still compiles against turf, but at runtime the
     classes come from the sibling module jar via the shared parent-first `ModuleClassLoader`).
  4. `gangland-build/pom.xml` — three edits mirroring the `gangland-mail` precedent:
     - `<artifactSet><excludes>`: add `<exclude>org.luckyraven:gangland-turf</exclude>` next to the mail line.
     - `maven-dependency-plugin` → `copy-runtime-modules` → `<artifactItems>`: add
       ```xml
       <artifactItem>
           <groupId>org.luckyraven</groupId>
           <artifactId>gangland-turf</artifactId>
           <version>${project.version}</version>
       </artifactItem>
       ```
     - `<dependencies>`: add `gangland-turf` with `<version>${project.parent.version}</version>` and
       `<scope>provided</scope>`, next to the mail dependency.
  5. `gangland-features/pom.xml` — **no change**, `gangland-turf` is already in `<modules>`.
- **Why:** M1. Removing turf from impl's compile closure turns the compiler into the work list.
- **Done when:** `mvn clean install -DskipTests` fails **only** in `gangland-impl`, with
  `package org.luckyraven.gangland.turf... does not exist` / `cannot find symbol` errors naming exactly the files
  T0/A listed. Record the error count in §7. Also: `mvn -q -pl gangland-features/gangland-turf -am install -DskipTests`
  **succeeds** (turf itself is unaffected so far).
- **Watch out:**
  - **Edits 1 and 2 must land together.** `gangland-turf` gaining `gangland-impl` while `gangland-impl` still has
    `gangland-turf` makes Maven abort with `The projects in the reactor contain a cyclic reference`. If you see that
    message, edit 1 did not save.
  - `-am` is mandatory on every single-module build; without it Maven tries to download `${revision}` from Central.
  - Do **not** delete `gangland-weapon`, `cops-n-crooks` or `gangland-gadget` lines from `gangland-impl/pom.xml`
    (weapon is flip 4; cops and gadget were removed by flips 1 and 2 — if they are still there, T0/B already failed).

#### T2 — Module entry: `TurfModule` + `module.yml`  (group A, 2 files)

- **Do:**
  1. Create `gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/TurfModule.java`, modelled
     literally on `MailModule.java` (Lombok `@CustomLog`, `implements KeystoneModule`, method braces on their own
     lines):

     ```java
     package org.luckyraven.gangland.turf;

     @CustomLog
     public final class TurfModule implements KeystoneModule {

         public static final String CONFIG_PACKAGE     = "org.luckyraven.gangland.turf";        // documentation only
         public static final String LISTENER_PACKAGE   = "org.luckyraven.gangland.turf.listener";
         public static final String TASK_PACKAGE       = "org.luckyraven.gangland.turf.task";
         public static final String COMMAND_PACKAGE    = "org.luckyraven.gangland.turf.command";
         public static final String REPOSITORY_PACKAGE = "org.luckyraven.gangland.turf.database";

         @Override
         public void configure(ModuleRegistrar registrar) {
             registrar.configuration(TurfModuleFileConfig.class)
                      .configuration(TurfModuleConfig.class)
                      .listenerPackage(LISTENER_PACKAGE)
                      .listenerPackage(TASK_PACKAGE)
                      .commandPackage(COMMAND_PACKAGE)
                      .repositoryPackage(REPOSITORY_PACKAGE);
         }

         @Override
         public void onEnabled(ModuleContext context) {
             log.info("Turf module {} enabled", context.module().descriptor().version());
         }

         @Override
         public void onDisabled() {
             log.debug("Turf module disabled");
         }
     }
     ```

     `TurfModuleFileConfig` and `TurfModuleConfig` do not exist yet — **create empty `@Configuration` stubs now**
     (`TurfModuleFileConfig`: `@Configuration(phase = Phase.KERNEL)` with no beans; `TurfModuleConfig`:
     `@Configuration` with no beans) so this task compiles; T7 and T8 fill them.
  2. Create `gangland-features/gangland-turf/src/main/resources/module.yml` (block-style, capitalised underscore
     keys, same shape as `gangland-mail/src/main/resources/module.yml`):

     ```yaml
     # Keystone module descriptor - read by Gangland's ModuleLoader from plugins/Gangland_Warfare/modules/.
     Id: turf
     Name: Gangland Turf
     Version: ${project.version}
     Main: org.luckyraven.gangland.turf.TurfModule
     Host_Api: 0.8
     Artifact: org.luckyraven:gangland-turf
     ```

     **No `Depends:`** — turf depends on nothing but the core.
  3. `module.properties` already exists at
     `gangland-turf/src/main/resources/org/luckyraven/gangland/turf/module.properties`
     (`module.name=${project.name}`). Verify only; do not recreate.
- **Why:** M2.
- **Done when:** `mvn -q -pl gangland-features/gangland-turf -am install -DskipTests` succeeds and
  `unzip -p gangland-features/gangland-turf/target/gangland-turf-0.8.4.jar module.yml` prints `Id: turf` with
  `Version: 0.8.4` (the `${project.version}` filter must have run — if it prints the literal `${project.version}`,
  resource filtering is not on for this module; copy the `<resources>` filtering block from `gangland-mail/pom.xml`
  if it has one, otherwise check the parent).
- **Watch out:** two `listenerPackage` calls are deliberate — `turf.task.GangPresenceListener` carries
  `@ListenerHandler` but lives outside `turf.listener` (see §6 Q3). `commandPackage` is used (unlike mail) because
  `TurfCommand` is a real top-level `/glw turf` command (`TurfCommand.java:34`, the only `@CommandHandler` in the
  tree).

> **Compile gate A:** `mvn clean install -DskipTests` — expect failure **only** in `gangland-impl`, error list == the
> T0/A file list. `mvn -q -pl gangland-features/gangland-turf -am install -DskipTests` — green.

---

### Group B — commands

#### T3 — Move the `/glw turf` command tree  (group B, 17 files)

- **Do:**
  1. `git mv` (or move + `git add`) every `*.java` from
     `gangland-impl/src/main/java/org/luckyraven/gangland/command/sub/turf/` to
     `gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/command/`, **except**
     `TurfPowerupNpcCommand.java` if T0/C found it in the cops module (expected). The 17 files are rows 1–17 of §1.1.
  2. In every moved file, change the package declaration to
     `package org.luckyraven.gangland.turf.command;`.
  3. Remove now-redundant `import org.luckyraven.gangland.turf.*` lines only where the moved class is in the *same*
     package — none are (the turf domain classes live in `turf.data`, `turf.manager`, … not `turf.command`), so
     **keep every turf import as-is**. Keep the impl imports (`org.luckyraven.gangland.Gangland`,
     `...command.Command`, `...file.configuration.{Messages, Settings}`, `...util.GanglandChatUtil`,
     `...gang.**`) unchanged — they resolve from the `provided` `gangland-impl` dependency.
  4. Delete the now-empty directory `gangland-impl/src/main/java/org/luckyraven/gangland/command/sub/turf/`.
  5. **`TurfCommand.initializeArguments()`** (`TurfCommand.java:102-152`): if flip 1 replaced the
     `TurfPowerupNpcCommand` construction with a `CommandContributions` call, that code moves verbatim — it uses only
     core types (`CommandContributions`, `Tree<Argument>`, `Argument`) which the module can import. Verify with
     `grep -n "contributions\|CommandContribution" TurfCommand.java` after the move; if the file still constructs
     `TurfPowerupNpcCommand` directly, apply §6 Q1's fallback.
  6. **Visibility:** keep every class exactly as it is (`TurfCommand` public final, the other 15 package-private) —
     they are all in one package again, so nothing breaks. **`TurfSelectionResolver` is the confirmed exception:**
     flip 1 (cops T12 step 3) already widened it to `public final class` with `public static resolve(...)` and left
     it in `command/sub/turf/`. **Keep both modifiers public** — the cops module's `TurfPowerupNpcCommand`
     contribution calls it across the module boundary — and note the new package for T9 step 2.
- **Why:** M7.
- **Done when:**
  - `grep -rn "org\.luckyraven\.gangland\.turf\." gangland-impl/src/main/java/org/luckyraven/gangland/command/`
    → empty.
  - `mvn -q -pl gangland-features/gangland-turf -am install -DskipTests` → green (the module now compiles the whole
    command tree against `gangland-impl` at provided scope).
  - `gangland-impl` still fails, but with **17 fewer** file entries than after T1.
- **Watch out:**
  - `TurfInfoCommand.renderInfo` and `TurfListCommand.sendRow` are package-private statics called from
    `TurfCommand.java:90,174`. Because all 17 land in one package this keeps working — do **not** widen them.
  - `TurfShowCommand` imports `org.luckyraven.gangland.turf.task.TurfVisualization`; `TurfCommand` imports
    `org.luckyraven.gangland.turf.listener.GangDisplayNameResolver`. Both are already turf-module classes.
  - Package-name collision check: `org.luckyraven.gangland.turf.command` does not exist in any other jar
    (`find . -path "*gangland/turf/command*" -name "*.java" -not -path "*/target/*"` → only your new files).

#### T4 — `commands.json` split + `InformationManagerTest` count  (group B, 2 files)

- **Do:**
  1. Create `gangland-features/gangland-turf/src/main/resources/commands.json` (jar root, tab-indented, same shape
     as `gangland-mail/src/main/resources/commands.json`) containing the turf entries **cut** from
     `gangland-impl/src/main/resources/commands.json` — copy each `usage`/`description` verbatim from the core file
     (core lines 834–901). Include every key T0/E listed except `turf_powerupnpc` when that key is already gone from
     core (flip 1 will have put it in the cops module's `commands.json`).
  2. Delete those same keys from `gangland-impl/src/main/resources/commands.json`. Keep the JSON valid (watch the
     trailing comma on the entry preceding the first deleted block and on the last entry of the file).
  3. `gangland-impl/src/test/java/org/luckyraven/gangland/command/data/InformationManagerTest.java:41` — change the
     asserted count to the new value and update the explanatory string. **Compute it, do not guess:**
     ```bash
     python -c "import json;print(len(json.load(open('gangland-impl/src/main/resources/commands.json',encoding='utf-8'))))"
     ```
     Extend the existing comment, e.g. `"…minus the 16 turf entries that moved to the turf module's own commands.json
     in 0.8.4"`. (On branch `0.8.3` the assertion is `225`; flips 1 and 2 lower it first, hence the recomputation.)
- **Why:** M7. `commands.json` entries live in the jar that owns the command (CLAUDE.md, *feedback_commands_json*).
- **Done when:**
  - `python -c "import json;d=json.load(open('gangland-impl/src/main/resources/commands.json',encoding='utf-8'));print([k for k in d if k.startswith('turf')])"` → `[]`
  - `python -c "import json;print(len(json.load(open('gangland-features/gangland-turf/src/main/resources/commands.json',encoding='utf-8'))))"` → 16 (or 17, per T0/E)
  - `mvn -q -pl gangland-impl -am test -Dtest=InformationManagerTest` → green.
- **Watch out:** `InformationManagerTest.observation10_deadEntriesStillPresent` (line ~78) checks 19 unrelated dead
  entries (`dealer`/`kit`/`safe_wand`/`spawn`/`warp`) — none start with `turf`; leave that list alone.

> **Compile gate B:** `mvn -q -pl gangland-features/gangland-turf -am install -DskipTests` green;
> `mvn clean install -DskipTests` still fails in `gangland-impl` only, now naming the 9 remaining files
> (`TurfConfig`, 3 repositories, 3 tables, 2 contract impls).

---

### Group C — configuration, persistence, contracts, YAML

#### T5 — Move the persistence layer  (group C, 6 files)

- **Do:**
  1. Move to `gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/database/`:
     `database/repositories/turf/{TurfRepository, ActiveTurfBuffRepository, TurfGarrisonRepository}.java` and
     `database/tables/turf/{TurfTable, ActiveTurfBuffTable, TurfGarrisonTable}.java`.
  2. Set every package declaration to `package org.luckyraven.gangland.turf.database;`.
  3. Fix the cross-references: each repository imports its table
     (`TurfRepository.java:4` → `import org.luckyraven.gangland.database.tables.turf.TurfTable;`) — after the move
     both are in the same package, so **delete those three table imports**.
  4. Keep every `@Repository(...)` annotation and the `(JavaPlugin, DatabaseHandler, DatabaseBackend)` constructor
     signature untouched — `RepositoryRegistry` injects by that exact shape.
  5. **Do not move** `TurfPowerupNpcRepository.java` / `TurfPowerupNpcTable.java` if T0/G still finds them in core
     (they are cops', see §6 Q2). If the two `turf` directories under `database/` are empty afterwards, delete them;
     if the powerup-npc pair is still there, leave the directories.
- **Why:** M5. The module registers the package via `TurfModule.REPOSITORY_PACKAGE`, and `DatabaseConfig` scans it
  through the module classloader (`DatabaseConfig.java:84-89`).
- **Done when:** `mvn -q -pl gangland-features/gangland-turf -am install -DskipTests` green, and
  `grep -rn "org\.luckyraven\.gangland\.turf\." gangland-impl/src/main/java/org/luckyraven/gangland/database/`
  → empty.
- **Watch out:** the data-supplier wiring is **already** on the turf side and must keep working —
  `TurfManager.java:61`, `ActiveBuffManager.java:55`, `GarrisonManager.java:29` each call `setDataSupplier` from
  `initialize()`, which `TurfModuleConfig` invokes (memory rule *feedback_repository_data_supplier*). Nothing to
  change; just do not drop the `manager.initialize()` calls in T7.

#### T6 — Move the two contract implementations  (group C, 2 files)

- **Do:**
  1. Move `file/configuration/turf/GanglandTurfMessages.java` and `file/configuration/turf/GanglandTurfSounds.java`
     to `gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/config/`.
  2. Package declaration → `package org.luckyraven.gangland.turf.config;`.
  3. Keep their imports of `org.luckyraven.gangland.file.configuration.{Messages, Settings}` and
     `org.luckyraven.gangland.util.TimeMessages` — allowed module → core direction.
  4. The interfaces `TurfMessageContract` / `TurfSoundContract` stay put in
     `gangland-turf/.../turf/contract/` — they are the test seam (PLANNER-BRIEF rule / M4). Add the explicit imports
     for them in the moved files (previously they were imported already; verify they survive the package change).
  5. Delete `gangland-impl/src/main/java/org/luckyraven/gangland/file/configuration/turf/` if empty after T0/G's
     survivors are accounted for.
- **Why:** M4.
- **Done when:** `mvn -q -pl gangland-features/gangland-turf -am install -DskipTests` green;
  `grep -rn "org\.luckyraven\.gangland\.turf\." gangland-impl/src/main/java/org/luckyraven/gangland/file/` → empty.
- **Watch out:** `GanglandTurfMessages.format` calls `Messages.valueOf(key)` and throws on a typo
  (`GanglandTurfMessages.java:22`) — do not "improve" it; the `TURF_*` constants stay in the core `Messages` enum,
  which `p0-wave-3` is editing. Read-only for us.

#### T7 — `TurfModuleConfig`: the 21 CONFIG beans  (group C, 2 files)

- **Do:**
  1. Fill the `TurfModuleConfig` stub created in T2
     (`gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/TurfModuleConfig.java`) with the
     complete body of `gangland-impl/src/main/java/org/luckyraven/gangland/config/TurfConfig.java`:
     - `package org.luckyraven.gangland.turf;`, `@Configuration` (no `phase` → CONFIG, matching today).
     - **Every `@Bean` method in the file — 20 today + `turfNpcContracts()` added by flip 1 = 21** (§1.3),
       **verbatim**, including every parameter (the `@SuppressWarnings("unused") Settings settings` ordering edge at
       `TurfConfig.java:177` and the `Gangland plugin` parameters). Count the `@Bean` annotations in the source
       before and after the move and assert they match — do not trust the number 21 if T0/A found a different one.
     - `turfNpcContracts()` needs the import `org.luckyraven.gangland.turf.turfnpcs.TurfNpcContracts`; that class is
       already in this module (it never moves), so after the move the import is same-module rather than
       cross-artifact. The cops module reaches the bean through the container — do not change its type or name.
     - The class Javadoc and the trailing 6-line comment at `TurfConfig.java:191-196` about `TurfBossBarListener` /
       `TurfCaptureNotifier` deliberately **not** being beans.
     - Imports: `org.luckyraven.gangland.turf.config.{GanglandTurfMessages, GanglandTurfSounds}` (new location from
       T6) and `org.luckyraven.gangland.turf.database.{TurfRepository, ActiveTurfBuffRepository,
       TurfGarrisonRepository}` (new location from T5) replace the old `gangland.file.configuration.turf.*` and
       `gangland.database.repositories.turf.*` imports. Everything else is unchanged.
  2. **Delete** `gangland-impl/src/main/java/org/luckyraven/gangland/config/TurfConfig.java`.
  3. If T0/G found a surviving `config/TurfNpcsConfig.java` with turf-only beans, move those methods into
     `TurfModuleConfig` too and delete the file. If the file is gone (expected), do nothing.
- **Why:** M3.
- **Done when:** `ls gangland-impl/src/main/java/org/luckyraven/gangland/config/TurfConfig.java` → "No such file";
  `mvn -q -pl gangland-features/gangland-turf -am install -DskipTests` green.
- **Watch out:**
  - **Do not** convert `turfRepositoryContract(TurfRepository repository)` (`TurfConfig.java:41`) to mail's
    `registry.getRepository(...)` style. The DATABASE phase hook publishes every scanned repository into the
    container by its concrete class (`GanglandContext.java:199-212`), and the module's `TurfRepository` class object
    comes from the same `ModuleClassLoader` that loads `TurfModuleConfig`, so injection by concrete type resolves.
    (If it does *not* resolve at runtime, the mail pattern
    `(TurfRepositoryContract) registry.getRepository(Turf.class)` — `MailModuleConfig.java:55-57` — is the drop-in
    fallback; log it in §7.)
  - `Gangland` as a `@Bean` parameter is fine (registered in `GanglandContext.java:107`).
  - Keep `manager.initialize()` inside beans 6, 7 and 11 — dropping them breaks autosave with
    `No data supplier set for repository: …`.

#### T8 — YAML: move `turf_powerups.yml` into the module jar  (group C, 4 files)

- **Do:**
  1. Move `gangland-impl/src/main/resources/turf/turf_powerups.yml` →
     `gangland-features/gangland-turf/src/main/resources/turf/turf_powerups.yml` (byte-identical; **same
     `turf/` path**, because `FileHandler` looks the default up at `<directory>/<name><fileType>`,
     `FileHandler.java:191,200`).
  2. `gangland-impl/src/main/java/org/luckyraven/gangland/config/KernelConfig.java` — delete the `turf_powerups`
     `fm.addFile(...)` line, **locating it by symbol, not by line number**:
     ```bash
     grep -n "turf_powerups" gangland-impl/src/main/java/org/luckyraven/gangland/config/KernelConfig.java
     ```
     It reads `fm.addFile(new FileHandler(gangland, "turf_powerups", "turf", ".yml"), true);` and sat at `:187` at
     `d6bb33ac`, but **flip 1 (cops T17) has already deleted the five `npc/*` lines and the `turf_npcs` line above
     and below it, so the numbering has shifted**. There is no `turf_npcs` line left to leave alone; if the grep
     for `turf_npcs` still returns a hit, flip 1 did not finish — see §6 Q2 before proceeding.
     After the deletion the `fileManager()` bean has no `turf/*` registration at all.
  3. Fill the `TurfModuleFileConfig` stub from T2
     (`gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/TurfModuleFileConfig.java`):

     ```java
     package org.luckyraven.gangland.turf;

     /**
      * KERNEL-phase registration of the turf module's own YAML defaults. The FileManager is a KERNEL bean
      * (KernelConfig#fileManager), so BeanGraph orders this method after it; addFile(handler, true) creates the file
      * immediately (FileManager#addFile), long before PowerupRegistryLoader's CONFIG-phase constructor calls
      * checkFileLoaded("turf_powerups"). The five-argument FileHandler reads the bundled default out of the module
      * jar through the module classloader instead of the plugin jar.
      */
     @Configuration(phase = Phase.KERNEL)
     public final class TurfModuleFileConfig {

         private final Gangland gangland;

         public TurfModuleFileConfig(Gangland gangland) {
             this.gangland = gangland;
         }

         @Bean
         public TurfModuleFiles turfModuleFiles(FileManager fileManager, ModuleLoader moduleLoader) {
             FileHandler powerups = new FileHandler(gangland, "turf_powerups", "turf", ".yml",
                                                   moduleLoader.classLoader());
             fileManager.addFile(powerups, true);
             return new TurfModuleFiles(powerups);
         }
     }
     ```

  4. Create the tiny marker type
     `gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/TurfModuleFiles.java`:
     ```java
     package org.luckyraven.gangland.turf;

     /** The FileHandlers the turf module registers with the host's FileManager. A distinct bean type so no other
      *  module's file registration collides on a bare FileHandler bean. */
     public record TurfModuleFiles(FileHandler powerups) {
     }
     ```
- **Why:** M8.
- **Done when:**
  - `turf_powerups.yml` was the **last** file in `gangland-impl/src/main/resources/turf/` (flip 1's cops T17 already
    took `turf_npcs.yml`), so after step 1 the directory is empty — **delete the directory**, then:
    ```bash
    test ! -d gangland-impl/src/main/resources/turf && echo OK
    ```
    → prints `OK`. If `ls gangland-impl/src/main/resources/turf/` still shows `turf_npcs.yml`, stop: flip 1 left it
    behind (§6 Q2 has the fallback, which puts it in the **cops** module, not this one).
  - `grep -n "turf_powerups\|turf_npcs" gangland-impl/src/main/java/org/luckyraven/gangland/config/KernelConfig.java`
    → empty.
  - After T14's package build: `unzip -l target/gangland_warfare-0.8.4.jar | grep "turf/turf_powerups.yml"` → **empty**,
    and `unzip -l target/modules/gangland-turf-0.8.4.jar | grep "turf/turf_powerups.yml"` → one hit.
- **Watch out:**
  - **The core copy must be deleted, not merely duplicated.** `FileHandler.copyFromResourceLoader` calls
    `resourceLoader.getResourceAsStream("turf/turf_powerups.yml")` on the **parent-first** `ModuleClassLoader`
    (`FileHandler.java:202,233-235`), so a surviving core copy wins silently and every future powerup edit in the
    module jar is invisible.
  - `Phase.KERNEL` is mandatory here. `PowerupRegistryLoader`'s constructor runs in CONFIG and immediately calls
    `fileManager.checkFileLoaded("turf_powerups")` (`PowerupRegistryLoader.java:40-41`); a FILE- or CONFIG-phase
    registration would race it.
  - `ModuleLoader` is injectable anywhere — `GanglandContext.java:110` registers it in the container before any phase.
  - A `@Configuration` class has exactly one phase (Keystone `Configuration.java:32-37`), which is why this is a
    second configuration class rather than a method on `TurfModuleConfig`.

#### T9 — Update the cops module for the new turf locations  (group C, 3 files)

- **Do:**
  1. `gangland-features/cops-n-crooks/src/main/resources/module.yml` — add a block-style `Depends` list (house YAML
     rule: every entry on its own line, no flow syntax):
     ```yaml
     Depends:
       - turf
     ```
     Place it after `Host_Api`. **Exactly two spaces before the `-`** — flip 4 (weapon T26) appends `  - weapon` as a
     pure one-line edit to this same list, so the indentation must match (consistency review finding 13). Never the
     flow form `[turf, weapon]` (memory rule *feedback_yaml_inline_braces*).
  2. Update the cops-side import of the resolver: flip 1 left `TurfSelectionResolver` in
     `command/sub/turf/` as `public final class` with `public static resolve(...)`, and T3 has just moved it, so
     change `org.luckyraven.gangland.command.sub.turf.TurfSelectionResolver` →
     `org.luckyraven.gangland.turf.command.TurfSelectionResolver` in the cops module
     (`grep -rn "command\.sub\.turf" gangland-features/cops-n-crooks/src` finds every site; the caller is
     `TurfPowerupNpcCommand`, which cops T12 moved to `copsncrooks.command.turf`).
  3. Verify nothing else in cops names a moved class:
     `grep -rn "gangland\.database\.repositories\.turf\|gangland\.database\.tables\.turf\|gangland\.file\.configuration\.turf\|gangland\.config\.TurfConfig" gangland-features/cops-n-crooks/src`
     → expect empty; fix any hit to the new package.
  4. The cops pom scope change was done in T1 step 3 — verify it stuck.
- **Why:** README's "the later flip's checklist owns the edit to the earlier module's pom and `module.yml`".
- **Done when:** `mvn -q -pl gangland-features/cops-n-crooks -am install -DskipTests` green, and
  `unzip -p gangland-features/cops-n-crooks/target/cops-n-crooks-0.8.4.jar module.yml` shows the `Depends` list.
- **Watch out:** do not add `Depends: - weapon` — weapon is still in the core jar at this flip and a missing module
  id raises the `module.dependency.missing` fault, which would skip the whole cops module at boot.

> **Compile gate C (the big one):**
> - `mvn clean install -DskipTests` → **whole reactor green.**
> - `grep -rn "org\.luckyraven\.gangland\.turf\." gangland-impl/src` → **empty** (main *and* test).
> - `grep -rn "TurfConfig\|TurfNpcsConfig" gangland-impl/src/main/java` → empty (or only the cops-owned survivors
>   T0/G recorded).

---

### Group D — tests, docs, gates

#### T10 — `TurfModuleTest`  (group D, 1 file)

- **Do:** create
  `gangland-features/gangland-turf/src/test/java/org/luckyraven/gangland/turf/TurfModuleTest.java`, modelled on
  `MailModuleTest.java`. Two tests:
  1. `configure_declaresConfigsAndPackages` — build a `ModuleRegistrations`, call `new TurfModule().configure(...)`,
     then assert
     `assertEquals(List.of(TurfModuleFileConfig.class, TurfModuleConfig.class), registrations.configurations())`,
     `assertEquals(List.of(TurfModule.LISTENER_PACKAGE, TurfModule.TASK_PACKAGE), registrations.listenerPackages())`,
     `assertEquals(List.of(TurfModule.COMMAND_PACKAGE), registrations.commandPackages())`,
     `assertEquals(List.of(TurfModule.REPOSITORY_PACKAGE), registrations.repositoryPackages())`.
  2. `declaredPackages_matchClasses` — assert the four constants equal the real
     `…getPackageName()` of `TurfBossBarListener`, `GangPresenceListener`, `TurfCommand` and `TurfRepository`.
- **Why:** M10; the same guard `MailModuleTest` gives the pilot.
- **Done when:** `mvn -q -pl gangland-features/gangland-turf -am test -Dtest=TurfModuleTest` green.
- **How to show it red first:** write the test **before** T3/T5 have moved the classes — `TurfCommand` and
  `TurfRepository` are then not in `org.luckyraven.gangland.turf.*` and the file will not even compile. Since the
  group order puts T10 after the moves, use this instead, and record the output in §7: temporarily change
  `TurfModule.COMMAND_PACKAGE` to `"org.luckyraven.gangland.turf.commands"` (typo), run the test, paste the
  `expected: <…turf.commands> but was: <…turf.command>` failure into §7, then revert the typo and re-run green.

#### T11 — Turf test suite still green  (group D, 0 files)

- **Do:** `mvn -q -pl gangland-features/gangland-turf -am test`
- **Why:** the 17 existing turf test files must survive the pom scope changes (`gangland-core` / `gangland-domain`
  → `provided`; provided scope *is* on the test classpath, so this should be a no-op).
- **Done when:** all 10 turf test classes pass. Report the counts verbatim in §7.
- **Watch out:** the turf tests use `keystone-testkit`'s `BukkitStatics` / `PluginMocks`; that dependency comes from
  the root `<dependencies>` — if it suddenly does not resolve, someone added a test dependency to the module pom
  (forbidden) or removed the root one.

#### T12 — Full suite  (group D, 0 files)

- **Do:** `mvn test` (whole reactor).
- **Done when:** green. `InformationManagerTest` passes with T4's new number; `CommandContributionsTest` passes
  unchanged.

#### T13 — Docs  (group D, 3 files)

- **Do:**
  1. `CLAUDE.md:153` — module table row for `gangland-features/gangland-turf`: change the Purpose cell to
     `**Runtime module** (`modules/gangland-turf-<rev>.jar`, never in the core jar): turf capture, contribution,
     garrison gameplay`, matching the wording of the mail row.
  2. `CLAUDE.md:171-173` — the two-tier paragraph: remove `turf` from the "still compile-time dependencies" list and
     add it to the runtime-module list. After this flip the sentence should read that the runtime modules are
     `gangland-mail`, `cops-n-crooks`, `gangland-gadget` and `gangland-turf`, and that **weapon** is the only
     feature still compiled into the core. Keep the DAG explanation.
  3. `documentation/module-loader.md:25-28` — the "What is a module today" table: add a `turf` row
     (`turf — TurfManager, capture, powerups/garrison, the /glw turf tree | gangland-turf | runtime module since
     0.8.4`) and drop `turf` from the "still compile-time dependencies" row (leaving weapon).
  4. `documentation/module-loader.md:30-33` — update the "Order for the remaining flips" paragraph to say only
     `weapon` remains.
  5. `documentation/module-loader.md`, the `module.yml` paragraph (`:59-68` at `d6bb33ac`) — it already documents
     `Depends:`; add one line under it noting the live example: *"`cops-n-crooks` declares `Depends:` with `- turf`
     because its turf-NPC views consume `turf.data.Turf` and the powerup managers."*
  6. `documentation/module-loader.md`, the module-YAML paragraph (`:77-80` at `d6bb33ac`) — it already describes the
     5-arg `FileHandler`; add `turf/turf_powerups.yml` as the second worked example beside the `weapon/rifle.yml`
     one, and note that the registration lives in a **KERNEL-phase** module configuration because `FileManager` is a
     KERNEL bean.
  6b. **`## Core seams` section.** Flip 1 creates this heading (cops T19, consistency review finding 12) and flips 2
     and 4 append rows to it. **Turf introduces no seam of its own, so it adds no row** — and it must **not** create
     a second seams heading under any other name. The one turf-adjacent entry, the `TurfNpcContracts` holder, is
     flip 1's row and stays as flip 1 wrote it; if T7 moved the `turfNpcContracts()` `@Bean` into
     `TurfModuleConfig`, amend **that existing row's** "registered by" cell to say
     `TurfModuleConfig` (turf module) instead of `config/TurfConfig.java` (core) — an edit to the existing row, not
     a new one, and not a new heading.
  7. `documentation/README.md` — `grep -n "turf" documentation/README.md` currently returns nothing; if flips 1–2
     added a module list there, add turf to it, otherwise no edit.
  8. Memory file `project_module_loader_plan.md` — record flip 3 done (turf module, cops gained `Depends: [turf]`,
     weapon is the last one).
- **Why:** M11 / gate G4.
- **Done when:** `grep -n "turf" CLAUDE.md documentation/module-loader.md` shows no sentence claiming turf is a
  compile-time dependency of impl.
- **Watch out:** `CLAUDE.md` is gitignored and local-only — edit it anyway (the project instructs it), but it will
  not appear in the commit diff.

#### T14 — Gates G1–G4 + graph refresh  (group D, 0 files)

- **Do:** run §5's commands in order and paste the output into §7.
- **Done when:** all four gates pass and `graphify update . --force` has run (M12).

---

## 3. Tests

**Tests to move:** none. `grep -rl "org\.luckyraven\.gangland\.turf\." gangland-impl/src/test` returns nothing, and
all 17 turf test files already live in `gangland-features/gangland-turf/src/test`.

**Tests to change:**

| test | file | change |
|---|---|---|
| `InformationManagerTest.processCommands_populatesFromBundledJson` | `gangland-impl/src/test/java/org/luckyraven/gangland/command/data/InformationManagerTest.java:41` | the asserted entry count drops by the number of turf keys removed from core `commands.json` — **16** if flip 1 took `turf_powerupnpc`, else **17**. On branch `0.8.3` the value is `225`; flips 1 and 2 change it first, so T4 recomputes it with the `python -c "len(json.load(...))"` one-liner and updates the explanatory message. |

No other core test asserts a count that this flip moves. `CommandContributionsTest` mentions the string `"turf"`
(line 56) but asserts the *absence* of a contribution for that path in an empty container — still true.

**New tests:**

| test | asserts | red-first |
|---|---|---|
| `TurfModuleTest.configure_declaresConfigsAndPackages` | `TurfModule.configure` registers exactly `[TurfModuleFileConfig, TurfModuleConfig]`, listener packages `[turf.listener, turf.task]`, command package `[turf.command]`, repository package `[turf.database]` | T10's documented procedure: introduce a one-character typo in `COMMAND_PACKAGE`, capture the `expected: … but was: …` failure, revert. Recorded in §7 rather than left un-evidenced. |
| `TurfModuleTest.declaredPackages_matchClasses` | the four constants equal the actual `getPackageName()` of `TurfBossBarListener`, `GangPresenceListener`, `TurfCommand`, `TurfRepository` | same typo trick; before T3/T5 it does not compile at all, which is the strongest possible red. |

---

## 4. Docs and config

| file | edit |
|---|---|
| `gangland-impl/pom.xml` | remove the `gangland-turf` dependency |
| `gangland-features/gangland-turf/pom.xml` | `+ keystone-command`, `+ keystone-module`, `+ gangland-impl` (provided); `gangland-core` and `gangland-domain` → `provided` |
| `gangland-features/cops-n-crooks/pom.xml` | `gangland-turf` → `provided` |
| `gangland-build/pom.xml` | `+ <exclude>org.luckyraven:gangland-turf</exclude>` in the shade `artifactSet`; `+ <artifactItem>` for `gangland-turf` in `copy-runtime-modules`; `+ gangland-turf` dependency at `provided` |
| `gangland-features/pom.xml` | **no change** (turf already listed) |
| `gangland-features/gangland-turf/src/main/resources/module.yml` | **new** — `Id: turf`, `Name: Gangland Turf`, `Version: ${project.version}`, `Main: org.luckyraven.gangland.turf.TurfModule`, `Host_Api: 0.8`, `Artifact: org.luckyraven:gangland-turf`, **no `Depends`** |
| `gangland-features/gangland-turf/src/main/resources/commands.json` | **new** — the 16 (or 17) turf help entries cut from core |
| `gangland-features/gangland-turf/src/main/resources/turf/turf_powerups.yml` | **new location** of the core file |
| `gangland-features/gangland-turf/src/main/resources/org/luckyraven/gangland/turf/module.properties` | already exists, unchanged |
| `gangland-features/cops-n-crooks/src/main/resources/module.yml` | `+ Depends:` block list with `- turf` |
| `gangland-impl/src/main/resources/commands.json` | delete the turf keys |
| `gangland-impl/src/main/resources/turf/turf_powerups.yml` | delete — it is the last file in `resources/turf/` (flip 1 took `turf_npcs.yml`), so **delete the now-empty `gangland-impl/src/main/resources/turf/` directory too** (`test ! -d gangland-impl/src/main/resources/turf`) |
| `gangland-impl/.../config/KernelConfig.java` | delete the `turf_powerups` `fm.addFile(...)` line — **locate by symbol**; flip 1 already removed the `npc/*` and `turf_npcs` lines above and below it, so `:187` has shifted |
| `CLAUDE.md` | lines 153 and 171-173 at `d6bb33ac` (see T13) |
| `documentation/module-loader.md` | the module table, the flip-order paragraph, the `module.yml` `Depends:` paragraph and the module-YAML paragraph (`:25-33`, `:59-68`, `:77-80` at `d6bb33ac`); **append nothing under `## Core seams`** — turf contributes no seam (T13 step 6b) |
| memory `project_module_loader_plan.md` | flip 3 done |

---

## 5. Verification (gates G1–G4 instantiated)

```bash
cd "E:/Programming/java/Gangland Warfare [Cubed-GTA recoded]"

# --- G1 compile -------------------------------------------------------------
mvn clean install -DskipTests -q                     # whole reactor green, impl builds before the module
grep -rn "org\.luckyraven\.gangland\.turf\." gangland-impl/src        # expect: NO OUTPUT
grep -rn "gangland-turf" gangland-impl/pom.xml                        # expect: NO OUTPUT
test ! -d gangland-impl/src/main/resources/turf && echo "turf/ removed from core resources"

# --- G2 tests ---------------------------------------------------------------
mvn test                                             # full suite green
# InformationManagerTest asserts the recomputed number (T4); TurfModuleTest passes; the 10 turf
# test classes pass unchanged.

# --- G3 jars ----------------------------------------------------------------
mvn clean package -DskipTests
unzip -l target/gangland_warfare-0.8.4.jar | grep "org/luckyraven/gangland/turf/"   # expect: NO OUTPUT
unzip -l target/gangland_warfare-0.8.4.jar | grep "turf/turf_powerups.yml"          # expect: NO OUTPUT
unzip -l target/gangland_warfare-0.8.4.jar | grep -c "org/luckyraven/gangland/weapon/"  # expect: > 0 (weapon is flip 4)
unzip -l target/modules/gangland-turf-0.8.4.jar                                     # expect:
#   module.yml
#   commands.json
#   turf/turf_powerups.yml
#   org/luckyraven/gangland/turf/module.properties
#   org/luckyraven/gangland/turf/{TurfModule,TurfModuleConfig,TurfModuleFileConfig,TurfModuleFiles}.class
#   org/luckyraven/gangland/turf/command/*.class          (17 classes)
#   org/luckyraven/gangland/turf/database/*.class         (6 classes)
#   org/luckyraven/gangland/turf/config/GanglandTurf{Messages,Sounds}.class
unzip -p target/modules/gangland-turf-0.8.4.jar module.yml            # Id: turf, Version: 0.8.4, no Depends
unzip -p target/modules/cops-n-crooks-0.8.4.jar module.yml            # Depends: block list containing "- turf"

# --- G4 docs ----------------------------------------------------------------
grep -n "turf" CLAUDE.md documentation/module-loader.md
#   expect: turf described as a runtime module; the "still compile-time dependencies" list names weapon only.

# --- M12 --------------------------------------------------------------------
graphify update . --force
```

---

## 6. Risks and open questions for the scrum master

**Q1 (biggest) — how did flip 1 break `TurfCommand`'s dependency on the cops `TurfPowerupManager`?**
`command/sub/turf/TurfCommand.java:8,43,55,67,126-128` and
`command/sub/turf/TurfPowerupNpcCommand.java:9,36,40,70,73` both import
`org.luckyraven.gangland.copsncrooks.npc.turf.TurfPowerupManager`. Flip 1 *must* have removed those imports from
core (the core cannot name a module type after cops flips). **The 2026-09-07 cross-plan consistency review settled
this: option A below is the confirmed design, checked against `cops-n-crooks.md` T12 — it is no longer an assumption.**
Option B is retained only as the escape hatch if T0 finds the tree in a different state.

- **CONFIRMED — option A (cops T12, consistency review finding 2):** flip 1 moves
  `TurfPowerupNpcCommand` into the cops module as a `CommandContribution` with `parent() == "turf"`, widens
  `TurfSelectionResolver` to `public final class` + `public static resolve(...)` **while leaving it in
  `command/sub/turf/`** (this flip's T3 is what relocates it to `org.luckyraven.gangland.turf.command`), and changes
  `TurfCommand.initializeArguments()` to append
  `contributions.createFor("turf", getArgumentTree(), getArgument())` the way `GangCommand.java:152` does — with
  `CommandContributions.from(container)` in the constructor (`GangCommand.java:85`). Under option A this flip simply
  carries `TurfCommand` (and the seam call) into the module, and T9 fixes the cops-side import of
  `TurfSelectionResolver`. **`turf_powerupnpc` then lives in the cops `commands.json`, not ours.**
- **Option B:** flip 1 introduced a core seam interface (say `TurfPowerupPlacement`) implemented by a cops bean, and
  `TurfPowerupNpcCommand` stayed in core against that interface. Then this flip moves 18 command files instead of 17,
  the seam interface must either move to the turf module or stay in core (prefer: **move it to the turf module**,
  since the turf command tree is its only consumer and cops already `Depends: turf`), and `turf_powerupnpc` is our
  `commands.json` key (17 keys, not 16).
- **T0 decides which world we are in.** If T0/B shows core `command/sub/turf/**` *still* importing `copsncrooks`,
  flip 1 is not green — escalate, do not improvise.

**Q2 — `turf_npcs.yml`, `TurfNpcsConfigLoader`, `TurfPowerupNpc{Repository,Table}` and `TurfNpcsConfig`.**
The PLANNER-BRIEF says flip 1 "left a turf-only remainder in core" of `TurfNpcsConfig.java`. Reading the file, there
is no turf-only remainder: all 8 `@Bean` methods produce or consume `copsncrooks` types
(`TurfNpcsConfig.java:47,52,57,65,72,80,87,96,103,110`), `TurfNpcsConfigLoader` produces two cops types
(`TurfNpcsConfigLoader.java:5-6`), and `turf/turf_npcs.yml`'s two sections (`Powerup_Npc`, `Defender`) describe
cops NPCs. **My decision: all of it is cops', including the `turf/turf_npcs.yml` resource and the
`KernelConfig.java:188` registration.**
*Default if T0/G finds any of them still in core:* move them into the **cops-n-crooks module** (which this flip is
already editing) — resource to `cops-n-crooks/src/main/resources/turf/turf_npcs.yml`, loader/config/repository/table
to the matching `copsncrooks.*` packages, and the `addFile` line into a cops KERNEL-phase configuration mirroring T8.
Record the deviation in §7 and tell the scrum master, since it is flip 1's scope leaking into flip 3.

**Q3 — `GangPresenceListener` sits in `org.luckyraven.gangland.turf.task`, not `…turf.listener`**
(it carries `@ListenerHandler`; the other 9 turf listeners are under `turf.listener` and its two sub-packages, which
the package scan covers recursively — proven by the core scanning the root `org.luckyraven.gangland` and finding
`listener/player/**`, `GanglandContext.java:76,222`). This violates the house rule *feedback_listener_package*.
**Default: do not rename it in this flip** — declare both `turf.listener` and `turf.task` as listener packages
(T2) and leave the tidy-up to a separate change, so a boot-critical flip is not carrying an unrelated refactor.
Flag it to the scrum master as a follow-up.

**Q4 — repository injection by concrete type from a module.** `TurfConfig.java:41,56,61` inject the concrete
`TurfRepository` / `ActiveTurfBuffRepository` / `TurfGarrisonRepository`. That works because the DATABASE phase hook
registers each scanned repository under `repo.getClass()` (`GanglandContext.java:209-210`), and for a module both
the repository and the config class come from the same `ModuleClassLoader`. Mail deliberately used the safer
`registry.getRepository(MailItem.class)` cast instead (`MailModuleConfig.java:55-57`). **Default: keep the concrete
injection** (zero diff, and the mechanism is sound); if a runtime `No bean of type TurfRepository` appears in the G6
smoke test, switch all three to the mail pattern and note it.

**Q5 — `gangland-core` / `gangland-domain` scope.** README M1 says feature-to-feature deps still in the core become
`provided`; the mail pilot left them at compile scope. Because a module jar is a plain jar (not shaded), the scope is
cosmetic at build time — but `provided` documents the runtime truth and stops accidental transitive leakage into
`gangland-build`. **Default: set them `provided` in the turf pom.** If that breaks anything unexpected, reverting to
inherited compile scope is safe and matches mail.

**Q6 — branch.** Per README G0, this runs on branch `0.8.4`, cut from `0.8.3` **after** the `p0-wave-3` session has
landed. This flip has **zero** file overlap with `p0-wave-3` (§0), so it does not force the merge itself — but flips
1 and 2 do (`config/GameplayConfig.java`, `config/CopsAndGadgetsConfig.java`, gadget `listener/car/*`), so the merge
will already have happened by the time flip 3 starts.

**Bugs found while planning:** none new. Nothing here is a bug-docket entry; no `triage/<slug>.txt` needed.

---

## 7. Status table (executors fill this)

| Task | Status | Executor | Notes (what changed, what was skipped, failures verbatim) |
|---|---|---|---|
| T0 recon | done | sonnet groupA | A: 26 files still in core (all rows 1-17 command tree, `TurfConfig.java`, 3 repos, 3 tables, `GanglandTurfMessages`/`GanglandTurfSounds` — exactly the flip-1-adjusted list; flip 1's 4 files already gone). B: empty (no `copsncrooks` import left in `command/sub/turf/`) — option A confirmed live. C: `TurfPowerupNpcCommand` already at `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/command/turf/TurfPowerupNpcCommand.java`. D: `TurfSelectionResolver.java:24` is `public final class`, `:29` has `public static @Nullable Turf resolve(...)` — confirmed widened + left in place. D2: `TurfConfig.java:31` imports `TurfNpcContracts`, `:206-207` has the `turfNpcContracts()` bean returning `new TurfNpcContracts()` — flip 1's row 21 present. E: core `commands.json` has 177 total keys, 16 turf keys (`turf, turf_wand, turf_pos1, turf_pos2, turf_create, turf_delete, turf_setowner, turf_list, turf_info, turf_show, turf_status, turf_select, turf_tp, turf_income, turf_garrison, turf_buff`) — `turf_powerupnpc` already gone (cops took it), so this flip moves **16**, not 17. F: `InformationManagerTest.java:41` currently asserts `177`. G: `config/TurfNpcsConfig.java` absent; `database/repositories/turf/` and `database/tables/turf/` each hold only the 3 turf-owned files (no `TurfPowerupNpc{Repository,Table}` survivors); `file/configuration/turf/` holds only `GanglandTurfMessages`/`GanglandTurfSounds` (no cops contract impls survive); `resources/turf/` holds only `turf_powerups.yml` (`turf_npcs.yml` already gone); `KernelConfig.java:181` has only the `turf_powerups` `addFile` line, no `turf_npcs` line. **Conclusion: flip 1 fully finished the confirmed option-A design and Q2's cops-owned remainder; no escalation needed, no §6 Q1/Q2 fallback triggered.** |
| T1 poms | done | sonnet groupA | Edited 4 files: (1) `gangland-impl/pom.xml` — removed the `gangland-turf` `<dependency>` block (was immediately above the "Was reached transitively through cops-n-crooks" comment; no line-number drift issue, located by artifactId). (2) `gangland-features/gangland-turf/pom.xml` — added `keystone-command` + `keystone-module` (no scope, inherited `provided` from root `dependencyManagement`), changed `gangland-core` and `gangland-domain` to `<scope>provided</scope>`, added the `gangland-impl` `provided` dependency at the end with the mail-style comment (adapted to say "never names a turf type"). Left `keystone-hooks` in place (needed by `TurfIncomeCommand`'s `Currency` usage per the checklist). Did NOT add junit/mockito/testkit (inherited from root, per rule). (3) `gangland-features/cops-n-crooks/pom.xml` — verified only, **no edit needed**: `gangland-turf` dependency (lines 70-74) was already `<scope>provided</scope>` before this flip started. (4) `gangland-build/pom.xml` — added `<exclude>org.luckyraven:gangland-turf</exclude>` to the shade `artifactSet`, added the `gangland-turf` `<artifactItem>` to `copy-runtime-modules`, added the `gangland-turf` `provided` dependency at `${project.parent.version}` beside the gadget one. `gangland-features/pom.xml` — no change (turf already listed). **Done-when verified:** `mvn clean install -DskipTests` fails **only** in `gangland-impl` (Reactor Summary: everything through `Gangland Domain` = SUCCESS, `Gangland` = FAILURE, everything after SKIPPED — no cyclic-reference error). Errors confined to `command/sub/turf/**` (javac's default `-Xmaxerrs 100` truncates the printed list before reaching `TurfConfig`/repos/tables/contract-impls, but every error present is inside the T0/A file list). `mvn -q -pl gangland-features/gangland-turf install -DskipTests` (no `-am`) succeeds using the local-repo `gangland-impl` jar built before this session. **Drift from checklist:** the checklist's literal done-when command `mvn -q -pl gangland-features/gangland-turf -am install -DskipTests` (with `-am`) does **not** succeed at this checkpoint and will not until T3 lands — `-am` is scope-blind and treats the new `provided` `gangland-impl` dependency as a reactor prerequisite, so it tries to rebuild the currently-broken `gangland-impl` and fails. This is structural (same will be true after T2, until T3 moves the command tree), not a bug in the edits; verified turf-alone correctness with the `-am`-free form instead and noted it here for gate B's executor. |
| T2 module entry | done | sonnet groupA | Created `gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/TurfModule.java` (literal `MailModule`/`GadgetModule` shape: `@CustomLog`, `implements KeystoneModule`, `CONFIG_PACKAGE`/`LISTENER_PACKAGE`/`TASK_PACKAGE`/`COMMAND_PACKAGE`/`REPOSITORY_PACKAGE` constants, `configure()` registers both config stubs + both listener packages + the command package + the repository package). Created stub `TurfModuleFileConfig.java` (`@Configuration(phase = Phase.KERNEL)`, no beans) and stub `TurfModuleConfig.java` (`@Configuration`, no beans) so T2 compiles standalone; T7/T8 fill them. Created `gangland-features/gangland-turf/src/main/resources/module.yml` (`Id: turf`, `Name: Gangland Turf`, `Version: ${project.version}`, `Main: org.luckyraven.gangland.turf.TurfModule`, `Host_Api: 0.8`, `Artifact: org.luckyraven:gangland-turf`, no `Depends:`). Verified `module.properties` already existed at `org/luckyraven/gangland/turf/module.properties` with `module.name=${project.name}` — untouched. **Done-when verified:** `mvn -q -pl gangland-features/gangland-turf install -DskipTests` succeeds (no `-am`, see T1 drift note) and `unzip -p .../gangland-turf-0.8.4.jar module.yml` prints `Version: 0.8.4` — resource filtering ran (root pom's `<resources><filtering>true</filtering>` covers it; no per-module override needed). |
| **gate A** | done | sonnet groupA | `mvn clean install -DskipTests` (full reactor, from repo root): Reactor Summary shows every module through `Gangland Domain` SUCCESS, `Gangland` (gangland-impl) FAILURE, `Gangland Mail`/`Gangland Turf`/`Cops N Crooks`/`Gangland Gadgets`/all version-* SKIPPED. No cyclic-reference message. Error list saved to `brainstorming/module-split-2026-09-07/turf-errors-after-T1.txt` via the exact command in the assignment (100 `[ERROR].*\.java` lines — javac's default `-Xmaxerrs 100` cap; total `[ERROR]` line count in the raw log is 326). Unique files in the saved list, all inside `gangland-impl/src/main/java/org/luckyraven/gangland/command/sub/turf/`: `TurfBuffCommand.java`, `TurfCommand.java`, `TurfCreateCommand.java`, `TurfDeleteCommand.java`, `TurfGarrisonCommand.java`, `TurfIncomeCommand.java`, `TurfInfoCommand.java`, `TurfListCommand.java` (8 of the 17 command files — javac's error cap truncated mid-`TurfListCommand.java` before reaching the remaining 9 command files, `TurfConfig.java`, the 3 repositories, 3 tables, or the 2 contract impls; every error present is still within the T0/A 26-file inventory). Grepped the full untruncated log for any `[ERROR]` line outside `command/sub/turf/` or a `symbol:`/`location:` continuation — none found; no file outside the checklist's inventory appeared. **Stop condition not triggered.** |
| T3 commands moved | done | sonnet groupB | `git mv` all 17 files from `gangland-impl/src/main/java/org/luckyraven/gangland/command/sub/turf/` to `gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/command/` (`TurfCommand`, `TurfWandCommand`, `TurfPos1Command`, `TurfPos2Command`, `TurfCreateCommand`, `TurfDeleteCommand`, `TurfSetOwnerCommand`, `TurfListCommand`, `TurfInfoCommand`, `TurfShowCommand`, `TurfStatusCommand`, `TurfSelectCommand`, `TurfTpCommand`, `TurfIncomeCommand`, `TurfGarrisonCommand`, `TurfBuffCommand`, `TurfSelectionResolver`) — matches T0/A's list exactly, `TurfPowerupNpcCommand` was already in the cops module (confirmed T0/C), so exactly 17 not 18. Rewrote `package org.luckyraven.gangland.command.sub.turf;` → `package org.luckyraven.gangland.turf.command;` in all 17 via `sed`. No self-package turf imports existed to strip (checklist's expectation confirmed: every `import org.luckyraven.gangland.turf.*` in these files targets a sub-package — `data`, `manager`, `selection`, `powerups`, `contract`, `listener`, `task`, `capture`, `events`, `state` — never bare `turf.command`). Old `command/sub/turf/` directory is gone (git mv emptied it; `rmdir` confirmed nothing left). `TurfCommand.java` still calls `contributions.createFor("turf", getArgumentTree(), getArgument())` via `CommandContributions.from(container)` — carried verbatim, no fallback needed. `TurfSelectionResolver` kept `public final class` + `public static @Nullable Turf resolve(...)` (verified post-move) — the cops module's `TurfPowerupNpcCommand` calls it across the module boundary; T9 (not in scope for this group) will fix that cops-side import. Package-collision check: `find . -path "*gangland/turf/command*" -name "*.java" -not -path "*/target/*"` → only the 17 new files, no collision. **Done-when verified:** `grep -rn "org\.luckyraven\.gangland\.turf\." gangland-impl/src/main/java/org/luckyraven/gangland/command/` → empty. `mvn -q -pl gangland-features/gangland-turf install -DskipTests` (no `-am`, per T1's established drift — `-am` still tries to rebuild the red `gangland-impl` and fails) → green, clean, no errors/warnings printed. Full reactor `mvn clean install -DskipTests` error file count dropped from the T1 100-line-truncated file to 9 unique files (`TurfConfig.java`, 3 repositories, 3 tables, `GanglandTurfMessages`, `GanglandTurfSounds`) — confirmed by grepping both the `-Xmaxerrs`-truncated `.java`-suffixed lines and the full untruncated log for any file outside `command/sub/turf` — none found outside the 9-file inventory, and zero hits for `command/sub/turf` anywhere in the log. |
| T4 commands.json + count | done | sonnet groupB | Verified via `python -c "json.load(...)"` that core `commands.json` had all 16 T0/E keys present (`turf, turf_wand, turf_pos1, turf_pos2, turf_create, turf_delete, turf_setowner, turf_list, turf_info, turf_show, turf_status, turf_select, turf_tp, turf_income, turf_garrison, turf_buff`) at 177 total keys, `turf_powerupnpc` already absent (cops took it in flip 1). Cut lines 646-709 (the 16 contiguous turf blocks) out of `gangland-impl/src/main/resources/commands.json` into new `gangland-features/gangland-turf/src/main/resources/commands.json`, wrapped in `{ }`, tab-indented, byte-identical `usage`/`description` text preserved verbatim — validated as parseable JSON with exactly 16 keys in original order. Removed the same 64 lines from the core file and fixed the dangling trailing comma left on the new last entry (`filter_next`'s closing `},` → `}`) so the core file stays valid JSON. **Old count → new count: 177 → 161** (`gangland-impl/src/test/.../InformationManagerTest.java:41` `assertEquals(177, …)` → `assertEquals(161, …)`, comment extended with "minus the 16 turf entries that moved to the turf module's own commands.json in 0.8.4"). **Done-when verified:** `python -c "...[k for k in d if k.startswith('turf')]"` on the core file → `[]`. `python -c "...len(json.load(open('gangland-features/gangland-turf/.../commands.json')))"` → `16` (matches T0/E's 16, not 17, since `turf_powerupnpc` was already cops'). `mvn -q -pl gangland-features/gangland-turf install -DskipTests` (no `-am`, same T1 drift) → still green after the resource change. **Skipped:** the checklist's literal `mvn -q -pl gangland-impl -am test -Dtest=InformationManagerTest` → green — cannot run yet and was **not** attempted as a pass/fail gate: `gangland-impl` still fails to *compile* (9 files pending T5-T9, group C), so no test in that module can execute regardless of this change; `-am` on that command additionally surfaces an unrelated surefire error on `version-impl` before even reaching `gangland-impl`. This mirrors T1's recorded drift (structural, not a defect in this task's edits). The 161 count is verified by direct arithmetic and JSON inspection instead; the assertion will actually execute, and needs to be shown green, at T12 (full suite) once group C lands. `observation10_deadEntriesStillPresent`'s `DEAD_ENTRIES` list (dealer/kit/safe_wand/spawn/warp) contains no `turf` entries — left untouched, as instructed. |
| **gate B** | done | sonnet groupB | `mvn -q -pl gangland-features/gangland-turf install -DskipTests` (no `-am`, per T1 drift note — using the previously-installed local-repo `gangland-impl` jar since `gangland-impl` itself won't build until group C) → **green**, no errors. Full reactor `mvn clean install -DskipTests`: Reactor Summary — every module through `Gangland Domain` = SUCCESS, `Gangland` (gangland-impl) = **FAILURE**, `Gangland Mail` / `Gangland Turf` = SKIPPED (full-reactor build-order consequence of the impl failure, not a turf-module defect — confirmed separately green via the no-`-am` command above). No cyclic-reference message anywhere in the log. Saved `brainstorming/module-split-2026-09-07/turf-errors-after-T4.txt` via the exact command given in the assignment (`mvn clean install -DskipTests 2>&1 | grep -E "^[ERROR].*\.java" | sort -u`) — 96 lines (still under javac's default `-Xmaxerrs 100` cap; total raw `[ERROR]` line count 324, same as after T1/T3, confirming no new error source appeared). **Unique files named, extracted from both the truncated saved file and a second full untruncated log capture: exactly 9** — `config/TurfConfig.java`, `database/repositories/turf/{TurfRepository,ActiveTurfBuffRepository,TurfGarrisonRepository}.java`, `database/tables/turf/{TurfTable,ActiveTurfBuffTable,TurfGarrisonTable}.java`, `file/configuration/turf/{GanglandTurfMessages,GanglandTurfSounds}.java` — matching gate B's stated expectation ("now naming the 9 remaining files (TurfConfig, 3 repositories, 3 tables, 2 contract impls)") exactly. Grepped the full untruncated log for `command/sub/turf` → **zero hits**, confirming nothing from T3's scope remains and every remaining error belongs to T5 (persistence), T6 (contract impls) or T7 (`TurfModuleConfig`) — all group C, none group B. **Stop condition met as instructed: stopping here.** |
| T5 persistence | done | sonnet groupC | `git mv` the 3 repositories (`TurfRepository`, `ActiveTurfBuffRepository`, `TurfGarrisonRepository`) and 3 tables (`TurfTable`, `ActiveTurfBuffTable`, `TurfGarrisonTable`) from `database/{repositories,tables}/turf/` into `gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/database/`. Package declarations rewritten to `org.luckyraven.gangland.turf.database` via `sed`; the 3 same-package table imports (`TurfTable`, `ActiveTurfBuffTable`, `TurfGarrisonTable`) stripped since repository+table now share one package. `@Repository(...)` annotations and the `(JavaPlugin, DatabaseHandler, DatabaseBackend)` constructor signatures untouched. `TurfPowerupNpcRepository`/`TurfPowerupNpcTable` were not present (T0/G already confirmed no cops survivors) — both impl `turf/` directories under `database/repositories` and `database/tables` were empty after the move and were `rmdir`'d. **Done-when verified:** `mvn -q -pl gangland-features/gangland-turf install -DskipTests` (no `-am`, per the established T1 drift — turf resolves `gangland-impl` from the locally-installed jar) green; `grep -rn "org\.luckyraven\.gangland\.turf\." gangland-impl/src/main/java/org/luckyraven/gangland/database/` empty. |
| T6 contract impls | done | sonnet groupC | `git mv` `GanglandTurfMessages.java` and `GanglandTurfSounds.java` from `file/configuration/turf/` to `gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/config/`; package declaration rewritten to `org.luckyraven.gangland.turf.config`. Both already carried explicit imports for `TurfMessageContract`/`TurfSoundContract` (from `turf.contract`, which stays put as the test seam) — survived the move unchanged, nothing to add. Emptied `gangland-impl/.../file/configuration/turf/` directory removed. **Done-when verified:** `mvn -q -pl gangland-features/gangland-turf install -DskipTests` green; `grep -rn "org\.luckyraven\.gangland\.turf\." gangland-impl/src/main/java/org/luckyraven/gangland/file/` empty. |
| T7 TurfModuleConfig | done | sonnet groupC | Counted `@Bean` annotations in source `TurfConfig.java` before moving: 21 real annotations (23 raw `grep -c "@Bean"` hits include 2 in the `TurfBossBarListener`/`TurfCaptureNotifier` explanatory comment — confirmed with `grep -n "^\s*@Bean\s*$"` → 21). Filled the T2 stub `TurfModuleConfig.java` (package `org.luckyraven.gangland.turf`) with the full body verbatim: all 21 `@Bean` methods including every parameter (the `@SuppressWarnings("unused") Settings settings` ordering edge on `turfContributionSettings`, the `Gangland plugin` parameters), the class Javadoc, and the trailing 6-line `TurfBossBarListener`/`TurfCaptureNotifier` non-bean comment, copied verbatim. Imports updated: `org.luckyraven.gangland.database.repositories.turf.*` → `org.luckyraven.gangland.turf.database.*` (T5's new location), `org.luckyraven.gangland.file.configuration.turf.*` → `org.luckyraven.gangland.turf.config.*` (T6's new location); `TurfNpcContracts` import unchanged (same-module, never moved). Re-counted `@Bean` in the new file: 21 (`grep -n "^\s*@Bean\s*$"`), matches source exactly. Kept concrete-type repository injection (`TurfRepository`/`ActiveTurfBuffRepository`/`TurfGarrisonRepository` as `@Bean` parameters) per Q4's default — did not switch to the mail `registry.getRepository(...)` pattern. Kept all three `manager.initialize()` calls (`activeBuffManager`, `garrisonManager`, `turfManager`). Deleted `gangland-impl/.../config/TurfConfig.java` (`git rm`). `config/TurfNpcsConfig.java` was already absent (T0/G) — step 3 (fold turf-only remainder) is a confirmed no-op. **Done-when verified:** `ls gangland-impl/.../config/TurfConfig.java` → "No such file"; `mvn -q -pl gangland-features/gangland-turf install -DskipTests` green. **Unplanned follow-on fix (recorded, not a checklist task):** the full-reactor build (gate C) then failed test-compilation in `gangland-impl` on `HolderSeamBeanTypeTest.java` (added by flip 1's G5 fix, not inventoried by this checklist) — it imported `org.luckyraven.gangland.turf.turfnpcs.TurfNpcContracts` and referenced `TurfConfig.class` by literal, both now gone from impl's classpath since T1 removed the `gangland-turf` dependency entirely. This is a direct, mechanical consequence of T7 deleting `TurfConfig`, and core test code may not name a module type post-flip, so the `turfNpcContractsBeanIsDeclaredAsTheHolderClass` test method (plus its now-dead `TurfNpcContracts` import) was moved out of `gangland-impl/src/test/.../config/HolderSeamBeanTypeTest.java` into a new `gangland-features/gangland-turf/src/test/java/org/luckyraven/gangland/turf/TurfModuleConfigHolderSeamTest.java`, asserting `TurfModuleConfig.turfNpcContracts()` declares `TurfNpcContracts` as its return type (same reflective assertion, same rationale, adjusted `configuration` target from `TurfConfig.class` to `TurfModuleConfig.class`). The 3 remaining `DataConfig`-based assertions (`moneyDropClassifier`, `bankTiers`, `wantedKillTrackers`) stayed in core untouched. This is outside T5-T9's literal scope but was required to satisfy the group's own "whole reactor green" done-when and gate G1 — flagged here for the scrum master since it is a plan gap (this test was never in turf.md's §1.1 inventory), not a defect in T7's own edits. |
| T8 YAML | done | sonnet groupC | `git mv gangland-impl/src/main/resources/turf/turf_powerups.yml` → `gangland-features/gangland-turf/src/main/resources/turf/turf_powerups.yml` (same `turf/` sub-path, byte-identical). Located the `turf_powerups` `fm.addFile(...)` line in `KernelConfig.java` by symbol (`grep -n "turf_powerups"`) — found at line 181 (drift from the checklist's `:187` at `d6bb33ac`, confirming flip 1's cops T17 already removed the `turf_npcs` + `npc/*` lines above/below it, exactly as §1.4/T8 predicted); deleted the single line, no `turf_npcs` line was present to leave alone. `gangland-impl/src/main/resources/turf/` was empty after the move (`turf_powerups.yml` was the last file, `turf_npcs.yml` already gone per T0/G) — `rmdir`'d. Filled the T2 stub `TurfModuleFileConfig.java` (`@Configuration(phase = Phase.KERNEL)`) with the constructor-injected `Gangland gangland` field and the `turfModuleFiles(FileManager, ModuleLoader)` `@Bean` exactly as the checklist's literal code block, and created `TurfModuleFiles.java` as the marker record (`FileHandler powerups`), matching `CopsNCrooksYamlConfig`/`CopsNCrooksFiles`'s reference shape. **Done-when verified:** `test ! -d gangland-impl/src/main/resources/turf && echo OK` → `OK`; `grep -n "turf_powerups\|turf_npcs" gangland-impl/.../config/KernelConfig.java` → empty; `mvn -q -pl gangland-features/gangland-turf install -DskipTests` green. (Jar-content check `unzip -l target/modules/gangland-turf-0.8.4.jar` deferred to T14/G3, which is group D's task, not this group's gate.) |
| T9 cops updates | done | sonnet groupC | `gangland-features/cops-n-crooks/src/main/resources/module.yml` — added block-style `Depends:` / `  - turf` (exactly two-space indent, no flow form) after `Host_Api` and before `Artifact`. `TurfPowerupNpcCommand.java` (cops module, `copsncrooks/command/turf/`) — changed `import org.luckyraven.gangland.command.sub.turf.TurfSelectionResolver;` → `import org.luckyraven.gangland.turf.command.TurfSelectionResolver;` (the only site `grep -rn "command\.sub\.turf" gangland-features/cops-n-crooks/src` found). Verified no other cops reference to `gangland.database.{repositories,tables}.turf`, `gangland.file.configuration.turf`, or `gangland.config.TurfConfig` (`grep` → empty). Verified the cops pom's `gangland-turf` dependency was already `<scope>provided</scope>` (set in T1 step 3, group A) — unchanged, no `Depends: - weapon` added. **Done-when verified:** `mvn -q -pl gangland-features/cops-n-crooks install -DskipTests` green; `unzip -p gangland-features/cops-n-crooks/target/cops-n-crooks-0.8.4.jar module.yml` shows `Host_Api: 0.8` / `Depends:` / `  - turf` / `Artifact: org.luckyraven:cops-n-crooks`. |
| **gate C** | done | sonnet groupC | `mvn clean install -DskipTests -q` (repo root, full reactor) → **exit 0, zero `[ERROR]` lines** (verified twice: once via `tee` to `brainstorming/module-split-2026-09-07/turf-gateC-build.txt`, once via a clean redirect capturing `$?` directly to rule out a pipe-status false positive — both `MVN_EXIT=0`). `grep -rn "org\.luckyraven\.gangland\.turf\." gangland-impl/src` → empty (main and test, confirmed after the `HolderSeamBeanTypeTest` fix above). `grep -rn "TurfConfig\|TurfNpcsConfig" gangland-impl/src/main/java` → empty (no cops-owned survivors to report — T0/G already found none). `grep -rn "turf" gangland-impl/src/main/resources` reviewed by hand: every hit is either `message/message_en.yml` (`TURF_*` message text using the literal word "turf", e.g. `Entering %turf%`) or `settings.yml`'s `Turf:` block comments/keys — both explicitly the checklist's KEEP list (§1.1: shared top-level YAML, module reads via `Settings`/`Messages`); `message_es.yml` and `commands.json` have zero "turf" hits. No dead or stray reference found. `test ! -d gangland-impl/src/main/resources/turf` → passes (dir absent). **All group-C done-when conditions satisfied; stopping at G1 as instructed, not proceeding to T10.** |
| T10 TurfModuleTest | done | sonnet groupD | Created `gangland-features/gangland-turf/src/test/java/org/luckyraven/gangland/turf/TurfModuleTest.java` modelled literally on `GadgetModuleTest`/`MailModuleTest`: `configure_declaresConfigsAndPackages` asserts `registrations.configurations() == List.of(TurfModuleFileConfig.class, TurfModuleConfig.class)`, `listenerPackages() == List.of(LISTENER_PACKAGE, TASK_PACKAGE)`, `commandPackages() == List.of(COMMAND_PACKAGE)`, `repositoryPackages() == List.of(REPOSITORY_PACKAGE)`; `declaredPackages_matchClasses` asserts the four constants equal the real `getPackageName()` of `TurfBossBarListener` (`turf.listener`), `GangPresenceListener` (`turf.task`), `TurfCommand` (`turf.command`) and `TurfRepository` (`turf.database`) — all four classes already lived in exactly those packages post-group-C, confirmed by `Glob` before writing the test. **Red-first evidence (T10's documented typo procedure, since the group order puts T10 after the moves):** temporarily changed `TurfModule.COMMAND_PACKAGE` to the literal `"org.luckyraven.gangland.turf.commands"`, ran `mvn -pl gangland-features/gangland-turf test -Dtest=TurfModuleTest`: `declaredPackages_matchClasses` failed with `org.opentest4j.AssertionFailedError: expected: <org.luckyraven.gangland.turf.commands> but was: <org.luckyraven.gangland.turf.command>` at `TurfModuleTest.java:43` (`configure_declaresConfigsAndPackages` stayed green since both sides of that assertion reference the same mutated constant — expected per the checklist, only the class-derived assertion can go red this way). Reverted the typo; re-ran `mvn -pl gangland-features/gangland-turf test -Dtest=TurfModuleTest` → **green, exit 0, both tests pass.** |
| T11 turf suite | done | sonnet groupD | `mvn -pl gangland-features/gangland-turf test` → **12 test classes, 87 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS**: `CaptureServiceHelpersTest` (10), `CaptureServiceOwnedTurfTest` (13), `CaptureServiceStartAndCompleteTest` (8), `CaptureServiceUnclaimedTurfTest` (9), `CuboidRegionTest` (10), `TurfManagerTest` (7), `ActiveBuffManagerTest` (8), `ActiveTurfBuffTest` (6), `GarrisonManagerTest` (9), `PowerupRegistryTest` (4), `TurfModuleConfigHolderSeamTest` (1, added by group C's T7 follow-on fix), `TurfModuleTest` (2, this task). No `-am` needed (siblings already installed by the G1/G3 builds run for this group). Pom scope changes (`gangland-core`/`gangland-domain` → `provided`) did not break anything — confirms the checklist's "should be a no-op" expectation. |
| T12 full suite | done | sonnet groupD | **Gate G2.** `mvn test -q -B` (repo root, full reactor) → **exit 0**, output redirected to a scratch file and grepped for `BUILD FAILURE` (0 hits) and any `Tests run:...Failures: [1-9]|Errors: [1-9]` line (0 hits) — genuinely green, not just quiet. Individually re-ran and confirmed the checklist's two named assertions: `mvn test -pl gangland-impl -Dtest=InformationManagerTest` → `Tests run: 5, Failures: 0, Errors: 0` (the `processCommands_populatesFromBundledJson` case now asserts the T4-recomputed `161`); `mvn test -pl gangland-impl -Dtest=CommandContributionsTest` → `Tests run: 2, Failures: 0, Errors: 0` (unchanged, the `"turf"` string assertion still passes). `mvn test -pl gangland-features/cops-n-crooks` → `Tests run: 77, Failures: 0, Errors: 0` including `CopsNCrooksModule` (3 tests, green with the new `Depends: [turf]` in its `module.yml`). |
| T13 docs | done | sonnet groupD | **`documentation/module-loader.md`** (5 edits): (1) folder tree gained `│   ├── gangland-turf-0.8.4.jar      turf capture, contribution, garrison gameplay`. (2) module table: split the old combined `turf, weapon` row into a `turf` row (`runtime module since 0.8.4`) and a `weapon`-only row (`still a compile-time dependency of gangland-impl; next in line`); the "Order for the remaining flips" paragraph rewritten to say the DAG's remaining order is **weapon** only and that `cops-n-crooks`' `module.yml` now carries `Depends: [turf]`. (3) the `Depends:` paragraph gained one sentence naming the live `cops-n-crooks → turf` example. (4) the 5-arg-`FileHandler` paragraph gained a second worked example (`turf/turf_powerups.yml` via the KERNEL-phase `TurfModuleFileConfig`, with the race-condition rationale). (5) **`## Core seams`** — confirmed exactly one heading before and after (`grep -c "^## Core seams"` → 1 both times); turf added **no new row**, per plan — instead amended the *existing* `TurfNpcContracts` row's "Core package" cell (there is no literal "registered by" column in the live table; that cell is where `TurfConfig`/core vs `TurfModuleConfig`/module is documented) to read "`turfNpcContracts()` is now registered by `TurfModuleConfig` in the turf module, moved there verbatim by the turf flip" instead of "gangland-turf, still core". **`CLAUDE.md`** (2 edits, gitignored/local-only per the file's own note): module table row 153 → `**Runtime module since 0.8.4** (modules/gangland-turf-<rev>.jar, never in the core jar): turf capture, contribution, garrison gameplay`; the two-tier paragraph (was 171-174) rewritten so the runtime-module list reads `gangland-mail` and, since 0.8.4, `cops-n-crooks`, `gangland-gadget` and `gangland-turf`, weapon named as the sole remaining compile-time dependency, and the sentence about `cops-n-crooks`' `Depends:` updated to state it now carries `- turf` (present tense, no longer "gains ... at flip 3"). **`documentation/README.md`**: `grep -n -i turf documentation/README.md` → no hits before or after — confirmed no edit needed (matches T13 step 7's "otherwise no edit"). **`git diff --stat -- documentation/`**: `documentation/module-loader.md | 24 ++++++++++++++++--------` (16 insertions, 8 deletions) — this is gate G4's evidence, reported again under T14. **Verification:** `grep -n "turf" CLAUDE.md documentation/module-loader.md` reviewed line-by-line — no sentence anywhere claims turf is still a compile-time dependency of impl. **Skipped, per this group's explicit assignment rules (which override turf.md step 8):** memory file `project_module_loader_plan.md` — the assignment states "memory files are the scrum master's"; not touched. |
| T14 gates + graphify | done | sonnet groupD | **G1** `mvn clean install -DskipTests -q` → exit 0 (full reactor, whole log captured to scratch, zero `[ERROR]` lines). `grep -rn "org\.luckyraven\.gangland\.turf\." gangland-impl/src` → empty. `grep -rn "gangland-turf" gangland-impl/pom.xml` → empty. `test ! -d gangland-impl/src/main/resources/turf && echo OK` → printed `turf/ removed from core resources`. **G2** see T12 above — `mvn test -q -B` exit 0, `InformationManagerTest` green at 161, `CommandContributionsTest` green, cops tests green with `Depends: turf`. **G3** `mvn clean package -DskipTests -q` → exit 0. `unzip -l target/gangland_warfare-0.8.4.jar \| grep -c "gangland/turf/"` → **0**. `unzip -l target/gangland_warfare-0.8.4.jar \| grep "turf/turf_powerups.yml"` → **no output** (grep exit 1). `unzip -l target/gangland_warfare-0.8.4.jar \| grep -c "org/luckyraven/gangland/weapon/"` → **143** (weapon still core, as expected pre-flip-4). `unzip -l target/modules/gangland-turf-0.8.4.jar` → 121 files: `module.yml`, `commands.json`, `org/luckyraven/gangland/turf/module.properties`, `turf/turf_powerups.yml`, and every class under `org/luckyraven/gangland/turf/**` only (17 command classes, 6 database classes, 2 config contract-impl classes, the 4 module-entry classes, plus the full capture/powerups/listener/task/data/selection/state/events/turfnpcs/contract/contribution trees) — no class outside `org/luckyraven/gangland/turf/`. `unzip -p target/modules/gangland-turf-0.8.4.jar module.yml` → `Id: turf`, `Version: 0.8.4`, **no `Depends:` key**. `unzip -p target/modules/cops-n-crooks-0.8.4.jar module.yml` → `Depends:` present as a block list with `  - turf`. `commands.json` counts: core `python -c "len(json.load(...))"` → **161**; turf module → **16**. **G4** `git diff --stat -- documentation/` → `documentation/module-loader.md \| 24 ++++++++++++++++--------` (16 insertions(+), 8 deletions(-)) — `CLAUDE.md` is gitignored so it never appears in this diff, matching T13's note. **M12** `graphify update . --force` run; tail pasted into the final report to the scrum master. |
