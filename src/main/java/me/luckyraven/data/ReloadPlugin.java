package me.luckyraven.data;

import me.luckyraven.Gangland;
import me.luckyraven.Initializer;
import me.luckyraven.account.gang.Member;
import me.luckyraven.account.gang.MemberManager;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.DatabaseManager;
import me.luckyraven.database.sub.GangDatabase;
import me.luckyraven.database.sub.UserDatabase;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.listener.ListenerManager;
import me.luckyraven.listener.player.CreateAccount;
import me.luckyraven.phone.Phone;
import me.luckyraven.util.UnhandledError;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

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
	 * Initializes the user and members data (effective for reloads).
	 *
	 * @implNote Very important to run this method after {@link ListenerManager}, {@link DatabaseManager},
	 * {@link CreateAccount}, {@link UserDatabase}, and {@link GangDatabase} initialization.
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
			gangland.getLogger().warning(UnhandledError.ERROR + ": Unable to find CreateAccount class.");
			return;
		}

		DatabaseManager databaseManager = initializer.getDatabaseManager();

		DatabaseHandler userHandler = databaseManager.getDatabases().stream().filter(
				handler -> handler instanceof UserDatabase).map(handler -> (UserDatabase) handler).findFirst().orElse(
				null);

		if (userHandler == null) {
			gangland.getLogger().warning(UnhandledError.ERROR + ": Unable to find UserDatabase class.");
			return;
		}

		UserManager<Player> userManager = initializer.getUserManager();
		DatabaseHelper      userHelper  = new DatabaseHelper(gangland, userHandler);

		DatabaseHandler memberHandler = databaseManager.getDatabases().stream().filter(
				handler -> handler instanceof GangDatabase).map(handler -> (GangDatabase) handler).findFirst().orElse(
				null);

		if (memberHandler == null) {
			gangland.getLogger().warning(UnhandledError.ERROR + ": Unable to find GangDatabase class.");
			return;
		}

		MemberManager  memberManager = initializer.getMemberManager();
		DatabaseHelper memberHelper  = new DatabaseHelper(gangland, memberHandler);

		if (resetCache) {
			userManager.clear();
			memberManager.clear();
		}

		for (Player player : Bukkit.getOnlinePlayers())
			if (!userManager.contains(userManager.getUser(player))) {
				Phone        phone   = new Phone(SettingAddon.getPhoneName());
				User<Player> newUser = new User<>(player);

				if (SettingAddon.isPhoneEnabled()) {
					newUser.setPhone(phone);
					if (!Phone.hasPhone(player)) phone.addPhoneToInventory(player);
				}

				createAccount.initializeUserData(newUser, userHelper);
				userManager.add(newUser);

				// this member doesn't have a gang because they are new
				Member member = memberManager.getMember(player.getUniqueId());
				if (member == null) {
					Member newMember = new Member(player.getUniqueId());
					createAccount.initializeMemberData(newMember, memberHelper);
					memberManager.add(newMember);
				}
			}

		if (!resetCache) return;

		memberHelper.runQueries(database -> {
			// need to get the whole members first
			List<Object[]> data = database.table("members").selectAll();

			// iterate over them all
			for (Object[] info : data) {
				UUID   uuid   = UUID.fromString(String.valueOf(info[0]));
				Member member = memberManager.getMember(uuid);

				// check if they are already in the list
				if (member != null) continue;

				// if not, add them
				Member newMember = new Member(uuid);
				createAccount.initializeMemberData(newMember, memberHelper);
				memberManager.add(newMember);
			}
		});
	}

	public void gangInitialize(boolean resetCache) {
		// TODO
	}

	public void rankInitialize(boolean resetCache) {
		// TODO
	}

}
