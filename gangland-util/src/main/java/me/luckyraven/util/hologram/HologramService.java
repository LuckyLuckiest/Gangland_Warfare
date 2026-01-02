package me.luckyraven.util.hologram;

import lombok.Getter;
import me.luckyraven.util.timer.RepeatingTimer;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Service for managing holograms throughout the plugin
 */
public class HologramService {

	private final JavaPlugin plugin;

	@Getter
	private final Map<UUID, Hologram>       holograms;
	private final Map<UUID, RepeatingTimer> updateTasks;
	private final Map<Location, Hologram>   hologramsByLocation;

	public HologramService(JavaPlugin plugin) {
		this.plugin              = plugin;
		this.holograms           = new ConcurrentHashMap<>();
		this.updateTasks         = new ConcurrentHashMap<>();
		this.hologramsByLocation = new ConcurrentHashMap<>();
	}

	/**
	 * Creates and spawns a new hologram at the specified location
	 */
	public Hologram createHologram(Location location, String... lines) {
		Hologram hologram = new Hologram(location);
		hologram.spawn(lines);

		holograms.put(hologram.getId(), hologram);
		hologramsByLocation.put(location, hologram);

		return hologram;
	}

	/**
	 * Creates a hologram with automatic updates at a fixed interval
	 *
	 * @param location the location for the hologram
	 * @param updateIntervalTicks how often to update (in ticks, 20 ticks = 1 second)
	 * @param updater function that provides new lines for the hologram
	 */
	public Hologram createUpdatingHologram(Location location, long updateIntervalTicks,
										   BiConsumer<Hologram, Long> updater, String... initialLines) {
		Hologram hologram = createHologram(location, initialLines);

		RepeatingTimer task = new RepeatingTimer(plugin, updateIntervalTicks, updateIntervalTicks, timer -> {
			if (!hologram.isSpawned()) {
				cancelUpdateTask(hologram.getId());
				return;
			}
			updater.accept(hologram, System.currentTimeMillis());
		});

		updateTasks.put(hologram.getId(), task);
		return hologram;
	}

	/**
	 * Gets a hologram by its ID
	 */
	public Optional<Hologram> getHologram(UUID id) {
		return Optional.ofNullable(holograms.get(id));
	}

	/**
	 * Gets a hologram at a specific location
	 */
	public Optional<Hologram> getHologramAt(Location location) {
		return Optional.ofNullable(hologramsByLocation.get(location));
	}

	/**
	 * Removes and despawns a hologram
	 */
	public void removeHologram(UUID id) {
		Hologram hologram = holograms.remove(id);
		if (hologram == null) return;

		cancelUpdateTask(id);
		hologramsByLocation.remove(hologram.getBaseLocation());
		hologram.despawn();
	}

	/**
	 * Removes a hologram at a specific location
	 */
	public void removeHologramAt(Location location) {
		Hologram hologram = hologramsByLocation.get(location);
		if (hologram == null) return;

		removeHologram(hologram.getId());
	}

	/**
	 * Cancels the update task for a hologram
	 */
	public void cancelUpdateTask(UUID hologramId) {
		RepeatingTimer task = updateTasks.remove(hologramId);

		if (task == null) return;

		task.cancel();
	}

	/**
	 * Clears all holograms
	 */
	public void clear() {
		updateTasks.values().forEach(RepeatingTimer::stop);
		updateTasks.clear();

		holograms.values().forEach(Hologram::despawn);
		holograms.clear();
		hologramsByLocation.clear();
	}

}
