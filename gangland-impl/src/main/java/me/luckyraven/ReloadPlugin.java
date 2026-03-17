package me.luckyraven;

import lombok.CustomLog;
import me.luckyraven.copsncrooks.detainment.DetainmentService;
import me.luckyraven.copsncrooks.jail.JailManager;
import me.luckyraven.copsncrooks.police.spawn.CopSpawnManager;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.member.Member;
import me.luckyraven.data.account.gang.member.MemberManager;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.data.plugin.PluginManager;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.data.teleportation.WaypointManager;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.database.repositories.lootchest.LootChestRepository;
import me.luckyraven.database.tables.gang.GangAllianceTable;
import me.luckyraven.database.tables.gang.GangTable;
import me.luckyraven.database.tables.lootchest.LootChestTable;
import me.luckyraven.database.tables.player.BankTable;
import me.luckyraven.database.tables.player.MemberTable;
import me.luckyraven.database.tables.player.UserTable;
import me.luckyraven.events.user.UserDataInitEvent;
import me.luckyraven.exception.PluginException;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.item.configuration.UniqueItemAddon;
import me.luckyraven.listener.ListenerManager;
import me.luckyraven.listener.player.CreateAccount;
import me.luckyraven.lootchest.LootChestManager;
import me.luckyraven.lootchest.LootChestService;
import me.luckyraven.lootchest.data.LootChestData;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.persistence.database.DatabaseHelper;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.persistence.repository.IRepository;
import me.luckyraven.scoreboard.Scoreboard;
import me.luckyraven.scoreboard.ScoreboardManager;
import me.luckyraven.scoreboard.driver.DriverHandler;
import me.luckyraven.util.item.unique.UniqueItemUtil;
import me.luckyraven.weapon.WeaponManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@CustomLog
public final class ReloadPlugin {

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
		if (Settings.isScoreboardEnabled()) scoreboardReload();
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
		// third, update message addon with new language configuration
		Messages.setMessageConfiguration(initializer.getLanguageLoader().getMessage());
	}

	/**
	 * Properly and in-order initializes the database data.
	 *
	 * @param resetCache if old data needs to be cleared
	 */
	public void databaseInitialize(boolean resetCache) {
		// order matters
		pluginDataInitialize(resetCache);
		rankInitialize(resetCache);
		gangInitialize(resetCache);
		memberInitialize(resetCache);
		// order doesn't matter
		userInitialize(resetCache);
		waypointInitialize(resetCache);
		weaponInitialize(resetCache);
		// required to be after weapon
		lootChestInitialize(resetCache);
		// cops-n-crooks data
		jailInitialize(resetCache);
		detainmentInitialize(resetCache);
		copSpawnerInitialize(resetCache);
	}

	/**
	 * Initializes the plugin data (effective for reloads).
	 *
	 * @param resetCache if old data needs to be cleared
	 */
	public void pluginDataInitialize(boolean resetCache) {
		PluginManager pluginManager = initializer.getPluginManager();

		if (resetCache) pluginManager.clear();

		pluginManager.initialize();
	}

	/**
	 * Initializes the rank data (effective for reloads).
	 *
	 * @param resetCache if old data needs to be cleared
	 *
	 * @implNote Very important to run this method after {@link RankManager} and its repositories are initialized.
	 */
	public void rankInitialize(boolean resetCache) {
		RankManager rankManager = initializer.getRankManager();

		if (resetCache) rankManager.clear();

		rankManager.initialize();
	}

	/**
	 * Initializes the gang data (effective for reloads).
	 *
	 * @param resetCache if old data needs to be cleared
	 *
	 * @implNote Very important to run this method after {@link GangManager}, {@link GangTable}, and
	 *        {@link GangAllianceTable} initialization.
	 */
	public void gangInitialize(boolean resetCache) {
		GangManager gangManager = initializer.getGangManager();

		if (resetCache) gangManager.clear();

		gangManager.initialize();
	}

	/**
	 * Initializes members' data (effective for reloads).
	 *
	 * @param resetCache if old data needs to be cleared
	 *
	 * @implNote Very important to run this method after {@link RankManager} and its repositories are initialized.
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
				// stop the timers
				user.getWanted().stopTimer();
				user.getBounty().stopTimer();

				// stop scoreboard
				if (user.getScoreboard() == null) continue;

				user.getScoreboard().end();
				user.setScoreboard(null);
			}

			userManager.clear();
		}

		List<Table<?>>  tables          = ganglandDatabase.getTables();
		UserTable       userTable       = initializer.getInstanceFromTables(UserTable.class, tables);
		BankTable       bankTable       = initializer.getInstanceFromTables(BankTable.class, tables);
		MemberTable     memberTable     = initializer.getInstanceFromTables(MemberTable.class, tables);
		UniqueItemAddon uniqueItemAddon = initializer.getUniqueItemAddon();

		// get the online users
		for (Player player : Bukkit.getOnlinePlayers()) {
			User<Player> onlineUser = userManager.getUser(player);

			if (onlineUser != null) continue;

			var newUser = new User<>(gangland, player);

			// add all the unique items
			var uniqueItems = uniqueItemAddon.getUniqueItems();

			for (var uniqueItem : uniqueItems.values()) {
				if (!uniqueItem.isAddOnJoin()) continue;
				if (!uniqueItem.isAddToInventory()) continue;

				if (UniqueItemUtil.hasUniqueItem(player, uniqueItem) && !uniqueItem.isAllowDuplicates()) continue;

				uniqueItem.addItemToInventory(player);
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

		// get the offline users
		UserManager<OfflinePlayer> offlineUserManager = initializer.getOfflineUserManager();

		if (resetCache) {
			offlineUserManager.clear();
		}

		DatabaseHelper helper = new DatabaseHelper(gangland, ganglandDatabase);

		helper.runQueries(database -> {
			// select all users from the user table
			List<Object[]> allUsers = database.table(userTable.getName()).selectAll();

			for (Object[] userData : allUsers) {
				String uuidString = String.valueOf(userData[0]);
				UUID   uuid       = UUID.fromString(uuidString);

				OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
				if (offlinePlayer.isOnline()) continue;

				User<OfflinePlayer> existingUser = offlineUserManager.getUser(offlinePlayer);
				if (existingUser != null) continue;

				User<OfflinePlayer> offlineUser = new User<>(gangland, offlinePlayer);

				offlineUserManager.initializeUserData(offlineUser, userTable, bankTable);

				offlineUserManager.add(offlineUser);
			}
		});
	}

	/**
	 * Initializes the waypoint data (effective for reloads).
	 *
	 * @param resetCache if old data needs to be cleared
	 *
	 * @implNote Very important to run this method after {@link WaypointManager} and its repositories are
	 * 		initialized.
	 */
	public void waypointInitialize(boolean resetCache) {
		WaypointManager waypointManager = initializer.getWaypointManager();

		if (resetCache) waypointManager.clear();

		waypointManager.initialize();
	}

	/**
	 * Initializes the weapon data (effective for reloads).
	 *
	 * @param resetCache if old data needs to be cleared
	 *
	 * @implNote Very important to run this method after {@link WeaponManager} and its repositories are initialized.
	 */
	public void weaponInitialize(boolean resetCache) {
		WeaponManager weaponManager = initializer.getWeaponManager();

		if (resetCache) weaponManager.clear();

		weaponManager.initialize();
	}

	/**
	 * Initializes the loot chest data (effective for reloads).
	 *
	 * @param resetCache if old data needs to be cleared
	 *
	 * @implNote Very important to run this method after {@link LootChestService} and {@link LootChestTable}
	 * 		initialization.
	 */
	public void lootChestInitialize(boolean resetCache) {
		LootChestManager lootChestManager = initializer.getLootChestManager();

		if (resetCache) lootChestManager.clear();

		// Reload config from files first
		initializer.lootChestLoader();

		IRepository<LootChestData> repository = ganglandDatabase.getRepositoryRegistry()
																.getRepository(LootChestData.class);

		if (!(repository instanceof LootChestRepository repo)) {
			String message = "LootChestData repository is not initialized!";

			log.error(message);
			throw new PluginException(message);
		}

		lootChestManager.initialize(repo, true);
	}

	/**
	 * Initializes the jail data (effective for reloads).
	 *
	 * @param resetCache if old data needs to be cleared
	 */
	public void jailInitialize(boolean resetCache) {
		JailManager jailManager = initializer.getJailManager();

		if (resetCache) jailManager.reload();
	}

	/**
	 * Initializes the detainment data (effective for reloads).
	 *
	 * @param resetCache if old data needs to be cleared
	 */
	public void detainmentInitialize(boolean resetCache) {
		DetainmentService detainmentService = initializer.getDetainmentService();

		if (resetCache) detainmentService.getDetainmentManager().reload();
	}

	/**
	 * Initializes the cop spawner data (effective for reloads).
	 *
	 * @param resetCache if old data needs to be cleared
	 */
	public void copSpawnerInitialize(boolean resetCache) {
		CopSpawnManager copSpawnManager = initializer.getCopSpawnManager();

		if (resetCache) copSpawnManager.reloadSpawners();
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
			log.error("scoreboard file is not loaded!", exception);
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
