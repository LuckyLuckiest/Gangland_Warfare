package me.luckyraven;

import com.zaxxer.hikari.HikariConfig;
import lombok.Getter;
import me.luckyraven.data.ReloadPlugin;
import me.luckyraven.database.DatabaseManager;
import me.luckyraven.dependency.PlaceholderAPIExpansion;
import me.luckyraven.economy.EconomyHandler;
import me.luckyraven.file.configuration.SettingAddon;
import net.milkbowl.vault.economy.Economy;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
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
		requiredDependency("NBTAPI", null);

		// soft dependencies
		softDependency("PlaceholderAPI", () -> new PlaceholderAPIExpansion(this).register());
		softDependency("Vault", () -> {
			RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);

			if (rsp == null) return;

			// set the vault economy
			EconomyHandler.setVaultEconomy(rsp.getProvider());
		});
	}

	private void requiredDependency(@NotNull String name, @Nullable Runnable runnable) {
		if (Bukkit.getPluginManager().getPlugin(name) != null) {
			if (runnable != null) runnable.run();
			return;
		}

		log4jLogger.error(name + " is a required dependency!");
		getPluginLoader().disablePlugin(this);
	}

	private void softDependency(@NotNull String name, @Nullable Runnable runnable) {
		if (Bukkit.getPluginManager().getPlugin(name) != null) if (runnable != null) runnable.run();
	}

}
