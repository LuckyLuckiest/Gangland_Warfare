package org.luckyraven.gangland.config;

import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.bootstrap.GanglandContext;
import org.luckyraven.gangland.command.data.InformationManager;
import org.luckyraven.gangland.compatibility.CompatibilitySetup;
import org.luckyraven.gangland.compatibility.CompatibilityWorker;
import org.luckyraven.gangland.compatibility.VersionSetup;
import org.luckyraven.gangland.core.bean.Bean;
import org.luckyraven.gangland.core.bean.BeanGraph;
import org.luckyraven.gangland.core.bean.Configuration;
import org.luckyraven.gangland.core.bean.Phase;
import org.luckyraven.gangland.data.permission.PermissionHandler;
import org.luckyraven.gangland.data.permission.PermissionManager;
import org.luckyraven.gangland.data.permission.PermissionWorker;
import org.luckyraven.gangland.data.placeholder.PlaceholderService;
import org.luckyraven.gangland.database.GanglandDatabaseSettings;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserFactory;
import org.luckyraven.gangland.inventory.InventoryHandler;
import org.luckyraven.gangland.inventory.service.InventoryRegistry;
import org.luckyraven.gangland.inventory.villager.VillagerInventory;
import org.luckyraven.gangland.inventory.villager.VillagerInventoryListener;
import org.luckyraven.gangland.inventory.villager.VillagerInventoryRegistry;
import org.luckyraven.gangland.persistence.FileHandler;
import org.luckyraven.gangland.persistence.FileInitializer;
import org.luckyraven.gangland.persistence.FileManager;
import org.luckyraven.gangland.persistence.database.DatabaseManager;
import org.luckyraven.gangland.persistence.database.DatabaseSettingsProvider;

/**
 * KERNEL-phase configuration that produces every bootstrap-critical singleton the plugin needs before the FILE phase
 * begins. These objects were previously constructed by hand in {@code Initializer.java} and seeded into the container
 * via {@code seedKernelBeans()}; they are now standard {@code @Bean} methods whose dependency ordering is resolved
 * automatically by {@link BeanGraph}.
 *
 * <p>Because all {@code @Configuration} constructors are instantiated before any phase runs, this class can only
 * inject beans that are pre-registered in {@link GanglandContext}'s constructor ({@code Gangland},
 * {@code GanglandContext}, {@code DependencyContainer}). Every other kernel object is produced by {@code @Bean} methods
 * whose parameters are resolved within the KERNEL phase itself.
 */
@Configuration(phase = Phase.KERNEL)
public class KernelConfig {

	private final Gangland gangland;

	public KernelConfig(Gangland gangland) {
		this.gangland = gangland;
	}

	@Bean
	public InformationManager informationManager() {
		InformationManager manager = new InformationManager();
		manager.processCommands();
		return manager;
	}

	@Bean
	public VersionSetup versionSetup() {
		return new VersionSetup();
	}

	@Bean
	public CompatibilitySetup compatibilitySetup(VersionSetup versionSetup) {
		return new CompatibilitySetup(versionSetup);
	}

	@Bean
	public PlaceholderService placeholderService() {
		return new PlaceholderService(gangland);
	}

	/**
	 * Per-player open-inventory tracker. Constructed once and threaded into the {@link UserFactory}, the four UI
	 * listeners, and the {@link InventoryHandler} static seam (the last static seam left after Phase 2 — same
	 * lightweight pattern as {@code Messages.init(...)}).
	 */
	@Bean
	public InventoryRegistry inventoryRegistry() {
		InventoryRegistry registry = new InventoryRegistry();
		InventoryHandler.setRegistry(registry);
		return registry;
	}

	/**
	 * Per-player tracker of open {@link VillagerInventory} wrappers. Threaded into the
	 * {@link VillagerInventoryListener} and into every wrapper that needs to register itself on open. Parallel to
	 * {@link InventoryRegistry} but for the native Bukkit merchant UI.
	 */
	@Bean
	public VillagerInventoryRegistry villagerInventoryRegistry() {
		return new VillagerInventoryRegistry();
	}

	/**
	 * Builds {@link User} instances with their {@link PlaceholderService} and {@link InventoryRegistry} dependencies
	 * wired in. Replaces the static {@code User.setPlaceholder(...)} field.
	 */
	@Bean
	public UserFactory userFactory(PlaceholderService placeholderService, InventoryRegistry inventoryRegistry) {
		return new UserFactory(gangland, placeholderService, inventoryRegistry);
	}

	/**
	 * ViaAPI is {@code null} at this point — it is set later by {@code Gangland.dependencyHandler()} after bootstrap
	 * completes. {@link CompatibilityWorker} handles a {@code null} ViaAPI gracefully.
	 */
	@Bean
	public CompatibilityWorker compatibilityWorker(CompatibilitySetup compatibilitySetup) {
		return new CompatibilityWorker(gangland.getViaAPI(), compatibilitySetup);
	}

	@Bean
	public PermissionWorker permissionWorker() {
		return new PermissionWorker(Gangland.FULL_PREFIX);
	}

	@Bean
	public PermissionManager permissionManager(PermissionHandler permissionHandler) {
		return new PermissionManager(permissionHandler);
	}

	/**
	 * Constructs the {@link FileManager} and registers every static {@link FileHandler} the plugin owns. The actual
	 * YAML load happens later when each {@link FileInitializer} bean fires in the FILE phase via the
	 * {@code FileManager.initializeAll()} phase hook.
	 */
	@Bean
	public FileManager fileManager() {
		FileManager fm = new FileManager(gangland);
		fm.addFile(new FileHandler(gangland, "settings", ".yml"), true);
		fm.addFile(new FileHandler(gangland, "scoreboard", ".yml"), true);

		fm.addFile(new FileHandler(gangland, "ammunition", "items", ".yml"), true);
		fm.addFile(new FileHandler(gangland, "unique_items", "items", ".yml"), true);
		fm.addFile(new FileHandler(gangland, "wearables", "items", ".yml"), true);
		fm.addFile(new FileHandler(gangland, "cars", "items", ".yml"), true);
		fm.addFile(new FileHandler(gangland, "money", "items", ".yml"), true);

		fm.addFile(new FileHandler(gangland, "loot_chests", "lootchests", ".yml"), true);
		fm.addFile(new FileHandler(gangland, "tiers", "lootchests", ".yml"), true);

		fm.addFile(new FileHandler(gangland, "cops", "npc", ".yml"), true);
		fm.addFile(new FileHandler(gangland, "civilians", "npc", ".yml"), true);
		fm.addFile(new FileHandler(gangland, "trader_traits", "npc", ".yml"), true);
		fm.addFile(new FileHandler(gangland, "bank_tiers", "npc", ".yml"), true);

		fm.addFile(new FileHandler(gangland, "turf_powerups", "turf", ".yml"), true);
		fm.addFile(new FileHandler(gangland, "turf_npcs", "turf", ".yml"), true);
		return fm;
	}

	@Bean
	public DatabaseSettingsProvider databaseSettings() {
		return new GanglandDatabaseSettings();
	}

	@Bean
	public DatabaseManager databaseManager(DatabaseSettingsProvider databaseSettings) {
		return new DatabaseManager(gangland, databaseSettings);
	}

}
