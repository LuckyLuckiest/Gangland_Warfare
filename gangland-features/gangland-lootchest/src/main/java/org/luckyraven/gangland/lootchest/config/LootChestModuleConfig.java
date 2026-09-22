package org.luckyraven.gangland.lootchest.config;

import lombok.CustomLog;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.GanglandApi;
import org.luckyraven.gangland.item.NbtTagCatalog;
import org.luckyraven.gangland.lootchest.LootChestManager;
import org.luckyraven.gangland.lootchest.LootChestService;
import org.luckyraven.gangland.lootchest.LootChestWandTag;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.hologram.HologramService;
import org.luckyraven.keystone.inventory.InventoryService;
import org.luckyraven.keystone.item.ItemParser;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;

/**
 * CONFIG-phase wiring for the loot chest module: the {@code keystone-hologram} bean (armor-stand holograms,
 * promoted out of Gangland's own {@code hologram-api} — WS3 G1), {@link LootChestManager}, its
 * {@link LootChestLoader}, the module's own {@link LootChestMessagesProvider}/{@link LootChestSettingsProvider}
 * beans (WS3 G4 — read {@code lootchests/lootchest_messages.yml}/{@code loot_chest_settings.yml} instead of
 * {@code gangland-api}'s {@code Messages}/{@code Settings}) and the {@link NbtTagCatalog} tag registration (all
 * moved out of the core's {@code GameplayConfig}/{@code ItemConfig} — WS3 G2).
 *
 * <p>{@link #lootChestLoader} calls {@code fileManager.registerInitializer(loader); fileManager.initializeAll();}
 * inline (B1) rather than a separate {@code @PostConstruct} — the exact precedent {@code CopsNCrooksModuleConfig}'s
 * {@code copLoader} and {@code CiviliansModuleConfig}'s civilian loader both use. The core's own
 * {@code GameplayConfig.initializeDeferredLoaders()} (renamed from {@code initializeLootChestLoader}) still runs its
 * own {@code fileManager.initializeAll()} afterwards for every other core-registered {@code FileLoader} — a second
 * {@code initializeAll()} call is a no-op for files already loaded.
 */
@CustomLog
@Configuration
public class LootChestModuleConfig {

	private final JavaPlugin plugin;

	public LootChestModuleConfig(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	@Bean
	public HologramService hologramService() {
		HologramService service = new HologramService(plugin);
		service.registerProtection(plugin);
		return service;
	}

	/**
	 * Registered as {@code FileInitializer} (G4) so it's reachable from the module's own listener/command beans
	 * ({@code LootChestEarnGoodsListener}, {@code LootChestWandListener}, {@code LootChestWandCommand}) by
	 * constructor injection, not just from {@link #lootChestManager}.
	 */
	@Bean
	public LootChestMessagesProvider lootChestMessages(FileManager fileManager) {
		GanglandLootChestMessages messages = new GanglandLootChestMessages(fileManager);
		fileManager.registerInitializer(messages);
		return messages;
	}

	/**
	 * Registered as {@code FileInitializer} (G4) so it's reachable from the module's own listener beans
	 * ({@code LootChestEarnGoodsListener}, {@code LootChestWandListener}) by constructor injection, not just from
	 * {@link #lootChestLoader}.
	 */
	@Bean
	public LootChestSettingsProvider lootChestSettings(FileManager fileManager) {
		LootChestSettings settings = new LootChestSettings(fileManager);
		fileManager.registerInitializer(settings);
		return settings;
	}

	@Bean
	public LootChestManager lootChestManager(HologramService hologramService, RepositoryRegistry repositoryRegistry,
	                                         ItemParser itemParser, InventoryService inventoryService,
	                                         LootChestMessagesProvider messagesProvider) {
		return new LootChestManager(plugin, GanglandApi.FULL_PREFIX, hologramService, repositoryRegistry, itemParser,
		                            messagesProvider, inventoryService);
	}

	@Bean
	public LootChestService lootChestService(LootChestManager lootChestManager) {
		return lootChestManager;
	}

	@Bean
	public LootChestLoader lootChestLoader(LootChestManager lootChestManager, FileManager fileManager,
	                                       LootChestSettingsProvider settingsProvider) {
		LootChestLoader loader = new LootChestLoader(plugin, lootChestManager, settingsProvider, false, null,
		                                             fileManager);
		fileManager.registerInitializer(loader);
		fileManager.initializeAll();
		return loader;
	}

	/**
	 * Registers the wand's 9 NBT tag names (lowercase — the exact form the core's pre-split
	 * {@code ItemConfig.nbtTagCatalog()} loop used, C11) into the always-present core catalog bean. Item registry
	 * injection, no new interface — see {@code documentation/module-loader.md}'s {@code NbtTagCatalog} entry.
	 */
	@Bean
	public LootChestWandTag[] lootChestWandTags(NbtTagCatalog catalog) {
		for (LootChestWandTag tag : LootChestWandTag.values()) {
			catalog.register(tag.toString().toLowerCase());
		}
		return LootChestWandTag.values();
	}
}
