package me.luckyraven;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.luckyraven.command.CommandManager;
import me.luckyraven.command.CommandTabCompleter;
import me.luckyraven.command.data.InformationManager;
import me.luckyraven.command.sub.*;
import me.luckyraven.command.sub.bank.BankCommand;
import me.luckyraven.command.sub.bounty.BountyCommand;
import me.luckyraven.command.sub.cops.CopCommand;
import me.luckyraven.command.sub.cuff.CuffCommand;
import me.luckyraven.command.sub.cuff.UncuffCommand;
import me.luckyraven.command.sub.debug.ComponentExecutorCommand;
import me.luckyraven.command.sub.debug.DebugCommand;
import me.luckyraven.command.sub.debug.ReadNBTCommand;
import me.luckyraven.command.sub.debug.TimerCommand;
import me.luckyraven.command.sub.gang.GangCommand;
import me.luckyraven.command.sub.jail.JailCommand;
import me.luckyraven.command.sub.lootchest.LootChestWandCommand;
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
import me.luckyraven.copsncrooks.bounty.BountySettings;
import me.luckyraven.copsncrooks.combo.KillCombo;
import me.luckyraven.copsncrooks.detainment.DetainedPlayer;
import me.luckyraven.copsncrooks.detainment.DetainmentRegistry;
import me.luckyraven.copsncrooks.detainment.DetainmentService;
import me.luckyraven.copsncrooks.entity.EntityMarkManager;
import me.luckyraven.copsncrooks.jail.Jail;
import me.luckyraven.copsncrooks.jail.JailRegistry;
import me.luckyraven.copsncrooks.jail.JailService;
import me.luckyraven.copsncrooks.police.CopManager;
import me.luckyraven.copsncrooks.police.CopService;
import me.luckyraven.copsncrooks.police.config.CopLoader;
import me.luckyraven.copsncrooks.police.config.CopSettings;
import me.luckyraven.copsncrooks.police.spawn.CopSpawnManager;
import me.luckyraven.copsncrooks.police.spawn.CopSpawner;
import me.luckyraven.copsncrooks.wanted.WantedSettings;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.member.MemberManager;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.data.permission.PermissionManager;
import me.luckyraven.data.permission.PermissionWorker;
import me.luckyraven.data.placeholder.PlaceholderService;
import me.luckyraven.data.placeholder.worker.GanglandPlaceholder;
import me.luckyraven.data.plugin.PluginManager;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.data.teleportation.Waypoint;
import me.luckyraven.data.teleportation.WaypointManager;
import me.luckyraven.data.teleportation.WaypointTeleport;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.database.GanglandDatabaseSettings;
import me.luckyraven.database.repositories.lootchest.LootChestRepository;
import me.luckyraven.database.tables.player.MemberTable;
import me.luckyraven.exception.PluginException;
import me.luckyraven.file.LanguageLoader;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.file.configuration.copsncrooks.GanglandBountySettings;
import me.luckyraven.file.configuration.copsncrooks.GanglandCopSettings;
import me.luckyraven.file.configuration.copsncrooks.GanglandWantedSettings;
import me.luckyraven.file.configuration.inventory.InventoryAddon;
import me.luckyraven.file.configuration.inventory.InventoryLoader;
import me.luckyraven.file.configuration.lootchest.GanglandLootChestMessages;
import me.luckyraven.file.configuration.lootchest.LootChestSettings;
import me.luckyraven.file.configuration.weapon.GanglandRepairMessages;
import me.luckyraven.file.configuration.weapon.WeaponLoader;
import me.luckyraven.inventory.condition.BooleanExpressionEvaluator;
import me.luckyraven.inventory.condition.ConditionEvaluator;
import me.luckyraven.item.ItemParserManager;
import me.luckyraven.item.configuration.UniqueItemAddon;
import me.luckyraven.item.configuration.WearableAddon;
import me.luckyraven.listener.ListenerManager;
import me.luckyraven.lootchest.GanglandLootItemProvider;
import me.luckyraven.lootchest.LootChestManager;
import me.luckyraven.lootchest.LootChestService;
import me.luckyraven.lootchest.config.LootChestLoader;
import me.luckyraven.lootchest.data.LootChestData;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.persistence.database.DatabaseHandler;
import me.luckyraven.persistence.database.DatabaseManager;
import me.luckyraven.persistence.database.DatabaseSettingsProvider;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.persistence.repository.IRepository;
import me.luckyraven.persistence.repository.RepositoryRegistry;
import me.luckyraven.scoreboard.ScoreboardManager;
import me.luckyraven.scoreboard.configuration.ScoreboardAddon;
import me.luckyraven.sign.GanglandSignInformation;
import me.luckyraven.sign.SignManager;
import me.luckyraven.sign.bulk.BulkActionManager;
import me.luckyraven.sign.registry.SignFormatRegistry;
import me.luckyraven.sign.registry.SignTypeRegistry;
import me.luckyraven.sign.service.SignFormatterService;
import me.luckyraven.sign.service.SignInformation;
import me.luckyraven.sign.service.SignInteraction;
import me.luckyraven.sign.service.SignInteractionService;
import me.luckyraven.util.TimeMessages;
import me.luckyraven.util.autowire.DependencyContainer;
import me.luckyraven.util.hologram.HologramService;
import me.luckyraven.util.listener.ListenerPriority;
import me.luckyraven.util.placeholder.replacer.Replacer;
import me.luckyraven.weapon.WeaponManager;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.configuration.AmmunitionAddon;
import me.luckyraven.weapon.configuration.WeaponAddon;
import me.luckyraven.weapon.projectile.BlockDamageManager;
import me.luckyraven.weapon.repair.RepairManager;
import me.luckyraven.weapon.repair.anvil.RepairAnvilGui;
import me.luckyraven.weapon.repair.config.RepairLoader;
import me.luckyraven.weapon.wearable.WearableService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Getter
public final class Initializer {

	@Getter(value = AccessLevel.NONE)
	private final Gangland gangland;

	// on plugin load
	private final InformationManager informationManager;
	private final VersionSetup       versionSetup;
	private final CompatibilitySetup compatibilitySetup;
	private final PlaceholderService placeholderService;

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
	private BulkActionManager          bulkActionManager;
	private EntityMarkManager          entityMarkManager;
	private ItemParserManager          itemParserManager;
	private HologramService            hologramService;
	private LootChestManager           lootChestManager;
	private BlockDamageManager         blockDamageManager;
	private CopService                 copService;
	private KillCombo                  killCombo;
	private DetainmentService          detainmentService;
	private DetainmentRegistry         detainmentRegistry;
	private JailService                jailService;
	private CopSpawnManager            copSpawnManager;
	// Addons
	private Settings                   settings;
	private ScoreboardAddon            scoreboardAddon;
	private AmmunitionAddon            ammunitionAddon;
	private WeaponAddon                weaponAddon;
	private UniqueItemAddon            uniqueItemAddon;
	private WearableAddon              wearableAddon;
	// Loader
	private LanguageLoader             languageLoader;
	private InventoryLoader            inventoryLoader;
	private WeaponLoader               weaponLoader;
	private LootChestLoader            lootChestLoader;
	private CopLoader                  copLoader;
	private RepairLoader               repairLoader;
	private RepairManager              repairManager;
	private RepairAnvilGui             repairAnvilGui;
	// Database
	private GanglandDatabase           ganglandDatabase;
	// Placeholder
	private GanglandPlaceholder        placeholder;
	// Compatibility
	private CompatibilityWorker        compatibilityWorker;
	// Condition Evaluator
	private ConditionEvaluator         evaluator;
	// Settings extension
	private SignInformation            signInformation;
	private BountySettings             bountySettings;
	private WantedSettings             wantedSettings;
	private CopSettings                copSettings;

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
	 * Initializes the rest of the necessary classes that would conflict with the first object initialization.
	 * </b>
	 * This is used to safeguard the first initialization.
	 */
	public void postInitialize() {
		// Compatibility loader
		compatibilityWorker = new CompatibilityWorker(gangland.getViaAPI(), compatibilitySetup);

		// permission manager
		var permissionWorker = new PermissionWorker(Gangland.FULL_PREFIX);

		permissionManager = new PermissionManager(permissionWorker);

		// File
		fileManager = new FileManager(gangland);
		files();

		// Database
		DatabaseSettingsProvider settings = new GanglandDatabaseSettings();
		databaseManager = new DatabaseManager(gangland, settings);
		databases(settings);
		databaseManager.initializeDatabases();

		// add all registered plugin permissions
		Set<Permission> permissions = Bukkit.getPluginManager().getPermissions();
		Set<String> ganglandPermissions = permissions.stream()
				.map(Permission::getName)
				.filter(permission -> permission.startsWith(Gangland.FULL_PREFIX))
				.collect(Collectors.toSet());

		permissionManager.addAllPermissions(ganglandPermissions);

		// settings extension
		signInformation = new GanglandSignInformation();
		bountySettings  = new GanglandBountySettings();
		wantedSettings  = new GanglandWantedSettings();
		copSettings     = new GanglandCopSettings();

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

		// wire data suppliers so repositories can flush memory on auto-save
		userManager.initialize();
		offlineUserManager.initialize();

		List<Table<?>> tables = ganglandDatabase.getTables();

		// plugin manager
		pluginManager = new PluginManager(gangland);
		pluginManager.initialize();

		// Rank manager
		rankManager = new RankManager(gangland);
		rankManager.initialize();

		// Gang manager
		gangManager   = new GangManager(gangland);
		memberManager = new MemberManager(gangland);

		// initialize the gang and member classes
		MemberTable memberTable = getInstanceFromTables(MemberTable.class, tables);

		gangManager.initialize();
		memberManager.initialize(memberTable, gangManager, rankManager);

		// Waypoint manager
		waypointManager = new WaypointManager(gangland);
		waypointManager.initialize();

		// Weapon manager
		weaponManager      = new WeaponManager(gangland);
		blockDamageManager = new BlockDamageManager(gangland);
		weaponManager.initialize();

		// sign manager
		signLoader();

		// entity mark manager
		entityMarkManager = new EntityMarkManager(gangland, Collections.emptyList(),
												  Settings.getDefaultCivilianEntities());

		// item parser
		itemParserManager = new ItemParserManager(weaponManager, ammunitionAddon, wearableAddon);

		// loot chest manager
		lootChestLoader();

		// kill combo
		killCombo = new KillCombo(gangland, Settings.getWantedKillCounter());

		// detainment
		detainment();

		// cop service
		copLoader();

		// repair system
		repairLoader();

		// Events
		listenerManager = new ListenerManager(gangland);
		events();
		listenerManager.registerEvents();

		// Commands
		commandManager = new CommandManager(gangland, Gangland.FULL_PREFIX, Gangland.SHORT_PREFIX);
		commands(gangland);

		// Placeholder
		placeholder = new GanglandPlaceholder(gangland, Gangland.FULL_PREFIX, Replacer.Closure.PERCENT);
	}

	/**
	 * Initializes the files and by default adds three types of files (even if not presented) to the registered files,
	 * and if they were not created, a new file would get created. Additionally, it loads the addons which help the
	 * plugin functionality work.
	 */
	public void files() {
		FileHandler settingsFile = new FileHandler(gangland, "settings", ".yml");
		fileManager.addFile(settingsFile, true);

		FileHandler scoreboardFile = new FileHandler(gangland, "scoreboard", ".yml");
		fileManager.addFile(scoreboardFile, true);

		FileHandler ammunitionFile = new FileHandler(gangland, "ammunition", ".yml");
		fileManager.addFile(ammunitionFile, true);

		FileHandler uniqueItemsFile = new FileHandler(gangland, "unique_items", ".yml");
		fileManager.addFile(uniqueItemsFile, true);

		FileHandler lootChestFile = new FileHandler(gangland, "loot-chests", "loot", ".yml");
		fileManager.addFile(lootChestFile, true);

		FileHandler tiersFile = new FileHandler(gangland, "tiers", "loot", ".yml");
		fileManager.addFile(tiersFile, true);

		FileHandler copsFile = new FileHandler(gangland, "cops", ".yml");
		fileManager.addFile(copsFile, true);

		FileHandler repairFile = new FileHandler(gangland, "repair", ".yml");
		fileManager.addFile(repairFile, true);

		FileHandler wearablesFile = new FileHandler(gangland, "wearables", ".yml");
		fileManager.addFile(wearablesFile, true);

		scoreboardManager = new ScoreboardManager(gangland);

		addonsLoader();
	}

	/**
	 * Helps the plugin features to properly load.
	 */
	public void addonsLoader() {
		// initialize settings addon
		settings = new Settings(fileManager);
		settings.initialize();

		// initialize language addon
		languageLoader = new LanguageLoader(gangland);
		languageLoader.initialize();

		Messages.setMessageConfiguration(languageLoader.getMessage());
		TimeMessages.initialize();

		// initialize scoreboard addon
		scoreboardLoader();

		// initialize inventory addon
		inventoryLoader();

		// initialize weapon addon
		weaponLoader();

		// initialize unique item addon
		if (uniqueItemAddon == null) {
			uniqueItemAddon = new UniqueItemAddon(permissionManager, fileManager);
		}

		uniqueItemAddon.initialize();

		// initialize wearable addon
		if (wearableAddon == null) {
			wearableAddon = new WearableAddon(permissionManager, fileManager);
		}

		wearableAddon.initialize();
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
		// clear the unique item addons
		uniqueItemAddon.clear();
		// clear the wearable addons
		wearableAddon.clear();
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

		evaluator = new BooleanExpressionEvaluator(placeholderService);

		InventoryAddon.setConditionEvaluator(evaluator);

		inventoryLoader = new InventoryLoader(gangland);

		inventoryLoader.addExpectedFile(new FileHandler(gangland, "gang_info", "inventory", ".yml"));
		inventoryLoader.addExpectedFile(new FileHandler(gangland, "phone", "inventory", ".yml"));
		inventoryLoader.addExpectedFile(new FileHandler(gangland, "phone_gang", "inventory", ".yml"));

		inventoryLoader.initialize();
	}

	public void lootChestLoader() {
		if (hologramService == null) {
			hologramService = new HologramService(gangland);
		}

		if (lootChestManager == null) {
			lootChestManager = new LootChestManager(gangland, Gangland.FULL_PREFIX, hologramService);
		}

		RepositoryRegistry         repositoryRegistry  = ganglandDatabase.getRepositoryRegistry();
		IRepository<LootChestData> lootChestRepository = repositoryRegistry.getRepository(LootChestData.class);

		if (!(lootChestRepository instanceof LootChestRepository repo)) {
			String message = "LootChestData repository is not initialized!";

			log.error(message);
			throw new PluginException(message);
		}

		lootChestManager.initialize(repo, false);

		var provider = new LootChestSettings();
		lootChestLoader = new LootChestLoader(gangland, lootChestManager, provider);

		lootChestLoader.load(false, null, fileManager);

		// set the item provider so loot can be generated
		var itemProvider = new GanglandLootItemProvider(weaponManager, ammunitionAddon, uniqueItemAddon);
		lootChestManager.setItemProvider(itemProvider);

		lootChestManager.setMessagesProvider(new GanglandLootChestMessages());
	}

	public void copLoader() {
		copLoader = new CopLoader(gangland, itemParserManager.getParser(), new GanglandCopSettings());

		copLoader.load(false, null, fileManager);

		copService = new CopService();
		IRepository<CopSpawner> repository = ganglandDatabase.getRepositoryRegistry().getRepository(CopSpawner.class);
		copService.initialize(gangland, copLoader.getLoadedProvider(), entityMarkManager, weaponManager, repository,
							  detainmentService);
		copSpawnManager = copService.getCopManager().getSpawnManager();
	}

	public void repairLoader() {
		repairLoader  = new RepairLoader(gangland);
		repairManager = new RepairManager();

		repairLoader.load(false, config -> repairManager.load(config), fileManager);

		repairManager.setMessages(new GanglandRepairMessages());
		repairAnvilGui = new RepairAnvilGui(gangland, repairManager);
	}

	public void weaponLoader() {
		if (ammunitionAddon == null) {
			ammunitionAddon = new AmmunitionAddon(fileManager);
		}

		ammunitionAddon.initialize();

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

	private void detainment() {
		JailRegistry jailRegistry = new JailRegistry();

		RepositoryRegistry          repositoryRegistry   = ganglandDatabase.getRepositoryRegistry();
		IRepository<Jail>           jailRepository       = repositoryRegistry.getRepository(Jail.class);
		IRepository<DetainedPlayer> detainmentRepository = repositoryRegistry.getRepository(DetainedPlayer.class);

		jailService = new JailService(jailRegistry, jailRepository);

		detainmentRegistry = new DetainmentRegistry(detainmentRepository, jailRegistry);
		detainmentService  = new DetainmentService(gangland, detainmentRegistry, jailService,
												   jailService.getJailRegistry(), Gangland.FULL_PREFIX);
	}

	private void signLoader() {
		SignTypeRegistry     registry         = new SignTypeRegistry();
		SignFormatRegistry   formatRegistry   = new SignFormatRegistry();
		SignFormatterService formatterService = new SignFormatterService(formatRegistry);

		String signPrefix = Gangland.SHORT_PREFIX + "-";

		SignInteraction signInteraction = new SignInteraction(signPrefix, registry, formatterService, signInformation);
		bulkActionManager = new BulkActionManager(gangland, signInformation);

		signManager = new SignManager(gangland, Gangland.SHORT_PREFIX, registry, signInteraction);

		signManager.initialize();
	}

	private void databases(DatabaseSettingsProvider settings) {
		int type;

		if (Settings.getDatabaseType().equalsIgnoreCase("mysql")) type = DatabaseHandler.MYSQL;
		else type = DatabaseHandler.SQLITE;

		// Primary database
		GanglandDatabase ganglandDatabase = new GanglandDatabase(gangland, Gangland.FULL_PREFIX, settings);
		ganglandDatabase.setType(type);

		// Scan and register all repositories BEFORE adding to database manager
		RepositoryRegistry repositoryRegistry = ganglandDatabase.getRepositoryRegistry();
		repositoryRegistry.scanAndRegisterRepositories("me.luckyraven.database.repositories");

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
		dependencyContainer.registerInstance(WearableService.class, wearableAddon);
		dependencyContainer.registerInstance(SignInteractionService.class, signManager.getSignService());
		dependencyContainer.registerInstance(BulkActionManager.class, bulkActionManager);
		dependencyContainer.registerInstance(LootChestService.class, lootChestManager);
		dependencyContainer.registerInstance(RecoilCompatibility.class, compatibilityWorker.getRecoilCompatibility());
		dependencyContainer.registerInstance(SignInformation.class, signInformation);
		dependencyContainer.registerInstance(HologramService.class, hologramService);
		dependencyContainer.registerInstance(BlockDamageManager.class, blockDamageManager);
		dependencyContainer.registerInstance(RepairManager.class, repairManager);
		dependencyContainer.registerInstance(RepairAnvilGui.class, repairAnvilGui);
		dependencyContainer.registerInstance(CopManager.class, copService.getCopManager());
		dependencyContainer.registerInstance(KillCombo.class, killCombo);
		dependencyContainer.registerInstance(DetainmentService.class, detainmentService);
		dependencyContainer.registerInstance(JailService.class, jailService);

		listenerManager.scanAndRegisterListeners("me.luckyraven", gangland);

		// waypoint
		Waypoint         dummy         = new Waypoint("dummy", Gangland.FULL_PREFIX);
		WaypointTeleport dummyTeleport = new WaypointTeleport(dummy);

		listenerManager.addEvent(dummyTeleport, ListenerPriority.NORMAL);
	}

	private void commands(Gangland gangland) {
		PluginCommand command = this.gangland.getCommand(Gangland.SHORT_PREFIX);

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
		commandManager.addCommand(new LootChestWandCommand(gangland));
		commandManager.addCommand(new CuffCommand(gangland));
		commandManager.addCommand(new UncuffCommand(gangland));
		commandManager.addCommand(new JailCommand(gangland));
		commandManager.addCommand(new CopCommand(gangland));

		// gang commands
		if (Settings.isGangEnabled()) {
			commandManager.addCommand(new GangCommand(gangland));
		}

		// debug commands
		commandManager.addCommand(new DebugCommand(gangland));
		commandManager.addCommand(new ComponentExecutorCommand(gangland));
		commandManager.addCommand(new ReadNBTCommand(gangland));
		commandManager.addCommand(new ReloadCommand(gangland));
		commandManager.addCommand(new TimerCommand(gangland));
		commandManager.addCommand(new DownloadPluginCommand(gangland, Gangland.SHORT_PREFIX));

		// Needs to be the final command to add all the help information
		commandManager.addCommand(new HelpCommand(gangland));

		// initialize the tab completer
		command.setTabCompleter(new CommandTabCompleter(CommandManager.getCommands()));
	}
}
