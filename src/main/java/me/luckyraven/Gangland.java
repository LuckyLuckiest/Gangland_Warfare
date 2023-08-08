package me.luckyraven;

import lombok.Getter;
import me.luckyraven.account.gang.Member;
import me.luckyraven.account.gang.MemberManager;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.DatabaseManager;
import me.luckyraven.database.sub.GangDatabase;
import me.luckyraven.database.sub.UserDatabase;
import me.luckyraven.dependency.GanglandExpansion;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.listener.ListenerManager;
import me.luckyraven.listener.player.CreateAccount;
import me.luckyraven.phone.Phone;
import me.luckyraven.util.UnhandledError;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

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
		CreateAccount createAccount = listenerManager.getListeners()
		                                             .stream()
		                                             .filter(listener -> listener instanceof CreateAccount)
		                                             .map(listener -> (CreateAccount) listener)
		                                             .findFirst()
		                                             .orElse(null);

		if (createAccount == null) {
			getLogger().warning(UnhandledError.ERROR + ": Unable to find CreateAccount class.");
			return;
		}

		DatabaseManager databaseManager = initializer.getDatabaseManager();
		DatabaseHandler userHandler = databaseManager.getDatabases().stream().filter(
				handler -> handler instanceof UserDatabase).map(handler -> (UserDatabase) handler).findFirst().orElse(
				null);

		if (userHandler == null) {
			getLogger().warning(UnhandledError.ERROR + ": Unable to find UserDatabase class.");
			return;
		}

		UserManager<Player> userManager = initializer.getUserManager();
		DatabaseHelper      userHelper  = new DatabaseHelper(this, userHandler);

		for (Player player : Bukkit.getOnlinePlayers())
			// online users only
			if (!userManager.contains(userManager.getUser(player))) {
				Phone        phone   = new Phone(SettingAddon.getPhoneName());
				User<Player> newUser = new User<>(player);

				// add a phone item
				if (SettingAddon.isPhoneEnabled()) {
					newUser.setPhone(phone);
					if (!Phone.hasPhone(player)) phone.addPhoneToInventory(player);
				}

				createAccount.initializeUserData(newUser, userHelper);
				userManager.add(newUser);

			}

		DatabaseHandler memberHandler = databaseManager.getDatabases().stream().filter(
				handler -> handler instanceof GangDatabase).map(handler -> (GangDatabase) handler).findFirst().orElse(
				null);

		if (memberHandler == null) {
			getLogger().warning(UnhandledError.ERROR + ": Unable to find GangDatabase class.");
			return;
		}

		MemberManager  memberManager = initializer.getMemberManager();
		DatabaseHelper memberHelper  = new DatabaseHelper(this, memberHandler);

		memberHelper.runQueries(database -> {
			List<Object[]> membersData = database.table("members").selectAll();

			for (Object[] data : membersData) {
				// offline limited members
				UUID   offlinePlayer = UUID.fromString(String.valueOf(data[0]));
				Member member        = memberManager.getMember(offlinePlayer);

				if (member != null) continue;

				Member newMember = new Member(offlinePlayer);
				createAccount.initializeMemberData(newMember, memberHelper);
				memberManager.add(newMember);
			}
		});
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
