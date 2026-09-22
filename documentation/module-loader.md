# Runtime modules

Since 0.8.2 Gangland Warfare ships as a **core jar** plus **runtime modules**: plain jars a server owner drops
into `plugins/Gangland_Warfare/modules/`. The core never references a module class, so the server boots with any
module absent. Keystone 1.9.0's `keystone-module` supplies the loader
(`E:\Programming\java\Keystone\docs\keystone-module.md` is the framework-side reference); this page is the
Gangland-side contract.

Since 0.9.0 the weapon system left the repo entirely for the standalone sibling plugin **Bartizan**; Keystone 1.9.0
also gained `keystone-item` (the generic item framework, promoted out of `gangland-item`) and `keystone-npc` (the
shared Citizens-wrapping NPC base). Six runtime modules ship today, not five — see the table below.

```
plugins/
├── Keystone-1.9.2.jar                 + keystone-item, keystone-npc (Citizens soft)
├── Bartizan-0.4.0.jar                  standalone weapons plugin (soft dep of the core; hard "Plugins:" dep of one module)
├── Gangland_Warfare-0.9.2.jar          core: impl, core, domain, item (fuel/unique-item leftovers), ui/* — no NMS
└── Gangland_Warfare/
    ├── modules/
    │   ├── gangland-mail-0.9.2.jar        mail (gang invites, alliance requests) — no Depends, no Plugins
    │   ├── gangland-turf-0.9.2.jar        turf capture + turf-NPC powerups, Depends: [civilians] (loads without Bartizan since 0.9.2)
    │   ├── gangland-civilians-0.9.2.jar   civilian NPCs — Bartizan SOFT since 0.9.2 (WS7 G5b): no Plugins: entry, unarmed hostiles without it
    │   ├── gangland-npc-shops-0.9.2.jar   trader/banker NPC shops — no Depends, no Plugins
    │   ├── cops-n-crooks-0.9.2.jar        cops only, Depends: [turf, civilians]; Plugins: [Bartizan] (the only module still hard)
    │   ├── gangland-gadget-0.9.2.jar      cars, jetpacks and the grappling hook (WS8) — Bartizan SOFT since 0.9.2 (WS7 G5): no Plugins: entry
    │   └── .stale/                        replaced jars, deleted on the next start
    └── settings.yml …
```

**0.9.2 (WS7 gadget wave, gates G5/G5b) soft-coupled two of the three previously-hard `Plugins: [Bartizan]` edges** —
`gangland-civilians` and `gangland-gadget` dropped the descriptor entry entirely and now load fine without Bartizan
(degrading to unarmed civilians / vanilla car-punch damage respectively; the jetpack itself is unaffected, since it
left Bartizan's catalog and became a gadget-owned item in the same wave, gates G2/G3 — see `CLAUDE.md`'s Keystone
section). `cops-n-crooks` is the one holdout, still `Plugins: [Bartizan]`, still hard, since cop NPCs put a
Bartizan-built weapon in their hand and that coupling was explicitly out of this wave's scope. `gangland-turf`
needed zero code changes of its own — its `Depends: [civilians]` was only ever a *transitive* block, and that block
lifted automatically the moment civilians went soft. Full story: `documentation/migration-0.9.2.md`.

`mvn clean package` emits both: `target/gangland_warfare-<rev>.jar` and `target/modules/<module>-<rev>.jar`
(`gangland-build` copies each module artifact through `maven-dependency-plugin`).

## What is a module today

| Module | Jar | Status |
|---|---|---|
| mail — `MailManager`, gang invites, alliance requests, join/quit surfacing | `gangland-mail` | runtime module since 0.8.2 (the pilot) |
| turf — `TurfManager`, capture, powerups/garrison, the `/glw turf` tree, and (since 0.9.0) the turf-NPC (Quartermaster/garrison defender) infrastructure moved out of cops-n-crooks | `gangland-turf` | runtime module since 0.8.4; `Depends: [civilians]` since 0.9.0 — loads without Bartizan since 0.9.2 (transitively, via civilians) |
| civilians — civilian NPCs, `NpcMarkManager`/`CombatEligibility`/target-filter seams cops and turf inject | `gangland-civilians` | **new runtime module, 0.9.0** (split out of cops-n-crooks); Bartizan **soft** since 0.9.2 (WS7 G5b — no `Plugins:` entry; unarmed hostiles without it), `Host_Api: 1.1` |
| npc-shops — trader/banker NPC shops (moved out of cops-n-crooks) | `gangland-npc-shops` | **new runtime module, 0.9.0** — no `Depends:`/`Plugins:` |
| cops-n-crooks — cops, jails, detainment (civilians and turf-NPC powerups moved out in 0.9.0) | `cops-n-crooks` | runtime module since 0.8.4; `Depends: [turf, civilians]`, `Plugins: [Bartizan]` since 0.9.0 — the only module still hard-coupled after 0.9.2 |
| gadget — cars (`/glw car`), jetpacks (`/glw jetpack`, gadget-owned item since 0.9.2, `items/jetpacks.yml`), and (WS8) the grappling hook (`/glw grapple`, cooldown-only, `items/grapples.yml`) | `gangland-gadget` | runtime module since 0.8.4; Bartizan **soft** since 0.9.2 (WS7 G5 — no `Plugins:` entry; vanilla car-punch damage without it), `Host_Api: 1.1` |

Weapons, ammunition, wearables and the projectile system left the repo entirely in 0.9.0 for the standalone
Bartizan plugin — there is no `gangland-weapon` module any more. All six modules above are runtime modules; the
core's feature compile closure is empty and the core names no Bartizan type either. Two independent descriptor keys
gate loading: `Depends:` (another **module**; `module.dependency.missing` when absent) and `Plugins:` (an external
**plugin**; `module.plugin.missing` when absent) — see "Writing a module" below. Since 0.9.2, only `cops-n-crooks`
declares `Plugins: [Bartizan]`; the other five load on a Bartizan-less server (`documentation/migration-0.9.2.md`).

## How the core loads modules

`GanglandContext` owns one `ModuleLoader` (`<dataFolder>/modules`, `Host_Api` = `GanglandApi.VERSION`, today
`1.0`):

1. `bootstrap()` calls `moduleLoader.load()` **before** the configuration scan: descriptors are read, `Host_Api`
   and `Depends` checked, every accepted jar added to one parent-first classloader, each `Main` instantiated and
   asked to `configure(registrar)`. A faulty module is skipped with a fault (`moduleLoader.faults()`); the server
   keeps booting.
2. Every configuration a module registered goes through `BeanFactory.registerConfiguration` next to the core's
   scanned ones, so module `@Bean` methods are topologically sorted with the core's and may inject any core bean.
3. `DatabaseConfig` scans each module's repository packages through the module classloader before the database
   initialises, so module tables join the schema pass. `KernelConfig` merges each module's `commands.json` (read
   from the module's own jar) into the `/glw help` index.
4. After `instantiate()`, the listener and command scans run once for the core and once per module package
   through the module classloader; then `moduleLoader.enableAll(container)` calls `onEnabled`.
5. `Gangland.onDisable()` calls `context.disableModules()` after `shutdownBeans()`.

`Host_Api` is the **module API line**, not the plugin version: bump its minor when the host adds API a module may
rely on, its major only on a breaking change. A module built for `1.x` loads on any host whose API is `1.y` with
`y >= x`; releasing Gangland 0.9.2 or 1.4.0 does not invalidate a single module jar.

Modules load once. A changed `modules/` folder — including an update — takes effect on the next start.

## Writing a module

A module is a Maven module under `gangland-features/` that depends on **`gangland-api`** at **`provided`** scope —
the only host artifact a module compiles against, so the compiler (not a convention) enforces the contract. It pulls
`gangland-core`, `gangland-domain`, `gangland-item` and `sign-api` in transitively, so
that one line replaces them all; add the Keystone modules it uses (also provided) — `keystone-shop` and
`keystone-inventory` are never re-exported by `gangland-api` (0.10.0, WS4/WS2 — `gangland-ui/inventory-api` itself
was deleted outright at the WS2 CUT gate, so there is nothing left to re-export there either), so a module that
needs either declares it directly. A module never depends on
`gangland-impl`: everything in the host jar that is *not* in `gangland-api` is deliberately out of reach. It ships:

- `src/main/resources/module.yml` at the jar root (house YAML style, capitalised underscore keys):

  ```yaml
  Id: mail
  Name: Gangland Mail
  Version: ${project.version}
  Main: org.luckyraven.gangland.mail.MailModule
  Host_Api: 1.0
  Artifact: org.luckyraven:gangland-mail
  ```

  `Host_Api` is the module API line the module was built against (`GanglandApi.VERSION`), *not* the plugin
  version — a module keeps loading across plugin releases as long as the host's API line still satisfies it.

  `Depends:` lists other **module** ids when needed (block-style list); the loader reports
  `module.dependency.missing` and skips the module if one is absent. `Plugins:` (since 0.9.0) lists external
  **plugin** names the module needs at runtime, checked the same way the core's own `softdepend:`/`depend:` are but
  fail-fast per module — the loader reports `module.plugin.missing` and skips the module rather than letting it
  NPE the first time it touches the plugin's API. `Artifact` is optional and only feeds the update service. Live
  examples: `gangland-turf` declares `Depends:` with `- civilians` (its turf-NPC spawns need civilians'
  `NpcMarkManager`/target-filter beans); `cops-n-crooks` declares `Depends:` with `- turf` and `- civilians`
  **and** `Plugins:` with `- Bartizan` (cop NPCs hold a Bartizan-built weapon) — the only module that still does.
  As of 0.9.2 (WS7 G5/G5b), `gangland-civilians` and `gangland-gadget` carry **no `Plugins:` entry at all**: both
  used to declare `Plugins:` with `- Bartizan` alone, and both were rewritten to resolve `Bartizan` softly instead
  (`Settings.isBartizanAvailable()`, api line `1.1`, guarding every Bartizan-typed call site rather than fail-fasting
  the whole module) — see `documentation/TESTING.md`'s `BartizanBlindScan`/`BartizanReferenceScan` section for the
  two tests that keep this true, and `documentation/migration-0.9.2.md` for the server-owner-facing change.
- A `Main` class implementing Keystone's `KeystoneModule`, declaring its configuration classes and listener,
  command and repository packages — see `MailModule`.
- Its `@Configuration` class(es), its `@Repository` classes and `Table`s, its `@ListenerHandler` classes (under
  `<module>.listener`), and any `@CommandHandler` top-level commands (under `<module>.command`).
- Its own `commands.json` at the jar root for the help entries of the commands it adds.
- Its YAML defaults in the module jar **at exactly the data-folder path** (e.g. `npc/cops.yml`), *not* under
  `src/main/resources/<module>/` — the rule that every YAML lives in `gangland-impl` now applies only to shared
  top-level files (`settings.yml`, messages). `FileHandler`'s resource lookup is `directory + fileType` with
  forward slashes, so the module jar must carry the file at that same relative path and it must no longer exist
  in the core jar, or the parent-first `ModuleClassLoader` keeps serving the core's (now missing) copy. Register
  the default with the five-argument
  `FileHandler(plugin, name, directory, ".yml", moduleLoader.classLoader())` so it is copied out of the module jar.
  When the data-folder path is a **pre-existing shared directory** that other modules and the core also drop files
  into (`items/`, `npc/`, `turf/`), the module's file sits at that shared path too, not under
  `<module>/` — e.g. the gadget module ships `src/main/resources/items/cars.yml`, resolving to the `items/cars.yml`
  data-folder path the core's `unique_items.yml` already shares, because existing servers already look for the
  file there (the weapon module's `items/ammunition.yml`/`items/wearables.yml` siblings left the repo with it in
  0.9.0 — those two now live in Bartizan's own data folder, not here). A second worked example: the turf module
  ships
  `src/main/resources/turf/turf_powerups.yml`, registered by a **KERNEL-phase** module configuration
  (`TurfModuleFileConfig`) rather than a method on the module's main CONFIG-phase configuration — the registration
  must run before `FileManager` is used, and `FileManager` is itself a KERNEL bean, so a `@Configuration` at any
  later phase would race the CONFIG-phase code (`PowerupRegistryLoader`) that calls
  `fileManager.checkFileLoaded("turf_powerups")`.

Modules may import any `gangland-api` type directly (`Messages`, `Settings`, `Command`, `Waypoint`,
`BankTiers`, the contribution interfaces …) and any Keystone type directly: the compile-time direction is
module → api. A host manager that lives in `gangland-impl` is *not* importable — where a module genuinely needs
one, the api carries a narrow read-only contract instead (`WaypointLookupContract` over `WaypointManager`), and a
module resolves host beans it cannot name through the injected `DependencyContainer`
(`container.getInstance(X.class)`) rather than through `GanglandContext`. Contract interfaces
(`MailRepositoryContract`, `TurfMessageContract`, …) stay as the test seam; their implementations move with the
module.

### Contract rules

- Within a major `Host_Api` line, `gangland-api` only **adds**: no public member is removed, renamed, or has its
  signature changed. Anything else is a major bump, which invalidates every module jar on the server.
- New module-specific messages and config knobs go in the **module's own YAML**, not in `Messages`/`Settings`.
  Those two live in the api for what already exists — they are not the place to grow a module's vocabulary.
- A module imports Keystone directly; it does not need the host to re-export it.

### Attaching sub-arguments under a core command

A module cannot register a `SubArgument` on a core command by hand, because the core builds its tree without
knowing the module. Instead it provides a `CommandContribution` bean:

```java
public final class GangMailContribution implements CommandContribution {

	@Override
	public String parent() {
		return "gang";                                   // dotted path below /glw; "gang.ally" is also queried
	}

	@Override
	public List<Argument> create(Tree<Argument> tree, Argument parent) {
		GangInviteCommand invite = new GangInviteCommand(gangland, tree, parent, …);
		return List.of(invite, invite.gangAccept());
	}
}
```

`GangCommand` pulls `CommandContributions.from(container)` and appends every contribution addressed to `gang`;
`GangAllyCommand` does the same for `gang.ally`. `BankCommand` does the same for `bank` — the npc-shops module
attaches `/glw bank menu` this way (`BankMenuContribution`). A core command that wants to accept contributions
queries its own path the same way. Register one bean per contribution (distinct concrete types, as
`MailModuleConfig` does).

## Core seams

When core code needs something a feature module provides, the module never gets a second bean of a type the core
already publishes — `DependencyContainer.registerInstance` walks the type hierarchy and `getInstance` returns
`list.get(0)`, so two beans of one interface resolve non-deterministically. Instead the sprint uses exactly two
seam shapes:

- **Contributions** — many providers, core registers no bean of the type, the consumer pulls
  `container.getAllInstances(<Type>.class)` (`CommandContribution`, `SignTypeContribution`, `SignViewProvider`).
- **Holders** — exactly one core bean with a safe (inert/no-op) default; the module installs one delegate from a
  single `@PostConstruct` via an `install(...)` method (`GanglandMoneyDropClassifier`/`NpcMoneyDropSource`,
  `BankTiers`/`BankTierView`, `WantedKillTrackers`/`WantedKillTracker`).

Holders introduced by the **cops-n-crooks** flip (0.8.4); `BankTiers`' installer moved to **npc-shops** in 0.9.0
(T-J3, group J) when traders/bankers split out — the other two stayed in cops-n-crooks:

| Holder (core) | Interface installed | Core package | Default when no module | Installed by |
|---|---|---|---|---|
| `GanglandMoneyDropClassifier` | `NpcMoneyDropSource` | `org.luckyraven.gangland.data.economy` | classifies no NPC as a cop/civilian cash drop | `CopsNCrooksModuleConfig`'s `installCoreSeams()` (`CopsMoneyDropSource`) |
| `BankTiers` | `BankTierView` | `org.luckyraven.gangland.data.economy` | `tierFor(...)` returns `null` — no tier cap, no daily deposit limit, no death-penalty insurance discount, empty `%..bank_tier%` placeholders | `NpcShopsModuleConfig`'s `installBankTiers()` (0.9.0; was `CopsNCrooksModuleConfig`'s `installCoreSeams()` before the npc-shops split) |
| `WantedKillTrackers` | `WantedKillTracker` | `org.luckyraven.gangland.gang.wanted` (gangland-domain) | `isActive()` false — kill combo and "counts for wanted" both disabled, `EntityDamageListener` falls back to its pre-combo branches | `CopsNCrooksModuleConfig`'s `installCoreSeams()` (`KillComboWantedTracker`) |

Contribution paths added by the cops-n-crooks flip, now installed by **npc-shops** (0.9.0 split):
`BankMenuContribution` (`parent() == "bank"`, attaches `/glw bank menu`, queried by `BankCommand`).

Contributions and seams added by the **gadget** flip (0.8.4):

- **`SignTypeContribution`** (`org.luckyraven.gangland.sign.extension`, gangland-api) — `List<Sign>
  signs(String signPrefix)`. `SignManager.setupSigns()` resolves `SignContributions.from(container)` once at the
  top of the method (lazily — a module bean is not guaranteed to exist yet at `SignManager`'s own construction
  time, only once Keystone's convention `initialize()` pass runs) and appends every contributed sign's format and
  definition. Installed by the gadget module's `carSignContribution` bean (`CarSignContribution`, which rebuilds
  the `car-buy`/`car-sell` sign types).
- **`SignViewProvider`** (same package) — `boolean open(Player player, String content)`; `ViewInventoryAspect`
  tries every registered provider in order and falls through to the generic item view when none claims the name.
  Installed by the gadget module's `carSignViewProvider` bean (`CarSignViewProvider`).
- **`SignContributions`** (same package) — the `getAllInstances`-backed holder both interfaces above resolve
  through, modelled on `CommandContributions`. `SignContributions.none()` is the inert default with zero modules
  installed.
- **Item registry injection (no new interface).** A module's `@Bean` methods take the existing
  `ItemConverterRegistry` / `ItemSerializerRegistry` / `ItemRefresherRegistry` beans as constructor parameters and
  call `register(...)` on them directly — the parameter is the `BeanGraph` ordering edge that guarantees the
  module bean runs after the core registry bean exists. No core seam interface is needed; this is the pattern
  every future item-owning module should reuse. See `GadgetModuleConfig`'s `carConverter`, `carItemSerializer`,
  `carItemRefresher` beans, and npc-shops' `TraderModuleConfig` equivalents.
- **`ItemSerializerRegistry.CATCH_ALL_PRIORITY`** (`gangland-infra/gangland-item`) — the registry now sorts its
  entries by priority (stable sort, so same-priority registrations keep insertion order); the core's `MATERIAL`
  catch-all registers at `CATCH_ALL_PRIORITY` (`Integer.MIN_VALUE`) so it always sorts last, letting a module's
  default-priority serializer (e.g. `CarItemSerializer`) win even though it registers after the core's beans in
  bootstrap order.

**`NbtTagCatalog`** (`org.luckyraven.gangland.item`, gangland-impl) — a registry bean (rule-3a pattern, no
interface): `register(String...)` / `tags()`. The core's `ItemConfig.nbtTagCatalog()` bean registers every
`LootChestWandTag`; a module's `@Bean` can take the catalog as a constructor parameter and register its own tags.
Consumed at command-execution time by `ReadNBTCommand` (`/glw debug nbt brief`), order-safe. Introduced alongside
the weapon-flip seams below but is **not** weapon-specific (OQ-4, 0.9.0 review) — it survived the weapon deletion
because it is a generic registry, not a per-feature interface; no module registers tags into it today.

**`ItemVocabulary`/`ItemVocabularies`** (`org.luckyraven.keystone.item.spi`, since Keystone 1.9.0/`keystone-item`)
— the mechanism a weapons-or-items plugin uses to publish its item ids into Gangland's item registries without
either side depending on the other's types. A module (or an external plugin such as Bartizan) registers an
`ItemVocabulary` on Bukkit's `ServicesManager`; once per boot, `GanglandContext.installItemVocabularies()` — run
as the `BeanFactory.instantiate(Runnable)` `beforeLifecycle` hook, so it always fires after every CONFIG-phase
bean exists but strictly before the `@PostConstruct` pass (ahead of `GameplayConfig`'s
`initializeInventoryLoader()`/`initializeLootChestLoader()`, which parse `weapon:`/`ammo:`/`wearable:` item
strings) — reads every registration off the `ServicesManager` and calls `ItemVocabularies.install(vocabularies,
converterRegistry, serializerRegistry, refresherRegistry)` to fold them into the core's own three registries,
logging `"Item vocabularies installed: [...]"` (or `"...: none"` when nothing registered — e.g. no Bartizan; both
log strings are contract, asserted by phase-D smoke rows D1/D6). This is how `weapon:`/`ammo:`/`wearable:` item
references in `loot_chests.yml` and trade signs resolve without the core or any module naming a Bartizan type:
Bartizan is the only vocabulary publisher today, but the mechanism accepts any number of them.

**Historical: seams added by the weapon flip (0.8.4), deleted with the weapon module in 0.9.0.** `MetricsContributor`
(`org.luckyraven.gangland.metrics`, fed `Gangland.bStats()`), `DataCleanupTask`
(`org.luckyraven.gangland.data.plugin`, fed `PluginDataCleanupService`), `ShopDisplayNameProvider`
(`org.luckyraven.gangland.file.configuration.shop`, fed `GanglandShopDisplayResolver` — replaced in 0.9.0 by
`ItemDefinitions.pristine`, no seam needed), `DeathMessageContributor` (`org.luckyraven.gangland.listener.death`,
fed `PlayerDeathListener`; the core now keeps only a generic `Death.Global` fallback with no per-weapon name — see
`documentation/bartizan-integration.md`), the `DebugWeaponContribution`/`ItemWearableContribution` command-
contribution paths (`/glw debug weapon`, `/glw item wearable`, both gone), and the weapon-era
`ItemRefresherRegistry` priority-`10` registration that kept weapon/wearable refreshers ahead of
`UniqueItemRefresher` — all removed along with `WeaponModuleConfig`. None of these interfaces exist in the 0.9.0
tree; do not reintroduce them for a new module without re-reading whether `ItemVocabulary` already covers the need.

Later flips append their own holders/contributions as new rows in this section rather than starting a new one.

## Faults you will see in the console

`module.descriptor.invalid` (bad `module.yml`), `module.host.incompatible` (built for another core line),
`module.dependency.missing` (a `Depends:` module absent), `module.plugin.missing` (a `Plugins:` plugin absent or
disabled, since 0.9.0), `module.cycle`, `module.duplicate`, `module.main.missing` / `module.main.invalid` /
`module.main.instantiation`, `module.configure.failed`. Each names the jar and skips only that module. They reach
the `Diagnostics` hub once it exists; the loader runs before it, so during bootstrap they are logged directly.
Separately, `npc.citizens.missing` (`NpcSupport.FAULT_CITIZENS_MISSING`, `keystone-npc`) is reported once by each
NPC-owning module's own `onEnabled` when Citizens is absent — it does not skip the module (Citizens is soft, not a
`Depends:`/`Plugins:` gate), just its NPC spawns.

## Installing modules from the network

`/glw module` (console-runnable, `gangland.command.module.*`) manages the `modules/` folder without an FTP client:

| Command | What it does |
|---|---|
| `/glw module list` | Every loaded module (id, name, version, `Host_Api`) and then every jar the loader refused, with its fault code — so a module that never came up is visible without the startup log. |
| `/glw module install <module> [version]` | Downloads a module jar. `<module>` is one of the six official ids (`mail`, `turf`, `civilians`, `copsncrooks`, `gadget`, `npcshops`) or a full `group:artifact[:version]`. Without a version the repository's newest is taken. Replacing a module that is already loaded retires the old jar the same way an update does. |
| `/glw module update [module]` | Checks every loaded module's `Artifact` (or only the one named) against the repository and downloads what is newer. |
| `/glw module remove <module>` | Marks a module jar for deletion at the next start. |

`Modules.Repository` in `settings.yml` is the Maven-layout base URL every fetch uses (default Maven Central; a
self-hosted mirror or a `file:///` path works too). A jar is read from
`<Repository>/<group path>/<artifact>/<version>/<artifact>-<version>.jar` and **must publish a `.sha256` beside it** —
a jar without a matching checksum is deleted, never installed. After the download the jar's own `module.yml` is read:
a wrong `Host_Api` deletes it again and reports the required line versus the host's, while unmet `Depends:`/`Plugins:`
entries are reported line by line and the jar is kept.

Nothing takes effect immediately. Modules load once per start, so every install, update and removal ends in a
restart-required line. A removal (and the retired jar of an update) is an empty `<jar>.jar.stale` marker beside the
jar — the running loader holds the jar open, Windows locks it outright, and `ModuleLoader.discover()` deletes both
before anything is loaded on the next start. Deleting the marker undoes the removal.

## Smoke checklist for a module change

1. Empty `modules/` → the server boots; the module's commands and listeners are absent; nothing else changes.
2. Module jar present → it loads (one `Loaded module <id>` line), its commands answer, its listeners fire, its
   tables exist and autosave writes them.
3. A copy with `Host_Api: 0.7` → skipped with a readable fault, server still boots.
4. `/glw reload` → the module keeps working (no re-scan, no duplicate listeners).
5. Stop → `onDisabled` logged, no classloader errors.
