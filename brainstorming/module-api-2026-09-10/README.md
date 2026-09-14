# Module API wave — 2026-09-10

Two asks (user, 2026-09-10):

1. A command that installs runtime modules without the admin downloading jars by hand.
2. Modules talk to Gangland Warfare through a versioned API contract, so a module's version and the plugin's
   version need not match — "an api layer rather than insert and remove".

Branches: Gangland `0.9.1` (cut from `0.9.0`, revision bumped), Keystone `phase-h9-host-api` (1.9.1 → 1.9.2).
Nothing committed by the session; the user reviews and commits.

## 1. What the research found (three explorer agents, 2026-09-10)

| Fact | Where |
|---|---|
| `Host_Api` is compared by **exact `major.minor` equality** | Keystone `ModuleDescriptor.isCompatibleWith`, `ModuleDescriptor.java:68-71`; the only caller is `ModuleResolution.java:77-88` |
| The host value **is the plugin version's `major.minor`** (`0.9.0` → `0.9`), so every Gangland minor release rejects every module | `GanglandContext.hostApi(Gangland)`, `GanglandContext.java:121-125` |
| Keystone 1.9.1 already ships a complete, tested, JDK-only Maven-repo installer that Gangland never wires: `module.artifact.{ArtifactCoordinate, MavenRepository, MavenMetadata, ArtifactResolver}` (metadata fetch, SHA-256-verified download to `.part` + atomic move, async via the Bukkit scheduler) and `module.update.{ModuleUpdateService, ModuleUpdate, ModuleMessages}` (check newest release, download, retire the old jar into `modules/.stale/` or leave a `<jar>.stale` marker on a Windows lock; `ModuleLoader.purgeStale()` deletes marked jars at the next boot) | in the consumed `keystone-module-1.9.1.jar` since 1.8.0; zero Gangland references |
| `Artifact: org.luckyraven:<artifactId>` is already in all six `module.yml`s and is read only by that update service | `ModuleUpdateService.java:71-75` |
| `ModuleLoader` is a container bean (`loaded()`, `find(id)`, `faults()`, `modulesDirectory()`, `hostApi()`) | `GanglandContext.java:117` |
| Modules import **95** host classes; **68** already live in library modules shaded into the core jar (domain 19, shop-api 18, sign-api 14, item 8, inventory-api 6, core 3); only **27** live in `gangland-impl` | import census, `scratchpad/census.json` |
| Of the 27: `Gangland` (89 files) is **never dereferenced** — it is passed to Keystone constructors that take `JavaPlugin`, plus two string constants; `Messages` (64 files), `Settings` (27), `GanglandChatUtil` (43) have **no** imports of `bootstrap`/`database`/`config` and move as-is; 22 classes are touched by 1–2 files each | agent B census |
| `DependencyContainer.registerInstance` walks the superclass chain and interfaces, so a `JavaPlugin` constructor parameter resolves to the Gangland instance | Keystone `DependencyContainer.java:145-153` |
| Bootstrap isolation gap: a module bean **body** that hits a deleted host method (`NoSuchMethodError`) escapes `BeanFactory.invokeBean` (`BeanFactory.java:488-492`) and aborts `Gangland.onEnable` (`Gangland.java:91-96`); only missing *types in signatures* are guarded (`ReflectionGuard`, 1.9.1) | agent A §2.2 |
| No library module depends on `gangland-impl`; `gangland-build` shades every `org.luckyraven:*` artifact except the six modules | `gangland-build/pom.xml:70-88` |

Docket: the exact Keystone code path the install command exercises has six open findings (KS-MO-01..06, all
without a status row = open). 01–04 are fixed in this wave (they sit in the install/update/purge path); 05–06
are cosmetic and stay open.

## 2. Decisions

| # | Decision | Why (ponytail: shortest correct diff) |
|---|---|---|
| D1 | `Host_Api` becomes **minor-compatible**: same major, module minor `<=` host minor. One operator in `ModuleDescriptor.isCompatibleWith` (Keystone 1.9.2). | Fault code, loader, descriptor format unchanged. |
| D2 | The host API line is a **constant owned by the api artifact** (`GanglandApi.VERSION = "1.0"`), no longer derived from the plugin version. Every `module.yml` says `Host_Api: 1.0`. Bump the minor when the host adds API a module may rely on; the major on a breaking change. | This is the half that actually decouples module and plugin versions. |
| D3 | Install command = **wire Keystone's existing resolver/update service** into `/glw module list\|install\|update\|remove`. No new download code, no new dependency. One `settings.yml` knob: `Modules.Repository` (default Maven Central; a mirror or `file:///` works). The six official ids map to coordinates in a code constant; third-party modules pass `group:artifact[:version]`. | Catalogue changes only with a plugin release, so it is not config. |
| D4 | Install validates the downloaded jar's descriptor (Host_Api compatible, Id sane) and deletes it otherwise; warns on absent `Depends:`/`Plugins:`; restart-required by design (classes cannot be unloaded, `ModuleLoader.java:45-46`). `remove` writes Keystone's `<jar>.stale` marker (jars are locked on Windows while loaded). | Reuses the loader's own purge mechanism. |
| D5 | New Maven module **`gangland-api`** (`org.luckyraven:gangland-api`, `${revision}`), the only host artifact a module compiles against (`provided`). It depends at compile scope on `gangland-core`, `gangland-domain`, `gangland-item`, `inventory-api`, `sign-api`, `shop-api` (re-exported to modules) and on Keystone/Spigot at provided. `gangland-impl` depends on it at compile scope so `gangland-build` shades it into the core jar. | The compiler then enforces the contract, the same trick that keeps the core from naming a module class. |
| D6 | The 19 pure impl classes **move with their package unchanged** (`Messages`, `Settings`, `GanglandChatUtil`, `Command` + its two help data types, `CommandContribution(s)`, `BankTiers`/`BankTierView`, `GanglandMoneyDropClassifier`/`NpcMoneyDropSource`, `Waypoint`, `UserLevelUpEvent`, `GanglandShopDisplayResolver`, `ItemAttributes`, the sign seam/aspect/parser/type classes, `TimeMessages`), plus `WeaponParsedSign` → sign-api and `UniqueItemAddon` → gangland-item. | Package unchanged = zero import edits in 143 module files. |
| D7 | `Gangland` is **not** in the API. Modules take `JavaPlugin` (89-file mechanical swap); `Gangland.FULL_PREFIX`/`SHORT_PREFIX` move to `GanglandApi`. `GanglandContext` is not in the API: the four module configs inject Keystone's `DependencyContainer` (already registered) instead of a new `BeanLookup`. `WaypointManager` stays in impl behind a two-method `WaypointLookupContract` (cops, 2 files). `BankCommand` stays; only `BYPASS_CAP_PERMISSION` relocates. `PlaceholderService` stays: gadget's unused parameter is dropped if it is not a bean-ordering edge. | No interface with one implementation unless the class genuinely cannot move. |
| D8 | **Contract rule** (documented in `documentation/module-loader.md`): within a major, `gangland-api` only adds — no removal, rename or signature change of a public member. New module-specific strings and config knobs go in the **module's own YAML** (the module jar already carries its defaults, e.g. `npc/cops.yml`); the 179 module-owned `Messages` constants and ~130 module-owned `Settings` getters that already exist stay in the api as legacy and are migrated per module in a later wave. | Moving them now is a multi-day churn with no user-visible gain; the rule stops the coupling from growing. |
| D9 | Order: Keystone 1.9.2 ∥ install command → api split (the split moves `Messages`/`Settings`, which the command wave edits). `<keystone.version>` bumped to 1.9.2 last. | Disjoint files per executor. |

Skipped, add when needed: a `HostApi` value type or version ranges (add when a module needs an upper bound);
a hot-reload path (classes cannot be unloaded); a remote module catalogue (coordinates cover it); a binary-
compatibility check (`japicmp`/`revapi` on `gangland-api` — add when the first api version is published to
Central, there is nothing to diff against yet); per-module bootstrap isolation (needs rollback-capable bean
registration in Keystone — a Keystone phase of its own; the api contract + Host_Api check are the prevention).

## 3. Waves and status

| Wave | Repo / branch | Scope | Executor | Status |
|---|---|---|---|---|
| W1 | Keystone `phase-h9-host-api` → 1.9.2 | D1 + docket KS-MO-01 (purgeStale per-file try/catch), KS-MO-02 (coordinate/version sanitising + path containment), KS-MO-03 (validate the downloaded descriptor before `retire`), KS-MO-04 (lock the `pending` mutation); tests red-first; `docs/phase-h9-host-api.md`; `mvn clean install` | opus | **done 2026-09-10** — BUILD SUCCESS, 1054 tests, 1.9.2 in `~/.m2`; uncommitted |
| W2 | Gangland `0.9.1` | D2 (as `GanglandContext.HOST_API`, relocated to `GanglandApi` in W3), D3, D4: `command/sub/module/*` (`ModuleCommand`, `ModuleListCommand`, `ModuleInstallCommand`, `ModuleUpdateCommand`, `ModuleRemoveCommand`, pure `ModuleInstalls` + 21 tests), `ModuleUpdateService` bean in `WiringConfig`, `Modules.Repository`, 22 `Messages` + en/es keys, `commands.json`, `documentation/module-loader.md` | opus | **done 2026-09-10** — impl 227 tests green |
| W3 | Gangland `0.9.1` | D5–D8: `gangland-api` module (30 moves incl. `HelpInfo`, `WaypointTeleport`, `TeleportEvent`, `IllegalTeleportException`; `WeaponParsedSign` → sign-api, `UniqueItemAddon` → gangland-item), pom flips, 89-file `JavaPlugin` sweep, `DependencyContainer` in 4 module configs, `WaypointLookupContract`, `BankTiers.BYPASS_CAP_PERMISSION`, gadget `Placeholder` param, `Command.getGangland()` deleted (Keystone's `getPlugin()`), docs | opus | **done 2026-09-13** (two 429 stops, resumed) — 258 files, BUILD SUCCESS, 814 tests; no api class in any module jar; no Keystone class in the core jar |
| W4 | both | `<keystone.version>` 1.9.2, full reactor build + tests, smoke rows M1 (new: six api-compiled modules + `/glw module list|install|remove` from the console) and D9 (Host_Api 0.8 copy rejected), docket (KS-MO-01..04 fixed, KS-MO-07 added), CLAUDE.md, memory | Fable | **done 2026-09-13** — M1 PASS, D9 PASS, 0 errors (`smoke/reports/2026-09-13-1746-*`) |

Nothing is committed: the user reviews `git status` on Gangland `0.9.1` (30 renames + ~230 modified) and Keystone `phase-h9-host-api`.

**Keystone branch also carries one change from another session (2026-09-13, Bartizan session `bartizan-e0`):**
`keystone-command/.../brigadier/BrigadierTabRegistrar.java` (+15/-3, four `suggests(ASK_SERVER)` lines so Paper's
Brigadier string-argument nodes ask the server for tab completions) plus a new test under
`keystone-command/src/test/.../brigadier/`. `mvn -pl keystone-command -am test` is green with it; the repackaged
`Keystone-1.9.2.jar` (17:55) postdates smoke rows M1/D9 (17:46), so that patch is unit-tested, not smoke-covered.
Asked that session to add it to `docs/phase-h9-host-api.md`. The jetpack root cause was the test server's stale
`plugins/Bartizan/items/wearables.yml` (pre-split `Jetpack:` block), fixed Bartizan-side; no Gangland change.

## 4. Follow-ups (not in this wave)

- Publish `gangland-api` and the six module artifacts to Maven Central (`central-release` profile exists; namespace/GPG/token still pending user action). Until then `/glw module install` only works against a mirror/`file:` repository. Central Portal publishes `.sha256` beside every file, which the resolver requires.
- Migrate module-owned `Messages` constants and `Settings` getters into module YAML (per module, additive to the api, no api bump).
- Keystone: per-module bootstrap isolation (see §1 last row); KS-MO-05/06.
- `japicmp` on `gangland-api` once a released version exists.
