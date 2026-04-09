package me.luckyraven;

import lombok.AccessLevel;
import lombok.CustomLog;
import lombok.Getter;
import me.luckyraven.command.data.InformationManager;
import me.luckyraven.compatibility.CompatibilitySetup;
import me.luckyraven.compatibility.CompatibilityWorker;
import me.luckyraven.compatibility.VersionSetup;
import me.luckyraven.context.GanglandContext;
import me.luckyraven.copsncrooks.detainment.DetainmentRegistry;
import me.luckyraven.copsncrooks.jail.JailService;
import me.luckyraven.copsncrooks.npc.civilian.CivilianService;
import me.luckyraven.copsncrooks.npc.police.CopService;
import me.luckyraven.copsncrooks.npc.police.spawn.CopSpawnManager;
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
import me.luckyraven.data.teleportation.WaypointManager;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.database.GanglandDatabaseSettings;
import me.luckyraven.database.repositories.lootchest.LootChestRepository;
import me.luckyraven.exception.PluginException;
import me.luckyraven.file.LanguageLoader;
import me.luckyraven.file.configuration.GadgetPhysicsConfigImpl;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.MoneyAddonInitializer;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.file.configuration.inventory.InventoryAddon;
import me.luckyraven.file.configuration.inventory.InventoryLoader;
import me.luckyraven.file.configuration.inventory.itemsource.GangItemSourceProvider;
import me.luckyraven.file.configuration.lootchest.GanglandLootChestMessages;
import me.luckyraven.file.configuration.lootchest.LootChestSettings;
import me.luckyraven.file.configuration.weapon.WeaponLoader;
import me.luckyraven.gadget.car.CarService;
import me.luckyraven.gadget.car.config.CarAddon;
import me.luckyraven.gadget.config.GadgetPhysicsConfig;
import me.luckyraven.gadget.fuel.FuelService;
import me.luckyraven.gadget.jetpack.JetpackService;
import me.luckyraven.gadget.wearable.WearableAddon;
import me.luckyraven.hologram.HologramService;
import me.luckyraven.inventory.condition.BooleanExpressionEvaluator;
import me.luckyraven.inventory.condition.ConditionEvaluator;
import me.luckyraven.inventory.handler.SlotItemFactory;
import me.luckyraven.item.ItemParserManager;
import me.luckyraven.item.configuration.UniqueItemAddon;
import me.luckyraven.item.money.MoneyAddon;
import me.luckyraven.lootchest.LootChestManager;
import me.luckyraven.lootchest.config.LootChestLoader;
import me.luckyraven.lootchest.data.LootChestData;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.persistence.database.DatabaseManager;
import me.luckyraven.persistence.database.DatabaseSettingsProvider;
import me.luckyraven.persistence.repository.IRepository;
import me.luckyraven.persistence.repository.RepositoryRegistry;
import me.luckyraven.scoreboard.ScoreboardManager;
import me.luckyraven.scoreboard.configuration.ScoreboardAddon;
import me.luckyraven.util.TimeMessages;
import me.luckyraven.weapon.WeaponManager;
import me.luckyraven.weapon.ammo.AmmunitionManager;
import me.luckyraven.weapon.configuration.AmmunitionAddon;
import me.luckyraven.weapon.configuration.WeaponAddon;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
@CustomLog
public final class Initializer {

	@Getter(value = AccessLevel.NONE)
	private final Gangland gangland;

	// on plugin load
	private final InformationManager informationManager;
	private final VersionSetup       versionSetup;
	private final CompatibilitySetup compatibilitySetup;
	private final PlaceholderService placeholderService;

	// Single root DI container + bean factory orchestration. Owns the entire bean lifecycle from FILE phase through
	// COMMAND phase. Created in postInitialize() before any bean is constructed.
	private GanglandContext context;

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
	private RankManager                rankManager;
	private WaypointManager            waypointManager;
	private ScoreboardManager          scoreboardManager;
	private WeaponManager              weaponManager;
	private ItemParserManager          itemParserManager;
	private HologramService            hologramService;
	private LootChestManager           lootChestManager;
	private CopService                 copService;
	private CopSpawnManager            copSpawnManager;
	private CivilianService            civilianService;
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
	 * Bootstraps the entire plugin via {@link GanglandContext}. The legacy hand-rolled construction sequence has been
	 * replaced by a phased bean pipeline:
	 * <ol>
	 *     <li>Build the kernel objects ({@code FileManager}, {@code PermissionManager}, {@code CompatibilityWorker},
	 *     {@code DatabaseManager}) up-front for failure isolation.</li>
	 *     <li>Add the static file handlers ({@code settings.yml}, {@code cops.yml}, etc.) so {@code FileManager} can
	 *     load them when the FILE phase fires its initializers.</li>
	 *     <li>Construct the {@link GanglandContext} and seed every kernel object into its container so the
	 *     {@code @Configuration} classes under {@code me.luckyraven.config} can constructor-inject them.</li>
	 *     <li>Call {@link GanglandContext#bootstrap(Runnable)} which runs the FILE → DATABASE → CONFIG bean phases,
	 *     then invokes {@link #hydrateFromContext()} (so legacy {@code gangland.getInitializer().getX()} reads inside
	 *     bean {@code initialize()} methods resolve), then runs the LIFECYCLE pass, and finally drives the listener
	 *     and command scans against the same root container.</li>
	 * </ol>
	 *
	 * <p>The legacy helper methods ({@link #addonsLoader()}, {@link #scoreboardLoader()}, {@link #inventoryLoader()},
	 * {@link #weaponLoader()}, {@link #lootChestLoader()}, {@link #addonsClear()}) are
	 * intentionally retained because {@code ReloadPlugin} still calls them on soft reload. Their bodies remain a
	 * second source of truth for that path until the reload flow is migrated to the bean pipeline as well.
	 */
	public void postInitialize() {
		// --- Kernel objects: built up-front so the rest of the bootstrap can fail loudly if any of these break ---
		compatibilityWorker = new CompatibilityWorker(gangland.getViaAPI(), compatibilitySetup);

		var permissionWorker = new PermissionWorker(Gangland.FULL_PREFIX);
		permissionManager = new PermissionManager(permissionWorker);

		fileManager = new FileManager(gangland);
		seedFileHandlers();

		DatabaseSettingsProvider databaseSettings = new GanglandDatabaseSettings();
		databaseManager = new DatabaseManager(gangland, databaseSettings);

		// Scoreboard manager has no file dependencies and is referenced by multiple beans; pre-build it so we can
		// seed it as a kernel object. PlaceholderService exists from the load-phase constructor, so it can be
		// passed in here. The FILE-phase ScoreboardAddon is wired in later via GameplayConfig.wireScoreboardAddon().
		scoreboardManager = new ScoreboardManager(gangland, placeholderService);

		// --- Build the root container + bean orchestrator ---
		this.context = new GanglandContext(gangland);
		seedKernelBeans(databaseSettings);

		// --- Drive the phased bean pipeline ---
		// hydrateFromContext fires AFTER bean creation finishes and BEFORE the lifecycle pass, so legacy
		// gangland.getInitializer().getX() calls inside bean .initialize() methods (WeaponLoader, InventoryLoader)
		// resolve against this Initializer's now-populated fields.
		context.bootstrap(this::hydrateFromContext);
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

		FileHandler lootChestFile = new FileHandler(gangland, "loot_chests", "loot", ".yml");
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

		scoreboardManager = new ScoreboardManager(gangland, placeholderService);

		addonsLoader();
	}

	/**
	 * Helps the plugin features to properly load.
	 */
	public void addonsLoader() {
		// initialize settings addon
		settings = new Settings(fileManager);
		fileManager.registerInitializer(settings);
		fileManager.initializeAll();

		// initialize language addon
		languageLoader = new LanguageLoader(gangland, fileManager);
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

		// hand the placeholder resolver to each addon BEFORE its initialize() runs so every parsed instance
		// receives the resolver via its constructor / builder chain
		uniqueItemAddon.setPlaceholder(placeholderService);
		fileManager.registerInitializer(uniqueItemAddon);

		// initialize wearable addon
		if (wearableAddon == null) {
			wearableAddon = new WearableAddon(permissionManager::addPermission, fileManager);
		}

		wearableAddon.setPlaceholder(placeholderService);
		fileManager.registerInitializer(wearableAddon);

		gadgetPhysicsConfig = new GadgetPhysicsConfigImpl();

		// initialize car addon
		if (carAddon == null) {
			carAddon = new CarAddon(permissionManager::addPermission, fileManager);
		}

		carAddon.setPlaceholder(placeholderService);
		fileManager.registerInitializer(carAddon);

		fileManager.initializeAll();

		// initialize money addon (cash drop variations + per-source rules)
		if (moneyAddon == null) {
			moneyAddon = new MoneyAddon();
		}

		fileManager.registerInitializer(new MoneyAddonInitializer(fileManager, moneyAddon));
		fileManager.initializeAll();

		moneyAddon.setEnabled(Settings.isMoneyDropEnabled());
	}

	/**
	 * Clears the addons information cached.
	 */
	public void addonsClear() {
		// drop stale FileInitializer references so the next addonsLoader() starts with a clean orchestrator state
		fileManager.clearInitializers();

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
		fileManager.registerInitializer(scoreboardAddon);
		fileManager.initializeAll();
	}

	/**
	 * Loads the inventory handler.
	 */
	public void inventoryLoader() {
		// Phase 3: migrate reload to bean pipeline. Until then, the legacy ReloadPlugin path constructs
		// GangItemSourceProvider here directly using whatever managers the hydrated initializer holds.
		if (userManager != null && gangManager != null) {
			InventoryAddon.setItemSourceProvider(new GangItemSourceProvider(userManager, gangManager));
		}

		// Lets slot YAML reference prefixed items (weapon:awp, wearable:police_vest, ammo:9mm, unique:phone, …)
		// via the central ItemParser. Must run before any inventory file is read.
		// Deferred lookup: itemParserManager is created later in postInitialize (after addonsLoader returns),
		// so the lambda must dereference the field on each invocation, not capture it at registration time.
		SlotItemFactory.setItemResolver(slot -> itemParserManager.getParser().parse(slot));

		evaluator = new BooleanExpressionEvaluator(placeholderService);

		InventoryAddon.setConditionEvaluator(evaluator);

		inventoryLoader = new InventoryLoader(gangland, fileManager);

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

		lootChestLoader.bind(false, null, fileManager);
		fileManager.registerInitializer(lootChestLoader);
		fileManager.initializeAll();

		// share the global item parser so loot tables resolve item strings through the same converter registry
		lootChestManager.setItemParser(itemParserManager.getParser());

		lootChestManager.setMessagesProvider(new GanglandLootChestMessages());
	}

	public void weaponLoader() {
		if (ammunitionManager == null) {
			ammunitionManager = new AmmunitionManager();
		}

		if (ammunitionAddon == null) {
			ammunitionAddon = new AmmunitionAddon(fileManager, ammunitionManager);
		}

		// hand the placeholder resolver to the ammo addon BEFORE initialize() so each parsed Ammunition instance
		// has its resolver set when buildItem(...) renders display name + lore
		ammunitionAddon.setPlaceholder(placeholderService);
		fileManager.registerInitializer(ammunitionAddon);
		fileManager.initializeAll();

		if (weaponAddon == null) {
			weaponAddon = new WeaponAddon();
		}

		weaponAddon.setPlaceholder(placeholderService);

		weaponLoader = new WeaponLoader(gangland, fileManager, weaponAddon, ammunitionManager);

		weaponLoader.addExpectedFile(new FileHandler(gangland, "rifle", "weapon", ".yml"));
		weaponLoader.addExpectedFile(new FileHandler(gangland, "grenade", "weapon", ".yml"));
		weaponLoader.addExpectedFile(new FileHandler(gangland, "knife", "weapon", ".yml"));
		weaponLoader.addExpectedFile(new FileHandler(gangland, "flamethrower", "weapon", ".yml"));
		weaponLoader.addExpectedFile(new FileHandler(gangland, "syringe_gun", "weapon", ".yml"));
		weaponLoader.initialize();
	}

	/**
	 * Adds every static {@link FileHandler} the plugin owns to the {@link FileManager}. The actual yaml load happens
	 * later when each {@link me.luckyraven.persistence.FileInitializer} bean is invoked in the FILE phase. This is a
	 * direct copy of the legacy {@code files()} body, minus the {@code addonsLoader()} call (the addons are now
	 *
	 * @Bean methods in {@link me.luckyraven.config.FileConfig}).
	 */
	private void seedFileHandlers() {
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
	private void seedKernelBeans(DatabaseSettingsProvider databaseSettings) {
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
		context.register(Initializer.class, this);
	}

	/**
	 * Pulls every produced bean back out of the root container and assigns it to the matching {@code Initializer}
	 * field, so legacy code paths that still read {@code gangland.getInitializer().getX()} continue to work without
	 * changes. Called by {@link GanglandContext#bootstrap(Runnable)} between bean creation and the lifecycle pass.
	 */
	@SuppressWarnings("unchecked")
	private void hydrateFromContext() {
		ganglandDatabase = context.get(GanglandDatabase.class);

		// data layer
		userManager        = (UserManager<Player>) context.getContainer().getInstance("online", UserManager.class);
		offlineUserManager = (UserManager<OfflinePlayer>) context.getContainer()
		                                                         .getInstance("offline", UserManager.class);
		pluginManager      = context.get(PluginManager.class);
		rankManager        = context.get(RankManager.class);
		gangManager        = context.get(GangManager.class);
		memberManager      = context.get(MemberManager.class);
		waypointManager    = context.get(WaypointManager.class);

		// file-phase addons
		settings            = context.get(Settings.class);
		languageLoader      = context.get(LanguageLoader.class);
		scoreboardAddon     = context.get(ScoreboardAddon.class);
		ammunitionManager   = context.get(AmmunitionManager.class);
		ammunitionAddon     = context.get(AmmunitionAddon.class);
		fuelService         = context.get(FuelService.class);
		uniqueItemAddon     = context.get(UniqueItemAddon.class);
		wearableAddon       = context.get(WearableAddon.class);
		carAddon            = context.get(CarAddon.class);
		moneyAddon          = context.get(MoneyAddon.class);
		weaponAddon         = context.get(WeaponAddon.class);
		weaponLoader        = context.get(WeaponLoader.class);
		inventoryLoader     = context.get(InventoryLoader.class);
		gadgetPhysicsConfig = context.get(GadgetPhysicsConfig.class);
		evaluator           = context.get(ConditionEvaluator.class);

		// gameplay
		weaponManager     = context.get(WeaponManager.class);
		hologramService   = context.get(HologramService.class);
		lootChestManager  = context.get(LootChestManager.class);
		lootChestLoader   = context.get(LootChestLoader.class);
		itemParserManager = context.get(ItemParserManager.class);

		// cops + gadgets
		jailService        = context.get(JailService.class);
		detainmentRegistry = context.get(DetainmentRegistry.class);
		copService         = context.get(CopService.class);
		copSpawnManager    = context.get(CopSpawnManager.class);
		civilianService    = context.get(CivilianService.class);
		carService         = context.get(CarService.class);
		jetpackService     = context.get(JetpackService.class);

		// glue
		placeholder = context.get(GanglandPlaceholder.class);
	}
}
