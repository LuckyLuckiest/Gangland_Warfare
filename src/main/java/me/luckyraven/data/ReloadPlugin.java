package me.luckyraven.data;

import me.luckyraven.Gangland;
import me.luckyraven.Initializer;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.Member;
import me.luckyraven.data.account.gang.MemberManager;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.DatabaseManager;
import me.luckyraven.database.sub.GangDatabase;
import me.luckyraven.database.sub.RankDatabase;
import me.luckyraven.database.sub.UserDatabase;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.listener.ListenerManager;
import me.luckyraven.listener.player.CreateAccount;
import me.luckyraven.phone.Phone;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.util.UnhandledError;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * The type Reload plugin.
 */
public class ReloadPlugin {

	private final Gangland    gangland;
	private final Initializer initializer;

	public ReloadPlugin(Gangland gangland) {
		this.gangland = gangland;
		this.initializer = gangland.getInitializer();
	}

	/**
	 * Reloads the files and they're linked addons.
	 */
	public void filesReload() {
		gangland.getInitializer().getFileManager().reloadFiles();
		initializer.addonsLoader();
	}

	/**
	 * Properly and inorder initialize the database data.
	 *
	 * @param resetCache if old data needs to be cleared
	 */
	public void databaseInitialize(boolean resetCache) {
		rankInitialize(resetCache);
		gangInitialize(resetCache);
		memberInitialize(resetCache);
		userInitialize(resetCache);
	}

	/**
	 * Initializes the user and new members data (effective for reloads).
	 *
	 * @param resetCache if old data needs to be cleared
	 * @implNote Very important to run this method after {@link ListenerManager}, {@link DatabaseManager},
	 * {@link CreateAccount}, {@link UserDatabase}, {@link RankDatabase}, and {@link GangDatabase} initialization.
	 */
	public void userInitialize(boolean resetCache) {
		ListenerManager listenerManager = initializer.getListenerManager();
		CreateAccount createAccount = listenerManager.getListeners()
		                                             .stream()
		                                             .filter(listener -> listener instanceof CreateAccount)
		                                             .map(listener -> (CreateAccount) listener)
		                                             .findFirst()
		                                             .orElse(null);

		if (createAccount == null) {
			Gangland.getLog4jLogger().error(UnhandledError.ERROR + ": Unable to find CreateAccount class.");
			return;
		}

		DatabaseManager databaseManager = initializer.getDatabaseManager();

		UserDatabase userHandler = databaseManager.getDatabases().stream().filter(
				handler -> handler instanceof UserDatabase).map(handler -> (UserDatabase) handler).findFirst().orElse(
				null);

		if (userHandler == null) {
			Gangland.getLog4jLogger().error(UnhandledError.ERROR + ": Unable to find UserDatabase class.");
			return;
		}

		UserManager<Player> userManager = initializer.getUserManager();

		GangDatabase memberHandler = databaseManager.getDatabases().stream().filter(
				handler -> handler instanceof GangDatabase).map(handler -> (GangDatabase) handler).findFirst().orElse(
				null);

		if (memberHandler == null) {
			Gangland.getLog4jLogger().error(UnhandledError.ERROR + ": Unable to find GangDatabase class.");
			return;
		}

		MemberManager memberManager = initializer.getMemberManager();

		if (resetCache) userManager.clear();

		for (Player player : Bukkit.getOnlinePlayers())
			if (!userManager.contains(userManager.getUser(player))) {
				Phone        phone   = new Phone(player, SettingAddon.getPhoneName());
				User<Player> newUser = new User<>(player);

				if (SettingAddon.isPhoneEnabled()) {
					newUser.setPhone(phone);
					if (!Phone.hasPhone(player)) phone.addPhoneToInventory(player);
				}

				createAccount.initializeUserData(newUser, userHandler);
				userManager.add(newUser);

				// this member doesn't have a gang because they are new
				Member member = memberManager.getMember(player.getUniqueId());
				if (member == null) {
					Member newMember = new Member(player.getUniqueId());
					createAccount.initializeMemberData(newMember, memberHandler);
					memberManager.add(newMember);
				}
			}
	}

	/**
	 * Initializes members' data (effective for reloads).
	 *
	 * @param resetCache if old data needs to be cleared
	 * @implNote Very important to run this method after {@link DatabaseManager}, {@link RankManager},
	 * {@link GangManager}, {@link UserDatabase}, {@link RankDatabase}, and {@link GangDatabase} initialization.
	 */
	public void memberInitialize(boolean resetCache) {
		RankManager   rankManager   = gangland.getInitializer().getRankManager();
		GangManager   gangManager   = gangland.getInitializer().getGangManager();
		MemberManager memberManager = gangland.getInitializer().getMemberManager();

		if (resetCache) memberManager.clear();

		for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases())
			if (handler instanceof GangDatabase gangDatabase) {
				memberManager.initialize(gangDatabase, gangManager, rankManager);
				break;
			}
	}

	/**
	 * Initializes the gang data (effective for reloads).
	 *
	 * @param resetCache if old data needs to be cleared
	 * @implNote Very important to run this method after {@link DatabaseManager}, and {@link GangDatabase}
	 * initialization.
	 */
	public void gangInitialize(boolean resetCache) {
		GangManager gangManager = gangland.getInitializer().getGangManager();

		if (resetCache) gangManager.clear();

		for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases())
			if (handler instanceof GangDatabase gangDatabase) {
				gangManager.initialize(gangDatabase);
				break;
			}
	}

	/**
	 * Initializes the rank data (effective for reloads).
	 *
	 * @param resetCache if old data needs to be cleared
	 * @implNote Very important to run this method after {@link DatabaseManager}, and {@link RankDatabase}
	 * initialization.
	 */
	public void rankInitialize(boolean resetCache) {
		RankManager rankManager = gangland.getInitializer().getRankManager();

		if (resetCache) rankManager.clear();

		for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases())
			if (handler instanceof RankDatabase rankDatabase) {
				rankManager.initialize(rankDatabase);
				break;
			}
	}

}
