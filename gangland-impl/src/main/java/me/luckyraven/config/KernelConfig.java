package me.luckyraven.config;

import me.luckyraven.Gangland;
import me.luckyraven.bootstrap.GanglandContext;
import me.luckyraven.command.data.InformationManager;
import me.luckyraven.compatibility.CompatibilitySetup;
import me.luckyraven.compatibility.CompatibilityWorker;
import me.luckyraven.compatibility.VersionSetup;
import me.luckyraven.data.account.user.UserFactory;
import me.luckyraven.data.permission.PermissionHandler;
import me.luckyraven.data.permission.PermissionManager;
import me.luckyraven.data.permission.PermissionWorker;
import me.luckyraven.data.placeholder.PlaceholderService;
import me.luckyraven.database.GanglandDatabaseSettings;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.service.InventoryRegistry;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.persistence.database.DatabaseManager;
import me.luckyraven.persistence.database.DatabaseSettingsProvider;

import me.luckyraven.util.autowire.bean.Bean;
import me.luckyraven.util.autowire.bean.BeanGraph;
import me.luckyraven.util.autowire.bean.Configuration;
import me.luckyraven.util.autowire.bean.Phase;

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
	 * Builds {@link me.luckyraven.data.account.user.User} instances with their {@link PlaceholderService} and
	 * {@link InventoryRegistry} dependencies wired in. Replaces the static {@code User.setPlaceholder(...)} field.
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
	 * YAML load happens later when each {@link me.luckyraven.persistence.FileInitializer} bean fires in the FILE phase
	 * via the {@code FileManager.initializeAll()} phase hook.
	 */
	@Bean
	public FileManager fileManager() {
		FileManager fm = new FileManager(gangland);
		fm.addFile(new FileHandler(gangland, "settings", ".yml"), true);
		fm.addFile(new FileHandler(gangland, "scoreboard", ".yml"), true);
		fm.addFile(new FileHandler(gangland, "ammunition", ".yml"), true);
		fm.addFile(new FileHandler(gangland, "unique_items", ".yml"), true);
		fm.addFile(new FileHandler(gangland, "loot_chests", "loot", ".yml"), true);
		fm.addFile(new FileHandler(gangland, "tiers", "loot", ".yml"), true);
		fm.addFile(new FileHandler(gangland, "cops", ".yml"), true);
		fm.addFile(new FileHandler(gangland, "civilians", ".yml"), true);
		fm.addFile(new FileHandler(gangland, "repair", ".yml"), true);
		fm.addFile(new FileHandler(gangland, "wearables", ".yml"), true);
		fm.addFile(new FileHandler(gangland, "cars", ".yml"), true);
		fm.addFile(new FileHandler(gangland, "money", ".yml"), true);
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
