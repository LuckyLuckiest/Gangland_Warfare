package me.luckyraven;

import lombok.CustomLog;
import lombok.Getter;
import me.luckyraven.command.data.InformationManager;
import me.luckyraven.compatibility.CompatibilitySetup;
import me.luckyraven.compatibility.CompatibilityWorker;
import me.luckyraven.compatibility.VersionSetup;
import me.luckyraven.context.GanglandContext;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.permission.PermissionManager;
import me.luckyraven.data.permission.PermissionWorker;
import me.luckyraven.data.placeholder.PlaceholderService;
import me.luckyraven.database.GanglandDatabaseSettings;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.persistence.database.DatabaseManager;
import me.luckyraven.persistence.database.DatabaseSettingsProvider;
import me.luckyraven.scoreboard.ScoreboardManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bootstraps the plugin by constructing kernel objects up-front and then driving the phased bean pipeline via
 * {@link GanglandContext}. After the bean migration, this class is intentionally thin: it owns the pre-bean load-phase
 * objects (version detection, compatibility setup, placeholder service) and orchestrates the kernel seed + bootstrap
 * call. All managers, addons, services, and lifecycle beans are created and wired by {@code @Configuration} classes
 * under {@code me.luckyraven.config}.
 */
@CustomLog
public final class Initializer {

	private final Gangland gangland;

	// on plugin load — pre-bean objects
	@Getter
	private final InformationManager informationManager;
	@Getter
	private final VersionSetup       versionSetup;
	@Getter
	private final CompatibilitySetup compatibilitySetup;
	@Getter
	private final PlaceholderService placeholderService;

	// Single root DI container + bean factory orchestration. Owns the entire bean lifecycle from FILE phase through
	// COMMAND phase. Created in postInitialize() before any bean is constructed.
	@Getter
	private GanglandContext context;

	public Initializer(Gangland gangland) {
		this.gangland = gangland;

		// If at any instance these data failed to load, then the plugin will not function
		this.informationManager = new InformationManager();
		this.informationManager.processCommands();

		this.versionSetup       = new VersionSetup();
		this.compatibilitySetup = new CompatibilitySetup(versionSetup);

		this.placeholderService = new PlaceholderService(gangland);
		User.setPlaceholder(placeholderService);
	}

	/**
	 * Bootstraps the entire plugin via {@link GanglandContext}. The kernel objects are built up-front for failure
	 * isolation, seeded into the container, then the phased bean pipeline (FILE → DATABASE → CONFIG → LIFECYCLE →
	 * LISTENER → COMMAND) is driven by {@link GanglandContext#bootstrap()}.
	 */
	public void postInitialize() {
		// --- Kernel objects: built up-front so the rest of the bootstrap can fail loudly if any of these break ---
		CompatibilityWorker compatibilityWorker = new CompatibilityWorker(gangland.getViaAPI(), compatibilitySetup);

		PermissionWorker  permissionWorker  = new PermissionWorker(Gangland.FULL_PREFIX);
		PermissionManager permissionManager = new PermissionManager(permissionWorker);

		FileManager fileManager = new FileManager(gangland);
		seedFileHandlers(fileManager);

		DatabaseSettingsProvider databaseSettings = new GanglandDatabaseSettings();
		DatabaseManager          databaseManager  = new DatabaseManager(gangland, databaseSettings);

		// Scoreboard manager has no file dependencies and is referenced by multiple beans; pre-build it so we can
		// seed it as a kernel object. PlaceholderService exists from the load-phase constructor, so it can be
		// passed in here. The FILE-phase ScoreboardAddon is wired in later via GameplayConfig.wireScoreboardAddon().
		ScoreboardManager scoreboardManager = new ScoreboardManager(gangland, placeholderService);

		// --- Build the root container + bean orchestrator ---
		this.context = new GanglandContext(gangland);
		// Publish the context on Gangland immediately so code that calls gangland.getContext() during the bootstrap
		// (e.g. Argument.addPermission() during command construction) can resolve beans from the container.
		gangland.setContext(context);
		seedKernelBeans(compatibilityWorker, permissionManager, fileManager, databaseSettings,
		                databaseManager, scoreboardManager);

		// --- Drive the phased bean pipeline ---
		context.bootstrap();
	}

	/**
	 * Adds every static {@link FileHandler} the plugin owns to the {@link FileManager}. The actual YAML load happens
	 * later when each {@link me.luckyraven.persistence.FileInitializer} bean is invoked in the FILE phase.
	 */
	private void seedFileHandlers(FileManager fileManager) {
		fileManager.addFile(new FileHandler(gangland, "settings", ".yml"), true);
		fileManager.addFile(new FileHandler(gangland, "scoreboard", ".yml"), true);
		fileManager.addFile(new FileHandler(gangland, "ammunition", ".yml"), true);
		fileManager.addFile(new FileHandler(gangland, "unique_items", ".yml"), true);
		fileManager.addFile(new FileHandler(gangland, "loot_chests", "loot", ".yml"), true);
		fileManager.addFile(new FileHandler(gangland, "tiers", "loot", ".yml"), true);
		fileManager.addFile(new FileHandler(gangland, "cops", ".yml"), true);
		fileManager.addFile(new FileHandler(gangland, "civilians", ".yml"), true);
		fileManager.addFile(new FileHandler(gangland, "repair", ".yml"), true);
		fileManager.addFile(new FileHandler(gangland, "wearables", ".yml"), true);
		fileManager.addFile(new FileHandler(gangland, "cars", ".yml"), true);
		fileManager.addFile(new FileHandler(gangland, "money", ".yml"), true);
	}

	/**
	 * Registers every kernel-built singleton into the root container so {@code @Configuration} classes can pull them
	 * via constructor injection. {@code Gangland} and {@code DependencyContainer} are self-registered by
	 * {@link GanglandContext}'s constructor.
	 */
	private void seedKernelBeans(CompatibilityWorker compatibilityWorker,
	                             PermissionManager permissionManager,
	                             FileManager fileManager,
	                             DatabaseSettingsProvider databaseSettings,
	                             DatabaseManager databaseManager,
	                             ScoreboardManager scoreboardManager) {
		context.register(JavaPlugin.class, gangland);
		context.register(InformationManager.class, informationManager);
		context.register(VersionSetup.class, versionSetup);
		context.register(CompatibilitySetup.class, compatibilitySetup);
		context.register(CompatibilityWorker.class, compatibilityWorker);
		context.register(PlaceholderService.class, placeholderService);
		context.register(PermissionManager.class, permissionManager);
		context.register(FileManager.class, fileManager);
		context.register(DatabaseManager.class, databaseManager);
		context.register(DatabaseSettingsProvider.class, databaseSettings);
		context.register(ScoreboardManager.class, scoreboardManager);
	}
}
