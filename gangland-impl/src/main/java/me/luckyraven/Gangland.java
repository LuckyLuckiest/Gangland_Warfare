package me.luckyraven;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.ViaAPI;
import com.zaxxer.hikari.HikariConfig;
import lombok.CustomLog;
import lombok.Getter;
import me.luckyraven.copsncrooks.npc.civilian.CivilianService;
import me.luckyraven.copsncrooks.npc.police.CopService;
import me.luckyraven.data.economy.EconomyHandler;
import me.luckyraven.data.placeholder.worker.PlaceholderAPIExpansion;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.file.configuration.inventory.InventoryAddon;
import me.luckyraven.gadget.car.CarService;
import me.luckyraven.gadget.jetpack.JetpackService;
import me.luckyraven.hologram.HologramService;
import me.luckyraven.persistence.database.DatabaseManager;
import me.luckyraven.scoreboard.ScoreboardManager;
import me.luckyraven.updater.UpdateChecker;
import net.milkbowl.vault.economy.Economy;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

@Getter
@CustomLog
public final class Gangland extends JavaPlugin {

	public static final String FULL_PREFIX  = "gangland";
	public static final String SHORT_PREFIX = "glw";

	private Initializer             initializer;
	private ReloadPlugin            reloadPlugin;
	private PeriodicalUpdates       periodicalUpdates;
	private UpdateChecker           updateChecker;
	private PlaceholderAPIExpansion placeholderAPIExpansion;
	private ViaAPI<?>               viaAPI;

	@Override
	public void onLoad() {
		// disable HikariCP logs
		disableAllLogs(HikariConfig.class);

		initializer = new Initializer(this);
	}

	@Override
	public void onDisable() {
		// vault soft dependency economy check
		if (EconomyHandler.getVaultEconomy() != null) EconomyHandler.setVaultEconomy(null);

		// deactivate all jetpack sessions before shutdown
		JetpackService jetpackService = initializer.getJetpackService();
		if (jetpackService != null) jetpackService.deactivateAll();

		// convert active car sessions to parked records BEFORE force-save so the data supplier
		// includes the converted sessions when PeriodicalUpdates flushes all repositories
		CarService carService = initializer.getCarService();
		if (carService != null) carService.destroyAll();

		// force save
		if (this.periodicalUpdates != null) {
			this.periodicalUpdates.forceUpdate();
			this.periodicalUpdates.stop();
		}

		// closing all connections
		DatabaseManager databaseManager = initializer.getDatabaseManager();
		if (databaseManager != null && !databaseManager.getDatabases().isEmpty()) databaseManager.closeConnections();

		// shutdown hologram service
		HologramService hologramService = initializer.getHologramService();
		if (hologramService != null) hologramService.clear();

		// shutdown cop service
		CopService copService = initializer.getCopService();
		if (copService != null) copService.shutdown();

		// shutdown civilian service
		CivilianService civilianService = initializer.getCivilianService();
		if (civilianService != null) civilianService.shutdown();
	}

	@Override
	public void onEnable() {
		// must initialize so the plugin works as normal
		initializer.postInitialize();

		reloadPlugin = new ReloadPlugin(this);

		// checks for dependencies
		dependencyHandler();

		// initializes users and members who joined and not registered in postInitialize
		reloadPlugin.userInitialize(false);

		// initializes the periodical updates
		periodicalUpdatesInitializer();

		// initialize bstats
		bStats();

		// check for new updates
		updateCheckerInitializer();
	}

	/**
	 * Initializes the plugin periodical update cycle.
	 */
	void periodicalUpdatesInitializer() {
		// periodical updates
		int minutes = Settings.getAutoSaveTime();

		var database           = initializer.getGanglandDatabase();
		var pluginManager      = initializer.getPluginManager();
		var userManager        = initializer.getUserManager();
		var offlineUserManager = initializer.getOfflineUserManager();
		var weaponManager      = initializer.getWeaponManager();

		if (Settings.isAutoSave()) {
			this.periodicalUpdates = new PeriodicalUpdates(this, database, pluginManager, userManager,
			                                               offlineUserManager, weaponManager, minutes * 60L);
		} else {
			this.periodicalUpdates = new PeriodicalUpdates(this, database, pluginManager, userManager,
			                                               offlineUserManager, weaponManager);
		}

		periodicalUpdates.start();
	}

	/**
	 * Uses bStats to create statistical metrics for development purposes.
	 */
	private void bStats() {
		int     pluginId = 21012;
		Metrics metrics  = new Metrics(this, pluginId);

		// number of weapons loaded
		metrics.addCustomChart(new SingleLineChart("number_of_weapons", () -> initializer.getWeaponAddon().size()));

		// number of inventories loaded
		metrics.addCustomChart(new SingleLineChart("number_of_inventories", InventoryAddon::size));

		// number of ranks
		metrics.addCustomChart(new SingleLineChart("number_of_ranks", () -> initializer.getRankManager().size()));

		// number of gangs
		metrics.addCustomChart(new SingleLineChart("number_of_gangs", () -> initializer.getGangManager().size()));

		// number of permissions
//		metrics.addCustomChart(
//				new SingleLineChart("number_of_permissions", () -> initializer.getPermissionManager().size()));

		// number of waypoints
		metrics.addCustomChart(
				new SingleLineChart("number_of_waypoints", () -> initializer.getWaypointManager().size()));

		// scoreboard driver
		metrics.addCustomChart(new AdvancedPie("scoreboard_driver", () -> {
			Map<String, Integer> values = new HashMap<>();

			for (String driver : ScoreboardManager.getDrivers()) {
				if (!driver.equalsIgnoreCase(Settings.getScoreboardDriver())) {
					values.put(driver, 0);
					continue;
				}

				values.put(driver, 100);
			}

			return values;
		}));
	}

	/**
	 * Removes all the logs of the specified class.
	 *
	 * @param clazz the class that contains the logs
	 */
	private void disableAllLogs(@NotNull Class<?> clazz) {
		String path = clazz.getPackageName();

		Configurator.setLevel(path, Level.ERROR);
	}

	/**
	 * Initializes the dependency handler by checking for each required and soft dependency of the plugin.
	 * </b>
	 * The plugin gets initialized based on the dependencies provided.
	 */
	private void dependencyHandler() {
		// required dependencies
		Dependency nbtApi = new Dependency("NBTAPI", Dependency.Type.REQUIRED);
		nbtApi.validate(null);

		Dependency citizens = new Dependency("Citizens", Dependency.Type.REQUIRED);
		citizens.validate(null);

		// soft dependencies
		Dependency placeholderApi = new Dependency("PlaceholderAPI", Dependency.Type.SOFT);
		placeholderApi.validate(() -> {
			this.placeholderAPIExpansion = new PlaceholderAPIExpansion(this, FULL_PREFIX, initializer.getPlaceholder());
			this.placeholderAPIExpansion.register();
		});

		Dependency vault = new Dependency("Vault", Dependency.Type.SOFT);
		vault.validate(() -> {
			RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);

			if (rsp == null) return;

			// set the vault economy
			EconomyHandler.setVaultEconomy(rsp.getProvider());
		});

		Dependency viaVersion = new Dependency("ViaVersion", Dependency.Type.SOFT);
		viaVersion.validate(() -> this.viaAPI = Via.getAPI());
	}

	/**
	 * Initializes the update checker timer, which checks if there was a new update for the plugin published.
	 */
	private void updateCheckerInitializer() {
		if (!Settings.isUpdaterEnabled()) return;

		// there needs to be checks every 6 hours
		// give an option if there was an update
		int hours      = 6;
		int resourceId = 131157;

		// initialize the update checker
		updateChecker = new UpdateChecker(this, FULL_PREFIX, resourceId, hours * 60 * 60L);

		// add the necessary permissions for checking for updates
		initializer.getPermissionManager().addPermission(updateChecker.getCheckPermission());

		// the tasks and timer should be async, so there is no load on the main server thread
		updateChecker.start();
	}

	/**
	 * A class helper that initializes this plugin and links it with other plugins.
	 */
	private class Dependency {

		private final Type   type;
		private final String name;

		public Dependency(String name, Type type) {
			this.name = name;
			this.type = type;
		}

		public void validate(@Nullable Runnable runnable) {
			if (Bukkit.getPluginManager().getPlugin(name) != null) {
				if (type == Type.SOFT) log.info("Found {}, linking...", name);
				if (runnable != null) runnable.run();

				log.info("Linked {}", name);
				return;
			}

			if (type != Type.REQUIRED) return;

			log.error("{} is a required dependency!", name);
			getPluginLoader().disablePlugin(Gangland.this);
		}

		enum Type {
			REQUIRED,
			SOFT
		}
	}

}
