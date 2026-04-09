package me.luckyraven.bootstrap;

import lombok.CustomLog;
import me.luckyraven.file.LanguageLoader;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.MoneyAddonInitializer;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.file.configuration.inventory.InventoryLoader;
import me.luckyraven.file.configuration.weapon.WeaponLoader;
import me.luckyraven.gadget.car.config.CarAddon;
import me.luckyraven.gadget.wearable.WearableAddon;
import me.luckyraven.item.configuration.UniqueItemAddon;
import me.luckyraven.item.money.MoneyAddon;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.scoreboard.configuration.ScoreboardAddon;
import me.luckyraven.util.TimeMessages;
import me.luckyraven.util.autowire.bean.BeanLifecycle;
import me.luckyraven.weapon.ammo.AmmunitionManager;
import me.luckyraven.weapon.configuration.AmmunitionAddon;
import me.luckyraven.weapon.configuration.WeaponAddon;

/**
 * Orchestrates the file addon clear/reload/re-initialize cycle as a single {@link BeanLifecycle} bean. On reload, this
 * service clears every addon's cached state, reloads all YAML files from disk, then re-registers and re-initializes
 * every {@link me.luckyraven.persistence.FileInitializer} in staged order — Settings first, then downstream addons,
 * with {@link FileManager#initializeAll()} between groups.
 *
 * <p>On first load ({@code onInitialize(true)}), this bean is a no-op because FILE-phase bean construction already
 * handles the initial load via the per-bean phase hook.
 */
@CustomLog
public final class FileAddonReloadService implements BeanLifecycle {

	private final FileManager           fileManager;
	private final Settings              settings;
	private final LanguageLoader        languageLoader;
	private final ScoreboardAddon       scoreboardAddon;
	private final AmmunitionManager     ammunitionManager;
	private final AmmunitionAddon       ammunitionAddon;
	private final WeaponAddon           weaponAddon;
	private final WeaponLoader          weaponLoader;
	private final UniqueItemAddon       uniqueItemAddon;
	private final WearableAddon         wearableAddon;
	private final CarAddon              carAddon;
	private final MoneyAddon            moneyAddon;
	private final MoneyAddonInitializer moneyAddonInitializer;
	private final InventoryLoader       inventoryLoader;

	public FileAddonReloadService(FileManager fileManager,
	                              Settings settings,
	                              LanguageLoader languageLoader,
	                              ScoreboardAddon scoreboardAddon,
	                              AmmunitionManager ammunitionManager,
	                              AmmunitionAddon ammunitionAddon,
	                              WeaponAddon weaponAddon,
	                              WeaponLoader weaponLoader,
	                              UniqueItemAddon uniqueItemAddon,
	                              WearableAddon wearableAddon,
	                              CarAddon carAddon,
	                              MoneyAddon moneyAddon,
	                              MoneyAddonInitializer moneyAddonInitializer,
	                              InventoryLoader inventoryLoader) {
		this.fileManager           = fileManager;
		this.settings              = settings;
		this.languageLoader        = languageLoader;
		this.scoreboardAddon       = scoreboardAddon;
		this.ammunitionManager     = ammunitionManager;
		this.ammunitionAddon       = ammunitionAddon;
		this.weaponAddon           = weaponAddon;
		this.weaponLoader          = weaponLoader;
		this.uniqueItemAddon       = uniqueItemAddon;
		this.wearableAddon         = wearableAddon;
		this.carAddon              = carAddon;
		this.moneyAddon            = moneyAddon;
		this.moneyAddonInitializer = moneyAddonInitializer;
		this.inventoryLoader       = inventoryLoader;
	}

	/**
	 * Clears all addon caches and drops stale {@link me.luckyraven.persistence.FileInitializer} references so the next
	 * {@link #onInitialize(boolean)} starts with a clean orchestrator state.
	 */
	@Override
	public void onClear() {
		inventoryLoader.clear();
		ammunitionManager.clear();
		weaponAddon.clear();
		weaponLoader.clear();
		uniqueItemAddon.clear();
		wearableAddon.clear();
		carAddon.clear();
		moneyAddon.clear();
		fileManager.clearInitializers();
	}

	/**
	 * Reloads all YAML files from disk and re-registers every {@link me.luckyraven.persistence.FileInitializer} in
	 * staged order. On first load this is a no-op because the FILE-phase bean construction already handles the
	 * initial load.
	 */
	@Override
	public void onInitialize(boolean firstLoad) {
		if (firstLoad) {
			return;
		}

		// reload all YAML from disk
		fileManager.reloadFiles();

		// --- Staged re-registration (mirrors legacy addonsLoader order) ---

		// 1. Settings must load first — downstream addons read Settings static fields at initialization time
		fileManager.registerInitializer(settings);
		fileManager.initializeAll();

		// 2. Language
		languageLoader.initialize();
		Messages.setMessageConfiguration(languageLoader.getMessage());
		TimeMessages.initialize();

		// 3. Scoreboard
		fileManager.registerInitializer(scoreboardAddon);
		fileManager.initializeAll();

		// 4. Ammunition
		fileManager.registerInitializer(ammunitionAddon);
		fileManager.initializeAll();

		// 5. Weapons (folder loader — reads rifle.yml, grenade.yml, etc.)
		weaponLoader.initialize();

		// 6. Unique items, wearables, cars — batch registered then initialized together
		fileManager.registerInitializer(uniqueItemAddon);
		fileManager.registerInitializer(wearableAddon);
		fileManager.registerInitializer(carAddon);
		fileManager.initializeAll();

		// 7. Money
		fileManager.registerInitializer(moneyAddonInitializer);
		fileManager.initializeAll();
		moneyAddon.setEnabled(Settings.isMoneyDropEnabled());

		// 8. Inventory (must be last — slot YAML references parsed items from weapon/wearable/unique addons)
		inventoryLoader.initialize();

		log.info("File addon reload complete");
	}
}
