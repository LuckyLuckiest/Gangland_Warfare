package me.luckyraven.data;

import me.luckyraven.Gangland;
import me.luckyraven.Initializer;
import me.luckyraven.account.gang.Member;
import me.luckyraven.account.gang.MemberManager;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.sub.UserDatabase;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.listener.ListenerManager;
import me.luckyraven.listener.player.CreateAccount;
import me.luckyraven.phone.Phone;
import me.luckyraven.util.UnhandledError;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

public class ReloadPlugin {

	private final Gangland    gangland;
	private final Initializer initializer;

	public ReloadPlugin(Gangland gangland) {
		this.gangland = gangland;
		this.initializer = gangland.getInitializer();
	}

	public void addonsLoader() {
		initializer.addonsLoader();
	}

	/**
	 * Very important to run this method after {@link Initializer#postInitialize()} method.
	 */
	public void userInitialize() {
		ListenerManager listenerManager = initializer.getListenerManager();
		CreateAccount   createAccount   = null;
		for (Listener listener : listenerManager.getListeners())
			if (listener instanceof CreateAccount account) {
				createAccount = account;
				break;
			}

		if (createAccount == null) {
			gangland.getLogger().warning(UnhandledError.ERROR + ": Unable to find CreateAccount class.");
			return;
		}

		DatabaseHandler databaseHandler = null;
		for (DatabaseHandler handler : initializer.getDatabaseManager().getDatabases())
			if (handler instanceof UserDatabase database) {
				databaseHandler = database;
				break;
			}

		if (databaseHandler == null) {
			gangland.getLogger().warning(UnhandledError.ERROR + ": Unable to find UserDatabase class.");
			return;
		}

		UserManager<Player> userManager   = initializer.getUserManager();
		MemberManager       memberManager = initializer.getMemberManager();
		DatabaseHelper      helper        = new DatabaseHelper(gangland, databaseHandler);

		for (Player player : Bukkit.getOnlinePlayers())
			if (!userManager.contains(userManager.getUser(player))) {
				Phone        phone   = new Phone(SettingAddon.getPhoneName());
				User<Player> newUser = new User<>(player);

				if (SettingAddon.isPhoneEnabled()) {
					newUser.setPhone(phone);
					if (!Phone.hasPhone(player)) phone.addPhoneToInventory(player);
				}

				createAccount.initializeUserData(newUser, helper);
				userManager.add(newUser);

				// this member doesn't have a gang because they are new
				Member member = memberManager.getMember(player.getUniqueId());
				if (member == null) {
					Member newMember = new Member(player.getUniqueId());
					createAccount.initializeMemberData(newMember, helper);
					memberManager.add(newMember);
				}
			}
	}

}
