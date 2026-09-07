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
    │   └── .stale/                      replaced jars, deleted on the next start
    └── settings.yml …
```

`mvn clean package` emits both: `target/gangland_warfare-<rev>.jar` and `target/modules/<module>-<rev>.jar`
(`gangland-build` copies each module artifact through `maven-dependency-plugin`).

## What is a module today

| Module | Jar | Status |
|---|---|---|
| mail — `MailManager`, gang invites, alliance requests, join/quit surfacing | `gangland-mail` | runtime module since 0.8.2 (the pilot) |
| turf, weapon, gadget, cops-n-crooks | `gangland-turf`, `gangland-weapon`, `gangland-gadget`, `cops-n-crooks` | still compile-time dependencies of `gangland-impl`; next in line |

**Order for the remaining flips.** The feature poms form a DAG (`gadget → weapon`, `cops-n-crooks → weapon + turf`),
so a feature can only be flipped once nothing left in the core's compile closure depends on it. Incrementally
that is **cops-n-crooks → gadget → turf → weapon** (or all four in one wave). Flipping turf while cops-n-crooks is
still in the core creates the reactor cycle impl → cops → turf → impl.

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
  update service.
- A `Main` class implementing Keystone's `KeystoneModule`, declaring its configuration classes and listener,
  command and repository packages — see `MailModule`.
- Its `@Configuration` class(es), its `@Repository` classes and `Table`s, its `@ListenerHandler` classes (under
  `<module>.listener`), and any `@CommandHandler` top-level commands (under `<module>.command`).
- Its own `commands.json` at the jar root for the help entries of the commands it adds.
- Its YAML defaults under `src/main/resources/<module>/` — the rule that every YAML lives in `gangland-impl` now
  applies only to shared top-level files (`settings.yml`, messages). Register them with the five-argument
  `FileHandler(plugin, name, directory, ".yml", moduleLoader.classLoader())` so the default is copied out of the
  module jar.

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
