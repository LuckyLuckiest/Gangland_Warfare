package me.luckyraven.gadget.car;

import io.netty.channel.Channel;
import lombok.Getter;
import me.luckyraven.gadget.car.vehicle.ParkedVehicle;
import me.luckyraven.gadget.car.vehicle.VehicleMovementTask;
import me.luckyraven.gadget.car.vehicle.VehicleRegistry;
import me.luckyraven.gadget.car.vehicle.VehicleSession;
import me.luckyraven.gadget.car.vehicle.entity.MinecartVehicle;
import me.luckyraven.gadget.car.vehicle.entity.VehicleEntity;
import me.luckyraven.gadget.car.vehicle.packet.VehicleInputInterceptor;
import me.luckyraven.gadget.config.GadgetPhysicsConfig;
import me.luckyraven.gadget.fuel.FuelService;
import me.luckyraven.item.fuel.Fuel;
import me.luckyraven.persistence.repository.IRepository;
import me.luckyraven.util.ItemBuilder;
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
 *       registers a {@link ParkedVehicle}. The item is consumed by the caller.</li>
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
public class CarService {

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
	private final NamespacedKey pdcDurability;
	private final NamespacedKey pdcPlacer;

	public CarService(CarManager carManager, VehicleRegistry vehicleRegistry, JavaPlugin plugin,
	                  IRepository<ParkedCar> parkedCarRepository, FuelService fuelService,
	                  GadgetPhysicsConfig physicsConfig) {
		this.carManager          = carManager;
		this.vehicleRegistry     = vehicleRegistry;
		this.plugin              = plugin;
		this.parkedCarRepository = parkedCarRepository;
		this.fuelService         = fuelService;
		this.physicsConfig       = physicsConfig;

		this.pdcCarId      = new NamespacedKey(plugin, "car_id");
		this.pdcFuel       = new NamespacedKey(plugin, "car_fuel");
		this.pdcDurability = new NamespacedKey(plugin, "car_durability");
		this.pdcPlacer     = new NamespacedKey(plugin, "car_placer");
	}

	// ------------------------------------------------------------------
	// Placement
	// ------------------------------------------------------------------

	/**
	 * Spawns a car entity at the given location without mounting any player. The caller is responsible for consuming
	 * the item from the player's hand.
	 *
	 * @param fuel initial fuel value (read from the item's NBT)
	 * @param durability initial durability value (read from the item's NBT)
	 *
	 * @return {@code true} if the entity was placed successfully
	 */
	public boolean placeCar(Player player, String carId, Location location, int fuel, int durability) {
		Car car = carManager.getCar(carId);
		if (car == null) return false;

		VehicleEntity entity = createVehicleEntity(car);

		Location spawnLoc = location.clone();
		spawnLoc.setYaw(player.getLocation().getYaw());
		entity.spawn(spawnLoc);

		UUID entityUUID = entity.getEntityUUID();
		if (entityUUID == null) return false;

		storePdc(entity, carId, fuel, durability, player.getUniqueId());

		ParkedVehicle pv     = new ParkedVehicle(entity, car, player.getUniqueId(), fuel, durability);
		ParkedCar     record = buildRecord(entityUUID, pv, spawnLoc);
		parkedVehicles.put(entityUUID, pv);
		if (record != null) {
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

		Fuel fuelDef = car.isFuelEnabled() && car.getFuelKey() != null ? fuelService.getFuel(car.getFuelKey()) : null;
		int  maxFuel = fuelDef != null ? fuelDef.getMaxFuel() : 0;

		VehicleSession session = new VehicleSession(entity, car, player, parked.getDurability(), parked.getFuel(),
		                                            maxFuel);
		VehicleMovementTask task = new VehicleMovementTask(session, this, physicsConfig);
		session.setTask(task);

		// Register before mounting so CarDismountListener can find the session if Minecraft
		// physics immediately eject the player (e.g. no-rail minecart after server restart).
		vehicleRegistry.register(session);

		entity.mount(player);

		Channel channel = VehicleInputInterceptor.getChannel(player);
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

		storePdc(session.getEntity(), session.getCar().getCarId(), currentFuel, durability, placerUUID);

		ParkedVehicle pv = new ParkedVehicle(session.getEntity(), session.getCar(), placerUUID, currentFuel,
		                                     durability);
		parkedVehicles.put(entityUUID, pv);
		vehicleRegistry.unregister(entityUUID);

		// Build updated record from current entity location and save
		Entity entity = session.getEntity().getBukkitEntity();
		if (entity != null && entity.getLocation().getWorld() != null) {
			Location  loc      = entity.getLocation();
			ParkedCar existing = parkedCarRecords.get(entityUUID);
			String    dbId     = existing != null ? existing.getDbId() : UUID.randomUUID().toString();
			ParkedCar record = new ParkedCar(dbId, session.getCar().getCarId(), loc.getWorld().getName(), loc.getX(),
			                                 loc.getY(), loc.getZ(), loc.getYaw(), currentFuel, durability, placerUUID);
			parkedCarRecords.put(entityUUID, record);
			parkedCarRepository.save(record);
		}
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

		ItemStack   item    = parked.getCar().buildItem();
		ItemBuilder builder = new ItemBuilder(item);
		builder.addTag(CarKey.CAR_DURABILITY.getKey(), parked.getDurability());
		builder.addTag(CarKey.CAR_FUEL.getKey(), parked.getFuel());
		builder.addTag(CarKey.CAR_OWNER.getKey(), player.getUniqueId().toString());
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
		// Convert every active session into a parked entry; build/update its DB record
		for (VehicleSession session : vehicleRegistry.getAllSessions()) {
			session.removeDisplays();
			removeInputHandler(session.getDriver());

			VehicleMovementTask task = session.getTask();
			if (task == null) return;

			if (!task.isCancelled()) {
				task.cancel();
			}

			UUID entityUUID = session.getEntity().getEntityUUID();
			if (entityUUID == null) continue;

			int  durability  = session.getCurrentDurability();
			int  currentFuel = session.getCurrentFuel();
			UUID placerUUID  = session.getDriverUUID();

			ParkedVehicle pv = new ParkedVehicle(session.getEntity(), session.getCar(), placerUUID, currentFuel,
			                                     durability);
			parkedVehicles.put(entityUUID, pv);

			Entity entity = session.getEntity().getBukkitEntity();
			if (entity != null && entity.getLocation().getWorld() != null) {
				Location  loc      = entity.getLocation();
				ParkedCar existing = parkedCarRecords.get(entityUUID);
				String    dbId     = existing != null ? existing.getDbId() : UUID.randomUUID().toString();
				ParkedCar record = new ParkedCar(dbId, session.getCar().getCarId(), loc.getWorld().getName(),
				                                 loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), currentFuel,
				                                 durability, placerUUID);
				parkedCarRecords.put(entityUUID, record);
			}
		}
		vehicleRegistry.clear();

		// DB persistence is handled by PeriodicalUpdates.forceUpdate() which runs after
		// destroyAll() completes. The data supplier returns parkedCarRecords.values(), so
		// all records (including the just-converted sessions) are flushed by the force-save.

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
			parkedVehicles.put(entityUUID,
			                   new ParkedVehicle(old.getEntity(), freshCar, old.getPlacerUUID(), old.getFuel(),
			                                     old.getDurability()));
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
	 * {@link CarManager} is ready. Minecart entities do not survive server restarts, so they are re-created here from
	 * the saved records.
	 */
	public void reloadParkedVehicles() {
		Collection<ParkedCar> records = parkedCarRepository.loadAll();

		for (ParkedCar record : records) {
			Car car = carManager.getCar(record.getCarId());
			if (car == null) continue;

			World world = Bukkit.getWorld(record.getWorld());
			if (world == null) continue;

			Location spawnLoc = new Location(world, record.getX(), record.getY(), record.getZ(), record.getYaw(), 0f);

			VehicleEntity entity = createVehicleEntity(car);
			entity.spawn(spawnLoc);

			UUID entityUUID = entity.getEntityUUID();
			if (entityUUID == null) continue;

			storePdc(entity, record.getCarId(), record.getFuel(), record.getDurability(), record.getPlacerUUID());
			parkedVehicles.put(entityUUID, new ParkedVehicle(entity, car, record.getPlacerUUID(), record.getFuel(),
			                                                 record.getDurability()));
			parkedCarRecords.put(entityUUID, record);
		}
	}

	// ------------------------------------------------------------------
	// Damage
	// ------------------------------------------------------------------

	/**
	 * Adds fuel to a parked (undriven) vehicle, capped at the fuel definition's max capacity. Updates both the
	 * in-memory record and the entity PDC. Does nothing if fuel is not enabled for this car or the tank is full.
	 *
	 * @param entityUUID UUID of the parked car entity
	 * @param amount fuel ticks to add
	 */
	public void refuelParkedCar(UUID entityUUID, int amount) {
		ParkedVehicle parked = parkedVehicles.get(entityUUID);
		if (parked == null || amount <= 0) return;

		Car car = parked.getCar();
		if (!car.isFuelEnabled() || car.getFuelKey() == null) return;

		Fuel fuelDef = fuelService.getFuel(car.getFuelKey());
		int  maxFuel = fuelDef != null ? fuelDef.getMaxFuel() : 0;

		int toAdd = Math.min(amount, maxFuel - parked.getFuel());
		if (toAdd <= 0) return;

		parked.addFuel(toAdd);
		storePdc(parked.getEntity(), car.getCarId(), parked.getFuel(), parked.getDurability(), parked.getPlacerUUID());

		ParkedCar record = parkedCarRecords.get(entityUUID);
		if (record != null) {
			record.setFuel(parked.getFuel());
			parkedCarRepository.save(record);
		}
	}

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

	// ------------------------------------------------------------------
	// Queries
	// ------------------------------------------------------------------

	/**
	 * Returns {@code true} if the entity UUID belongs to a parked (undriven) vehicle.
	 */
	public boolean isParkedVehicle(UUID entityUUID) {
		return parkedVehicles.containsKey(entityUUID);
	}

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

	// ------------------------------------------------------------------
	// Internal helpers
	// ------------------------------------------------------------------

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
		                     loc.getYaw(), pv.getFuel(), pv.getDurability(), pv.getPlacerUUID());
	}

	private void storePdc(VehicleEntity vehicleEntity, String carId, int fuel, int durability,
	                      @Nullable UUID placerUUID) {
		Entity entity = vehicleEntity.getBukkitEntity();
		if (entity == null) return;

		PersistentDataContainer pdc = entity.getPersistentDataContainer();
		pdc.set(pdcCarId, PersistentDataType.STRING, carId);
		pdc.set(pdcFuel, PersistentDataType.INTEGER, fuel);
		pdc.set(pdcDurability, PersistentDataType.INTEGER, durability);
		if (placerUUID != null) {
			pdc.set(pdcPlacer, PersistentDataType.STRING, placerUUID.toString());
		}
	}

	private void removeInputHandler(Player player) {
		Channel channel = VehicleInputInterceptor.getChannel(player);
		if (channel != null && channel.pipeline().get(VehicleInputInterceptor.HANDLER_NAME) != null) {
			channel.pipeline().remove(VehicleInputInterceptor.HANDLER_NAME);
		}
	}
}
