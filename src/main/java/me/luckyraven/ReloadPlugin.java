package me.luckyraven;

import me.luckyraven.bukkit.inventory.InventoryHandler;
import me.luckyraven.bukkit.scoreboard.Scoreboard;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.Member;
import me.luckyraven.data.account.gang.MemberManager;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.data.teleportation.WaypointManager;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserDataInitEvent;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.DatabaseManager;
import me.luckyraven.database.sub.GangDatabase;
import me.luckyraven.database.sub.RankDatabase;
import me.luckyraven.database.sub.UserDatabase;
import me.luckyraven.database.sub.WaypointDatabase;
import me.luckyraven.feature.phone.Phone;
import me.luckyraven.file.FileHandler;
import me.luckyraven.file.FileManager;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.listener.ListenerManager;
import me.luckyraven.listener.player.CreateAccount;
import me.luckyraven.util.UnhandledError;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;

public class ReloadPlugin {

	private final Gangland    gangland;
	private final Initializer initializer;

	public ReloadPlugin(Gangland gangland) {
		this.gangland = gangland;
		this.initializer = gangland.getInitializer();
	}

	/**
	 * Reloads the periodical updates.
	 */
	public void periodicalUpdatesReload() {
		gangland.getPeriodicalUpdates().stop();
		gangland.periodicalUpdatesInitializer();
	}

	/**
	 * Reloads the files and their linked addons.
	 */
	public void filesReload() {
		initializer.getFileManager().reloadFiles();
		initializer.addonsLoader();
	}

	/**
	 * Properly and inorder initialize the database data.
	 *
	 * @param resetCache if old data needs to be cleared
	 */
	public void databaseInitialize(boolean resetCache) {
		// order matters
		rankInitialize(resetCache);
		gangInitialize(resetCache);
		memberInitialize(resetCache);
		// order doesn't matter
		userInitialize(resetCache);
		waypointInitialize(resetCache);
	}

	/**
	 * Reloads the scoreboard content from the file.
	 */
	public void scoreboardReload() {
		// reload scoreboard file
		FileManager fileManager = initializer.getFileManager();
		try {
			fileManager.checkFileLoaded("scoreboard");

			FileHandler scoreboard = fileManager.getFile("scoreboard");
			if (scoreboard == null) throw new IOException("scoreboard file is not loaded!");
			scoreboard.reloadData();
		} catch (IOException exception) {
			Gangland.getLog4jLogger().error("scoreboard file is not loaded!", exception);
			return;
		}

		initializer.scoreboardLoader();

		for (User<Player> user : initializer.getUserManager().getUsers().values()) {
			if (user.getScoreboard() != null) {
				user.getScoreboard().end();
				user.setScoreboard(null);
			}

			user.setScoreboard(new Scoreboard(initializer.getScoreboardManager().getDriverHandler(user.getUser())));
			user.getScoreboard().start();
		}
	}

	public void inventoryReload() {
		// Simple inventory reload for special inventories ONLY
		InventoryHandler.removeAllSpecialInventories();
		initializer.inventoryLoader();
	}

	/**
	 * Initializes the user and new members data (effective for reloads).
	 *
	 * @param resetCache if old data needs to be cleared
	 * @implNote Very important to run this method after {@link ListenerManager}, {@link DatabaseManager},
	 * {@link CreateAccount}, {@link UserDatabase}, and {@link GangDatabase} initialization.
	 */
	public void userInitialize(boolean resetCache) {
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

		if (resetCache) {
			for (User<Player> user : userManager.getUsers().values()) {
				if (user.getScoreboard() == null) continue;

				user.getScoreboard().end();
				user.setScoreboard(null);
			}

			userManager.clear();
		}

		for (Player player : Bukkit.getOnlinePlayers())
			if (!userManager.contains(userManager.getUser(player))) {
				Phone        phone   = new Phone(gangland, player, SettingAddon.getPhoneName());
				User<Player> newUser = new User<>(player);

				if (SettingAddon.isPhoneEnabled()) {
					newUser.setPhone(phone);
					if (!Phone.hasPhone(player)) phone.addPhoneToInventory(player);
				}

				initializer.getUserManager().initializeUserData(newUser, userHandler);

				UserDataInitEvent userDataInitEvent = new UserDataInitEvent(newUser);
				Bukkit.getPluginManager().callEvent(userDataInitEvent);

				userManager.add(newUser);

				// this member doesn't have a gang because they are new
				Member member = memberManager.getMember(player.getUniqueId());

				// initialize the rank permissions
				if (member != null) {
					initializer.getUserManager().initializeUserPermission(newUser, member);
					continue;
				}

				// for a new member
				Member newMember = new Member(player.getUniqueId());
				initializer.getMemberManager().initializeMemberData(newMember, memberHandler);
				memberManager.add(newMember);
			}
	}

	/**
	 * Initializes members' data (effective for reloads).
	 *
	 * @param resetCache if old data needs to be cleared
	 * @implNote Very important to run this method after {@link DatabaseManager}, {@link RankManager},
	 * {@link GangManager}, {@link RankDatabase}, and {@link GangDatabase} initialization.
	 */
	public void memberInitialize(boolean resetCache) {
		RankManager   rankManager   = initializer.getRankManager();
		GangManager   gangManager   = initializer.getGangManager();
		MemberManager memberManager = initializer.getMemberManager();

		if (resetCache) memberManager.clear();

		for (DatabaseHandler handler : initializer.getDatabaseManager().getDatabases())
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
		GangManager gangManager = initializer.getGangManager();

		if (resetCache) gangManager.clear();

		for (DatabaseHandler handler : initializer.getDatabaseManager().getDatabases())
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
		RankManager rankManager = initializer.getRankManager();

		if (resetCache) rankManager.clear();

		for (DatabaseHandler handler : initializer.getDatabaseManager().getDatabases())
			if (handler instanceof RankDatabase rankDatabase) {
				rankManager.initialize(rankDatabase);
				break;
			}
	}

	/**
	 * Initializes the waypoint data (effective for reloads).
	 *
	 * @param resetCache if old data needs to be cleared
	 * @implNote Very important to run this method after {@link DatabaseManager}, and {@link WaypointDatabase}
	 * initialization.
	 */
	public void waypointInitialize(boolean resetCache) {
		WaypointManager waypointManager = initializer.getWaypointManager();

		if (resetCache) waypointManager.clear();

		for (DatabaseHandler handler : initializer.getDatabaseManager().getDatabases())
			if (handler instanceof WaypointDatabase waypointDatabase) {
				waypointManager.initialize(waypointDatabase);
				break;
			}
	}

}
