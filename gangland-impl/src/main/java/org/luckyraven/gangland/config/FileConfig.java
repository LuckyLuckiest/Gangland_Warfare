package org.luckyraven.gangland.config;

import lombok.CustomLog;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.copsncrooks.npc.civilian.config.CivilianSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopSettings;
import org.luckyraven.gangland.core.bean.Bean;
import org.luckyraven.gangland.core.bean.Configuration;
import org.luckyraven.gangland.core.bean.Phase;
import org.luckyraven.gangland.data.permission.PermissionManager;
import org.luckyraven.gangland.data.placeholder.PlaceholderService;
import org.luckyraven.gangland.file.LanguageLoader;
import org.luckyraven.gangland.file.configuration.*;
import org.luckyraven.gangland.file.configuration.copsncrooks.*;
import org.luckyraven.gangland.file.configuration.inventory.InventoryDefinitionStore;
import org.luckyraven.gangland.file.configuration.weapon.GanglandBlockRegenerationSettings;
import org.luckyraven.gangland.file.configuration.weapon.WeaponLoader;
import org.luckyraven.gangland.gadget.car.config.CarAddon;
import org.luckyraven.gangland.gadget.config.GadgetPhysicsConfig;
import org.luckyraven.gangland.gadget.fuel.FuelService;
import org.luckyraven.gangland.gadget.wearable.WearableAddon;
import org.luckyraven.gangland.gang.bounty.BountySettings;
import org.luckyraven.gangland.gang.wanted.WantedSettings;
import org.luckyraven.gangland.inventory.condition.BooleanExpressionEvaluator;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.item.money.MoneyAddon;
import org.luckyraven.gangland.persistence.FileHandler;
import org.luckyraven.gangland.persistence.FileManager;
import org.luckyraven.gangland.scoreboard.ScoreboardManager;
import org.luckyraven.gangland.scoreboard.configuration.ScoreboardAddon;
import org.luckyraven.gangland.sign.GanglandSignInformation;
import org.luckyraven.gangland.sign.service.SignInformation;
import org.luckyraven.gangland.util.TimeMessages;
import org.luckyraven.gangland.weapon.ammo.AmmunitionManager;
import org.luckyraven.gangland.weapon.configuration.AmmunitionAddon;
import org.luckyraven.gangland.weapon.configuration.WeaponAddon;

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
		// Single static seam: the MessageProvider bean is published into the Messages enum once at startup.
		Messages.init(new YamlMessageProvider(loader.getMessage(), loader.getJarMessage()));
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
		return new BooleanExpressionEvaluator(placeholderService);
	}

	/**
	 * Pure-data half of the former {@code InventoryAddon}. No external dependencies — populates its five lookup maps in
	 * its own constructor. The runtime services half ({@code InventoryRuntimeContext}) lives in the CONFIG phase
	 * because it needs CONFIG-phase beans like the user manager and item source provider.
	 */
	@Bean
	public InventoryDefinitionStore inventoryDefinitionStore() {
		return new InventoryDefinitionStore(gangland);
	}
}
