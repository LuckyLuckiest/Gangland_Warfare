package org.luckyraven.gangland.config;

import lombok.CustomLog;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Phase;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.gangland.data.placeholder.PlaceholderService;
import org.luckyraven.keystone.persistence.message.LanguageLoader;
import org.luckyraven.gangland.file.configuration.*;
import org.luckyraven.gangland.file.configuration.wanted.GanglandBountySettings;
import org.luckyraven.gangland.file.configuration.wanted.GanglandWantedSettings;
import org.luckyraven.gangland.file.configuration.inventory.InventoryDefinitionStore;
import org.luckyraven.gangland.item.fuel.FuelService;
import org.luckyraven.gangland.gang.bounty.BountySettings;
import org.luckyraven.gangland.gang.wanted.WantedSettings;
import org.luckyraven.gangland.menu.condition.BooleanExpressionEvaluator;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.item.money.MoneyAddon;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.gangland.sign.GanglandSignInformation;
import org.luckyraven.gangland.sign.service.SignInformation;
import org.luckyraven.gangland.util.TimeMessages;

/**
 * FILE-phase wiring. Every {@code @Bean} here is invoked before the DATABASE phase begins, and {@link FileManager}'s
 * recovery loop is fired between each bean by {@code GanglandContext}'s FILE-phase hook so each file is fully loaded by
 * the time the next bean's method body runs (downstream addons typically read static fields off {@link Settings} at
 * construction time).
 *
 * <p>Intra-phase ordering is enforced by declaring upstream beans as parameters even when the produced bean's
 * constructor doesn't need them — listing {@link Settings} as a parameter on a downstream bean forces the
 * topological sort to put it first, which is the load order the legacy {@code addonsLoader()} relied on.
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
		// Keystone's LanguageLoader (1.7.x migration): the language comes from the settings supplier, missing keys
		// are reported through Messages.findMissingPaths, and the onLoaded callback re-publishes the provider into
		// the Messages/TimeMessages static seams on BOTH first load and every /glw reload lifecycle pass.
		LanguageLoader loader = new LanguageLoader(gangland, fileManager,
		                                           Settings::getLanguagePicked,
		                                           "message", "message",
		                                           Messages::findMissingPaths,
		                                           provider -> {
			                                           Messages.init(provider);
			                                           TimeMessages.initialize();
		                                           });
		loader.initialize();
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

	// ---------------------------------------------------------------------------------------------------------------
	// FileInitializer beans
	// ---------------------------------------------------------------------------------------------------------------

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
