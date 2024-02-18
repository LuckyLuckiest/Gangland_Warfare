package me.luckyraven;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.ViaAPI;
import com.zaxxer.hikari.HikariConfig;
import lombok.Getter;
import me.clip.placeholderapi.PlaceholderAPI;
import me.luckyraven.bukkit.scoreboard.ScoreboardManager;
import me.luckyraven.data.economy.EconomyHandler;
import me.luckyraven.data.placeholder.worker.GanglandPlaceholder;
import me.luckyraven.data.placeholder.worker.PlaceholderAPIExpansion;
import me.luckyraven.database.DatabaseManager;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.file.configuration.inventory.InventoryAddon;
import net.milkbowl.vault.economy.Economy;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

@Getter
public final class Gangland extends JavaPlugin {

	private static final @Getter Logger log4jLogger = LogManager.getLogger("Gangland_Warfare");

	private Initializer             initializer;
	private ReloadPlugin            reloadPlugin;
	private PeriodicalUpdates       periodicalUpdates;
	private PlaceholderAPIExpansion placeholderAPIExpansion;
	private ViaAPI<?>               viaAPI;

	@Override
	public void onLoad() {
		// disable hikaricp logs
		disableAllLogs(HikariConfig.class);

		initializer  = new Initializer(this);
		reloadPlugin = new ReloadPlugin(this);
	}

	@Override
	public void onDisable() {
		// vault soft dependency economy check
		if (EconomyHandler.getVaultEconomy() != null) EconomyHandler.setVaultEconomy(null);

		// force save
		this.periodicalUpdates.forceUpdate();
		this.periodicalUpdates.stop();

		// closing all connections
		DatabaseManager databaseManager = initializer.getDatabaseManager();
		if (databaseManager != null && !databaseManager.getDatabases().isEmpty()) databaseManager.closeConnections();
	}

	@Override
	public void onEnable() {
		// must initialize so the plugin works as normal
		initializer.postInitialize();

		// checks for dependencies
		dependencyHandler();

		// initializes users and members who joined and not registered in postInitialize
		reloadPlugin.userInitialize(false);

		// initializes the periodical updates
		periodicalUpdatesInitializer();

		// initialize bstats
		bStats();

		log4jLogger.info(ScoreboardManager.getDrivers());
	}

	public String usePlaceholder(Player player, String text) {
		if (placeholderAPIExpansion != null) {
			if (PlaceholderAPI.containsPlaceholders(text)) return PlaceholderAPI.setPlaceholders(player, text);
		} else {
			GanglandPlaceholder placeholder = initializer.getPlaceholder();
			if (placeholder.containsPlaceholder(text)) return placeholder.replacePlaceholder(player, text);
		}

		return text;
	}

	void periodicalUpdatesInitializer() {
		// periodical updates
		int minutes = SettingAddon.getAutoSaveTime();

		if (SettingAddon.isAutoSave()) this.periodicalUpdates = new PeriodicalUpdates(this, minutes * 60 * 20L);
		else this.periodicalUpdates = new PeriodicalUpdates(this);

		periodicalUpdates.start();
	}

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
				if (!driver.equalsIgnoreCase(SettingAddon.getScoreboardDriver())) {
					values.put(driver, 0);
					continue;
				}

				values.put(driver, 100);
			}

			return values;
		}));
	}

	private void disableAllLogs(Class<?> clazz) {
		String path = clazz.getPackageName();

		Configurator.setLevel(path, Level.ERROR);
	}

	private void dependencyHandler() {
		// required dependencies
		Dependency nbtApi = new Dependency("NBTAPI", Dependency.Type.REQUIRED);
		nbtApi.validate(null);

		Dependency protocolLib = new Dependency("ProtocolLib", Dependency.Type.REQUIRED);
		protocolLib.validate(null);

		// soft dependencies
		Dependency placeholderApi = new Dependency("PlaceholderAPI", Dependency.Type.SOFT);
		placeholderApi.validate(() -> {
			this.placeholderAPIExpansion = new PlaceholderAPIExpansion(this);
			this.placeholderAPIExpansion.register();
		});

		Dependency vault = new Dependency("Vault", Dependency.Type.SOFT);
		vault.validate(() -> {
			RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);

			if (rsp == null) return;

			// set the vault economy
			EconomyHandler.setVaultEconomy(rsp.getProvider());
		});

		Dependency citizens = new Dependency("Citizens", Dependency.Type.SOFT);
		citizens.validate(null);

		Dependency viaVersion = new Dependency("ViaVersion", Dependency.Type.SOFT);
		viaVersion.validate(() -> this.viaAPI = Via.getAPI());
	}

	private class Dependency {

		private final Type   type;
		private final String name;

		public Dependency(String name, Type type) {
			this.name = name;
			this.type = type;
		}

		public void validate(@Nullable Runnable runnable) {
			if (Bukkit.getPluginManager().getPlugin(name) != null) {
				if (type == Type.SOFT) log4jLogger.info("Found " + name + ", linking...");
				if (runnable != null) runnable.run();
				log4jLogger.info("Linked " + name);
				return;
			}

			if (type == Type.REQUIRED) {
				log4jLogger.error(name + " is a required dependency!");
				getPluginLoader().disablePlugin(Gangland.this);
			}
		}

		enum Type {
			REQUIRED,
			SOFT
		}

	}

}
