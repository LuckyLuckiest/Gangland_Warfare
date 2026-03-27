package me.luckyraven.gadget.car;

import io.netty.channel.Channel;
import lombok.Getter;
import me.luckyraven.gadget.car.vehicle.*;
import me.luckyraven.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
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
 *   <li>Player dismounts (shift) → {@link #destroyCar} tears down the session and returns the item.</li>
 *   <li>Player shift+right-clicks the entity (while parked) → {@link #pickupCar} returns the item.</li>
 * </ol>
 *
 * <h3>Cross-reload persistence</h3>
 * Parked vehicle state (car ID, location, fuel, durability, placer UUID) is written to
 * {@code parked_cars.yml} in the plugin data folder whenever the parked map changes.
 * On plugin enable, {@link #reloadParkedVehicles} reads that file and re-spawns the Minecart
 * entities so the cars appear in the world exactly where they were left.
 */
@Getter
public class CarService {

	private final CarManager      carManager;
	private final VehicleRegistry vehicleRegistry;
	private final JavaPlugin      plugin;

	/**
	 * Placed-but-not-driven vehicles, keyed by entity UUID.
	 */
	private final Map<UUID, ParkedVehicle> parkedVehicles = new ConcurrentHashMap<>();

	// PersistentDataContainer keys stored on the Minecart / ArmorStand entity
	private final NamespacedKey pdcCarId;
	private final NamespacedKey pdcFuel;
	private final NamespacedKey pdcDurability;
	private final NamespacedKey pdcPlacer;

	public CarService(CarManager carManager, VehicleRegistry vehicleRegistry, JavaPlugin plugin) {
		this.carManager      = carManager;
		this.vehicleRegistry = vehicleRegistry;
		this.plugin          = plugin;

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

		parkedVehicles.put(entityUUID, new ParkedVehicle(entity, car, player.getUniqueId(), fuel, durability));
		saveParkedVehicles();
		return true;
	}

	// ------------------------------------------------------------------
	// Mounting
	// ------------------------------------------------------------------

	/**
	 * Mounts {@code player} into the parked vehicle identified by {@code entityUUID}, moving it from the parked
	 * registry to an active {@link VehicleSession}.
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

		entity.mount(player);

		VehicleSession      session = new VehicleSession(entity, car, player, parked.getFuel(), parked.getDurability());
		VehicleMovementTask task    = new VehicleMovementTask(session, this);
		session.setTask(task);

		vehicleRegistry.register(session);

		Channel channel = VehicleInputInterceptor.getChannel(player);
		if (channel != null) {
			channel.pipeline().addBefore("packet_handler", VehicleInputInterceptor.HANDLER_NAME,
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

		int  fuel       = session.getCurrentFuel();
		int  durability = session.getCurrentDurability();
		UUID placerUUID = session.getDriverUUID();

		storePdc(session.getEntity(), session.getCar().getCarId(), fuel, durability, placerUUID);

		parkedVehicles.put(entityUUID,
		                   new ParkedVehicle(session.getEntity(), session.getCar(), placerUUID, fuel, durability));

		vehicleRegistry.unregister(entityUUID);
		saveParkedVehicles();
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

		ItemStack   item    = parked.getCar().buildItem();
		ItemBuilder builder = new ItemBuilder(item);
		builder.addTag(CarKey.CAR_FUEL.getKey(), parked.getFuel());
		builder.addTag(CarKey.CAR_DURABILITY.getKey(), parked.getDurability());
		builder.addTag(CarKey.CAR_OWNER.getKey(), player.getUniqueId().toString());
		player.getInventory().addItem(builder.build());
		saveParkedVehicles();
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

			if (session.getTask() != null && !session.getTask().isCancelled()) {
				session.getTask().cancel();
			}

			session.getEntity().despawn();

			if (returnItem && session.getDriver().isOnline() && !session.isDestroyed()) {
				session.getDriver().getInventory().addItem(session.buildReturnItem());
			}

			vehicleRegistry.unregister(entityUUID);
			saveParkedVehicles();
			return;
		}

		// --- parked vehicle ---
		ParkedVehicle parked = parkedVehicles.remove(entityUUID);
		if (parked != null) {
			parked.getEntity().despawn();
			saveParkedVehicles();
		}
	}

	/**
	 * Destroys all active and parked vehicles. Called on plugin disable to ensure clean shutdown. Active sessions are
	 * converted to parked entries so their location and stats are saved and the Minecart re-appears on the next server
	 * start.
	 */
	public void destroyAll() {
		// Convert every active session into a parked entry (entities still alive, location valid)
		for (VehicleSession session : vehicleRegistry.getAllSessions()) {
			session.removeDisplays();
			removeInputHandler(session.getDriver());

			if (session.getTask() != null && !session.getTask().isCancelled()) {
				session.getTask().cancel();
			}

			UUID uuid = session.getEntity().getEntityUUID();
			if (uuid != null) {
				parkedVehicles.put(uuid, new ParkedVehicle(
						session.getEntity(), session.getCar(), session.getDriverUUID(),
						session.getCurrentFuel(), session.getCurrentDurability()));
			}
		}
		vehicleRegistry.clear();

		// Persist all parked data while entities are still alive and have valid locations
		saveParkedVehicles();

		// Now despawn everything
		for (ParkedVehicle parked : parkedVehicles.values()) {
			parked.getEntity().despawn();
		}
		parkedVehicles.clear();
		// Do NOT save here — the file was already written above with correct data
	}

	// ------------------------------------------------------------------
	// Reload persistence
	// ------------------------------------------------------------------

	/**
	 * Loads parked vehicle data from {@code parked_cars.yml} and re-spawns each car entity. Call this once on plugin
	 * enable after {@link CarManager} is ready. Minecart entities do not survive server restarts, so they are
	 * re-created here from the saved file.
	 */
	public void reloadParkedVehicles() {
		File file = getParkedVehiclesFile();
		if (!file.exists()) return;

		YamlConfiguration config  = YamlConfiguration.loadConfiguration(file);
		var               section = config.getConfigurationSection("parked");
		if (section == null) return;

		for (String key : section.getKeys(false)) {
			String path = "parked." + key;

			String carId = config.getString(path + ".car_id");
			Car    car   = carManager.getCar(carId);
			if (car == null) continue;

			String worldName = config.getString(path + ".world");
			World  world     = Bukkit.getWorld(worldName != null ? worldName : "");
			if (world == null) continue;

			double x          = config.getDouble(path + ".x");
			double y          = config.getDouble(path + ".y");
			double z          = config.getDouble(path + ".z");
			float  yaw        = (float) config.getDouble(path + ".yaw");
			int    fuel       = config.getInt(path + ".fuel", car.getMaxFuel());
			int    durability = config.getInt(path + ".durability", car.getMaxDurability());
			String placerStr  = config.getString(path + ".placer", "");

			UUID placerUUID = null;
			try {
				if (placerStr != null && !placerStr.isEmpty()) {
					placerUUID = UUID.fromString(placerStr);
				}
			} catch (IllegalArgumentException ignored) { }

			Location      spawnLoc = new Location(world, x, y, z, yaw, 0f);
			VehicleEntity entity   = createVehicleEntity(car);
			entity.spawn(spawnLoc);

			UUID entityUUID = entity.getEntityUUID();
			if (entityUUID == null) continue;

			storePdc(entity, carId, fuel, durability, placerUUID);
			parkedVehicles.put(entityUUID, new ParkedVehicle(entity, car, placerUUID, fuel, durability));
		}

		// Overwrite the file with current entity UUIDs now that entities are spawned
		saveParkedVehicles();
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
			saveParkedVehicles();
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
	// File persistence helpers
	// ------------------------------------------------------------------

	/**
	 * Writes the current parked-vehicles map to {@code parked_cars.yml}. Called whenever the map is mutated so the file
	 * always reflects the in-world state.
	 */
	private void saveParkedVehicles() {
		YamlConfiguration config = new YamlConfiguration();

		for (Map.Entry<UUID, ParkedVehicle> entry : parkedVehicles.entrySet()) {
			ParkedVehicle pv     = entry.getValue();
			Entity        entity = pv.getEntity().getBukkitEntity();
			if (entity == null) continue;

			Location loc = entity.getLocation();
			if (loc.getWorld() == null) continue;

			String base = "parked." + entry.getKey();
			config.set(base + ".car_id", pv.getCar().getCarId());
			config.set(base + ".world", loc.getWorld().getName());
			config.set(base + ".x", loc.getX());
			config.set(base + ".y", loc.getY());
			config.set(base + ".z", loc.getZ());
			config.set(base + ".yaw", (double) loc.getYaw());
			config.set(base + ".fuel", pv.getFuel());
			config.set(base + ".durability", pv.getDurability());
			if (pv.getPlacerUUID() != null) {
				config.set(base + ".placer", pv.getPlacerUUID().toString());
			}
		}

		try {
			File file = getParkedVehiclesFile();
			file.getParentFile().mkdirs();
			config.save(file);
		} catch (IOException e) {
			plugin.getLogger().warning("[CarService] Failed to save parked_cars.yml: " + e.getMessage());
		}
	}

	private File getParkedVehiclesFile() {
		return new File(plugin.getDataFolder(), "parked_cars.yml");
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
