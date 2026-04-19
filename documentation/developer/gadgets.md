# Gadget System

## Overview

The gadget system lives in the `gangland-features/gangland-gadget` module (`me.luckyraven.gadget`) and
provides three major subsystems:

| Subsystem   | Classes | Purpose                                       |
|-------------|---------|-----------------------------------------------|
| **Car**     | 15      | Drivable vehicles with WASD physics           |
| **Jetpack** | 5       | Velocity-based flight with thrust ramp        |
| **Fuel**    | 1       | Shared fuel registry and consumption API      |
| **Other**   | 17      | Listeners (10), config, wearable addon, enums |

**Total: 38 classes** across 3 packages.

### Module Dependencies

```
gangland-gadget
    depends on ──> gangland-item      (Fuel, Wearable data models)
    depends on ──> gangland-weapon    (WeaponService, projectile damage lookup)
    depends on ──> gangland-core      (ItemBuilder, ActionBarManager, ParticleUtil)
    depends on ──> plugin-common      (custom logging)
    depends on ──> plugin-persistence (IRepository for ParkedCar)
```

### Gadget Types

```java
public enum GadgetType {
	CAR,
	WEARABLE,
	JETPACK
}
```

---

## Car System (15 classes)

The car system lets players place, drive, park, pick up, damage, refuel, and destroy vehicles in the
world. Cars are backed by Minecart entities with custom velocity-based movement (no rails required).

### Architecture Diagram

```
                  ╭──────────────────╮
                  │    CarAddon      │  Reads cars.yml, builds Car objects
                  │  (config loader) │
                  ╰────────┬─────────╯
                           │
                           v
                  ╭──────────────────╮
                  │   CarManager     │  Registry: carId ─> Car
                  ╰────────┬─────────╯
                           │
                           v
╭─────────────╮   ╭──────────────────╮   ╭───────────────────╮
│ CarInteract │──>│   CarService     │──>│ VehicleRegistry   │
│  Listener   │   │ (lifecycle API)  │   │ (active sessions) │
╰─────────────╯   ╰────────┬─────────╯   ╰───────────────────╯
                           │
              ╭────────────┼─────────────────╮
              │            │                 │
              v            v                 v
     ╭────────────────╮ ╭──────────────╮ ╭───────────────────╮
     │ MinecartVehicle│ │VehicleSession│ │VehicleMovementTask│
     │ (entity wrap)  │ │              │ │ (tick handler)    │
     ╰────────────────╯ ╰──────────────╯ ╰───────────────────╯
              │                              ^
              v                              │
     ╭──────────────────╮    ╭───────────────────────────╮
     │  ParkedVehicle   │    │ VehicleInputInterceptor   │
     │  (in-world idle) │    │ (Netty packet capture)    │
     ╰──────────────────╯    ╰───────────────────────────╯
              │
              v
     ╭──────────────────╮
     │   ParkedCar      │  DB record (IRepository)
     ╰──────────────────╯
```

### Car (data model)

**File:** `me.luckyraven.gadget.car.Car`

Immutable `@Builder` value object holding a car type's configuration. Built by `CarAddon` from
`cars.yml`.

| Field             | Type     | Description                                         |
|-------------------|----------|-----------------------------------------------------|
| `carId`           | String   | Unique config key (e.g. `"sports_car"`)             |
| `displayName`     | String   | Colored display name for items and HUD              |
| `itemMaterial`    | Material | Bukkit material for the inventory item              |
| `customModelData` | int      | Custom model data for resource packs                |
| `lore`            | List     | Item lore lines                                     |
| `permission`      | String   | Required permission node (nullable)                 |
| `maxSpeed`        | double   | Maximum forward speed (blocks/tick)                 |
| `acceleration`    | double   | Speed gain per tick when W is held                  |
| `deceleration`    | double   | Speed loss per tick when coasting                   |
| `turnSpeed`       | double   | Yaw degrees per tick when A/D is held               |
| `maxHealth`       | double   | Maximum health (unused directly; see maxDurability) |
| `fuelEnabled`     | boolean  | Whether fuel consumption is active                  |
| `fuelKey`         | String   | Reference to a registered Fuel definition           |
| `maxFuel`         | int      | Maximum fuel capacity (ticks)                       |
| `maxDurability`   | int      | Maximum durability (hit points)                     |

**Static helpers:**

- `Car.isCarItem(ItemStack)` -- checks for the `car` NBT tag
- `Car.getCarId(ItemStack)` -- extracts the car config key from an item
- `Car.buildItem()` -- creates an ItemStack with all identifying NBT tags

### CarKey (NBT tags)

```java
public enum CarKey {
	CAR_ID("car"),
	CAR_OWNER("car_owner"),
	CAR_DURABILITY("car_durability"),
	CAR_MAX_DURABILITY("car_max_durability"),
	CAR_EXHAUST_SIDE("car_exhaust_side");
}
```

### CarManager

**File:** `me.luckyraven.gadget.car.CarManager`

Simple `Map<String, Car>` registry. Subclassed by `CarAddon` which loads from YAML.

| Method               | Description                      |
|----------------------|----------------------------------|
| `register(key, car)` | Adds a car to the registry       |
| `getCar(key)`        | Case-insensitive lookup          |
| `getCars()`          | Unmodifiable map of all cars     |
| `clear()`            | Removes all entries (for reload) |

### CarAddon (config loader)

**File:** `me.luckyraven.gadget.car.config.CarAddon`

Extends `CarManager`. Reads `cars.yml` via `FileManager` and constructs `Car` objects.

**YAML structure:**

```yaml
sports_car:
   Material: "MINECART"
   Display_Name: "&6Sports Car"
   Custom_Model_Data: 100
   Permission: "gangland.car.sports"
   Drop_On_Death: false
   Droppable: true
   Lore:
      - "&7A fast sports car"
   Vehicle:
      Max_Speed: 0.8          # blocks/tick
      Acceleration: 0.04      # blocks/tick^2
      Deceleration: 0.02      # blocks/tick^2
      Turn_Speed: 4.0          # degrees/tick
      Max_Health: 100.0
   Fuel:
      Enabled: true
      Fuel_Key: "gasoline"
      Max_Fuel: 6000
   Repair:
      Max_Durability: 500
```

### CarService (core lifecycle API)

**File:** `me.luckyraven.gadget.car.CarService` ── 659 lines

The central API for the car system. Manages the full vehicle lifecycle:

```
Item in inventory ──> placeCar() ──> Parked in world ──> mountCar() ──> Active session
                                          ^                                  │
                                          │                                  │
                                   parkCar() <────────── dismount (shift) ───╯
                                          │
                                   pickupCar() ──> Item back in inventory
                                          │
                                   destroyCar() ──> Entity removed
```

#### Lifecycle Methods

| Method         | Signature                                                                              | Description                                                                                                                                                                                                                                                                         |
|----------------|----------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **placeCar**   | `(Player, String carId, Location, int fuel, int maxFuel, int durability, ExhaustSide)` | Spawns a Minecart entity at the location. Writes PDC tags. Creates a `ParkedVehicle` in memory and a `ParkedCar` DB record. Returns `true` on success.                                                                                                                              |
| **mountCar**   | `(Player, UUID entityUUID)`                                                            | Moves vehicle from parked to active. Creates a `VehicleSession`, registers in `VehicleRegistry`, injects `VehicleInputInterceptor` into the player's Netty pipeline, and starts `VehicleMovementTask`. Handles entity re-spawn if the Minecart died between reload and interaction. |
| **parkCar**    | `(UUID entityUUID)`                                                                    | Ends an active session. Cancels the movement task, removes the Netty handler, saves fuel/durability to PDC and DB, and returns the vehicle to the parked registry.                                                                                                                  |
| **pickupCar**  | `(Player, UUID entityUUID)`                                                            | Removes a parked vehicle from the world and returns the car item (with saved fuel, durability, exhaust side) to the player's inventory. Deletes the DB record.                                                                                                                      |
| **destroyCar** | `(UUID entityUUID, boolean returnItem)`                                                | Destroys an active or parked vehicle. Optionally returns the item to the driver. Handles cleanup of task, Netty handler, registry, entity, and DB record.                                                                                                                           |
| **destroyAll** | `()`                                                                                   | Shutdown hook. Converts all active sessions to parked entries (preserving location and stats for the next server start), then despawns all entities.                                                                                                                                |

#### Persistence Methods

| Method                    | Description                                                                                                                                                        |
|---------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **reloadParkedVehicles**  | Called on plugin enable. Loads all `ParkedCar` records from the DB, force-loads chunks, reclaims surviving Minecart entities (crash recovery), or spawns new ones. |
| **refreshCarDefinitions** | Called after `/glw reload`. Updates the `Car` reference in all parked vehicles and active sessions to reflect config changes.                                      |

#### Fuel and Damage Methods

| Method                         | Description                                                                 |
|--------------------------------|-----------------------------------------------------------------------------|
| **refuelParkedCar(UUID, int)** | Adds fuel to a parked vehicle, capped at max. Updates memory and DB.        |
| **damageParkedCar(UUID, int)** | Applies damage to a parked vehicle. Destroys it if durability reaches zero. |

#### PDC (PersistentDataContainer) Keys

Stored on the Minecart entity for crash recovery:

| NamespacedKey      | Type    | Purpose                          |
|--------------------|---------|----------------------------------|
| `car_id`           | STRING  | Config key                       |
| `car_fuel`         | INTEGER | Current fuel level               |
| `car_fuel_max`     | INTEGER | Maximum fuel capacity            |
| `car_durability`   | INTEGER | Current durability               |
| `car_placer`       | STRING  | UUID of the player who placed it |
| `car_exhaust_side` | STRING  | LEFT, RIGHT, or BOTH             |
| `car_db_id`        | STRING  | Stable DB primary key            |

### VehicleEntity (interface)

**File:** `me.luckyraven.gadget.car.vehicle.entity.VehicleEntity`

Abstraction over the underlying Bukkit entity used to represent a drivable car.

```java
public interface VehicleEntity {
	void spawn(Location location);

	void mount(Player player);

	void despawn();

	void updateMovement(double speed, float yaw);

	void setFacing(float yaw);

	boolean isAlive();

	Location getLocation();

	Entity getBukkitEntity();

	UUID getEntityUUID();

	default void wobble(JavaPlugin plugin) { }
}
```

### MinecartVehicle (entity wrapper)

**File:** `me.luckyraven.gadget.car.vehicle.entity.MinecartVehicle`

Implements `VehicleEntity` using a `Minecart` as the host entity. Key behaviors:

- **Spawn:** `world.spawn(location, Minecart.class)` with `maxSpeed=10.0` (high ceiling so manually
  applied velocity is never internally capped) and `slowWhenEmpty=false`.
- **Movement:** Converts speed + yaw to a direction vector and calls `minecart.setVelocity()` every
  tick. Overrides Minecart's built-in physics drag.
- **Wobble:** Plays a brief side-to-side shake animation on damage using alternating perpendicular
  velocity impulses over 12 ticks with decaying amplitude.
- **Two constructors:** one for spawning fresh, one for wrapping an existing Minecart (used when
  reclaiming survivors after a crash).

**Movement math:**

```java
double radians = Math.toRadians(yaw);
double dx = -Math.sin(radians) * speed;
double dz = Math.cos(radians) * speed;
minecart.

setVelocity(new Vector(dx, minecart.getVelocity().

getY(),dz));
```

Negative speed naturally produces reverse movement along the same yaw axis. The Y component is
preserved from the entity's current velocity so gravity still works.

### VehicleSession (driver state)

**File:** `me.luckyraven.gadget.car.vehicle.VehicleSession` ── 151 lines

Represents an active driving session. Created when a player mounts a car, destroyed on park/destroy.

**State tracked:**

| Field               | Type                | Description                                |
|---------------------|---------------------|--------------------------------------------|
| `entity`            | VehicleEntity       | The underlying Minecart wrapper            |
| `car`               | Car                 | Config definition (mutable for hot-reload) |
| `driver`            | Player              | The player driving the car                 |
| `driverUUID`        | UUID                | Cached for use after disconnection         |
| `currentDurability` | int                 | Health remaining                           |
| `currentFuel`       | int                 | Fuel remaining                             |
| `maxFuel`           | int                 | Maximum fuel capacity                      |
| `exhaustSide`       | ExhaustSide         | LEFT, RIGHT, or BOTH (random if not set)   |
| `healthBar`         | BossBar             | Red boss bar showing durability            |
| `task`              | VehicleMovementTask | The tick handler                           |
| `inputForward`      | volatile boolean    | W key state (from Netty thread)            |
| `inputBackward`     | volatile boolean    | S key state                                |
| `inputLeft`         | volatile boolean    | A key state                                |
| `inputRight`        | volatile boolean    | D key state                                |

**Display:**

- **Health:** Red `BossBar` showing `"[heart] CarName -- current/max"` durability, updated every tick.
- **Fuel:** Action bar rendered by `FuelBar.render()`, sent via `ActionBarManager.sendBackground()`.

**Key methods:**

- `damage(int)` -- reduces durability (floor at 0)
- `consumeFuel(int)` -- reduces fuel (floor at 0)
- `addFuel(int)` -- increases fuel (cap at maxFuel)
- `isDestroyed()` -- `currentDurability <= 0`
- `hasFuel()` -- `currentFuel > 0`
- `setInput(fwd, bwd, left, right)` -- called from Netty IO thread
- `buildReturnItem()` -- creates car ItemStack with current durability/fuel/exhaust NBT

### VehicleMovementTask (tick-based physics)

**File:** `me.luckyraven.gadget.car.vehicle.VehicleMovementTask` ── 195 lines

Extends `BukkitRunnable`, scheduled at `runTaskTimer(plugin, 1L, 1L)` (every tick = 50ms).

#### Physics Diagram

```
                        ╭───────────────────╮
                        │  checkGuards()    │  Entity dead? ─> destroyCar
                        │                   │  Driver offline? ─> parkCar
                        ╰─────────┬─────────╯
                                  │
                        ╭─────────v─────────╮
                        │ Read WASD input   │  From volatile session fields
                        ╰─────────┬─────────╯
                                  │
                    ╭─────────────┼──────────────╮
                    │             │              │
               W && S held   Has fuel?      No fuel
                    │             │              │
               tickBurnout  updateSteering  coastToStop
                    │        updateSpeed         │
                    │             │              │
                    ╰──────┬──────╯──────────────╯
                           │
                  ╭────────v────────╮
                  │  finalizeTick   │  Apply velocity, exhaust particles,
                  │                 │  consume fuel, refresh HUD
                  ╰─────────────────╯
```

#### Steering Logic

```
IF W pressed AND speed ~= 0:
    currentYaw = player's look direction (snap to face direction)
ELSE IF speed > 0 AND (A or D held):
    A -> currentYaw -= turnSpeed
    D -> currentYaw += turnSpeed
ELSE:
    heading preserved (coasting)
```

#### Speed Update Logic

```
IF W (forward):
    speed = min(maxSpeed, speed + acceleration)

IF S (backward):
    IF speed > 0:
        speed = decelerate(speed, deceleration * hardBrakeMultiplier)   // BRAKE first
    ELSE:
        speed = max(-maxSpeed * reverseSpeedRatio, speed - acceleration) // then REVERSE

IF nothing held:
    speed = decelerate(speed, deceleration)                              // coast to stop
```

#### Burnout (W + S simultaneously)

When both forward and backward are held:

1. Speed decelerates at `deceleration * hardBrakeMultiplier` (hard brake).
2. A/D still rotates the heading (drift spin).
3. Tire-smoke particles spawn at the vehicle location.
4. An explosion sound plays at low volume (0.3f) and high pitch (1.5f).
5. Fuel is consumed normally.

#### Deceleration Helper

```java
private double decelerate(double speed, double amount) {
	if (speed > 0) return Math.max(0, speed - amount);
	if (speed < 0) return Math.min(0, speed + amount);
	return 0;
}
```

Moves speed toward zero without overshooting.

### VehicleInputInterceptor (packet-level input)

**File:** `me.luckyraven.gadget.car.vehicle.packet.VehicleInputInterceptor` -- 157 lines

A Netty `ChannelInboundHandlerAdapter` injected into the player's pipeline at the
`"packet_handler"` position under the name `"gangland_vehicle_input"`.

**Purpose:** Intercepts `ServerboundPlayerInputPacket` on the Netty IO thread and writes WASD state
into the `VehicleSession`'s volatile fields. The main-thread `VehicleMovementTask` reads those
fields each tick.

**Thread safety:** All session input fields are `volatile`. Writes happen on the Netty IO thread;
reads happen on the main server thread.

**NMS version support via reflection:**

| Version    | API                                                                                            |
|------------|------------------------------------------------------------------------------------------------|
| 1.21.2+    | `packet.input()` returns an `Input` record with `forward()`, `backward()`, `left()`, `right()` |
| Pre-1.21.2 | Float accessors `xxa()` and `zza()` on the packet directly                                     |

**Pipeline lifecycle:**

- **Inject:** On `mountCar()` --
  `channel.pipeline().addBefore("packet_handler", HANDLER_NAME, new VehicleInputInterceptor(session))`
- **Remove:** On `parkCar()` / `destroyCar()` -- `channel.pipeline().remove(HANDLER_NAME)`

**Channel retrieval:** Uses reflection to walk `CraftPlayer -> ServerPlayer.connection -> Connection.channel`.

### VehicleRegistry

**File:** `me.luckyraven.gadget.car.vehicle.VehicleRegistry`

Dual-index `ConcurrentHashMap` registry tracking all active sessions:

| Index            | Key         | Value          |
|------------------|-------------|----------------|
| `byEntity`       | Entity UUID | VehicleSession |
| `playerToEntity` | Player UUID | Entity UUID    |

| Method                   | Description                        |
|--------------------------|------------------------------------|
| `register(session)`      | Adds to both indexes               |
| `unregister(entityUUID)` | Removes from both                  |
| `getByEntity(UUID)`      | Lookup by Minecart UUID            |
| `getByPlayer(UUID)`      | Lookup by driver UUID              |
| `isPlayerDriving(UUID)`  | Check if a player is in any car    |
| `getAllSessions()`       | All active sessions (for shutdown) |
| `clear()`                | Remove all entries                 |

### ParkedVehicle (in-world idle state)

**File:** `me.luckyraven.gadget.car.vehicle.ParkedVehicle`

In-memory representation of a placed-but-not-driven car. Holds a reference to the live
`VehicleEntity` plus serializable state (fuel, durability, placer UUID, exhaust side).

| Method          | Description                     |
|-----------------|---------------------------------|
| `damage(int)`   | Reduces durability (floor at 0) |
| `addFuel(int)`  | Increases fuel                  |
| `isDestroyed()` | `durability <= 0`               |

### ParkedCar (database record)

**File:** `me.luckyraven.gadget.car.ParkedCar`

Pure data record persisted via `IRepository<ParkedCar>`. Contains only serializable fields -- no
live entity references.

| Field         | Type        | Description                  |
|---------------|-------------|------------------------------|
| `dbId`        | String      | Stable UUID primary key      |
| `carId`       | String      | Config key                   |
| `world`       | String      | World name                   |
| `x/y/z`       | double      | Coordinates                  |
| `yaw`         | float       | Facing direction             |
| `fuel`        | int         | Current fuel (mutable)       |
| `maxFuel`     | int         | Maximum fuel capacity        |
| `durability`  | int         | Current durability (mutable) |
| `placerUUID`  | UUID        | Who placed it (nullable)     |
| `exhaustSide` | ExhaustSide | LEFT/RIGHT/BOTH (nullable)   |

### ExhaustSide

```java
public enum ExhaustSide {
	LEFT,
	RIGHT,
	BOTH;

	public static ExhaustSide random();         // random from all 3 values

	public static ExhaustSide fromString(String); // with random fallback
}
```

Assigned randomly on first mount and persisted in the car item's NBT so it never changes between
sessions. Controls which side(s) exhaust particles spawn from.

### GadgetPhysicsConfig (interface)

**File:** `me.luckyraven.gadget.config.GadgetPhysicsConfig`

Contract interface for physics constants loaded from `settings.yml`. The implementation lives in
`gangland-impl` (never imported directly from feature modules per project convention).

#### Car Constants

| Method                        | Description                                       | Example Value |
|-------------------------------|---------------------------------------------------|---------------|
| `getCarReverseSpeedRatio()`   | Fraction of max speed when reversing              | `0.5`         |
| `getCarHardBrakeMultiplier()` | Multiplier on deceleration for hard brake / S key | `3.0`         |
| `getCarFuelConsumePerTick()`  | Fuel units consumed per tick while moving         | `1`           |

#### Jetpack Constants

| Method                        | Description                                      | Example Value |
|-------------------------------|--------------------------------------------------|---------------|
| `getJetpackThrustRampTicks()` | Ticks to ramp from 10% to 100% ascend power      | `20`          |
| `getJetpackDescentAccel()`    | Downward acceleration per tick (m/tick^2)        | `0.022`       |
| `getJetpackMaxDescentSpeed()` | Terminal descent speed (negative = downward)     | `-0.5`        |
| `getJetpackHorizInfluence()`  | Horizontal speed delta per tick (m/tick)         | `0.03`        |
| `getJetpackMaxHorizSpeed()`   | Maximum horizontal speed while airborne (m/tick) | `0.25`        |

---

## Jetpack System (5 classes)

The jetpack system provides velocity-based flight for players wearing a jetpack wearable in the
chestplate slot. It does NOT use Bukkit's `setFlying(true)` -- all movement is driven by
`player.setVelocity()` each tick. `setAllowFlight(true)` is used solely to suppress Spigot's
anti-cheat kick.

### Architecture Diagram

```
╭─────────────────────╮     ╭──────────────────╮     ╭────────────────────────╮
│ JetpackEquipListener│────>│ JetpackService   │────>│ JetpackSession         │
│ (chestplate events) │     │ (lifecycle API)  │     │ (per-player state)     │
╰─────────────────────╯     ╰────────┬─────────╯     ╰────────────────────────╯
                                     │                          ^
╭────────────────────────╮           │                          │
│JetpackActivateListener │────>      │                          │
│ (join/quit)            │           v                          │
╰────────────────────────╯   ╭──────────────────╮    ╭────────────────────────╮
                             │ JetpackTask      │    │JetpackInputInterceptor │
╭─────────────────────────╮  │ (tick physics)   │───>│ (Netty packet capture) │
│JetpackFallDamageListener│  ╰──────────────────╯    ╰────────────────────────╯
│ (cancel fall damage)    │
╰─────────────────────────╯
```

### JetpackService (lifecycle manager)

**File:** `me.luckyraven.gadget.jetpack.JetpackService` -- 166 lines

| Method                            | Description                                                                                                                 |
|-----------------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| `activate(Player, Wearable)`      | Creates session, starts tick task, sets `allowFlight(true)`, injects Netty handler. No-op if already active.                |
| `deactivate(Player)`              | Cancels task, sets `flying(false)` and `allowFlight(false)`, removes Netty handler.                                         |
| `isActive(Player)`                | Checks if player has an active session.                                                                                     |
| `getSession(Player)`              | Returns the session or null.                                                                                                |
| `scheduleChestplateCheck(Player)` | Next-tick check: if wearing a jetpack wearable, activate; otherwise deactivate. Called by equip/join listeners.             |
| `refreshSessions()`               | Hot-reload: updates wearable references in all sessions. Deactivates sessions for offline players or non-jetpack wearables. |
| `deactivateAll()`                 | Shutdown hook: cancels all tasks, resets flight state, removes all Netty handlers.                                          |

### JetpackSession (per-player state)

**File:** `me.luckyraven.gadget.jetpack.JetpackSession`

| Field             | Type             | Description                                  |
|-------------------|------------------|----------------------------------------------|
| `player`          | Player           | The player using the jetpack                 |
| `jetpackWearable` | Wearable         | The wearable definition (mutable for reload) |
| `task`            | JetpackTask      | The active tick handler                      |
| `thrusting`       | boolean          | Currently ascending                          |
| `gliding`         | boolean          | Currently descending/coasting in air         |
| `glideModeActive` | boolean          | Glide mode toggled on (hover in place)       |
| `inputJump`       | volatile boolean | Space key (from Netty thread)                |
| `inputForward`    | volatile boolean | W key                                        |
| `inputBackward`   | volatile boolean | S key                                        |
| `inputLeft`       | volatile boolean | A key                                        |
| `inputRight`      | volatile boolean | D key                                        |
| `inputSneak`      | volatile boolean | Shift key                                    |

### JetpackTask (tick-based physics)

**File:** `me.luckyraven.gadget.jetpack.JetpackTask` -- 232 lines

Extends `BukkitRunnable`, runs every tick (50ms). All physics constants sourced from
`GadgetPhysicsConfig`.

#### Vertical Physics Diagram

```
                   ╭──────────────────╮
                   │  checkGuards()   │  Offline? ─> deactivate
                   │                  │  Not wearing jetpack? ─> deactivate
                   ╰────────┬─────────╯
                            │
                   ╭────────v─────────╮
                   │ handleGlideToggle│  Sneak+Space (combo) toggles glide mode
                   ╰────────┬─────────╯
                            │
               ╭────────────┼────────────╮
               │            │            │
          Glide mode    Space held    Airborne,
          active        + has fuel    no thrust
               │            │            │
               v            v            v
           newY = 0     ASCEND:       DESCEND:
        (hover/glide)   ramp thrust   apply gravity
                        particles     glide particles
                            │            │
               ╭────────────┴────────────╯
               │
               v
        applyHorizontalPhysics()  (WASD influence while airborne)
               │
               v
        player.setVelocity(newX, newY, newZ)
```

#### Thrust Ramp

Thrust power ramps from 10% to 100% of `ascendPower` over `thrustRampTicks` ticks:

```java
double ramp = Math.min(thrustTicks / (double) physicsConfig.getJetpackThrustRampTicks(), 1.0);
newY =Math.

min(currentY +jetpack.getAscendPower() *(0.1+0.9*ramp),jetpack.

getMaxSpeedY());
```

This creates a smooth liftoff feel -- the player doesn't instantly shoot upward.

#### Descent Physics

When airborne with no thrust:

```java
newY =Math.

max(currentY -physicsConfig.getJetpackDescentAccel(),
                physicsConfig.

getJetpackMaxDescentSpeed());
```

Descent gradually accelerates each tick until hitting the terminal descent speed.

#### Horizontal Physics

While airborne, WASD applies directional influence in the player's look direction:

```java
// Normalize input direction vector
// Apply per-tick influence
newX +=dx *physicsConfig.

getJetpackHorizInfluence();

newZ +=dz *physicsConfig.

getJetpackHorizInfluence();

// Cap horizontal speed
double horizSpeed = Math.sqrt(newX * newX + newZ * newZ);
if(horizSpeed >physicsConfig.

getJetpackMaxHorizSpeed()){
newX =newX /horizSpeed *physicsConfig.

getJetpackMaxHorizSpeed();

newZ =newZ /horizSpeed *physicsConfig.

getJetpackMaxHorizSpeed();
}
```

Vanilla air drag decelerates when no key is held.

#### Glide Mode

Toggled by pressing Sneak + Space simultaneously while airborne:

- Sets vertical velocity to `0.0` (hover/level flight).
- Consumes fuel at the normal rate.
- Deactivates when: landing on ground, pressing Space alone, or fuel runs out.

#### Fuel Efficiency Trait

The `FUEL_EFFICIENT` wearable trait reduces consumption:

```java
int capped = Math.min(fuelEfficientLevel, WearableTrait.FUEL_EFFICIENT.getMaxLevel());
double reduction = capped * WearableTrait.FUEL_EFFICIENT.getEffectPerLevel();
return Math.

max(1,(int)(baseRate *(1.0-reduction)));
```

#### Sound System

Plays flight sounds every 10 ticks:

- **Thrusting:** Plays `thrustDefaultSound` and/or `thrustCustomSound`.
- **Gliding:** Plays `glideDefaultSound` and/or `glideCustomSound`.

### JetpackInputInterceptor (packet capture)

**File:** `me.luckyraven.gadget.jetpack.packet.JetpackInputInterceptor`

Netty handler named `"gangland_jetpack_input"`. Same architecture as `VehicleInputInterceptor` but
captures additional keys:

| Key   | Session Field   | Purpose             |
|-------|-----------------|---------------------|
| Space | `inputJump`     | Thrust (ascend)     |
| W     | `inputForward`  | Forward horizontal  |
| S     | `inputBackward` | Backward horizontal |
| A     | `inputLeft`     | Left strafe         |
| D     | `inputRight`    | Right strafe        |
| Shift | `inputSneak`    | Glide toggle combo  |

Supports the same two NMS APIs as the vehicle interceptor (1.21.2+ `Input` record vs pre-1.21.2
float accessors), plus `jump()` / `jumping()` and `shift()` / `shiftKeyDown()`.

---

## Fuel System

### FuelService (runtime API)

**File:** `me.luckyraven.gadget.fuel.FuelService` -- 261 lines

Central fuel operations API. Manages a registry of `Fuel` definitions and provides methods to
consume, add, and query fuel from items in player inventories.

#### Registry

| Method                         | Description                             |
|--------------------------------|-----------------------------------------|
| `registerFuel(Fuel)`           | Adds a fuel definition (called at init) |
| `getFuel(String fuelKey)`      | Lookup by key                           |
| `findFuelByMaterial(Material)` | Find fuel def matching a material type  |

#### Inventory Fuel Operations

These work with fuel items (e.g. gasoline cans) in the player's inventory:

| Method                                    | Description                                |
|-------------------------------------------|--------------------------------------------|
| `findFuelSlot(Player, String fuelKey)`    | Returns slot index, uses cached hint first |
| `findFuelItem(Player, String fuelKey)`    | Returns the ItemStack                      |
| `hasFuel(Player, String fuelKey)`         | Whether the item has remaining fuel        |
| `getFuelLevel(Player, String fuelKey)`    | Current fuel of the first matching item    |
| `getMaxFuelLevel(Player, String fuelKey)` | Max fuel of the first matching item        |
| `consumeFuel(Player, String, int)`        | Consume fuel, update NBT in-place          |
| `addFuel(Player, String, int)`            | Add fuel, cap at max, update NBT           |

#### Slot Caching

A `Map<UUID, Map<String, Integer>>` caches the last-known inventory slot per player per fuel key.
This avoids a full inventory scan every tick. The cache is cleared on disconnect via
`clearCache(UUID)`.

#### Wearable (Chestplate) Fuel Operations

These read/write fuel directly from the player's equipped chestplate (used by jetpacks):

| Method                                 | Description                            |
|----------------------------------------|----------------------------------------|
| `hasFuelOnWearable(Player)`            | Check chestplate fuel NBT              |
| `consumeFuelFromWearable(Player, int)` | Deduct fuel and update chestplate item |
| `getWearableFuelLevel(Player)`         | Read current fuel from chestplate      |
| `getWearableMaxFuelLevel(Player)`      | Read max fuel from chestplate          |

### Fuel Data Model (gangland-item module)

The `Fuel` class lives in `gangland-item` (`me.luckyraven.item.fuel.Fuel`). It is a `@Builder` data
object plus static NBT helpers.

**FuelKey NBT constants:**

| Key            | Purpose               |
|----------------|-----------------------|
| `FUEL_ID`      | Fuel type identifier  |
| `FUEL_CURRENT` | Current fuel level    |
| `FUEL_MAX`     | Maximum fuel capacity |

**Static helpers on `Fuel`:**

| Method                           | Description                             |
|----------------------------------|-----------------------------------------|
| `isFuelItem(ItemStack)`          | Has the fuel NBT tag?                   |
| `getFuelKey(ItemStack)`          | Read the fuel type ID                   |
| `getCurrentFuel(ItemStack)`      | Read current fuel from NBT              |
| `getMaxFuel(ItemStack)`          | Read max fuel from NBT                  |
| `setCurrentFuel(ItemStack, int)` | Write current fuel, return updated item |

### FuelBar (action bar display)

**File:** `me.luckyraven.item.fuel.FuelBar`

Renders a 20-segment fuel gauge for the action bar:

```
fuel_icon Fuel ||||||||||||||||||| current/max
               ^─── green (filled) + gray (empty)
```

Used by `VehicleMovementTask`, `JetpackTask`, `FuelHoldDisplayListener`, and
`CarEntityInteractListener`.

---


## Wearable System

### WearableAddon (config loader)

**File:** `me.luckyraven.gadget.wearable.WearableAddon`

Extends `WearableService`. Reads `wearables.yml` via `FileManager` and constructs `Wearable`
objects. Each wearable can optionally include a Jetpack section.

**YAML structure (wearables.yml):**

```yaml
jetpack_mk1:
   Material: "LEATHER_CHESTPLATE"
   Name: "&bJetpack MK1"
   Permission: "gangland.wearable.jetpack_mk1"
   Base_Damage_Reduction: 0.15
   Leather_Color: "#3498DB"
   Lore:
      - "&7A basic jetpack"
   Traits:
      fuel_efficient: 2
   Jetpack:
      Fuel_Key: "gasoline"
      Fuel_Consumption_Rate: 2
      Ascend_Power: 0.35
      Glide_Descent_Rate: -0.05
      Max_Speed_Y: 0.8
      Max_Fuel: 3600
      Sound:
         Thrust:
            Default_Sound:
               Sound: "ENTITY_BLAZE_SHOOT"
               Volume: 0.5
               Pitch: 1.5
         Glide:
            Default_Sound:
               Sound: "ENTITY_PHANTOM_FLAP"
               Volume: 0.3
               Pitch: 1.0
```

---

## Listeners (11 total)

### Car Listeners

| Listener                      | Events Handled                                                                             | Purpose                                                                                                                                                             |
|-------------------------------|--------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **CarInteractListener**       | `PlayerInteractEvent` (RIGHT_CLICK_BLOCK)                                                  | Right-click car item on block to place vehicle. Reads fuel/durability/exhaust from NBT. Consumes item.                                                              |
| **CarEntityInteractListener** | `PlayerInteractEntityEvent`                                                                | Right-click parked entity to mount (or refuel with fuel can). Shift+right-click reserved.                                                                           |
| **CarDamageListener**         | `VehicleDamageEvent`, `ProjectileHitEvent`, `EntityDamageEvent`, `WeaponEntityDamageEvent` | Routes all damage sources to car durability. Melee uses weapon damage if held. Shift+left-click picks up parked car. Explosions check for throwable grenade damage. |
| **CarDismountListener**       | `EntityDismountEvent`                                                                      | Parks the car on dismount. Cancels accidental ejects (non-shift, non-death). Teleports player to safe exit location.                                                |

### Jetpack Listeners

| Listener                      | Events Handled                                                          | Purpose                                                                                                |
|-------------------------------|-------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| **JetpackEquipListener**      | `InventoryClickEvent`, `PlayerInteractEvent`, `PlayerToggleFlightEvent` | Schedules chestplate check on equip/unequip. Cancels `toggleFlight` events for active jetpack players. |
| **JetpackActivateListener**   | `PlayerJoinEvent`, `PlayerQuitEvent`                                    | Auto-activates jetpack on join (if wearing one). Deactivates on quit.                                  |
| **JetpackFallDamageListener** | `EntityDamageEvent` (FALL cause)                                        | Cancels fall damage for players with active jetpack sessions.                                          |

### Fuel Listeners

| Listener                    | Events Handled                                                                           | Purpose                                                                                                                 |
|-----------------------------|------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------|
| **FuelRefuelListener**      | `PlayerInteractEvent`, `InventoryClickEvent`                                             | Handles refueling: right-click with material item, inventory cursor interactions, fuel can to wearable transfer.        |
| **FuelHoldDisplayListener** | `PlayerItemHeldEvent`, `EntityPickupItemEvent`, `PlayerDropItemEvent`, `PlayerQuitEvent` | Shows fuel bar on action bar while holding a fuel item. Runs a repeating display task. Clears slot cache on disconnect. |

### Wearable Listener

| Listener                  | Events Handled        | Purpose                                                                                   |
|---------------------------|-----------------------|-------------------------------------------------------------------------------------------|
| **WearableEquipListener** | `InventoryClickEvent` | Blocks equipping wearables the player lacks permission for (drag to slot or shift-click). |

---

## Cross-Module Integration Points

### gangland-gadget -> gangland-item

- `Fuel` -- NBT read/write helpers, `FuelBar` renderer, `FuelKey` constants
- `Wearable` -- jetpack wearable data model, `WearableTrait` enum

### gangland-gadget -> gangland-weapon

- `WeaponService` -- checks if an item is a weapon (for melee damage in `CarDamageListener`)
- `ProjectileDamageListener.getDamageForProjectile()` -- reads weapon projectile damage
- `ThrowableAction.pendingVehicleExplosionDamage` -- grenade explosion damage for vehicles
- `WeaponEntityDamageEvent` -- custom damage event for incendiary/biological weapons

### gangland-gadget -> gangland-core

- `ItemBuilder` -- NBT tag manipulation
- `ActionBarManager` -- action bar display
- `ChatUtil` -- color code formatting
- `ParticleUtil` -- exhaust, burnout, jetpack flame/glide, explosion particles
- `PlayerUtil.isOnGround()` -- ground detection for jetpack
- `SoundConfiguration` -- vanilla/custom sound playback

### gangland-gadget -> plugin-persistence

- `IRepository<ParkedCar>` -- CRUD for parked vehicle database records
- `FileManager` / `FileHandler` -- YAML config file loading

### gangland-gadget -> gangland-impl (contract interfaces)

- `GadgetPhysicsConfig` -- physics constants from `settings.yml`

These follow the project convention: feature modules define the interface, `gangland-impl` provides
the implementation. Feature modules never import `Settings` or `Messages` directly.
