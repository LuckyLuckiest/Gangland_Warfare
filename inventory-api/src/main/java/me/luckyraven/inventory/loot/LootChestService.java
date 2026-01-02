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

	@Getter
	protected final JavaPlugin plugin;

	private final String                      prefix;
	private final Map<UUID, LootChestData>    registeredChests;
	private final Map<Location, UUID>         chestsByLocation;
	private final Map<UUID, LootChestSession> activeSessions;
	private final Map<String, LootTable>      lootTables;
	private final Map<String, LootTier>       tiers;

	// Cracking sessions - player UUID -> cracking session
	private final Map<UUID, CrackingSession> crackingSessions;

	@Getter
	private final HologramService      hologramService;
	@Getter
	private final ChestCooldownManager cooldownManager;

	@Getter
	private LootChestConfig                 config;
	@Setter
	private LootItemProvider                itemProvider;
	@Setter
	private Consumer<LootChestSession>      onSessionComplete;
	@Setter
	private Consumer<LootChestSession>      onSessionStart;
	@Setter
	private BiConsumer<LootChestData, Long> onChestCooldownTick;
	@Setter
	private Consumer<LootChestData>         onChestCooldownComplete;

	// Cracking minigame callbacks
	@Setter
	private Consumer<CrackingSession>         onCrackingStart;
	@Setter
	private BiConsumer<CrackingSession, Long> onCrackingTick;
	@Setter
	private Consumer<CrackingSession>         onCrackingSuccess;
	@Setter
	private Consumer<CrackingSession>         onCrackingFailed;

	public LootChestService(JavaPlugin plugin, HologramService hologramService, String prefix) {
		this.plugin = plugin;
		this.prefix = prefix;

		this.registeredChests = new ConcurrentHashMap<>();
		this.chestsByLocation = new ConcurrentHashMap<>();
		this.activeSessions   = new ConcurrentHashMap<>();
		this.lootTables       = new HashMap<>();
		this.tiers            = new LinkedHashMap<>();
		this.crackingSessions = new ConcurrentHashMap<>();

		// Initialize hologram and cooldown services
		this.hologramService = hologramService;
		this.cooldownManager = new ChestCooldownManager(plugin, hologramService);

		// Wire up cooldown callbacks
		this.cooldownManager.setOnCooldownTick((chest, remaining) -> {
			if (onChestCooldownTick != null) {
				onChestCooldownTick.accept(chest, remaining);
			}
		});
		this.cooldownManager.setOnCooldownComplete(chest -> {
			// Clear the persistent inventory when cooldown ends
			chest.clearInventory();

			if (onChestCooldownComplete != null) {
				onChestCooldownComplete.accept(chest);
			}
		});
	}

	// ... existing code ...

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
		chestsByLocation.put(normalizeLocation(chestData.getLocation()), chestData.getId());

		// Show initial hologram if chest is available
		if (!chestData.isOnCooldown() && !chestData.isLooted()) {
			cooldownManager.showAvailableHologram(chestData);
			return;
		}

		if (!chestData.isOnCooldown()) return;

		// Resume cooldown timer if chest was on cooldown
		long remaining = chestData.getRemainingCooldownSeconds();

		if (remaining <= 0) return;

		cooldownManager.startCooldown(chestData, remaining);
	}

	public void unregisterChest(UUID chestId) {
		LootChestData data = registeredChests.remove(chestId);

		if (data == null) return;

		chestsByLocation.remove(normalizeLocation(data.getLocation()));
		cooldownManager.cancelCooldown(chestId);
		cooldownManager.removeChestHologram(chestId);
	}

	public Optional<LootChestData> getChestAt(Location location) {
		UUID chestId = chestsByLocation.get(normalizeLocation(location));

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

	public boolean hasCrackingSession(Player player) {
		return crackingSessions.containsKey(player.getUniqueId());
	}

	public Optional<CrackingSession> getCrackingSession(Player player) {
		return Optional.ofNullable(crackingSessions.get(player.getUniqueId()));
	}

	/**
	 * Attempts to open a loot chest for a player. Opens immediately if available. If the chest has items remaining
	 * (even on cooldown), it can still be opened. Only blocks when empty AND on cooldown.
	 */
	public OpenResult tryOpenChest(Player player, LootChestData chestData) {
		if (itemProvider == null) {
			return OpenResult.NO_ITEM_PROVIDER;
		}

		if (hasActiveSession(player)) {
			return OpenResult.ALREADY_IN_SESSION;
		}

		if (hasCrackingSession(player)) {
			return OpenResult.ALREADY_IN_SESSION;
		}

		// Only block if chest is empty AND on cooldown
		if (chestData.isBlocked()) {
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

		// Check if cracking minigame is required
		if (chestData.isCrackingEnabled() && chestData.getCrackingTimeSeconds() > 0) {
			return startCrackingMinigame(player, chestData, lootTable, tier);
		}

		// Proceed to open directly
		return openChestDirectly(player, chestData, lootTable, tier);
	}

	/**
	 * Marks the cracking minigame as complete for a player
	 */
	public void completeCracking(Player player) {
		CrackingSession session = crackingSessions.get(player.getUniqueId());

		if (session == null) return;

		session.complete();
	}

	/**
	 * Cancels the cracking minigame for a player
	 */
	public void cancelCracking(Player player) {
		CrackingSession session = crackingSessions.remove(player.getUniqueId());

		if (session == null) return;

		session.cancel();
	}

	/**
	 * Closes the session. Starts cooldown only if an item was taken AND cooldown not already running.
	 */
	public void closeSession(Player player) {
		LootChestSession session = activeSessions.remove(player.getUniqueId());

		if (session == null) return;

		session.close();

		// Only start cooldown if player actually took an item AND cooldown not already started
		if (!session.hasItemBeenTaken()) return;

		LootChestData chestData = session.getChestData();

		// Only start cooldown if not already on cooldown
		if (chestData.isOnCooldown()) return;

		long cooldownTime = chestData.getRespawnTime();

		if (cooldownTime > 0) {
			cooldownManager.startCooldown(chestData, cooldownTime);
		}

		if (onSessionComplete != null) onSessionComplete.accept(session);
	}

	public void cancelSession(Player player) {
		LootChestSession session = activeSessions.remove(player.getUniqueId());

		if (session != null) {
			session.close(); // Still sync inventory state
		}

		cancelCracking(player);
	}

	public void clear() {
		activeSessions.values().forEach(LootChestSession::cancel);
		crackingSessions.values().forEach(CrackingSession::cancel);

		activeSessions.clear();
		crackingSessions.clear();
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

	/**
	 * Starts the cracking minigame for a chest
	 */
	private OpenResult startCrackingMinigame(Player player, LootChestData chestData, LootTable lootTable,
											 LootTier tier) {
		CrackingSession crackingSession = new CrackingSession(plugin, player, chestData, lootTable, tier,
															  chestData.getCrackingTimeSeconds());

		crackingSessions.put(player.getUniqueId(), crackingSession);

		// Start the cracking timer
		crackingSession.start(
				// On tick
				(session, remaining) -> {
					if (onCrackingTick != null) {
						onCrackingTick.accept(session, remaining);
					}
				},
				// On success (completed in time)
				session -> {
					crackingSessions.remove(player.getUniqueId());
					if (onCrackingSuccess != null) {
						onCrackingSuccess.accept(session);
					}
					// Open the chest
					openChestDirectly(player, chestData, lootTable, tier);
				},
				// On failure (time ran out)
				session -> {
					crackingSessions.remove(player.getUniqueId());
					if (onCrackingFailed != null) {
						onCrackingFailed.accept(session);
					}
				});

		if (onCrackingStart != null) {
			onCrackingStart.accept(crackingSession);
		}

		return OpenResult.CRACKING_STARTED;
	}

	/**
	 * Opens the chest directly without cracking minigame
	 */
	private OpenResult openChestDirectly(Player player, LootChestData chestData, LootTable lootTable, LootTier tier) {
		// Check if chest already has items (reusing existing inventory)
		List<ItemStack> items;
		if (chestData.getCurrentInventory() != null && !chestData.getCurrentInventory().isEmpty()) {
			// Use existing inventory
			items = chestData.getCurrentInventory();
		} else {
			// Generate new loot
			String tierId = tier != null ? tier.id() : "default";
			items = lootTable.generateLoot(tierId, itemProvider);
		}

		// Create inventory
		String        title = chestData.getDisplayName();
		NamespacedKey key   = new NamespacedKey(plugin, "loot_chest_" + chestData.getId().toString());
		InventoryHandler inventory = new InventoryHandler(title, chestData.getInventorySize(), key,
														  player.getUniqueId());

		// Create session and open immediately (no countdown)
		var session = new LootChestSession(player, chestData, inventory, items);

		activeSessions.put(player.getUniqueId(), session);

		if (onSessionStart != null) onSessionStart.accept(session);

		// Open the chest immediately
		session.open();

		return OpenResult.SUCCESS;
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

	private Location normalizeLocation(Location location) {
		return new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
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
		NO_ITEM_PROVIDER,
		CRACKING_STARTED
	}

}
