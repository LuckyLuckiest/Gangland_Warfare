package me.luckyraven;

import me.luckyraven.bukkit.inventory.InventoryHandler;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.Member;
import me.luckyraven.data.account.gang.MemberManager;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.data.teleportation.WaypointManager;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserDataInitEvent;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.database.component.Table;
import me.luckyraven.database.tables.*;
import me.luckyraven.feature.phone.Phone;
import me.luckyraven.feature.scoreboard.ScoreboardManager;
import me.luckyraven.file.FileHandler;
import me.luckyraven.file.FileManager;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.listener.ListenerManager;
import me.luckyraven.listener.player.CreateAccount;
import me.luckyraven.scoreboard.Scoreboard;
import me.luckyraven.scoreboard.driver.DriverHandler;
import me.luckyraven.weapon.WeaponManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.List;

public final class ReloadPlugin {

	private static final Logger logger = LogManager.getLogger(ReloadPlugin.class.getSimpleName());

	private final Gangland         gangland;
	private final Initializer      initializer;
	private final GanglandDatabase ganglandDatabase;

	public ReloadPlugin(Gangland gangland) {
		this.gangland         = gangland;
		this.initializer      = gangland.getInitializer();
		this.ganglandDatabase = initializer.getGanglandDatabase();
	}

	/**
	 * Reloads the whole plugin files, scoreboards, and database.
	 *
	 * @param resetCache if old data needs to be cleared
	 */
	public void reload(boolean resetCache) {
		filesReload();
		// when resetting the cache, there would be a problem with the scoreboard trying to get some values
		// first killing all scoreboards, then initializing the data
		killAllScoreboards();
		databaseInitialize(resetCache);
		if (SettingAddon.isScoreboardEnabled()) scoreboardReload();
		periodicalUpdatesReload();
	}

	/**
	 * Reloads the files and their linked addons.
	 */
	public void filesReload() {
		// first, remove all the data saved from the files
		initializer.addonsClear();
		// second, reload the files
		initializer.getFileManager().reloadFiles();
		initializer.addonsLoader();
	}

	/**
	 * Properly and in-order initializes the database data.
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
		weaponInitialize(resetCache);
	}

	/**
	 * Initializes the rank data (effective for reloads).
	 *
	 * @param resetCache if old data needs to be cleared
	 *
	 * @implNote Very important to run this method after {@link RankManager}, {@link RankTable},
	 *        {@link RankParentTable}, {@link PermissionTable}, and {@link RankPermissionTable} initialization.
	 */
	public void rankInitialize(boolean resetCache) {
		RankManager rankManager = initializer.getRankManager();

		if (resetCache) rankManager.clear();

		List<Table<?>>      tables              = ganglandDatabase.getTables();
		RankTable           rankTable           = initializer.getInstanceFromTables(RankTable.class, tables);
		RankParentTable     rankParentTable     = initializer.getInstanceFromTables(RankParentTable.class, tables);
		PermissionTable     permissionTable     = initializer.getInstanceFromTables(PermissionTable.class, tables);
		RankPermissionTable rankPermissionTable = initializer.getInstanceFromTables(RankPermissionTable.class, tables);

		rankManager.initialize(rankTable, rankParentTable, permissionTable, rankPermissionTable);
	}

	/**
	 * Initializes the gang data (effective for reloads).
	 *
	 * @param resetCache if old data needs to be cleared
	 *
	 * @implNote Very important to run this method after {@link GangManager}, {@link GangTable}, and
	 *        {@link GangAlliesTable} initialization.
	 */
	public void gangInitialize(boolean resetCache) {
		GangManager gangManager = initializer.getGangManager();

		if (resetCache) gangManager.clear();

		List<Table<?>>  tables          = ganglandDatabase.getTables();
		GangTable       gangTable       = initializer.getInstanceFromTables(GangTable.class, tables);
		GangAlliesTable gangAlliesTable = initializer.getInstanceFromTables(GangAlliesTable.class, tables);

		gangManager.initialize(gangTable, gangAlliesTable);
	}

	/**
	 * Initializes members' data (effective for reloads).
	 *
	 * @param resetCache if old data needs to be cleared
	 *
	 * @implNote Very important to run this method after {@link RankManager}, {@link GangManager},
	 *        {@link MemberManager}, and {@link MemberTable} initialization.
	 */
	public void memberInitialize(boolean resetCache) {
		RankManager   rankManager   = initializer.getRankManager();
		GangManager   gangManager   = initializer.getGangManager();
		MemberManager memberManager = initializer.getMemberManager();

		if (resetCache) memberManager.clear();

		List<Table<?>> tables      = ganglandDatabase.getTables();
		MemberTable    memberTable = initializer.getInstanceFromTables(MemberTable.class, tables);

		memberManager.initialize(memberTable, gangManager, rankManager);
	}

	/**
	 * Initializes the user and new members data (effective for reloads).
	 *
	 * @param resetCache if old data needs to be cleared
	 *
	 * @implNote Very important to run this method after {@link ListenerManager}, {@link CreateAccount},
	 *        {@link UserManager}, {@link MemberManager} {@link UserTable}, {@link BankTable}, and {@link MemberTable}
	 * 		initialization.
	 */
	public void userInitialize(boolean resetCache) {
		UserManager<Player> userManager   = initializer.getUserManager();
		MemberManager       memberManager = initializer.getMemberManager();

		if (resetCache) {
			for (User<Player> user : userManager.getUsers().values()) {
				if (user.getScoreboard() == null) continue;

				user.getScoreboard().end();
				user.setScoreboard(null);
			}

			userManager.clear();
		}

		List<Table<?>> tables      = ganglandDatabase.getTables();
		UserTable      userTable   = initializer.getInstanceFromTables(UserTable.class, tables);
		BankTable      bankTable   = initializer.getInstanceFromTables(BankTable.class, tables);
		MemberTable    memberTable = initializer.getInstanceFromTables(MemberTable.class, tables);

		for (Player player : Bukkit.getOnlinePlayers())
			if (!userManager.contains(userManager.getUser(player))) {
				User<Player> newUser = new User<>(player);
				Phone        phone   = new Phone(gangland, newUser, SettingAddon.getPhoneName());

				if (SettingAddon.isPhoneEnabled()) {
					newUser.setPhone(phone);
					if (!Phone.hasPhone(player)) phone.addPhoneToInventory(player);
				}

				initializer.getUserManager().initializeUserData(newUser, userTable, bankTable);

				UserDataInitEvent userDataInitEvent = new UserDataInitEvent(false, newUser);
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

				initializer.getMemberManager().initializeMemberData(newMember, memberTable);

				memberManager.add(newMember);
			}
	}

	/**
	 * Initializes the waypoint data (effective for reloads).
	 *
	 * @param resetCache if old data needs to be cleared
	 *
	 * @implNote Very important to run this method after {@link WaypointManager}, and {@link WaypointTable}
	 * 		initialization.
	 */
	public void waypointInitialize(boolean resetCache) {
		WaypointManager waypointManager = initializer.getWaypointManager();

		if (resetCache) waypointManager.clear();

		List<Table<?>> tables        = ganglandDatabase.getTables();
		WaypointTable  waypointTable = initializer.getInstanceFromTables(WaypointTable.class, tables);

		waypointManager.initialize(waypointTable);
	}

	/**
	 * Initializes the weapon data (effective for reloads).
	 *
	 * @param resetCache if old data needs to be cleared
	 *
	 * @implNote Very important to run this method after {@link WeaponManager}, and {@link WeaponTable}
	 * 		initialization.
	 */
	public void weaponInitialize(boolean resetCache) {
		WeaponManager weaponManager = initializer.getWeaponManager();

		if (resetCache) weaponManager.clear();

		List<Table<?>> tables      = ganglandDatabase.getTables();
		WeaponTable    weaponTable = initializer.getInstanceFromTables(WeaponTable.class, tables);

		weaponManager.initialize(weaponTable);
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
			logger.error("scoreboard file is not loaded!", exception);
			return;
		}

		initializer.scoreboardLoader();

		for (User<Player> user : initializer.getUserManager().getUsers().values()) {
			killScoreboard(user);

			ScoreboardManager scoreboardManager = initializer.getScoreboardManager();
			DriverHandler     driverHandler     = scoreboardManager.getDriverHandler(user.getUser());
			Scoreboard        scoreboard        = new Scoreboard(gangland, driverHandler);

			user.setScoreboard(scoreboard);
			user.getScoreboard().start();
		}
	}

	/**
	 * Reloads the periodical updates.
	 */
	public void periodicalUpdatesReload() {
		gangland.getPeriodicalUpdates().stop();
		gangland.periodicalUpdatesInitializer();
	}

	/**
	 * Reloads the inventory information.
	 */
	public void inventoryReload() {
		// Simple inventory reload for special inventories ONLY
		InventoryHandler.removeAllSpecialInventories();
		initializer.inventoryLoader();
	}

	private void killAllScoreboards() {
		for (User<Player> user : initializer.getUserManager().getUsers().values())
			killScoreboard(user);
	}

	private void killScoreboard(User<Player> user) {
		if (user.getScoreboard() == null) return;

		user.getScoreboard().end();
		user.setScoreboard(null);
	}

}
