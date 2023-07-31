package me.luckyraven;

import lombok.Getter;
import me.luckyraven.account.gang.Member;
import me.luckyraven.account.gang.MemberManager;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
public final class Gangland extends JavaPlugin {

	private Initializer initializer;

	@Override
	public void onLoad() {
		initializer = new Initializer(this);
	}

	@Override
	public void onDisable() {
		DatabaseManager databaseManager = initializer.getDatabaseManager();
		if (databaseManager != null && !databaseManager.getDatabases().isEmpty()) databaseManager.closeConnections();
	}

	@Override
	public void onEnable() {
		initializer.postInitialize();
		dependencyHandler();
		userInitialize();
	}

	private void dependencyHandler() {
		// required dependencies
		requiredDependency("NBTAPI", null);

		// soft dependencies
		softDependency("PlaceholderAPI", () -> new GanglandExpansion(this).register());
		softDependency("Vault", null);
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

		UserManager<Player> userManager   = initializer.getUserManager();
		MemberManager       memberManager = initializer.getMemberManager();
		DatabaseHelper      helper        = new DatabaseHelper(this, databaseHandler);

		for (Player player : Bukkit.getOnlinePlayers())
			if (!userManager.contains(userManager.getUser(player))) {
				User<Player> newUser = new User<>(player);
				createAccount.initializeUserData(newUser, helper);
				userManager.add(newUser);

				Member member = memberManager.getMember(player.getUniqueId());
				if (member == null) {
					Member newMember = new Member(player.getUniqueId());
					createAccount.initializeMemberData(newMember, helper);
					memberManager.add(newMember);
				}
			}
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
