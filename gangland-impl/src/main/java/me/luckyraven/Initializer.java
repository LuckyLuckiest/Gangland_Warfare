package me.luckyraven;

import lombok.AccessLevel;
import lombok.Getter;
import me.luckyraven.command.CommandManager;
import me.luckyraven.command.CommandTabCompleter;
import me.luckyraven.command.data.InformationManager;
import me.luckyraven.command.sub.*;
import me.luckyraven.command.sub.bank.BankCommand;
import me.luckyraven.command.sub.bounty.BountyCommand;
import me.luckyraven.command.sub.debug.ComponentExecutorCommand;
import me.luckyraven.command.sub.debug.DebugCommand;
import me.luckyraven.command.sub.debug.ReadNBTCommand;
import me.luckyraven.command.sub.debug.TimerCommand;
import me.luckyraven.command.sub.gang.GangCommand;
import me.luckyraven.command.sub.rank.RankCommand;
import me.luckyraven.command.sub.wanted.WantedCommand;
import me.luckyraven.command.sub.waypoint.TeleportCommand;
import me.luckyraven.command.sub.waypoint.WaypointCommand;
import me.luckyraven.command.sub.weapon.AmmunitionCommand;
import me.luckyraven.command.sub.weapon.WeaponCommand;
import me.luckyraven.compatibility.CompatibilitySetup;
import me.luckyraven.compatibility.CompatibilityWorker;
import me.luckyraven.compatibility.VersionSetup;
import me.luckyraven.compatibility.recoil.RecoilCompatibility;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.MemberManager;
import me.luckyraven.data.permission.PermissionManager;
import me.luckyraven.data.permission.PermissionWorker;
import me.luckyraven.data.placeholder.replacer.Replacer;
import me.luckyraven.data.placeholder.worker.GanglandPlaceholder;
import me.luckyraven.data.plugin.PluginManager;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.data.teleportation.Waypoint;
import me.luckyraven.data.teleportation.WaypointManager;
import me.luckyraven.data.teleportation.WaypointTeleport;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.DatabaseManager;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.database.component.Table;
import me.luckyraven.database.tables.*;
import me.luckyraven.exception.PluginException;
import me.luckyraven.feature.entity.EntityMarkManager;
import me.luckyraven.file.FileHandler;
import me.luckyraven.file.FileManager;
import me.luckyraven.file.LanguageLoader;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.file.configuration.inventory.InventoryAddon;
import me.luckyraven.file.configuration.inventory.InventoryLoader;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.condition.BooleanExpressionEvaluator;
import me.luckyraven.inventory.condition.ConditionEvaluator;
import me.luckyraven.listener.ListenerManager;
import me.luckyraven.scoreboard.ScoreboardManager;
import me.luckyraven.scoreboard.configuration.ScoreboardAddon;
import me.luckyraven.sign.SignManager;
import me.luckyraven.sign.registry.SignFormatRegistry;
import me.luckyraven.sign.registry.SignTypeRegistry;
import me.luckyraven.sign.service.SignFormatterService;
import me.luckyraven.sign.service.SignInteraction;
import me.luckyraven.sign.service.SignInteractionService;
import me.luckyraven.util.autowire.DependencyContainer;
import me.luckyraven.util.listener.ListenerPriority;
import me.luckyraven.weapon.WeaponManager;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.configuration.AmmunitionAddon;
import me.luckyraven.weapon.configuration.WeaponAddon;
import me.luckyraven.weapon.configuration.WeaponLoader;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
public final class Initializer {

	@Getter(value = AccessLevel.NONE)
	private final Gangland gangland;

	// on plugin load
	private final InformationManager informationManager;
	private final VersionSetup       versionSetup;
	private final CompatibilitySetup compatibilitySetup;

	private final String prefix;

	// on plugin enable
	// Managers
	private PluginManager              pluginManager;
	private UserManager<Player>        userManager;
	private UserManager<OfflinePlayer> offlineUserManager;
	private PermissionManager          permissionManager;
	private FileManager                fileManager;
	private DatabaseManager            databaseManager;
	private GangManager                gangManager;
	private MemberManager              memberManager;
	private ListenerManager            listenerManager;
	private CommandManager             commandManager;
	private RankManager                rankManager;
	private WaypointManager            waypointManager;
	private ScoreboardManager          scoreboardManager;
	private WeaponManager              weaponManager;
	private SignManager                signManager;
	private EntityMarkManager          entityMarkManager;
	// Addons
	private SettingAddon               settingAddon;
	private ScoreboardAddon            scoreboardAddon;
	private AmmunitionAddon            ammunitionAddon;
	private WeaponAddon                weaponAddon;
	// Loader
	private LanguageLoader             languageLoader;
	private InventoryLoader            inventoryLoader;
	private WeaponLoader               weaponLoader;
	// Database
	private GanglandDatabase           ganglandDatabase;
	// Placeholder
	private GanglandPlaceholder        placeholder;
	// Compatibility
	private CompatibilityWorker        compatibilityWorker;
	// Condition Evaluator
	private ConditionEvaluator         evaluator;

	public Initializer(Gangland gangland) {
		this.gangland = gangland;

		// If at any instance these data failed to load, then the plugin will not function
		this.informationManager = new InformationManager();
		this.informationManager.processCommands();

		this.versionSetup       = new VersionSetup();
		this.compatibilitySetup = new CompatibilitySetup(versionSetup);

		this.prefix = "glw";
	}

	/**
	 * Initializes the rest of the necessary classes that would conflict with the first object initialization.
	 * </b>
	 * This is used to safeguard the first initialization.
	 */
	public void postInitialize() {
		// Compatibility loader
		compatibilityWorker = new CompatibilityWorker(gangland.getViaAPI(), compatibilitySetup);

		// permission manager
		var ganglandString   = "gangland";
		var permissionWorker = new PermissionWorker(ganglandString);

		permissionManager = new PermissionManager(permissionWorker);

		// File
		fileManager = new FileManager(gangland);
		files();

		// Database
		databaseManager = new DatabaseManager(gangland);
		databases();
		databaseManager.initializeDatabases();

		// add all registered plugin permissions
		Set<Permission> permissions = Bukkit.getPluginManager().getPermissions();
		Set<String> ganglandPermissions = permissions.stream()
				.map(Permission::getName)
				.filter(permission -> permission.startsWith(ganglandString))
				.collect(Collectors.toSet());

		permissionManager.addAllPermissions(ganglandPermissions);

		// User manager
		userManager        = new UserManager<>(gangland);
		offlineUserManager = new UserManager<>(gangland);

		// initialize the database
		ganglandDatabase = GanglandDatabase.findInstance(databaseManager);

		// manage if the database was null
		if (ganglandDatabase == null) {
			throw new PluginException("Gangland Database instance is not found.");
			// plugin crashes
		}

		List<Table<?>> tables = ganglandDatabase.getTables();

		// plugin manager
		pluginManager = new PluginManager(gangland);

		// initialize the plugin manager
		PluginDataTable pluginTable = getInstanceFromTables(PluginDataTable.class, tables);

		pluginManager.initialize(pluginTable);

		// Rank manager
		rankManager = new RankManager(gangland);

		// initialize the rank class
		RankTable           rankTable           = getInstanceFromTables(RankTable.class, tables);
		RankParentTable     rankParentTable     = getInstanceFromTables(RankParentTable.class, tables);
		PermissionTable     permissionTable     = getInstanceFromTables(PermissionTable.class, tables);
		RankPermissionTable rankPermissionTable = getInstanceFromTables(RankPermissionTable.class, tables);

		rankManager.initialize(rankTable, rankParentTable, permissionTable, rankPermissionTable);

		// Gang manager
		gangManager   = new GangManager(gangland);
		memberManager = new MemberManager(gangland);

		// initialize the gang and member classes
		GangTable       gangTable       = getInstanceFromTables(GangTable.class, tables);
		GangAlliesTable gangAlliesTable = getInstanceFromTables(GangAlliesTable.class, tables);
		MemberTable     memberTable     = getInstanceFromTables(MemberTable.class, tables);

		gangManager.initialize(gangTable, gangAlliesTable);
		memberManager.initialize(memberTable, gangManager, rankManager);

		// Waypoint manager
		waypointManager = new WaypointManager(gangland);

		WaypointTable waypointTable = getInstanceFromTables(WaypointTable.class, tables);

		// initialize the waypoint class
		waypointManager.initialize(waypointTable);

		// Weapon manager
		weaponManager = new WeaponManager(gangland);

		WeaponTable weaponTable = getInstanceFromTables(WeaponTable.class, tables);

		// initialize the weapon class
		weaponManager.initialize(weaponTable);

		// sign manager
		SignTypeRegistry     registry         = new SignTypeRegistry();
		SignFormatRegistry   formatRegistry   = new SignFormatRegistry();
		SignFormatterService formatterService = new SignFormatterService(formatRegistry);

		String signPrefix = prefix + "-";

		SignInteraction signInteraction = new SignInteraction(signPrefix, registry, formatterService);

		signManager = new SignManager(gangland, registry, signInteraction);

		signManager.initialize();

		entityMarkManager = new EntityMarkManager(gangland);

		// Events
		listenerManager = new ListenerManager(gangland);
		events();
		listenerManager.registerEvents();

		// Commands
		commandManager = new CommandManager(gangland);
		commands(gangland);

		// Placeholder
		placeholder = new GanglandPlaceholder(gangland, Replacer.Closure.PERCENT);
	}

	/**
	 * Initializes the files and by default adds three types of files (even if not presented) to the registered files,
	 * and if they were not created, a new file would get created. Additionally, it loads the addons which help the
	 * plugin functionality work.
	 */
	public void files() {
		fileManager.addFile(new FileHandler(gangland, "settings", ".yml"), true);
		fileManager.addFile(new FileHandler(gangland, "scoreboard", ".yml"), true);
		fileManager.addFile(new FileHandler(gangland, "ammunition", ".yml"), true);
		scoreboardManager = new ScoreboardManager(gangland);

		addonsLoader();
	}

	/**
	 * Helps the plugin features to properly load.
	 */
	public void addonsLoader() {
		settingAddon   = new SettingAddon(fileManager);
		languageLoader = new LanguageLoader(gangland);

		languageLoader.initialize();

		MessageAddon.setMessageConfiguration(languageLoader.getMessage());

		scoreboardLoader();
		inventoryLoader();
		weaponLoader();
	}

	/**
	 * Clears the addons information cached.
	 */
	public void addonsClear() {
		// clear the inventory loader
		inventoryLoader.clear();
		// clear the ammunition addons
		ammunitionAddon.clear();
		// clear the weapon addons
		weaponAddon.clear();
		weaponLoader.clear();
	}

	/**
	 * Loads the scoreboard.
	 */
	public void scoreboardLoader() {
		scoreboardAddon = new ScoreboardAddon(fileManager);
	}

	/**
	 * Loads the inventory handler.
	 */
	public void inventoryLoader() {
		InventoryAddon.setItemSourceProvider(gangland);

		evaluator = new BooleanExpressionEvaluator(gangland);

		InventoryAddon.setConditionEvaluator(evaluator);

		inventoryLoader = new InventoryLoader(gangland);

		inventoryLoader.addExpectedFile(new FileHandler(gangland, "gang_info", "inventory", ".yml"));
		inventoryLoader.addExpectedFile(new FileHandler(gangland, "phone", "inventory", ".yml"));
		inventoryLoader.addExpectedFile(new FileHandler(gangland, "phone_gang", "inventory", ".yml"));

		inventoryLoader.initialize();
	}

	public void weaponLoader() {
		ammunitionAddon = new AmmunitionAddon(fileManager);

		if (weaponAddon == null) {
			weaponAddon = new WeaponAddon();
		}

		weaponLoader = new WeaponLoader(gangland);

		weaponLoader.addExpectedFile(new FileHandler(gangland, "rifle", "weapon", ".yml"));

		weaponLoader.initialize();
	}

	public <E> E getInstanceFromTables(Class<E> clazz, List<Table<?>> tables) {
		return tables.stream()
				.filter(clazz::isInstance)
				.map(clazz::cast)
				.findFirst()
				.orElseThrow(() -> new RuntimeException("There was a problem finding class, " + clazz.getName()));
	}

	private void databases() {
		int type;

		if (SettingAddon.getDatabaseType().equalsIgnoreCase("mysql")) type = DatabaseHandler.MYSQL;
		else type = DatabaseHandler.SQLITE;

		// Primary database
		GanglandDatabase ganglandDatabase = new GanglandDatabase(gangland);
		ganglandDatabase.setType(type);
		databaseManager.addDatabase(ganglandDatabase);
	}

	private void events() {
		String basePackage = this.getClass().getPackage().getName();
		// Register components first (order matters!)
		// Register all the managers and services that listeners might need
		DependencyContainer dependencyContainer = listenerManager.getDependencyContainer();

//		listenerManager.scanAndRegisterComponents(basePackage, gangland);

		dependencyContainer.registerInstance(JavaPlugin.class, gangland);
		dependencyContainer.registerInstance(UserManager.class, userManager);
		dependencyContainer.registerInstance(RankManager.class, rankManager);
		dependencyContainer.registerInstance(GangManager.class, gangManager);
		dependencyContainer.registerInstance(WeaponService.class, weaponManager);
		dependencyContainer.registerInstance(SignInteractionService.class, signManager.getSignService());
		dependencyContainer.registerInstance(RecoilCompatibility.class, compatibilityWorker.getRecoilCompatibility());

		listenerManager.scanAndRegisterListeners("me.luckyraven", gangland);

		// waypoint
		listenerManager.addEvent(new WaypointTeleport(new Waypoint("dummy")), ListenerPriority.NORMAL);
		// inventory
		new InventoryHandler(gangland, "dummy", 9, "dummy_inventory", false);
	}

	private void commands(Gangland gangland) {
		PluginCommand command = this.gangland.getCommand(prefix);

		if (command == null) return;

		// initial command
		command.setExecutor(commandManager);

		// sub commands
		// default plugin commands
		commandManager.addCommand(new BalanceCommand(gangland));
		commandManager.addCommand(new BankCommand(gangland));
		commandManager.addCommand(new EconomyCommand(gangland));
		commandManager.addCommand(new RankCommand(gangland));
		commandManager.addCommand(new BountyCommand(gangland));
		commandManager.addCommand(new LevelCommand(gangland));
		commandManager.addCommand(new WaypointCommand(gangland));
		commandManager.addCommand(new TeleportCommand(gangland));
		commandManager.addCommand(new WantedCommand(gangland));
		commandManager.addCommand(new WeaponCommand(gangland));
		commandManager.addCommand(new AmmunitionCommand(gangland));
		commandManager.addCommand(new DownloadResourceCommand(gangland));

		// gang commands
		if (SettingAddon.isGangEnabled()) {
			commandManager.addCommand(new GangCommand(gangland));
		}

		// debug commands
		commandManager.addCommand(new DebugCommand(gangland));
		commandManager.addCommand(new ComponentExecutorCommand(gangland));
		commandManager.addCommand(new ReadNBTCommand(gangland));
		commandManager.addCommand(new ReloadCommand(gangland));
		commandManager.addCommand(new TimerCommand(gangland));
		commandManager.addCommand(new DownloadPluginCommand(gangland));

		// Needs to be the final command to add all the help information
		commandManager.addCommand(new HelpCommand(gangland));

		// initialize the tab completer
		command.setTabCompleter(new CommandTabCompleter(gangland, CommandManager.getCommands()));
	}

}
