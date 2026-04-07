# Dependency Injection

[Back to Developer Docs](./README.md)

---

## Overview

Gangland Warfare uses a custom lightweight dependency injection container (`DependencyContainer`) that
provides constructor-based injection via reflection. It is not a full IoC framework like Spring or
Guice -- it is purpose-built for wiring event listeners and services in a Spigot plugin environment.

**Module:** `gangland-core`  
**Package:** `me.luckyraven.util.autowire`

---

## Core Components

### DependencyContainer

The central registry that stores instances by type and resolves dependencies via constructor injection.

```
Storage: ConcurrentHashMap<Class<?>, List<Object>>
```

**Registration Methods:**

```java
// Register a single instance by its type
container.registerInstance(UserManager.class, userManager);

// The container also registers the instance under all its superclasses and interfaces
// So if UserManager implements IManager, you can also resolve via IManager.class
```

**Resolution Methods:**

```java
// Get instance by type
UserManager um = container.getInstance(UserManager.class);

// Get all instances of a type (useful for interface lookups)
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

### @Autowired

Annotation that can be placed on constructors to indicate they should be used for injection.
If no `@Autowired` constructor is found, the container uses the constructor with the most
resolvable parameters.

```java
public class MyListener implements Listener {

    private final UserManager userManager;
    private final GangManager gangManager;

    @Autowired
    public MyListener(UserManager userManager, GangManager gangManager) {
        this.userManager = userManager;
        this.gangManager = gangManager;
    }
}
```

### @AutowireTarget

Annotation placed on classes to mark them as DI targets. Used by the listener scanning system
to identify classes that should be auto-instantiated.

```java
@AutowireTarget({UserManager.class, GangManager.class})
public class SomeListener implements Listener {
    public SomeListener(UserManager userManager, GangManager gangManager) {
        // Constructor injection
    }
}
```

The annotation value lists the required types, serving as both documentation and a hint to
the scanning system.

---

## Registration Flow

During `Initializer.postInitialize()`, instances are registered in a specific order to ensure
all dependencies are available before they are needed:

```
Phase 1: Configuration
  ├── FileManager
  ├── Settings
  └── Messages

Phase 2: Database
  ├── GanglandDatabase
  └── RepositoryRegistry

Phase 3: Core Managers
  ├── UserManager
  ├── GangManager
  ├── MemberManager
  ├── RankManager
  ├── PermissionManager
  ├── WaypointManager
  ├── BountyManager
  └── SignManager

Phase 4: Gadget Services
  ├── CarService
  ├── JetpackService
  ├── FuelManager
  └── AmmunitionManager

Phase 5: UI & Data
  ├── LootChestManager
  ├── ScoreboardManager
  └── HologramService

Phase 6: Feature Services
  ├── CopService
  ├── CopSpawnManager
  ├── DetainmentService
  ├── JailManager
  └── CivilianService

Phase 7: Weapon System
  ├── WeaponManager
  └── WeaponService

Phase 8: JavaPlugin (self-registration)
  └── JavaPlugin.class → Gangland instance
```

After all instances are registered, listeners are auto-discovered:

```java
container.registerInstance(JavaPlugin.class, gangland);
listenerManager.scanAndRegisterListeners("me.luckyraven", plugin);
```

---

## Listener Auto-Discovery

### @ListenerHandler Annotation

Classes annotated with `@ListenerHandler` that implement `Listener` are automatically
discovered, instantiated via the DI container, and registered with Bukkit's event system.

```java
@ListenerHandler
@RequiredArgsConstructor
public class PlayerJoinListener implements Listener {

    private final UserManager userManager;
    private final Settings    settings;

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // ...
    }
}
```

### ListenerManager

The `ListenerManager` scans a package for `@ListenerHandler`-annotated classes:

1. Uses `ReflectionUtil.findClasses()` to discover all classes in the package
2. Filters for classes with `@ListenerHandler` annotation
3. For each class, calls `container.createInstance(clazz)` to resolve constructor dependencies
4. Registers the instantiated listener with Bukkit: `Bukkit.getPluginManager().registerEvents(listener, plugin)`

### ListenerPriority

Optional annotation to control the order in which listeners are registered. Higher priority
listeners are registered first.

---

## Constructor Resolution Algorithm

When `createInstance(Class)` is called:

```
1. Get all declared constructors
2. If any has @Autowired → use that one
3. Otherwise, for each constructor:
   a. Check if ALL parameter types can be resolved from the container
   b. Track the constructor with the most resolvable parameters
4. Use the best-matching constructor
5. Resolve each parameter: container.getInstance(paramType)
6. Instantiate via Constructor.newInstance(resolvedParams)
```

If no constructor can be fully resolved, instantiation fails with an exception.

---

## Type Hierarchy Registration

When registering an instance, the container also indexes it under:

- All superclasses (up to `Object`)
- All implemented interfaces

This means if `GanglandDatabase` extends `DatabaseHandler`, registering it as `GanglandDatabase.class`
also makes it resolvable via `DatabaseHandler.class`.

---

## Thread Safety

The internal storage uses `ConcurrentHashMap`, so registration and resolution are thread-safe.
However, the intended usage pattern is:

- **Registration:** single-threaded during plugin startup (main server thread)
- **Resolution:** single-threaded during listener scanning (main server thread)
- **Runtime access:** `getInstance()` is safe from any thread after startup

---

## Best Practices

1. **Register order matters.** Register dependencies before the classes that need them.
2. **Use constructor injection.** Field injection is not supported.
3. **One instance per type.** The container stores a list but typical usage is one-to-one.
4. **Annotate listeners.** Use `@ListenerHandler` + `@RequiredArgsConstructor` (Lombok) for clean DI.
5. **Don't inject Settings/Messages in feature modules.** Use contract interfaces instead
   (see `feedback_settings_contract` memory).
