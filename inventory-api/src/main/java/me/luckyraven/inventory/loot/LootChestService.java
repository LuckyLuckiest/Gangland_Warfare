package me.luckyraven.inventory.loot;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.loot.data.*;
import me.luckyraven.inventory.loot.item.LootItemProvider;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.hologram.HologramService;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Manages all loot chests, sessions, and configuration
 */
public abstract class LootChestService {

	private final JavaPlugin                  plugin;
	private final String                      prefix;
	private final Map<UUID, LootChestData>    registeredChests;
	private final Map<Location, UUID>         chestsByLocation;
	private final Map<UUID, LootChestSession> activeSessions;   // Player sessions (for opening animation)
	private final Map<String, LootTable>      lootTables;
	private final Map<String, LootTier>       tiers;

	@Getter
	private final HologramService      hologramService;
	@Getter
	private final ChestCooldownManager cooldownManager;

	@Getter
	private LootChestConfig                 config;
	@Setter
	private LootItemProvider                itemProvider;
	@Setter
	private Consumer<LootChestSession>      onCountdownTick;
	@Setter
	private Consumer<LootChestSession>      onSessionComplete;
	@Setter
	private Consumer<LootChestSession>      onSessionStart;
	@Setter
	private BiConsumer<LootChestData, Long> onChestCooldownTick;
	@Setter
	private Consumer<LootChestData>         onChestCooldownComplete;

	public LootChestService(JavaPlugin plugin, String prefix) {
		this.plugin = plugin;
		this.prefix = prefix;

		this.registeredChests = new ConcurrentHashMap<>();
		this.chestsByLocation = new ConcurrentHashMap<>();
		this.activeSessions   = new ConcurrentHashMap<>();
		this.lootTables       = new HashMap<>();
		this.tiers            = new LinkedHashMap<>();

		// Initialize hologram and cooldown services
		this.hologramService = new HologramService(plugin);
		this.cooldownManager = new ChestCooldownManager(plugin, hologramService);

		// Wire up cooldown callbacks
		this.cooldownManager.setOnCooldownTick((chest, remaining) -> {
			if (onChestCooldownTick != null) {
				onChestCooldownTick.accept(chest, remaining);
			}
		});
		this.cooldownManager.setOnCooldownComplete(chest -> {
			if (onChestCooldownComplete != null) {
				onChestCooldownComplete.accept(chest);
			}
		});
	}

	public void setConfig(LootChestConfig config) {
		this.config = config;

		this.tiers.clear();
		config.getTiers().values().forEach(this::registerTier);

		this.lootTables.clear();
		config.getLootTables().values().forEach(this::registerLootTable);
	}

	public void registerTier(LootTier tier) {
		tiers.put(tier.id(), tier);
	}

	public void registerLootTable(LootTable lootTable) {
		lootTables.put(lootTable.getId(), lootTable);
	}

	public Optional<LootTier> getTier(String tierId) {
		return Optional.ofNullable(tiers.get(tierId));
	}

	public Optional<LootTable> getLootTable(String tableId) {
		return Optional.ofNullable(lootTables.get(tableId));
	}

	public void registerChest(LootChestData chestData) {
		registeredChests.put(chestData.getId(), chestData);
		chestsByLocation.put(chestData.getLocation(), chestData.getId());

		// Show initial hologram if chest is available
		if (!chestData.isOnCooldown() && !chestData.isLooted()) {
			cooldownManager.showAvailableHologram(chestData);
		} else if (chestData.isOnCooldown()) {
			// Resume cooldown timer if chest was on cooldown
			long remaining = chestData.getRemainingCooldownSeconds();
			if (remaining > 0) {
				cooldownManager.startCooldown(chestData, remaining);
			}
		}
	}

	public void unregisterChest(UUID chestId) {
		LootChestData data = registeredChests.remove(chestId);

		if (data == null) return;

		chestsByLocation.remove(data.getLocation());
		cooldownManager.cancelCooldown(chestId);
		cooldownManager.removeChestHologram(chestId);
	}

	public Optional<LootChestData> getChestAt(Location location) {
		UUID chestId = chestsByLocation.get(location);

		if (chestId == null) return Optional.empty();

		return Optional.ofNullable(registeredChests.get(chestId));
	}

	public Optional<LootChestData> getChest(UUID chestId) {
		return Optional.ofNullable(registeredChests.get(chestId));
	}

	public boolean hasActiveSession(Player player) {
		return activeSessions.containsKey(player.getUniqueId());
	}

	public Optional<LootChestSession> getActiveSession(Player player) {
		return Optional.ofNullable(activeSessions.get(player.getUniqueId()));
	}

	/**
	 * Attempts to open a loot chest for a player. Players can open chests that are not on global cooldown. Once loot is
	 * taken, a global cooldown starts for that chest.
	 */
	public OpenResult tryOpenChest(Player player, LootChestData chestData) {
		if (itemProvider == null) {
			return OpenResult.NO_ITEM_PROVIDER;
		}

		if (hasActiveSession(player)) {
			return OpenResult.ALREADY_IN_SESSION;
		}

		// Check if chest is on global cooldown
		if (chestData.isOnCooldown()) {
			return OpenResult.ON_COOLDOWN;
		}

		// Check tier requirements
		LootTier tier = chestData.getTier();
		if (tier != null) {
			OpenResult unlockResult = checkUnlockRequirement(player, tier);
			if (unlockResult != OpenResult.SUCCESS) {
				return unlockResult;
			}
		}

		// Get loot table
		LootTable lootTable = lootTables.get(chestData.getLootTableId());
		if (lootTable == null) {
			return OpenResult.INVALID_LOOT_TABLE;
		}

		// Generate loot using the provider
		String          tierId = tier != null ? tier.id() : "default";
		List<ItemStack> items  = lootTable.generateLoot(tierId, itemProvider);

		// Create inventory
		String        title = chestData.getDisplayName();
		NamespacedKey key   = new NamespacedKey(plugin, "loot_chest_" + chestData.getId().toString());
		InventoryHandler inventory = new InventoryHandler(title, chestData.getInventorySize(), key,
														  player.getUniqueId());

		// Create session with countdown (for opening animation)
		long countdownTime = config != null ? config.getDefaultCountdownTime() : 3L;

		var session = new LootChestSession(plugin, player, chestData, inventory, items, countdownTime, timer -> {
			if (onCountdownTick == null) return;

			onCountdownTick.accept(timer);
		}, completedSession -> {
			if (onSessionComplete == null) return;

			onSessionComplete.accept(completedSession);
		});

		activeSessions.put(player.getUniqueId(), session);

		if (onSessionStart != null) onSessionStart.accept(session);
		session.start(false);

		return OpenResult.SUCCESS;
	}

	/**
	 * Closes the session and starts the global chest cooldown
	 */
	public void closeSession(Player player) {
		LootChestSession session = activeSessions.remove(player.getUniqueId());

		if (session == null) return;

		session.close();

		// Start global cooldown for this chest
		LootChestData chestData    = session.getChestData();
		long          cooldownTime = chestData.getRespawnTime(); // Use respawnTime as cooldown duration

		if (cooldownTime > 0) {
			cooldownManager.startCooldown(chestData, cooldownTime);
		}
	}

	public void cancelSession(Player player) {
		LootChestSession session = activeSessions.remove(player.getUniqueId());

		if (session == null) return;

		session.cancel();
		// Don't start cooldown on cancelled sessions
	}

	public void clear() {
		activeSessions.values().forEach(LootChestSession::cancel);

		activeSessions.clear();
		registeredChests.clear();
		chestsByLocation.clear();
		lootTables.clear();
		tiers.clear();

		cooldownManager.clear();
		hologramService.clear();
	}

	public Collection<LootChestData> getAllChests() {
		return Collections.unmodifiableCollection(registeredChests.values());
	}

	public Collection<LootTier> getAllTiers() {
		return Collections.unmodifiableCollection(tiers.values());
	}

	public Collection<LootTable> getAllLootTables() {
		return Collections.unmodifiableCollection(lootTables.values());
	}

	private OpenResult checkUnlockRequirement(Player player, LootTier tier) {
		return switch (tier.unlockRequirement()) {
			case NONE -> OpenResult.SUCCESS;
			case LOCKPICK -> {
				String itemId = tier.unlockItemId() != null ? tier.unlockItemId() : "lockpick";

				yield hasRequiredItem(player, itemId) ? OpenResult.SUCCESS : OpenResult.REQUIRES_LOCKPICK;
			}
			case KEY -> {
				String itemId = tier.unlockItemId() != null ? tier.unlockItemId() : "key_" + tier.id();

				yield hasRequiredItem(player, itemId) ? OpenResult.SUCCESS : OpenResult.REQUIRES_KEY;
			}
			case PERMISSION -> {
				String permission = prefix + ".lootchest.tier." + tier.id();

				yield player.hasPermission(permission) ? OpenResult.SUCCESS : OpenResult.NO_PERMISSION;
			}
		};
	}

	private boolean hasRequiredItem(Player player, String itemKey) {
		for (ItemStack item : player.getInventory().getContents()) {
			if (item == null) continue;

			ItemBuilder builder = new ItemBuilder(item);

			if (!(builder.hasNBTTag("loot_key") && itemKey.equals(builder.getStringTagData("loot_key")))) continue;

			return true;
		}

		return false;
	}

	public enum OpenResult {
		SUCCESS,
		ALREADY_IN_SESSION,
		ALREADY_LOOTED,
		ON_COOLDOWN,
		REQUIRES_LOCKPICK,
		REQUIRES_KEY,
		NO_PERMISSION,
		INVALID_LOOT_TABLE,
		INVALID_CHEST,
		NO_ITEM_PROVIDER
	}

}
