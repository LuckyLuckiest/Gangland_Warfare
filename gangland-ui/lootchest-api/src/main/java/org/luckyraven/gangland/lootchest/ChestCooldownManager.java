package org.luckyraven.gangland.lootchest;

import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.luckyraven.keystone.util.ChatUtil;
import org.luckyraven.gangland.hologram.Hologram;
import org.luckyraven.gangland.hologram.HologramService;
import org.luckyraven.gangland.item.ItemParser;
import org.luckyraven.gangland.lootchest.config.LootChestMessagesProvider;
import org.luckyraven.gangland.lootchest.data.LootChestData;
import org.luckyraven.gangland.lootchest.data.LootTier;

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

	// Spawn offset for the floating icon above the chest. The icon is a gravity-less
	// dropped Item entity whose visual centre sits ~0.2 above its spawn y, so this
	// places it just above the top face of the chest block — beneath the hologram
	// text lines. The client renders its bob/spin naturally.
	private static final double ICON_Y_OFFSET = 0.95;

	private final JavaPlugin            plugin;
	private final HologramService       hologramService;
	private final Map<UUID, BukkitTask> cooldownTasks;
	private final Map<UUID, Hologram>   chestHolograms;
	private final Map<UUID, Item>       chestIcons;

	@Setter
	private BiConsumer<LootChestData, Long> onCooldownTick;
	@Setter
	private Consumer<LootChestData>         onCooldownComplete;

	@Setter
	private LootChestMessagesProvider messagesProvider;

	@Setter
	private ItemParser itemParser;

	@Setter
	private double hologramYOffset = 1.5;

	public ChestCooldownManager(JavaPlugin plugin, HologramService hologramService) {
		this.plugin          = plugin;
		this.hologramService = hologramService;

		this.cooldownTasks  = new ConcurrentHashMap<>();
		this.chestHolograms = new ConcurrentHashMap<>();
		this.chestIcons     = new ConcurrentHashMap<>();
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

		String timerText = formatTime(remainingSeconds);
		String statusText = messagesProvider != null ?
		                    messagesProvider.getHologramCooldownStatus() :
		                    ChatUtil.color("&c&lON COOLDOWN");

		if (hologram == null || !hologram.isSpawned()) {
			Location holoLocation = chestData.getLocation().clone().add(0.5, hologramYOffset, 0.5);
			hologram = hologramService.createHologram(holoLocation, statusText, timerText);
			chestHolograms.put(chestId, hologram);
		} else {
			hologram.update(statusText, timerText);
		}
	}

	/**
	 * Shows an "available" hologram for a chest. For tiered chests with a lock requirement, the hologram advertises the
	 * requirement (and spawns a floating icon of the required item) so players can tell at a glance what the chest
	 * needs before they right-click it.
	 */
	public void showAvailableHologram(LootChestData chestData) {
		UUID chestId = chestData.getId();
		removeChestHologram(chestId);

		Location holoLocation = chestData.getLocation().clone().add(0.5, hologramYOffset, 0.5);
		LootTier tier         = chestData.getTier();

		String[] lines = buildAvailableHologramLines(chestData, tier);

		Hologram hologram = hologramService.createHologram(holoLocation, lines);
		chestHolograms.put(chestId, hologram);

		// Only the "locked & needs-item" state shows a floating icon.
		if (shouldShowIcon(chestData, tier)) {
			ItemStack icon = resolveUnlockIcon(tier);
			if (icon != null) {
				spawnIcon(chestData, icon);
			}
		}
	}

	/**
	 * Removes the hologram for a chest
	 */
	public void removeChestHologram(UUID chestId) {
		Hologram hologram = chestHolograms.remove(chestId);
		if (hologram != null) {
			hologram.despawn();
		}

		removeChestIcon(chestId);
	}

	/**
	 * Clears all cooldowns and holograms
	 */
	public void clear() {
		cooldownTasks.values().forEach(BukkitTask::cancel);
		cooldownTasks.clear();

		chestHolograms.values().forEach(Hologram::despawn);
		chestHolograms.clear();

		chestIcons.values().forEach(Item::remove);
		chestIcons.clear();
	}

	/**
	 * Returns {@code true} if the given item entity is currently tracked as a loot-chest floating icon. Used by
	 * listeners to block mob pickup (Spigot has no {@code setCanMobPickup}).
	 */
	public boolean isChestIcon(Item item) {
		if (item == null) return false;
		return chestIcons.containsValue(item);
	}

	private String[] buildAvailableHologramLines(LootChestData chestData, LootTier tier) {
		if (tier == null || tier.unlockRequirement() == LootTier.UnlockRequirement.NONE) {
			String availableStatus = messagesProvider != null ?
			                         messagesProvider.getHologramAvailableStatus() :
			                         ChatUtil.color("&a&lAVAILABLE");
			String availableHint = messagesProvider != null ?
			                       messagesProvider.getHologramAvailableHint() :
			                       ChatUtil.color("&7Right-click to open");

			return new String[]{availableStatus, availableHint};
		}

		String tierLabel = messagesProvider != null ?
		                   messagesProvider.getHologramTierLabel(tier) :
		                   ChatUtil.color(tier.displayName());
		String hint = messagesProvider != null ?
		              messagesProvider.getHologramAvailableHint() :
		              ChatUtil.color("&7Right-click to open");

		String middle = switch (tier.unlockRequirement()) {
			case PERMISSION -> messagesProvider != null ?
			                   messagesProvider.getHologramLockedPermission() :
			                   ChatUtil.color("&c\uD83D\uDD12 Permission Only");
			case LOCKPICK, KEY -> {
				if (chestData.isUnlocked()) {
					yield messagesProvider != null ?
					      messagesProvider.getHologramUnlocked() :
					      ChatUtil.color("&a\uD83D\uDD13 Unlocked");
				}
				String itemDisplay = tier.unlockItemDisplay() != null ?
				                     tier.unlockItemDisplay() :
				                     humaniseUnlockId(tier.unlockItemId());
				yield messagesProvider != null ?
				      messagesProvider.getHologramLockedRequires(itemDisplay) :
				      ChatUtil.color("&c\uD83D\uDD12 Requires &e" + itemDisplay);
			}
			default -> hint;
		};

		return new String[]{tierLabel, middle, hint};
	}

	private boolean shouldShowIcon(LootChestData chestData, LootTier tier) {
		if (tier == null) return false;
		if (chestData.isUnlocked()) return false;

		return tier.unlockRequirement() == LootTier.UnlockRequirement.LOCKPICK
		       || tier.unlockRequirement() == LootTier.UnlockRequirement.KEY;
	}

	private ItemStack resolveUnlockIcon(LootTier tier) {
		if (itemParser == null) return null;

		String iconString = tier.floatingItemIcon();
		if (iconString == null || iconString.isBlank()) {
			if (tier.unlockItemId() == null || tier.unlockItemId().isBlank()) return null;
			iconString = "unique:" + tier.unlockItemId();
		}

		try {
			return itemParser.parse(iconString);
		} catch (RuntimeException ex) {
			return null;
		}
	}

	private void spawnIcon(LootChestData chestData, ItemStack icon) {
		Location base  = chestData.getLocation();
		World    world = base.getWorld();
		if (world == null) return;

		Location iconLocation = base.clone().add(0.5, ICON_Y_OFFSET, 0.5);
		Item     item         = world.dropItem(iconLocation, icon);

		item.setGravity(false);
		item.setVelocity(new Vector(0, 0, 0));
		item.setPickupDelay(Integer.MAX_VALUE);
		item.setInvulnerable(true);
		item.setSilent(true);
		item.setPersistent(false);
		item.setUnlimitedLifetime(true);

		chestIcons.put(chestData.getId(), item);
	}

	private void removeChestIcon(UUID chestId) {
		Item item = chestIcons.remove(chestId);
		if (item != null && !item.isDead()) {
			item.remove();
		}
	}

	private String humaniseUnlockId(String id) {
		if (id == null || id.isEmpty()) return "item";

		String replaced = id.replace('_', ' ');
		return Character.toUpperCase(replaced.charAt(0)) + replaced.substring(1);
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
			return ChatUtil.color("&e" + seconds + "s");
		} else if (seconds < 3600) {
			long minutes = seconds / 60;
			long secs    = seconds % 60;
			return ChatUtil.color("&e" + minutes + "m " + secs + "s");
		} else {
			long hours   = seconds / 3600;
			long minutes = (seconds % 3600) / 60;
			return ChatUtil.color("&e" + hours + "h " + minutes + "m");
		}
	}
}