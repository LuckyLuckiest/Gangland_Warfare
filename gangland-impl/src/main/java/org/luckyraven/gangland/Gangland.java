package org.luckyraven.gangland;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.ViaAPI;
import com.zaxxer.hikari.HikariConfig;
import lombok.CustomLog;
import lombok.Getter;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
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
import org.luckyraven.gangland.bootstrap.GanglandContext;
import org.luckyraven.gangland.bootstrap.PeriodicalUpdates;
import org.luckyraven.gangland.bootstrap.ReloadPlugin;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.gangland.data.placeholder.worker.GanglandPlaceholder;
import org.luckyraven.gangland.data.placeholder.worker.PlaceholderAPIExpansion;
import org.luckyraven.gangland.data.teleportation.WaypointManager;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.file.configuration.inventory.InventoryDefinitionStore;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.gang.vault.permission.VaultPermissionBridge;
import org.luckyraven.keystone.persistence.database.DatabaseManager;
import org.luckyraven.gangland.scoreboard.ScoreboardManager;
import org.luckyraven.gangland.util.UpdateNotifier;
import org.luckyraven.keystone.update.UpdateChecker;
import org.luckyraven.gangland.weapon.configuration.WeaponAddon;

import java.util.HashMap;
import java.util.Map;

@Getter
@CustomLog
public final class Gangland extends JavaPlugin {

	public static final String FULL_PREFIX  = "gangland";
	public static final String SHORT_PREFIX = "glw";

	private GanglandContext         context;
	private ReloadPlugin            reloadPlugin;
	private UpdateNotifier          updateChecker;
	private PlaceholderAPIExpansion placeholderAPIExpansion;
	private ViaAPI<?>               viaAPI;

	@Override
	public void onLoad() {
		// disable HikariCP logs
		disableAllLogs(HikariConfig.class);
	}

	@Override
	public void onDisable() {
		// vault soft dependency economy check
		if (EconomyHandler.getVaultEconomy() != null) {
			EconomyHandler.setVaultEconomy(null);
		}

		// vault soft dependency permission check
		if (VaultPermissionBridge.isEnabled()) {
			VaultPermissionBridge.set(null, null);
		}

		// unified bean lifecycle shutdown — deactivates sessions, converts active car data to parked records,
		// despawns NPCs and holograms, all in reverse topological order
		context.shutdownBeans();

		// force save all pending data AFTER bean shutdown so converted records (CarService etc.) are included
		PeriodicalUpdates periodicalUpdates = context.get(PeriodicalUpdates.class);
		if (periodicalUpdates != null) {
			periodicalUpdates.forceUpdate();
		}

		// closing all connections
		DatabaseManager databaseManager = context.get(DatabaseManager.class);
		if (databaseManager != null && !databaseManager.getDatabases().isEmpty()) {
			databaseManager.closeConnections();
		}
	}

	@Override
	public void onEnable() {
		// Create the root DI context and drive the full phased bean pipeline (KERNEL → FILE → DATABASE →
		// CONFIG → LIFECYCLE → LISTENER → COMMAND). KernelConfig produces every bootstrap-critical singleton;
		// all managers, services, and addons are wired by @Configuration classes under org.luckyraven.gangland.config.
		this.context = new GanglandContext(this);
		context.bootstrap();

		reloadPlugin = new ReloadPlugin(context);

		// checks for dependencies
		dependencyHandler();

		// initialize bstats
		bStats();

		// check for new updates
		updateCheckerInitializer();
	}

	/**
	 * Uses bStats to create statistical metrics for development purposes.
	 */
	private void bStats() {
		int     pluginId = 21012;
		Metrics metrics  = new Metrics(this, pluginId);

		// number of weapons loaded
		metrics.addCustomChart(new SingleLineChart("number_of_weapons", () -> context.get(WeaponAddon.class).size()));

		// number of inventories loaded
		metrics.addCustomChart(new SingleLineChart("number_of_inventories",
		                                           () -> context.get(InventoryDefinitionStore.class).size()));

		// number of ranks
		metrics.addCustomChart(new SingleLineChart("number_of_ranks", () -> context.get(RankManager.class).size()));

		// number of gangs
		metrics.addCustomChart(new SingleLineChart("number_of_gangs", () -> context.get(GangManager.class).size()));

		// number of waypoints
		metrics.addCustomChart(
				new SingleLineChart("number_of_waypoints", () -> context.get(WaypointManager.class).size()));

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
			GanglandPlaceholder placeholder = context.get(GanglandPlaceholder.class);
			this.placeholderAPIExpansion = new PlaceholderAPIExpansion(this, FULL_PREFIX, placeholder);
			this.placeholderAPIExpansion.register();
		});

		Dependency vault = new Dependency("Vault", Dependency.Type.SOFT);
		vault.validate(() -> {
			RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);

			if (rsp == null) return;

			// set the vault economy
			EconomyHandler.setVaultEconomy(rsp.getProvider());
		});

		Dependency vaultPermissions = new Dependency("Vault", Dependency.Type.SOFT);
		vaultPermissions.validate(() -> {
			RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager()
			                                                       .getRegistration(Permission.class);

			if (rsp == null) return;

			// set the vault permissions bridge
			VaultPermissionBridge.set(rsp.getProvider(), this);
		});

		Dependency viaVersion = new Dependency("ViaVersion", Dependency.Type.SOFT);
		viaVersion.validate(() -> this.viaAPI = Via.getAPI());
	}

	/**
	 * Initializes the update checker timer, which checks if there was a new update for the plugin published.
	 */
	private void updateCheckerInitializer() {
		if (!Settings.isUpdaterEnabled()) {
			return;
		}

		// there needs to be checks every 6 hours
		// give an option if there was an update
		int hours      = 6;
		int resourceId = 131157;

		// Keystone's checker fetches/compares/downloads and registers the check permission itself; the
		// UpdateNotifier wraps it with Gangland's periodic operator notification + auto-update policy.
		UpdateChecker checker = new UpdateChecker(this, context.get(PermissionManager.class), FULL_PREFIX, resourceId);
		this.updateChecker = new UpdateNotifier(this, checker, hours * 60 * 60L);

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
