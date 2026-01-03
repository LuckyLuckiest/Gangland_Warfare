package me.luckyraven.loot;

import lombok.Setter;
import me.luckyraven.loot.data.LootChestData;
import me.luckyraven.util.hologram.Hologram;
import me.luckyraven.util.hologram.HologramService;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Manages global cooldowns for loot chests. Unlike player sessions, these cooldowns are chest-specific and persist even
 * when players leave.
 */
public class ChestCooldownManager {

	private final JavaPlugin            plugin;
	private final HologramService       hologramService;
	private final Map<UUID, BukkitTask> cooldownTasks;
	private final Map<UUID, Hologram>   chestHolograms;

	@Setter
	private BiConsumer<LootChestData, Long> onCooldownTick;
	@Setter
	private Consumer<LootChestData>         onCooldownComplete;

	@Setter
	private double hologramYOffset = 1.5;

	public ChestCooldownManager(JavaPlugin plugin, HologramService hologramService) {
		this.plugin          = plugin;
		this.hologramService = hologramService;

		this.cooldownTasks  = new ConcurrentHashMap<>();
		this.chestHolograms = new ConcurrentHashMap<>();
	}

	/**
	 * Starts a global cooldown for a chest
	 *
	 * @param chestData the chest to start cooldown for
	 * @param cooldownSeconds duration in seconds
	 */
	public void startCooldown(LootChestData chestData, long cooldownSeconds) {
		UUID chestId = chestData.getId();

		// Cancel any existing cooldown
		cancelCooldown(chestId);

		// Set the cooldown on the chest data
		chestData.startCooldown(cooldownSeconds);

		// Create the cooldown task
		BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
			long remaining = chestData.getRemainingCooldownSeconds();

			if (remaining <= 0) {
				completeCooldown(chestData);
				return;
			}

			if (onCooldownTick != null) {
				onCooldownTick.accept(chestData, remaining);
			}

			// Update hologram
			updateCooldownHologram(chestData, remaining);

		}, 0L, 20L);

		cooldownTasks.put(chestId, task);
	}

	/**
	 * Checks if a chest is currently on cooldown
	 */
	public boolean isOnCooldown(UUID chestId, LootChestData chestData) {
		return chestData.isOnCooldown();
	}

	/**
	 * Cancels a chest's cooldown
	 */
	public void cancelCooldown(UUID chestId) {
		BukkitTask task = cooldownTasks.remove(chestId);

		if (task != null) {
			task.cancel();
		}

		removeChestHologram(chestId);
	}

	/**
	 * Creates or updates the hologram for a chest during cooldown
	 */
	public void updateCooldownHologram(LootChestData chestData, long remainingSeconds) {
		UUID     chestId  = chestData.getId();
		Hologram hologram = chestHolograms.get(chestId);

		String timerText  = formatTime(remainingSeconds);
		String statusText = "§c§lON COOLDOWN";

		if (hologram == null || !hologram.isSpawned()) {
			Location holoLocation = chestData.getLocation().clone().add(0.5, hologramYOffset, 0.5);
			hologram = hologramService.createHologram(holoLocation, statusText, timerText);
			chestHolograms.put(chestId, hologram);
		} else {
			hologram.update(statusText, timerText);
		}
	}

	/**
	 * Shows an "available" hologram for a chest
	 */
	public void showAvailableHologram(LootChestData chestData) {
		UUID chestId = chestData.getId();
		removeChestHologram(chestId);

		Location holoLocation = chestData.getLocation().clone().add(0.5, hologramYOffset, 0.5);
		Hologram hologram     = hologramService.createHologram(holoLocation, "§a§lAVAILABLE", "§7Right-click to open");

		chestHolograms.put(chestId, hologram);
	}

	/**
	 * Removes the hologram for a chest
	 */
	public void removeChestHologram(UUID chestId) {
		Hologram hologram = chestHolograms.remove(chestId);

		if (hologram == null) return;

		hologram.despawn();
	}

	/**
	 * Clears all cooldowns and holograms
	 */
	public void clear() {
		cooldownTasks.values().forEach(BukkitTask::cancel);
		cooldownTasks.clear();

		chestHolograms.values().forEach(Hologram::despawn);
		chestHolograms.clear();
	}

	private void completeCooldown(LootChestData chestData) {
		UUID chestId = chestData.getId();

		// Cancel the task
		BukkitTask task = cooldownTasks.remove(chestId);

		if (task != null) {
			task.cancel();
		}

		// Respawn the chest
		chestData.respawn();

		// Update hologram to show available
		showAvailableHologram(chestData);

		// Notify callback
		if (onCooldownComplete != null) {
			onCooldownComplete.accept(chestData);
		}
	}

	private String formatTime(long seconds) {
		if (seconds < 60) {
			return "§e" + seconds + "s";
		} else if (seconds < 3600) {
			long minutes = seconds / 60;
			long secs    = seconds % 60;
			return "§e" + minutes + "m " + secs + "s";
		} else {
			long hours   = seconds / 3600;
			long minutes = (seconds % 3600) / 60;
			return "§e" + hours + "h " + minutes + "m";
		}
	}
}