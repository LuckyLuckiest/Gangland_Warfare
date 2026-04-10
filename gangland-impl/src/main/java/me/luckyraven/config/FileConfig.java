package me.luckyraven.config;

import lombok.CustomLog;
import me.luckyraven.Gangland;
import me.luckyraven.bootstrap.GanglandContext;
import me.luckyraven.copsncrooks.bounty.BountySettings;
import me.luckyraven.copsncrooks.npc.civilian.config.CivilianSettings;
import me.luckyraven.copsncrooks.npc.police.config.CopSettings;
import me.luckyraven.copsncrooks.wanted.WantedSettings;
import me.luckyraven.data.permission.PermissionManager;
import me.luckyraven.data.placeholder.PlaceholderService;
import me.luckyraven.file.LanguageLoader;
import me.luckyraven.file.configuration.GadgetPhysicsConfigImpl;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.MoneyAddonInitializer;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.file.configuration.copsncrooks.*;
import me.luckyraven.file.configuration.inventory.InventoryAddon;
import me.luckyraven.file.configuration.inventory.InventoryLoader;
import me.luckyraven.file.configuration.weapon.GanglandBlockRegenerationSettings;
import me.luckyraven.file.configuration.weapon.WeaponLoader;
import me.luckyraven.gadget.car.config.CarAddon;
import me.luckyraven.gadget.config.GadgetPhysicsConfig;
import me.luckyraven.gadget.fuel.FuelService;
import me.luckyraven.gadget.wearable.WearableAddon;
import me.luckyraven.inventory.condition.BooleanExpressionEvaluator;
import me.luckyraven.inventory.handler.SlotItemFactory;
import me.luckyraven.item.ItemParser;
import me.luckyraven.item.ItemParserManager;
import me.luckyraven.item.configuration.UniqueItemAddon;
import me.luckyraven.item.money.MoneyAddon;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.scoreboard.ScoreboardManager;
import me.luckyraven.scoreboard.configuration.ScoreboardAddon;
import me.luckyraven.sign.GanglandSignInformation;
import me.luckyraven.sign.service.SignInformation;
import me.luckyraven.util.TimeMessages;
import me.luckyraven.util.autowire.bean.Bean;
import me.luckyraven.util.autowire.bean.Configuration;
import me.luckyraven.util.autowire.bean.Phase;
import me.luckyraven.util.autowire.bean.PostConstruct;
import me.luckyraven.weapon.ammo.AmmunitionManager;
import me.luckyraven.weapon.configuration.AmmunitionAddon;
import me.luckyraven.weapon.configuration.WeaponAddon;

/**
 * FILE-phase wiring. Every {@code @Bean} here is invoked before the DATABASE phase begins, and {@link FileManager}'s
 * recovery loop is fired between each bean by {@code GanglandContext}'s FILE-phase hook so each file is fully loaded by
 * the time the next bean's method body runs (downstream addons typically read static fields off {@link Settings} at
 * construction time).
 *
 * <p>Intra-phase ordering is enforced by declaring upstream beans as parameters even when the produced bean's
 * constructor doesn't need them. For example, {@link #scoreboardAddon(FileManager, Settings)} doesn't actually use
 * {@code Settings} in the call to {@code new ScoreboardAddon(fileManager)} — but listing it as a parameter forces the
 * topological sort to put {@code Settings} first, which is the load order the legacy {@code addonsLoader()} relied on.
 *
 * <p>Pure data beans (settings extension classes such as {@link GanglandCopSettings}) live here too because they
 * have no dependencies on the database / managers and naturally fit the FILE phase.
 */
@CustomLog
@Configuration(phase = Phase.FILE)
public class FileConfig {

	private final Gangland gangland;

	public FileConfig(Gangland gangland) {
		this.gangland = gangland;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Settings + messages
	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * Loads {@code settings.yml}. The bean type is {@link Settings}; many downstream beans depend on it via static
	 * field reads. Every other FILE bean lists this as a parameter to force ordering. Registers itself with the
	 * {@link FileManager} so the FILE phase hook (which calls {@code fileManager.initializeAll()} after every bean)
	 * actually populates the static fields — without this, {@link Settings#getLanguagePicked()} returns {@code null}
	 * and {@link LanguageLoader#initialize()} aborts trying to read {@code message_null.yml}.
	 */
	@Bean
	public Settings settings(FileManager fileManager) {
		Settings settings = new Settings(fileManager);
		fileManager.registerInitializer(settings);
		return settings;
	}

	/**
	 * Pulls the message {@link org.bukkit.configuration.file.YamlConfiguration} out of the language loader and
	 * publishes it into {@link Messages} + {@link TimeMessages} for the rest of the plugin to consume. Returns the
	 * loader itself so consumers that need it (e.g. reload command) can constructor-inject it.
	 */
	@Bean
	public LanguageLoader languageLoader(FileManager fileManager, Settings settings) {
		LanguageLoader loader = new LanguageLoader(gangland, fileManager);
		loader.initialize();
		// STRUCTURAL NECESSITY: Messages is a static config holder, not a bean — set once during FILE phase.
		Messages.setMessageConfiguration(loader.getMessage());
		TimeMessages.initialize();
		return loader;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Settings extension classes (pure data, no file load)
	// ---------------------------------------------------------------------------------------------------------------

	@Bean
	public SignInformation signInformation() {
		return new GanglandSignInformation();
	}

	@Bean
	public BountySettings bountySettings() {
		return new GanglandBountySettings();
	}

	@Bean
	public WantedSettings wantedSettings() {
		return new GanglandWantedSettings();
	}

	@Bean
	public CopSettings copSettings() {
		return new GanglandCopSettings();
	}

	@Bean
	public CivilianSettings civilianSettings() {
		return new GanglandCivilianSettings();
	}

	@Bean
	public GanglandCivilianSpawnConfigProvider civilianSpawnConfigProvider() {
		return new GanglandCivilianSpawnConfigProvider();
	}

	@Bean
	public GanglandBlockRegenerationSettings blockRegenerationSettings() {
		return new GanglandBlockRegenerationSettings();
	}

	@Bean
	public GadgetPhysicsConfig gadgetPhysicsConfig(Settings settings) {
		return new GadgetPhysicsConfigImpl();
	}

	// ---------------------------------------------------------------------------------------------------------------
	// FileInitializer beans
	// ---------------------------------------------------------------------------------------------------------------

	@Bean
	public ScoreboardAddon scoreboardAddon(FileManager fileManager, Settings settings) {
		ScoreboardAddon addon = new ScoreboardAddon(fileManager);
		fileManager.registerInitializer(addon);
		return addon;
	}

	@Bean
	public ScoreboardManager scoreboardManager(PlaceholderService placeholderService,
	                                           ScoreboardAddon scoreboardAddon) {
		return new ScoreboardManager(gangland, placeholderService, scoreboardAddon);
	}

	/**
	 * Empty {@link AmmunitionManager} created in the FILE phase so {@link AmmunitionAddon} can populate it. Belongs to
	 * FILE rather than CONFIG because its lifecycle is bound to ammunition.yml loading.
	 */
	@Bean
	public AmmunitionManager ammunitionManager(Settings settings) {
		return new AmmunitionManager();
	}

	@Bean
	public AmmunitionAddon ammunitionAddon(FileManager fileManager,
	                                       AmmunitionManager ammunitionManager,
	                                       PlaceholderService placeholderService) {
		AmmunitionAddon addon = new AmmunitionAddon(fileManager, ammunitionManager, placeholderService);
		fileManager.registerInitializer(addon);
		return addon;
	}

	@Bean
	public FuelService fuelService(Settings settings) {
		return new FuelService();
	}

	@Bean
	public UniqueItemAddon uniqueItemAddon(PermissionManager permissionManager,
	                                       FileManager fileManager,
	                                       FuelService fuelService,
	                                       PlaceholderService placeholderService) {
		UniqueItemAddon addon = new UniqueItemAddon(permissionManager, fileManager, fuelService, placeholderService);
		fileManager.registerInitializer(addon);
		return addon;
	}

	@Bean
	public WearableAddon wearableAddon(PermissionManager permissionManager,
	                                   FileManager fileManager,
	                                   PlaceholderService placeholderService) {
		WearableAddon addon = new WearableAddon(permissionManager::addPermission, fileManager, placeholderService);
		fileManager.registerInitializer(addon);
		return addon;
	}

	@Bean
	public CarAddon carAddon(PermissionManager permissionManager,
	                         FileManager fileManager,
	                         PlaceholderService placeholderService) {
		CarAddon addon = new CarAddon(permissionManager::addPermission, fileManager, placeholderService);
		fileManager.registerInitializer(addon);
		return addon;
	}

	@Bean
	public WeaponAddon weaponAddon(AmmunitionAddon ammunitionAddon, PlaceholderService placeholderService) {
		return new WeaponAddon(placeholderService);
	}

	/**
	 * {@link WeaponLoader} reads its own folder of YAML files (rifle, grenade, knife, flamethrower, syringe_gun) and
	 * registers them via {@link WeaponAddon}. Constructor injection supplies {@link FileManager},
	 * {@link AmmunitionManager} and {@link WeaponAddon} directly.
	 */
	@Bean
	public WeaponLoader weaponLoader(FileManager fileManager,
	                                 AmmunitionManager ammunitionManager,
	                                 WeaponAddon weaponAddon) {
		WeaponLoader loader = new WeaponLoader(gangland, fileManager, weaponAddon, ammunitionManager);
		loader.addExpectedFile(new FileHandler(gangland, "rifle", "weapon", ".yml"));
		loader.addExpectedFile(new FileHandler(gangland, "grenade", "weapon", ".yml"));
		loader.addExpectedFile(new FileHandler(gangland, "knife", "weapon", ".yml"));
		loader.addExpectedFile(new FileHandler(gangland, "flamethrower", "weapon", ".yml"));
		loader.addExpectedFile(new FileHandler(gangland, "syringe_gun", "weapon", ".yml"));
		loader.initialize();
		return loader;
	}

	@Bean
	public MoneyAddon moneyAddon(Settings settings) {
		return new MoneyAddon();
	}

	@Bean
	public MoneyAddonInitializer moneyAddonInitializer(FileManager fileManager, MoneyAddon moneyAddon) {
		MoneyAddonInitializer initializer = new MoneyAddonInitializer(fileManager, moneyAddon);
		fileManager.registerInitializer(initializer);
		return initializer;
	}

	@Bean
	public BooleanExpressionEvaluator conditionEvaluator(PlaceholderService placeholderService) {
		BooleanExpressionEvaluator evaluator = new BooleanExpressionEvaluator(placeholderService);
		// STRUCTURAL NECESSITY: InventoryAddon is all-static — converting to instance-based requires refactoring
		// every caller across the codebase. These 3 static setters are called once during FILE phase.
		InventoryAddon.setConditionEvaluator(evaluator);
		return evaluator;
	}

	/**
	 * {@link InventoryLoader} is special: its {@code initialize()} requires the global {@link ItemParser} to exist
	 * because slot YAML can reference prefixed item refs (weapon:awp, wearable:police_vest, …). The CONFIG phase builds
	 * {@link ItemParserManager}, and the LIFECYCLE pass invokes {@code inventoryLoader.initialize()} after the parser
	 * is wired. Until then we just construct it and pre-register the inventory file handlers.
	 */
	@Bean
	public InventoryLoader inventoryLoader(FileManager fileManager,
	                                       BooleanExpressionEvaluator evaluator,
	                                       GanglandContext context,
	                                       PermissionManager permissionManager,
	                                       PlaceholderService placeholderService) {
		// STRUCTURAL NECESSITY: InventoryAddon is all-static — same as conditionEvaluator above.
		InventoryAddon.setPermissionManager(permissionManager);
		InventoryAddon.setPlaceholderService(placeholderService);
		// STRUCTURAL NECESSITY: SlotItemFactory is in inventory-api which cannot depend on gangland-item.
		// Deferred lambda bridges the cross-module boundary.
		SlotItemFactory.setItemResolver(slot -> {
			ItemParserManager mgr = context.get(ItemParserManager.class);
			return mgr.getParser().parse(slot);
		});
		InventoryLoader loader = new InventoryLoader(gangland, fileManager);
		loader.addExpectedFile(new FileHandler(gangland, "gang_info", "inventory", ".yml"));
		loader.addExpectedFile(new FileHandler(gangland, "phone", "inventory", ".yml"));
		loader.addExpectedFile(new FileHandler(gangland, "phone_gang", "inventory", ".yml"));
		return loader;
	}

	/**
	 * After every other FILE bean has been registered + loaded by the per-bean hook, this @PostConstruct flips the
	 * money addon's enabled flag to mirror {@link Settings#isMoneyDropEnabled()}. The check has to happen after
	 * Settings has finished loading (which the FILE phase guarantees) but before any CONFIG bean reads the flag.
	 */
	@PostConstruct
	public void finalizeFilePhase() {
		// no-op anchor for now: the moneyAddon enabled-flag setup happens in the moneyAddon @Bean's postProcess.
		log.info("FILE phase configuration complete");
	}
}
