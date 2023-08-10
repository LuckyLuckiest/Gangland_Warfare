package me.luckyraven;

import lombok.Getter;
import me.luckyraven.data.ReloadPlugin;
import me.luckyraven.database.DatabaseManager;
import me.luckyraven.dependency.GanglandExpansion;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
public final class Gangland extends JavaPlugin {

	private Initializer  initializer;
	private ReloadPlugin reloadPlugin;

	@Override
	public void onLoad() {
		initializer = new Initializer(this);
		reloadPlugin = new ReloadPlugin(this);
	}

	@Override
	public void onDisable() {
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

		// auto-save to database
		PeriodicalUpdates periodicalUpdates = new PeriodicalUpdates(this, 5 * 60 * 20L);

		periodicalUpdates.start();
	}

	private void dependencyHandler() {
		// required dependencies
		requiredDependency("NBTAPI", null);

		// soft dependencies
		softDependency("PlaceholderAPI", () -> new GanglandExpansion(this).register());
		softDependency("Vault", null);
	}

	private void requiredDependency(@NotNull String name, @Nullable Runnable runnable) {
		if (Bukkit.getPluginManager().getPlugin(name) != null) return;

		getLogger().warning(name + " is a required dependency!");
		if (runnable != null) runnable.run();
		getPluginLoader().disablePlugin(this);
	}

	private void softDependency(@NotNull String name, @Nullable Runnable runnable) {
		if (Bukkit.getPluginManager().getPlugin(name) != null) if (runnable != null) runnable.run();
	}

}
