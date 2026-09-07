# Beans System & Dependency Injection

[Back to Developer Docs](./README.md)

---

## Overview

Gangland Warfare uses a Spring-style bean framework to manage the entire plugin lifecycle. All managers, services, and
infrastructure objects are produced by `@Bean` factory methods inside `@Configuration` classes, topologically sorted by
their dependencies, and registered as singletons in a shared `DependencyContainer`.

This system replaced the old `Initializer.java` hand-coded wiring. Instead of manually constructing and ordering every
manager, the framework discovers, resolves, and invokes bean methods automatically.

**Module:** `gangland-core`
**Package:** `org.luckyraven.gangland.util.autowire` (container) and `org.luckyraven.gangland.util.autowire.bean` (bean
framework)

---

## Core Components

### DependencyContainer

The central registry that stores singleton instances by type and resolves dependencies via constructor injection.

```
Storage: ConcurrentHashMap<Class<?>, List<Object>>
```

**Registration Methods:**

```java
// Register by type (also registers under all superclasses and interfaces)
container.registerInstance(UserManager .class, userManager);

// Register by name + type (for @Qualifier disambiguation)
container.

registerInstance("online",UserManager .class, userManager);
```

**Resolution Methods:**

```java
// Get instance by type
UserManager um = container.getInstance(UserManager.class);

// Get named instance
UserManager online = container.getInstance("online", UserManager.class);

// Get all instances of a type
List<Object> all = container.getAllInstances(IManager.class);

// Check if a type is registered
boolean exists = container.hasInstance(UserManager.class);
```

**Instantiation Methods:**

```java
// Create a new instance with automatic constructor injection
SomeListener listener = container.createInstance(SomeListener.class);
```

The `createInstance` method:

1. Finds the best matching constructor via `findBestConstructor()`
2. Resolves each constructor parameter from the container via `resolveConstructorParameters()`
3. Instantiates the class via reflection

### GanglandContext

The single root for the entire plugin's wiring. Owns the only `DependencyContainer` and `BeanFactory` that exist at
runtime.

```java
// In Gangland.onEnable():
this.context =new

GanglandContext(this);
context.

bootstrap();
```

**Key methods:**

| Method             | Purpose                                                                 |
|--------------------|-------------------------------------------------------------------------|
| `bootstrap()`      | Scan configs, run all phases, register listeners and commands           |
| `get(Class<T>)`    | Convenience accessor to pull a bean by type from the container          |
| `reloadBeans()`    | Run the reload lifecycle on all `BeanLifecycle` beans                   |
| `shutdownBeans()`  | Run graceful shutdown on all `BeanLifecycle` beans (reverse topo order) |
| `getContainer()`   | Direct access to the `DependencyContainer` for qualified lookups        |
| `getBeanFactory()` | Direct access to the `BeanFactory`                                      |

---

## Annotations Reference

| Annotation              | Target           | Purpose                                                               |
|-------------------------|------------------|-----------------------------------------------------------------------|
| `@Configuration`        | Class            | Marks a class as a bean factory; assigns a `Phase`                    |
| `@Bean`                 | Method           | Marks a factory method; parameters injected, result registered        |
| `@Qualifier`            | Parameter/Method | Disambiguates beans by name when multiple candidates of same type     |
| `@PostConstruct`        | Method           | No-arg method invoked after all beans are wired                       |
| `@ConditionalOnSetting` | Class/Method     | Skip bean if a settings flag resolves to `false`                      |
| `@ConditionalOnBean`    | Class/Method     | Skip bean if required types are not in the container                  |
| `@Autowired`            | Constructor      | Hints which constructor to use for DI (optional; best-match fallback) |

---

## Bootstrap Phases

`BeanFactory.instantiate()` processes phases in this fixed order. Each phase has its own dependency subgraph;
topological sort runs **per phase**, so cross-phase ordering is enforced by the pipeline itself.

### Phase 1: KERNEL

Re-publishes the constructor-built kernel objects (`Gangland`, `FileManager`, `PermissionManager`,
`CompatibilitySetup`, `PlaceholderService`, `DatabaseManager`, `ScoreboardManager`) so downstream phases can
constructor-inject them by type.

**Configuration class:** `KernelConfig`

### Phase 2: FILE

Produces `FileInitializer` implementations (Settings, LanguageLoader, weapon addons, item addons, etc.).

**Phase hook:** After every bean in this phase is registered, `FileManager.initializeAll()` is called so the YAML file
is loaded before the next FILE-phase bean tries to read it. This staged loading ensures that e.g. `Settings` is fully
loaded before `ScoreboardAddon` reads `Settings.getX()` at construction time.

**Configuration class:** `FileConfig`

### Phase 3: DATABASE

Produces `GanglandDatabase` and `RepositoryRegistry`.

**Phase hook:** After each bean, walks `RepositoryRegistry.getAllRepositories()` and publishes every `IRepository` into
the container by its concrete class. This means later `@Bean` parameters of type `UserRepository` (or any concrete
repo) resolve automatically.

**Configuration class:** `DatabaseConfig`

### Phase 4: CONFIG

The bulk manager phase. Produces all domain managers and services: `UserManager`, `GangManager`, `MemberManager`,
`RankManager`, `WaypointManager`, `WeaponManager`, `SignManager`, `CarService`, `CopService`, `LootChestManager`,
`PeriodicalUpdates`, `ListenerManager`, `CommandManager`, and many more.

Beans implementing `BeanLifecycle` have their `onInitialize(true)` called immediately after registration (in topo
order), so each bean is populated with data before the next bean in the order is constructed.

**Configuration classes:** `DataConfig`, `GameplayConfig`, `SchedulingConfig`, `WiringConfig`,
`CopsAndGadgetsConfig`

### Phase 5: LIFECYCLE

Synthetic phase. After all bean phases complete:

1. `@PostConstruct` methods run on all configuration instances and beans
2. Convention-based `initialize()` methods are invoked on beans that have a zero-arg `void initialize()` method
   (skipping `FileInitializer` beans and `BeanLifecycle` beans, which are handled separately)

### Phase 6: LISTENER

`GanglandContext` pulls `ListenerManager` from the container and calls `scanAndRegisterListeners("org.luckyraven.gangland",
plugin)`. This:

1. Discovers all `@ListenerHandler`-annotated classes via `ReflectionUtil.findClasses()`
2. Instantiates each via `DependencyContainer.createInstance()` (constructor injection)
3. Registers each with Bukkit's event system

### Phase 7: COMMAND

Same shape as LISTENER. `GanglandContext` pulls `CommandManager` from the container and calls
`scanAndRegisterCommands("org.luckyraven.gangland.command.sub", classLoader)`. This:

1. Discovers all `Command` subclasses annotated with `@CommandHandler`
2. Instantiates each via constructor injection
3. Binds the executor and tab completer to the `/glw` `PluginCommand`

---

## BeanLifecycle Interface

Beans that implement `BeanLifecycle` participate in managed reload and shutdown pipelines. Beans that do **not**
implement it are treated as simple singletons with no managed lifecycle.

```java
public interface BeanLifecycle {
	default void onPreClear() { }

	default void onClear() { }

	default void onInitialize(boolean firstLoad) { }

	default void onShutdown() { }
}
```

### Startup Sequence

1. Bean constructed and registered into container
2. `onInitialize(true)` called in **forward** topological order (dependencies first)
3. `@PostConstruct` runs after all beans are wired

### Reload Sequence (`context.reloadBeans()`)

| Step | Method                | Order                                        | Purpose                                          |
|------|-----------------------|----------------------------------------------|--------------------------------------------------|
| 1    | `onPreClear()`        | **Reverse** topological (dependents first)   | Stop timers, cancel async tasks, end scoreboards |
| 2    | `onClear()`           | **Reverse** topological                      | Wipe maps, reset state to post-construction      |
| 3    | `onInitialize(false)` | **Forward** topological (dependencies first) | Re-populate from database/config files           |

### Shutdown Sequence (`context.shutdownBeans()`)

| Step | Method         | Order                                      | Purpose                                     |
|------|----------------|--------------------------------------------|---------------------------------------------|
| 1    | `onShutdown()` | **Reverse** topological (dependents first) | Despawn entities, flush state, cancel tasks |

**Important:** `onShutdown()` is only called during plugin disable, not during reload. Use `onPreClear()` for
reload-time teardown.

### Example

```java
public class CarService implements BeanLifecycle {

	@Override
	public void onInitialize(boolean firstLoad) {
		// Load parked cars from database, spawn entities
	}

	@Override
	public void onPreClear() {
		// Stop all active car movement timers
	}

	@Override
	public void onClear() {
		// Clear the active cars map
	}

	@Override
	public void onShutdown() {
		// Convert active cars to parked records, despawn entities
	}
}
```

---

## How to Add a New Bean

### Step 1: Choose the right Configuration class and Phase

| What you're adding               | Configuration class   | Phase    |
|----------------------------------|-----------------------|----------|
| Bootstrap-critical singleton     | `KernelConfig`        | KERNEL   |
| YAML file initializer            | `FileConfig`          | FILE     |
| Database/repository              | `DatabaseConfig`      | DATABASE |
| Domain manager or service        | Existing CONFIG class | CONFIG   |
| New feature area with many beans | New `@Configuration`  | CONFIG   |

### Step 2: Write the @Bean method

Add a method to the appropriate `@Configuration` class. Declare dependencies as parameters.

```java

@Configuration
public class GameplayConfig {

	@Bean
	public WaypointManager waypointManager(WeaponManager weaponManager,
	                                       GanglandDatabase database) {
		return new WaypointManager(weaponManager, database);
	}
}
```

### Step 3: Implement BeanLifecycle (if needed)

If your bean holds mutable state that needs reload/shutdown management:

```java
public class WaypointManager implements BeanLifecycle {

	@Override
	public void onInitialize(boolean firstLoad) {
		// Load waypoints from config
	}

	@Override
	public void onClear() {
		// Clear cached waypoints
	}

	@Override
	public void onShutdown() {
		// Cancel any active waypoint timers
	}
}
```

### Step 4: Inject it elsewhere

Other `@Bean` methods, listeners, and commands can now receive it as a constructor parameter:

```java

@ListenerHandler
public class WaypointListener implements Listener {
	public WaypointListener(WaypointManager waypointManager) {
		this.waypointManager = waypointManager;
	}
}
```

---

## How to Add a New Configuration Class

Create a new `@Configuration` class when you have a distinct feature area with multiple beans. Each configuration
belongs to exactly one `Phase` -- there is no per-bean phase override. To produce beans for a different phase, write
a separate configuration class.

```java

@CustomLog
@Configuration(phase = Phase.CONFIG)
public class MyFeatureConfig {

	private final Gangland gangland;

	public MyFeatureConfig(Gangland gangland) {
		this.gangland = gangland;
	}

	@Bean
	public MyService myService(GanglandDatabase database) {
		return new MyService(gangland, database);
	}

	@Bean
	public MyOtherService myOtherService(MyService myService) {
		return new MyOtherService(myService);
	}
}
```

**Rules:**

- Place in `org.luckyraven.gangland.config` package (the package scanned by `GanglandContext`)
- Annotate with `@Configuration` and specify the `phase`
- Constructor parameters are injected from the container (beans from earlier phases)
- Default phase is `CONFIG` if not specified

---

## Using @Qualifier for Generic Beans

When multiple beans share the same raw type (e.g. `UserManager<Player>` and `UserManager<OfflinePlayer>`), Java's
type erasure means the container sees two `UserManager` instances. Use `@Qualifier` to disambiguate.

### Producing named beans

```java

@Bean(name = "online", isGeneric = true)
public UserManager<Player> userManager(GanglandDatabase database,
                                       MemberManager memberManager) {
	return new UserManager<>(gangland, database, memberManager);
}

@Bean(name = "offline", isGeneric = true)
public UserManager<OfflinePlayer> offlineUserManager(GanglandDatabase database,
                                                     MemberManager memberManager) {
	return new UserManager<>(gangland, database, memberManager);
}
```

### Consuming named beans

```java

@Bean
public GangManager gangManager(@Qualifier("online") UserManager<Player> onlineUsers) {
	return new GangManager(onlineUsers);
}
```

```java

@ListenerHandler
public class CreateAccountListener implements Listener {
	public CreateAccountListener(@Qualifier("online") UserManager<Player> userManager,
	                             @Qualifier("offline") UserManager<OfflinePlayer> offlineUserManager) {
		// ...
	}
}
```

The `isGeneric = true` flag on `@Bean` documents that raw-class injection would be ambiguous. Consumers **must** use
`@Qualifier`.

---

## Conditional Beans

### @ConditionalOnSetting

Skip a bean or entire configuration class based on a settings flag:

```java

@Configuration
@ConditionalOnSetting("cops.enabled")
public class CopsConfig {
	// All beans in this class are skipped if cops.enabled is false
}
```

Or on individual methods:

```java

@Bean
@ConditionalOnSetting("gadgets.jetpack.enabled")
public JetpackService jetpackService() {
	return new JetpackService();
}
```

The value is a dotted-path key resolved via `SettingsLookup.isEnabled(key)`. Unknown keys return `false` (fail
closed).

### @ConditionalOnBean

Skip a bean if required types are not in the container:

```java

@Bean
@ConditionalOnBean({CopService.class, JailService.class})
public DetainmentService detainmentService(CopService cops, JailService jails) {
	return new DetainmentService(cops, jails);
}
```

Class-level conditions are evaluated before any bean methods on the class run. Method-level conditions are evaluated
after class-level conditions pass.

---

## Dependency Resolution & Ordering

### How BeanGraph resolves dependencies

Within each phase, `BeanGraph` builds a directed acyclic graph (DAG) from `@Bean` method parameters:

1. Each bean's parameters create incoming edges from the bean that produces that type
2. Parameters already in the container (from earlier phases) don't create graph edges
3. `@Qualifier` names narrow the lookup to a specific bean

### Topological sort (Kahn's algorithm)

1. Start with beans that have no unsatisfied incoming edges
2. Emit them in order, decrementing edge counts on dependents
3. Repeat until all beans are emitted

### Cycle detection (fail-fast)

If any beans remain after the sort completes, a DFS traces the cycle path and throws `BeanCycleException` with a
human-readable message:

```
BeanCycleException: Circular dependency detected: GangManager -> UserManager -> GangManager
```

This aborts startup immediately so the wiring bug is visible.

---

## Listener Auto-Discovery

### @ListenerHandler Annotation

Classes annotated with `@ListenerHandler` that implement `Listener` are automatically discovered, instantiated via
the DI container, and registered with Bukkit's event system.

```java

@ListenerHandler
public final class PlayerJoinListener implements Listener {

	private final UserManager<Player> userManager;
	private final GangManager         gangManager;

	public PlayerJoinListener(@Qualifier("online") UserManager<Player> userManager,
	                          GangManager gangManager) {
		this.userManager = userManager;
		this.gangManager = gangManager;
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		// ...
	}
}
```

### ListenerManager

The `ListenerManager` (produced as a CONFIG-phase bean) scans a package for `@ListenerHandler` classes:

1. Uses `ReflectionUtil.findClasses()` to discover all classes in the package
2. Filters for classes with `@ListenerHandler` annotation
3. For each class, calls `container.createInstance(clazz)` to resolve constructor dependencies
4. Registers the instantiated listener with Bukkit: `Bukkit.getPluginManager().registerEvents(listener, plugin)`

### ListenerPriority

Optional attribute on `@ListenerHandler` to control the order in which listeners are instantiated and registered.
Higher priority listeners are registered first.

---

## Command Auto-Discovery

### @CommandHandler Annotation

Command classes annotated with `@CommandHandler` that extend `Command` are automatically discovered and registered.

```java

@CommandHandler
public final class BalanceCommand extends Command {

	private final UserManager<Player> userManager;
	private final GanglandDatabase    database;

	public BalanceCommand(Gangland gangland,
	                      @Qualifier("online") UserManager<Player> userManager,
	                      GanglandDatabase database) {
		super(gangland, "balance", "bal");
		this.userManager = userManager;
		this.database    = database;
	}
}
```

### CommandManager

The `CommandManager` (produced as a CONFIG-phase bean) scans `org.luckyraven.gangland.command.sub` for `@CommandHandler`
classes:

1. Discovers all `Command` subclasses
2. Instantiates each via `container.createInstance(clazz)`
3. Registers them as sub-commands of `/glw`

---

## Constructor Resolution Algorithm

When `DependencyContainer.createInstance(Class)` is called (for listeners, commands, or configuration classes):

```
1. Get all declared constructors
2. If any has @Autowired -> use that one
3. Otherwise, for each constructor:
   a. Check if ALL parameter types can be resolved from the container
   b. Track the constructor with the most resolvable parameters
4. Use the best-matching constructor
5. Resolve each parameter: container.getInstance(paramType)
   - If @Qualifier present on parameter: container.getInstance(name, paramType)
6. Instantiate via Constructor.newInstance(resolvedParams)
```

If no constructor can be fully resolved, instantiation fails with an exception.

---

## Type Hierarchy Registration

When registering an instance, the container also indexes it under:

- All superclasses (up to `Object`)
- All implemented interfaces

This means if `GanglandDatabase` extends `DatabaseHandler`, registering it as `GanglandDatabase.class` also makes it
resolvable via `DatabaseHandler.class`.

---

## Thread Safety

The internal storage uses `ConcurrentHashMap`, so registration and resolution are thread-safe. However, the intended
usage pattern is:

- **Registration:** single-threaded during plugin startup (main server thread)
- **Resolution:** single-threaded during listener/command scanning (main server thread)
- **Runtime access:** `getInstance()` is safe from any thread after startup

---

## Common Pitfalls

### 1. No CGLIB proxying -- never call `this.foo()` between @Bean methods

The framework does **not** use CGLIB proxies. Calling another `@Bean` method via `this.foo()` creates a **second
instance**, bypassing the singleton guarantee.

```java
// WRONG -- creates a second UserManager
@Bean
public GangManager gangManager() {
	return new GangManager(this.userManager());
}

// CORRECT -- framework injects the cached singleton
@Bean
public GangManager gangManager(UserManager userManager) {
	return new GangManager(userManager);
}
```

### 2. Missing @Qualifier for generic beans

When multiple beans share a raw type, omitting `@Qualifier` causes an ambiguous resolution error at startup:

```
IllegalStateException: Ambiguous bean for parameter 'userManager' of type UserManager
for bean DataConfig.gangManager(): 2 candidates registered. Add @Qualifier to disambiguate.
```

### 3. Cross-phase dependency assumptions

A CONFIG-phase bean can inject a FILE-phase or DATABASE-phase bean (those phases have already run). But a FILE-phase
bean cannot inject a CONFIG-phase bean -- it doesn't exist yet. If you get a resolution error, check that your bean's
phase runs **after** the phase that produces its dependency.

### 4. @Bean methods must not return null

Returning `null` from a `@Bean` method throws immediately:

```
IllegalStateException: Bean FileConfig.settings() returned null -- bean methods must produce a value.
```

### 5. @PostConstruct methods must take zero parameters

```
IllegalStateException: @PostConstruct method DataConfig.registerPermissions() must take zero parameters.
```

### 6. FileInitializer beans are skipped by convention-based initialize()

Beans implementing `FileInitializer` have their `initialize()` method driven by `FileManager.initializeAll()` in the
FILE phase hook. They are intentionally skipped by the LIFECYCLE phase's convention-based `initialize()` scan to
avoid double-loading YAML files.

---

## Best Practices

1. **Declare dependencies as parameters.** Never call `container.getInstance()` inside a `@Bean` method -- let the
   framework resolve for you.
2. **One phase per configuration class.** Each `@Configuration` class belongs to one `Phase`. Split across files if
   you need beans in different phases.
3. **Use constructor injection everywhere.** Field injection is not supported. Listeners, commands, and configuration
   classes all receive dependencies as constructor parameters.
4. **Implement `BeanLifecycle` for stateful beans.** If your bean holds maps, caches, or timers that need clearing on
   reload or cleanup on shutdown.
5. **Use `@Qualifier` when producing generic beans.** Flag them with `isGeneric = true` so it's clear consumers must
   qualify.
6. **Don't import Settings/Messages in feature modules.** Use contract interfaces implemented in `gangland-impl`
   instead.
7. **Use `@ConditionalOnSetting` for optional features.** This lets the bean framework skip entire feature trees
   cleanly.

---

*See [Architecture Overview](./architecture.md) for the full plugin lifecycle and module dependency graph.*
