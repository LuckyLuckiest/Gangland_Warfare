# Runtime modules

Since 0.8.2 Gangland Warfare ships as a **core jar** plus **runtime modules**: plain jars a server owner drops
into `plugins/Gangland_Warfare/modules/`. The core never references a module class, so the server boots with any
module absent. Keystone 1.8.0's `keystone-module` supplies the loader
(`E:\Programming\java\Keystone\docs\keystone-module.md` is the framework-side reference); this page is the
Gangland-side contract.

```
plugins/
├── Keystone-1.8.0.jar
├── Gangland_Warfare-0.8.2.jar          core: impl, core, domain, item, ui/*, version adapters
└── Gangland_Warfare/
    ├── modules/
    │   ├── gangland-mail-0.8.2.jar      the mail module (gang invites, alliance requests)
    │   ├── cops-n-crooks-0.8.4.jar      cops, civilians, jails, detainment, trader/banker NPCs
    │   ├── gangland-gadget-0.8.4.jar    cars and jetpacks
    │   ├── gangland-turf-0.8.4.jar      turf capture, contribution, garrison gameplay
    │   └── .stale/                      replaced jars, deleted on the next start
    └── settings.yml …
```

`mvn clean package` emits both: `target/gangland_warfare-<rev>.jar` and `target/modules/<module>-<rev>.jar`
(`gangland-build` copies each module artifact through `maven-dependency-plugin`).

## What is a module today

| Module | Jar | Status |
|---|---|---|
| mail — `MailManager`, gang invites, alliance requests, join/quit surfacing | `gangland-mail` | runtime module since 0.8.2 (the pilot) |
| cops-n-crooks — cops, civilians, jails, detainment, trader/banker NPCs, turf-NPC powerups | `cops-n-crooks` | runtime module since 0.8.4 |
| gadget — cars (`/glw car`), jetpacks | `gangland-gadget` | runtime module since 0.8.4 |
| turf — `TurfManager`, capture, powerups/garrison, the `/glw turf` tree | `gangland-turf` | runtime module since 0.8.4 |
| weapon | `gangland-weapon` | still a compile-time dependency of `gangland-impl`; next in line |

**Order for the remaining flips.** The feature poms form a DAG (`gadget → weapon`, `cops-n-crooks → weapon + turf`),
so a feature can only be flipped once nothing left in the core's compile closure depends on it. With cops-n-crooks,
gadget and turf already flipped, the remaining incremental order is **weapon** only.
`cops-n-crooks`' `module.yml` now carries `Depends:` with `- turf`; `gadget`'s carries no `Depends:` yet.
`cops-n-crooks` gains `[weapon]` and `gadget` gains `[weapon]` at the weapon flip.

## How the core loads modules

`GanglandContext` owns one `ModuleLoader` (`<dataFolder>/modules`, `Host_Api` = the plugin's major.minor):

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

Modules load once. A changed `modules/` folder — including an update — takes effect on the next start.

## Writing a module

A module is a Maven module under `gangland-features/` that depends on `gangland-impl` at **`provided`** scope
(plus the Keystone modules it uses, also provided). It ships:

- `src/main/resources/module.yml` at the jar root (house YAML style, capitalised underscore keys):

  ```yaml
  Id: mail
  Name: Gangland Mail
  Version: ${project.version}
  Main: org.luckyraven.gangland.mail.MailModule
  Host_Api: 0.8
  Artifact: org.luckyraven:gangland-mail
  ```

  `Depends:` lists other module ids when needed (block-style list). `Artifact` is optional and only feeds the
  update service. Live example: `cops-n-crooks` declares `Depends:` with `- turf` because its turf-NPC views
  consume `turf.data.Turf` and the powerup managers.
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
  into (`items/`, `npc/`, `turf/`, `weapon/`), the module's file sits at that shared path too, not under
  `<module>/` — e.g. the gadget module ships `src/main/resources/items/cars.yml`, resolving to the `items/cars.yml`
  data-folder path the core's `ammunition.yml`/`wearables.yml`/`unique_items.yml` already share, because existing
  servers already look for the file there. A second worked example: the turf module ships
  `src/main/resources/turf/turf_powerups.yml`, registered by a **KERNEL-phase** module configuration
  (`TurfModuleFileConfig`) rather than a method on the module's main CONFIG-phase configuration — the registration
  must run before `FileManager` is used, and `FileManager` is itself a KERNEL bean, so a `@Configuration` at any
  later phase would race the CONFIG-phase code (`PowerupRegistryLoader`) that calls
  `fileManager.checkFileLoaded("turf_powerups")`.

Modules may import core types directly (`Messages`, `Settings`, managers): the compile-time direction is
module → core. Contract interfaces (`MailRepositoryContract`, `TurfMessageContract`, …) stay as the test seam;
their implementations move with the module.

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
`GangAllyCommand` does the same for `gang.ally`. A core command that wants to accept contributions queries its own
path the same way. Register one bean per contribution (distinct concrete types, as `MailModuleConfig` does).

## Core seams

When core code needs something a feature module provides, the module never gets a second bean of a type the core
already publishes — `DependencyContainer.registerInstance` walks the type hierarchy and `getInstance` returns
`list.get(0)`, so two beans of one interface resolve non-deterministically. Instead the sprint uses exactly two
seam shapes:

- **Contributions** — many providers, core registers no bean of the type, the consumer pulls
  `container.getAllInstances(<Type>.class)` (`CommandContribution`, `SignTypeContribution`, `SignViewProvider`).
- **Holders** — exactly one core bean with a safe (inert/no-op) default; the module installs one delegate from a
  single `@PostConstruct` via an `install(...)` method (`GanglandMoneyDropClassifier`/`NpcMoneyDropSource`,
  `BankTiers`/`BankTierView`, `WantedKillTrackers`/`WantedKillTracker`, `TurfNpcContracts`).

Holders introduced by the **cops-n-crooks** flip (0.8.4):

| Holder (core) | Interface installed | Core package | Default when no module | Installed by |
|---|---|---|---|---|
| `GanglandMoneyDropClassifier` | `NpcMoneyDropSource` | `org.luckyraven.gangland.data.economy` | classifies no NPC as a cop/civilian cash drop | `CopsNCrooksModuleConfig`'s `installCoreSeams()` (`CopsMoneyDropSource`) |
| `BankTiers` | `BankTierView` | `org.luckyraven.gangland.data.economy` | `tierFor(...)` returns `null` — no tier cap, no daily deposit limit, no death-penalty insurance discount, empty `%..bank_tier%` placeholders | `CopsNCrooksModuleConfig`'s `installCoreSeams()` |
| `WantedKillTrackers` | `WantedKillTracker` | `org.luckyraven.gangland.gang.wanted` (gangland-domain) | `isActive()` false — kill combo and "counts for wanted" both disabled, `EntityDamageListener` falls back to its pre-combo branches | `CopsNCrooksModuleConfig`'s `installCoreSeams()` (`KillComboWantedTracker`) |
| `TurfNpcContracts` | `TurfNpcContract` | `org.luckyraven.gangland.turf.turfnpcs` (gangland-turf; `turfNpcContracts()` is now registered by `TurfModuleConfig` in the turf module, moved there verbatim by the turf flip) | all four methods no-op — `GarrisonDeployListener` sees an inert contract, garrison deployment silently does nothing | `CopsNCrooksModuleConfig`'s `installCoreSeams()` (`TurfNpcContractImpl`) |

Contribution paths added by the cops-n-crooks flip: `BankMenuContribution` (`parent() == "bank"`, attaches
`/glw bank menu`, queried by `BankCommand`) and `TurfPowerupNpcContribution` (`parent() == "turf"`, attaches
`/glw turf powerupnpc`, queried by `TurfCommand`).

Contributions and seams added by the **gadget** flip (0.8.4):

- **`SignTypeContribution`** (`org.luckyraven.gangland.sign.extension`, gangland-impl) — `List<Sign>
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
  every future item-owning module (weapon included) should reuse. See `GadgetModuleConfig`'s `carConverter`,
  `carItemSerializer`, `carItemRefresher` beans.
- **`ItemSerializerRegistry.CATCH_ALL_PRIORITY`** (`gangland-infra/gangland-item`) — the registry now sorts its
  entries by priority (stable sort, so same-priority registrations keep insertion order); the core's `MATERIAL`
  catch-all registers at `CATCH_ALL_PRIORITY` (`Integer.MIN_VALUE`) so it always sorts last, letting a module's
  default-priority serializer (e.g. `CarItemSerializer`) win even though it registers after the core's beans in
  bootstrap order.

Later flips append their own holders/contributions as new rows in this section rather than starting a new one.

## Faults you will see in the console

`module.descriptor.invalid` (bad `module.yml`), `module.host.incompatible` (built for another core line),
`module.dependency.missing`, `module.cycle`, `module.duplicate`, `module.main.missing` / `module.main.invalid` /
`module.main.instantiation`, `module.configure.failed`. Each names the jar and skips only that module. They reach
the `Diagnostics` hub once it exists; the loader runs before it, so during bootstrap they are logged directly.

## Smoke checklist for a module change

1. Empty `modules/` → the server boots; the module's commands and listeners are absent; nothing else changes.
2. Module jar present → it loads (one `Loaded module <id>` line), its commands answer, its listeners fire, its
   tables exist and autosave writes them.
3. A copy with `Host_Api: 0.7` → skipped with a readable fault, server still boots.
4. `/glw reload` → the module keeps working (no re-scan, no duplicate listeners).
5. Stop → `onDisabled` logged, no classloader errors.
