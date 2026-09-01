package org.luckyraven.gangland.lootchest;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.gangland.core.configuration.SoundConfiguration;
import org.luckyraven.gangland.hologram.HologramService;
import org.luckyraven.gangland.inventory.InventoryHandler;
import org.luckyraven.gangland.item.ItemParser;
import org.luckyraven.gangland.lootchest.config.LootChestConfig;
import org.luckyraven.gangland.lootchest.config.LootChestMessagesProvider;
import org.luckyraven.gangland.lootchest.data.*;
import org.luckyraven.gangland.lootchest.events.cracking.*;
import org.luckyraven.gangland.lootchest.events.lootchest.LootChestCloseEvent;
import org.luckyraven.gangland.lootchest.events.lootchest.LootChestCooldownCompleteEvent;
import org.luckyraven.gangland.lootchest.events.lootchest.LootChestDuringCooldownEvent;
import org.luckyraven.gangland.lootchest.events.lootchest.LootChestOpenEvent;
import org.luckyraven.gangland.lootchest.handler.cracking.CrackingFailedHandler;
import org.luckyraven.gangland.lootchest.handler.cracking.CrackingStartHandler;
import org.luckyraven.gangland.lootchest.handler.cracking.CrackingSuccessHandler;
import org.luckyraven.gangland.lootchest.handler.cracking.CrackingTickHandler;
import org.luckyraven.gangland.lootchest.handler.lootchest.ChestCooldownCompleteHandler;
import org.luckyraven.gangland.lootchest.handler.lootchest.ChestCooldownTickHandler;
import org.luckyraven.gangland.lootchest.handler.lootchest.SessionCompleteHandler;
import org.luckyraven.gangland.lootchest.handler.lootchest.SessionStartHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all loot chests, sessions, and configuration
 */
public abstract class LootChestService {

	@Getter
	protected final JavaPlugin plugin;

	private final String                           prefix;
	private final Map<UUID, LootChestData>         registeredChests;
	private final Map<Location, UUID>              chestsByLocation;
	private final Map<UUID, LootChestSession>      activeSessions;
	private final Map<String, LootTable>           lootTables;
	private final Map<String, LootTier>            tiers;
	private final Map<UUID, CrackingSession>       crackingSessions;
	private final Map<UUID, Set<LootChestSession>> activeSessionsByChest;
	private final Map<UUID, InventoryHandler>      sharedChestInventories;

	@Getter
	private final HologramService      hologramService;
	@Getter
	private final ChestCooldownManager cooldownManager;

	@Getter
	private final SessionCompleteHandler       sessionCompleteHandler;
	@Getter
	private final SessionStartHandler          sessionStartHandler;
	@Getter
	private final ChestCooldownTickHandler     chestCooldownTickHandler;
	@Getter
	private final ChestCooldownCompleteHandler chestCooldownCompleteHandler;
	@Getter
	private final CrackingStartHandler         crackingStartHandler;
	@Getter
	private final CrackingTickHandler          crackingTickHandler;
	@Getter
	private final CrackingSuccessHandler       crackingSuccessHandler;
	@Getter
	private final CrackingFailedHandler        crackingFailedHandler;

	@Getter
	private LootChestConfig           config;
	@Getter
	private ItemParser                itemParser;
	@Getter
	private LootChestMessagesProvider messagesProvider;

	public LootChestService(JavaPlugin plugin, HologramService hologramService, String prefix,
	                        @Nullable ItemParser itemParser,
	                        @Nullable LootChestMessagesProvider messagesProvider) {
		this.plugin           = plugin;
		this.prefix           = prefix;
		this.itemParser       = itemParser;
		this.messagesProvider = messagesProvider;

		// Let the cooldown manager resolve unlock-icon ItemStacks for locked tiers.
		// Null itemParser → icons are skipped silently.

		this.registeredChests       = new ConcurrentHashMap<>();
		this.chestsByLocation       = new ConcurrentHashMap<>();
		this.activeSessions         = new ConcurrentHashMap<>();
		this.lootTables             = new HashMap<>();
		this.tiers                  = new LinkedHashMap<>();
		this.crackingSessions       = new ConcurrentHashMap<>();
		this.activeSessionsByChest  = new ConcurrentHashMap<>();
		this.sharedChestInventories = new ConcurrentHashMap<>();

		// Initialize handler chains
		this.sessionCompleteHandler       = new SessionCompleteHandler();
		this.sessionStartHandler          = new SessionStartHandler();
		this.chestCooldownTickHandler     = new ChestCooldownTickHandler();
		this.chestCooldownCompleteHandler = new ChestCooldownCompleteHandler();
		this.crackingStartHandler         = new CrackingStartHandler();
		this.crackingTickHandler          = new CrackingTickHandler();
		this.crackingSuccessHandler       = new CrackingSuccessHandler();
		this.crackingFailedHandler        = new CrackingFailedHandler();

		// Initialize hologram and cooldown services
		this.hologramService = hologramService;
		this.cooldownManager = new ChestCooldownManager(plugin, hologramService);
		this.cooldownManager.setItemParser(itemParser);

		if (messagesProvider != null) {
			this.cooldownManager.setMessagesProvider(messagesProvider);
		}

		// Wire up cooldown callbacks
		this.cooldownManager.setOnCooldownTick((lootChestData, remaining) -> {
			chestCooldownTickHandler.handle(lootChestData);
		});
		this.cooldownManager.setOnCooldownComplete(lootChestData -> {
			// Close all active sessions for this chest before clearing inventory
			closeAllSessionsForChest(lootChestData.getId());

			// Clear the persistent inventory when cooldown ends
			lootChestData.clearInventory();

			chestCooldownCompleteHandler.handle(lootChestData);
		});

		callEvents();
		addSounds();
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
		if (itemParser == null) {
			return OpenResult.NO_ITEM_PARSER;
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
			OpenResult unlockResult = checkUnlockRequirement(player, tier, chestData);
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

		LootChestData chestData = session.getChestData();
		UUID          chestId   = chestData.getId();

		// Remove from chest's active sessions
		Set<LootChestSession> chestSessions = activeSessionsByChest.get(chestId);
		if (chestSessions != null) {
			chestSessions.remove(session);

			// If no more players viewing this chest, clean up shared inventory
			if (chestSessions.isEmpty()) {
				activeSessionsByChest.remove(chestId);
				sharedChestInventories.remove(chestId);
			}
		}

		session.close();

		// Always fire close event and play closing sound, regardless of whether items were taken
		sessionCompleteHandler.handle(session);

		// Start the cooldown whenever the session closes — the chest refreshes on every close,
		// whether or not the player actually took any items. No-op if another viewer already
		// started the cooldown via their own close.
		if (chestData.isOnCooldown()) return;

		long cooldownTime = chestData.getRespawnTime();

		if (cooldownTime > 0) {
			cooldownManager.startCooldown(chestData, cooldownTime);
		}
	}

	public void cancelSession(Player player) {
		LootChestSession session = activeSessions.remove(player.getUniqueId());

		if (session != null) {
			session.close(); // Still sync inventory state
		}

		cancelCracking(player);
	}

	/**
	 * Closes all active sessions for a specific chest. Used when cooldown ends to force-close inventories for all
	 * viewers.
	 *
	 * @param chestId the chest UUID
	 */
	public void closeAllSessionsForChest(UUID chestId) {
		// Find all sessions viewing this chest
		List<UUID> playersToClose = new ArrayList<>();

		for (Map.Entry<UUID, LootChestSession> entry : activeSessions.entrySet()) {
			LootChestSession session = entry.getValue();

			if (!session.getChestData().getId().equals(chestId)) continue;

			playersToClose.add(entry.getKey());
		}

		// Close each session and force-close the player's inventory
		for (UUID playerId : playersToClose) {
			LootChestSession session = activeSessions.remove(playerId);
			if (session == null) continue;

			Player player = session.getPlayer();

			// Force close the inventory on the main thread
			if (player != null && player.isOnline()) {
				plugin.getServer().getScheduler().runTask(plugin, player::closeInventory);
			}

			// Mark session as closed without triggering cooldown logic
			session.cancel();
		}

		// Clean up shared inventory and session tracking for this chest
		sharedChestInventories.remove(chestId);
		activeSessionsByChest.remove(chestId);
	}

	public void clear() {
		cancelSessions();
		clearChests();
		lootTables.clear();
		tiers.clear();
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
	 * Cancels all active loot and cracking sessions.
	 */
	protected void cancelSessions() {
		activeSessions.values().forEach(LootChestSession::cancel);
		crackingSessions.values().forEach(CrackingSession::cancel);

		activeSessions.clear();
		crackingSessions.clear();
	}

	/**
	 * Clears all registered chest instances, locations, cooldowns, and holograms. Does <b>not</b> clear config data
	 * (tiers, loot tables).
	 */
	protected void clearChests() {
		registeredChests.clear();
		chestsByLocation.clear();

		cooldownManager.clear();
		hologramService.clear();
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
				(session, remaining) -> crackingTickHandler.handle(session),
				// On success (completed in time)
				session -> {
					crackingSessions.remove(player.getUniqueId());
					crackingSuccessHandler.handle(session);
					// Open the chest
					openChestDirectly(player, chestData, lootTable, tier);
				},
				// On failure (time ran out)
				session -> {
					crackingSessions.remove(player.getUniqueId());
					crackingFailedHandler.handle(session);
				});

		crackingStartHandler.handle(crackingSession);

		return OpenResult.CRACKING_STARTED;
	}

	private void addSounds() {
		sessionStartHandler.addHandler(session -> {
			Player player = session.getPlayer();

			var soundConfig = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA, config.getOpeningSound(),
			                                         1.0f, 1.0f);

			soundConfig.playSound(player);
		});

		sessionCompleteHandler.addHandler(session -> {
			Player player = session.getPlayer();

			var soundConfig = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA, config.getClosingSound(),
			                                         1.0f, 1.0f);

			soundConfig.playSound(player);
		});
	}

	private void callEvents() {
		// loot chest session
		sessionStartHandler.addHandler(lootChestSession -> {
			var event = new LootChestOpenEvent(lootChestSession.getChestData(), lootChestSession);
			Bukkit.getPluginManager().callEvent(event);
		});

		sessionCompleteHandler.addHandler(lootChestSession -> {
			var event = new LootChestCloseEvent(lootChestSession.getChestData(), lootChestSession);
			Bukkit.getPluginManager().callEvent(event);
		});

		chestCooldownTickHandler.addHandler(lootChestData -> {
			var event = new LootChestDuringCooldownEvent(lootChestData);
			Bukkit.getPluginManager().callEvent(event);
		});

		chestCooldownCompleteHandler.addHandler(lootChestData -> {
			var event = new LootChestCooldownCompleteEvent(lootChestData);
			Bukkit.getPluginManager().callEvent(event);
		});

		// cracking session
		crackingStartHandler.addHandler(crackingSession -> {
			var event = new LootChestCrackingStartEvent(crackingSession.getChestData(), crackingSession);
			Bukkit.getPluginManager().callEvent(event);
		});

		crackingTickHandler.addHandler(crackingSession -> {
			var event = new LootChestDuringCrackingEvent(crackingSession.getChestData(), crackingSession);
			Bukkit.getPluginManager().callEvent(event);
		});

		crackingSuccessHandler.addHandler(crackingSession -> {
			var event    = new LootChestCrackingSuccessEvent(crackingSession.getChestData(), crackingSession);
			var endEvent = new LootChestCrackingEndEvent(crackingSession.getChestData(), crackingSession);
			Bukkit.getPluginManager().callEvent(event);
			Bukkit.getPluginManager().callEvent(endEvent);
		});

		crackingFailedHandler.addHandler(crackingSession -> {
			var event    = new LootChestCrackingFailureEvent(crackingSession.getChestData(), crackingSession);
			var endEvent = new LootChestCrackingEndEvent(crackingSession.getChestData(), crackingSession);
			Bukkit.getPluginManager().callEvent(event);
			Bukkit.getPluginManager().callEvent(endEvent);
		});
	}

	/**
	 * Opens the chest directly without cracking minigame
	 */
	private OpenResult openChestDirectly(Player player, LootChestData chestData, LootTable lootTable, LootTier tier) {
		// Consume the tier's unlock item (lockpick / key) on the *first* successful open of the cycle.
		// Subsequent openers during the same cycle see isUnlocked() == true and skip this block.
		// PERMISSION tiers never consume and never flip the flag — every player keeps re-checking.
		if (tier != null && !chestData.isUnlocked()) {
			switch (tier.unlockRequirement()) {
				case LOCKPICK -> {
					String itemId = tier.unlockItemId() != null ? tier.unlockItemId() : "lockpick";
					consumeRequiredItem(player, itemId);
					chestData.setUnlocked(true);
					cooldownManager.showAvailableHologram(chestData);
				}
				case KEY -> {
					String itemId = tier.unlockItemId() != null ? tier.unlockItemId() : "key_" + tier.id();
					consumeRequiredItem(player, itemId);
					chestData.setUnlocked(true);
					cooldownManager.showAvailableHologram(chestData);
				}
				default -> {
					// NONE / PERMISSION: nothing to consume
				}
			}
		}

		UUID chestId = chestData.getId();

		// Check if there's already a shared inventory for this chest
		InventoryHandler inventory = sharedChestInventories.get(chestId);
		List<ItemStack>  items;
		boolean          isShared  = false;

		if (inventory != null) {
			// Reuse existing shared inventory - another player has it open
			items    = chestData.getCurrentInventory();
			isShared = true;
		} else {
			// Check if chest already has items (reusing existing inventory from previous session)
			List<ItemStack> currentInventory = chestData.getCurrentInventory();
			if (currentInventory != null && !currentInventory.isEmpty()) {
				// Use existing inventory
				items = currentInventory;
			} else {
				// Generate new loot
				String tierId = tier != null ? tier.id() : "default";
				items = lootTable.generateLoot(tierId, itemParser);
			}

			// Create new shared inventory
			String        title = chestData.getDisplayName();
			NamespacedKey key   = new NamespacedKey(plugin, "loot_chest_" + chestId.toString());
			inventory = new InventoryHandler(title, chestData.getInventorySize(), key, player.getUniqueId());
			sharedChestInventories.put(chestId, inventory);
		}

		// Create session with shared inventory - pass isShared flag explicitly
		var session = new LootChestSession(player, chestData, inventory, items, isShared);

		activeSessions.put(player.getUniqueId(), session);

		// Track session by chest
		activeSessionsByChest.computeIfAbsent(chestId, k -> ConcurrentHashMap.newKeySet()).add(session);

		sessionStartHandler.handle(session);

		// Open the chest immediately
		session.open();

		return OpenResult.SUCCESS;
	}

	private OpenResult checkUnlockRequirement(Player player, LootTier tier, LootChestData chestData) {
		// Consumable tiers stay unlocked for the rest of the cycle once any player burns the item.
		// The flag is cleared in LootChestData#respawn, so each new cycle re-locks.
		if (chestData.isUnlocked()
		    && (tier.unlockRequirement() == LootTier.UnlockRequirement.LOCKPICK
		        || tier.unlockRequirement() == LootTier.UnlockRequirement.KEY)) {
			return OpenResult.SUCCESS;
		}

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

	/**
	 * Removes one unit of the unlock item (matched by the {@code loot_key} NBT tag) from the player's inventory. Caller
	 * must have already verified the item is present — failure to match here is silently ignored.
	 */
	private void consumeRequiredItem(Player player, String itemKey) {
		ItemStack[] contents = player.getInventory().getContents();

		for (int slot = 0; slot < contents.length; slot++) {
			ItemStack item = contents[slot];
			if (item == null) continue;

			ItemBuilder builder = new ItemBuilder(item);

			if (!(builder.hasNBTTag("loot_key") && itemKey.equals(builder.getStringTagData("loot_key")))) continue;

			int amount = item.getAmount();
			if (amount <= 1) {
				player.getInventory().setItem(slot, null);
			} else {
				item.setAmount(amount - 1);
			}
			return;
		}
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
		NO_ITEM_PARSER,
		CRACKING_STARTED
	}

}
