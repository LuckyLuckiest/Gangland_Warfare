package me.luckyraven;

import com.zaxxer.hikari.HikariConfig;
import lombok.Getter;
import me.luckyraven.data.economy.EconomyHandler;
import me.luckyraven.database.DatabaseManager;
import me.luckyraven.dependency.PlaceholderAPIExpansion;
import me.luckyraven.file.configuration.SettingAddon;
import net.milkbowl.vault.economy.Economy;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

@Getter
public final class Gangland extends JavaPlugin {

	@Getter
	private static final Logger            log4jLogger = LogManager.getLogger("Gangland_Warfare");
	private              Initializer       initializer;
	private              ReloadPlugin      reloadPlugin;
	private              PeriodicalUpdates periodicalUpdates;

	@Override
	public void onLoad() {
		// disable hikari cp logs
		disableAllLogs(HikariConfig.class);

		initializer = new Initializer(this);
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
	}

	void periodicalUpdatesInitializer() {
		// periodical updates
		int minutes = SettingAddon.getAutoSaveTime();

		if (SettingAddon.isAutoSave()) this.periodicalUpdates = new PeriodicalUpdates(this, minutes * 60 * 20L);
		else this.periodicalUpdates = new PeriodicalUpdates(this);

		periodicalUpdates.start();
	}

	private void disableAllLogs(Class<?> clazz) {
		String path = clazz.getPackageName();

		Configurator.setLevel(path, Level.ERROR);
	}

	private void dependencyHandler() {
		// required dependencies
		Dependency nbtApi = new Dependency("NBTAPI", Dependency.Type.REQUIRED);
		nbtApi.validate(null);

		// soft dependencies
		Dependency placeholderApi = new Dependency("PlaceholderAPI", Dependency.Type.SOFT);
		placeholderApi.validate(() -> new PlaceholderAPIExpansion(this).register());

		Dependency vault = new Dependency("Vault", Dependency.Type.SOFT);
		vault.validate(() -> {
			RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);

			if (rsp == null) return;

			// set the vault economy
			EconomyHandler.setVaultEconomy(rsp.getProvider());
		});
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
				if (runnable != null) runnable.run();
				if (type == Type.SOFT) log4jLogger.info("Found " + name + ", linking...");
				return;
			}

			if (type == Type.REQUIRED) {
				log4jLogger.error(name + " is a required dependency!");
				getPluginLoader().disablePlugin(Gangland.this);
			}
		}

		enum Type {
			REQUIRED, SOFT
		}

	}

}
