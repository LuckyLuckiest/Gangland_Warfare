package me.luckyraven;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import me.luckyraven.command.CommandManager;
import me.luckyraven.command.CommandTabCompleter;
import me.luckyraven.command.data.InformationManager;
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
import me.luckyraven.copsncrooks.npc.civilian.CivilianService;
import me.luckyraven.copsncrooks.npc.civilian.config.CivilianSettings;
import me.luckyraven.copsncrooks.npc.civilian.config.CiviliansLoader;
import me.luckyraven.copsncrooks.npc.civilian.spawn.CivilianSpawnManager;
import me.luckyraven.copsncrooks.npc.civilian.spawn.CivilianSpawner;
import me.luckyraven.copsncrooks.npc.police.CopManager;
import me.luckyraven.copsncrooks.npc.police.CopService;
import me.luckyraven.copsncrooks.npc.police.config.CopLoader;
import me.luckyraven.copsncrooks.npc.police.config.CopSettings;
import me.luckyraven.copsncrooks.npc.police.spawn.CopSpawnManager;
import me.luckyraven.copsncrooks.npc.police.spawn.CopSpawner;
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
import me.luckyraven.file.configuration.GadgetPhysicsConfigImpl;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.file.configuration.copsncrooks.*;
import me.luckyraven.file.configuration.inventory.InventoryAddon;
import me.luckyraven.file.configuration.inventory.InventoryLoader;
import me.luckyraven.file.configuration.lootchest.GanglandLootChestMessages;
import me.luckyraven.file.configuration.lootchest.LootChestSettings;
import me.luckyraven.file.configuration.weapon.GanglandBlockRegenerationSettings;
import me.luckyraven.file.configuration.weapon.GanglandRepairMessages;
import me.luckyraven.file.configuration.weapon.WeaponLoader;
import me.luckyraven.gadget.car.CarManager;
import me.luckyraven.gadget.car.CarService;
import me.luckyraven.gadget.car.ParkedCar;
import me.luckyraven.gadget.car.config.CarAddon;
import me.luckyraven.gadget.car.vehicle.VehicleRegistry;
import me.luckyraven.gadget.config.GadgetPhysicsConfig;
import me.luckyraven.gadget.fuel.FuelService;
import me.luckyraven.gadget.jetpack.JetpackService;
import me.luckyraven.gadget.repair.GanglandRepairService;
import me.luckyraven.gadget.repair.RepairManager;
import me.luckyraven.gadget.repair.anvil.RepairAnvilGui;
import me.luckyraven.gadget.repair.config.RepairLoader;
import me.luckyraven.gadget.wearable.WearableAddon;
import me.luckyraven.hologram.HologramService;
import me.luckyraven.inventory.condition.BooleanExpressionEvaluator;
import me.luckyraven.inventory.condition.ConditionEvaluator;
import me.luckyraven.inventory.handler.SlotItemFactory;
import me.luckyraven.item.ItemParser;
import me.luckyraven.item.ItemParserManager;
import me.luckyraven.item.configuration.UniqueItemAddon;
import me.luckyraven.item.contract.*;
import me.luckyraven.item.money.MoneyAddon;
import me.luckyraven.item.money.MoneyDepositService;
import me.luckyraven.item.money.MoneyDropClassifier;
import me.luckyraven.listener.ListenerManager;
import me.luckyraven.lootchest.GanglandLootItemProvider;
import me.luckyraven.lootchest.LootChestManager;
import me.luckyraven.lootchest.LootChestService;
import me.luckyraven.lootchest.config.LootChestLoader;
import me.luckyraven.lootchest.data.LootChestData;
import me.luckyraven.money.GanglandMoneyDepositService;
import me.luckyraven.money.GanglandMoneyDropClassifier;
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
import me.luckyraven.util.listener.ListenerPriority;
import me.luckyraven.util.placeholder.replacer.Replacer;
import me.luckyraven.weapon.WeaponManager;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.ammo.AmmunitionManager;
import me.luckyraven.weapon.configuration.AmmunitionAddon;
import me.luckyraven.weapon.configuration.WeaponAddon;
import me.luckyraven.weapon.modifiers.BlockDamageManager;
import me.luckyraven.weapon.raytrace.WeaponRaytracer;
import me.luckyraven.weapon.raytrace.WeaponVisualSpawner;
import me.luckyraven.weapon.wearable.WearableService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
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
	private WeaponVisualSpawner        weaponVisualSpawner;
	private WeaponRaytracer            weaponRaytracer;
	private CopService                 copService;
	private CopSpawnManager            copSpawnManager;
	private CivilianService            civilianService;
	private CivilianSpawnManager       civilianSpawnManager;
	private KillCombo                  killCombo;
	private DetainmentService          detainmentService;
	private DetainmentRegistry         detainmentRegistry;
	private JailService                jailService;
	// Addons
	private Settings                   settings;
	private ScoreboardAddon            scoreboardAddon;
	private AmmunitionAddon            ammunitionAddon;
	private AmmunitionManager          ammunitionManager;
	private WeaponAddon                weaponAddon;
	private UniqueItemAddon            uniqueItemAddon;
	private WearableAddon              wearableAddon;
	private MoneyAddon                 moneyAddon;
	// Loader
	private LanguageLoader             languageLoader;
	private InventoryLoader            inventoryLoader;
	private WeaponLoader               weaponLoader;
	private LootChestLoader            lootChestLoader;
	private CopLoader                  copLoader;
	private CiviliansLoader            civiliansLoader;
	private RepairLoader               repairLoader;
	private RepairManager              repairManager;
	private RepairAnvilGui             repairAnvilGui;
	// Gadgets
	private CarAddon                   carAddon;
	private CarService                 carService;
	private GadgetPhysicsConfig        gadgetPhysicsConfig;
	// Fuel & Jetpack
	private FuelService                fuelService;
	private JetpackService             jetpackService;
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
	private CivilianSettings           civilianSettings;

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
		signInformation  = new GanglandSignInformation();
		bountySettings   = new GanglandBountySettings();
		wantedSettings   = new GanglandWantedSettings();
		copSettings      = new GanglandCopSettings();
		civilianSettings = new GanglandCivilianSettings();

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
		weaponManager       = new WeaponManager(gangland);
		blockDamageManager  = new BlockDamageManager(gangland, new GanglandBlockRegenerationSettings());
		weaponVisualSpawner = new WeaponVisualSpawner();
		weaponRaytracer     = new WeaponRaytracer(weaponManager, wearableAddon, blockDamageManager,
		                                          weaponVisualSpawner);
		weaponManager.initialize();

		// sign manager
		signLoader();

		// item parser (must be before civilians loader — weapon pool parsing needs it,
		// and before inventoryLoader.initialize() — slot YAML parses prefixed item refs via the resolver)
		itemParserManager = new ItemParserManager(weaponManager, ammunitionManager, wearableAddon, carAddon,
		                                          moneyAddon);

		// inventory loader: actual file load is deferred to here so the slot resolver can dereference
		// itemParserManager (registered earlier in inventoryLoader() but only invoked once load() runs).
		inventoryLoader.initialize();

		// civilians loader (reads civilians.yml; resolves weapon pools via ItemParser)
		civiliansLoader = new CiviliansLoader(gangland, itemParserManager.getParser(), civilianSettings);
		civiliansLoader.load(false, null, fileManager);

		// entity mark manager (uses the loaded default entity lists instead of settings.yml)
		entityMarkManager = new EntityMarkManager(gangland,
		                                          civiliansLoader.getLoadedConfig().defaultPoliceEntities(),
		                                          civiliansLoader.getLoadedConfig().defaultCivilianEntities());

		// loot chest manager
		lootChestLoader();

		// kill combo
		killCombo = new KillCombo(gangland, Settings.getWantedKillCounter());

		// detainment
		detainment();

		// cop service
		copLoader();

		// civilian service
		civilianLoader();

		// wire civilian service into cop manager so cops can pursue wanted hostile civilians
		copService.getCopManager().setCivilianService(civilianService);

		// repair system
		repairLoader();

		// car service
		carServiceInit();

		// jetpack service
		jetpackServiceInit();

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

		FileHandler civiliansFile = new FileHandler(gangland, "civilians", ".yml");
		fileManager.addFile(civiliansFile, true);

		FileHandler repairFile = new FileHandler(gangland, "repair", ".yml");
		fileManager.addFile(repairFile, true);

		FileHandler wearablesFile = new FileHandler(gangland, "wearables", ".yml");
		fileManager.addFile(wearablesFile, true);

		FileHandler carsFile = new FileHandler(gangland, "cars", ".yml");
		fileManager.addFile(carsFile, true);

		FileHandler moneyFile = new FileHandler(gangland, "money", ".yml");
		fileManager.addFile(moneyFile, true);

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

		// initialize fuel service
		if (fuelService == null) {
			fuelService = new me.luckyraven.gadget.fuel.FuelService();
		}

		// initialize unique item addon (registers fuel definitions into FuelService)
		if (uniqueItemAddon == null) {
			uniqueItemAddon = new UniqueItemAddon(permissionManager, fileManager, fuelService);
		}

		uniqueItemAddon.initialize();

		// initialize wearable addon
		if (wearableAddon == null) {
			wearableAddon = new WearableAddon(permissionManager::addPermission, fileManager);
		}

		wearableAddon.initialize();

		gadgetPhysicsConfig = new GadgetPhysicsConfigImpl();

		// initialize car addon
		if (carAddon == null) {
			carAddon = new CarAddon(permissionManager::addPermission, fileManager);
		}

		carAddon.initialize();

		// initialize money addon (cash drop variations + per-source rules)
		if (moneyAddon == null) {
			moneyAddon = new MoneyAddon();
		}

		FileHandler moneyFile = fileManager.getFile("money");
		if (moneyFile != null && moneyFile.getFileConfiguration() != null) {
			moneyAddon.load(moneyFile.getFileConfiguration());
		}
		moneyAddon.setEnabled(Settings.isMoneyDropEnabled());
	}

	/**
	 * Clears the addons information cached.
	 */
	public void addonsClear() {
		// clear the inventory loader
		inventoryLoader.clear();
		// clear the ammunition addons
		ammunitionManager.clear();
		// clear the weapon addons
		weaponAddon.clear();
		weaponLoader.clear();
		// clear the unique item addons
		uniqueItemAddon.clear();
		// clear the wearable addons
		wearableAddon.clear();
		// clear the car addons
		carAddon.clear();
		// clear the money addon
		if (moneyAddon != null) moneyAddon.clear();
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

		// Lets slot YAML reference prefixed items (weapon:awp, wearable:police_vest, ammo:9mm, unique:phone, …)
		// via the central ItemParser. Must run before any inventory file is read.
		// Deferred lookup: itemParserManager is created later in postInitialize (after addonsLoader returns),
		// so the lambda must dereference the field on each invocation, not capture it at registration time.
		SlotItemFactory.setItemResolver(slot -> itemParserManager.getParser().parse(slot));

		evaluator = new BooleanExpressionEvaluator(placeholderService);

		InventoryAddon.setConditionEvaluator(evaluator);

		inventoryLoader = new InventoryLoader(gangland);

		inventoryLoader.addExpectedFile(new FileHandler(gangland, "gang_info", "inventory", ".yml"));
		inventoryLoader.addExpectedFile(new FileHandler(gangland, "phone", "inventory", ".yml"));
		inventoryLoader.addExpectedFile(new FileHandler(gangland, "phone_gang", "inventory", ".yml"));

		// inventoryLoader.initialize() triggers slot YAML parsing, which invokes the item resolver registered
		// above; that resolver dereferences itemParserManager. On first startup, itemParserManager doesn't exist
		// yet (it's constructed later in postInitialize), so we skip initialize() here and let postInitialize
		// call it explicitly after the item parser is wired. On reload, itemParserManager is already alive, so
		// it's safe — and necessary, since ReloadPlugin calls inventoryLoader() directly.
		if (itemParserManager != null) {
			inventoryLoader.initialize();
		}
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
		var itemProvider = new GanglandLootItemProvider(weaponManager, ammunitionManager, uniqueItemAddon,
		                                                wearableAddon, carAddon);
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

	public void civilianLoader() {
		civilianService = new CivilianService();
		IRepository<CivilianSpawner> repository = ganglandDatabase.getRepositoryRegistry()
		                                                          .getRepository(CivilianSpawner.class);
		var ganglandCivilianSpawnConfigProvider = new GanglandCivilianSpawnConfigProvider();

		civilianService.initialize(gangland, civiliansLoader.getLoadedConfig(), entityMarkManager, repository,
		                           civilianSettings, ganglandCivilianSpawnConfigProvider, itemParserManager.getParser(),
		                           weaponManager);
		civilianSpawnManager = civilianService.getSpawnManager();
	}

	public void repairLoader() {
		repairLoader  = new RepairLoader(gangland);
		repairManager = new RepairManager();

		repairLoader.load(false, config -> repairManager.load(config), fileManager);

		repairManager.setMessages(new GanglandRepairMessages());
		repairAnvilGui = new RepairAnvilGui(gangland, repairManager);
	}

	public void carServiceInit() {
		IRepository<ParkedCar> parkedCarRepository = ganglandDatabase.getRepositoryRegistry()
		                                                             .getRepository(ParkedCar.class);
		carService = new CarService(carAddon, new VehicleRegistry(), gangland, parkedCarRepository, fuelService,
		                            gadgetPhysicsConfig);
		parkedCarRepository.setDataSupplier(() -> new ArrayList<>(carService.getParkedCarRecords().values()));
		carService.reloadParkedVehicles();
	}

	public void jetpackServiceInit() {
		if (jetpackService == null) {
			jetpackService = new JetpackService(fuelService, gangland, gadgetPhysicsConfig, wearableAddon);
		}
	}

	public void weaponLoader() {
		if (ammunitionAddon == null) {
			ammunitionAddon = new AmmunitionAddon(fileManager);
		}

		if (ammunitionManager == null) {
			ammunitionManager = new AmmunitionManager();
		}

		ammunitionAddon.initialize(ammunitionManager);

		if (weaponAddon == null) {
			weaponAddon = new WeaponAddon();
		}

		weaponLoader = new WeaponLoader(gangland);

		weaponLoader.addExpectedFile(new FileHandler(gangland, "rifle", "weapon", ".yml"));
		weaponLoader.addExpectedFile(new FileHandler(gangland, "grenade", "weapon", ".yml"));
		weaponLoader.addExpectedFile(new FileHandler(gangland, "knife", "weapon", ".yml"));
		weaponLoader.addExpectedFile(new FileHandler(gangland, "flamethrower", "weapon", ".yml"));
		weaponLoader.addExpectedFile(new FileHandler(gangland, "syringe_gun", "weapon", ".yml"));
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
		dependencyContainer.registerInstance(WeaponVisualSpawner.class, weaponVisualSpawner);
		dependencyContainer.registerInstance(WeaponRaytracer.class, weaponRaytracer);

		// Also publish via Bukkit ServicesManager so cross-module consumers (cops-n-crooks NPCs)
		// can resolve the raytracer without threading it through their factory chains.
		Bukkit.getServicesManager().register(WeaponRaytracer.class, weaponRaytracer, gangland, ServicePriority.Normal);
		dependencyContainer.registerInstance(RepairManager.class, repairManager);
		dependencyContainer.registerInstance(RepairAnvilGui.class, repairAnvilGui);
		dependencyContainer.registerInstance(CopManager.class, copService.getCopManager());
		dependencyContainer.registerInstance(KillCombo.class, killCombo);
		dependencyContainer.registerInstance(DetainmentService.class, detainmentService);
		dependencyContainer.registerInstance(JailService.class, jailService);
		dependencyContainer.registerInstance(CarManager.class, carAddon);
		dependencyContainer.registerInstance(CarService.class, carService);
		dependencyContainer.registerInstance(FuelService.class, fuelService);
		// also expose under the gangland-item interface key so the moved fuel listeners can resolve via DI
		dependencyContainer.registerInstance(me.luckyraven.item.fuel.FuelService.class, fuelService);
		dependencyContainer.registerInstance(JetpackService.class, jetpackService);
		dependencyContainer.registerInstance(CivilianService.class, civilianService);
		dependencyContainer.registerInstance(ItemParser.class, itemParserManager.getParser());

		// gangland-item contract wiring (used by the listeners that moved out of impl/gadget)
		dependencyContainer.registerInstance(UniqueItemAddon.class, uniqueItemAddon);
		dependencyContainer.registerInstance(UniqueItemRegistry.class, uniqueItemAddon);
		dependencyContainer.registerInstance(UniqueItemInteractionService.class,
		                                     new GanglandUniqueItemInteractionService(gangland));
		dependencyContainer.registerInstance(WearableEquipService.class, wearableAddon);
		dependencyContainer.registerInstance(RepairService.class,
		                                     new GanglandRepairService(gangland, weaponManager, repairAnvilGui));

		// money drop wiring
		dependencyContainer.registerInstance(MoneyAddon.class, moneyAddon);
		dependencyContainer.registerInstance(MoneyDepositService.class,
		                                     new GanglandMoneyDepositService(userManager, moneyAddon));
		dependencyContainer.registerInstance(MoneyDropClassifier.class,
		                                     new GanglandMoneyDropClassifier(copService, civilianService));

		listenerManager.scanAndRegisterListeners("me.luckyraven", gangland);

		// waypoint
		Waypoint         dummy         = new Waypoint("dummy", Gangland.FULL_PREFIX);
		WaypointTeleport dummyTeleport = new WaypointTeleport(dummy);

		listenerManager.addEvent(dummyTeleport, ListenerPriority.NORMAL);
	}

	private void commands(Gangland gangland) {
		PluginCommand command = this.gangland.getCommand(Gangland.SHORT_PREFIX);

		if (command == null) return;

		command.setExecutor(commandManager);
		commandManager.scanAndRegisterCommands("me.luckyraven.command.sub", gangland.getClass().getClassLoader());
		command.setTabCompleter(new CommandTabCompleter(CommandManager.getCommands()));
	}
}
