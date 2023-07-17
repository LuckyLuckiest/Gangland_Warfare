package me.luckyraven;

import lombok.Getter;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.DatabaseManager;
import me.luckyraven.database.sub.UserDatabase;
import me.luckyraven.dependency.GanglandExpansion;
import me.luckyraven.listener.ListenerManager;
import me.luckyraven.listener.player.CreateAccount;
import me.luckyraven.util.UnhandledError;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class Gangland extends JavaPlugin {

	@Getter
	private Initializer initializer;

	@Override
	public void onLoad() {
		initializer = new Initializer(this);
	}

	@Override
	public void onDisable() {
		DatabaseManager databaseManager = initializer.getDatabaseManager();
		if (!databaseManager.getDatabases().isEmpty()) databaseManager.closeConnections(initializer.getFileManager());
	}

	@Override
	public void onEnable() {
		initializer.postInitialize();
		dependencyHandler();
		userInitialize();
	}

	private void dependencyHandler() {
		if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) new GanglandExpansion(this).register();
	}

	private void userInitialize() {
		ListenerManager listenerManager = initializer.getListenerManager();
		CreateAccount   createAccount   = null;
		for (Listener listener : listenerManager.getListeners())
			if (listener instanceof CreateAccount account) {
				createAccount = account;
				break;
			}

		if (createAccount == null) {
			getLogger().warning(UnhandledError.ERROR + ": Unable to find CreateAccount class.");
			return;
		}

		DatabaseHandler databaseHandler = null;
		for (DatabaseHandler handler : initializer.getDatabaseManager().getDatabases())
			if (handler instanceof UserDatabase database) {
				databaseHandler = database;
				break;
			}

		if (databaseHandler == null) {
			getLogger().warning(UnhandledError.ERROR + ": Unable to find UserDatabase class.");
			return;
		}

		UserManager<Player> userManager = initializer.getUserManager();
		DatabaseHelper      helper      = new DatabaseHelper(this, databaseHandler);
		for (Player player : Bukkit.getOnlinePlayers()) {
			if (!userManager.contains(userManager.getUser(player))) {
				User<Player> newUser = new User<>(player);
				createAccount.initializeUserData(newUser, helper);
				userManager.add(newUser);
			}
		}
	}

}
