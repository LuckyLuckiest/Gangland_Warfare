package me.luckyraven;

import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.Member;
import me.luckyraven.data.account.gang.MemberManager;
import me.luckyraven.data.plugin.PluginData;
import me.luckyraven.data.plugin.PluginDataCleanupService;
import me.luckyraven.data.plugin.PluginManager;
import me.luckyraven.data.rank.Permission;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.data.teleportation.Waypoint;
import me.luckyraven.data.teleportation.WaypointManager;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.database.component.Table;
import me.luckyraven.database.tables.*;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.loot.LootChestService;
import me.luckyraven.loot.data.LootChestData;
import me.luckyraven.util.Pair;
import me.luckyraven.util.timer.RepeatingTimer;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PeriodicalUpdates {

	private static final Logger logger = LogManager.getLogger(PeriodicalUpdates.class.getSimpleName());

	private final Gangland    gangland;
	private final Initializer initializer;

	private PluginDataCleanupService cleanupService;
	private RepeatingTimer           repeatingTimer;

	public PeriodicalUpdates(Gangland gangland, long interval) {
		this(gangland);
		this.repeatingTimer = new RepeatingTimer(gangland, 20L * interval, timer -> task());
	}

	public PeriodicalUpdates(Gangland gangland) {
		this.gangland    = gangland;
		this.initializer = gangland.getInitializer();
	}

	/**
	 * All queried data is sent and handle in the database.
	 */
	public void updatingDatabase() {
		GanglandDatabase database = initializer.getGanglandDatabase();
		DatabaseHelper   helper   = new DatabaseHelper(gangland, database);
		List<Table<?>>   tables   = database.getTables();

		// update the rank data
		RankManager         rankManager         = initializer.getRankManager();
		RankTable           rankTable           = initializer.getInstanceFromTables(RankTable.class, tables);
		PermissionTable     permissionTable     = initializer.getInstanceFromTables(PermissionTable.class, tables);
		RankPermissionTable rankPermissionTable = initializer.getInstanceFromTables(RankPermissionTable.class, tables);
		RankParentTable     rankParentTable     = initializer.getInstanceFromTables(RankParentTable.class, tables);

		// rank data
		updateRankData(rankManager, helper, rankTable, permissionTable, rankPermissionTable, rankParentTable);

		// update the user data
		UserManager<Player>        userManager        = initializer.getUserManager();
		UserManager<OfflinePlayer> offlineUserManager = initializer.getOfflineUserManager();
		UserTable                  userTable          = initializer.getInstanceFromTables(UserTable.class, tables);
		BankTable                  bankTable          = initializer.getInstanceFromTables(BankTable.class, tables);

		// online users
		updateUserData(userManager, helper, userTable, bankTable);

		// offline users
		updateUserData(offlineUserManager, helper, userTable, bankTable);
		offlineUserManager.clear();

		// update the gang data
		GangManager     gangManager     = initializer.getGangManager();
		MemberManager   memberManager   = initializer.getMemberManager();
		GangTable       gangTable       = initializer.getInstanceFromTables(GangTable.class, tables);
		GangAlliesTable gangAlliesTable = initializer.getInstanceFromTables(GangAlliesTable.class, tables);
		MemberTable     memberTable     = initializer.getInstanceFromTables(MemberTable.class, tables);

		// gang data
		updateGangData(gangManager, helper, gangTable, gangAlliesTable);

		// member data
		updateMemberData(memberManager, helper, memberTable);

		// update the waypoint data
		WaypointManager waypointManager = initializer.getWaypointManager();
		WaypointTable   waypointTable   = initializer.getInstanceFromTables(WaypointTable.class, tables);

		// waypoint data
		updateWaypointData(waypointManager, helper, waypointTable);

		// update the weapon data
		WeaponManager weaponManager = initializer.getWeaponManager();
		WeaponTable   weaponTable   = initializer.getInstanceFromTables(WeaponTable.class, tables);

		// weapon data
		updateWeaponData(weaponManager, helper, weaponTable);

		// update the loot chest data
		LootChestService lootChestManager = initializer.getLootChestManager();
		LootChestTable   lootChestTable   = initializer.getInstanceFromTables(LootChestTable.class, tables);

		// loot chest data
		updateLootChestData(lootChestManager, helper, lootChestTable);

		// update plugin data
		PluginManager   pluginManager   = initializer.getPluginManager();
		PluginDataTable pluginDataTable = initializer.getInstanceFromTables(PluginDataTable.class, tables);

		// plugin data
		updatePluginData(pluginManager, helper, pluginDataTable);
	}

	/**
	 * Resets the cache data.
	 */
	public void resetCache() { }

	/**
	 * Updates the plugin information.
	 */
	public void forceUpdate() {
		logger.info("Force update...");
		task();
	}

	/**
	 * Stops the periodical update timer.
	 */
	public void stop() {
		if (this.repeatingTimer == null) return;

		this.repeatingTimer.stop();
		this.repeatingTimer = null;
	}

	/**
	 * Starts the periodical update tasks.
	 */
	public void start() {
		if (this.repeatingTimer == null) return;

		logger.info("Initializing auto-save...");

		initializeCleanupService();

		this.repeatingTimer.start(true);
	}

	private void initializeCleanupService() {
		GanglandDatabase database = initializer.getGanglandDatabase();
		DatabaseHelper   helper   = new DatabaseHelper(gangland, database);
		List<Table<?>>   tables   = database.getTables();

		PluginManager pluginManager = initializer.getPluginManager();
		WeaponTable   weaponTable   = initializer.getInstanceFromTables(WeaponTable.class, tables);

		cleanupService = new PluginDataCleanupService(pluginManager, helper, weaponTable);
	}

	private void task() {
		long    start = System.currentTimeMillis();
		boolean log   = SettingAddon.isAutoSaveDebug();

		// Check for scheduled cleanup
		if (cleanupService != null) {
			try {
				cleanupService.checkAndPerformCleanup();
			} catch (Throwable throwable) {
				logger.error("There was an issue during cleanup check...", throwable);
			}
		}

		// auto-saving
		if (log) logger.info("Saving...");
		try {
			updatingDatabase();
			if (log) logger.info("Data save complete");
		} catch (Throwable throwable) {
			logger.error("There was an issue saving the data...");
		}

		// resetting player inventories
		if (log) logger.info("Cache reset...");
		try {
			resetCache();
		} catch (Throwable exception) {
			logger.error("There was an issue resetting the cache...", exception);
		}

		long end = System.currentTimeMillis();

		if (log) logger.info("The process took {}ms", end - start);
	}

	private void updateUserData(UserManager<? extends OfflinePlayer> userManager, DatabaseHelper helper,
								UserTable userTable, BankTable bankTable) {
		helper.runQueries(database -> {
			for (User<? extends OfflinePlayer> user : userManager.getUsers().values()) {
				// update user data
				Map<String, Object> searchUser = userTable.searchCriteria(user);
				Object[] data = database.table(userTable.getName())
										.select((String) searchUser.get("search"), (Object[]) searchUser.get("info"),
												(int[]) searchUser.get("type"), new String[]{"*"});

				if (data.length == 0) userTable.insertTableQuery(database, user);
				else userTable.updateTableQuery(database, user);

				// update bank data
				Map<String, Object> searchBank = bankTable.searchCriteria(user);
				Object[] bank = database.table(bankTable.getName())
										.select((String) searchBank.get("search"), (Object[]) searchBank.get("info"),
												(int[]) searchBank.get("type"), new String[]{"*"});

				if (bank.length == 0) bankTable.insertTableQuery(database, user);
				else bankTable.updateTableQuery(database, user);
			}
		});
	}

	private void updateGangData(GangManager gangManager, DatabaseHelper helper, GangTable gangTable,
								GangAlliesTable gangAlliesTable) {
		helper.runQueries(database -> {
			for (Gang gang : gangManager.getGangs().values()) {
				// update gang data
				Map<String, Object> searchGang = gangTable.searchCriteria(gang);
				Object[] data = database.table(gangTable.getName())
										.select((String) searchGang.get("search"), (Object[]) searchGang.get("info"),
												(int[]) searchGang.get("type"), new String[]{"*"});

				if (data.length == 0) gangTable.insertTableQuery(database, gang);
				else gangTable.updateTableQuery(database, gang);

				// update gang allie data
				for (Pair<Gang, Long> alliedGang : gang.getAllies()) {
					Pair<Gang, Gang>    team            = new Pair<>(gang, alliedGang.first());
					Map<String, Object> searchGangAllie = gangAlliesTable.searchCriteria(team);
					Object[] allie = database.table(gangAlliesTable.getName())
											 .select((String) searchGangAllie.get("search"),
													 (Object[]) searchGangAllie.get("info"),
													 (int[]) searchGangAllie.get("type"), new String[]{"*"});

					if (allie.length == 0) gangAlliesTable.insertTableQuery(database, team);
					else gangAlliesTable.updateTableQuery(database, team);
				}
			}
		});
	}

	private void updateMemberData(MemberManager memberManager, DatabaseHelper helper, MemberTable memberTable) {
		helper.runQueries(database -> {
			for (Member member : memberManager.getMembers().values()) {
				Map<String, Object> search = memberTable.searchCriteria(member);
				Object[] data = database.table(memberTable.getName())
										.select((String) search.get("search"), (Object[]) search.get("info"),
												(int[]) search.get("type"), new String[]{"*"});

				if (data.length == 0) memberTable.insertTableQuery(database, member);
				else memberTable.updateTableQuery(database, member);
			}
		});
	}

	private void updateRankData(RankManager rankManager, DatabaseHelper helper, RankTable rankTable,
								PermissionTable permissionTable, RankPermissionTable rankPermissionTable,
								RankParentTable rankParentTable) {
		helper.runQueries(database -> {
			// update rank data
			for (Rank rank : rankManager.getRankTree()) {
				Map<String, Object> search = rankTable.searchCriteria(rank);
				Object[] data = database.table(rankTable.getName())
										.select((String) search.get("search"), (Object[]) search.get("info"),
												(int[]) search.get("type"), new String[]{"*"});

				if (data.length == 0) rankTable.insertTableQuery(database, rank);
				else rankTable.updateTableQuery(database, rank);
			}

			// update permission data
			for (Permission permission : rankManager.getPermissions().values()) {
				Map<String, Object> search = permissionTable.searchCriteria(permission);
				Object[] data = database.table(permissionTable.getName())
										.select((String) search.get("search"), (Object[]) search.get("info"),
												(int[]) search.get("type"), new String[]{"*"});

				if (data.length == 0) permissionTable.insertTableQuery(database, permission);
				else permissionTable.updateTableQuery(database, permission);
			}

			// update permission data
			for (Pair<Integer, Integer> rankParentIds : rankManager.getRanksParent()) {
				Pair<Rank, Rank> rankParent = new Pair<>(rankManager.get(rankParentIds.first()),
														 rankManager.get(rankParentIds.second()));
				Map<String, Object> search = rankParentTable.searchCriteria(rankParent);
				Object[] data = database.table(rankParentTable.getName())
										.select((String) search.get("search"), (Object[]) search.get("info"),
												(int[]) search.get("type"), new String[]{"*"});

				if (data.length == 0) rankParentTable.insertTableQuery(database, rankParent);
				else rankParentTable.updateTableQuery(database, rankParent);
			}

			// update permission data
			for (Pair<Integer, Integer> rankPermissionIds : rankManager.getRanksPermissions()) {
				Pair<Rank, Permission> rankPermission = new Pair<>(rankManager.get(rankPermissionIds.first()),
																   rankManager.getPermission(
																		   rankPermissionIds.second()));
				Map<String, Object> search = rankPermissionTable.searchCriteria(rankPermission);
				Object[] data = database.table(rankPermissionTable.getName())
										.select((String) search.get("search"), (Object[]) search.get("info"),
												(int[]) search.get("type"), new String[]{"*"});

				if (data.length == 0) rankPermissionTable.insertTableQuery(database, rankPermission);
				else rankPermissionTable.updateTableQuery(database, rankPermission);
			}
		});
	}

	private void updateWaypointData(WaypointManager waypointManager, DatabaseHelper helper,
									WaypointTable waypointTable) {
		helper.runQueries(database -> {
			for (Waypoint waypoint : waypointManager.getWaypoints().values()) {
				Map<String, Object> search = waypointTable.searchCriteria(waypoint);
				Object[] data = database.table(waypointTable.getName())
										.select((String) search.get("search"), (Object[]) search.get("info"),
												(int[]) search.get("type"), new String[]{"*"});

				if (data.length == 0) waypointTable.insertTableQuery(database, waypoint);
				else waypointTable.updateTableQuery(database, waypoint);
			}
		});
	}

	private void updateWeaponData(WeaponManager weaponManager, DatabaseHelper helper, WeaponTable weaponTable) {
		helper.runQueries(database -> {
			for (Weapon weapon : weaponManager.getWeapons().values()) {
				Map<String, Object> search = weaponTable.searchCriteria(weapon);
				Object[] data = database.table(weaponTable.getName())
										.select((String) search.get("search"), (Object[]) search.get("info"),
												(int[]) search.get("type"), new String[]{"*"});

				if (data.length == 0) weaponTable.insertTableQuery(database, weapon);
				else weaponTable.updateTableQuery(database, weapon);
			}
		});
	}

	private void updateLootChestData(LootChestService lootChestManager, DatabaseHelper helper,
									 LootChestTable lootChestTable) {
		helper.runQueries(database -> {
			for (LootChestData chestData : lootChestManager.getAllChests()) {
				Map<String, Object> search = lootChestTable.searchCriteria(chestData);
				Object[] data = database.table(lootChestTable.getName())
										.select((String) search.get("search"), (Object[]) search.get("info"),
												(int[]) search.get("type"), new String[]{"*"});

				if (data.length == 0) lootChestTable.insertTableQuery(database, chestData);
				else lootChestTable.updateTableQuery(database, chestData);
			}
		});
	}

	private void updatePluginData(PluginManager pluginManager, DatabaseHelper helper, PluginDataTable pluginDataTable) {
		helper.runQueries(database -> {
			for (PluginData pluginData : pluginManager.getPluginDataList()) {
				Map<String, Object> search = pluginDataTable.searchCriteria(pluginData);
				Object[] data = database.table(pluginDataTable.getName())
										.select((String) search.get("search"), (Object[]) search.get("info"),
												(int[]) search.get("type"), new String[]{"*"});

				if (data.length == 0) pluginDataTable.insertTableQuery(database, pluginData);
				else pluginDataTable.updateTableQuery(database, pluginData);
			}
		});
	}

	private boolean userViewingInventory(User<Player> user) {
		List<InventoryType> inventoryTypes = new ArrayList<>();

		// the plugin uses only anvil and chest so far
		inventoryTypes.add(InventoryType.ANVIL);
		inventoryTypes.add(InventoryType.CHEST);

		for (InventoryType type : inventoryTypes)
			if (user.getUser().getOpenInventory().getTopInventory().getType() == type) return true;

		return false;
	}

}
