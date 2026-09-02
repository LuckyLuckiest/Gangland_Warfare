package org.luckyraven.gangland.config;

import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.bootstrap.GanglandContext;
import org.luckyraven.gangland.command.data.InformationManager;
import org.luckyraven.gangland.compatibility.CompatibilityWorker;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.BeanGraph;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Phase;
import org.luckyraven.keystone.permission.PermissionHandler;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.keystone.permission.PermissionWorker;
import org.luckyraven.gangland.data.placeholder.PlaceholderService;
import org.luckyraven.gangland.database.GanglandDatabaseSettings;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.keystone.diagnostics.Diagnostics;
import org.luckyraven.keystone.diagnostics.LoggingSink;
import org.luckyraven.keystone.diagnostics.RecentFaultsSink;
import org.luckyraven.gangland.gang.user.UserFactory;
import org.luckyraven.gangland.inventory.InventoryHandler;
import org.luckyraven.gangland.inventory.service.InventoryRegistry;
import org.luckyraven.gangland.inventory.villager.VillagerInventory;
import org.luckyraven.gangland.inventory.villager.VillagerInventoryListener;
import org.luckyraven.gangland.inventory.villager.VillagerInventoryRegistry;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileInitializer;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.persistence.database.DatabaseManager;
import org.luckyraven.keystone.persistence.database.DatabaseSettingsProvider;
import org.luckyraven.keystone.sound.ResourcePackTracker;

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

	/**
	 * Tracks which players accepted the resource pack, gating custom-sound playback. Installed process-wide so
	 * {@code SoundEffect}'s CUSTOM branch reaches it via {@code ResourcePackTracker.active()}; the listener feeds
	 * it and {@code Gangland.onDisable} uninstalls it.
	 */
	@Bean
	public ResourcePackTracker resourcePackTracker() {
		ResourcePackTracker tracker = new ResourcePackTracker();
		ResourcePackTracker.install(tracker);
		return tracker;
	}

	/**
	 * The plugin's fault hub (Keystone diagnostics, 1.7.x migration). Guard-wrapped listener dispatch, the command
	 * funnels, repository failures and backend queries all report here; faults are classified
	 * (user error / dependency / internal bug), logged at the right level, kept in a recent-faults ring, and — once
	 * the DATABASE phase adds the {@code DatabaseFaultSink} — persisted. Installed process-wide so Keystone code
	 * paths reach it via {@code Diagnostics.active()}.
	 */
	@Bean
	public Diagnostics diagnostics() {
		Diagnostics hub = Diagnostics.withDefaults()
		                             .addSink(new LoggingSink())
		                             .addSink(new RecentFaultsSink());
		Diagnostics.install(hub);
		return hub;
	}

	@Bean
	public PlaceholderService placeholderService() {
		PlaceholderService service = new PlaceholderService(gangland);
		// The old gangland-core ChatUtil.color(String) implicitly substituted %money_symbol% with a hardcoded "$";
		// Keystone's ChatUtil (1.7.0+) is pure color translation. The token is resolved here instead, settings-backed,
		// so inventory/scoreboard templates keep rendering — now with the configured symbol.
		service.register((player, text) -> text.replace("%money_symbol%", Settings.getMoneySymbol()));
		return service;
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
	 * Version detection and adapter loading live in Keystone now ({@code CraftBukkitRevision} +
	 * {@code VersionedAdapterLoader}); the worker keeps only Gangland's contract + fallback. ViaAPI is passed as a
	 * supplier because it is still {@code null} here — {@code Gangland.dependencyHandler()} sets it after
	 * bootstrap, and the supplier resolves it at recoil time.
	 */
	@Bean
	public CompatibilityWorker compatibilityWorker() {
		return new CompatibilityWorker(gangland::getViaAPI);
	}

	@Bean
	public PermissionWorker permissionWorker() {
		return new PermissionWorker(Gangland.FULL_PREFIX);
	}

	@Bean
	public PermissionManager permissionManager(PermissionHandler permissionHandler) {
		return new PermissionManager(permissionHandler, Gangland.FULL_PREFIX);
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
