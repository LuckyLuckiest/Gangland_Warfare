package me.luckyraven.gadget.car;

import io.netty.channel.Channel;
import lombok.Getter;
import me.luckyraven.core.ItemBuilder;
import me.luckyraven.core.bean.BeanLifecycle;
import me.luckyraven.gadget.car.vehicle.ParkedVehicle;
import me.luckyraven.gadget.car.vehicle.VehicleMovementTask;
import me.luckyraven.gadget.car.vehicle.VehicleRegistry;
import me.luckyraven.gadget.car.vehicle.VehicleSession;
import me.luckyraven.gadget.car.vehicle.entity.MinecartVehicle;
import me.luckyraven.gadget.car.vehicle.entity.VehicleEntity;
import me.luckyraven.gadget.car.vehicle.packet.VehicleInputInterceptor;
import me.luckyraven.gadget.config.GadgetPhysicsConfig;
import me.luckyraven.gadget.fuel.FuelService;
import me.luckyraven.gadget.packet.PlayerInputInterceptor;
import me.luckyraven.item.fuel.FuelKey;
import me.luckyraven.persistence.repository.IRepository;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-level API for placing, mounting, picking up, and destroying active car vehicles.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>Player right-clicks the car item on a block → {@link #placeCar} spawns the entity and
 *       registers a {@link ParkedVehicle}. The caller consumes the item.</li>
 *   <li>Player right-clicks the entity → {@link #mountCar} creates a {@link VehicleSession},
 *       mounts the player, and starts the movement task.</li>
 *   <li>Player dismounts (shift) → {@link #parkCar} tears down the session, returns to parked.</li>
 *   <li>Player shift+right-clicks the entity (while parked) → {@link #pickupCar} returns the item.</li>
 * </ol>
 *
 * <h3>Cross-reload persistence</h3>
 * Parked vehicle state (car ID, location, fuel, durability, placer UUID) is stored in the database
 * via {@link IRepository}. On plugin enable, {@link #reloadParkedVehicles} loads all records and
 * re-spawns the Minecart entities so the cars appear in the world exactly where they were left.
 */
@Getter
public class CarService implements BeanLifecycle {

	private final CarManager             carManager;
	private final VehicleRegistry        vehicleRegistry;
	private final JavaPlugin             plugin;
	private final IRepository<ParkedCar> parkedCarRepository;
	private final FuelService            fuelService;
	private final GadgetPhysicsConfig    physicsConfig;

	/**
	 * Placed-but-not-driven vehicles, keyed by entity UUID.
	 */
	private final Map<UUID, ParkedVehicle> parkedVehicles = new ConcurrentHashMap<>();

	/**
	 * Database records for placed vehicles, keyed by entity UUID. Retained across mount/unmount cycles so the same
	 * {@code dbId} is reused on update, avoiding orphaned DB rows.
	 */
	private final Map<UUID, ParkedCar> parkedCarRecords = new ConcurrentHashMap<>();

	// PersistentDataContainer keys stored on the Minecart entity
	private final NamespacedKey pdcCarId;
	private final NamespacedKey pdcFuel;
	private final NamespacedKey pdcFuelMax;
	private final NamespacedKey pdcDurability;
	private final NamespacedKey pdcPlacer;
	private final NamespacedKey pdcExhaustSide;
	private final NamespacedKey pdcDbId;

	public CarService(CarManager carManager, VehicleRegistry vehicleRegistry, JavaPlugin plugin,
	                  IRepository<ParkedCar> parkedCarRepository, FuelService fuelService,
	                  GadgetPhysicsConfig physicsConfig) {
		this.carManager          = carManager;
		this.vehicleRegistry     = vehicleRegistry;
		this.plugin              = plugin;
		this.parkedCarRepository = parkedCarRepository;
		this.fuelService         = fuelService;
		this.physicsConfig       = physicsConfig;

		this.pdcCarId       = new NamespacedKey(plugin, "car_id");
		this.pdcFuel        = new NamespacedKey(plugin, "car_fuel");
		this.pdcFuelMax     = new NamespacedKey(plugin, "car_fuel_max");
		this.pdcDurability  = new NamespacedKey(plugin, "car_durability");
		this.pdcPlacer      = new NamespacedKey(plugin, "car_placer");
		this.pdcExhaustSide = new NamespacedKey(plugin, "car_exhaust_side");
		this.pdcDbId        = new NamespacedKey(plugin, "car_db_id");

		parkedCarRepository.setDataSupplier(() -> new ArrayList<>(parkedCarRecords.values()));
	}

	// ------------------------------------------------------------------
	// Placement
	// ------------------------------------------------------------------

	/**
	 * Spawns a car entity at the given location without mounting any player. The caller is responsible for consuming
	 * the item from the player's hand.
	 *
	 * @param fuel initial fuel value (read from the item's NBT)
	 * @param maxFuel max fuel capacity (read from the item's NBT; falls back to the car config value)
	 * @param durability initial durability value (read from the item's NBT)
	 *
	 * @return {@code true} if the entity was placed successfully
	 */
	public boolean placeCar(Player player, String carId, Location location, int fuel, int maxFuel, int durability,
	                        @Nullable ExhaustSide exhaustSide) {
		Car car = carManager.getCar(carId);
		if (car == null) return false;

		VehicleEntity entity = createVehicleEntity(car);

		Location spawnLoc = location.clone();
		spawnLoc.setYaw(player.getLocation().getYaw());
		entity.spawn(spawnLoc);

		UUID entityUUID = entity.getEntityUUID();
		if (entityUUID == null) return false;

		storePdc(entity, carId, fuel, maxFuel, durability, player.getUniqueId());
		storePdcExhaustSide(entity, exhaustSide);

		ParkedVehicle pv = new ParkedVehicle(entity, car, player.getUniqueId(), fuel, maxFuel, durability,
		                                     exhaustSide);
		ParkedCar record = buildRecord(entityUUID, pv, spawnLoc);
		parkedVehicles.put(entityUUID, pv);
		if (record != null) {
			storePdcDbId(entity, record.getDbId());
			parkedCarRecords.put(entityUUID, record);
			parkedCarRepository.save(record);
		}
		return true;
	}

	// ------------------------------------------------------------------
	// Mounting
	// ------------------------------------------------------------------

	/**
	 * Mounts {@code player} into the parked vehicle identified by {@code entityUUID}, moving it from the parked
	 * registry to an active {@link VehicleSession}. The database record is intentionally kept so it can be updated with
	 * the new location when the car is parked again.
	 *
	 * @return {@code true} if the player was successfully mounted
	 */
	public boolean mountCar(Player player, UUID entityUUID) {
		ParkedVehicle parked = parkedVehicles.remove(entityUUID);
		if (parked == null) return false;

		if (vehicleRegistry.isPlayerDriving(player.getUniqueId())) {
			// Put it back — player is already in a vehicle
			parkedVehicles.put(entityUUID, parked);
			return false;
		}

		VehicleEntity entity = parked.getEntity();
		Car           car    = parked.getCar();

		// Entity may have died between reload and this interaction (e.g. minecart physics on
		// non-rail terrain after a server restart). Only re-spawn when the entity is definitively
		// gone (null or isDead=true). Do NOT re-spawn for isValid()=false — the entity still exists
		// physically in that case and despawn()+spawn() would leave two entities in the world.
		Entity bukkitEntity = entity.getBukkitEntity();
		if (bukkitEntity == null || bukkitEntity.isDead()) {
			ParkedCar record = parkedCarRecords.remove(entityUUID);
			if (record == null) {
				parkedVehicles.put(entityUUID, parked);
				return false;
			}
			World spawnWorld = Bukkit.getWorld(record.getWorld());
			if (spawnWorld == null) {
				parkedCarRecords.put(entityUUID, record);
				parkedVehicles.put(entityUUID, parked);
				return false;
			}
			Location respawnLoc = new Location(spawnWorld, record.getX(), record.getY(), record.getZ(), record.getYaw(),
			                                   0f);
			entity.despawn();
			entity.spawn(respawnLoc);
			storePdc(entity, record.getCarId(), record.getFuel(), parked.getMaxFuel(), record.getDurability(),
			         record.getPlacerUUID());
			storePdcDbId(entity, record.getDbId());

			UUID newEntityUUID = entity.getEntityUUID();
			if (newEntityUUID == null || !entity.isAlive()) {
				parkedCarRecords.put(entityUUID, record);
				parkedVehicles.put(entityUUID, parked);
				return false;
			}
			parkedCarRecords.put(newEntityUUID, record);
		}

		int maxFuel = parked.getMaxFuel();

		VehicleSession session = new VehicleSession(entity, car, player, parked.getDurability(), parked.getFuel(),
		                                            maxFuel, parked.getExhaustSide());
		VehicleMovementTask task = new VehicleMovementTask(session, this, physicsConfig);
		session.setTask(task);

		// Register before mounting so CarDismountListener can find the session if Minecraft
		// physics immediately eject the player (e.g. no-rail minecart after server restart).
		vehicleRegistry.register(session);

		entity.mount(player);

		Channel channel = PlayerInputInterceptor.getChannel(player);
		if (channel != null) {
			channel.pipeline()
			       .addBefore("packet_handler", VehicleInputInterceptor.HANDLER_NAME,
			                  new VehicleInputInterceptor(session));
		}

		task.runTaskTimer(plugin, 1L, 1L);
		return true;
	}

	// ------------------------------------------------------------------
	// Park (dismount without returning item)
	// ------------------------------------------------------------------

	/**
	 * Ends an active driving session and leaves the vehicle entity in the world as a parked car. The current fuel and
	 * durability are preserved. The car item is NOT returned to the player. Call this when a player voluntarily
	 * dismounts; use {@link #destroyCar} only when the car should be fully removed.
	 */
	public void parkCar(UUID entityUUID) {
		VehicleSession session = vehicleRegistry.getByEntity(entityUUID);
		if (session == null) return;

		session.removeDisplays();
		removeInputHandler(session.getDriver());

		if (session.getTask() != null && !session.getTask().isCancelled()) {
			session.getTask().cancel();
		}

		int  durability  = session.getCurrentDurability();
		int  currentFuel = session.getCurrentFuel();
		UUID placerUUID  = session.getDriverUUID();

		int maxFuel = session.getMaxFuel();

		storePdc(session.getEntity(), session.getCar().getCarId(), currentFuel, maxFuel, durability, placerUUID);
		storePdcExhaustSide(session.getEntity(), session.getExhaustSide());

		ParkedVehicle pv = new ParkedVehicle(session.getEntity(), session.getCar(), placerUUID, currentFuel, maxFuel,
		                                     durability, session.getExhaustSide());
		parkedVehicles.put(entityUUID, pv);
		vehicleRegistry.unregister(entityUUID);

		// Build updated record. Prefer the per-tick lastKnownLocation; fall back to live entity location.
		Entity   entity  = session.getEntity().getBukkitEntity();
		Location loc     = null;
		Location tracked = session.getLastKnownLocation();
		if (tracked != null && tracked.getWorld() != null) {
			loc = tracked;
		} else if (entity != null && entity.getLocation().getWorld() != null) {
			loc = entity.getLocation();
		}
		if (loc != null) {
			ParkedCar existing = parkedCarRecords.get(entityUUID);
			String    dbId     = existing != null ? existing.getDbId() : UUID.randomUUID().toString();
			ParkedCar record = new ParkedCar(dbId, session.getCar().getCarId(), loc.getWorld().getName(), loc.getX(),
			                                 loc.getY(), loc.getZ(), loc.getYaw(), currentFuel, maxFuel, durability,
			                                 placerUUID, session.getExhaustSide());
			parkedCarRecords.put(entityUUID, record);
			parkedCarRepository.save(record);
		}

		// Clear any leftover passenger. Critical for the disconnect path: when checkGuards parks the car because the
		// driver went offline, the player is still mounted in saved entity data — without this eject they'd auto-mount
		// the parked minecart on rejoin, then walk off and push it, leaving a duplicate when reloadParkedVehicles
		// spawns a fresh car at the persisted location. A no-op when the player already dismounted normally.
		if (entity != null && !entity.isDead()) {
			entity.eject();
		}
	}

	/**
	 * Preserves the database record for a driven vehicle whose underlying entity has been force-removed (e.g. another
	 * plugin killed the minecart, it fell into the void, or Minecraft itself despawned it while the player was
	 * mounted). Unlike {@link #parkCar}, this does not re-insert a {@link ParkedVehicle} — the live entity is already
	 * gone — it only updates the persisted record so {@link #reloadParkedVehicles} can respawn the car at the last
	 * known location on the next enable.
	 *
	 * @param entityUUID UUID of the entity whose session should be force-parked
	 * @param fallbackLoc location to use if the entity's own location is no longer available (typically the driver's
	 * 		current location); may be {@code null}, in which case the existing DB record location is kept
	 */
	public void forcePark(UUID entityUUID, @Nullable Location fallbackLoc) {
		VehicleSession session = vehicleRegistry.getByEntity(entityUUID);
		if (session == null) return;

		session.removeDisplays();
		removeInputHandler(session.getDriver());

		if (session.getTask() != null && !session.getTask().isCancelled()) {
			session.getTask().cancel();
		}

		int  durability  = session.getCurrentDurability();
		int  currentFuel = session.getCurrentFuel();
		UUID placerUUID  = session.getDriverUUID();
		int  maxFuel     = session.getMaxFuel();

		vehicleRegistry.unregister(entityUUID);

		Location resolved = null;
		Location tracked  = session.getLastKnownLocation();
		if (tracked != null && tracked.getWorld() != null) {
			resolved = tracked;
		} else {
			Entity bukkitEntity = session.getEntity().getBukkitEntity();
			if (bukkitEntity != null && bukkitEntity.isValid() && bukkitEntity.getLocation().getWorld() != null) {
				resolved = bukkitEntity.getLocation();
			} else if (fallbackLoc != null && fallbackLoc.getWorld() != null) {
				resolved = fallbackLoc;
			}
		}

		ParkedCar existing = parkedCarRecords.get(entityUUID);
		String    dbId     = existing != null ? existing.getDbId() : UUID.randomUUID().toString();

		String world;
		double x, y, z;
		float  yaw;
		if (resolved != null) {
			world = resolved.getWorld().getName();
			x     = resolved.getX();
			y     = resolved.getY();
			z     = resolved.getZ();
			yaw   = resolved.getYaw();
		} else if (existing != null) {
			world = existing.getWorld();
			x     = existing.getX();
			y     = existing.getY();
			z     = existing.getZ();
			yaw   = existing.getYaw();
		} else {
			return;
		}

		ParkedCar record = new ParkedCar(dbId, session.getCar().getCarId(), world, x, y, z, yaw, currentFuel, maxFuel,
		                                 durability, placerUUID, session.getExhaustSide());
		parkedCarRecords.put(entityUUID, record);
		parkedCarRepository.save(record);

		// Remove any orphaned remnant of the original entity. If !isAlive triggered because the chunk unloaded
		// (entity invalid, not dead), the minecart still exists in world data and would re-appear when the chunk
		// reloads — duplicating the freshly spawned car on next reloadParkedVehicles().
		session.getEntity().despawn();
	}

	// ------------------------------------------------------------------
	// Pickup
	// ------------------------------------------------------------------

	/**
	 * Removes a parked vehicle from the world and returns the car item (with saved fuel and durability) to
	 * {@code player}'s inventory.
	 */
	public void pickupCar(Player player, UUID entityUUID) {
		ParkedVehicle parked = parkedVehicles.remove(entityUUID);
		if (parked == null) return;

		parked.getEntity().despawn();

		ParkedCar record = parkedCarRecords.remove(entityUUID);
		if (record != null) {
			parkedCarRepository.delete(record);
		}

		ItemStack   item    = parked.getCar().buildItem(player);
		ItemBuilder builder = new ItemBuilder(item);
		builder.addTag(CarKey.CAR_DURABILITY.getKey(), parked.getDurability());
		builder.addTag(FuelKey.FUEL_CURRENT.getKey(), parked.getFuel());
		builder.addTag(FuelKey.FUEL_MAX.getKey(), parked.getMaxFuel());
		builder.addTag(CarKey.CAR_OWNER.getKey(), player.getUniqueId().toString());
		if (parked.getExhaustSide() != null) {
			builder.addTag(CarKey.CAR_EXHAUST_SIDE.getKey(), parked.getExhaustSide().name());
		}
		player.getInventory().addItem(builder.build());
	}

	// ------------------------------------------------------------------
	// Destroy (active sessions + parked)
	// ------------------------------------------------------------------

	/**
	 * Destroys an active car vehicle and optionally returns the car item to the driver. Also handles parked vehicles
	 * (no item is returned for those — use {@link #pickupCar} instead).
	 */
	public void destroyCar(UUID entityUUID, boolean returnItem) {
		// --- active session ---
		VehicleSession session = vehicleRegistry.getByEntity(entityUUID);
		if (session != null) {
			session.removeDisplays();
			removeInputHandler(session.getDriver());

			VehicleMovementTask task = session.getTask();
			if (task == null) return;

			if (!task.isCancelled()) {
				task.cancel();
			}

			// Unregister before despawn so CarDismountListener finds no session when
			// the entity eject fires, allowing the player to dismount naturally.
			vehicleRegistry.unregister(entityUUID);

			session.getEntity().despawn();

			if (returnItem && session.getDriver().isOnline() && !session.isDestroyed()) {
				session.getDriver().getInventory().addItem(session.buildReturnItem());
			}

			ParkedCar record = parkedCarRecords.remove(entityUUID);
			if (record != null) {
				parkedCarRepository.delete(record);
			}
			return;
		}

		// --- parked vehicle ---
		ParkedVehicle parked = parkedVehicles.remove(entityUUID);
		if (parked != null) {
			parked.getEntity().despawn();
			ParkedCar record = parkedCarRecords.remove(entityUUID);
			if (record != null) {
				parkedCarRepository.delete(record);
			}
		}
	}

	/**
	 * Destroys all active and parked vehicles. Called on plugin disable to ensure clean shutdown. Active sessions are
	 * converted to parked entries so their location and stats are persisted and the Minecart re-appears on the next
	 * server start.
	 */
	public void destroyAll() {
		// Snapshot the active sessions so the two passes below iterate the same set regardless of
		// incidental mutation (e.g. a checkGuards tick mid-shutdown).
		Collection<VehicleSession> activeSessions = new ArrayList<>(vehicleRegistry.getAllSessions());

		// Pass 1 — dismount every driver and cancel their movement task. This must complete for ALL
		// sessions before we start persisting records, otherwise a still-mounted driver in a later
		// session leaks a RootVehicle NBT tag to their .dat (Minecraft serializes the mounted minecart
		// into the player entity when saveAll fires later in the shutdown sequence), and the rogue
		// minecart that NBT restores on rejoin duplicates the car reloadParkedVehicles spawns from the
		// DB record.
		for (VehicleSession session : activeSessions) {
			session.removeDisplays();
			removeInputHandler(session.getDriver());

			VehicleMovementTask task = session.getTask();
			if (task != null && !task.isCancelled()) {
				task.cancel();
			}

			Entity live = session.getEntity().getBukkitEntity();
			if (live != null && !live.isDead()) {
				live.eject();
			}
		}

		// Pass 2 — now that every driver is unmounted, convert each session into a parked entry and
		// build/update its DB record.
		for (VehicleSession session : activeSessions) {
			UUID entityUUID = session.getEntity().getEntityUUID();
			if (entityUUID == null) continue;

			int  durability  = session.getCurrentDurability();
			int  currentFuel = session.getCurrentFuel();
			UUID placerUUID  = session.getDriverUUID();

			ParkedVehicle pv = new ParkedVehicle(session.getEntity(), session.getCar(), placerUUID, currentFuel,
			                                     session.getMaxFuel(), durability, session.getExhaustSide());
			parkedVehicles.put(entityUUID, pv);

			Location loc     = null;
			Location tracked = session.getLastKnownLocation();
			if (tracked != null && tracked.getWorld() != null) {
				loc = tracked;
			} else {
				Entity entity = session.getEntity().getBukkitEntity();
				if (entity != null && entity.getLocation().getWorld() != null) {
					loc = entity.getLocation();
				}
			}
			if (loc != null) {
				ParkedCar existing = parkedCarRecords.get(entityUUID);
				String    dbId     = existing != null ? existing.getDbId() : UUID.randomUUID().toString();
				ParkedCar record = new ParkedCar(dbId, session.getCar().getCarId(), loc.getWorld().getName(),
				                                 loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), currentFuel,
				                                 session.getMaxFuel(), durability, placerUUID,
				                                 session.getExhaustSide());
				parkedCarRecords.put(entityUUID, record);
				// Persist the driven-location record immediately. Relying on PeriodicalUpdates.forceUpdate() is
				// unreliable here: whichever order the bean shutdown visits us in, parkedCarRecords.clear() below
				// wipes the map, so a post-destroyAll forceUpdate would see no records, and a pre-destroyAll one
				// would miss the just-converted session records. Saving inline mirrors how parkCar persists.
				parkedCarRepository.save(record);
			}
		}
		vehicleRegistry.clear();

		for (ParkedVehicle parked : parkedVehicles.values()) {
			parked.getEntity().despawn();
		}
		parkedVehicles.clear();
		parkedCarRecords.clear();
	}

	// ------------------------------------------------------------------
	// Config reload
	// ------------------------------------------------------------------

	/**
	 * Refreshes the {@link Car} definition references held by all in-memory parked vehicles and active sessions. Must
	 * be called after {@link CarManager} has been reloaded from config (e.g. on {@code /glw reload}) so that existing
	 * in-world cars immediately pick up the updated configuration.
	 */
	public void refreshCarDefinitions() {
		for (UUID entityUUID : new ArrayList<>(parkedVehicles.keySet())) {
			ParkedVehicle old = parkedVehicles.get(entityUUID);
			if (old == null) continue;

			Car freshCar = carManager.getCar(old.getCar().getCarId());
			if (freshCar == null) continue;

			ParkedVehicle parkedVehicle = new ParkedVehicle(old.getEntity(), freshCar, old.getPlacerUUID(),
			                                                old.getFuel(), old.getMaxFuel(), old.getDurability(),
			                                                old.getExhaustSide());
			parkedVehicles.put(entityUUID, parkedVehicle);
		}

		for (VehicleSession session : vehicleRegistry.getAllSessions()) {
			Car freshCar = carManager.getCar(session.getCar().getCarId());
			if (freshCar != null) session.setCar(freshCar);
		}
	}

	// ------------------------------------------------------------------
	// Reload persistence
	// ------------------------------------------------------------------

	/**
	 * Loads parked vehicle data from the database and re-spawns each car entity. Call this once on plugin enable after
	 * {@link CarManager} is ready.
	 *
	 * <p>Minecart entities are normally despawned on shutdown, but they can survive if the server
	 * crashed or entity chunk-saves raced ahead of the plugin's cleanup. This method first scans loaded worlds for any
	 * leftover car-tagged minecarts keyed by their {@code car_db_id} PDC value, reclaims those instead of spawning a
	 * duplicate, and removes any orphans that no longer have a matching database record.
	 */
	public void reloadParkedVehicles() {
		Collection<ParkedCar> records = parkedCarRepository.loadAll();

		for (ParkedCar record : records) {
			Car car = carManager.getCar(record.getCarId());
			if (car == null) continue;

			World world = Bukkit.getWorld(record.getWorld());
			if (world == null) continue;

			Location spawnLoc = new Location(world, record.getX(), record.getY(), record.getZ(), record.getYaw(), 0f);

			// Force-load the chunk so any entity that survived from a previous session
			// (crash or chunk-save race) is present before we check for it.
			spawnLoc.getChunk().load();

			Minecart survivor = findSurvivor(spawnLoc.getChunk(), record.getDbId());

			// Use the persisted max fuel from the DB record; fall back to config if not set (0 = legacy row).
			int maxFuel = record.getMaxFuel() > 0 ? record.getMaxFuel() : car.getMaxFuel();

			VehicleEntity entity;
			if (survivor != null) {
				// Reclaim the surviving entity — do not spawn a duplicate.
				entity = new MinecartVehicle(car, survivor);
			} else {
				entity = createVehicleEntity(car);
				entity.spawn(spawnLoc);
			}

			UUID entityUUID = entity.getEntityUUID();
			if (entityUUID == null) continue;

			storePdc(entity, record.getCarId(), record.getFuel(), maxFuel, record.getDurability(),
			         record.getPlacerUUID());
			storePdcDbId(entity, record.getDbId());

			ParkedVehicle parkedVehicle = new ParkedVehicle(entity, car, record.getPlacerUUID(), record.getFuel(),
			                                                maxFuel, record.getDurability(), record.getExhaustSide());
			parkedVehicles.put(entityUUID, parkedVehicle);
			parkedCarRecords.put(entityUUID, record);
		}
	}

	/**
	 * Adds fuel to a parked (undriven) vehicle, capped at the fuel definition's max capacity. Updates both the
	 * in-memory record and the entity PDC. Does nothing if fuel is not enabled for this car or the tank is full.
	 *
	 * @param entityUUID UUID of the parked car entity
	 * @param amount fuel ticks to add
	 */
	public boolean refuelParkedCar(UUID entityUUID, int amount) {
		ParkedVehicle parked = parkedVehicles.get(entityUUID);
		if (parked == null || amount <= 0) return false;

		Car car = parked.getCar();
		if (!car.isFuelEnabled() || car.getFuelKey() == null) return false;

		int toAdd = Math.clamp(amount, 0, parked.getMaxFuel() - parked.getFuel());
		if (toAdd == 0) return false;

		parked.addFuel(toAdd);
		storePdc(parked.getEntity(), car.getCarId(), parked.getFuel(), parked.getMaxFuel(), parked.getDurability(),
		         parked.getPlacerUUID());

		ParkedCar record = parkedCarRecords.get(entityUUID);
		if (record != null) {
			record.setFuel(parked.getFuel());
			parkedCarRepository.save(record);
		}

		return true;
	}

	// ------------------------------------------------------------------
	// Damage
	// ------------------------------------------------------------------

	/**
	 * Applies damage to a parked vehicle and persists the updated durability. Destroys the vehicle without returning an
	 * item if durability reaches zero.
	 */
	public void damageParkedCar(UUID entityUUID, int amount) {
		ParkedVehicle parked = parkedVehicles.get(entityUUID);
		if (parked == null) return;

		parked.damage(amount);

		if (parked.isDestroyed()) {
			destroyCar(entityUUID, false);
		} else {
			ParkedCar record = parkedCarRecords.get(entityUUID);
			if (record != null) {
				record.setDurability(parked.getDurability());
				parkedCarRepository.save(record);
			}
		}
	}

	/**
	 * Returns {@code true} if the entity UUID belongs to a parked (undriven) vehicle.
	 */
	public boolean isParkedVehicle(UUID entityUUID) {
		return parkedVehicles.containsKey(entityUUID);
	}

	// ------------------------------------------------------------------
	// Queries
	// ------------------------------------------------------------------

	/**
	 * Returns the parked vehicle for the given entity UUID, or {@code null}.
	 */
	@Nullable
	public ParkedVehicle getParkedVehicle(UUID entityUUID) {
		return parkedVehicles.get(entityUUID);
	}

	/**
	 * Gets the active session for a given player, or {@code null} if the player is not driving.
	 */
	@Nullable
	public VehicleSession getSession(Player player) {
		return vehicleRegistry.getByPlayer(player.getUniqueId());
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		if (!firstLoad) refreshCarDefinitions();
	}

	// ------------------------------------------------------------------
	// Internal helpers
	// ------------------------------------------------------------------

	@Override
	public void onShutdown() {
		destroyAll();
	}

	/**
	 * Scans a single chunk for a {@link Minecart} whose {@code car_db_id} PDC value matches {@code dbId}. Returns
	 * {@code null} if none is found or the entity is dead.
	 */
	@Nullable
	private Minecart findSurvivor(org.bukkit.Chunk chunk, String dbId) {
		for (Entity e : chunk.getEntities()) {
			if (!(e instanceof Minecart mc) || mc.isDead()) continue;
			String stored = mc.getPersistentDataContainer().get(pdcDbId, PersistentDataType.STRING);
			if (dbId.equals(stored)) return mc;
		}
		return null;
	}

	private VehicleEntity createVehicleEntity(Car car) {
		return new MinecartVehicle(car);
	}

	/**
	 * Wraps an already-existing world entity in a {@link MinecartVehicle} adapter.
	 */
	@Nullable
	private VehicleEntity wrapExistingEntity(Entity entity, Car car) {
		return entity instanceof Minecart m ? new MinecartVehicle(car, m) : null;
	}

	/**
	 * Builds a {@link ParkedCar} DB record from a {@link ParkedVehicle} using the provided location. Reuses the
	 * existing {@code dbId} from {@link #parkedCarRecords} if one exists for this entity, otherwise generates a new
	 * UUID.
	 */
	@Nullable
	private ParkedCar buildRecord(UUID entityUUID, ParkedVehicle pv, Location loc) {
		if (loc.getWorld() == null) return null;
		ParkedCar existing = parkedCarRecords.get(entityUUID);
		String    dbId     = existing != null ? existing.getDbId() : UUID.randomUUID().toString();
		return new ParkedCar(dbId, pv.getCar().getCarId(), loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(),
		                     loc.getYaw(), pv.getFuel(), pv.getMaxFuel(), pv.getDurability(), pv.getPlacerUUID(),
		                     pv.getExhaustSide());
	}

	private void storePdc(VehicleEntity vehicleEntity, String carId, int fuel, int maxFuel, int durability,
	                      @Nullable UUID placerUUID) {
		Entity entity = vehicleEntity.getBukkitEntity();
		if (entity == null) return;

		PersistentDataContainer pdc = entity.getPersistentDataContainer();
		pdc.set(pdcCarId, PersistentDataType.STRING, carId);
		pdc.set(pdcFuel, PersistentDataType.INTEGER, fuel);
		pdc.set(pdcFuelMax, PersistentDataType.INTEGER, maxFuel);
		pdc.set(pdcDurability, PersistentDataType.INTEGER, durability);
		if (placerUUID != null) {
			pdc.set(pdcPlacer, PersistentDataType.STRING, placerUUID.toString());
		}
	}

	private void storePdcDbId(VehicleEntity vehicleEntity, String dbId) {
		Entity entity = vehicleEntity.getBukkitEntity();
		if (entity == null) return;
		entity.getPersistentDataContainer().set(pdcDbId, PersistentDataType.STRING, dbId);
	}

	private void storePdcExhaustSide(VehicleEntity vehicleEntity, @Nullable ExhaustSide exhaustSide) {
		if (exhaustSide == null) return;
		Entity entity = vehicleEntity.getBukkitEntity();
		if (entity == null) return;
		entity.getPersistentDataContainer().set(pdcExhaustSide, PersistentDataType.STRING, exhaustSide.name());
	}

	private void removeInputHandler(Player player) {
		Channel channel = PlayerInputInterceptor.getChannel(player);
		if (channel != null && channel.pipeline().get(VehicleInputInterceptor.HANDLER_NAME) != null) {
			channel.pipeline().remove(VehicleInputInterceptor.HANDLER_NAME);
		}
	}
}
