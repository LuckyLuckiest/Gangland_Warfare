# Module split sprint — flipping cops-n-crooks, gadget, turf and weapon into runtime modules

**SPRINT COMPLETE 2026-09-08.** All five features are runtime modules: mail (0.8.2), cops-n-crooks `04d5171b`, gadget `0550aaf0`+`4304172c`, turf `6ffc6d85`, weapon `0272008b`. Core `commands.json` 225 → 149. Remaining for the user: G6 server smoke of all modules (checklist in `documentation/module-loader.md`), push `0.8.4`, decide the group id for wave 3.

Sprint opened 2026-09-07. Branch **`0.8.4`** cut 2026-09-07 19:40 at c285b78c from `0.8.3` after the `p0-wave-3` wave landed (merge cc67598d, docket a8520601); root `<revision>` 0.8.4. Baseline `mvn clean install -DskipTests` green at that commit.
Scrum master / orchestrator: the Claude session that owns this folder. Planners: Opus agents, one per feature.
Executors: Sonnet agents, one per task group, strictly in the flip order below.

**Artifact page:** https://claude.ai/code/artifact/031591f0-0342-4073-baea-d49532237471 ("Module Split Sprint") — this
board, the report feedback, the consistency review and the four checklists in one page. Status tiles read the page's
shared database (collection `flips`, doc ids `cops|gadget|turf|weapon`, fields `plan, execution, review, note,
updatedAt`); the scrum master writes them with `write_db` at flip boundaries and republishes the page from
`build_board.py` when the markdown changes.

## Sources of truth (read in this order)

1. `documentation/module-loader.md` — the Gangland-side module contract and the mail recipe.
2. Design report "Gangland Module Loader" (artifact `1687bb19`, 2026-09-03) — the wave plan; the checklist below
   is the executable form of its "Wave 2" steps, corrected by what the mail pilot taught us.
3. `gangland-features/gangland-mail/**` — the reference implementation of a runtime module (pom, `module.yml`,
   `MailModule`, `MailModuleConfig`, `commands.json`, `CommandContribution` beans, `MailModuleTest`).
4. `bootstrap/GanglandContext.java`, `config/DatabaseConfig.java`, `config/KernelConfig.java` — the host hooks.
5. Keystone 1.8.0 (`E:\Programming\java\Keystone`, branch `phase-h7-module-loader`): `docs/keystone-module.md`,
   `keystone-module/**`, `keystone-persistence/.../FileHandler.java` (5-arg constructor).

## Flip order and why it is fixed

The feature poms form a DAG: `gadget → weapon`, `cops-n-crooks → weapon + turf`. A feature can only be flipped once
nothing left in the core's compile closure depends on it, so the only incremental order is

| Flip | Feature | Module id | Java package root | Impl files that reference it today |
|---|---|---|---|---|
| 1 | `gangland-features/cops-n-crooks` | `copsncrooks` | `org.luckyraven.gangland.copsncrooks` | 101 main, 5 test |
| 2 | `gangland-features/gangland-gadget` | `gadget` | `org.luckyraven.gangland.gadget` | 29 main |
| 3 | `gangland-features/gangland-turf` | `turf` | `org.luckyraven.gangland.turf` | 30 main |
| 4 | `gangland-features/gangland-weapon` | `weapon` | `org.luckyraven.gangland.weapon` | 55 main, 2 test |

Each flip is executed and reviewed to green before the next starts. Checklists are planned in parallel, but a
later checklist must state which earlier flips it assumes.

`module.yml` `Depends:` evolves as flips land: after flip 1 cops has **no** `Depends` (weapon and turf are still in
the core jar); flip 3 adds `Depends: [turf]` to cops; flip 4 adds `Depends: [weapon]` to cops and gadget. The later
checklist owns those edits to the earlier modules.

## Ground rules for every agent in this sprint

- Orient with `graphify query/explain/affected` first (graph refreshed 2026-09-07 16:30); open raw files only after
  the graph has pointed at them. Quote `source_location` when citing.
- House rules from `CLAUDE.md` apply unchanged: Spigot only, method braces on their own lines, Lombok `@CustomLog`,
  block-style YAML with `Capitalized_Underscore_Separated` keys, `ChatUtil.color()` with `&` codes, no Paper APIs,
  never add test dependencies to a module pom (they are inherited from the root pom), `commands.json` entry in the
  jar that owns the command.
- Direction is **module → core**. A module may import `Messages`, `Settings`, managers and any core bean. The core
  may never name a module type; when core code needs something a module provides, add a small core seam interface
  and let the module register a bean implementing it (`CommandContribution` is the model; pull implementations with
  `container.getAllInstances(Seam.class)`).
- Module resources are read through the parent-first `ModuleClassLoader`, so a module YAML default must sit in the
  module jar at **exactly the data-folder path** (`FileHandler(plugin, "rifle", "weapon", ".yml", loader)` reads
  `weapon/rifle.yml`) and that path must no longer exist in the core jar. Module-private files (`module.yml`,
  `commands.json`, `module.properties`) are read from the module's own jar via `LoadedModule.readResource`.
- Do not touch `.claude/worktrees/**`. A separate session (`p0-wave-3`, pid 19728) is editing the P0 permission
  fixes there; it modifies `config/CopsAndGadgetsConfig.java`, `config/GameplayConfig.java`,
  `command/sub/gang/*`, `command/sub/waypoint/*`, `command/sub/debug/DebugCommand.java`,
  `data/placeholder/worker/GanglandPlaceholder.java`, `file/configuration/Messages.java`,
  `sign/GanglandSignInformation.java`, gadget `listener/car/{CarDamageListener,CarEntityInteractListener}.java`,
  `message_en/es.yml`, sign-api `sign/listener/SignCreation.java` + `sign/service/SignInformation.java`; and it ADDS
  `gangland-impl/.../gadget/GanglandCarGangs.java` (a core class in the gadget module's root package),
  `gangland-gadget/.../gadget/car/access/{CarAccessPolicy,CarGangContract}.java`, sign-api
  `sign/SignPermissions.java` + `sign/listener/SignProtection.java`, a `carAccessPolicy` bean in
  `CopsAndGadgetsConfig`, and edits the tracked bug-docket files under `brainstorming/bug-docket-2026-09-06/`.
  Flag any task that moves one of those files so the scrum master can merge `0.8.3` first.
- Executors never commit; the scrum master commits at flip boundaries after review. Executors update the status
  table of their checklist after every task (status, what changed, anything skipped) and report failures verbatim.
- Every new test must be shown red before the change that makes it green, or the checklist must say why it cannot.
- Bugs noticed on the way go to the bug docket (`brainstorming/bug-docket-2026-09-06/`, see CLAUDE.md), never only
  into a commit message.

## Gates per flip (the definition of done)

| Gate | Check | Command / evidence |
|---|---|---|
| G0 pre | branch `0.8.4` at the tip of `0.8.3` (merge if `0.8.3` moved) | `git log --oneline 0.8.3 -1` vs `git merge-base` |
| G1 compile | reactor builds, impl before the module, zero feature imports left in impl | `mvn clean install -DskipTests -q`; `grep -rn "org\.luckyraven\.gangland\.<pkg>\." gangland-impl/src` empty |
| G2 tests | full suite green, counts adjusted (e.g. `InformationManagerTest` 225 → n) | `mvn test` |
| G3 jars | core jar has no module class, module jar has `module.yml` + `commands.json` + its YAML | `mvn clean package -DskipTests`; `unzip -l target/gangland_warfare-0.8.4.jar \| grep gangland/<pkg>` empty; `unzip -l target/modules/<module>-0.8.4.jar` |
| G4 docs | `CLAUDE.md` module table, `documentation/module-loader.md` module table, `README` mentions, memory | diff |
| G5 review | scrum master (plus a code-review agent) reviews the diff; findings fixed or logged | this README's status board |
| G6 smoke | user runs the smoke checklist in `documentation/module-loader.md` on a server | user |

## Master checklist (derived from the report's Wave 2, corrected by the pilot)

Per flip the executor works the feature checklist (`<feature>.md` in this folder). The master items below are the
recipe those checklists must instantiate; a planner may reorder inside a flip but may not drop an item.

- [ ] **M1 Poms.** Remove the feature from `gangland-impl/pom.xml`; add `gangland-impl` (provided) plus the
      Keystone modules it uses to the feature pom; turn feature-to-feature deps still in the core into `provided`;
      add the feature to `gangland-build/pom.xml` at provided scope, to the shade `<excludes>` and to the
      `maven-dependency-plugin` copy list. `mvn clean install -DskipTests` then fails **only** in impl; that error
      list is the work list.
- [ ] **M2 Module entry.** `<Feature>Module implements KeystoneModule` + `src/main/resources/module.yml` (Id, Name,
      Version `${project.version}`, Main, `Host_Api: 0.8`, Artifact, `Depends` as the order dictates) +
      `module.properties` for the logger.
- [ ] **M3 Configuration.** Move the feature's `@Bean` methods out of the mixed core configs (`GameplayConfig`,
      `FileConfig`, `SchedulingConfig`, `ItemConfig`, `CopsAndGadgetsConfig`, `ShopConfig`, `BankerConfig`,
      `WiringConfig`, `TurfConfig`, `TurfNpcsConfig`, `DataConfig`) into `<Feature>ModuleConfig` (one or more
      `@Configuration` classes registered by the module). Keep `@Bean` parameters that encode load order.
- [ ] **M4 Contracts.** Move every impl-side `*Contract` implementation (`file/configuration/<feature>/**`,
      `data/**` feature slices) into the module; the interfaces stay where they are as the test seam.
- [ ] **M5 Persistence.** Move `database/repositories/<feature>/**` and `database/tables/<feature>/**` into
      `<module>.database`; the module registers the package via `registrar.repositoryPackage`; the owning manager
      still calls `setDataSupplier(...)`.
- [ ] **M6 Listeners.** Move impl listeners that reference the feature into `<module>.listener.<sub>`; the module
      registers the package. A listener that mixes core and feature concerns is split, not moved whole.
- [ ] **M7 Commands.** Move `command/sub/<feature>/**` into `<module>.command`. Top-level `/glw` commands are
      scanned from `registrar.commandPackage`; sub-arguments under a core command go through a `CommandContribution`
      bean. Cut the entries from core `commands.json` into the module's `commands.json`; fix the count in
      `InformationManagerTest`.
- [ ] **M8 YAML.** Move the feature's defaults from `gangland-impl/src/main/resources/<dir>/` to the module jar at
      the same path; replace core `FileHandler` registrations with module-side ones built with
      `moduleLoader.classLoader()` (the loader comes from `ModuleContext`/the `ModuleLoader` bean).
- [ ] **M9 Seams.** Every remaining core → feature reference gets a core seam interface + module bean
      (`MetricsContributor` for `Gangland.bStats()`, item-refresher registration from the module instead of
      `ItemConfig.itemRefresherRegistry`, sign types, placeholders, …). Seams are generic, named after the core
      concept, and documented in `documentation/module-loader.md`.
- [ ] **M10 Tests.** Move impl tests that reference the feature into the module's `src/test`; add
      `<Feature>ModuleTest` (configure registers the right packages); adjust counts; the core must still boot with
      zero modules (`GanglandContext` tests).
- [ ] **M11 Docs.** `CLAUDE.md` module table + two-tier section, `documentation/module-loader.md` module table and
      any new seam, `documentation/README.md`, memory file `project_module_loader_plan.md`.
- [ ] **M12 Verification.** Gates G1–G4, then `graphify update . --force`.

Obsolete blocker from the 2026-09-04 list: `DebugLoggingInitializer` no longer exists in the tree (nothing to fix).
Still real: `Gangland.bStats()` `WeaponAddon` (`Gangland.java:38,117`), `ItemConfig.itemRefresherRegistry`
(`ItemConfig.java:183-191`), `TurfNpcsConfig` straddling cops + turf, `Settings` feature sections (modules may read
`Settings` directly, so only feature *types* inside `Settings` are a problem).

## Decisions log (scrum master)

- 2026-09-07 · turf Q1: `/glw turf powerupnpc` moves into the **cops** module as a `CommandContribution` with
  parent `turf` (option A); `TurfCommand` queries `CommandContributions` for its path. Cops checklist owns it.
- 2026-09-07 · turf Q2: **all** of `TurfNpcsConfig`, `TurfNpcsConfigLoader`, `TurfPowerupNpc{Repository,Table}` and
  `turf/turf_npcs.yml` are cops-owned and move in flip 1 (no turf-only remainder). Only `turf/turf_powerups.yml` is
  turf's. The brief's "remainder" premise was wrong.
- 2026-09-07 · turf Q3: `turf.task.GangPresenceListener` is NOT renamed in this sprint; the module declares both
  `turf.listener` and `turf.task` as listener packages. Follow-up: move it under `turf.listener`.
- 2026-09-07 · turf Q4: keep concrete repository injection in module configs (registered by `repo.getClass()`,
  same classloader). Switch to the mail `registry.getRepository(...)` pattern only if smoke shows a lookup failure.
- 2026-09-07 · turf Q5: feature-to-feature and core deps in a module pom are `provided`.
- 2026-09-07 · gadget group A: `FuelService` relocates to `gangland-infra/gangland-item` and `WearableAddon` to
  `gangland-weapon` **before** the gadget flip, as their own commit. Wearables (addon, `/glw item wearable` commands,
  converter/refresher/serializer) are weapon's and move in flip 4.
- 2026-09-07 · gadget OQ-1: the gadget-only remainder of `CopsAndGadgetsConfig` is named **`GadgetConfig`** (core,
  `config/GadgetConfig.java`) by flip 1; flip 2 moves its beans into `GadgetModuleConfig` and deletes it.
- 2026-09-07 · gadget OQ-2/OQ-3: CONFIG-phase item-string parsing (`GameplayConfig.lootChestLoader`) and the double
  `SignManager.initialize()` go to the docket via `triage/module-split-config-phase-item-parsing.txt`; the double
  init is fixed in gadget T4 (required by the sign seam), the parse ordering is not changed this sprint.
- 2026-09-07 · shared seams fixed: item registries = module `@Bean` injecting `ItemConverterRegistry` /
  `ItemSerializerRegistry` (new priority overload, `MATERIAL` at `CATCH_ALL_PRIORITY`) / `ItemRefresherRegistry`;
  sign types = `sign/extension/{SignTypeContribution, SignViewProvider, SignContributions}` resolved lazily in
  `SignManager.setupSigns()`. Weapon reuses both.
- 2026-09-07 · seam shapes, two and only two: **contributions** (many providers, core registers no bean of the
  type, consumer pulls `getAllInstances`: `CommandContribution`, `SignTypeContribution`, `SignViewProvider`) and
  **holders** (exactly one core bean with a safe default, the module installs one delegate from `@PostConstruct`:
  `GanglandMoneyDropClassifier`/`NpcMoneyDropSource`, `BankTiers`, `WantedKillTrackers`, `TurfNpcContracts`). Never
  a second bean of an interface the core already publishes — `DependencyContainer.getInstance` returns the first
  registered.
- 2026-09-07 · cops §6.3: with no cops module, `/glw bank` runs with no tier caps, no daily limit, no insurance
  discount and empty tier placeholders (the existing `tier == null` paths). Accepted as the honest "no Banker
  catalogue" state; a settings-driven default tier is a follow-up, not part of the flip.
- 2026-09-07 · cops §6.5: `EntityDamageListener` kill-credit parity is covered by seam unit tests plus the G6 smoke
  items (wanted rises on a cop kill, combo resets on death, bounty pays out); no listener-level pin this sprint.
- 2026-09-07 · cops executors: groups B–H run with the reactor red by design. Gate between them = the impl error
  list from `mvn clean install -DskipTests 2>&1 | grep -E "^\[ERROR\].*\.java"` must shrink and contain only
  files the remaining tasks name; any error outside that list stops the executor.
- 2026-09-07 · weapon C-1 accepted: `ItemRefresherRegistry` gets the same `register(refresher, priority)` +
  `CATCH_ALL_PRIORITY` + stable sort as the serializer registry (weapon T8); weapon/wearable/ammo refreshers at
  priority 10 so a unique weapon keeps refreshing as a weapon. C-2, C-3, R-2, R-3 accepted as written.
- 2026-09-07 · weapon seams accepted: `MetricsContributor`, `DataCleanupTask` (lazy `Supplier`), `NbtTagCatalog`,
  `ShopDisplayNameProvider`, `DeathMessageContributor`; `/glw debug weapon` and `/glw item wearable` via
  `CommandContribution`.
- 2026-09-07 · **deferred**: the five docket candidates in weapon §6 (B-1 loader expected-files incomplete, B-2
  double `setupSigns`, B-3 dead `ViewSignValidator` checks, B-4 unused weapon pom deps, B-5 refresher order) plus
  gadget OQ-2 go into `bug-docket-2026-09-06/triage/` **after** the `p0-wave-3` session lands — it is modifying the
  docket's tracked files in the main tree right now (`git status`: `bugs.json`, html, five triage files).
- 2026-09-07 · docs: `documentation/module-loader.md` wording "YAML under `resources/<module>/`" is wrong and is
  fixed by cops T19 to "at the data-folder path".

- 2026-09-07 · execution finding (cops T12): a `CommandContribution` class is only visible to
  `CommandContributions.from(container)` if a `@Configuration` produces it as a `@Bean` (one bean per concrete
  type, as `MailModuleConfig` does). cops T13 now adds `bankMenuContribution()` and `turfPowerupNpcContribution()`
  to `CopsNCrooksModuleConfig`. Gadget (`carSignContribution`) and weapon (`debug`/`item` contributions, sign
  contributions, `MetricsContributor`, `DataCleanupTask`, `DeathMessageContributor`, `ShopDisplayNameProvider`)
  executors must verify every contribution/seam implementation has a `@Bean` before their G1 gate.

- 2026-09-07 · G5 finding (cops, critical, fixed): `DataConfig.moneyDropClassifier()` declared its return type as the
  `MoneyDropClassifier` interface. `DependencyContainer.registerInstance` files a bean under the declared type plus
  the concrete class's supertypes, never the concrete class, so the module's `@PostConstruct`
  `context.get(GanglandMoneyDropClassifier.class)` returned null and the install hook would NPE at boot with the
  module PRESENT. Rule for every holder seam: the `@Bean` method declares the **concrete holder class** as its
  return type. Pinned by `gangland-impl/src/test/.../config/HolderSeamBeanTypeTest` (red before, green after).
  Gadget and weapon executors: apply the same rule to every seam bean they add. Follow-up for a later sprint: a
  bootstrap test that boots `GanglandContext` with a module's configurations registered would have caught this.
- 2026-09-07 · G1 finding (cops, fixed): `BankTierView` lacked `interestRate()`, `weeklyLoanAmount()`,
  `monthlyLoanAmount()` that `GanglandPlaceholder` reads; added.

- 2026-09-07 · execution finding (gadget D1): `provided` scope is not transitive. A module whose code touches
  `User`/`UserManager` (which implement Keystone's `EconomyOwner`) must declare `keystone-hooks` itself; mail and
  cops already do. Weapon executors: expect the same for `keystone-hooks` (and any other Keystone module the moved
  code imports) when the module first builds on its own.

- 2026-09-08 · execution finding (weapon H): `viaversion-api` is NOT unused — `RecoilCompatibility` references `ViaAPI`; kept at `provided` (docket T-09 corrected). Anvilgui was unused and is gone.
- 2026-09-08 · docket bookkeeping (done at sprint close): T-07 (double `setupSigns`) fixed in `4304172c` (executor set the live
  status); T-08, T-09, T-10 are fixed by the weapon flip; record all four in `triage/new-findings.txt` with commit
  ids and rebuild/republish the docket once flip 4 lands.

## Status board

| Flip | Checklist | Planner | Plan status | Execution status | Review | Commit |
|---|---|---|---|---|---|---|
| 1 cops-n-crooks | `cops-n-crooks.md` | opus | ready (reviewed + patched) | **done** — T1–T20, gates G1–G4 green, `mvn test` green | **done** — code-review agent + scrum master; 1 critical fixed (`moneyDropClassifier` bean type), 1 gap fixed (`BankTierView` accessors) | `04d5171b` on 0.8.4 (146 files) — G6 smoke is the user's |
| 2 gadget | `gadget.md` | opus | ready (reviewed + patched) | **done** — T1–T16, gates G1–G4 green, `mvn test` green | **done** — code-review agent: no findings | group A `0550aaf0`, flip `4304172c` on 0.8.4 (50 files) — G6 smoke is the user's |
| 3 turf | `turf.md` | opus | ready (reviewed + patched) | **done** — T0–T14, gates G1–G4 green, `mvn test` green (turf 87, cops 77 with `Depends: turf`) | **done** — code-review agent: no findings (one javadoc fixed) | `6ffc6d85` on 0.8.4 (45 files) — G6 smoke is the user's |
| 4 weapon | `weapon.md` | opus | ready (reviewed + patched) | **done** — T0–T28, gates G1–G4 green, `mvn test` green (all 42 reactor modules) | **done** — code-review agent: one finding (= T26 `Depends: weapon`, landed) | `0272008b` on 0.8.4 (134 files) — G6 smoke is the user's |
